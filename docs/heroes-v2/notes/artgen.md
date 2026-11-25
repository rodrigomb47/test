# Art generator notes (herogen)

Working notes of the art pipeline tasks (HEROES_V2.md section 13, task IDs from section 15.2). Each task appends its
own section. Where a section here differs from the design, the section records why; HEROES_V2.md is updated with the
docs task (V5).

---

## G1: herogen core (2026-11-12)

### What exists

| Path | What |
|---|---|
| `tools/herogen/herogen.py` | entry point: `python3 tools/herogen/herogen.py <command>` (checks for Python 3.10+, Pillow, NumPy) |
| `cli.py` | `build [ids \| --all] [--check]`, `check [ids \| --all]`, `previews [--out DIR]`, `stats [ids]` |
| `util.py` | deterministic JSON writer, PNG writer, splitmix64 RNG and integer hashes |
| `expr.py` | whitelist expression parser (no eval) for part params and base dimensions |
| `rig.py` | rig v1, the six bases, the in-memory model (bones, cubes) |
| `parts.py` | parts loader: params, slots, mirror, phase and fine bones, paint ops, weapons |
| `uvpack.py` | whole-texel face rectangles, skin-net islands, the packer, the file UV convention |
| `palette.py` | sRGB / OKLab / OKLCH / CIELab, 5-tone ramps, palette resolution and checks |
| `paint.py` | the procedural painter (light by face, bands, AO, outlines, materials, patterns, cutouts, glow) |
| `face.py` | eye, brow, mouth and cheek templates; the 12 + 8 + 4 frames; head front with holes; mood table |
| `geo.py` | GeckoLib geometry writer (format 1.12.0) and reader |
| `visual.py` | `<id>.visual.json` |
| `build.py` | hero source to output files, `--check` |
| `validate.py` | the geometry and face half of the 14.2 asset checks |
| `render/ortho.py`, `previews.py` | minimal rest-pose orthographic renderer and the preview sheets (stopgap until G2) |
| `tests/` | unit tests (87 after the review): `python3 -m unittest discover tools/herogen/tests` (or `python3 -m unittest` in `tools/herogen`) |
| `herosrc/rig/rig_v1.json` | bones (61 with every option, 33 required), pivots, heights, joint limits, UV template, face layout, budgets (72 bones, 140 cubes); replaced by `rig_v2.json` in the style pass below |
| `herosrc/bases/*.json` | the six bases (dims) and `_template.json` (body cubes shared by all six); rig v2 bases in the style pass below |
| `herosrc/parts/**` | 11 starter parts for the test rig and the fallback (G6a-c grow this into the library) |
| `herosrc/heroes/test_rig.hero.json`, `_fallback.hero.json` | the pipeline fixture and the neutral model |
| `src/main/resources/assets/factionclash/{geo/hero, textures/entity/hero, animations/hero, heroes}/` | generated: `test_rig.*` (geometry, texture, glow mask, `_p2` and `_p2_glowmask`, empty animation file, visual.json) and `_fallback.*` |
| `docs/heroes-v2/notes/previews/g1_*.png` | previews for the owner: the six bases, the test rig, its faces and moods, its texture, the fallback |

Acceptance (15.2 G1): the test rig with face frames builds byte-identically twice (`test_build`: same process, and two
processes with different `PYTHONHASHSEED`); `herogen build --check` reports the committed outputs fresh; `herogen check`
passes for both models and all six bare bases.

### Conventions later tasks rely on

- **Coordinates** are Bedrock model pixels as written to the file: feet at y 0, the hero faces north (-z), the hero's
  RIGHT is -x. GeckoLib negates x when it bakes (and Blockbench shows files the same way), so -x in the file is the
  hero's right on screen. `east` faces are the hero's right side (file -x), `west` the left side (file +x).
- **Face UVs** are written per face, never box UV. Each face's rectangle has vanilla box-UV orientation: front and
  back as seen from outside; right side (east) with its left edge at the back; left side (west) with its left edge at
  the front; top and bottom with the left edge on the hero's right and the bottom row at the front. The bottom face is
  stored as `uv = (u, v + h)`, `uv_size = (w, -h)`, exactly what GeckoLib's own box-UV code computes for `DOWN`, so
  per-face and box UV look the same. One function pair owns this (`uvpack.to_file_uv` / `from_file_uv`), and
  `uvpack.texel_point` maps a texel to its 3D point for the painter. **Confirmed against GeckoLib 4.9.3's code by the review** (`tests/test_geckolib.py`); G2's fidelity test still checks the whole render (the
  preview renderer uses the same convention, so a mistake would show in both and only the game screenshot catches it).
- **Rotations**: cube rotations are file degrees about the cube pivot. The renderer emulates GeckoLib: render-space
  angles `(-rx, -ry, rz)`, applied X first, then Y, then Z. With that, `+55` about x swings a hanging scabbard
  backward. Also confirmed against GeckoLib's code by the review.
- **Determinism**: JSON with a fixed key order (geometry keys first: `format_version`, `minecraft:geometry`,
  `description`, `identifier`, `name`, `parent`, `pivot`, `rotation`, `origin`, `size`, `inflate`, `uv`, `uv_size`;
  everything else sorted), numbers rounded to 3 decimals and written without `.0`; PNGs RGBA with only IHDR, IDAT,
  IEND; randomness from splitmix64 seeded by the hero id (never Python's `random` or `hash`).

### Deviations from the design and additions (each one deliberate)

1. **Locators follow their part.** The tree in 4.3 draws the `vfx_*` locators under `root`. A locator under root
   would not move with a swing, so `vfx_tip_r/l` are children of `weapon_r/l` (at the hand until a weapon part sets
   the tip), `vfx_chest` of `chest`, `vfx_head` of `head`; `vfx_feet` stays under `root`. Names are unchanged.
2. **Base additions (PROPOSED, like the table):** leg pivot `hip_x` (female 1.5 / 1.75 / 2.0 with a 0.5 px gap
   between the legs, male 1.5 / 1.75 / 2.0 with the legs touching); neck 2x2 (f slim and standard), 2.5 (f large,
   m slim), 3 (m standard), 3.5 (m large); a small bust cube on the female bases (4x2.5x0.5, 5x2.5x0.75, 6x2.5x1 at
   y 19-21.5). Limb split: thigh 13-6.5, shin 6.5-2, foot 0-2 with a 1 px toe forward; upper arm 22-17, forearm
   17-13.5, hand 13.5-11.5 (wrist pivot 13.5); item and weapon pivots at the hand centre (y 12.5).
3. **One body template.** The six base files hold only their dimensions; `_template.json` holds the body cubes as
   expressions over them, so the six bodies cannot drift apart.
4. **Packer.** Instead of a shelf packer: first fit in row-major order on an occupancy grid, testing the island's
   face rectangles rather than its bounding box (small islands nest in the empty corners of a skin net), largest
   first; it tries height order, then area, then width, and the first order that fits wins; 1-texel gutter around
   everything. m_large's body did not fit the 64x64 base region with bounding boxes. Each cube's net is still a
   vanilla skin net, but the base region is no longer laid out like a vanilla skin.
5. **Eye spacing is 2 or 4 texels**, not 2-4: mirrored eyes always leave an even gap. A 6-wide eye needs spacing 2.
6. **Zero-thickness cubes get one face** (the face planes have only `north`): rendering is cull-free, so a second
   face would z-fight.
7. **Parts format extensions** (all in `parts.py`'s docstring): logical colour slots `main`, `trim`, `alt`, `detail`
   mapped by the hero entry's `color`, `trim`, `alt`, `detail` (a part may also name palette slots or the fixed slots
   `sash`, `sclera`, `blush`, `tear` ... directly); `paint` ops that paint over existing cubes, usually the base body,
   by cube glob, face, height band (`y`), `x`, `z`, `abs_x` (symmetric regions) and side, with an optional trim band:
   this is how outfits, hair on the head and boots are drawn without extra cubes; cube `trim`, `taper`, `tone`,
   `glow`, `faces`; part `locators`. `art.base_layer` maps the base slots (`base_top`, `base_bottom`, `shoes`, `skin`;
   defaults `secondary`, `accent`, `leather`, `skin`).
8. **Phase and fine bones.** A phase part's own bones are renamed `pN_<name>`; its cubes on rig bones go into a nested
   `pN_<bone>` child with the same pivot (so it still follows that bone's animation). A cube with `"fine": true` goes
   into a nested `fine_<bone>`. GeckoLib hides children with their parent, so nesting is enough.
9. **Weapons** are authored once in grip space (grip centre at 0,0,0, blade along -z like a held item at rest), with
   cube roles `hilt` / `blade`, a `scabbard` list and a `tip`. The generator places the prop on `weapon_r` (and
   `weapon_l` for `pair`), sets `vfx_tip_*` at the tip, and builds the sheathed copy on `weapon_back` from the hilt and
   scabbard cubes: `hip` hangs it on the opposite hip, blade down, rotated +55 degrees about x; `back` carries it
   blade down behind the back, rotated -35 degrees about z (hilt over the right shoulder for a right-hand weapon).
10. **visual.json** carries the **resolved** mood table (`face.moods`, defaults from `face.MOOD_FRAMES` with the hero's
    overrides, blush and fang applied), the blink profile with its intervals, the frame regions, bone groups (`tint`,
    `fine`, `phase`, `weapon_drawn`, `weapon_sheathed`, `items`, `locators`), the texture facts and
    `texture.glow_outside_face`. A2 should read `face.moods` instead of hard-coding the table (the `_fallback` keeps a
    copy). The glow mask is always written because eye frame 10 glows; when `glow_outside_face` is false the renderer
    can skip the glow layer unless frame 10 is showing.
11. **Default mood table** (eyes / mouth / cheeks): calm 0/0/0, happy 3/1/0 (cheeks 1 with `blush`), angry 4/4/0,
    focused 8/0/0, hurt 7/4/0, tired 1/0/2, sad 5/5/0, smug 9/1/0 (mouth 7 with a fang), surprised 6/2/0,
    asleep 11/0/0, flustered 6/5/1, determined 4/0/0.
12. **A phase palette that changes `eyes` also recolours the iris**, even when `art.face.iris` is authored.
13. **The hero's own animation file is written empty** (`{"format_version": "1.8.0", "animations": {}}`), because
    GeckoLib needs every file of the chain; G3's writer replaces it. The server definition is not written by G1.
14. **`_fallback`** (a shared asset in SLICE_PLAN 3.9) is built too: m_slim, light-grey clothes so a faction tint
    reads, short hair, a plain face, the sash.
15. **Face-front details** (bangs changed by the review, see below): the head front is skin with darker jaw corners; bangs in the starter hair stop at y 30.5
    (they cover brow row 0, never the lash row). Hair gets its bright crown ring at y 31-31.75.
16. **Minimal renderer** (until G2): rest pose, the GeckoLib conventions above, cut-out at alpha 0.1, nearest
    sampling, block-style face shading (top 1.0, front and back 0.8, sides 0.6, bottom 0.5), not vanilla's entity light
    directions.

### Painter and face rules as built (13.4, 13.5)

- Ramps: OKLCH, 0.08 lightness per tone; shadows shift hue 6 degrees per step toward blue-violet (hue 285) and lose
  12% chroma per step, skin shadows shift toward red (hue 28) and keep their chroma; highlights shift 4 degrees per
  step toward yellow (hue 100); greys get faint cool shadows; lightness clamped to 0.14-0.965 (no pure black or white
  off tone 0). Out-of-gamut colours keep lightness and hue and lose chroma.
- Materials: cloth (fold lines every 3-4 texels on faces 6+ tall), skin (flat), hair (strand lines, crown ring),
  metal (specular column, top rim, dark far edge), leather (stitch row), fur (noise), scale (arcs), wood (grain),
  lacquer (sharp highlight row), bone, gem (facets), glow (bright, into the glow mask), paper (flat).
- Patterns built so far: `stripes` (h, v, d), `checks`, `plaid`, `dots`, `pleats` (tone only). Cutouts: `jagged_N`,
  `tip_jagged_N`, `bangs_jagged_N` (whole-column teeth with the tip on the middle column), `tri_up`, `round_bottom`,
  `fur_tips`, `torn`; `taper`. Hemp-leaf, waves, petals, lace, emblem stamps and the rest are G6c.
- Palette checks (printed as NOTEs by `build`): primary, secondary and accent differ by CIE76 delta-E 10 or more;
  primary and secondary differ by 15% or more in greyscale (Pillow's luma); no pure black or white.
- Eyes: 9 templates (`round`, `almond`, `sharp`, `droopy`, `cat`, `sleepy`, `wide`, `narrow`, `heavy_lid`) with width
  4-6, height 4-5, iris 2-4, pupils `round`, `slit`, `star`, `ring`, `none`, lashes 0-3, 1-2 highlights, optional
  lower lash; 6 brow templates; 5 mouth templates with fangs; the frame rules of 13.5. The main highlight sits on the
  topmost iris (or pupil) texel the lid and lashes leave visible.

### Open items for later tasks

- **G2**: confirm the whole render (entity transforms, lighting, the face planes) against an in-game screenshot (the UV
  and rotation conventions themselves are now pinned to GeckoLib's code by `tests/test_geckolib.py`); replace
  `render/ortho.py` and `previews.py` with the full renderer, `preview`, `faces`, `sheet`, `gif`.
- **G3**: write the hero's own animation file (build.py writes the empty one today) and the server definition;
  read `model.secondary` (chains declared by parts, recorded on the model) for the follow-through bake; the rig's
  `joint_limits` leave the sign of `flex` to G3.
- **G7**: animation, marker and kit checks, distinctness NOTEs and the Java `HeroAssetsTest` on top of
  `validate.py`. Byte-identical PNGs assume the same Pillow and zlib (and the same libm for the colour maths); if
  `build --check` must pass across machines, compare decoded pixels for PNGs instead of bytes.
- **A2**: `test_rig` has client assets only (no server definition); it needs a fixture definition (for example via
  `HeroRegistry.registerTransient`) to be spawned. Tint bones: `sash`, `fine_sash_knot`. The face planes are
  `eyes`, `mouth`, `cheeks` with their frame 0 at the template regions.
- **G6a-c**: the 11 starter parts (`hair/high_ponytail_long`, `hair/short_crop`, `ears/cat`, `tails/cat_long`,
  `outfit_top/short_jacket`, `outfit_bottom/pleated_skirt`, `legwear/tights_boots`, `back/cape_short`,
  `accessories/sash_obi`, `headwear/halo_ring`, `weapons/sword_plain`) use the final format; `lint` (every part on
  every base) is not built yet.

---

## G1 review (2026-11-12)

Adversarial review of the G1 commit (1b94e959) against HEROES_V2.md 4.3, 4.6, 13 and 14.2, the CRITIQUE, and GeckoLib
4.9.3 itself, then fixes. Commit "Review fixes: ...".

### What was checked and holds

- **GeckoLib conventions, against GeckoLib's own code.** The published `geckolib-neoforge-1.21.1-4.9.3.jar` (SHA-256
  `20a1995e...1c47`, the hash HEROES_V2.md 4.2 records) was downloaded to a scratch folder and read with `javap -c`
  (never executed): `BakedModelFactory.Builtin.constructCube/constructBone/buildQuad`, `BakedModelFactory.VertexSet`,
  `GeoQuad.build`, `FaceUV.Rotation.rotateUvs`, `RenderUtil.rotateMatrixAroundBone/Cube`, `GeoRenderer.renderCube`,
  the `loading.json.raw` records and `FormatVersion`. Every G1 convention matches: x mirrored at bake, per-face UV
  orientation of all six faces (front and back as seen from outside, east = the hero's right at file -x with its left
  edge at the back, west with its left edge at the front, top and bottom with the hero's right on the left and the
  front on the bottom row), the bottom face addressed as `uv (u, v+h)`, `uv_size (w, -h)`, rotations baked as
  `(-rx, -ry, rz)` and applied about Z, then Y, then X, null quads (faces missing from a per-face `uv`) skipped by the
  renderer, `format_version` 1.12.0 accepted, the empty animation file valid. `tests/test_geckolib.py` ports that code
  (vertex sets, quad vertex order, the UV swap without mirror) and checks every texel of every face lands on the point
  the painter paints, the preview renderer's corners and rotations, and that written geometry uses only keys GeckoLib
  reads (Gson drops unknown keys silently). Removing the bottom-face flip or changing the rotation order fails it.
- Bone names, 61-bone tree, 33 required, budgets 72 bones and 140 cubes, the six bases against the 4.3 table, whole
  texels, the fixed UV regions and the 4 x 3 / 8 / 4 frame grids, byte-identical builds (one process and two processes
  with different hash seeds), `build --check`.

### Fixed

1. **Brows merged with the lash.** Rest brows used rows 0-1 and the lash row 2, and the starter bangs (down to y 30.5)
   hid row 0, so what showed was brow fragments touching the lash: a heavy dark blob over each eye in most moods. Now
   rest brows sit on row 0 only, with row 1 kept as skin; the arched brow tapers with a lighter outer texel; mood
   brows use row 1 only where that frame moves the lash away (angry: inner end down; sad and pain: outer end down;
   focused, smug, sleeping: the lid is lowered); open, half and closed share their brows, so a blink never moves them.
   The starter bangs (`hair/high_ponytail_long`, `hair/short_crop`) now end at y 31: they cover the forehead rows and
   leave the brows visible (bangs may still cover the brow rows in other hair parts, 4.6).
2. **Garments were striped.** The lower-third band was applied per body cube and occlusion to every cube under a wider
   one, so one top or pair of trousers showed a dark band at every cube seam (chest/waist, thigh/shin: "knee pads").
   Now the band is applied only where the region ends at the face's bottom edge (a hem), and occlusion only where a
   different region sits on top (a sleeve over a hand, a collar over a shirt).
3. **Hard line under the bust.** A cloth or leather edge that rests against another cube (bust, collar) now gets a
   one-tone soft shadow instead of the two-tone hem outline; free hems and all hair keep the full outline.
4. **Cutouts only on the side faces.** A triangular ear kept a full-height side strip and a full-width top, a jagged
   fringe and a tapered lock kept a full-size flat bottom (seen from slightly below, which is the player's usual view
   of a hero's bangs). Now the top and bottom faces keep only what the side faces keep on their edge rows, and a
   one-texel-thin cube cuts its narrow faces like the broad face's edge column. The lowest texel a cutout leaves in a
   column (a jagged tip) gets the hem outline too.
5. **The mouth plane's angled-view sliver.** The planes sit 0.05 px in front of their holes; at an angle that gap
   showed a thin dark line into the head beside the mouth (visible in the 3/4 preview, about a pixel at three blocks
   in game). The mouth frames' edge columns are plain skin in every frame, so the head front now keeps its skin under
   them (hole columns 5-10 instead of 4-11): the gap shows identical skin, and the two coincident texels cannot flicker
   because they are the same colour. `herogen check` verifies that every eyes and mouth frame equals the head front
   wherever both are opaque. **Deviation** from 4.6 ("transparent exactly under the planes"): the hole is one column
   narrower on each side of the mouth; the eyes hole is unchanged (its edge columns are at the head's corners, and
   its top and bottom rows carry brows and lids).
6. **A hip scabbard swung with the chest.** `weapon_back` is a child of `back` (chest), so a waist twist or chest lean
   swung a hip-sheathed sword (9-12 px of scabbard) through the leg. For `sheathed: hip`, `weapon_back` now hangs from
   `body` (the hips, pivot 0, 15.5, -0.5); for `back` it stays under `back`. The name is unchanged, so the renderer's
   rule (4.3 naming table) and `visual.json` `bones.weapon_sheathed` still work. **Deviation** from the 4.3 tree
   (recorded in `rig_v1.json` and `parts.SHEATH`).

### Tests added (72 -> 87)

`test_geckolib.py` (6, above); faces: rest brows clear of the lash for every template and lash weight, blink frames
keep the brows, angry and sad brow directions, mouth edge columns equal the head front skin; painter: one garment not
striped, band at a free hem, soft edge on the bust, cutouts carried to caps and narrow faces, outlined cut tips;
validator: a mouth edge texel that differs from the head front fails; parts: hip and back sheath parents, and a hip
sheath refusing a `weapon_back` already used by a part.

### Open (for the tasks named)

- **A2 (HeroFace)**: the blink frames (half, closed) carry rest brows, so a blink over angry, sad, focused or smug eyes
  (4.6 lists them) shows rest brows for its 4 ticks. Either accept it (it reads as a flinch) or blink only over open
  and glowing eyes; decide with the screenshots.
- **G3**: `sash` is a child of `chest` while the starter obi sits at the waist (y 16.25-18): keep chest rotations
  relative to the waist small (a few degrees) or the obi drifts off the waist; twists belong on `waist`.
- **G2**: the eyes plane keeps the 0.05 px offset over its full-width hole; looking from well above or below, its top
  and bottom edges can show a sub-pixel sliver of the head's inside. Check it in the fidelity screenshots up close.
- **G6a-c**: author new cut-out parts knowing that caps and narrow faces now follow the side faces' cut (a thick cube
  with different cuts on its front and side faces gets their intersection on its caps).

---

## Style pass: rig v2 and the style system (2026-11-12)

The owner sent a style reference ("something that feels blocky but has different body forms": three sizes of one
fox-eared shrine maiden standing on grass, and a close-up of her face). It is used for **style only** (proportions,
volume, construction, shading); none of our heroes copies that character. This pass replaces rig v1 with **rig v2**
and adds the parts and painter features the style needs. HEROES_V2.md 3.6, 4.3, 4.6, 12.5, 13.2-13.5 and 15.2 (G1s)
were updated to match. Where this section and the G1 section above differ, this one holds.

Previews (committed): `docs/heroes-v2/previews/style/`
- `lineup_three_forms.png` (one character per form, shot like the reference), `lineup.png` (all six fixtures),
- `face_closeups.png` (every fixture's head in six moods, a blink and a 3/4 view),
- `bases_v2.png` (the six bases, mannequin palette), `style_<id>.png` (front, 3/4, side, back, blink, weapon drawn),
  `style_<id>_frames.png` (the 12 moods and every eye, mouth and cheek frame), `test_rig.png`, `fallback.png`,
  `test_rig_texture.png`. Regenerate with `python3 tools/herogen/herogen.py previews`.
The G1 previews in `notes/previews/g1_*.png` show rig v1 and are kept as history.

### Style guide

What the reference does, as rules our generator follows (all numbers are rig v2 pixels; "texels" are texture pixels).

**Proportions.** Blocky cube bodies with anime chibi-leaning proportions: the head is big and the same 10x10x10 cube
on every form, so the face and every head part fit everywhere; the forms differ below the neck.

| Form | Crown | Head-heights tall | Head share | Legs | Torso | Neck | Arm reach | Feel |
|---|---|---|---|---|---|---|---|---|
| chibi | 23 px | 2.3 | 0.43 | 7 (thigh 3, shin 2.5, foot 1.5) | 5.5 | 0.5 | hand bottom at the hip line | a small child: the head is as wide as shoulders and sleeves together |
| teen | 28 px | 2.8 | 0.36 | 11 (5.5, 4, 1.5) | 6.5 | 0.5 | hand at the upper thigh | petite, head about a third |
| adult | 33 px | 3.3 | 0.30 | 14 (6.5, 5.5, 2) | 8 | 1 | hand at mid thigh | tall, long legs, head a bit under a third |

The reference's tall form has a smaller head share (about 0.25 counting the hair); we keep 0.30 so the face reads at a
distance, as the owner asked for big heads. Hair adds 1-2 px of volume above the head and 0.5-2 px around it, ears
3-5 px more. Hitbox: the form's height x 15/16 (adult 1.93 blocks, under the 1.98 cap).

**Builds.** Female and male differ in widths (male: broader chest and shoulders, thicker arms and legs; female:
narrower waist, wider hips than waist, a small bust cube on teen and adult). Each base has slim and large builds that
override widths only. Arms hang flush with the chest; legs stay inside the hips.

**Face (double density).** The head front is 20x20 texels (0.5 px each). Layout, top to bottom: forehead rows 0-3
(covered by the fringe), eyes plane rows 4-13 (brows 4-5, skin 6, lash 7, eye body 8-13), cheeks plane rows 14-15,
mouth plane rows 16-19 (the mouth line on row 17, a small anime mouth 2-4 texels wide). No nose.

**Eyes.** Big: 5-7 texels wide and 5-7 tall with the lash (about 3 x 3.5 px on a 10 px face, a third of its width
and height), 2-6 texels apart. Structure of an open eye, outer corner first:
- a heavy dark upper lash across the eye (the hair's hue, never black); female faces (lash weight 2-3) add an outer
  corner texel and an upward flick, male faces (weight 1) a plain line; a soft lower-lash texel at the outer corner;
- the white only beside the iris, shadowed (a cool grey) in the upper half under the lash;
- the iris 3-5 texels wide, its ramp running from tone -2 directly under the lash to +2 on the bottom row (dark top,
  light bottom: the anime gradient), a 2x2 pupil (1 texel when surprised, a slit for cat eyes);
- highlights: a 2x2 pure-white highlight in the upper outer iris (it may overlap the pupil), a small second highlight
  (white mixed into the light iris) low on the inner side. Pure white is kept for highlights only.
Brows are thin (1 texel), rest on row 1 so a skin row separates them from the lash, and move for expressions (raised
to row 0, inner end down to row 2 when angry, outer end down when sad). The fringe may cover them; strands leave gaps.
Blink frames keep the brows. The 12 moods and the frame table are unchanged from G1.

**Hair (built from layered cubes, never painted on the head).** Every style uses the hair kit, all head-local and at
double density:
1. a **cap** open at the front (faces up, south, east, west), 0.5 px outside the head, from y 3-4.5 up to 10.75;
2. a **stepped dome** on top (one or two cubes 1 px narrower each) and, for messy styles, tufts of different heights,
   so the top is a mass, not a flat lid;
3. a **fringe block** in front of the forehead (z -6..-5, y 8.5..11.25) and 3-5 **strand cubes** over it (2-2.5 px
   wide, 0.75-0.9 px deep, alternating depth, tapered or jagged tips) whose tips stop between y 6.5 and 8.25: the
   bangs cover the forehead and partly the brows, **never the lash or the eyes** (validated: no cube face in front of
   the face below the lash line within |x| < 4.5);
4. **side locks** hanging outside the face (|x| >= 4.75) in front of the ears, past the chin and over the shoulders
   (1.75 px thick, tapered, a thin face-framing strand beside them), plus **side cover** slabs over the head's sides;
5. **back cover** behind the head, then the style's long pieces: layered back strands at two depths (long hair),
   ponytail or twin tails on bone chains (with a tie and ribbon), an **ahoge** (a thin peaked cube on its own bone);
6. lengths are params clamped so long hair stays above the ankles of a chibi (`max(-length, 2 - head_y)`).
Touching hair cubes of one colour are one region: no outline where a strand rests on the cap, only at free tips.

**Ears and tails (volume).** Ears are thick cubes (1.5 px) with a `peak` cutout (full width at the base, pointed top),
tilted 10 degrees outward, with a thin inner-ear plate in the alt colour (pink, cream) in front; round ears use
`round_top`. The narrow sides of a cut-out cube follow its front silhouette. Tails are 3-bone chains: slender (1.5 px,
a curl and a lighter tip) or voluminous (a narrow root, a 4.5-5.5 px body that sweeps up, a lighter tapered fur tip).

**Outfits (volume).** Clothes are painted on the body and then given volume with cubes on the right bones:
- **wide sleeves**: a box around each forearm on the `sleeve` bone, 1.75-2.75 px wider than the arm outward (widest on
  the small forms, so a chibi's sleeves span the head's width), 1.5 px in front and 2.75 px behind it, from mid upper
  arm to the wrist, open at the bottom so the hand shows under it, a trimmed cuff, a hanging pouch at the back; puffed
  upper sleeves 0.75 px around the upper arm;
- **flared skirts and hakama**: stacked tiers on the `skirt` bone, each wider and deeper than the one above (+1, +2, +3
  px), open at the bottom, pleats (tone), a trimmed hem; the ledge on top of each tier is painted (it catches light);
- **coat tails and tabards**: flat panels on `skirt_f/b/l/r` (two split tails at the back), trimmed;
- **collars**: a V neckline painted with `vee` (an inner layer edged in trim), a standing back collar, lapels, a hood
  bunched behind the neck, a scarf ring up to the chin with tails on their own bones (one fluttering back);
- **sash and bow**: the faction sash (tinted, greyscale) as a wide obi with a big bow (knot, two loops, two tails);
- **armour**: shells 0.35-0.5 px around the body part (breastplate over chest and bust, fauld at the waist, gauntlets,
  greaves with knee cops), layered pauldrons (two stacked plates, each wider than the arm), trimmed edges;
- a fur mantle and a two-segment cape for knights.

**Footwear.** A shell 0.3 px around the foot (round shoes in lacquer with a trimmed strap band, sabatons, boots), a
boot shaft over the shin with a turned-down cuff, socks painted on the shin with a folded cuff at the knee; white tabi
with dark sandals by painting socks white and the shoe shell dark.

**Shading.** Five-tone OKLCH ramps per colour (unchanged), with clear light and dark steps: top faces +1, the hero's
right side -1, bottoms -2; every hair strand lit in its top fifth (+1), darker in its lower third (-1) and outlined at
its tip (-2), with strand lines every 4 texels and short highlight streaks; cloth outlined at free hems, softer where it
rests on the body; trims in a contrasting slot. Palettes are saturated, with a value contrast between neighbouring
pieces (hair against the face, the top against the bottom), so each piece reads as its own block from a distance.

### What was built

- **Rig v2** (`herosrc/rig/rig_v2.json`): pivots are expressions over the base dims (heights per form); the head
  facts (`head.size` 10, `front_z` -5); three new optional bones (`ahoge`, `sleeve_r`, `sleeve_l`; 64 in the full
  tree, 33 required, budget 72); `sash` under `waist`; the face block with head-local plane origins and the eye rows.
  Rig v1 and its six bases were removed (no hero content used them; git keeps them).
- **Bases**: `_template.json` (body cubes as expressions) and six `<form>_<f|m>.json` files with standard dims and
  `builds.slim` / `builds.large` width overrides (`rig.load_base(base_id, build)` refuses height overrides).
- **Hero format**: `art.form` (`chibi`, `teen`, `adult`; default adult) with `art.base` and `art.build`;
  `visual.json` gains `form`, `build`, `height_px` and `head_y` (A1 sizes the hitbox from them).
- **Texture**: 256x128 (256x256 when needed). Fixed regions: base 0,0,64,80; eyes 64,0,80,30; head front 144,0,20,20;
  mouth 64,30,64,4; cheeks 64,34,64,2. All eight current models fit 256x128.
- **Face** (`face.py`): constants checked against the rig by `check_layout` (also run by `herogen check`); new eye
  templates and drawing (see the style guide); cheeks with hatch lines; the 20x20 head front and its holes.
- **Parts format**: part `offset` and `density`; cube `density`, `shade`, `skip_if_empty`; paint op `vee`; cutouts
  `tri_down`, `peak`, `round_top`; silhouette-consistent cutouts (narrow faces follow the broad ones).
- **Painter**: hair `vertical` shading, strand lines scaled by density, front-to-back strands on top faces, hair as
  one region; the v1 crown ring is gone (it read as a stripe across the head).
- **Validator**: the clear-face rule is face-based and head-relative (a cap open at the front may enclose the head).
- **Parts library** (30 parts): see HEROES_V2.md 13.3, "Built for the rig v2 style set". The 11 G1 starter ids are
  kept and rewritten for rig v2 (the test rig still uses them).
- **Style fixtures** (`herosrc/style/*.hero.json`, original characters, one per base, built in memory for previews and
  tests, never written to `src/main/resources`): Tansy (chibi_f, cat ears and tail, twin tails, wide-sleeved kimono
  top, flared skirt, sash bow), Tobin (chibi_m large, round ears, fluffy tail, wide sleeves over hakama, tabi), Rowan
  (teen_m, messy hair, open jacket with hood, striped scarf, boots), Wren (teen_f slim, long layered hair with an
  ahoge, short jacket, pleated skirt, capelet), Maren (adult_f, high ponytail, breastplate, layered pauldrons,
  gauntlets, coat tails, fur-mantled cape, greaves, sword), Garrick (adult_m, ponytail, wide-sleeved coat over hakama,
  sword). They use 54-76 cubes and 42-52 bones. `style_preview` in a fixture holds its preview faction tint and
  lineup order (not part of the hero format).
- **Previews**: the renderer takes a camera `pitch`; sheets on a light background; a sky-and-grass lineup with ground
  shadows; face close-ups. `herogen check --style` and `stats --style` include the fixtures.
- **Tests** (105, was 87): rig v2 forms, builds and heights; every part on every base passes the geometry checks and
  stays above ground; head parts are form-independent; every hair style is layered, at 2x, covers the forehead; the
  fixtures cover all six bases, pass every check and are deterministic; offset, density, `vee`, `skip_if_empty`, ear
  cutouts and the hair region; the v2 eye structure (lash, iris gradient, pupil, highlight) and the face layout.

### Decisions (each deliberate)

1. **One head size for every form** (10 px). Different head sizes would need different face layouts or non-square
   texels; the forms differ in the body, which is also how the reference's three sizes read.
2. **Head parts at double density, body at 1x.** The reference draws face and hair finer than the clothes; hair next
   to a 2x face at 1x looked coarse. The texture stays 256x128 for every model so far.
3. **Rig v2 replaces v1** instead of living beside it: nothing shipped uses v1, and two face layouts would double the
   face code and the A2 renderer's cases.
4. **Builds as width overrides** of six form/family files (18 bodies) instead of 18 files, so a form's heights cannot
   drift between builds.
5. **The hair cap is open at the front** and the clear-face rule checks faces, not bounding boxes.
6. **No hair crown ring**: per-strand vertical shading gives the reference's lit-top, dark-tip look without a stripe.
7. **Style fixtures are not heroes**: they live in `herosrc/style/`, are never written to the mod's resources, and do
   not count against the roster.

### Open items

- **A1 (HeroEntity)**: size the hitbox from `visual.json` `height_px` (and scale); decide a floor for chibi heroes
  (PROPOSED 1.2 blocks) so the small forms are not much harder to hit; eye height from the face (head_y + 5.5).
- **A2 (HeroRenderer)**: frame shifts read `visual.json` regions (now 20x10 eyes, 16x2 cheeks, texture 256 wide).
- **G2**: confirm the v2 face planes and the 2x hair in the in-game screenshot; the preview renderer's lighting is
  still the block-style stopgap.
- **G3 (animations)**: author on the adult and check on the chibi: rotations transfer, root offsets are larger
  relative to a chibi; long hair and wide sleeves on the head and forearm bones swing with them (secondary chains are
  declared: twin tails, ponytail, sleeves, scarf tails, skirts).
- **G6a-c**: grow the library on the kit (horns, wings, hats need head-local authoring with `offset`); `lint` as a
  command (the test `test_every_part_on_every_base` is the miniature); more hair styles (braids, buns, side tail,
  hime cut), more ears (fox, wolf, rabbit), robes, more armour.
- **Owner review**: the proportions in the table above are PROPOSED; the lineup and the close-ups are the material to
  decide on (head share of the adult, chibi height, eye size).

---

## Style pass 2: rig v3, the strand hair kit and the face system (2026-11-12)

The owner's feedback on the rig v2 previews: "the head feels too big and their faces [are] quite similar to each
other". Inputs: the owner's three screenshots (three body forms on grass, a face close-up, a fox girl playing a violin
whose body is long against its head, with a hair mass of many strand cubes, a big layered tail and a layered outfit),
the measurements of the models behind them (`reference-study.md`) and of five other creators' models
(`reference-survey.md`). Those models are used **only as measurements and techniques**: nothing of their geometry,
textures, animations or character designs is in the repository or the generator, and the style below does not imitate
any one creator. It takes the screenshots' feel (small face in a big hair mass, long body, dense layered detail) and the
surveys' ranges (head shares, eye placement, cube budgets, construction methods). Where this section and the earlier
ones differ, this one holds. HEROES_V2.md 3.6, 4.3, 4.6, 4.9, 11, 12.5, 13.2-13.5, 14.2 and 15.2 (G1s2) were updated
to match.

Previews (committed, regenerate with `python3 tools/herogen/herogen.py previews`), `docs/heroes-v2/previews/style/`:
- `lineup.png` (the six fixtures on grass, one scale, heights in blocks), `lineup_three_forms.png` (one character per
  form, shot like the owner's reference);
- `face_lineup.png` (the six calm faces side by side, front and 3/4, with the features that differ),
  `face_closeups.png` (every head in six moods, a blink and a 3/4 view);
- `style_<id>.png` (turnaround: front, 3/4, side, back, 3/4 back, a blink, the far level of detail with the `fine_`
  bones hidden, the weapon drawn), `style_<id>_frames.png` (the 12 moods and every frame);
- `bases_v3.png` (the six bases), `test_rig.png`, `fallback.png`, `test_rig_texture.png`.
The previews show the idle stance the animations will use (arms 9 degrees out, `previews.IDLE_POSE`); the geometry's
rest pose keeps the arms straight down.

### What changed, in one paragraph

Rig v3 keeps the 10 px head, its 20x20-texel face and every bone name, makes the bodies 1.5-1.6 times longer and
draws the whole model at a render scale of 0.56, so the head with its hair is about a quarter of an adult's height
(the references' 0.22-0.25) and about the size of a vanilla player's head in the world. The eyes moved down and apart
(eye centre 0.30 of the head height above the chin, was 0.45; default gap 6 texels, was 2-4). Hair is a mass of 68-89
mostly rotated strand cubes from generators instead of 20-30 untilted slabs; ears are stacked slabs; big tails are five-bone
chains of overlapping rolled boxes; outfits gained bell sleeves, panel skirts, frills, collars, bows, belts, an apron
and armour plates. A face system gives every hero its own face (shape family, iris, pupil, highlights, lash, brows,
mouth, blush, marks, glasses or an eye patch), and the six fixtures were redesigned so no two share an eye shape or a
mouth. Cubes per hero: 136-224 (was 54-76), budget 300.

### Proportions (rig v3)

| Form | Head top (px) | Chin | Hip (legs) | Waist / chest / chest top | Shoulder / elbow / wrist / hand bottom | Head share with 3 px of hair | World height at 0.56 |
|---|---|---|---|---|---|---|---|
| adult | 52 | 42 | 24.5 (0.47) | 27.5 / 31 / 39.5 | 38.5 / 31 / 23 / 19.5 (mid-thigh) | 0.24 (references 0.22-0.24) | 1.82 blocks (a player is 1.8) |
| teen | 45 | 35 | 20 (0.44) | 22.5 / 25.5 / 32.5 | 31.5 / 25.5 / 19.5 / 16.5 (upper thigh) | 0.27 (references 0.25) | 1.58 blocks |
| chibi | 35 | 25 | 12 (0.34) | 14 / 16.5 / 23.5 | 22.5 / 18 / 14 / 11.5 (hip line) | 0.34 (references 0.32-0.33) | 1.23 blocks |

- Measured on the fixtures (hair top from the hair cubes): adult 0.235, teen 0.27-0.30 (the spiky style is tall),
  chibi 0.34-0.37. The reference screenshot gives 0.22 / 0.25 / 0.32 for its tall, mid and chibi forms.
- **Why keep the 10 px head** (the survey's route a) rather than the study's 8 px head: the 20x20-texel face has room
  for the face system's shapes (the study's 16x16 would cut the eye body to 5 rows), and every head-local number in the
  parts stays. **Why one render scale for every form** (the survey proposed 0.71 / 0.7 / 0.63): the same texel size and
  the same head size on every form, as in the owner's three-forms screenshot, and a lineup at one model-pixel scale is
  already to world scale. 0.56 has two decimals because generated JSON keeps three.
- Widths grew with the bodies (the references' limbs are thick: legs about 0.35-0.5 of the head width). Standard
  builds, female / male: adult chest 7.5 / 9, waist 6 / 7.5, hips 8 / 8.5, arm 3 / 3.5, leg 3.75 / 4; teen chest
  6.5 / 7.5, arm 2.75 / 3, leg 3.25 / 3.5; chibi chest 6 / 6.5, arm 2.5 / 2.75, leg 3. Slim and large builds as before
  (widths only). Arms still hang flush with the chest, legs inside the hips.
- Necks are 1.5-2.5 px visible; hands 2.5-3.5 px tall "mittens" at the arm's width; toes 1-1.5 px.
- `visual.json` gains `render_scale`; the hitbox is `height_px x render_scale x scale / 16` blocks (A1).

### Face (rig v3 layout and the face system)

Face rows (0.5 px each, top = 0): 0-6 forehead (under the bangs), 7-16 the eyes plane (brows 7-8, skin 9, lash 10,
eye body 11-16), 17-19 the mouth plane (8x3, the mouth line on row 18), 17-18 the cut-out cheeks plane. The bangs may
reach down to head-local y 5.5 (they may cover the brows, never the lash or the eyes). Everything is drawn over one
**skin layer** (skin, jaw shading, marks): the head front is that layer with holes, the eye frames start from its rows
7-16, the mouth frames from rows 17-19, so marks cross planes without seams and the mouth plane's edge columns always
equal the head front.

| Lever | Values | Notes |
|---|---|---|
| `eyes` (shape family) | `round`, `wide` (big, sparkle highlights), `almond`, `sharp` (tsurime), `droopy` (tareme), `narrow`, `sleepy` (jitome, a heavy lid hides the iris top), `heavy_lid`, `cat` (slit pupil), `small`; aliases `tsurime`, `tareme`, `jitome`, `sparkly` | each shape is a per-column opening (top and bottom row), so a raised or lowered outer corner, a sloped lash and a lidded look come from the shape; `width` 5-7 stretches or squeezes it |
| `iris_style` | `gradient`, `two_tone` (the reference look), `ring`, `solid`, `glossy` | `iris` colour; `iris2` for a different left eye (heterochromia) |
| `pupil` | `round` (2x2), `dot`, `slit`, `none`, `star`, `ring` | |
| `highlights` | `one`, `two`, `sparkle`, `inner`, `none` (1 and 2 still read) | |
| `lashes`, `flick`, `lower_lash` | weight 0-3; outer flick `up`, `down`, `none` | female default 2 / up / lower lash, male 1 / none |
| `spacing` | 2, 4, 6 (default), 8 texels between the inner corners | eyes may reach the face edges |
| `brows` | `thin_arched`, `straight`, `straight_thick`, `worried`, `bold`, `angled` (stern), `short` (round dots), `none` | moods move them as before; blinks keep them |
| `mouth`, `fang` | `small`, `soft`, `wide`, `cat` (ω), `stern`, `fang`, `pout`, `smirk`; fangs `left`, `right`, `both` | each style has its own rest mouth and its own 8 frames |
| `blush` | `true` / `false`, or a style: `soft`, `hatch`, `dots` | |
| `marks` | `freckles`, `beauty_mark` (at the eye or the mouth), `scar` (eye, cheek, nose), `face_paint` (stripes, tear, dots, chevron; any palette slot), `stubble`, `bandage`; `side` r, l or both | one-sided marks make the face asymmetric |
| wear | `glasses` (`round`, `square`, `half`; `glasses_color` a palette slot), `eyepatch` (r or l) | drawn into every eye frame, so blinks and moods keep working behind them; the patched eye shows nothing in any frame |

- All 12 eye frames, 8 mouth and 4 cheek frames and the 12-mood table are unchanged in meaning. The closed eye is now
  the reference's "dropped lash bar": a dark line low across the eye, doubled by a soft row for lash weight 2 or more.
- The validator's mirror check is skipped for asymmetric faces (`spec.asymmetric`: an eye patch, `iris2`, a one-sided
  mark).
- `face.signature` and `face.distinct` compare two faces feature by feature (the iris hue counts when 40 degrees or more
  apart). The six fixtures differ pairwise in at least five features and share no eye shape and no mouth
  (`test_style.test_faces_are_distinct`).

### The six fixtures (original designs, each with its own face and silhouette)

| Fixture | Base | Silhouette | Face |
|---|---|---|---|
| Tansy | chibi_f | twin tails tied high with gold ties, stacked cat ears, a curling five-bone cat tail, bell-sleeved kimono top with frilled cuffs, panel skirt with a frill hem under a frilled apron, sash bow | `wide` eyes with sparkle highlights, lash 3 with an up flick, no brows (bangs), `cat` mouth with a fang, hatched blush |
| Tobin | chibi_m large | round fluffy bob that curls under, round ears, a big five-bone fluffy tail, kimono top over a hakama, tabi and sandals | `droopy` eyes, two-tone iris, `short` brows, `soft` mouth, freckles, dot blush |
| Rowan | teen_m | spiky messy hair, open jacket with lapels, hood and flared hem, striped scarf with a tail blowing back, belt and pouch, cuffed boots | `sharp` eyes with a ring iris, `angled` brows, `smirk` with a fang, a plaster on the left cheek |
| Wren | teen_f slim | long layered hair to the back with a curled ahoge, sailor-collared short jacket with a ribbon bow, pleated skirt, capelet, thigh socks | `sleepy` (jitome) eyes behind round gold glasses, a down flick, `pout`, a beauty mark under the left eye |
| Maren | adult_f | high ponytail, breastplate with ridge and tassets, layered pauldrons, gauntlets with flared cuffs, coat tails, long cape with a fur mantle, greaves with knee cops, sword | `narrow` eyes 8 texels apart, two-tone iris, thick straight brows, `stern` mouth, crimson war paint stripes |
| Garrick | adult_m | low ponytail with a side-swept fringe, wide-sleeved kimono coat over a long hakama, sword | an eye patch over the right eye, a `heavy_lid` left eye with a slit pupil and a scar through it, `bold` brows, `wide` mouth, stubble |

### Hair kit 2 (strands, `gen.py`)

Every style is built from the same layers, head-local, 1 texel per px (the strands are the detail now, so double
density is kept for the face only):
1. **cap** open at the front, 0.3 px outside the head (y 1.5 up to 10.5), and a **stepped dome** (three slabs to
   y 12.5): the far silhouette, not `fine_`;
2. **crown**: 16 strands on a ring of radius 3.5 at y 12.1, 3 px wide, 4.5-5 px long, flared about 40 degrees, so the
   top reads round and the mass is 14-15 px wide (1.4-1.5 head widths; the reference's 1.9 is for a 7 px head); a
   **crown top** ring of 7 and a **whorl** of 8 strands lying almost flat (74 degrees) from the centre hide the dome's
   steps;
3. an **inner** ring (darker, short flare) and a **mid** ring (5.75 px radius, 2.75 px strands, two depths) for the
   sides and back, length per style;
4. a **fringe base** slab (jagged) and 7-8 **fringe** strands (1.75-2 px wide), shaped by `fan` (positive parts them,
   negative draws them together), `zigzag` (alternating tilt) or `lean` (side-swept); their tips are clipped above the
   eyes by the generator (`clear_face`), so a fringe can never cover the lash;
5. **side locks** (1-2 per side, tapered, past the chin) and side strands;
6. the style's own pieces: long **back** layers at two depths (9 strands, tapered), a **ponytail** or **twin tails** as
   a ring of strands round the tie on their own bone (plus one core box for the far view), an **ahoge** as a two-bone
   chain (`when: ahoge`).
Strands are painted flat: lit top fifth, a darker lower third and a dark tip; no painted strand lines (the cube edges
are the strands); a per-strand `tone` cycle gives the layered look. Hair cubes: 68-89 per style, 90% of them
rotated and on `fine_` bones.

Styles: `long_layered`, `twin_tails`, `messy_short`, `short_crop`, `bob`, `high_ponytail_long`, `low_ponytail` (new).

### Ears, tails, outfits

- **Ears**: `cat` is seven 1 px slabs, each narrower and shallower than the one below, a dark tip, a pink inner plate
  and a light tuft, tilted out and back as one rotated `group`; `round` is a rounded disc with a cap and an inner plate.
  They stand on the hair's top corners.
- **Tails**: five-bone `chains` (rig v3 adds the optional `tail_4`, `tail_5`). `fluffy`: per segment a main box and
  two boxes rolled 28 degrees either way about the segment (`fine_`), cross-section up to 7 x 6.5 px, flat bands
  lightening toward a tapered light tip; `cat_long`: a slender curl with a light tip. Chain boxes are painted flat with
  no hem line except at the tip, and every rotated face takes its baked light from the direction it faces after the
  rotation (`paint.face_light`), so an upturned segment is not lit from below.
- **Outfits** (all expressions over the base dims, so they fit every base):
  - `kimono_wide`: bell sleeves of 8 panels round each forearm on the `sleeve` bones (flare 15-25 degrees by
    `sleeve`), frill rings at the cuffs, puffed upper sleeves, crossed lapel slabs, the painted V collar;
  - `jacket_open`: lapels, layered collar, a hood bunched behind the neck (three rotated slabs), cuffs, pockets, a
    flared hem of panels open at the front;
  - `short_jacket`: a sailor collar (back flap and two front flaps meeting in a V), a ribbon bow with tails, cuffs, a
    short flared hem;
  - skirts as rings of hinged panels on `skirt_f/b/l/r`: `flared_skirt` (12 panels and a frill hem ring),
    `pleated_skirt` (14 knife pleats: alternate tones and depths), `hakama` (16 panels to the shins, a back board),
    `coat_tails` (8 panels open at the front);
  - accessories: `sash_bow` (obi, knot, loops up and out, tails splayed back), `sash_obi`, `scarf` (two tails, one
    blowing back), `belt` (buckle, pouch), `apron` (new: a frilled panel on `skirt_f` and a back bow);
  - armour: `breastplate` (ridge, gorget, waist plate, 8 tassets), `pauldrons` (a cap and two lames, each tilted
    further out), `gauntlets` (shell, hand plate, a flared cuff of plates);
  - back: `cape_long` (fur mantle ring, two hinged panels hanging back), `cape_short` (a ring of panels round the
    shoulders, open at the front, with a clasp);
  - legwear: `socks_shoes` (cuffed socks, round shoes with a sole and a strap), `boots` (cuff, sole), `tights_boots`,
    `thigh_socks` (new), `tabi_sandals` (new: wooden soles, straps), `greaves` (knee cops, sabatons);
  - headwear: `halo_ring` (moved above the new hair top), `ribbon_bow` (new).
- The library has 37 parts (was 30). Every part on every base passes the geometry checks (`test_every_part_on_every_base`).

### Parts format additions

- **Generators** (`gen.py`): `strands` (line or arc paths; `count`, `width`, `depth`, `length` lists, `jitter`,
  `flare`, `fan`, `zigzag`, `lean`, `layers`, `drop`, `tone`, `dir` up or down, `bones_by_angle`, `mirror`,
  `clear_face`, default `fine`), `chains` (bones along a polyline, a box per segment, rolled extras, `taper_last`,
  `cutout_last`, `slot_last`), `groups` (cubes sharing one rotation and pivot), and `when` on any of them.
- Cube `outline` (false: no hem row, for a segment that continues). Named part cubes are now prefixed with the part id
  (`<part>#<name>`), so two parts may use the same short names.
- **Rotations in one place** (`rot.py`): GeckoLib applies a file rotation (rx, ry, rz) as Rz(-rz) Ry(ry) Rx(-rx) in
  file coordinates; `file_angles` turns any wanted matrix into file angles. Positive x tilts a hanging piece's free end
  back (+z), negative x forward; for a hanging piece on the hero's right (-x), positive z swings its end inward and
  negative z outward (mirrored copies flip y and z). `test_parts.GeneratorTest` and the validator test pin this.

### Validator and previews

- The clear-face check now rotates corners with GeckoLib's sense of rotation (`rot.file_matrix`; the old check used the
  opposite sign for x and z rotations, harmless while only a few cubes were rotated).
- New budget `lod_cubes` (130): cubes outside `fine_` bones, what is still drawn beyond the fine distance.
- Asymmetric faces skip the mirror check (`check_built(..., asymmetric=...)`).
- Previews: `ortho.render(..., pose=...)` (file degrees added to bone rotations; normals follow), the far view (`fine_`
  hidden), `face_lineup`, heights in blocks.

### Cube budget (PROPOSED) and why

| Budget | Value | Validator |
|---|---|---|
| cubes per hero | at most **300**, typical **130-230** (the fixtures: 136-224) | error |
| cubes outside `fine_` bones | at most **130** (the fixtures: 54-92) | error |
| bones | at most **100** (the fixtures: 51-74) | error |
| texture | 256x128 (every fixture fits, 9-18k texels painted) or 256x256 | error |

Why not the reference's 300-500: up to about 50 heroes can be on screen and GeckoLib transforms every vertex on the CPU
each frame. With HEROES_V2 11's unmeasured estimate (0.4-1.1 microseconds per visible cube), 50 heroes at 450 cubes
would cost 9-25 ms per frame, while 12 near heroes at 230 plus 38 far ones at 90 (their `fine_` strands, frills and
rolled tail boxes hidden beyond 20 blocks) cost about 6,200 cube-renders, 2.5-6.8 ms, the same band as the study's
proposal and about 1.8 times rig v2's worst case for about three times the detail where the player looks. The survey
shows multi-character packs at 70-250 cubes and single-avatar showpieces above 300; the cap leaves room for leaders
and bosses up to 300. The per-cube cost is still to be measured (HEROES_V2 14.7); if it is high, tighten the LOD rule
(fewer heroes at full detail), not the art.

### Decisions (each deliberate)

1. 10 px head with one render scale (above), not an 8 px head or per-form scales.
2. Expressions stay texture frames on three planes (12 + 8 + 4 frames from 3 cubes); the references' cube faces would
   cost 46-118 cubes per hero. Their look is borrowed instead (low wide eyes, two-tone iris, dropped lash bar).
3. Hair at 1 texel per px (was 2): the strands carry the detail, the 2x texels went into a 4x larger hair surface.
4. No UV island sharing (the study's suggestion): every fixture fits 256x128 without it; revisit when a hero needs
   256x256.
5. Previews show an idle stance (arms 9 degrees out); the rest pose stays straight so animations key from neutral.
6. The six fixtures keep their names and roles but were redesigned (new hair, faces and outfits); none copies a
   reference character.

### Open items

- **Owner review**: the head shares (0.24 / 0.27 / 0.34 with hair), the render scale (adult 1.82 blocks, a teen 1.58,
  a chibi 1.23), and the face system on the six fixtures.
- **A1**: hitbox from `height_px x render_scale x scale`; eye height (head_y + 3) x render_scale.
- **A2**: the renderer scales the whole model by `render_scale`; items at `item_r`/`item_l` need a 1/0.56 counter-scale
  to keep vanilla tool size; frame regions changed (mouth 8x3 at 64,30; cheeks at 64,33), read them from `visual.json`.
- **G2**: confirm in game that cube rotations on all three axes render as `ortho` shows (the rotation convention is
  pinned to GeckoLib's code, the in-game screenshot is still to come), and the far view's look at 20+ blocks.
- **G3**: the idle animation sets the arms' A-pose; chains to key or sway: twin tails (`twin_r`, `twin_l`), ponytail
  (`tail_hair_1/2`), ahoge (`ahoge`, `ahoge_tip`), tails (`tail_1..5`), ears, sleeves, skirt panels, scarf tails, cape.
  Sway wrapper bones (study 11.7) are not added yet.
- **G7**: a distinctness NOTE from `face.distinct` across a batch of heroes; a second LOD tier (study 11.5 LOD2) if the
  measurement asks for it.

---

## Art direction pass 2: review of style pass 2 against the references (2026-11-12)

An art-director review of the style pass 2 previews (commit 6cc6cba). The previews were put side by side with the
owner's three screenshots and the ranges in `reference-survey.md` and `reference-study.md`, and judged the way the owner
would: head size, distinct faces, hair density and layering, ear and tail volume, outfit detail and silhouette, shading
and colour appeal, distinct body forms, glitches. Generator fixes followed, with two re-render rounds after the first
fix list. As before, the reference models were used only for measurements and techniques. Nothing of their geometry,
textures, animations or designs is in the repository. Where this section and the earlier ones differ, this one holds.

Before and after: `docs/heroes-v2/notes/previews/artdir2_before_after.png` (the three-forms shot of 6cc6cba next to the
new one). The style set in `docs/heroes-v2/previews/style/` is regenerated.

### What the review found (style pass 2 as committed)

| Area | Finding | Weight |
|---|---|---|
| Hair, crown | The top of every head was a radial pinwheel. Eight near-flat "whorl" strands met at one point, with a dark hole in the middle, over a second ring of 7 strands and three stepped dome slabs. At the usual camera pitch this read as a spider web, or a pot lid, with seams. The references' crowns read as a mass of chunky strands. | high: every hero, every view |
| Hair, shading | The hair was nearly flat. The 0.08-per-step ramp, the blue-violet shadow shift that loses chroma, and a lit band only on the top fifth of each strand left purple, red and brown masses with little strand-to-strand variation. The references' hair is their most striking element: a pale lit root, a gold middle and orange-red tips. Black and navy hair read as one dark blob. | high |
| Hair, nape | The cap starts 1.5 px above the chin, and the head's back face was skin. Every short or tied style showed a bald skin strip at the nape from the back and the side, with jag teeth over it. | high (looks like a glitch) |
| Head size | The visual head share (hair top to chin over the hair top), measured on the fixtures, was 0.24 (adult), 0.28-0.29 (teen) and 0.35 (chibi). On the owner's screenshot the same measure is about 0.20-0.22, 0.25-0.26 and 0.32-0.35. The heads were still a little big, mostly because the hair rose 3.4 px over the head, and up to 4.5 px with Rowan's spikes. | medium |
| Spiky hair | Rowan's six up-spikes were 2.25 px boxes 2-3 px tall. They read as wooden crates on his head. | medium |
| Faces | The faces are clearly distinct (feedback point 2 is met): every pair differs in 5+ features. Defects: the stubble was every other texel on the chin row, which read as stitches; the plaster sat under the outer eye corner, 3 x 2 texels, and read as a tear streak; the cat mouth (ω) used the light mouth line and only diagonal steps, so it read as loose dots. | medium |
| Skin | Fixture skins at OKLCH L 0.85-0.91 and hue 57-61 looked beige-grey on the front face, which the renderer draws at 0.8, as entity lighting does at about 0.74. The references' skin is a light pink-peach. | medium |
| Ears | Each stacked 1 px slab of a cat ear got a lit +1 top and a -2 hem outline on its 1-texel sides, because the slab below is rotated and the painter cannot see it. From behind, the ears were striped "Christmas trees". | medium |
| Barcode glitch | Jagged slabs (back and side covers) cut their outermost columns. Their narrow side faces follow those columns, so the slab's side opened at the bottom and its toothed bottom face showed through from inside. In side views this appeared as a black-and-clear striped "barcode" at the hair hem (Wren, Rowan, Tansy). Cull-free entity rendering would show it in game too. | medium |
| Twin tails, back | The back of the twin-tail style was one flat slab with a jagged hem. With the nape fix it showed a row of dark "windows". | low |
| Outfits, silhouettes, forms | Bell sleeves, panel skirts, frills, collars, capes and armour give the references' layered look, and every silhouette differs. The three forms are clearly distinct. No change. | ok |
| Tails | Tobin's five-segment tail has the references' volume. Tansy's cat tail is slender by design. No change. | ok |

### Fixes (generator and parts)

1. **Hair ramp** (`palette.py`, `ramp(kind="hair")`, used for the `hair` and `hair_accent` slots, so ears and tails in
   the hair colour get it too):
   - Steps are 0.085 L down and 0.095 L up (cloth: 0.08).
   - Shadows keep their chroma, plus 6% per step. They lean toward orange-red (OKLCH hue 35) for warm hair (hues
     below 115 or above 345: gold, brown, ginger, red) and toward blue-violet for cool hair, 7 degrees per step.
   - Dark hair (L below 0.42) takes 0.06 down and 0.12 up, and its highlights turn toward a cool blue (hue 255, 18
     degrees per step) with at least 0.02 chroma per step. Black and navy strands then show a blue sheen.
   - Example: gold #D9B04C gives #B07300 / #C79021 / #D9B04C / #F0D277 / #FFF4C6, the references' gold-to-orange.
   - Cloth, metal and the other slots keep the old ramp.
2. **Strand gradient** (`paint.py`): the lit root now covers the top 30% of a hair side face (was the top fifth, and it
   now applies from 3 texels tall). The darker band covers the lowest 40% (was a third) of hair faces 4 or more texels
   tall. With the -2 tip row, a strand reads lit root, mid tone, darker lower part, dark tip.
3. **Parted crown**: the `whorl` (8 strands) and `crown_top` (7) were removed from all seven hair styles. A `part`
   group replaces them: 5 strands on each side of a centre line from the front to the back of the crown (mirrored),
   2.1 x 0.9 px, 5-5.75 px long, lying 66-72 degrees out over the dome, with the end strands dropped 0.3 px for a
   rounder profile. The top now reads as hair combed from a parting. The crown ring moved down to y 11.6 (was 12.1),
   the dome slabs to 11.0 and 11.6, and the part line sits at 12.35. Each style has 5 fewer cubes.
4. **Covered nape**: every hair style paints the head's back face, and the back of its side faces (head-local z 0.5 to
   5.5), as hair (paint ops). The temples in front of the ears stay skin. The hair's lower band and hem row give the
   nape a shadow line.
5. **Lower crown, smaller visual head**: the hair top is now 2.8 px over the head on every style except the spiky one
   (was 3.4). The visual head share is 0.234 (adult), 0.268 (teen, Rowan 0.283) and 0.339 (chibi), down from 0.242,
   0.277-0.293 and 0.349. This is within the references' range for teen and chibi and 0.01-0.03 above it for the adult
   (see the open item).
6. **Spikes** (`messy_short`): six 1.5 x 1 px spikes, 2.5-3.25 px long, tilted 52-60 degrees outward on the back
   two-thirds of the crown (angles -110 to 130). They read as cowlicks, not crates, and the hair top dropped to 3.8 px
   over the head.
7. **Faces** (`face.py`):
   - `stubble` is a solid soft shadow along the whole chin row and the jaw corners (`stubble_soft`, the skin and the
     stubble colour at 55%), a little darker on the chin. It is no longer every other texel.
   - The plaster (`bandage`) is 4 x 2 texels with a 2 x 2 pad, low on the cheek and inside the face. It reads as a
     plaster across the cheek.
   - The cat mouth (ω) uses a bolder line (`mouth_line_bold`: the skin shadow and the mouth's dark colour at 80%).
8. **Ears**: a new cube shade `mass` means no vertical shading, no hem outline, and a top face lit like the sides. Each
   cat-ear slab uses it, the slab under the dark tip is one tone darker, and the tip two. From behind, an ear reads as
   one stepped solid in the hair colour with a dark tip, as in the reference technique, and no longer as stripes.
9. **Jagged slabs keep their outer columns** (`paint.jag_depth`): the outermost column on each side is never cut (for
   faces 3 or more texels wide). The narrow side faces, which follow those columns, stay whole. The bottom face can no
   longer show through from inside, so the barcode is gone. The broad face is still jagged inside.
10. **Twin tails**: a `nape` group of 6 tapered strands, 6.25-7 px long, hangs over the back slab and its hem. This adds
    6 cubes.
11. **Skin** (the six fixtures' palettes, OKLCH): Tansy, Wren, Tobin and Maren are lighter and pinker (L 0.91-0.935,
    hue 45-52). Rowan is a little warmer (L 0.905), and Garrick stays the tan one (L 0.86). There is still a range of
    skin tones across the cast.

Cubes per fixture: 131-225 (Rowan 131, Wren 163, Garrick 168, Maren 173, Tobin 174, Tansy 225). Hair strands: 68-90.
Beyond the fine distance: 54-92. Bones: 51-75. All are inside the budgets (300 / 130 / 100). The budget reasoning
in "Cube budget" above is unchanged.

### Tests (123 -> 136)

`tests/test_artdir.py`:
- the hair ramp: warm hair shades toward orange and keeps its chroma, cool hair toward violet, dark hair gets a cool
  sheen at least 0.11 L brighter, and only the hair slots use it;
- every hair style has the 5 + 5 part strands and no whorl, its hair top is at most 3 px over the head (spikes and
  ahoge aside), and the head's back face is hair while the temple stays skin;
- a long strand reads root lighter than its middle, and its middle lighter than its tip;
- jag outer columns stay uncut, and a jagged slab's narrow faces are whole;
- the ear slabs' ledges have the same tone as their fronts, with a dark tip;
- the stubble has no every-other-texel pattern, the plaster is wider than tall and inside the face, and the cat mouth
  uses the bold line.
`herogen check --style` passes for all 8 models, and `herogen build --check` matches (test_rig and _fallback were rebuilt:
their hair uses the new kit and ramp).

### Decisions

1. **The rig is unchanged.** The adult's visual head share (0.234) is 0.01-0.03 above the owner's screenshot (about
   0.20-0.22). Closing that gap needs a longer body, not a smaller head. For example: the adult head top at 56 px
   instead of 52, the teen at 48, the chibi at 37, with the render scale lowered to about 0.52 to keep the world
   heights. That changes the rig, the bases, the hitbox numbers for A1 and every proportion test, so it is left to
   the owner's decision after seeing these previews.
2. **Flat painting stays, with a stronger hair ramp.** The study measured 2-3 tones per hair face. A wider, warmer
   ramp gives those tones the references' contrast without adding strand lines.
3. **Ears keep the stacked-slab construction** (the references' technique). Only their painting changed, so they stay
   solid from every side. A two-plate triangular ear would open at the slanted edges in side views.
4. **Skin is lighter on most fixtures**, not on all: Garrick keeps a tan skin and Rowan a warm one.

### Open items

- **Owner review**: the head size after this pass (adult 0.234, teen 0.268-0.283, chibi 0.339 with hair). If the
  adult should match the screenshot exactly, take the longer-body option in decision 1.
- **G2**: confirm in game that the hair ramp's contrast survives vanilla entity lighting. The front faces are lit at
  about 0.74 and the sides at about 0.5. The preview renderer uses 0.8 and 0.6.
- **Polish not done**:
  - The twin tails' bundles show their hollow top ring when seen from above.
  - Seen at a grazing angle, the inside of a jagged slab still shows between its teeth.
  - Outfits have no fabric patterns yet: no hero-level pattern override exists, and the parts library has none on
    its panels.

---

## G3: the animation generator (2026-11-12)

HEROES_V2.md 13.6 and 4.4-4.8, built on rig v3. Python only (`tools/herogen/anim/`), sources in `herosrc/anim/`, outputs
committed. As in the earlier passes, the reference models (`reference-study.md`) were used only for measurements and
for the list of animations a rich model has; no animation data, timing curve or pose of theirs is in the repository.
Where this section and the design differ, this section says why; HEROES_V2.md is updated to match.

### What exists

| Path | What |
|---|---|
| `anim/clip.py` | keys, tracks, clips and events with GeckoLib 4.9.3's keyframe semantics; its easings ported from `EasingType` |
| `anim/skeleton.py` | forward kinematics in file space (GeckoLib's `prepMatrixForBone`), the feet's sole corners; one skeleton per base |
| `anim/poses.py` | the pose library: `extends`, `mirror`, `knees` (bend with planted feet), fixes `level_feet` and `ground` |
| `anim/primitives.py` | `breathe`, `weight_shift`, `look`, `ear_twitch`, `walk_cycle`, `run_cycle` |
| `anim/recipes.py` | recipe -> clip: pose keys (overshoot, settle), layers, face keys, markers, the `bones` filter, the settle on the idle |
| `anim/secondary.py` | the follow-through bake: damped springs on the rig's chain bones, with gravity `hang` |
| `anim/writer.py` | the GeckoLib `.animation.json` writer and the server timing file |
| `anim/quality.py` | the 13.6 / 14.2 animation rules, on all six bases, and the timing agreement check |
| `anim/library.py` | the core library, the style libraries, a hero's own file, the animation chain, output paths |
| `anim/timing.py` | the `generated` block of the server definition: each move's frames from its clip (for F2) |
| `anim/preview.py` | filmstrips and GIFs (`herogen anim-previews`) |
| `herosrc/anim/poses/{core,katana}.json` | `rest`, `stance.relaxed`; `katana.stance`, `.windup`, `.windup_peak`, `.cut`, `.follow` |
| `herosrc/anim/core/locomotion.json` | `move.idle`, `move.walk`, `move.run` |
| `herosrc/anim/styles/katana.json` | `combat.idle`, `attack.1` |
| `herosrc/anim/secondary.json` | the seven spring groups (tail, ponytail, ears, hair, sleeves, skirt, cape) |
| outputs | `assets/.../animations/hero/_core.animation.json` (88 KB), `_style_katana.animation.json` (25 KB), `_style_fists.animation.json` (empty: `_fallback`'s style), `test_rig` and `_fallback` own files (empty), and beside each a server timing file `data/factionclash/hero_timings/<file>.json` |

Commands: `herogen build` also writes the libraries (and `--check` compares them); `herogen check` runs the animation
rules on the libraries and on each hero's own file, and checks that every timing file says what its animation file's
markers say; `herogen anim` lists the libraries; `herogen anim-previews` writes `docs/heroes-v2/previews/anim/`.
`visual.json` gains `animation_chain` (hero file, style library, core: what `HeroModel.getAnimationResource` and its
fallbacks return) and `secondary` (the chains the hero's parts declare, with their stiffness and damping).

### GeckoLib facts the writer relies on (read from the 4.9.3 jar with `javap -c`)

- `BakedAnimationsAdapter`: a channel's keys are read **in file order**; each key's segment runs from the previous
  key's time, so the map must ascend in time, and a `"0"` key that is not the first entry is an error. herogen's JSON
  writer sorts keys, so keyframe maps are now written as `util.Ordered` (insertion order); "24.0" no longer sorts
  before "3.0".
- A key is a vector, `{"vector", "easing", "easingArgs"}`, or Bedrock's `{"pre", "post"}` (pre placed 0.001 s before
  the key). Rotation constants are negated on x and y and turned into radians, exactly like a bone's rest rotation, so
  animation angles add to rest angles in file degrees (`rot.file_matrix` holds). Position keys are file pixels
  (`RenderUtil.translateMatrixToBone` negates x when it applies them).
- The easing of a key shapes the segment **arriving** at it; before the first key a channel holds that key's value,
  after the last key its value. Easing names are matched in lower case; unknown names fall back to linear.
- **`step` is 2 steps**: `EasingType.step(2)` returns 0 for the first half of a segment and **0.5** for the second
  half. A face_ctrl change from eye frame 0 to frame 4 through `step` would show frame 2 (closed) for half the
  segment, and frame 2 is a whole number, so the 0.05 rule of 4.6 would not catch it. **Deviation**: face changes are
  written as pre/post jumps (instant), never `step`.
- `KeyFramesAdapter`: `timeline` values are strings (an array would arrive as "[a, b]"), so instructions are joined
  with ";"; `sound_effects` take `{"effect"}`, `particle_effects` `{"effect", "locator", "pre_effect_script"}`.
- `loop`: `true`, `"hold_on_last_frame"` or absent (play once); `animation_length` in seconds.
A test ports `getKeyframes`, `addBedrockKeyframes`, `buildKeyframeStack` and the point lookup and reads every written
channel back: it gives herogen's own sampled values at every tick.

### Sign conventions (pinned by `tests/test_anim.py`)

Positive rx leans an upright bone forward and swings a hanging one back, so a forward arm or leg swing is negative
rx, **elbow flexion is negative rx on `forearm_*` and knee flexion positive rx on `shin_*`** (the rig's joint limits
now carry `sign` -1 and +1: the "flex" sign G1 left open). Positive ry turns toward the hero's right (-x). Positive
rz tilts an upright bone's top toward the hero's left and swings a hanging bone toward the hero's right: raising
`arm_r` sideways is +rz, `arm_l` -rz. Mirroring a pose keeps rx and negates ry and rz (positions: x). Euler order
matters once an arm is raised (X is applied first), so the swing's arm angles were solved numerically for hand and
blade targets on the teen and written into the pose file as plain numbers.

### The generated animations

| Animation | File | Length | What |
|---|---|---|---|
| `move.idle` | `_core` | 24 s loop | the relaxed A-pose stance; breathing every 3 s (chest up 0.14 px and back 0.9 degrees, shoulders out 1.2, head kept level); weight on the right foot 4-10.4 s and the left 14.5-21 s (hips 0.4 px over the foot, pelvis tips 1.8 degrees, chest and neck counter-tilt, the legs tilt so the feet stay put); glances on the neck 6.6-9.5 s (18 right), 11.8-13.5 s (9 down), 17-19.2 s (13 left); seeded ear twitches at 5.0 (right), 9.9 (left), 13.1 (right) and 18.9 s (left): one ear flicks back and out by 24 degrees in one tick, holds a tick and returns in three |
| `move.walk` | `_core` | 1.0 s loop | legs +-21 degrees, a 42 degree knee lift in the swing, heel strike and toe-off, the root following the lower foot, hips turning 4 and tipping 1.6 degrees, shoulders counter-turning, arms +-17 with the elbows bending as they come forward, skirt panels following the legs, a 4 degree tail sway |
| `move.run` | `_core` | 0.6 s loop | legs +-36, a 96 degree knee in the swing, a 9 degree forward lean (the head kept up), elbows at 72-86, arms +-32, a flight phase after each push-off (both feet up to 0.9 px off the ground on the teen) |
| `combat.idle` | `_style_katana` | 3.0 s loop | the ready stance: knees bent 10 degrees with level feet on the ground, right foot forward, the right shoulder leading, the blade forward-up at about 45 degrees; breathing twice as fast |
| `attack.1` | `_style_katana` | 0.9 s | wind-up to 0.20 s (easeOutCubic) and a stretch to 0.30 s, the shoulder sweeps about 130 degrees through the cut, which lands at **0.40 s** clip time (`hit;vfx:slash_arc`, written at 0.50 s, tick 10 from the action's start: G3 review), follow-through to 0.55 s, back on `combat.idle` at 0.90 s; a sweep sound at 0.30 s clip time; the face goes focused at 0.05 s, angry eyes with a shout at the hit, no override from 0.70 s; upper body only (waist, chest, neck, arms) |

The idle keys `neck`, never `head`: the head is left to the procedural head look of `HeroModel.setCustomAnimations`.
`attack.1` keys no leg, `body` or `root` bone, so a hero can swing while the base controller walks it (the references
also split the arm layer from the locomotion). Its timing file says `frames: windup 0.5, active 0.05, recovery 0.45`
(ticks 10 / 1 / 9, from the action's start with the 2-tick blend; G3 review): class light, the clip's own wind-up at
the 6.4 minimums (0.4 / 0.3).

### Every body form, one file

Bone names are the same on every base and rotations transfer exactly; what does not transfer is anything in pixels
that depends on leg length (the root height that keeps a sole on the ground as the legs swing, the leg tilt that
keeps the feet planted under a hip shift, the drop of a knee bend). Those are solved on all three forms and set to
the **compromise that minimises the worst error** (the middle of the per-form solutions), not on one reference form.
Measured lowest sole over each clip (px; 0 is the ground; tests allow +-0.6 for grounded clips, planted feet may move
0.5):

| Clip | chibi | teen | adult f | adult m |
|---|---|---|---|---|
| `move.idle` | 0.00 | 0.00 | 0.00..0.01 | 0.00..0.01 |
| `move.walk` | -0.27..0.00 | 0.00..0.10 | 0.00..0.27 | 0.00..0.25 |
| `move.run` | -0.44..0.15 | 0.04..0.90 | 0.04..1.34 | 0.04..1.34 (flight) |
| `combat.idle`, `attack.1` | -0.14 | 0.04 | 0.14 | 0.14 |

An option for later (A2/G4), not used: GeckoLib evaluates Molang in keyframes, so a root key could read a per-model
variable (`v.leg_px`) that `HeroModel` sets from `visual.json`, which would make pixel offsets exact on every form.

### The secondary bake

Each link of a spring group (`herosrc/anim/secondary.json`) is a point mass at the link's tip, pulled toward where the
**parent** carries it by a spring of 0.8 + 3.5 x stiffness Hz with a damping ratio of 0.8 x damping, damped relative
to the target's own velocity (a steady motion leaves no lag, every acceleration does). The bone's own authored
rotation is added on top unchanged (otherwise the spring would smear the ear twitch it was given) while the bones below
it feel it. `hang` (0-1) also pulls the tip toward its bind direction in the world, as gravity does: without it a wide
kimono sleeve stood out like a disc whenever the forearm was raised (seen on Garrick and Tansy in the first renders).
Input sampled every tick with 4 sub-steps; output keyed every 2 ticks plus the authored keys' times, simplified at 0.5
degrees; a bone whose follow-through stays under 0.5 degrees gets no keys; loops run 3 s of warm-up and spread the
residual so they close exactly.

| Group | Bones | Stiffness / damping | Hang | Limit | Peak in walk / run / attack.1 (degrees) |
|---|---|---|---|---|---|
| tail | `tail_1..5` (chain) | 0.4 / 0.75 | - | 35 | 21 / 34 / - (body not keyed) |
| ponytail | `tail_hair_1..3` (chain) | 0.35 / 0.6 | 0.6 | 40 | 1 / 8 / 17 |
| ears | `ear_r`, `ear_l` | 0.65 / 0.6 | - | 10 | - / 2.5 / 10 |
| hair | `hair_back`, `hair_l`, `hair_r` | 0.8 / 0.75 | 0.4 | 6 | - / 1 / 6 |
| sleeves | `sleeve_r`, `sleeve_l` | 0.5 / 0.7 | 1.0, fading out (`hang_fade` -0.6..-0.3) as the forearm rises above horizontal; lag 30 | 85 | 34 / 85 / 82 (mostly hanging) |
| skirt | `skirt_f/b/r/l` | 0.6 / 0.7 | 0.5 | 12 | 1.5 / 5.6 / - (not under a bone the swing keys) |
| cape | `cape_1`, `cape_2` (chain) | 0.35 / 0.6 | 0.7 | 45 | 2 / 10 / 11 |

The tail's first tuning (0.3 / 0.55, the cat tail part's own numbers) resonated with the walk's 2 Hz bob and pinned
the tip at its limit; 0.4 / 0.75 moves it to 2.2 Hz and a 21 degree peak. (The sleeve's open end facing the viewer
through the swing was a bake bug and the hang model, fixed in the G3 review below.)

**Runtime cost.** GeckoLib samples only keyed channels, every frame, per animated hero: `move.walk` keys 31 bones
(447 keys), `move.run` 36 (336), `move.idle` 30 (820 keys over 24 s), `combat.idle` 29, `attack.1` 26. Interpolating
a channel is a few multiplications, so the animation pass is small next to the vertex transform of 130-230 cubes; the
bake adds keys, not bones. The libraries are shared files (core 88 KB, katana 25 KB), loaded once.

**Controllers (a design change, for A2).** GeckoLib overrides per bone and channel, so the planned `secondary`
controller with `sec.*` loops would overwrite the follow-through baked into walk, run and the swing on those bones.
Recommendation: no `sec.*` clip keys a bone the library bakes; the part-declared chains the library cannot know
(Tansy's `twin_r`/`twin_l`, Rowan's `scarf_tail_*`, an `ahoge`) and the per-part stiffness and damping go to
`visual.json` `secondary` for the procedural lag of `setCustomAnimations` (additive, after the animation pass). If a
later hero needs authored keys and sway on the same bone, add sway wrapper bones (study 11.7).

### Markers and the server timing file

Markers sit in recipe keys (`marker`, `sound`, `particle`) and must be on whole ticks. `hit;vfx:slash_arc` is split:
`sfx:<id>` parts become sound keyframes, the rest is joined into one `timeline` string. Each animation file gets a
server timing file (`data/factionclash/hero_timings/<file>.json`, same chain as the client): per animation its length
in seconds and ticks, loop mode, kind (base or action), class, every event with its tick, and for one with `hit` or
`cast` markers `hits`, `frames` (wind-up = first hit, active = last - first + one tick, recovery = the rest; 12.18's
definition) and `frame_ticks`. `anim/timing.generated(hero)` resolves `move.<id>` and `ultimate` through the chain
and returns the move frames for the server definition's `generated` block, with NOTEs for a missing animation, a
clip without a hit, or frames under the class minimums. The server definition itself stays F2's (it is not written
for `test_rig`, which is not a roster hero).

### Quality rules as built (`anim/quality.py`)

Keys at 0 and none after the end; finite values; GeckoLib easing names; loops close; markers on ticks and inside the
clip; the class minimums before the first hit and after the last; face_ctrl whole frame numbers changing only by
jumps; joint limits every tick (elbow and knee 0-150 by their sign, head yaw 80 / pitch 60, neck yaw 60 / pitch 40,
waist twist 45); actions end on their settle clip's first key on every channel they key (or the rest pose); on all
six bases planted clips keep every sole corner within 0.5 px, grounded clips keep the lowest sole within 0.6 px of
the ground, gaits never sink more than 0.6 px; no root motion beyond 1.5 px; keyed bones are rig bones (or the hero's
own); at most 24 animations in a hero's own file; and the timing file agrees with the animation file's markers.
`herogen check --style` passes for the libraries and all 8 models.

### Recipe format additions

`layers` (`[{primitive, params, add}]`, additive or replacing, over the whole clip) instead of a primitive inside a
key; `bones` (globs of the bones the keys and layers may key); `planted` and `ground` (feet rules); `kind`;
`description`. A pose key is a whole pose (every bone any key's pose mentions is keyed at every pose key). `overshoot`
k pushes the key k x the last change past its pose and returns 0.1 s later (an explicit key, not `easeOutBack`, so the
overshoot size is the author's); `settle` k relaxes k x the change back 0.15 s later.

### The core library plan (from the reference study's animation list, our own content)

What a rich model has (study 9, survey 7) mapped to what our NPC heroes need (study 11.9) and to HEROES_V2 4.5; G4
builds the rest of this table, G5 the style libraries. Lengths are targets.

| Group | Animations | Length | Status |
|---|---|---|---|
| idle | `move.idle` (one long loop with weight shifts, glances, ear twitches) | 24 s | **G3** |
| locomotion | `move.walk`, `move.run` | 1.0 / 0.6 s | **G3** |
| locomotion | `move.crouch`, `move.swim`, `move.climb`, `move.fall` (loop), jump take-off and land (small, roll) | 1.0-1.3 / 0.75 / 0.12-0.6 s | G4 |
| rest and carry | `rest.sit`, `rest.sleep`, `carry.loop` | loops | G4 |
| hurt and death | `hurt.heavy` (lean back, ears back, tail up, surprised face), `stagger`, `stun` (+`.loop`), `interrupt`, `death` (held), `defeated` (+`.loop`) | 0.5 / 1.0 s | G4 |
| work | the 16 `work.*` of 7.3 (mine, chop, till, plant, harvest, fish cast/wait/reel, cook, smith, build, read, pray, sweep, tend, eat) | 0.6-1.6 s | G4 |
| emotes | `emote.greet`, `bow`, `wave`, `laugh`, `angry`, `sad`, `think`, `cheer`, `talk`, `shrug`; `personal.*` generics | 1-3 s | G4 |
| generic combat | `dodge`, `intro`, `victory` | 0.5-3 s | G4 |
| style (16) | `combat.idle`, `attack.1-3`, `attack.ranged`, `guard` (+`.loop`, `.hit`), `dodge`, `draw`, `sheathe`, `intro`, `victory`, `taunt`, a `move.run` arm carry | 0.4-1.5 s | katana `combat.idle` and `attack.1` **G3**; the rest G5 |
| not needed | vehicles, guns, elytra, GUI poses, board games, carrying a player | | - |

### Previews (`docs/heroes-v2/previews/anim/`)

`filmstrip_idle.png`, `filmstrip_walk.png`, `filmstrip_run.png` (Tansy the chibi, Wren the teen, the test rig adult;
side and 3/4), `filmstrip_attack.png` (the same three with a preview-only katana variant, and Garrick, adult male),
and `anim_walk.gif`, `anim_run.gif`, `anim_attack_1.gif`, `anim_idle.gif` (an excerpt with an ear twitch, the weight
shift and a glance). They are drawn by the G1 renderer with GeckoLib's bone transforms, the face frames the face_ctrl
keys pick, the action over its settle clip and the weapon drawn for combat.

### Tests (136 -> 181)

`tests/test_anim.py` (45): easings and track semantics against GeckoLib's, `step`'s midpoint; every written channel
read back through a port of GeckoLib's parser; keyframe maps in time order; loop and marker fields; the attack's hit
at 0.40 s / tick 8 and its frames; timing files agree with their animation files, and a moved hit, a too-early hit and
an off-tick marker fail; frames of several hits; move frames from a signature through the chain; byte-identical
library builds twice and across processes with different hash seeds; the committed libraries are fresh; the quality
rules pass on all six bases; one file serves every form; walk phase (right foot and left hand forward at 0) on every
base; the run's flight; breathing period; ear twitches (one tick up, over 20 degrees, 3-6 s apart or a double); no
`head` keys; the attack keys no leg bone and settles on `combat.idle`; the cut sweeps the hand down and forward
through the hit; joint-limit signs, open loops, drifting feet, sliding face frames and root motion fail; pose sign
conventions, mirroring, `extends` and the feet fixes; overshoot, settle, face and marker keys, layers; the spring lags
a turn, swings through and settles; baked keys are sparse and loops close; the twitch is not smeared; sleeves hang;
part chains reach `visual.json`; every file of every chain exists; a signature hero's file passes; the own-file
budget; a pose position moves the render.

### Decisions (each deliberate)

1. **Face frames jump** (pre/post keys), they never use `step` (GeckoLib's step would show a third frame).
2. **One timing file per animation file** in `data/factionclash/hero_timings/`, not only the hero's server
   definition: basic attacks (`attack.1-3`) live in style libraries and need server timings too. The move frames of a
   hero's definition come from the same clips (`anim/timing.py`).
3. **Actions key the upper body** unless they must move the legs, so locomotion keeps playing under them.
4. **Springs answer the parent only**, with gravity `hang` for sleeves, capes, ponytails, skirts and hair.
5. **The library bakes the rig's chain bones**; part-declared chains and per-part spring numbers go to `visual.json`
   for the procedural lag (A2); the `secondary` controller should not key baked bones.
6. **Pixel offsets solved over the three forms** (the compromise), so one file serves chibi, teen and adult.
7. **The idle is one 24 s loop** (the study's recommendation) instead of a short loop plus rare idles; `personal.1-3`
   stay for synchronised moments the server starts.
8. **Joint limits** get the flex sign and a neck limit; the rest of the rig is unchanged (no geometry change: only
   `visual.json` changed in the committed models, for the new fields and the rig file's hash).

### Open items

- **A2**: play `move.idle`, `move.walk`, `move.run` (base controller), `combat.idle` and `attack.1` (action controller,
  log the `hit` instruction's tick: 8); read face_ctrl jumps (pre/post); register a sound keyframe handler for
  `sound_effects`; the `secondary` controller recommendation above; the procedural lag from `visual.json` `secondary`.
- **G4 / G5**: the rest of the core and style libraries (the plan above); the remaining primitives (swing, thrust,
  slam, spin, leap, land, cast, draw_bow, throw, guard, dodge, hurt, stagger, kneel, death, greet_bow, wave, cheer,
  taunt, play_instrument, work_loop); a two-handed katana string.
- **G2**: compare the preview renderer's animated poses with in-game screenshots (rotation order and position sign
  are pinned to GeckoLib's code, as for the geometry).
- **G7**: the Java `HeroAssetsTest` port of `quality.check_timing` (the timing files against the animation files).
- **Owner**: the timing of the swing (wind-up 0.4 s is the light minimum; a basic attack every 0.9 s) and the
  amount of tail and sleeve follow-through.
- **Polish**: the sleeve's open end during the fastest frames of a swing (above); the idle's motions are deliberately
  small (0.4 px hip shift, 1-2 degree tilts), worth a look in game at normal distance.

## G3 review: GeckoLib semantics, timing, the action bake (2026-11-12)

An adversarial review of commit c7a3326 against the GeckoLib 4.9.3 jar (`javap -c` of `BakedAnimationsAdapter`,
`KeyFramesAdapter`, `EasingType`, `Animation$LoopType` and `AnimationController`, jar SHA-256 20a1995e...1c47), the
frame renders and the tests. No reference model was opened for this pass.

### Confirmed correct

`format_version` 1.8.0 (GeckoLib does not read it); `animation_length` in seconds (read x 20); `loop` true /
`"hold_on_last_frame"` / absent (absent and any non-primitive are play-once); keys as `{"vector"}`, `{"vector",
"easing"}` or `{"pre", "post"}` (pre placed 0.001 s early, a `lerp_mode` on post becomes its easing); the easing shapes
the segment arriving at its key; time strings parsed with `NumberUtils.isCreatable`; a `"0"` key after another key
throws; rotation constants negated on x and y and converted to radians at load; `timeline` values are strings (an
array would become "[a, b]"), `sound_effects` `{"effect"}`, `particle_effects` `{"effect", "locator",
"pre_effect_script"}`; event times x 20 = ticks. The writer's output matched on every point.

### Found and fixed

1. **Events fired 2 ticks before their pose, and the server's hit 2 ticks before the swing.** `AnimationController`
   blends for its transition length T before it plays a clip from 0 (state TRANSITIONING, then `adjustTick` restarts
   the clock), but `processCurrentAnimation` fires keyframe events when `clip tick + T >= event tick` (it adds
   `transitionLength` before the event loops, on every pass of a loop too). So the pose of clip time p shows at T + p
   from the start, an event written at p fires at p: the `hit` instruction and the sweep sound came 2 ticks (the action
   controller's T) before the cut showed, and the server, whose wind-up was the marker time, damaged the target 0.1 s
   before the blade arrived. Fix: the writer writes every event at p + T (`writer.TRANSITION_TICKS`: action 2, base 4,
   HEROES_V2 4.4), so it fires on the frame its pose shows; timing files (format 2) count from the action's start and
   state `transition_ticks` and `total_ticks`; `frames` include the blend (`attack.1`: wind-up 0.50 s, ticks 10 / 1 /
   9 = 20 = the blend and the clip); move frames in `generated` carry `transition_ticks`. GeckoLib's speed modifier
   scales the whole clock, the blend included, so dividing every frame by the speed (4.8) stays exact. Recipes,
   clips and the class minimums keep clip time (the minimums count the clip's own anticipation, not the blend).
2. **A marker on a clip's last tick never fires.** When a clip reaches its length, GeckoLib ends it (or restarts a
   loop and resets the fired events) and returns before that frame's events. Recipes and the quality rules now reject
   markers after `length - 1 tick`.
3. **The action bake counted the base's follow-through twice.** `attack.1` was simulated over `combat.idle` as baked,
   so a chain bone's "authored" value under the action was combat.idle's own spring and hang result, and the action's
   bake added its full offset on top: at t = 0 the right sleeve hung 130 degrees off the forearm (combat.idle: 65), the
   left 29 (14.5), and capes, hair, skirt panels and the ponytail started at twice their offset. The action controller's
   2-tick blend turned that into a visible pop at every swing, and through the swing the doubled hang stood the bell
   sleeve off the arm as a disc (Garrick's and Tansy's frames at 0.10, 0.20 and 0.40 s). Fix: each clip keeps its
   pre-bake chain tracks (`meta["authored"]`), and an action is baked over that authored view (`secondary.authored_view`).
4. **The swing keyed the skirt panels.** The bake keyed every chain bone that moved more than 0.5 degrees, including
   bones the swing never moves (skirt panels under `body`): GeckoLib overrides per bone, so swinging while walking would
   have frozen the walk's skirt sway. Fix: an action bakes only the chain bones below the bones it keys (`movers`);
   `attack.1` now keys 22 bones (was 26) with 193 keys (was 213), never `skirt_*` or `tail_*`.
5. **A bell sleeve on a raised forearm showed its open end.** With `hang` 1 and an 85-degree limit, a forearm pointing up
   swung the sleeve 85 degrees off it (horizontal), and in the cut it trailed 85 degrees behind. Fix (secondary.json):
   `hang_fade` [-0.6, -0.3] fades the pull out as the forearm rises from about 17 to 37 degrees above horizontal (the
   cosine of the carried direction to the bind direction), so a sleeve on a raised arm stays around it like a tube; it
   hangs again as the forearm comes down; `lag` 30 caps the spring's trail behind its target. The walk and the run are
   unchanged (their forearms stay below the fade: the run's highest is 15 degrees above horizontal; a narrower fade
   made the run's pumping sleeve face forward as a ring on Tansy).
6. A jump key at 0 is now written as a plain vector (GeckoLib shows its `post` from 0; herogen's sampler showed `pre`).

### Tests (181 -> 187)

The clock port (`test_events_fire_on_the_tick_their_pose_shows`): GeckoLib's controller clock at speeds 1, 2, 0.5 and
1.25, with render frames between ticks; the written hit fires on the frame the clip shows the cut, at the timing
file's wind-up divided by the speed (unshifted events fail it). The attack's hit (clip 0.40 s; file 0.50 s, tick 10;
frames 10 / 1 / 9 = total 20) and `combat.idle`'s blend (4). A marker on the last tick fails in recipes and in the
quality rules. The action starts on `combat.idle` on every channel; sabotage: the old double bake fails the new start
rule. The swing bakes sleeves, ears, ponytail and cape, never skirt or tail (sabotage: baking all chains fails). The
raised sleeve stays within 35 degrees of the forearm through the wind-up and hangs again in the recovery (sabotage:
no fade fails). A jump at 0 is its post value. Updated: the signature move's frames (0.95 / 0.05 / 0.9), its timeline
(0.95 s), the frames of several hits, the moved-marker sabotage.

### Decisions

1. **Events carry the controller blend** in the written files rather than leaving the server to add it: the client's
   own `hit`, sounds and particles must fire on their pose too, and GeckoLib gives no hook to delay them. The cost: a
   file's event time is 2 ticks (actions) or 4 (base loops) after its pose's time; the writer docstring says so.
2. **Timing format 2**: `t`, `tick`, `hits` and `frames` count from the action's start; `length` and `ticks` stay the
   clip's; `transition_ticks` and `total_ticks` are new. `HeroModel` must use exactly these transition lengths; the Java
   test (G7) compares them.
3. **The class minimums apply to the clip's own wind-up** (the blend is a pose blend, not readable anticipation).
4. **Sleeve hang** fades by the forearm's angle rather than a smaller limit: a smaller limit would leave a level
   forearm's sleeve sticking out forward; the fade keeps the stance and the run hanging and the raised arm tidy.

### Open items

- **A2**: controllers with transition lengths 4 (base) and 2 (action) as the timing files say; the server's hit tick =
  `frame_ticks.windup` / speed. The idle always restarts at 0 when a hero stops, so heroes that stop together twitch
  their ears together at 5 s: start `move.idle` at a per-entity offset if GeckoLib allows, or accept it.
- **G4/G5**: footstep or work sounds in base loops will be written 4 ticks after their pose (automatic).
- **G6b**: paint the inside of a bell sleeve darker; the cut still shows the sleeve's inside for a frame at 0.35 s.

---

## G2: the preview renderer (2026-11-12)

HEROES_V2.md 13.8 without the in-game fidelity test (that needs A2's `HeroRenderer`; see the open items). The
renderer draws a hero from its written files the way Minecraft 1.21.1 and GeckoLib 4.9.3 draw an entity in daylight,
plays the written animation files through an emulation of the two controllers, picks the face frames as `HeroFace`
will (moods, authored face keys, blinks), and writes PNG sheets and GIFs.

### What exists

| Path | What |
|---|---|
| `render/raster.py` | the renderer: GeckoLib's bone and cube transforms, vanilla entity light, cut-out at alpha 0.1, nearest texels, the faction tint, the glow layer; orthographic camera (previews) or perspective (`distance`, for the fidelity test); a `Frame` keeps the depth, the face index and the texel of every pixel for tests. Faces are rasterised in batches (each face inverted through its projective map, the nearest fragment wins), 2-4x faster than G1's renderer at preview scales |
| `render/animfile.py` | animation files read back the way GeckoLib reads them; `AnimChain` (the hero's file, its style, the core, as `visual.json` `animation_chain` lists them); `Playback` (a base loop plus an action with the action controller's 2-tick blend; events on the frame they fire); `authored_face` (the `face_ctrl` whole-number rule) |
| `render/face_sim.py` | a stand-in for `HeroFace` (4.6): the priority order, the mood table from `visual.json`, seeded blinks per profile, no blink in a wind-up |
| `render/scene.py` | `HeroAssets`: a hero's built files (in memory, so style fixtures and preview variants work) or the committed ones, and HeroModel's per-frame rules read from `visual.json`: face UV shifts, tint bones, phase bones and texture, drawn or sheathed weapon, fine bones beyond the fine distance, glow mask; `stance()` = the first frame of `move.idle` (or `combat.idle` with the weapon drawn) |
| `render/sheet.py` | turnaround (eight views 45 degrees apart, from above, weapon drawn, far LOD, phase look at night), lineup on grass, face strip, contact sheet (6 x 4 cells) |
| `render/gif.py` | films (heroes side by side, the clip time, events written over the frame they fire on) and GIFs (one shared palette, Pillow's frame differencing) |
| `render/showcase.py` | the owner's set in `docs/heroes-v2/previews/render/`, with the face-plane evidence image |
| `cli.py` | `preview`, `gif`, `faces`, `sheet` (to `build/herogen/` unless `--out`), `render-previews` |
| `tests/test_render.py`, `tests/golden/` | 31 tests; 4 golden images of a synthetic fixture |

`render/ortho.py` is gone: `previews.py` (the style set) and `anim/preview.py` (the G3 filmstrips) now draw with
`raster`, in the real idle stance instead of the hand-set `IDLE_POSE`, and their sheets were regenerated, so every
preview under `docs/heroes-v2/previews/` now shows the game's light.

The owner's set (`render-previews`, about 4 minutes): `forms_lineup.png` (the six bases), `lineup.png` (the six style
characters and the test rig), `turnaround_<id>.png` (each of the seven), `faces.png` (calm, a blink midway and
closed, happy, angry, 3/4), `contact_sheet.png` (the seven and `_fallback`), `idle_blink.gif` (the whole 24 s idle at
10 fps on the three forms), `idle_blink_faces.gif` (the seven heads at 20 fps, one frame a tick, 6 s in which every
hero blinks at least once and Rowan blinks twice in a row), `walk.gif`, `run.gif` and `swing.gif` (the katana
`attack.1` over `combat.idle` on Tansy and Wren with the test sword, the test rig and Garrick; "sound" and "hit"
written over the frames they fire on), and `face_plane_light.png` (see "Found while rendering").

### What it emulates, and where each rule was read

- **Geometry**: unchanged from G1 (pinned to GeckoLib by `tests/test_geckolib.py`). With the old block shading
  substituted, the new rasteriser reproduced G1's images to within 1 of rounding on every pixel, posed and unposed.
- **Light** (vanilla `minecraft_mix_light`, `Lighting.DIFFUSE_LIGHT_0/1`): `min(1, (max(0, n.L0) + max(0, n.L1)) *
  0.6 + 0.4)` with `L0 = normalize(0.2, 1, -0.7)`, `L1 = normalize(-0.2, 1, 0.7)` in world space. In 1.21 the
  entity's pose stack carries no camera rotation (it is in the model-view matrix), so the light belongs to the world:
  a hero facing south or north is lit 0.74 on its front and back, 0.50 on its sides, 1.0 on top, 0.4 underneath
  (G1's stopgap used 0.8, 0.6, 1.0, 0.5). Facing east or west the front gets 0.50 and the sides 0.74 (vanilla mobs do
  the same).
- **Normals** (`GeoRenderer.renderCube`, `RenderUtil.fixInvertedFlatCube`, javap of the 4.9.3 jar): the face's
  direction through the pose's normal matrix, then the flat-cube fix (a cube with a zero size flips a negative x
  when its y or z size is 0, a negative y when its x or z size is 0, a negative z when its x or y size is 0).
- **Glow layer** (`AutoGlowingGeoLayer`, `AutoGlowingTexture`, javap): the `entity_translucent_emissive` shader,
  translucent blending, packed light `0xF00000`: the glow texel of the visible surface, lit by the diffuse light but
  not by the light map, blended by its alpha. The night views use a light map of 0.25.
- **Entity and camera**: the model turned by 180 - body yaw (body yaw 0 faces south); the camera goes round the hero
  (`yaw`, `pitch`), so turnarounds show what a player sees walking round a hero standing still.
- **Animations**: the files are parsed like `BakedAnimationsAdapter` (file order, pre/post jumps 0.001 s apart,
  easings), and a test compares every channel of the written libraries at every tick with the generator's clips. The
  base controller loops from 0; an action blends linearly for 2 ticks from the pose at its start to its first frame
  (only the channels it keys), then replaces those channels; its events fire at action start + file time, which is
  the frame on which the generator's pose shows them (tested: the hit at 0.80 s in `swing.gif`, the frame of the cut).
- **Face**: the frames come from `visual.json` (`face.regions`, `face.moods`, `face.blink`), so the previews use the
  same data A2 will.

### Decisions

1. **The previews stand in the real idle stance** (the first frame of `move.idle`: arms 8 degrees out, forearms bent
   7 degrees, sleeves hanging) instead of G1's hand-set arms at 9 degrees, and in `combat.idle` with the weapon drawn.
2. **Golden images are of a synthetic fixture** (a body, a rotated arm bone, a cube with its own rotation, an
   inflated cube, a zero-thickness plane, a tinted bone, a cut-out hole, a glowing part), so art changes never break
   them. The real models are covered by property tests: every pixel equals its texel times its face's light times its
   tint, the light of a face does not change when the camera moves, the committed and the freshly built test rig
   render identically, and renders are deterministic. `HEROGEN_UPDATE_GOLDEN=1` rewrites the images.
3. **GIFs** use one palette for every frame (255 colours from a sample of frames, no dithering) and Pillow's frame
   differencing (unchanged pixels transparent): 5 times smaller, and a test decodes the GIF back to its frames.
4. **The blink stand-in** draws the next interval from the end of the last blink, in whole ticks; a double blink
   starts 2 ticks after the first ends (`DOUBLE_GAP`, the one number 4.6 leaves open: PROPOSED for A2); the talking
   mouth changes every 3 ticks (4.6: every 2-3 with noise). A2's `HeroFace` and `FaceRules` are the reference.
5. **The perspective camera** (`View(distance=...)`, perspective-correct by inverting each face's projective map) is
   there for the fidelity test; the previews stay orthographic.

### Found while rendering (for other tasks)

1. **Face planes are lit apart from the head in some poses** (`previews/render/face_plane_light.png`). The `eyes`,
   `mouth` and `cheeks` planes have zero thickness in z, so GeckoLib's flat-cube fix makes the x and y of their world
   normal positive. A face pointing down is then lit like a face pointing up: with the head pitched 20 degrees down
   the eyes plane is lit 0.88 while the head front around it is 0.55, a bright rectangle over the eyes; turned 30
   degrees to its right (facing south) the plane is 0.65 against 0.74. Level, looking up or turned the other way they
   match. This is GeckoLib's code as read, so it should happen in game (the fidelity test will confirm). Fix options:
   (a) A2: `HeroRenderer` overrides `renderCube` for the face-plane bones and skips the flat-cube fix (a few lines;
   recommended); (b) the generator gives the planes a small thickness (changes the packer's flat-cube path and the
   checks). Until then any pose that points the face down (a look down, the procedural look at a lower target, a bow)
   shows the eye and mouth rectangles.
2. **One frame of a bell sleeve seen end-on in the swing** (`swing.gif` at 0.45 s, clip time 0.05 s, Tansy and
   Garrick in the 3/4 view): the arm rises about 100 degrees in the first tick (`easeOutCubic` into the raised key)
   and the sleeve shows its open end as a ring for that frame. For G5 or G6b, with the darker sleeve inside.
3. **The previews are darker than before** on fronts (0.74 against 0.8) and sides (0.50 against 0.6). That is the
   daylight look the game gives; the art pass's question (does the hair ramp survive the game's light?) can now be
   judged on the previews, with the in-game screenshot as the final word.

### Tests (187 -> 215)

`tests/test_render.py` (the 3 G1 render tests rewritten for `raster`, 28 new): golden images of the fixture in four
cases (front; 3/4 posed with a bone position; back with tint, glow and a dark light map; perspective); the light
values per direction; the entity turn; the flat-cube fix; every pixel = texel x light x tint over three camera angles;
the plane-versus-surface quirk; a turned entity lights its sides; cut-out and brightness; the glow ignores the light
map; the test rig's height, hidden children, the sheathed blade, view directions; perspective converging to the
orthographic image at a large distance and drawing the near face larger; the animation reader against the generator
at every tick; event file times and the chain; the blend then play; the hit on the frame of the cut; the face_ctrl
rule; a blink is half, closed, closed, half; intervals per profile with doubles; seeded and never in step; no blink
over happy eyes or in a wind-up; face priorities; face UV shifts equal the rig's frame regions; hidden bones by phase,
weapon and distance; the tint reaches only the sash; committed and built render alike; a GIF decodes to its frames
and is byte-identical twice; `preview` from the command line. `test_anim.py` and `test_geckolib.py` now use `raster`.

### Open items

- **G2 fidelity test (with A2)**: screenshot the test rig in three poses (rest, an animated frame, a frame with the
  head pitched down) with the Xvfb client of HANDOFF section 4, render the same poses with the perspective camera at
  the screenshot's position (FOV 70, its distance and pitch) and compare silhouettes within 1 px at 4x; check the
  light map value at noon (taken as 1.0) and the face-plane quirk above.
- **A2**: fix the face-plane light (option a above); the double-blink gap (`DOUBLE_GAP` 2 ticks, PROPOSED); match
  `FaceRules` to 4.6 and `face_sim.py` or update the stand-in.
- **Not emulated**: GeckoLib's bone reset after an action ends (the base shows at once here), blends between two base
  animations, the procedural look and the secondary chains of `setCustomAnimations`, held vanilla items, name tags,
  fog, the hurt overlay and the entity shadow (lineups draw a soft ellipse).

## G2 review: stale GIFs, coplanar faces, readable films (2026-11-12)

Reviewed c2e967a: every image and GIF of `previews/render/` (GIF frames extracted and read), the renderer against the
vanilla and GeckoLib rules it cites, the tests, and a fresh render of the whole set compared byte for byte with the
committed files (the style and animation previews too).

### Confirmed correct

- The light values recomputed by hand from `DIFFUSE_LIGHT_0/1` (front and back 0.74, sides 0.50, top 1.0, bottom 0.4)
  and the face-plane numbers of `face_plane_light.png` (head down 20 degrees: front 0.55, eyes plane 0.88; turned 30
  degrees to its right: 0.74 against 0.65).
- Bone and cube transform order, the normal matrix, hidden children, cut-out, no culling (the sleeve's open end shows
  its inside, as in game), the action blend and the event clock (the hit on the frame of the cut, 0.80 s).
- Determinism: 16 of the 18 committed files of the set and all 28 style and animation previews came out byte for byte.
- Blinks: every hero blinks inside the faces GIF's window, Rowan's double blink is 2 ticks apart, and the half and
  closed frames are both visible at 20 fps.
- No missing faces and no wrong overlaps in any view, apart from the depth ties below.

### Found and fixed

1. **Two committed GIFs were stale** (not what the code makes). `swing.gif` showed every event one frame late (the
   sweep sound at 0.75 s, the hit at 0.85 s, while the cut shows at 0.80 s: an earlier event window), so the G2 summary's
   "hit at 0.80 s" was not true of the committed file; `idle_blink_faces.gif` was an 8 s render (2.0-10.0 s) where the
   code and these notes say 6 s (2.5-8.5 s). Both regenerated. New `render-previews --check` renders the set in a
   temporary folder and fails on any committed preview that differs (about 4 minutes; run it before committing
   previews).
2. **The renderer hid z-fighting.** It settles a depth tie by draw order (the earlier face wins), so two coplanar faces
   of different cubes look like one clean surface; in game they z-fight (their depths differ in the last bits from
   pixel to pixel and frame to frame, worst at a distance). Drawing the faces a second time in reverse order changes
   exactly the tied pixels. In the seven characters' stance (front, 3/4, back at 6x): **Maren's sash has exactly the
   extents of her waist coat layer** (x +-3.4, z +-2.4), so the sash is hidden in every preview and would flicker
   against the coat in game (611 px); **Garrick's and Tobin's sash shares its side planes with the skirt's top band**
   (y 27.5-28.75 on Garrick: 495 and 250 px); Maren's two greaves overlap by 0.45 px at the centre in one front plane
   (477 px); smaller: the test rig's chest/fine_chest and tail_4/tail_5, the ear slabs of Tansy, Tobin and the test rig
   (cubes of one bone flush with each other), Rowan's chest, Wren's bangs on the head (8 px). Added
   `raster.coplanar_ties` (ties narrower than 2 screen pixels are dropped: where two faces cross, or an edge lies flush
   on a face, is a seam, not a flickering area), `HeroAssets.ties`, `preview --ties` (magenta overlay and the bone
   pairs) and the evidence image `previews/render/coplanar_faces.png`. The fixes belong to the parts (G6b): a layer
   over another sits at least 0.05 px proud (the face planes' rule, HEROES_V2 4.3), or the faces that overlap carry
   the same colour; then a `check` rule (G7).
3. **The GIFs were too small to judge**: 360-411 px wide at 3x, so a blink was 1-2 screen pixels and the blade a 1 px
   line. Walk, run and the swing are now at 5x and the idle at 4x (all GIFs together about 2.4 MB, was 1.9).
4. **Event labels ran together** over the swing ("hit vfx:slash_arc" of four heroes in one line): now one event per
   line above each hero (a vfx without its prefix), in a band reserved above the tallest hero.
5. **Sheet details**: the turnaround's view from above is centred in its row (it sat at the bottom of a tall row);
   labels "from above (front at the bottom)" and "far (beyond 16 blocks): fine_ bones hidden"; the contact sheet's back
   view and phase look are drawn at the main view's scale (they were half size in a half-empty cell), and a style
   fixture or the fallback (no kit) shows its base, build, cube count and weapon instead of "fixture / - / - / -".
6. **The GIF test could not see a shifted frame** (it compared mean differences, which a slow walk passes one frame
   off): decoded frames must now equal the quantized frames exactly (`gif.quantize`).

### Tests (215 -> 217)

`CoplanarTest`: the fixture has no ties in three views; a belt cube flush with the body front is found with exactly its
area (6 x 1 model pixels at 4x) and the pair (belt, body), and the preview shows the body there (file order).
`GifTest` now exact.

### Open items

- **G6b**: fix the coplanar faces above, the sash layers first (the sash carries the faction colour); then make
  `coplanar_ties` a `check` error over the stance's front, 3/4 and back (G7).
- From G2, still open: the face-plane light (A2), the fidelity test (A2), the sleeve's open end at 0.45 s in the swing
  (G5 or G6b).
- Not part of this step: the owner's request for more anime reference models by other authors (for the art-direction
  study; measure only, as with the WineFox set).

---

## G4: the core animation library (2026-11-12)

HEROES_V2.md 13.6 "Core library", 7.3 and appendix D, built on G3's generator. `_core.animation.json` now holds 55
animations (G3's three plus 52); every one passes `herogen check` on all six bases, and the katana style library gets
its own copies of the core's combat reactions (below). As in the earlier passes, no reference model's animation,
pose or timing is in the repository: the clips are authored here, with the references used only for the list of
animations a rich model has (G3's plan). The style is unchanged (the approved style pass 2 look); this pass only adds
motion.

### What exists

| Path | What |
|---|---|
| `herosrc/anim/core/locomotion.json` | G3's `move.idle`, `move.walk`, `move.run` (unchanged, byte-identical clips) and `move.crouch`, `move.swim`, `move.fall`, `move.climb` |
| `herosrc/anim/core/rest.json` | `rest.sit`, `rest.sleep`, `carry.loop` |
| `herosrc/anim/core/work.json` | the 16 `work.*` of 7.3 (`work.fish_wait` with its `.loop`) and `eat` (code 15, a copy of `work.eat`) |
| `herosrc/anim/core/emotes.json` | the 10 `emote.*`, `personal.1-3` (generics: a stretch, a look into the distance, a sheepish scratch) and `founding` (code 23) |
| `herosrc/anim/core/combat.json` | `hurt.heavy`, `stagger`, `stun` (+ `.loop`), `interrupt`, `dodge`, `death` (held), `defeated` (+ `.loop`), `spared` (code 22), `intro`, `victory`, `enrage` (code 19) |
| `herosrc/anim/poses/{react,move,work,emote}.json` | the poses they use (kneels, lunges, sits and lying poses with the floor fix; work strokes; gestures) |
| `anim/core_preview.py` | the previews below (`herogen anim-previews --core [--groups ...] [--check]`) |
| `tests/test_core_anim.py` | 28 tests (below) |

| Group | Animations (length) |
|---|---|
| movement (base loops) | `move.crouch` 1.2 s (a sneaking walk, knees 38 degrees), `move.swim` 1.2 s (breaststroke, body pitched 44 degrees, legs trailing), `move.fall` 0.8 s (arms flung up, legs paddling, a surprised face), `move.climb` 1.0 s (hand over hand, knee over knee) |
| rest and carry (base loops) | `rest.sit` 8 s (on the ground, one knee up, glances, breathing), `rest.sleep` 8 s (on the back, sleeping eyes, 4 s breaths), `carry.loop` 1.0 s (a loaded walk, forearms under the load) |
| work (actions) | `work.mine` 0.7, `work.chop` 0.8, `work.till` 0.8, `work.plant` 1.5 (kneels), `work.harvest` 1.2, `work.fish_cast` 1.1 -> `work.fish_wait` 0.5 -> `work.fish_wait.loop` 4 s -> `work.fish_reel` 1.2, `work.cook` 1.8, `work.smith` 1.0 (sparks), `work.build` 0.7, `work.read` 2.4, `work.pray` 3.0, `work.sweep` 1.4, `work.tend` 1.5, `work.eat` 1.8 |
| emotes (actions) | `emote.greet` 1.2, `bow` 1.6, `wave` 1.8, `laugh` 2.0, `angry` 1.5 (a stamp, an anger mark), `sad` 2.4 (a tear), `think` 2.4 (an idea at the end), `cheer` 1.4 (a hop), `talk` 2.0, `shrug` 1.3; `personal.1-3` 2.0-2.6; `founding` 2.0 |
| combat reactions (actions) | `hurt.heavy` 0.5, `stagger` 2.5 (a hop, down on one knee with stars, a dizzy circle, back up), `stun` 0.4 -> `stun.loop` 2 s, `interrupt` 0.6, `dodge` 0.55 (a tucked hop), `death` 0.75 held (buckle, a hard sit, onto the back; 1.0 s before the G4 review), `defeated` 0.9 -> `defeated.loop` 3 s -> `spared` 1.8, `intro` 1.4, `victory` 2.0, `enrage` 1.3 (a roar) |

Every action ends on the first frame of the clip that follows it (move.idle, a style's combat.idle, or its own
`.loop`), so GeckoLib's snap when the action controller stops (4.8) shows nothing. Faces come from `face_ctrl` keys
(pain, grit, happy, surprised, sleeping, a tear, a blush, chewing and talking mouths). Markers: `vfx:stars`
(stagger, stun.loop), `vfx:sparks` (smith), `vfx:splash` (reel), `vfx:anger`, `vfx:roar`, and the vanilla fishing
throw and retrieve sounds; all on whole ticks and written after the controller's blend (G3 review).

### What the generator gained (and why)

1. **Form-scaled keys** (`anim/clip.py`). One file serves every form because rotations transfer; pixel offsets that
   depend on leg length do not. G3's poses only bent the knees 10 degrees, where the compromise over the forms stays
   within half a pixel. A kneel drops the hips 5 px on a chibi and 11 on an adult, so any single offset leaves a
   chibi's knee 3 px in the ground or an adult's 3 px in the air. A key may now carry `k`: its value on a model is
   `v + k x v.fc_dhip`, where `v.fc_dhip` is the model's hip height minus 18.25 px (the middle of the forms' 12 and
   24.5). The writer turns such a component into the Molang string `"v + k * v.fc_dhip"`; GeckoLib 4.9.3 compiles a
   non-number keyframe component with `MathParser.parseJson` and evaluates it every frame, and an unknown variable is
   created on first use with the value 0 (`MolangQueries.getVariableFor`, read with `javap -c` in the jar whose
   SHA-256 4.2 records). So a model that never sets the variable plays the middle-form compromise (what G3's
   constants gave), and **A2's `HeroModel` makes every form exact by setting it in `applyMolangQueries`**:
   `MathParser.setVariable("v.fc_dhip", () -> leg_r.getPivotY() - 18.25)` from the baked model (the `leg_r` pivot is
   the hip height; no `visual.json` change). Only `root` position y is ever scaled (a test pins it). The readers
   (`render/animfile.py`, the GeckoLib port in `tests/test_anim.py`) evaluate the same form, and the quality rules
   evaluate it with each base's own value.
2. **The floor fix** (`poses.py`): `fix: ["floor"]` puts the lowest point of the base body (or of the bones in
   `floor`: the knee of a kneel, the seat of a sit, the back of a lying pose, a planted foot) on y 0 on every form,
   fitted as `a + b x v.fc_dhip` over the three forms (least squares; exact when the contacts scale with the legs,
   which is why the sit's legs rest on the heels and the lying pose keeps its legs flat).
3. **The root follows the floor between keys** (`recipes._follow_floor`): two pose keys on the ground whose legs
   differ (a squat, a kneel, a step) get root keys every tick fitted to the interpolated legs, since angles
   interpolated linearly shorten a leg along a curve and the feet would sink or float between the keys. Only the
   feet planted at both keys count (a foot that lifts or lands is free), so a step is not pulled to the ground early.
4. **Kneels without sliding.** The kneel's angles were solved with a two-link IK on the teen (angles hold on every
   form because the segments scale together): the front foot stays on its stance spot, the back foot lifts, steps
   back and tucks its toes. The transition goes through `kneel.r_lift` and `kneel.r_mid`; getting up reverses it.
5. **New rules** (`quality.py`): `steps: true` replaces the planted rule by the slide rule (a sole corner on the
   ground stays within 0.5 px of where it touched down until it lifts); `contact: body` takes the lowest base-body
   point (all but the head, which is 10 px deep on every form while a chibi's torso is 3.5, so a hero lying on its
   back rests its head up to 2 px into the pillow or the grass) for the ground rule and never lets it sink. A
   one-shot that leads into a `.loop` ends on the loop's first key; action loops close instead of settling; a held
   clip (`loop: "hold"`, death) stays on its last frame.
6. **Stance-relative keys and restyled reactions** (`recipes.py`, `library.py`). The pose `"@stance"` is the first
   frame of the clip an action plays over; `over: true` adds a pose to it, `over: [globs]` only for those bones (a
   kneel's legs absolute under a style's arms). A core recipe with `restyle: true` is expanded again in every style
   library that has a `combat.idle`, over that stance (unless the style defines the name), so a katana hero's
   stagger, stun, dodge, death or victory starts and ends on the katana stance with the sword in hand and no snap.
   `_style_katana` now holds those 15 copies; G5's styles get theirs automatically. Kneels and hops that would start
   from a style's own footing begin with a little hop or stumble (both feet off the ground for two ticks), so no
   foot slides from the style's footing to the kneel's.
7. **Skirt panels over the thighs.** In a sit or a kneel (a thigh 30 degrees or more off the hips), `skirt_f/b/r/l`
   follow the thighs at every pose key (the front panel lies over both thighs of a sit, a third of a single raised
   knee; the back panel flares behind a deep sit), counted from the stance's legs; a hakama no longer hangs through
   the ground.
8. **Primitives** (`primitives.py`): `crouch_cycle` and `carry_cycle` (G3's gait with a knee bend and a form-scaled
   root, or with the arms left to the recipe), `oscillate` (sine, one-way pulse or circle, windowed: waves, nods,
   shakes, pumps, stirring, a dizzy head, a swimmer's bob) and `flinch` (a damped impulse). The design's keyed
   motions (dodge, hurt, stagger, kneel, death, greet_bow, wave, cheer, work strokes) are pose keys with these layers
   on top; the attack primitives (swing, thrust, slam, spin, leap, land, cast, draw_bow, throw, guard, taunt,
   play_instrument) come with G5's styles.
9. **Base loops that leave the ground** (swim, fall, climb) set `planted: false`, `ground: false`.

### Conventions for A2 and the jobs (recorded in HEROES_V2 4.4, 4.7, 4.8)

- `HeroModel.applyMolangQueries` sets `v.fc_dhip` (above). Without it, kneels, sits, sleep and death are off by up to
  3 px on chibis and adults (the middle-form compromise).
- **Death lays the body down itself**: A2 returns 0 from `getDeathMaxRotation`, or GeckoLib's own death roll adds a
  sideways 90 degrees. The clip is 0.75 s (G4 review; it was 1.0 s, which with the blend ran past vanilla's 20 death
  ticks); `death` holds its last frame.
- **Sleep and sit are on the ground**, laid down by the clip (not vanilla's `Pose.SLEEPING`, which would rotate the
  model again): the entity stands at the hips, the body lies toward +z behind it (`rest.sleep`), the seat is at the
  entity's feet (`rest.sit`).
- **Chains**: the server plays `stun` then `stun.loop` (automatic, as `ultimate.loop`), `defeated` then
  `defeated.loop` then `spared` (or death), and `work.fish_cast`, `work.fish_wait` (its loop follows), then
  `work.fish_reel`; a code 0 after `work.fish_cast` would snap from the waiting hold to the idle.
- **Tools at item_r**: the handle along the hand's -z from the grip, the head at the far end with its striking side
  toward the hand's -y (a pick's points, an axe's edge, a hoe's blade): the work strokes are authored for it, the
  previews draw props that way, and the item layer must hold vanilla tools the same way. `carry.loop` expects the
  hauled item at `item_r` between the hands.
- **Swing-driven work** (`work.mine`, `work.chop`, `work.till`, `work.build`) and the hand emotes (`emote.greet`,
  `emote.wave`, `emote.talk`) key only the upper body (`waist`, `chest`, `neck`, the arms), like `attack.1`, so they
  can play while the base controller walks.

### Previews (`docs/heroes-v2/previews/anim/core/`)

Tansy (chibi_f: cat ears, a long tail, twin tails, bell sleeves, a skirt) and Garrick (adult_m: a ponytail, bell
sleeves, a long hakama), side by side, playing the written `_core` through the controller emulation of G2 (an action
starts with the 2-tick blend from the pose it cuts, so the chains play as in game) with HeroFace's frames and blinks.
`movement`, `work` (tools), `work_home` (the fishing chain, cook, read, pray, eat), `emotes`, `combat` (over
move.idle) and `combat_katana` (the restyled copies over combat.idle, sword drawn): a GIF at 20 fps (one frame a
tick) and a contact sheet each, and a README. Tools are preview props (an inline part at the grip, built in memory;
nothing is written to the hero files). `herogen anim-previews --core --check` fails on a stale preview.

### Measured

- Lowest base-body point over every clip on all six bases: within 0.6 px of the ground for every grounded clip
  (kneel contacts within 0.45 px: knee, toes and front sole), never sinking more than 0.6 px; no foot slides more than
  0.5 px on the ground; the core and the katana copies pass with no problem.
- Without `v.fc_dhip` the kneel and sit offsets are the middle form's: about 3 px off on a chibi and an adult, equal
  and opposite (a test pins it), which is what A2's variable removes.
- File sizes: `_core.animation.json` about 1.2 MB (55 clips, 15-37 KB each, like `move.idle`), `_style_katana`
  about 0.3 MB; loaded once. Per animated hero GeckoLib samples only the channels the playing clips key (21-39
  bones).

### Tests (217 -> 245)

`tests/test_core_anim.py`: the design's list is complete (55 names, 17 `work.*`); kinds and loop modes; one-shots
end on their loop's or move.idle's first key; `eat` is `work.eat`; markers and faces (stars, sparks, the throw sound,
sleeping eyes); G3's clips unchanged and unscaled; FORM_VAR per form and from a written model; scaled keys sample
per form; the Molang round trip (writer, reader, every form); only `root` position is scaled; the kneel touches on all
six bases though the drop differs by over 5 px; without the variable the compromise; sit, sleep and death rest on
the ground on all bases and the dead torso lies flat; kneel contacts in stagger, defeated.loop and plant; the crouch
keeps its soles down and really crouches; a skating foot fails the slide rule and a lifted step passes; a floating
seat fails; restyled reactions settle on the katana stance and differ from the core's; `@stance` without a stance
fails; skirts follow a sit's thighs; `oscillate`, `flinch`, `crouch_cycle`, `carry_cycle`; the written core plays
each form's sit at its own height; a chain blends into its loop without a pop. `tests/test_anim.py`: the GeckoLib
port evaluates Molang components (with the variable and without), and the katana set includes the restyled copies.

### Decisions (each deliberate)

1. **One Molang variable, not per-form files or constants**: one file for every form stays the rule; the variable
   degrades to G3's compromise where it is not set. Hip height alone is enough because the forms' leg segments scale
   together (the fits are exact within 0.1-0.4 px where the contacts scale).
2. **Lying poses fold at the hips** (torso back 90 degrees, legs forward) rather than rotating the root about the
   feet: the hips stay over the entity, the fall reads as sit-then-lie, and the contacts do not depend on the form.
3. **The head may rest into the ground by up to 2 px when lying** (excluded from the body contact), rather than the
   torso floating 2 px on a chibi.
4. **Restyled copies are generated, not authored per style**; a style that defines its own `dodge`, `intro` or
   `victory` (G5) replaces the copy.
5. **Shinto-style prayer** (two bows, two claps, a bow) for `work.pray`: the slice's prayer is a shrine maiden's, and
   the timing and poses are our own.
6. **Extra codes served by the core** (`eat`, `spared`, `enrage`, `founding`): appendix D codes no other file
   provides, so every code resolves (14.2) before G5 and the heroes exist.

### Open items

- **A2**: set `v.fc_dhip`; `getDeathMaxRotation` 0; items at `item_r` with the grip convention; play the chains;
  `carry.loop` with the hauled item; the procedural lag and the bake both treat lying heroes' hair as hanging (the
  sleep and death clips bake only the tail, ears and skirt, so a ponytail stays with the head).
- **G5**: style versions of `work.chop` and `work.mine` where it matters (a katana `work.chop`), its own `dodge`,
  `intro`, `victory`; the restyled copies cover the rest.
- **G10**: the mobility set (jump, land, leap, glide, fly...); `move.climb` and `move.swim` here are the generic
  ones, the climbers' and swimmers' variants come with it.
- **Polish**: bell sleeves show their open end when the arms are raised forward (swim, cheer, the recoil of a hit);
  the hip scabbard stays on the hips when lying (it sinks into the ground); a chibi's crouch is subtle (short legs).

## G4 review: tools that read as the job, death inside vanilla's ticks, the settle rule's test (2026-11-12)

A review of commit 16fbb16: every new clip rendered on Tansy (chibi_f) and Garrick (adult_m) from the side, the
front and the tool side (frames read one by one, not only the committed sheets), the written files loaded with
GeckoLib 4.9.3's own classes, and the quality rules sabotaged one at a time. No reference model was opened.

### Confirmed correct

- **The files, read by GeckoLib itself.** `_core` and `_style_katana` parsed with GeckoLib's `KeyFramesAdapter.GEO_GSON`
  (the jar on a plain JVM with the Minecraft classes of the NeoForm cache, `v.fc_dhip` set to the chibi, middle and
  adult values through `MathParser.setVariable`): all 55 + 18 animations load, every keyframe's end value (224 847,
  rotations converted back from GeckoLib's negated radians) equals herogen's reader within 0.0012, loop types match
  (`loop`, `hold_on_last_frame`, play once), and GeckoLib's event keyframes are exactly the timing files' ticks.
  The Molang roots, `"a - b * v.fc_dhip"` included, evaluate right in GeckoLib's own `MathParser`.
- Keys ascend in file order on every channel, start at 0, end inside the clip; every key is a `{vector, easing}` or a
  `{pre, post}` jump; easing names are GeckoLib's. Every one-shot ends on the first key of the clip that follows it
  (`move.idle`, the katana `combat.idle`, or its `.loop`) on every channel it keys, form-scaled roots included (a
  check written for this review, independent of `quality.py`). The timing files agree with the markers.
- Movement, rest, the emotes, the reactions and the chains read: the kneels touch on every base, the sit and the sleep
  rest on the ground, `stun` and `defeated` hand over to their loops without a pop, faces change where the emotion is
  the point. Only `work.fish_wait`, its loop and `work.sweep` had no face key; the sweep now smiles, the wait keeps
  the mood face (a calm wait is the point).

### Found and fixed

1. **The tool strokes did not read as the work.** The grip puts the handle along the hand's -z, so a tool points
   forward when the arm hangs and straight **up** when the arm is raised forward, and G4's poses did not correct for
   it: at `work.chop`'s contact the axe stood upright like a torch at head height (its head at y 42 on the teen,
   the trunk is at chest height); `work.till`'s hoe stayed level at hip height, 16 px above the soil; the broom of
   `work.sweep` was held like a lance at hip height, never touching the floor, with the left hand at the chest;
   `work.smith`'s hammer stood upright for 0.3 s after the blow; the rod of the fishing chain pointed straight up
   through the whole wait. The arms of these poses (`chop.*`, `till.*`, `smith.*`, `sweep.*`, `fish.*`, the plant's
   kneels) were solved numerically on teen_f (an offline Nelder-Mead over arm, forearm with its twist, and hand,
   seeded from the previous key of the stroke so keys interpolate smoothly, the wrist kept within 80 degrees of
   flexion and 25 of sideways bend) for these targets, then written into the pose files as plain numbers:

   | Pose | Grip (teen_f px; -z forward) | Handle | What it reads as |
   |---|---|---|---|
   | `chop.wind` | (-10, 33.5, 1), beside the right shoulder | back and out (-0.5, 0.3, 0.8) | the axe cocked behind the shoulder |
   | `chop.hit` | (4, 28, -7), in front of the chest | forward (0.1, 0.1, -1), edge to the left | flat into the trunk at chest height |
   | `till.raise` / `.strike` | (-2.5, 44, -0.5) / (-3.4, 18, -10) | back-down / down-forward 40 degrees | over the head, then into the soil a block ahead; both hands on the handle, the hips fold (`body` now keyed, still no leg) |
   | `smith.raise` / `.over` / `.strike` | (-4.5, 43, 1) / (-4.5, 42, -6) / (-3.8, 23.5, -12) | back / up / forward-up 29 degrees, face down-forward | behind the head, over the top (a new key at 0.35 s: the hammer had swung out to the side between raise and strike), the face on the work at anvil height (28.4 = one block) |
   | `sweep.right` / `.left` | (-6, 20, -4.5) / (1, 20, -5.5) | steeply down to the right / left | the bristles on the floor in front of the feet, 15 px across; the left hand above the right on the handle |
   | `fish.back` / `.flick` / `.hold` | over the shoulder / (-3.4, 30, -11) / (-4.6, 25.5, -8) | up-back / forward-up 50 / forward-up 28 degrees | the cast and the rod held out over the water; `fish.reel` puts the left hand on the reel |
   | `plant.kneel_poke` / `_pat` | (-3.7, 5.4, -11.4) | | a deeper bow (hips 40 degrees) so the hand reaches the soil (it stopped 10 px above it) |

   Rotations transfer across forms, the tools do not scale: on the chibi the hoe goes 5 px lower and the broom's
   bristles about 5 px into the floor, on an adult they stop 4 px above it (the teen is the reference; a hero or a
   style may override `work.sweep`, and G10's staff-broom is its own clip). `work.mine` and `work.build` were
   right and are unchanged.
2. **Death ran past vanilla's death.** `LivingEntity.tickDeath` removes the entity at deathTime 20 (HeroEntity
   keeps it), the action controller first blends 2 ticks, and `death` was 1.0 s: it reached its last pose on tick
   20-22, so the hero vanished as it hit the ground and never lay still. It is now 0.75 s (keys at 0.06, 0.18, 0.25,
   0.33, 0.52, a bounce at 0.58, still from 0.68): the body is down by tick 13, still from tick 16, the clip ends on
   tick 17. The katana copy follows (restyled).
3. **The dead head sank into the ground.** `death.lie` rolled the head 24 degrees on its side; a rolled 10 px head
   reaches deeper than the 3.5 px chibi torso it lies behind, 3.5 px into the ground (the body contact rule leaves the
   head out, up to 2 px allowed). The neck now tucks the chin (34, 8, 4): 1.8 px on a chibi, 0.5 on a teen, 0.2 on
   an adult.
4. **Forearms bent past straight.** Three solved poses ended a hair past 0 degrees of elbow flexion (`herogen check`
   caught them); written as -1.
5. **The settle rule had no failing test.** With `quality.check_settle` sabotaged to return nothing, all 79 animation
   tests passed: every test only checked clean clips. `SettleRuleTest` now builds an action that ends 10 degrees off
   `move.idle`'s first key and one whose last key comes early, and both must fail. The slide rule's sabotage
   (`check_slide` returning nothing) was already caught by `test_a_sliding_foot_fails`.

### Tests (245 -> 255)

`tests/test_core_anim.py`: `ToolTest` (7) pins what each stroke must read as on teen_f, with the grip convention of
4.7: the axe flat into the trunk at chest height with the edge leading left, cocked behind the shoulder at the
wind-up; the hoe raised over the head, then down into the soil a block ahead with the hips folding and no leg keyed;
the hammer head at anvil height with its face down, over the top of its arc (no sideways swing between keys) and not
upright in the rebound; the broom's bristles on the floor at every stroke key, 8 px or more across, both hands on the
handle; the rod 15-45 degrees above level over the water in the wait and the reel, back over the shoulder in the
cast's wind-up and forward-up at the flick; the pick behind the head at the raise and striking forward-down; the
planting hand down at the soil. Sabotage: with G4's sources, 8 of these 9 tool and death tests fail (all but the
pick's, which G4 had right). `DeathTest` (2): the clip and its blend end by tick 18 and the body is still at the end;
the dead head rests no more than 2 px into the ground on all six bases. `SettleRuleTest` (1): above.

### Decisions

1. **Tool poses are solved offline and written as numbers**, as G3 did for the katana swing; the targets are in the
   table above and the tests pin the outcome, so a later author can re-solve or hand-tune within them.
2. **The teen is the reference form for tools.** One file serves every form and only the root height is form-scaled
   (G4); scaling arm angles by `v.fc_dhip` would also be possible in Molang, but tools of one size cannot fit every
   form exactly anyway, and the item layer's scale is A2's.
3. **`work.till` keys `body`** (the hips fold): the torso alone could not bring the blade down to the soil. `body` is
   above the legs (they hang from `root`), so the stroke still plays over a walk.

### Open items

- **A2**: the item layer must hold tools exactly as the grip convention says (the strokes are solved for it), and its
  item scale decides how close the hoe and the broom come to the ground on each form.
- **G5**: style versions of `work.chop`/`work.mine` should be solved the same way (grip, handle and edge targets).
- **G10**: a broom clip of its own for Ambrose; per-form tool reach (the chibi's broom) if it shows in game.
- **Polish** (unchanged): bell sleeves show their open end when an arm is raised forward (the axe wind-up, the
  hammer strike, swim, cheer); the hip scabbard when lying.

---

## G6a and G6b: head and body parts, the coplanar rule, `lint` (2026-11-12)

HEROES_V2.md 13.3 (the slice column), 14.2 and 15.2 rows G6a and G6b, on the approved look (style pass 2, rig v3):
the parts keep the style of the existing library (strand hair kit, stacked-slab ears, chain tails, panel skirts,
flat painting, trims). As before, the reference models were used only for measurements; every part here is our own.

### The slice first (SLICE_PLAN 3)

| Hero | Parts (new in **bold**) |
|---|---|
| Natsuho (cat) | `hair/bob` with the new **`tufts`** and **`ahoge`** params, **`ears/cat_tufted`** (odd ears through the entry's new `left` slots), `tails/cat_long`, `outfit_top/jacket_open` (hem 0.5, the white wrap showing), **`outfit_bottom/shorts`**, `accessories/belt`, **`legwear/tabi_boots`** (`socks` 1: white thigh socks) |
| Yugen (fox) | **`hair/half_up_long`**, **`ears/fox`**, **`tails/fox`** (`count` 3, up to 9), **`robes/court_robe`**, **`headwear/fox_mask`** (worn on the side), `legwear/socks_shoes` |
| Benika (oni) | **`hair/wild_mane`** (red tips: the new strand `tip`), **`horns/single_curved`**, **`outfit_top/open_coat`** (chest wraps), `outfit_bottom/hakama` (short), `armour/gauntlets`, **`legwear/foot_wraps`** |
| Hinae (shrine) | **`hair/hime_long`** (paper tie), **`outfit_top/kimono_top`** (straight sleeves), `outfit_bottom/hakama`, **`legwear/geta`** |
| Kagenori (samurai) | `hair/high_ponytail_long`, **`outfit_top/haori`**, `outfit_bottom/hakama`, **`armour/sode`**, **`armour/bracers`**, `legwear/tabi_sandals`; phase 2 **`headwear/kabuto`** |
| Tobimaru (ninja) | `hair/high_ponytail_long` (short), **`headwear/half_mask`**, `accessories/scarf`, **`outfit_top/wrap_top`**, **`outfit_bottom/trousers`** (`wraps` 1), **`armour/bracers`**, **`legwear/tabi_boots`** |
| Ilsabet (knight) | **`hair/braid_crown`**, `armour/breastplate`, **`outfit_top/tabard`**, `back/cape_short`, `armour/gauntlets`, `legwear/greaves`; phase 2 **`headwear/visored_helm`**, phase 3 **`back/standard`** |
| Ambrose (witch) | **`hair/messy_medium`**, **`headwear/witch_hat`**, **`robes/long_coat`**, **`outfit_bottom/trousers`**, `legwear/boots` |

These looks are written down as the **sample outfits** (`herosrc/style/outfits.json`, with each hero's palette and
face from SLICE_PLAN 3). They are not hero files (C1-C4 write those); `lint` builds each on all six bases and the
sheet `previews/parts/outfits.png` shows them. Small details (the bell choker, prayer beads, talismans, the gourd, the
vial bandolier, the crow, crests and the calico patches) are G6c's accessories, markings and emblem stamps.

### The rest of the slice column (13.3)

- **Head (30, G6a)**: hair 12 (new: `hime_long`, `wild_mane`, `half_up_long`, `braid_crown`, `messy_medium`), ears 4
  (new: `fox`, `cat_tufted`), horns 2 (new: `single_curved`, a four-bone chain from the forehead; `twin_short`,
  stacked slabs on `horn_r`/`horn_l`), tails 3 (new: `fox`), wings 1 (new: `feathered`, three rows of feathers on
  `wing_r`/`wing_l`, half open), headwear 8 (new: `witch_hat` with its brim on its own bone, `fox_mask`, `half_mask`,
  `kabuto` with a three-lame neck guard and a V crest, `visored_helm`, `headband`).
- **Body (34, G6b; the column asks for 29)**: outfit tops 8 (new: `wrap_top`, `kimono_top`, `haori`, `tabard`,
  `open_coat`), bottoms 6 (new: `shorts`, `trousers`), robes 3 (new: `court_robe`, `long_coat`, `priest_robe`),
  armour 5 (new: `sode`, `bracers`), legwear 9 (new: `geta`, `foot_wraps`, `tabi_boots`), back 3 (new: `standard`).
- The visor of `visored_helm` is on its own bone (`visor`) hinged at the brow and raised `visor_up` degrees at rest, so
  the face stays clear; the hero's `phase.2` lowers it by turning that bone. The fox mask is on its own bone too
  (`fox_mask`), for a flip over the face. The kabuto and the helm sit over the hair (the hair below the bowl shows).

### Coplanar faces fixed (the G2 review's list, `previews/render/coplanar_faces.png`)

| Where | Cause | Fix |
|---|---|---|
| Maren's sash / waist plate (611 px), Garrick's and Tobin's sash / hakama band (495, 250) | the sash obi had exactly the extents of the layer under it | `sash_obi` and `sash_bow` are the **outermost waist layer, 0.6 px out** (skirt bands 0.3-0.4, armour waist plates 0.4); knots and bows moved with it. The sash now shows over Maren's armour (it was hidden in every preview) |
| Maren's greaves (477) and sabatons; Rowan's and the test rig's boots (foot / shaft) | both legs' shells crossed the centre line in one front plane; foot shells had the shaft's side and back planes | every leg shell's **inner side stops 0.05 px short of the centre line** (`max(0.05, min(e, hip_x - leg_w / 2 - 0.05))`), and **nested shells are 0.15 px apart** (shaft 0.3, foot 0.45, sole 0.6; greave 0.35, sabaton 0.5), in `greaves`, `boots`, `tights_boots`, `socks_shoes`, `thigh_socks`, `tabi_sandals` and the new legwear |
| ear slabs of Tansy, Tobin, the test rig | inner plates on the slab's front plane; the round ear's discs touching | inner plates 0.1 px proud and thicker; stacked discs overlap by 0.15 px |
| tail_4 / tail_5 | a chain curling in one plane keeps every side face at +-w/2, and two segments had the same width | the chain generator draws a box 0.1 px narrower (or shallower) when it is within 0.1 of the previous box |
| Rowan's chest | the scarf wrap on the jacket collar's side planes | the wrap is 0.15 px wider |
| test rig chest / fine_chest | the sailor bow's back face on the bust's front | the bow sits on the bust (`bust_d`), its back face inside it |
| Maren's back / chest, arm / arm | the fur mantle's top on the gorget's top; pauldron lames as deep as the cap | the mantle rises 0.25 px over the gorget; lames 0.3 px shallower |
| Wren's bangs / head, the fallback's sideburns | fringe bases' back faces on the head front; two sideburn strands in one layer | fringe bases reach 0.1 px into the head; the second sideburn is 0.15 px out |
| found by the new rule | jacket cuffs ending exactly at the wrist (their bottoms on the forearm's: the combat stance shows them), the breastplate's gorget as deep as a teen's chest | cuffs reach 0.1 px past the wrist; the gorget is never deeper than the chest minus 0.2 |

The **layer convention** that came out of it (px out from the body cube, so two parts worn together never share a
plane; a new part picks a free value): hips: bottoms' waistbands 0.15, top hems 0.25, belt 0.3 (and skirt bands
0.3-0.4 at the waist), armour waist plate 0.4, sash 0.6 (its depth lands 0.35 out on the hips); arms: sleeve cuffs
0.3, gauntlet shells 0.35, bracers 0.4 (rims 0.55), sleeve shoulders 0.55 (puffs 0.6), the sode's cap 0.7; legs:
gathers 0.2, boot shafts and sock cuffs 0.3, trouser thighs and greaves 0.35, foot shells 0.45, sabatons 0.5, wraps
0.55, soles 0.6; offsets that equal a base's own steps (chest minus arm depth is 0.5-0.75) are avoided.

### The sleeve's open end at 0.45 s of the swing

With the forearm pointing at the camera for one frame, the bell sleeve showed its inside as a lit ring and the light
cuff frill as a cogwheel. The bell panels' inner faces (`south` in strand space) are now painted two tones darker, the
frill's inner and bottom faces take the same lining, and the frill pieces are 1.6 px wide so they overlap into one
ring: end on, the sleeve reads as a shadowed opening round the hand. G4's note (swim, cheer and the recoil of a hit
raise the arms forward) gets the same look. The motion itself (the arm rising 100 degrees in a tick) is G5's.

### The coplanar rule in `herogen check`

`check` renders each hero (its own and, with `--style`, the fixtures) in its idle stance and, when it has a weapon, in
the combat stance with it drawn, in every phase it has, from six directions at 6x (`HeroAssets.tie_report`), and any
pixel where two coplanar faces of different colour tie is an error naming the bone pairs (`preview <id> --ties` shows
them in magenta). It adds about 1-3 s per hero; `--no-ties` skips it. G7's Java test does not repeat it.

### `herogen lint` (`lint.py`)

For every part (or the globs given): on each of the six bases, alone on a mannequin with short hair and a sword at the
hip, the whole build, the asset checks, every cube above the ground and the coplanar rule (idle and combat stances);
on the slim and large builds and at each param's minimum and maximum (smallest and largest base), assembly, packing
and the geometry checks; once, `conflicts` globs that name parts, declared `occupies`, and exact mirrors. Then the
eight sample outfits on all six bases with the full checks. Four worker processes; the whole library and the
outfits take about 10 minutes. The lint palette gives every slot its own colour, so a tie cannot hide.

### Parts format additions (`parts.py`, `gen.py`)

- Cube `tip` `{slot, frac, edge}`: the end of a strand (side-face rows and the end cap) in another slot.
- Hero entry `left` `{color, trim, alt, detail}`: the mirrored (left-hand) copies take these slots.
- Chain `copies`, `fan`, `fan_axis`, `copy_scale`, `copy_points`, `copy_sections`: a fan of chains (nine tails at 91
  bones on a mannequin, inside the 100 budget: the copies use a coarser three-bone chain); each copy gets its own
  spring chain in `visual.json` `secondary`; the middle copy keeps the rig's `tail_1..5` that the core library bakes.
- Paint op globs starting with `#` name the part's own cubes (`#bell*`: a lining).
- A bone named `mask` may sit in front of the face below the eyes plane (validator; a cloth mask covers the mouth,
  never the eyes).
- `strands` pass `tip` and `outline` through to their cubes.

### Sheets (`herogen parts-previews [--check]`, `render/partsheet.py`)

`docs/heroes-v2/previews/parts/`: `head_hair.png`, `head_ears_horns.png`, `head_tails_wings.png`, `head_headwear.png`
(every head part on the six bases, front left, and the back and the side on the teen), `body_tops_robes.png`,
`body_bottoms_legwear.png`, `body_armour_back.png` (every body part the same way), `outfits.png` (the eight slice
looks on the six bases and a back view). About 3.5 minutes; `--check` renders them in a temporary folder and fails on
a stale sheet. Regenerated with them: the G2 set (`render-previews`), the style set (`previews`), G3's and G4's
animation previews (the fixtures they draw wear the fixed parts). `coplanar_faces.png` is now the evidence of the
fix: no magenta on any of the seven.

### Tests (245 -> 259)

`tests/test_partslib.py` (14): the slice column's counts (30 head, 34 body); the static lint rules on every part; the
eight outfits build and pass the checks on chibi_f and adult_m; the sash outside every waist layer by at least
0.1 px on every base; leg shells short of the centre line for every legwear and bottom on every base; chain boxes of
different widths; strand tips paint the end rows and the cap; `left` slots on the mirrored copies only; nine fanned
tails inside the bone budget, one spring chain each, the fan symmetric; `#` paint globs; the bell sleeve's lining
darker than its outside; a `mask` cube below the eyes passes the clear-face rule, above it (or on another bone) fails;
sabotage: the G2 review's sash put back fails `check` with `sash/waist`, a sunk cube fails lint's ground rule.
`test_artdir`'s hair-top rule caught the half-up knot (lowered).

### Decisions

1. **Layering by convention and a check, not by an automatic nudge**: moving a face by a few hundredths in the
   generator would hide real mistakes (a sash under a waist plate); the rule finds them and the part says where it
   sits.
2. **The check renders stances, not every animation frame**: the idle and combat stances from six directions catch
   the shared planes (they are in the parts, not in the motion); a frame-by-frame check would cost minutes per hero.
3. **Sample outfits instead of hero files**: the slice's looks are tested and shown now, while C1-C4 still own the
   heroes (kits, phases, animations).
4. **Two tops are not stacked**: a hero's inner layer is painted by the outer part (`jacket_open`'s open front shows
   the wrap top), so `occupies` keeps one torso part; robes take the torso, sleeves and skirt slots together.

### Open items

- **G6c**: Natsuho's bell choker and calico patches, Yugen's prayer beads and the plum robe's crest, Benika's gourd
  and arm stripes, Hinae's talisman strips, Kagenori's crane crest, Tobimaru's kunai holster, Ilsabet's frost-lily on
  the tabard and the standard, Ambrose's vial bandolier and crow; the weapons in each hand pose.
- **A2 / C1-C4**: phase 2 of Ilsabet lowers the helm's `visor` (turn the bone by `visor_up`); Tobimaru's mask is hidden
  in phase 3 (the phase's `hide`); Yugen's tail count grows by phase (`p2_`/`p3_` copies of `tails/fox`).
- **G10**: the wings' loops (`sec.wings_*`) and Ambrose's staff-broom.
- The coplanar rule runs on the standard builds; slim and large builds get the geometry checks only (their widths
  could bring a part onto another's plane: a later lint option).

---

## G6c: details, weapons in their hand poses, patterns and emblem stamps (2026-11-13)

HEROES_V2.md 13.3 (the slice column: accessories, markings, weapons), 13.4 (patterns, emblem stamps) and 15.2 row
G6c, on the approved look (style pass 2, rig v3). This task was interrupted by a usage-limit pause and finished by a
second run: the first run had built the painter's patterns and stamps, the weapon carries and most parts; the second
judged them on renders, re-solved the hand poses so every prop clears the body on all six bases, enlarged what did not
read (the wand's paper, the fan, the talismans), added the tenth accessory, fixed the coplanar faces the lint found,
and wrote the tests, sheets and docs. As before, the reference models were used only for measurements; every part,
emblem and pose here is our own.

### What exists

| Path | What |
|---|---|
| `herosrc/parts/accessories/` (10 new) | `choker_bell` (Natsuho: ribbon band, bow, a bell with its slit), `prayer_beads` (Yugen: a bead loop over the chest, a big bead and tassel), `foxfire_lanterns` (Yugen: three glowing paper lanterns on their own bones `lantern_1..3`, at head height clear of the sleeves), `rope_belt` (Benika: a twisted rope with a knot and tufted ends), `gourd` (Benika: a lacquered gourd on its own sprung bone `gourd`), `talisman_strips` (Hinae: three ofuda hanging from the belt on the skirt bones), `talisman_ring` (Hinae's phase 2: seven glowing talismans on a ring round the chest on their own bone `talisman_ring`), `kunai_holster` (Tobimaru: a thigh strap, a pouch, three handles), `vial_bandolier` (Ambrose: a strap across the chest with four vials in his barrage colours), `crow` (Ambrose: on the left shoulder, bones `crow` and `crow_head` for a head-turn idle) |
| `herosrc/parts/markings/` (3) | `calico` (patches over the hair and the tail), `arm_stripes` (bands round the arms that glow in the phase look), `crest` (the hero entry's emblem on the back, the chest, both breasts or the sleeves) |
| `herosrc/parts/weapons/` (11 new) | `claw_gauntlet` (Natsuho, a pair: glove and plate worn always, three hooked claws when drawn), `folding_fan` (Yugen), `iron_club` (Benika, two-handed: `grip2`), `purification_wand` and `talisman_fan` (Hinae: right and left hand), `katana` (Kagenori's long sword, and his short one at `blade` 10), `short_blade` (Tobimaru, reverse grip) and `kunai` (his left hand), `longsword` and `kite_shield` (Ilsabet: right hand, left arm), `cauldron_staff` (Ambrose: also his broom, with a `mount`) |
| `herosrc/anim/poses/{fists,focus,hammer,staff,sword_shield,twin_blades}.json` | the hand poses of the six slice styles beside G3's `katana.*` (unchanged): a stance and two or three key poses each, and `ride.broom` (the seat on the staff-broom) |
| `tools/herogen/emblems.py` | the emblem bitmaps, drawn by hand at each size |
| `tools/herogen/grips.py` | the hand-pose checks: every weapon in each hand pose of its style |
| `paint.py`, `parts.py`, `gen.py`, `face.py`, `visual.py`, `build.py`, `render/scene.py`, `render/partsheet.py`, `lint.py` | the format and painter additions below |
| `herosrc/style/outfits.json` | the eight sample outfits now carry their weapon (and off-hand prop), accessories, markings and emblems |
| `tests/test_details.py` | 22 tests (below) |

The parts library has 94 parts (was 70): head 30, body 34, accessories 15, markings 3, weapons 12.

### Patterns, overlays and emblem stamps (13.4)

- **Patterns** (on a cube or a paint op, 1-2 colour slots and a `size`): `waves` (seigaiha: two rings per arc on a
  brick layout), `scales` (one rim per arc, a darker lower half), `hemp` (hemp-leaf: verticals and both diagonals
  every 2 x size), `petals` (five-texel blossoms on a staggered grid, the heart in the second slot), `patches` (soft
  blobs from a smooth seeded value noise: calico and piebald; with a `center` the noise is read by direction from it,
  so the inner and outer strands of a hair mass share one blob instead of speckling), `solid` (the whole region: a
  band of a marking). Cutout `lace` (scallops on the free edge and a row of holes above it).
- **Overlay paint ops** (`overlay: true`, the default for a stamp without its own slot): the op keeps each texel's own
  slot, material and shading and only adds its pattern or emblem, so calico patches go over any hair (every strand
  keeps its lit root and dark tip) and a crest over any coat. Paint ops also take `when` and `optional` (no matching
  cube is not an error: calico on a hero without a tail).
- **Emblem stamps** (`emblems.py`): `paw`, `flame`, `bell`, `crest`, `star`, `moon`, `leaf`, `lily`, `crane`, `plum`
  at 5, 7, 9 and (lily) 11 texels, and the small `eye`, `slit` and `script`. Each size is drawn by hand: a scaled
  bitmap at these sizes is noise. Characters: `#` the stamp's slot, `-` a tone darker, `+` the second slot (or a tone
  lighter), `o` the second slot a tone darker. A **cube** carries `stamp` {emblem, face(s), slot, slot2, size (default:
  the largest that fits), offset, rotate, flip}: centred on that face; a **paint op** carries one with a `center`
  point (it lands on whatever the op covers there: the crest across a haori's back). `"emblem": "@"` takes the hero
  entry's `emblem` (Ilsabet's lily on the shield, the tabard and the standard), and an entry without one gets no
  stamp. A mirrored (left-hand) copy stamps the mirrored face, flipped. A weapon's stamps are carried through the
  sheath's axis permutation (`parts.stamp_through`), so the shield's lily stays upright on her back.
- **Phase glow**: a cube or op with `glow: "phase"` glows only in the phase texture (`_p2`, `_p2_glowmask`); a hero
  with a later phase then gets the phase texture even without a palette change, and `visual.json` points that phase
  at it (Benika's stripes light up from phase 2).
- **Face**: `face_paint` style `whiskers` (two faint lines from the face's edge on each cheek, fading inward).
- `patterns_emblems.png` shows every pattern, cutout and emblem at its sizes, drawn straight from the painter.

### Weapons: hands, grips, sheaths, the off-hand and the mount point

A weapon is still authored once in grip space (the grip at 0,0,0, the head along -z, the striking side toward -y,
the convention G4's tools use). New part fields: `hand` (`right`, `left`: a shield; `pair`: claws; `both`: one prop
for two hands with the left on `grip2`), `style` (whose hand poses it is held in), `sheath`, `grip` (`reverse` turns
it 180 degrees about the hand's y: the blade leaves the little-finger side and lies back along the forearm), `vars`
(named values: `k = 0.6 + 0.4 x head_y / 42` shrinks a prop a little on the small forms, chibi 0.84), cube roles
`hilt`, `blade`, `worn` (a gauntlet's glove: on the hand bone always), cube rotations in grip space, `phase` cubes
(the staff's bristles from phase 2), `fine: "sheathed"` (a guard that is detail only on the carried copy),
`scabbard_stays` (a katana's saya hangs on its own `sheath` bone and stays at the hip while the blade is out).

| Sheath | Where (for a right-hand prop; mirrored for the left) | Used by |
|---|---|---|
| `hip`, `hip_same` | the opposite (or the same) hip, head down, swung back 55 degrees | longsword, fan |
| `obi`, `obi_same` | thrust through the sash at the front of the hip, hilt forward and up, edge up | katana (both swords), Hinae's wand |
| `back` | diagonally on the back, head down, hilt over the drawing shoulder | iron club |
| `back_up` | diagonally on the back, head up, its striking side outward | cauldron staff |
| `back_flat` | flat on the back, face out | kite shield |
| `waist_back` | across the small of the back | short blade |
| `worn` / `none` | the worn cubes stay on the hand / nothing is carried | claws / kunai, talismans |

- **Off-hand** (`art.weapon.offhand` {prop, sheathed, phase, sheath_offset, colours}): a second prop in the left hand.
  With `phase` N its drawn copy is a `pN_` child of `weapon_l` and its carried copy appears twice, as `pN_` under the
  sheathed bone and on a `holstered` bone that `visual.json` hides from phase N: before Kagenori's phase 3 the short
  sword rides at his hip, from phase 3 it is in his hand when he fights and back in its saya when he does not.
- A second carried prop whose sheath needs another parent gets **`weapon_back_2`** (Ilsabet's sword at the hip, her
  shield on the back); `visual.json` lists every sheathed bone (`weapon_back`, `weapon_back_2`) and counts a drawn
  bone when anything below it has cubes.
- **The mount point (for G10)**: a prop with `mount` {seat, height} and an entry with `"mount": true` get a `mount`
  bone under `root` (so a hover bob carries it), its pivot the seat at `height` above the ground (`hip_y - leg_d / 2`:
  under the thighs of a rider), with a whole copy of the prop laid along it head forward, bristles included from phase
  2. `visual.json` has `bones.mount` and `weapon.mount`; the preview renderer shows the mount only while riding (and
  then hides the hand and back copies). `ride.broom` is a placeholder seat (astride, thighs level) that checks the
  height on every form; G10 authors `move.ride` side-saddle on the same point.

### The hand poses (13.6 pose library; checked by `grips.py`)

G5 builds the style libraries on these poses, so G6c fitted the props to them first. Each pose was solved offline
(a Nelder-Mead over the arm, the forearm with its twist and the hand, wrists kept within 80 degrees of flexion, 35
sideways and a 60-90 degree twist) for targets on the teen (where the grip is and where the prop points), with the props kept out
of the body on every base (the head with a 1.5 px margin for the hair) and, for the club, the left hand on the
handle on every base; the numbers are written into the pose files. The first run's poses were authored for the teen
alone and failed on the other forms: a chibi's arm (9.75 px to the grip) is shorter than its head (10 px), so a wand
held upright under the chin, a cast with the prop pointing up, a sword drawn back over the shoulder or a club raised
overhead all ran into a chibi's head, and the club's second hand missed the handle by 2.4-3.8 px on the adults.

| Style | Poses (the props) | What changed in the second run |
|---|---|---|
| `fists` | stance (fists before the chin, claws forward-up), strike (arm straight, claws leading), guard (forearms crossed in front of the chin) | all three re-solved: the fists sat 0.1-1.6 px inside a chibi's head |
| `focus` | stance (the prop forward, up and out from the side of the chest; the talismans fanned low at the hip), cast (the arm straight, the wrist bent so the prop points at the target), throw (the left arm forward, its prop at the target; the right drawn back) | the cast pointed the prop up and back past the head; the stance held it upright under the chin |
| `hammer` | stance (the club over the right shoulder), raise (the club held high and upright at the right side), slam (bent forward, the club head down in front) | the raise is no longer overhead (a chibi cannot lift its hands over its head); the slam pointed the club forward and up; the second hand now within 1.8 px of `grip2` on all six bases |
| `staff` | stance (the staff upright beside the body), cast (the staff thrust forward, cauldron first); `ride.broom` | the cast pointed the cauldron up and back through the head |
| `sword_shield` | stance (the shield upright before the chest, facing the target), cut (the blade level at the target past the shield), guard (the shield high, the sword hand by the right temple, blade forward over the shield) | the guard's sword went through the head; the shield touched a chibi's face |
| `twin_blades` | stance (the reversed blade back along the forearm, the kunai forward), slash, throw (the kunai at the target) | the kunai pointed up instead of at the target |

What `grips.problems` checks, for a hero with a weapon, in every hand pose of its style (held for a second, so the
secondary bake hangs sleeves and capes): no prop cube more than 1 px inside the head, torso, legs or the other arm
(the holding hand and forearm excepted, and both for a two-handed prop); nothing more than 1.5 px under the ground;
the second hand within 2 px of `grip2`; with `ties`, no coplanar faces of different colour with the weapon drawn
(six directions for the stance, three for the others). `herogen lint` runs it on every weapon part and every sample
outfit, on all six bases.

Rotations transfer between the forms and lengths do not, so contact points differ: the slam's club head is on the
ground on a chibi and 6 px (teen) to 9 px (adult) above it; the staff's foot touches a chibi's ground and hangs 5-10
px above it on the larger forms. One file serves every form, so these are the price; G5 can add a form-scaled root
(`v.fc_dhip`) to a slam's impact frame if the gap shows in game.

### Fixes found on the renders and by the lint

- The wand's paper streamers were 0.15 px plates that vanished: now two zigzags of four 1.8 x 0.9 x 1.85 px folds.
  The fan is wider toward its top (1.1, 2.0, 2.8 px). The waist talismans were four overlapping strips whose dense
  script read as a red-and-white checker: now three strips a little apart, and the `script` emblem keeps the strip's
  sides plain.
- Coplanar faces the lint found (each moved 0.05-0.2 px off the shared plane): the choker's loop and bell on the
  chest's front (chibi_f, teen_m); the rope belt's top on the hips' top (every base); the claws' outer faces on the
  gauntlet plate's, and the glove's top on the wrist (a cuff now reaches 0.25 px past it); the kite shield's top rim
  overlapping the main rim by 0.2 px in one plane; the kunai handle's end on a 3 px hand's back face (teen_m,
  adult_f); the fan's rivet end on the stick's end on the adults; the staff's bristle skirt and core sharing their
  top; and, newly visible with Kagenori's sword drawn, the G6b bracers' lower rim on the forearm's bottom face (it now
  reaches 0.1 px past the wrist, as the G6b cuffs do). Hinae's wand carried across the small of the back sat on her
  sash bow's loop: it is thrust through the sash at the hip (`obi`) instead. The talisman ring's front talismans
  reached in front of a slim chibi's chin: the ring is never higher than 5.75 px under the chin.
- The hand-pose coplanar check counts only pairs with a prop bone. Held poses with no secondary motion on `hair_back`
  showed a tie between every hair style's `back_base` (its inner face at head-local z 5) and the head's back face,
  through the jagged hem's teeth; the idle's bake rotation (-0.007 degrees) hides it in `herogen check`. It is a hair
  kit matter (open item below), not a weapon's.
- Patterns redrawn after the first sheet: `waves` and `scales` are now overlapping discs on a brick lattice (the
  first draft drew a grid of crosses), shown at sizes 6 and 4 where they read; the crane emblem is a crane in flight
  seen from below (the first draft's roundel did not read at 5-9 texels).
- The library test carried every weapon as a pair at the hip: a staff hung there swung its foot up in front of the
  face. Weapons are now linted and tested as their part says (`lint.weapon_entry`).
- A hip sheath whose `weapon_back` a part already used now gets `weapon_back_2` (it used to be an error).

### Sheets (`herogen parts-previews`, `docs/heroes-v2/previews/parts/`)

New: `details_accessories.png` (every accessory and marking on the six bases, cropped to where it is worn),
`weapons.png` (every weapon part drawn in the stance of its style on the six bases, then from the right and
sheathed from the front and the back), `weapons_poses.png` (each slice outfit on its own base: sheathed front and
back, every hand pose of its style from the front left and the right front, and its special look: claws away, the
lanterns at night, the stripes glowing, the far view, both swords, the standard, the staff ridden) and
`patterns_emblems.png`. Regenerated: `outfits.png` (weapons sheathed, with the details), `body_armour_back.png`
(`gauntlets` gained `reach`, `bracers`' lower rim, `standard` stamps the emblem) and `body_tops_robes.png`
(`open_coat` paints the bare forearms as skin); the head sheets and `body_bottoms_legwear.png` came out byte for byte.
On the sheets a cell is as wide as its label, long param lists wrap, and the crest is shown in red on the mannequin's
cream shirt (its own default colour would vanish there).

### Tests (269 -> 291)

`tests/test_details.py`: the slice column (10 accessories, 3 markings, a weapon for each slice hero); every slice
weapon names a style with hand poses; emblems well formed, rotation, size fitting, every emblem a source names
exists; the new patterns draw both colours, patches seeded and deterministic, the lace cutout; a cube stamp paints
its emblem centred; the crest takes the entry's emblem or none; stamps stay upright through a carry; whiskers; calico
is an overlay that changes part of the hair only; arm stripes glow only in the phase look and `visual.json` points
the phase at the phase texture; the talisman ring is a phase-2 glowing ring clear of the arms; the reverse grip; worn
claws; the saya that stays and the short sword from phase 3 (`holstered` hidden by the phase); the shield on the
left and flat on the back with its emblem facing out; the staff-broom's mount point (bone, height, head forward,
hidden unless riding, bristles from phase 2); **every slice weapon in every hand pose of its style on all six
bases**; the props point where each pose says on the teen; sabotage: the first draft's cast runs the wand into a
chibi's head and the check catches it. `test_style` and `test_parts` updated for the carries above.

### Decisions (each deliberate)

1. **Poses are solved for every form at once**, not for the teen alone: the check runs on all six bases and the
   chibi's short arms set the limits (a raise beside the head, props pointing away from the face).
2. **The mount is a second copy of the prop** on its own bone rather than the hand's copy moved at run time: no
   re-parenting in `HeroModel`, and a riding animation keys the rider, not the prop.
3. **Emblems are bitmaps drawn per size** (two to four sizes each) instead of a scaled vector: at 5-11 texels only a
   hand-placed texel reads.
4. **An off-hand from a phase has a holstered copy hidden by the phase** (`phases[].hide`), so the existing
   `HeroLook` phase rules show the right one with no new engine field.
5. **Kagenori's two swords are one part** (`katana`, `blade` 16 and 10) and Tobimaru's kunai is its own part (the
   holster accessory carries three more), so the slice's ten weapon looks are eleven props.

### Open items

- **A2 / G10**: `HeroModel` must hide `visual.json` `bones.mount` unless the hero rides (today it would draw the
  mounted staff under Ambrose's feet; no hero in the mod's resources has a mount yet, so nothing shows now), and hide
  the hand and back copies while riding. `weapon_back_2`, `sheath` (always shown) and `holstered` (hidden by the
  phase) already work through the lists `HeroLook` reads.
- **G5**: the style libraries on these poses; the slam's contact on the larger forms (above); orbit loops for
  `lantern_1..3` and `talisman_ring`, the crow's head turn, the gourd's swing (its `secondary` chain).
- **C1-C4**: the heroes' own files take the sample outfits' entries (weapon, off-hand, emblem, phases).
- The coplanar rule still runs on the standard builds only (slim and large: geometry checks).
- **Hair kit (a later art pass or G7)**: every hair style's `back_base` has its inner face on the head's back face
  (head-local z 5); through a jagged hem the two show and z-fight in any frame where `hair_back` is at rest. Moving
  each `back_base` 0.1 px back fixes it; it changes the test rig, the fallback and every preview, so it was left out
  of this task.

---

## G5: the style libraries of the slice (2026-11-13)

HEROES_V2.md 13.6 "Style libraries" and 15.2 row G5, on G3's generator, G4's core and G6c's hand poses. Three runs
were interrupted (container restarts and a usage-limit pause). The first two wrote katana, fists and focus and the
generator additions. The third reviewed that work (the bugs under "Found and fixed"), wrote hammer, twin_blades,
sword_shield and staff, the previews, the tests and these notes, and stopped just before its commit. A fourth run
repeated the checks and fixed the four stances that `herogen lint` caught (under "Found at the commit check"),
and was stopped in its turn while the preview checks ran. A fifth reran the checks, regenerated the two weapon
sheets the stance fix had made stale and committed. The approved look (rig v3, style pass 2) is unchanged: this
task adds motion only. As before, no reference model's animation, pose or timing is in the
repository.

### What exists

| Path | What |
|---|---|
| `herosrc/anim/styles/{fists,focus,hammer,katana,twin_blades,sword_shield,staff}.json` | the seven slice libraries: `restyle_arms`, the file's own `poses` (the in-between keys of its clips; the hand poses stay in `herosrc/anim/poses/<style>.json`) and `animations` |
| outputs | `_style_<style>.animation.json` and `hero_timings/_style_<style>.json` for the seven (`_style_fists` was the empty file of `_fallback`'s style; katana grew from 17 clips to 25; the other five are new) |
| `anim/library.py` | `style_poses` (a style file's poses join the pose library for its clips and its heroes' own files) and `restyle_arms` |
| `anim/recipes.py` | `plays_over`, `arms_over`, and the error for a settle or overshoot key that lands after the next pose key |
| `anim/primitives.py` | `run_cycle` with `upper: 0` (the legs and torso of the run; the arms are left to the recipe's carry pose: `combat.run`) |
| `grips.py` | `animation_problems` (the props through every tick of a library's clips) and the second hand's slide along the handle |
| `anim/style_preview.py`, `herogen anim-previews --styles [--check]` | the previews below |
| `herosrc/anim/poses/twin_blades.json` | the crouch now uses the form-scaled `floor` fix (below) |
| `herosrc/anim/poses/{focus,hammer,staff,sword_shield}.json` | the stances' head bowed 3 degrees more (focus, hammer, staff) and the sword wrist turned 3 degrees sideways (sword_shield): the coplanar rule (below) |
| `tests/test_styles.py` | 18 tests (below) |

### The seven libraries

Every library has `combat.idle`, `combat.run` (the core run's legs and lean with the style's carry: the run while
the weapon is drawn, decision 7), `draw`, `sheathe`,
`attack.1-3`, `guard`, `guard.loop`, `guard.hit`, `guard.hit.loop` (a copy of `guard.loop`: the guard stays up after a
blow until the next code), `dodge`, `intro`, `victory`, `taunt`, and the twelve restyled core reactions over its own
stance (G4). 24-25 clips each.

| Style | Slice prop (preview heroes) | Stance and string (the hit at clip time) | Ranged | Draw / sheathe | Guard | Dodge, intro, victory, taunt | `combat.run` |
|---|---|---|---|---|---|---|---|
| `fists` | Natsuho's claws (Natsuho on chibi_f and adult_f) | guard up; lead jab 0.40, cross 0.40, claw rake 0.45 | - | the claws flexed out (`weapon:drawn` 0.35) / shaken in | forearms crossed | a cat's spring back; the claws knocked together; a fist pump with a fang grin; a neck crack and a beckon | fists high and pumping |
| `focus` | Hinae's wand and talismans (chibi_f), Yugen's fan (adult_m) | casting ready; one-hand cast 0.40, off-hand flick 0.40, two-hand burst 0.45 | overhand toss 0.40 | from the hip, a flourish | a ward, the prop upright and the palm out | a light hop; a bow over the prop; the prop raised with a twirl; a wagging hand | the prop trailing like a baton |
| `hammer` | Benika's club, both hands (chibi_f, adult_f) | the club on the shoulder; overhead 0.45, side swing 0.45, slam 0.55 | - | over the right shoulder from the back | the club as a bar | a heavy hop; the club levelled one-handed at the enemy; hoisted with a laugh; bounced on the shoulder with a beckon | the shoulder carry |
| `katana` | Kagenori's long sword (chibi_m, adult_m) | low ready; diagonal 0.40, level 0.40, thrust 0.45 | - | the quick draw from the obi into a draw-cut; the chiburi and the slide home | the blade across | a hop; a salute; the chiburi and the lowered blade; a beckon | the blade trailing |
| `twin_blades` | Tobimaru's reversed blade and kunai (chibi_m, adult_m) | crouched; backhand slash 0.40, kunai stab 0.40 and slash 0.60, the whirl 0.45 | the kunai 0.40 | the blade from the small of the back, the kunai from the thigh holster | crossed before the face | a low hop; the blade raised before the face; the blades lowered, eyes closed; a kunai twirl | the ninja run, arms back |
| `sword_shield` | Ilsabet's longsword and kite shield (chibi_f, adult_f) | shield forward; diagonal cut 0.40, level cut 0.40, shield bash 0.45 | - | the sword from the left hip and the shield from the back; a flick and the slide home | the shield high | a step-hop behind the shield; a salute; the sword raised; the sword tapped on the rim | behind the shield |
| `staff` | Ambrose's cauldron staff (chibi_m, adult_m) | caster ready; forward cast 0.40, upward cast 0.45, the bonk 0.50 | a vial 0.40 | from the back | the staff as a bar | a startled hop; the staff planted with a flourish; raised with a cheer; a beckon, leaning on the staff | the staff upright beside the body |

Work strokes where they matter (13.6, 7.3): the hammer's `work.mine` (Benika is a miner: the hammer's raise and
overhead strike with a pick at `item_r` and the left hand on its handle) and the katana's `work.chop` (the level cut
of `attack.2` with an axe, the edge leading, as 7.3's example asks). Both play over `move.idle`, settle on it and key
the upper body only, like the core's strokes. The other styles use the core's.

The style's own `enrage` replaces the restyled one where the core's roar did not fit: the twin blades' deep crouch
(the roar bent the legs from its own footing and slid a foot), the hammer (the hunch put the head into the club on the
shoulder) and the staff (the arms flung down and back drove the staff's foot into the ground). The fists' was written
by the first run (the roar with the root an offset over the stance).

**Markers and timing.** Every string attack is class light. The wind-up meets the 0.4 s minimum before the first hit
and the recovery the 0.3 s after the last. The timing file gives the server's wind-up as the hit's clip time plus the
action controller's 2-tick blend: 10-11 ticks for the strings, 13 for the slam. The heavy weapons take longer: the
hammer's attacks last 1.05-1.35 s, the blades' 0.75-1.0 s. `draw` and `sheathe` carry `weapon:drawn` and
`weapon:sheathed` on the tick the hand reaches the carried prop, and the timing files list them, so the server swaps the
weapon flag (the drawn and carried copies, 4.3) on the frame the swap shows. The swap tick is placed where the copy in
the hand is nearest the carried one. A blade drawn from an obi swaps once it has started to slide out (katana 0.25 s,
sword 0.35 s): at the grip the blade still lay through a chibi_m's waist. A shield cannot be in the same place on the
back and on the arm, so it pops in the middle of a swing. Marker sounds are vanilla events (SOUND.md 5: the style
libraries keep vanilla sounds until a style kit exists): `entity.player.attack.{sweep,strong,weak,knockback,nodamage}`,
`entity.evoker.cast_spell` and `.prepare_attack`, `entity.snowball.throw`, `entity.splash_potion.throw`,
`item.armor.equip_{iron,chain,leather}`, `item.book.page_turn`, `item.shield.block` and `block.wood.place`. `vfx:`
names (`slash_arc`, `thrust`, `impact`, `ground_slam`, `cast`, `burst`, `claw_jab`, `claw_marks`, `roar`) have no
particle handler yet (A2).

### Generator additions

1. **Style poses**: a style file's `poses` are the in-between keys of its clips (a wind-up peak, a follow-through, a
   reach to the carried prop). They join the pose library for that style's clips and for its heroes' own files (C1-C4
   can reuse them). A name already in `herosrc/anim/poses` is an error. The hand poses that `grips.py` checks stay in
   the pose library.
2. **`plays_over`**: the base clip an action starts over, when it is not the one it settles on. `draw` starts over
   `move.idle` (sheathed) and settles on `combat.idle`; `sheathe` does the opposite; work strokes play over and settle
   on `move.idle`.
3. **`run_cycle` with `upper: 0`**: the core run's legs, lean and bob, with the arms left to a carry pose (a club on the
   shoulder, a shield before the chest).
4. **`arms_over` and `restyle_arms`**: the restyled reactions add their arm offsets to the guard, so a death, a kneel or
   a dizzy stagger folded the weapon into the head. In the renders and the clearance check: the katana 3 px into a
   chibi's head in `death`, the claws 2-4.6 px in `stagger`, `defeated` and `death`, the fan and the wand in `death`.
   A style file's `restyle_arms` {pose, clips} gives those copies the recipe field `arms_over`: keys with `over` add
   their arm offsets to that pose's arms, while the `@stance` keys keep the guard, so the clip still starts and ends
   on the stance. Every style lowers its arms (`stance.relaxed`) in `death`, `defeated`, `defeated.loop` and
   `stagger`. Focus uses `focus.rest`: the prop points down and forward, off the kneeling thigh. The staff uses
   `staff.rest` (its foot rides up behind) and also lowers its arms in `hurt.heavy`, `stun` and `stun.loop`: the
   cauldron stands beside a chibi's head and the foot near its shins.
5. **A settle or overshoot key after the next pose key is an error.** The first runs held each wind-up with `settle:
   -0.06`. Its extra key landed 0.15 s later, after the hit, and pulled the arm back to the wind-up for a frame: the
   claw rake at 0.50 s, the focus cast at 0.45 s and the burst. The recipe expander now refuses such a key.
6. **The twin blades' crouch** (knees 26) uses the form-scaled `floor` fix (feet only). The three-form compromise of
   `ground` left a chibi's soles 0.64 px in the ground and an adult's above it (the 0.6 px rule failed on four bases
   for `combat.idle` and every restyled reaction). The hand pose is otherwise unchanged and passes G6c's checks.
7. **`grips.animation_problems`** (written by the first run, now tested): the body, ground and second-hand measures of
   the hand-pose check through every tick of every clip, with the action layered over its base clip and the weapon
   drawn or sheathed as its markers say. Lying reactions may rest the prop on the ground. Two-handed props must stay
   in both hands in `combat.idle`, `combat.run`, the attacks and the guard, within 4 px between keys (`ANIM_GRIP2`, was 3:
   each arm interpolates on its own chain, so between two keys with the hand on the handle it drifts off for a frame or
   two, and the slam's recovery drifts 3.0-3.9 px even through a solved waypoint). The second hand may now **slide
   1.2 px along the handle** either way of `grip2` (`GRIP2_SLIDE`). The left hand of the hammer's bar sat 3.5 px from
   `grip2` on a chibi_m, but on the handle, a little up it. The hand-pose check (2 px) uses the same measure.

### Found and fixed (the interrupted runs' work)

- The settle keys after the hit (above): `fists` attack.3, `focus` attack.1 and attack.3.
- `focus` did not build: attack.3's sound marker sat at 0.36 s, off the tick grid.
- `focus` attack.1 and attack.ranged overshot the straightened elbow to 8.7 and 7.3 degrees of hyperextension.
- The restyled reactions with the weapon in the face (above).
- The katana's draw: the swap at 0.20 s left the blade 1.2 px inside a chibi_m's waist; it now swaps at 0.25 s.
- The pose solver now keeps every elbow at 1 degree of flexion or more (the joint limit is 0-150).

### Found at the commit check (the fourth run)

`herogen lint` failed on seven weapon parts (42 problems), all with the coplanar rule, which `lint` and `check` run on
the first frame of the style's `combat.idle` with the weapon drawn (`HeroAssets.stance`). Before G5 these four styles
had no `combat.idle`, so the rule saw `move.idle` instead. The interrupted run had started this lint just before the
pause.

- **focus, hammer and staff** (the wand, both fans, the club, the cauldron staff): `hair_back/head` 54-120 px at 6x.
  These stances held the head level (the body, chest and neck pitches summed to 0), so the bake left the hair at rest,
  and the hair kit's `back_base` z-fights with the head's back plane through the jagged hem (the hair-kit item of
  G6c). The katana, fists, twin_blades and sword_shield stances bow the head 1-3 degrees and the hair hangs off it.
  The three stances now bow the head 3 degrees more (`neck` pitch 0 to 3, the hammer's -4 to -1; a caster's eyes on
  the target): the bake hangs `hair_back` 1.2 degrees back through the whole loop. The hand poses are unchanged.
- **sword_shield** (the longsword and the kite shield): `forearm_r/hand_r` 8-35 px. The stance's sword wrist was
  flexed with no sideways turn (`hand_r` [24, 0, 0]), so the hand's side faces lay in the forearm's planes and
  overlapped at the wrist. It is now [24, 0, 3]: the blade leans 3 degrees.

The four libraries were rebuilt (the timing files change only their source hash: no hit, marker or frame moved), the
hand-pose and clip clearance checks rerun on all six bases, and their previews re-rendered, with the parts sheets
`weapons.png` and `weapons_poses.png` (they draw each slice weapon in its style's stance and hand poses) and
`render/turnaround_style_maren.png` (Maren's drawn sword is in the sword_shield stance). A new test
(`test_the_combat_stance_passes_the_coplanar_rule`) runs the rule on each slice weapon in its style's stance.

**Not fixed here: part geometry that the combat stances expose.** After these fixes the full `herogen lint` still
reports 63 coplanar problems, all outside the weapons: `legwear/boots`, `greaves`, `socks_shoes`, `thigh_socks`,
`outfit_bottom/shorts` and `flared_skirt`, `armour/gauntlets`, and the sample outfits of Ambrose, Ilsabet, Natsuho,
Tobimaru and Yugen. None is in a G5 file. The lint now stands every part in a real combat stance: a part's mannequin
has no style, so it takes `fists`, whose `combat.idle` was empty until G5, and each outfit takes its own style's. These
stances spread the legs and raise the forearms, and that shows faces that `move.idle` keeps hidden. The causes, by the
cubes the tied pixels belong to:

1. **Inner sides of nested leg shells** (most of the pixels; Tobimaru's shins up to 1240 px at 6x): G6b's clamp
   `max(0.05, min(e, hip_x - leg_w / 2 - 0.05))` keeps a shell from crossing the centre line, but the gap between the
   legs is 0.1-0.15 px on every base. So every shell's inner face lands at 0.05 px whatever its layer: boots `cuff` and
   `shaft`, trousers `gather` and `wrap` against boots and `tabi_boots`, `socks_shoes` and `thigh_socks` `sole`,
   `strap` and `shoe`, shorts `cuff` and `leg`, `tabi_boots` `band` against shorts, the kunai holster strap against
   the trousers. The fix belongs to the layer convention (inner sides that keep their order, or that stop inside the
   layer under them). The test rig's `tights_boots` uses the same clamp, so the fix rebuilds the test rig and its
   previews.
2. **Knee sides** (`thigh_l` / `shin_l`, 4-50 px; greaves, thigh socks, Ilsabet, Natsuho): a bent knee turns the shin
   about x only, so its side faces stay in the thigh's planes and overlap in a small wedge at the knee. Painted in two
   colours, the wedge flickers. The walk and the run bend the knee as well, so it shows in game already. This is a
   rig-level question (a shin 0.05 px narrower than the thigh, for instance), and the fix rebuilds every hero.
3. **Bracer rims and jacket cuffs** (Tobimaru's forearms, 280-470 px): both end 0.1 px past the wrist (G6b's cuff
   rule, G6c's rim), so their bottom faces tie wherever a raised forearm shows them. One of them needs another value.
4. Small ones: `gauntlets`' hand plate against the plain sword's grip in the fists guard on the chibis (8-24 px), and two
   `flared_skirt` back panels on chibi_f (4 px).

### How the poses were made

As in G4 and G6c: each in-between pose was solved offline for grip, prop-direction and edge targets on the teen. The
solver is a Nelder-Mead over the arm, the forearm with its twist and the hand, with the wrists within the G6c limits.
It keeps the props, hands and forearms out of the body and every prop at least 0.8 px above the ground on the chibi,
teen and adult. For the club, it also holds the left hand on `grip2`, over four forms where one form was not enough.
The numbers are written into the style files. The clips were then checked through every tick on all six bases.
Recoveries got waypoints where joint-space interpolation took a prop through the body. The hammer's guard swings the
club forward off the shoulder before it turns it into a bar, and its slam and side swing come back up through a
two-handed haul. The staff's bonk comes back up through a high twirl. The twin blades' sheathe swings out to the right
before it reaches behind the back.

Contacts differ by form, as G6c measured for the hand poses: the slam's club head reaches 1.7 px from the ground on a
chibi, 6.1 on a teen and 8.9 on an adult, even with the knees giving 10 degrees (a form-scaled root keeps the soles
down). A deeper drop on the larger forms would need form-scaled angles, which the files do not have (only the root
height is scaled).

### Previews (`docs/heroes-v2/previews/anim/styles/`)

For each style, the chibi and the adult of its preview heroes (the sample outfits of `herosrc/style/outfits.json`,
holding their slice props): `<style>_string.gif` (attack.1, attack.2 and attack.3 back to back over `combat.idle`, as
the server sends them), `<style>_set.gif` (draw from the idle, guard and its hit, dodge, attack.ranged, taunt, intro,
victory, sheathe, `combat.run`, the work strokes with preview tools) and `<style>.png` (a contact sheet of every clip the
style defines, 7 frames each). They play the written files through G2's controller emulation, with the weapon flag
swapped on the draw and sheathe markers and the base controller's 4-tick blend between `move.idle` and
`combat.idle`. `herogen anim-previews --styles --check` fails on a stale preview.

### Tests (291 -> 309: `tests/test_styles.py`, 18)

The full list in each style (ranged and work where expected, every attack class light with a hit, settling on the
stance, every restyled reaction present); the quality rules and the timing agreement on all six bases for the seven;
each string's server wind-up is its hit tick plus the blend, and the twin blades' attack.2 has two hits; draw and
sheathe swap the weapon flag and settle where they should; the strikes leave the legs to the walk (the slam bends the
knees with a form-scaled root). On the teen: the slam drives the club head from over the shoulder to under the hands,
the side swing crosses the front to the left, the shield bash drives the shield forward with its face leading, the casts
and the kunai point at the target, the twin blades alternate hands, the hammer's `work.mine` and the katana's
`work.chop` hold the tool as the core's strokes do. The slow one (about 4 minutes): every sample outfit's props
through every second tick of every clip of its style on all six bases. Sabotage: the claws' stagger without
`restyle_arms` folds them into the head; the first run's settled rake fails to build. Also: `arms_over` and an unknown
`arms_over` pose, the deep crouch's soles on the ground on every base, the previews' heroes hold the style's prop,
and each slice weapon in its style's combat stance passes the coplanar rule with the hair hanging off the head (the
fourth run's fix; with the first stances it found the seven parts `lint` found). `test_anim`'s katana set now counts the style's own clips (the G5 list) beside the restyled reactions.

### Decisions (each deliberate)

1. **Basic attacks are class light** for every style, with longer clips for the heavy weapon, so a club reads heavy
   (1.05-1.35 s) while the server's basic-attack rules stay one class.
2. **The strikes key the hips and the upper body**, except the hammer's slam, which bends the knees with planted feet.
   A hero can swing while the base controller walks it, as G3 decided for the katana.
3. **The restyled reactions lower the weapon arm** (`restyle_arms`) rather than being authored seven times twelve.
   The arms differ from the core's by the stance only where the guard can fold into the body.
4. **Work strokes only where they matter**: Benika's mining and the samurai's chop of 7.3. A style without one uses
   the core's.
5. **Vanilla marker sounds** until style kits exist (SOUND.md 5).
6. **The twin blades' whirl is not a full turn.** A 360-degree root spin would end 360 degrees off the stance's first
   key. GeckoLib would snap it back when the action stops, or ease it back the long way round. The whirl is a
   130-degree coil and unwind of the hips and chest.
7. **The style's run is `combat.run`, not `move.run`** (a deviation from 13.6's list, which says "`move.run` arm
   carry"). GeckoLib resolves a name through the hero's chain, so a style's `move.run` would replace the core's run
   whatever the weapon flag. The first run's katana and fists did that, and the G2 previews showed it: the fists
   fixtures ran in a boxer's guard in `run.gif`. A hero whose club, shield or staff is on its back would run
   with both hands round nothing. So the carry is `combat.run`, the counterpart of `combat.idle`. The engine plays the
   core's `move.run` until A2 tries `combat.run` first while the weapon is drawn (open item).

### Open items

- **A2**: `HeroClips.baseCandidates` should try `combat.run` before `move.run` while the weapon is drawn, as it tries
  `combat.idle` before `move.idle` (decision 7; until then a hero with its weapon drawn runs the core's run).
- **A1b / A5**: swap the weapon flag on the `weapon:drawn` / `weapon:sheathed` ticks of the timing file. Play
  `guard` then `guard.loop`, and `guard.hit` then `guard.hit.loop`, until the next code.
- **A2**: a particle handler for the `vfx:` names above.
- **G10**: the staff-broom's riding clips (`move.ride`, `move.ride_cast`) on the mount point. The locomotion set may
  be overridden per style (13.6, MOBILITY 4.1).
- **C1-C4**: the heroes' own clips can use their style's poses (`style_poses`); Kagenori's `twin_sky` and the
  off-hand from a phase need their own guard and string over the two-sword stance.
- **Polish**: the shield's pop at the draw's swap. The hammer's left hand drifts up to 4 px off the handle between
  keys. The slam's contact on the larger forms (above).
- **Parts (G6b / G6c follow-up)**: the 63 coplanar problems above, which the combat stances expose (the inner
  sides of nested leg shells, the bracer rim against the jacket cuff, the gauntlet plate, two skirt panels). Until then
  `herogen lint` fails on 7 parts and 5 sample outfits, while every weapon part passes. The knee wedge is a question
  for the rig.
- **Hair kit** (G6c's item, still open): the coplanar rule sees only the first frame of each stance. Any frame where
  the head is level and still, so `hair_back` comes to rest, z-fights through the hem in game until each `back_base`
  moves 0.1 px back. The stance bow above keeps the combat idles clear; the fix belongs to the hair kit.

---

## G10: the mobility set (2026-11-13)

HEROES_V2.md 15.2 row G10 and MOBILITY_AND_WORK.md 4.1-4.3 and 15, on G3's generator and G4's core library. The
approved look (rig v3, style pass 2) is unchanged: this task adds motion, the rig's `mount` bone and a broom carrier
part. As before, no reference model's animation, pose or timing is in the repository: every pose here was authored for
this task (the squats and the skid's brace solved for planted feet on the teen, as G4 did).

### What exists

| Path | What |
|---|---|
| `herosrc/anim/core/mobility.json` | the 27 animations of milestone 1 and the three wing loops (`_core` now holds 85 clips, 1.6 MB) |
| `herosrc/anim/poses/mobility.json` | their poses (jumps, landings, the roll and the flip, wall kick, mantle, climbing, gliding, flight, the charge and the skid, the downed get-up, riding, the dive, the wings) |
| `herosrc/anim/variants/{cat_twist,fox_lope,crane_leap,broom}.json` | the slice heroes' locomotion overrides (below) |
| `herosrc/parts/mounts/broom.json` | the `mounts` category's first part: a flying broom on the `mount` bone |
| `herosrc/rig/rig_v3.json` | the optional bone `mount` (parent `root`, pivot `[0, hip_y - leg_d / 2, 0]`: the underside of level thighs) |
| `herosrc/style/outfits.json` | Natsuho, Yugen, Kagenori and Ambrose carry `animations.variants` |
| `anim/library.py` | `variant(name)`, `animations.variants` in a hero's own file |
| `anim/recipes.py` | `settle_to` on base one-shots, `contact: mount` with `seat`, the `floor` spans, layers that keep jump keys |
| `anim/primitives.py` | `spin` (a full turn that unwinds) and `hold` (a constant offset over a gait layer) |
| `anim/quality.py` | the settle rule for base one-shots, `check_mount` |
| `anim/loco_preview.py`, `herogen anim-previews --locomotion [--groups ...] [--check]` | the previews below |
| `parts.py`, `visual.py`, `rig.py`, `render/scene.py` | the `mounts` category, `visual.json` `carrier`, the riding rule of the renderer |
| `tests/test_mobility.py` | 22 tests (below) |

### The clips

| Clip | Kind | Length | Leads into | What |
|---|---|---|---|---|
| `move.leap` | base loop | 1.0 s | | in the air on any jump: the right knee up, the left leg trailing, the arms out for balance, a slow drift so a long leap stays alive |
| `move.jump` | base one-shot | 0.3 | `move.leap` | leaving the ground: the push-off (legs straight, toes pointed, the arms swung up and out), then the knee draws up; dust |
| `move.crouch_jump` | base one-shot | 0.3 | `move.leap` | the leap's tell: a dip, the deep crouch (knee 80 degrees, feet planted on every base, the eyes on the target), the push on the last tick |
| `move.air_jump` | base one-shot | 0.35 | `move.leap` | the legs kick down (a ring of air, `entity.breeze.jump`), the tuck turns one front flip about the hips (0.08-0.28 s), opens out |
| `move.wall_kick` | base one-shot | 0.3 | `move.leap` | crouched against the wall behind with a foot planted on it, the kick: the leg drives back, the knee and arms forward |
| `move.mantle` | base one-shot | 0.5 | `move.idle` | hands on the edge, the pull with a knee up, the press over the edge, the step up; gritted teeth |
| `move.land` | base one-shot | 0.3 | `move.idle` | touch-down, the knees take the weight (70 degrees, feet planted), up |
| `move.land_heavy` | base one-shot | 0.5 | `move.idle` | a deep crouch (110 degrees) with a fist on the ground and the other arm flung out, held, up |
| `move.land_roll` | base one-shot | 0.5 | `move.idle` | the hands reach for the ground, a forward roll over the back (0.06-0.3 s), up into a crouch and the stance |
| `move.climb_idle` | base loop | 4.0 | | clinging: hands wide on holds, breathing, a glance over the shoulder and one down, a grip shifting |
| `move.climb_down` | base loop | 1.0 | | foot under foot, hand under hand, looking down, a small drop on each step |
| `move.glide_start` | base one-shot | 0.4 | `move.glide` | from the fall the arms sweep out (`item.armor.equip_elytra`), the body tips forward with an overshoot |
| `move.glide` | base loop | 2.4 | | pitched 62 degrees, legs trailing, arms spread like wings, a slow bank one way and the other, a bob |
| `move.gust` | base one-shot | 0.4 | `move.glide` | lifted: nearly upright, the arms swept up and out, the knees tucked (`entity.wind_charge.wind_burst`), spread again |
| `move.takeoff` | base one-shot | 0.6 | `move.fly` | the 0.6 s tell: a crouch with the arms opening like wings, the push with the arms driven down, the rise |
| `move.fly` | base loop | 1.6 | | leaning 38 degrees, legs trailing, arms relaxed back, a slow bob and a flutter |
| `move.fly_fast` | base loop | 0.8 | | nearly level and streamlined, the arms back along the sides, a quick shallow bob |
| `move.fly_hover` | base loop | 1.6 | | upright, treading the air, the arms balancing |
| `move.fly_land` | base one-shot | 0.4 | `move.idle` | the flare (the body back, the legs forward, the arms braking), touch-down, up |
| `move.sprint` | base loop | 0.5 | | the runner's burst: a longer stride (42 degrees), the knee at 108, a 13 degree lean, a longer flight phase |
| `move.charge_run` | base loop | 0.6 | | a heavy driving run leaning 20 degrees, the head down, the right shoulder leading, both forearms up |
| `move.skid` | base one-shot | 0.4 | `move.idle` | braked on the front heel with the weight sunk on the back leg (both soles down, solved), the heel lifts and comes back (`entity.breeze.slide`) |
| `move.downed` | base one-shot | 2.0 | `move.idle` | shot down: sprawled on the back with stars, dazed until 1.0 s, a rock back with the legs up, a kip-up (in the air for a moment), a deep squat landing on the feet's own spots, up |
| `move.winded` | base loop | 1.2 | | hunched with the hands on the knees, panting twice a second, the mouth opening on each breath, a sweat drop |
| `move.ride` | base loop | 2.0 | | astride the carrier on the `mount` bone, both hands on the handle, a bob (the mount bobs with the root) and a sway |
| `move.ride_cast` | action over `move.ride` | 0.6 | `move.ride` | the upper body only: the right hand draws back and casts forward (`vfx:cast`) |
| `move.air_dive` | action (class heavy) | 2.0 | `move.idle` | the `dive` form's default: a wind-up in the air (knees tucked, the fist raised, the eyes on the target), the plunge (`item.trident.riptide_1`), the hit on landing in the heavy-landing crouch at 1.0 s (`hit;vfx:ground_slam`), the recovery |
| `sec.wings_fold` | secondary loop | 4.0 | | the wings tucked against the back, rising with the breath, a ruffle |
| `sec.wings_flap` | secondary loop | 0.5 | | a power stroke from high to low in 0.2 s, half folded on the way up |
| `sec.wings_glide` | secondary loop | 2.0 | | spread wide and level, the tips fluttering |

Faces come from `face_ctrl` keys where the move has an expression (a determined jump and take-off, focused eyes on the
tell and the roll, closed eyes in the tuck, gritted teeth on the mantle, surprise on the skid and the gust, pain on
the downing, a tired face with a sweat drop while winded); the loops leave the mood alone except `move.fly_fast` and
`move.sprint` (focused) and `move.winded`.

### Generator additions

1. **Base one-shots settle.** A recipe with `loop: false`, `kind: "base"` and `settle_to` ends on the first frame of
   that loop on every channel it keys (the same `_settle` as actions), and `quality.check_settle` checks it. GeckoLib
   blends a queued animation in over the controller's transition from the pose shown, so a one-shot that ends on the
   next loop's first frame hands over with nothing to see. (Without `kind: "base"` a one-shot defaults to an action:
   the first draft did that and its events were written 2 ticks early for the base controller; a test pins the kinds.)
2. **Full turns** (`primitives.spin`): a flip, a roll or a twist turns bones by whole turns from t0 to t1 (keyed
   every tick) and unwinds at t1 with a Bedrock pre/post key (the same orientation), so the clip ends where it started
   and GeckoLib never turns it back the long way. A whole figure turns about the hips when `body`, `leg_r` and `leg_l`
   (all pivoted at the hips) turn together (the flip, the roll), or about its vertical axis on `root` (the cat twist).
   Layers now keep jump keys when they are summed (`recipes._layer`).
3. **Floor spans** (recipe `floor: [{from, to, bones}]`): after the layers, the root height follows the lowest point
   of the bones tick by tick, fitted over the forms (form-scaled): the roll's tucked body stays on the ground while it
   turns over (head included: within 0.75 px on every base, a test).
4. **`hold`**: a constant offset added over a gait layer that replaces those channels (the charge's leading shoulder
   over `run_cycle`).
5. **Riding** (`contact: "mount"`, `seat` default `leg_*`): `quality.check_mount` keeps the lowest point of the seat
   bones within 0.6 px of the `mount` bone's pivot (carried by the root) every tick on all six bases. The pivot is the
   underside of level thighs on every form, so thighs at -90 degrees sit exactly on it.
6. **Variants** (`herosrc/anim/variants/<name>.json`, `{variant, poses, animations}`): a hero's
   `animations.variants` copies them into its own file; its own signatures and overrides still win; a variant's
   animations must be `move.*` and its poses may not reuse a pose library name.

### Carriers and the mount

- The rig's optional `mount` bone (67 bones with every option) is under `root`, so a bob carries the carrier with its
  rider; its pivot is the seat. G6c's staff mount now uses it (same pivot as before).
- A `mounts` part (category `mounts`, `carrier: <kind>`) puts its cubes on `mount`; `mounts/broom` is a straight
  handle with its top at the seat, a knob, a cord binding and two splayed straw bundles authored hanging and laid back
  by their rotation (their ragged ends are the far end). A hero has one carrier: a mounts part and a weapon with a
  mount refuse each other.
- `visual.json` gains `carrier` {part (`weapon` or the part id), kind, bone, seat} whenever the mount has cubes, and
  `bones.mount`. The preview renderer shows the mount only while riding and hides the weapon's hand and back copies
  only when the carrier is the weapon (Ambrose); a broom part leaves the weapon where it is.

### Hero variants (MOBILITY_AND_WORK 15)

| Variant | Hero (sample outfit) | Overrides | What |
|---|---|---|---|
| `cat_twist` | Natsuho | `move.air_jump` | the cat twist: she arches and tucks, turns once round her vertical axis (the root, 0.06-0.28 s) with the chest leading, the hips following and the tail sweeping the other way, a smug grin |
| `fox_lope` | Yugen | `move.sprint` | long low bounds with a long float (flight 1.8), the body stretched 20 degrees, the arms swept back like trailing sleeves, a sly smile; 0.6 s |
| `crane_leap` | Kagenori | `move.leap` | the crane: arms spread wide and a little back with the sleeves hanging from them, the right knee high, the left leg straight below, one slow wing beat per 1.2 s |
| `broom` | Ambrose | `move.takeoff`, `move.ride`, `move.ride_cast`, `move.fly_land` | side-saddle: a crouch and a hop onto the staff, both thighs level on it and turned to the left with the ankles crossed, the chest and head turned back to the way ahead, the right hand on the shaft; the free left hand throws a vial; he slides off to land |

The four pass every animation rule in their hero's own file on the hero's base and on a chibi (a test). The core's
jump one-shots end on the core `move.leap`'s pose; for Kagenori the base controller then blends into his crane (4
ticks).

### Sounds and markers

SOUND.md 7 gives the server the take-off, the heavy landing, the glide loop, the downing and the steps, so the clips
mark only client details, all vanilla 1.21.1 events (a test reads sfxgen's list): `entity.breeze.jump` (the air jump,
the wall kick), `item.armor.equip_elytra` (the glide opens), `entity.wind_charge.wind_burst` (the gust),
`entity.breeze.slide` (the skid), `item.trident.riptide_1` (the dive's plunge). `vfx:` markers: `jump_dust`,
`ground_dust`, `air_ring`, `wall_dust`, `gust`, `takeoff_dust`, `skid_dust`, `stars`, `cast`, `ground_slam` (no
particle handler yet, A2). Base clips write their events after the base controller's 4-tick blend (G3 review).

### Previews (`docs/heroes-v2/previews/anim/locomotion/`)

`jumps`, `climb_glide`, `flight` (with feathered wings and their loops), `ground` (sprint, charge, skid, the dive) and
`ride` (a mounts/broom) on Tansy (chibi_f) and Maren (adult_f), and `hero_natsuho`, `hero_yugen`, `hero_kagenori`,
`hero_ambrose` (each variant on the hero's own base and a chibi): a GIF at 20 fps and a contact sheet each, and a
README. `anim/loco_preview.LocoPlayback` plays the base controller's queue (each clip blends in from the pose shown
over 4 ticks, as GeckoLib starts a queued animation), an action over it (2 ticks) and the wing loop on the secondary
controller (6 ticks); the carrier shows from the take-off's start until the landing has played.

### Tests (309 -> 331: `tests/test_mobility.py`, 22)

The set of 4.1 (27, kinds, loop modes, lengths); every one-shot ends on its loop and one moved off it fails (the
settle rule and `check_built`); every clip passes the quality rules on all six bases; the wing loops key only the
wings, a full beat, and no locomotion clip keys a wing; no root motion; the crouch jump crouches with planted feet on
every base; markers and their file times; every sound is a 1.21.1 event; the flip turns once and unwinds (written as a
pre/post key and read back), a layer keeps the jump, a spin outside the clip fails; the roll stays on the ground on
every base; the mount bone is the seat on every base; the broom part is a carrier (visual.json, hidden unless riding,
the weapon kept, the handle's top at the seat); the staff is a weapon carrier (its copies hidden while riding); one
carrier, and a mounts part off the mount bone fails; the rider sits on the mount and lifted thighs fail; variants go
into the hero's own file and its override wins, an unknown variant fails; the four slice variants pass on their bases;
the cat twists round its vertical axis; the broom rides side-saddle on the mount; the preview queue hands a one-shot
to its loop without a pop. `test_rig`: 67 bones with every option, and the `mount` pivot (the seat) is the one height
that follows the build (the leg's depth) instead of the form.

### Decisions (each deliberate)

1. **Locomotion one-shots are base clips** (MOBILITY_AND_WORK 4.2: one-shots play on the base controller over the
   loop), so an attack on the action controller is never cut by a landing; they end on the loop that follows instead
   of `move.idle`.
2. **Flips and rolls are real full turns**, unwound by a pre/post key. GeckoLib places `pre` 0.001 s before its key and
   interpolates between them, so a render frame that falls in that millisecond (about one flip in 16 at 60 fps) shows
   the body at an in-between angle for that frame. A tuck without the turn read as a crouch in the air and a roll
   without it as a stumble; the open item below has the engine-side fix.
3. **Variants instead of per-hero copies**: an archetype's flavour (a cat's twist, a witch's broom) is one file any
   hero can pick (MOBILITY_AND_WORK 18.3); the sample outfits carry them until C1-C4 write the heroes.
4. **The broom variant keys no part bone** (Ambrose's crow): a hero's own file is checked against its own bones, and a
   witch without a crow must be able to use it.
5. **Wings are animated only by the secondary controller's loops**, so no base clip and no action has to know whether
   a hero has wings, and the loops play over anything.
6. **`move.air_dive` is 2.0 s**: the heavy class needs 0.8 s before the hit and 0.8 s after it; the dive's touch-and-go
   (0.5 s) is the hero's own recipe or A12's.

### Open items

- **A10 (`DATA_LOCO`, `HeroClips`)**: the clip names are MOBILITY_AND_WORK 4.1's; `HeroClips.LOCO_CLIPS` (A2) still
  plays `move.cling`, `move.charge`, `move.run` and `rest.sit` for modes 7, 10, 11 and 14: they should be
  `move.climb_idle`, `move.charge_run`, `move.sprint` and `move.ride`. Play the one-shots as `thenPlay(one-shot)
  .thenLoop(loop)` on the base controller (the loop each one leads into is its `settle_to` in the timing file's clip
  and in this table). A hero whose carrier is a mount flies in mode 14 (ride): the broom variant has no `move.fly`.
- **A10 / A2**: show `visual.json` `bones.mount` only in the fly, glide and ride modes and, when `carrier.part` is
  `weapon`, hide `bones.weapon_drawn` and `bones.weapon_sheathed` meanwhile; play `sec.wings_fold`, `sec.wings_flap`
  (speed from the flight speed) or `sec.wings_glide` by mode on the secondary controller; the `tired` bit could slow
  the beat.
- **A2 (the unwind)**: to remove decision 2's one-frame risk, `HeroModel` can skip a frame's sample inside a pre/post
  millisecond (or snap a rotation channel that moved by more than 300 degrees since the last frame to its post value).
- **Timing files**: the `sec.*` loops are written with the base controller's 4 ticks (the Java test accepts base and
  action only); they carry no events, so nothing fires early. A `secondary` kind (6 ticks) needs the Java side first.
- **C3**: Ambrose's crow flapping beside him while he flies needs wing bones on the crow part; his `broom_dive`
  recipe is his own.
- **G8 / G10b**: style overrides of the set (a samurai's `move.leap` keeping the sword hand still), the other
  carriers (cloud, sword, crane, jets, kite, canopies) and G10b's six (hover, drift, swim_fast, tread, swim_down,
  surface_leap).
- **Polish**: raised arms hide behind the big head from behind (the climb's hands were widened for it); Garrick's and
  Kagenori's hip scabbards point up while riding astride (they hang from `body`).

---

## Art review: G6c, G5, G10 (2026-11-13)

An art director's review of G6c (b1fbd8a), G5 (dda14db) and G10 (6e0013a), with a second look at the G6a and G6b sheets,
judged against the approved look (rig v3, style pass 2, the in-game checkpoint) and the owner's reference style
(blocky, anime proportions, distinct faces, detailed outfits). Every committed sheet and GIF in
`docs/heroes-v2/previews/parts/`, `anim/styles/` and `anim/locomotion/` was looked at; frames were pulled from the
GIFs, and suspicious ones were rendered again at 3-9x from four directions with the slice outfits on (the committed
GIFs are 420 px wide, too small for a blade or a stripe). Each finding below was checked with a number or a render
before it was fixed. The style is unchanged: nothing here moves a silhouette, a proportion or a palette; no reference
model was opened.

### What holds

- **The strings read as their weapons** on the adult and the chibi: the hammer's raise, overhead and slam (the club
  rests over the right shoulder in the stance, its head behind the shoulder: from the front it reads as a club held
  up and out), the katana's diagonal, level and thrust, the sword and shield's cuts and bash, the staff's casts and
  bonk, the fists' jab, cross and rake, the twin blades' backhand and kunai work, the focus casts. Props stay in the
  hands through every tick (`grips.animation_problems`, G5); the club's second hand is within 2 px of its grip on
  all but three single ticks (the worst, 3.9 px on adult_m at 0.75 s of the slam's recovery, is G5's open item).
- **Locomotion has weight where the move needs it**: the crouch jump's tell, the take-off's crouch, the 70 and 110
  degree landings, the roll on the ground, the skid's braced heel, the kip-up; `move.jump` has no pre-crouch by
  design (the entity is already rising when the clip starts).
- **No part copies a known character's signature design.** The slice looks follow SLICE_PLAN 3's own descriptions;
  the emblems are drawn per size here; the hime cut, the braided crown, the oni's single horn and the witch's hat are
  genre staples, combined with this project's palettes and faces.
- **Dark hair** (Hinae, Kagenori, Tobimaru) reads as a dark mass with cool sheen strands; the in-game checkpoint shows
  the same look, which the owner approved, so the dark-hair ramp is unchanged.

### Found and fixed

1. **Coplanar faces left in the library** (`herogen lint` failed on 7 parts and 5 sample outfits with 63 problems,
   G5's open item, plus G6c's hair-kit item). All fixed; `herogen lint` is clean on the 94 parts and the 8 outfits on
   all six bases, and `herogen check --all --style` is clean.
   - **Nested leg shells on one inner plane** (boots' cuff and shaft, trouser gathers and wraps under boots and
     `tabi_boots`, `socks_shoes` and `thigh_socks` soles and straps, shorts, the kunai holster strap; Tobimaru's
     shins up to 1240 px at 6x). On the inner side of a leg there is no room for the layer convention: the gap to
     the centre line is 0.1-0.15 px on every base and build, so G6b's clamp put every shell's inner side 0.05 px from
     the centre line. New `parts.settle_leg_shells` (run by `build.assemble`): among the shells whose inner sides lie
     between a leg and the centre line and overlap in height (1.5 px margin: a bent knee brings a thigh shell and a
     shin shell together), the outermost by the layer convention keeps the plane and each shell under it moves its
     inner side 0.025 px toward the leg (a shell that would come within 0.02 px of the leg goes 0.12 px inside it,
     covered by the shells over it). The outer layer is then the one seen between the legs, as on the outer side.
     This is the one place where the generator places a layer itself (G6b's decision 1 holds elsewhere): the
     convention has no room there. 0.025 px is enough depth up to about 27 blocks with a 24-bit depth buffer, where
     the inner side of a leg is a pixel or two.
   - **Joint wedges** (`thigh` / `shin` sides at a bent knee, 4-50 px on Ilsabet and Natsuho; `upper_arm` /
     `forearm` at the elbow on Tobimaru). A joint that bends about x keeps the side faces of the two segments in one
     plane, so they overlap in a wedge. The base template's forearm and shin are now 0.1 px narrower than the upper
     arm, the hand, the thigh and the foot (0.05 px a side, under a texel): knees, elbows, wrists and ankles can no
     longer tie. This rebuilds `test_rig` and `_fallback` (geometry and texture; names and the fallback chain
     unchanged). The painter's ambient occlusion shades the top row of a cube that sits under one "at least as wide";
     under the narrower forearm the hand lost its shaded top row on every hero (`test_paint`'s AO test caught it in
     the finishing run). A cube above may now be narrower by a joint's 0.1 px on each axis (`paint.AO_NARROWER`), so
     the hands are shaded as before.
   - **Bracer rim against a wrap top's cuff** (Tobimaru's forearms, 325-472 px): both ended 0.1 px past the wrist.
     The bracers' lower rim now reaches 0.2 px past it.
   - **Gauntlet hand plate against a held grip**: the plain sword's grip in the fists guard on the chibis (8 px). A
     held grip is centred on the hand, so the faces of the slice grips lie at `hand_bottom` + 0.5 to 1.45 in steps of
     0.05 px on the six bases (0.75 is the plain sword's on a chibi, 0.8 the longsword's). The plate's lower face
     moved from + 0.75 to + 0.875, 0.025 px clear of all of them (the interrupted run's + 0.8 put it on the
     longsword's grip: the finishing run's lint caught it in Ilsabet's guard on both chibis, 18 and 32 px).
   - **A doubled back panel in every full ring of panels** (`flared_skirt` on chibi_f, 4 px, the last failure of
     the first lint run): an arc's angles include both ends, so `flared_skirt`, `pleated_skirt`, `hakama` and
     `court_robe`, authored over [-180, 179.999], put their last panel on their first, one layer out, and the two
     side faces z-fought. The rings now end one step short of 180 (150, 154.286 and 157.5 for 12, 14 and 16
     panels): the same count, evenly spaced, with a panel at the front centre.
   - **The hair kit's back slab on the head's back plane** (G6c's and G5's open item): every style's `back_base` now
     starts at head-local z 5.1 (0.1 px behind the head) and is 0.1 px thinner, so its outer face does not move. The
     stances no longer need their head bow to hide it (the bows stay: they read as a fighter's focus).
2. **Benika's stripes and horn never lit up.** The sample outfit named no phases, so the `glow: "phase"` stripes had
   no phase texture: the G6c sheet's "phase 2 at night: stripes" showed a dark figure with nothing glowing. The lower
   stripe was also half a pixel tall and painted no texel at all on the adult and the chibi, and on the chibi the
   gauntlets' flared cuff overlapped the coat's cuff, leaving no bare forearm. Now: her outfit has phases 2 and 3
   (phase 3 with glowing eyes, SLICE_PLAN 3.3); `markings/arm_stripes` paints whole texel rows (elbow - 4 to - 3,
   elbow - 6 to - 5) with a bare row between; `armour/gauntlets` has a `flare` param (0: a plain wrist shell, no
   flared cuff) and Benika wears `reach` 0.3, `flare` 0, so the forearm is bare between the coat's cuff and the
   shell: 3.75 px on her own form (the adult, both stripes show), 2.35 on a teen (one; the other is under the
   shell), 0.95 on a chibi. There the first band lay on the wrist's row, under the shell, with a 0.2 px sliver above
   it; it now takes the row above (`max(elbow_y - 4, wrist_y + 1)`, the finishing run), which shows. The adult and
   the teen keep their rows. `horns/single_curved` has a `glow` param (an overlay that lights the horn in the phase
   look) and Benika's horn glows from phase 2, as SLICE_PLAN asks.
3. **The braided crown's back was a flat crate** (Ilsabet, `head_hair.png` back view): its back layers (radius
   5.55-6) lay inside the back slab (out to 6.25), so from behind and from the side the lower head showed one plain
   slab with a dark triangle. New `back` strands (7, combed down from the crown to the nape over the slab, radius
   6.3-6.4, drawn together toward the nape) and the nape strands moved out to 6.55-6.65.
4. **Capes, tails, ponytails and coat tails did not follow in flight.** The bake answers the parent's motion and
   gravity only, so on a level body (the glide pitched 62 degrees, fast flight nearly flat) a cape and coat tails hung
   straight down toward the ground and a tail or a fox's three tails stood straight up off the back (Yugen's glide,
   Maren's fast flight). New recipe field `wind` {dir, strength, groups, limit, ramp, fade} (`secondary.Wind`): the
   air streaming past a flying hero pulls the tips of the named groups (default tail, ponytail, cape, skirt) toward
   `dir` in the world (+z behind the hero), like `hang`, with a larger turn limit (80 degrees) for those groups in that
   clip. Given to `move.glide`, `move.glide_start` (ramped in), `move.gust`, `move.fly` (0.6), `move.fly_fast` (0.85),
   `move.takeoff` (ramped in after the push), `move.fly_land` (faded out), `move.leap` (0.35, a long jump) and
   `move.ride`, and to the broom and crane variants; an action without its own wind takes the wind of the base it
   plays over (`move.ride_cast` over `move.ride`), so it starts on the base's baked follow-through (the settle rule
   caught the first build). Ground clips feel no wind. Only `_core.animation.json` changes (chain-bone keys); no
   event, frame or timing moves (the timing files change their source hash only).

### Previews

Regenerated, since the base template, the hair kit and the painter changed every model: the style set
(`previews/style/`), the G2 set (`previews/render/`), the G3 filmstrips and GIFs, `previews/anim/core/`,
`anim/styles/`, `anim/locomotion/` and every parts sheet (`previews/parts/`). `render-previews --check`,
`anim-previews --core/--styles/--locomotion --check` and `parts-previews --check` pass. In the regenerated
locomotion set the wind shows: Maren's hair and coat tails stream behind her in the glide, the gust, flight and the
leap (her coat tails hung toward the ground before), and Tansy's tail trails out behind her; the turnarounds and the lineup keep their silhouettes and
palettes (the skirt rings turn by half a panel, the forearms and shins are 0.1 px narrower).

### The finishing run

A usage-limit pause stopped the review while the full lint, the Python suite and the preview sets were running. A
second run read the diff and these notes, reran every check and looked at the regenerated lineup, turnarounds, strings
and locomotion. Three things failed or were wrong, all from this review's own fixes:

- `herogen lint` (2090 builds and checks) found 2 problems: Ilsabet's `sword_shield.guard` on chibi_f and chibi_m,
  `hand_r/weapon_r` 18 and 32 px: the gauntlet plate's first fix (+ 0.8) had moved it onto the longsword's grip. Now
  + 0.875 (above).
- The Python suite failed `test_paint.test_ao_under_the_sleeve`: the narrower forearm had cost every hand its shaded
  top row. Fixed in the painter (above).
- Benika's first stripe hid under the gauntlet shell on a chibi (a 0.2 px sliver): moved one row up there (above).

The gauntlets, the stripes and the eight outfits were linted again on all six bases (clean), then `check --all
--style`, `build --all --check`, the whole Python suite and every preview set.

### Tests (331 -> 347: `tests/test_artreview.py`, 16)

The glide trails the cape and the tail behind (+z), fast flight streams the ponytail and the back skirt panel; ground
clips have no wind; `move.ride_cast` takes `move.ride`'s wind; ramp and fade; `Wind` validation; sabotage: the glide
baked without wind lets the cape hang. A boot's cuff and shaft have inner planes 0.02 px or more apart on all 18
bodies, the cuff (outer) nearer the centre line and short of it; trousers with wraps under `tabi_boots`: every pair
of overlapping shells on its own plane, none on the leg's or the shin's plane; sabotage: with the pass disabled 19
subtests fail. The forearm and the shin are 0.05 px a side narrower than the segments they bend against, on all 18
bodies. Every hair style's back slab starts at z 5.1 or more; the braided crown's back strands lie over its slab.
Benika's stripes and horn are in the phase glow mask (not the base one) on the chibi, the teen and the adult; the
stripes are whole texel rows on every base; a glowing row shows between the coat's cuff and the gauntlet on her
chibi, teen and adult (two on the adult; sabotage: the wrist-row band fails on the chibi). No full ring of panels puts its last panel on its first (sabotage: the old flared skirt
fails).

### Decisions

1. **The generator places the inner sides of nested leg shells** (above): the layer convention cannot work in a
   0.1 px gap, and authoring a different inner side per part pair would not survive a new combination.
2. **Narrower forearms and shins rather than a wedge rule**: 0.05 px is invisible, it fixes every joint of every
   hero at once, and no animation or part changes.
3. **Wind is per clip, authored**: the server moves the entity physically and the bake cannot know the hero's speed,
   so a flying clip says how much air it feels; the default groups leave ears, hair and sleeves to the bake.
4. **The hammer stance and the dark hair stay** (above): they read as intended at the in-game scale.
5. **The painter allows the joint's 0.1 px** instead of the joints going back to equal widths: the narrower forearm
   fixes a z-fight at every bent elbow, while the shaded top row of a hand is a texture rule that only needs to know
   a sleeve or a forearm sits on it.
6. **The gauntlet plate sits off a 0.05 px grid** (+ 0.875): the slice grips put their faces on that grid on every
   base, so any value on it ties with some grip on some form.

### Open items

- **A2**: nothing new; `visual.json` of `test_rig` and `_fallback` changed (geometry and texture), their names, bones
  and the fallback chain did not.
- **G5 polish (unchanged)**: the shield's pop at the draw; the club's second hand off the handle for one tick of the
  slam's recovery; the slam's contact on the larger forms.
- **Previews**: the jumps stand in place (root motion is physical); a preview-only arc for airborne clips would make
  their weight easier to judge.
- **Markings on small forms**: a chibi's forearm is 4 px, so under a short gauntlet and a pushed-up sleeve one band
  of `arm_stripes` fits; a hero who needs the pair on a chibi needs a shorter sleeve or no gauntlet.
- **Housekeeping (not this review's)**: the stray `/v3_styles_check.log` and `/v3_render_check.log` that the G5 run
  left at the filesystem root still need removing by hand.

---

## G7: `herogen check` covers 14.2 (2026-11-13)

HEROES_V2.md 14.2 and 15.2 row G7, the Python side. `herogen check` now runs every rule of 14.2, and SLICE_PLAN 6.3's
three negative tests fail it with a clear message. The Java `HeroAssetsTest` and `verifyHeroAssets` are the Java
workflow's (next); 15.2 G7 lists exactly which Python functions they mirror. No art, animation, hit tick or timing file
changed: the only generated change is `visual.json`'s new `face.asymmetric` (test rig and fallback: `false`).

### What exists

| Path | What |
|---|---|
| `tools/herogen/assets.py` | the file-level rules: everything that needs only the files the mod ships (the geometry, the textures, `visual.json`, the animation and timing files of the hero's chain, the hero's server definition and the action-code table of `HeroActions.java`), so the Java test can mirror it one to one |
| `tools/herogen/distinct.py` | the distinctness NOTEs (silhouette and colour regions of two heroes of the same archetype) |
| `cli.py` `check` | runs the file-level rules on each hero's committed files (on the in-memory build for the style fixtures), the animation rules on its built clips, the coplanar rule, `build --check`, then the NOTEs; `--slice` (the eight slice heroes that have a source, SLICE_PLAN 6.3), `--no-distinct` |
| `visual.py` | `face.asymmetric` (an eye patch, `iris2`, a one-sided mark: the mirror rule is skipped), and `moves` now lists the hero source's move ids in its order (empty for the test rig and the fallback, which have none) |
| `tests/test_assets.py` | 27 tests (below) |

### The rules of 14.2 and where each one is

| 14.2 rule | Where | New in G7 |
|---|---|---|
| JSON syntax | `assets.check_json` (strict: no NaN or Infinity) on every JSON file of the hero and its chain | yes (before: only implied by `build --check`) |
| geometry `format_version` 1.12.0 | `assets.check_geometry` (an error line; `geo.GeoModel` used to raise) | yes, as a reported problem |
| budgets: bones, cubes, texture size | `validate.check_geometry` (100 bones, 300 cubes, 256x128 or 256x256), refused earlier by `build.assemble` | - |
| budgets: animations | `anim/quality.check_built(own_file=True)` (24 in a hero's own file) | - |
| bone names from the rig or declared by a part | by construction: `rig.Model.use_bone` refuses any other name; in the file, `validate.check_geometry` (lower_snake_case, unique, parents first) | - |
| required bones | `validate.check_geometry` (the 33) | - |
| face planes on the fixed UV regions | `validate._check_uvs`; `assets.check_visual` also checks `visual.json` `face.regions` against the rig | the `visual.json` half |
| texture size matches the geometry | `validate.check_geometry` (PNG against the geometry); `assets.check_visual` (`visual.json` `texture` against the geometry, and the glow mask and phase texture it names exist) | the `visual.json` half |
| UV islands inside the texture, not overlapping; whole-texel UVs | `validate._check_uvs` | - |
| every action code the server can send resolves through the hero's chain | `assets.check_codes` with `assets.required_actions`; the code table is read from `HeroActions.java` as text (`assets.action_table`) | yes |
| `hit` markers equal the server `frames` within one tick | `anim/quality.check_timing` (each timing file against its animation file, now on the committed pair) and `assets.check_frames` (each move's, the ultimate's and the awakened ultimate's clip against the definition's `frames` or `generated` entry: wind-up, active and recovery ticks, each within one) | the definition half |
| `face_ctrl` changes are jumps | `anim/quality.check_face` on the built clips; `assets.check_face_keys` on the written files | the file half |
| joint limits, planted feet, closed loops | `anim/quality.check_joints`, `check_feet`, `check_slide`, `check_mount`, `check_keys` on the built clips; `assets.check_channels` on the written files (times ascend in file order, the first at 0, none after the end, a loop keyed at its end with its start value) | the file half |
| actions end on the idle settle key | `anim/quality.check_settle` and `check_start` | - |
| alpha holes under the planes; nothing in front of the face below head-local y 5.5 | `validate.check_face_texture`, `validate._check_clear_face` | - |
| at most 130 cubes outside `fine_` bones | `validate.check_geometry` (`lod_cubes`) | - |
| the eyes mirror unless asymmetric by design | `validate.check_face_texture(asymmetric=...)`, the flag now read from `visual.json` `face.asymmetric` on the committed files | the flag in the file |
| `visual.json` move list equals the server move list | `assets.check_lists` (moves and states against the definition) | yes |
| every file in the chain exists | `assets.check_chain` (`animation_chain` is [hero, `_style_<style>`, `_core`], and each animation and timing file exists) | yes (before: a test only) |
| the build is reproducible | `build.check_files`, `build.check_library_files` | - |
| no coplanar faces of different colour | `cli.tie_problems` (`HeroAssets.tie_report`) | - |
| distinctness NOTEs | `distinct.notes` | yes |

**Which definition.** `assets.definition_of`: `data/factionclash/heroes/<id>.json`, else the slice's format-2 fixture
`src/test/resources/heroes/slice/<id>.json` (the slice's real kit until C1-C4 write the definitions). The test rig's
definition is the hand-written one of A1b; the fallback has none.

**Which codes are required** (`required_actions`, HEROES_V2 appendix D): every fixed name of the table except the
conditional ones; `attack.ranged` when the basic attack is not `melee` (the slice's thrown basics: Yugen and Hinae on
`focus`, Ambrose on `staff`, whose libraries have it); `phase.2` and `phase.3` with that many boss phases;
`state.<name>` and `move.<id>` for the slots `visual.json` names; `ultimate` and `ultimate.awakened` when the definition
has them; `ultimate.end` when the chain has an `ultimate.loop` to end. `defeated` and `spared` stay required while
they are in the Java table (the core serves them). Reading the table as text means a code added to `HeroActions` is
checked without touching herogen; the layout (`actionName`'s ranges) is ported, and a test pins known codes.

**The move list.** `visual.json` `moves` (bare ids, the slot order of `move.N`) must equal the definition's moves in
its order, except that a move with no `move.<id>` clip anywhere in the chain may be left out: the server then gives it
no code and plays its look's telegraph pose (A4). That exception is a NOTE (the test rig's hand-written `lunge`, the
one case today), and an error when the definition gives the move `frames` (the kit says how it is timed, so the art
must show it: every slice move). With every move animated, as every roster hero will be, the rule is plain equality.
States are compared exactly. Why not plain equality now: the test rig's `visual.json` would have to list `lunge`,
which changes what the engine sends while the Java workflow's live check runs (a code 64 with no clip instead of no
code); the rule as built is what the engine does, crisply.

**Frames.** For a move whose definition gives `frames`, its clip's `frame_ticks` (from the action's start, the 2-tick
blend included, as the server counts) must match each of wind-up, active and recovery within one tick. A single `hit`
gives an active of one tick, so a move whose kit says 0.2 s active (Natsuho's pounce) needs a first and a last
marker 4 ticks apart (a `cast` at the take-off and the `hit`, say): C1-C4 author them that way.

### Distinctness NOTEs (`distinct.py`)

For every two heroes of the same archetype (the definition's, else the source's; the pipeline fixtures' `fixture` and
the fallback are skipped): their front and side silhouettes, rendered by the preview renderer at 4x in the idle stance
(weapon sheathed, phase 1, a level camera) in one frame shared by the batch, overlap more than 90% (intersection over
union of the two views together); and their two largest colour regions are both within CIE76 delta-E 15 of the other's
two (best pairing). A colour region is a palette slot: every texel the painter writes is a tone of one slot's ramp, so
a visible pixel names its slot (a mixed face texel goes to the nearest ramp colour), the region's colour is the slot's
authored colour, and the faction-tinted sash is left out. On the test rig: hair 5,395 px, primary 3,638, accent 2,623,
secondary 2,229. The test rig against style Tansy overlaps 42%; Maren against the test rig, both adult_f, 78%; a copy
100% (both NOTEs); a recolour 100% (the silhouette NOTE only). Both are NOTEs for review gate 2, never errors.

### The three negative tests (SLICE_PLAN 6.3) and the rest

`NegativeTest` copies what the Java test reads for the test rig (its committed outputs, the `_style_katana` and
`_core` animation and timing files, its definition, `HeroActions.java`) into a temporary root, breaks one file and
runs the check `herogen check` runs on committed files (`assets.check_committed`):
- **a marker moved by 3 ticks**: `attack.1`'s `hit` written at 0.65 s instead of 0.50 s fails with
  `_style_katana: attack.1: markers [... (0.65, 'hit') ...] in the animation, [... (0.5, 'hit') ...] in the timing
  file` and `wind-up 0.50 s in the timing file, first hit at 0.65 s`; the timing file's wind-up moved
  3 ticks fails with `attack.1: wind-up 0.65 s in the timing file, first hit at 0.50 s`; and a kit hero whose
  definition says 3 ticks more than its clip fails with `move quick_cut: windup 19 ticks in t_signature (move.quick_cut,
  its first hit or cast at tick 19 of the action), 22 in the definition's frames (1.10 s): more than one tick apart`;
- **over the cube budget**: 120 cubes added to the body fail with `307 cubes (budget 300)` and
  `228 cubes outside fine_ bones (budget 130): move detail (strands, frills, trims) onto fine_ bones`;
- **a missing eye frame**: frame 4 erased fails with `eyes frame 4 is not fully opaque (missing frame?)`, and painted
  over with skin with `eyes frame 4 is blank skin (missing frame?)`.
Also: a missing chain file, broken JSON, a wrong geometry version, `visual.json` texture or regions that disagree, and
a definition with three phases (no `phase.2`/`phase.3` in the test rig's chain) each fail.

### Tests (347 -> 374: `tests/test_assets.py`, 27)

The code table read from the Java source (known codes; an unreadable table is a clear error); the committed heroes
pass, the test rig's `lunge` is a NOTE; `visual.json` carries the source's move ids and the asymmetry; the negative
tests above; the face-key, channel, code, list and frame rules one by one, each with passing and failing inputs; the
written libraries pass the file rules; the eight slice kits (the format-2 fixtures) against their style libraries as
written, with every per-hero clip present (stand-ins with the kit's frames): every code resolves, the lists and frames
hold, and the thrown basics find `attack.ranged`; a missing signature is named; a built kit hero passes and its moved
hit fails; the NOTEs (regions are palette slots without the sash, a copy gets both, a recolour the silhouette one,
different heroes or archetypes none). Sabotage: with `check_timing`, `check_frames` or `check_face_keys` emptied, 5
tests fail.

### Decisions

1. **The file-level rules are their own module** (`assets.py`), so the Java side has one list to mirror, and `check`
   runs them on what the mod ships (`build --check` then ties the committed files to the sources).
2. **The code table is read from the Java source** rather than copied: one table, two readers.
3. **A move without a clip may be left out of the move list** only when its definition gives no frames (above).
4. **`face.asymmetric` is written to `visual.json`** so the mirror rule can be checked from the files alone.
5. **Distinctness compares palette slots**, not clustered pixel colours: the slots are what an author changes, and
   the painter's tones (light, shade, outlines) would split one garment into several clusters.

### Open items

- **Java (G7, next)**: `HeroAssetsTest` and `verifyHeroAssets` (15.2 G7 lists the functions to mirror).
- **C1-C4**: author each move's markers so its clip's frames match the kit's within one tick (two markers where the
  kit's active window is longer than a tick); `herogen check --slice` runs on the slice heroes as their sources land.
- When the test rig's definition is regenerated or its `lunge` gets a clip, the move-list exception has no user left
  and can become plain equality.

---

## Polish after the art review: the shield's swap, the club's second hand, the slam's contact (2026-11-13)

The three G5 items the art review left open. The approved look is unchanged (no part, palette or proportion moved),
and no hit tick, marker tick or timing-file entry moved: the two style libraries' timing files change their source
hash only (checked entry by entry), and `test_polish` pins the ticks.

### The shield no longer pops at the draw and the sheathe

The weapon flag swaps the shield from its carried copy (flat on the back, `back_flat`) to the copy on the left forearm
on the draw's `weapon:drawn` tick (0.35 s) and back on the sheathe's `weapon:sheathed` (0.55 s). At those ticks the arm
was behind the hip, so the shield jumped 12-17 px (centres) and turned 50-70 degrees in one frame. Now
`sword_shield.reach` slides the left forearm behind the back under the shield's strap: the carried copy is a rigid map
of the hand's copy (a proper rotation, fitted from the cube centres), so the arm was solved for the corners of the
shield's 13 cubes to land on the carried copy's corners, on the chibi, the teen and the adult together, with the
shield kept off a chibi's head (the carried copy itself sits 0.8-1 px into a chibi's hair line) and the forearm out of
the back: `arm_l` [51.1, -31.7, 77.7], `forearm_l` [-104.5, -58.6, 0], `hand_l` [-80, -40, 3.2] (the wrist at its
80-degree flexion limit). At the swap the corners are now 2.0-3.3 px apart on average (at most 5.3 px at a far corner
on an adult_m), the faces 4-6 degrees apart. The first solve matched only the cube centres and turned the shield edge-on
to the back (its centres all lie in one plane, so the width axis was free): the renders showed it, the corners fixed it.
- The draw holds the shield in place to the swap with a new key at 0.35 s, `sword_shield.draw_free`: the sword arm
  and the torso where they were at that tick before (the blade has started out of the scabbard, so it still clears a
  chibi_m's waist), the shield arm still under the strap; then `c2_wind` by 0.52 s (`easeOutQuad`, the rest of the old
  `easeOutCubic` segment).
- The sheathe keeps its keys (`reach` at 0.58 s, 98% of the way at the swap); a pulse on `arm_l` (rz -15 at 0.425 s,
  zero at 0.3 and at the swap) swings the shield out a little on its way round, so it passes clear of the hips (it
  went 1.1-1.3 px into a teen's and an adult's hips at 0.45 s without it).

### The club stays in both hands through the slam

Between two keys each arm swings on its own chain, so the left hand left the handle by up to 3.9 px at 0.75 s of the
slam's recovery (adult_m), 2.75 at 0.50 s of the downswing (chibi_m) and 2.1 at 1.0 s of the haul (adult_f). Three
`oscillate` pulses on the left arm (`add`, one tick wide, zero at the neighbouring keys) were solved over the six
bases for the worst distance: the second hand now stays within 2.06 px of the handle on every tick of `attack.3` on
every base (`grips.measure`, the G5 measure with its 1.2 px slide). Nothing else of the clip moved. Still above 2 px,
not on the open list: the club string's hits on a chibi_m (`attack.1` and `attack.2` at 0.45 s, 2.6-2.9 px) and the
guard (2.7 px), all key poses solved on the teen.

### The slam's club head reaches the ground on every form

Rotations transfer between the forms, lengths do not: the hands are higher above the ground on the longer forms while
the club stays about one length, so at the hit the club head stopped 1.7 px above the ground on a chibi, 6.2 on a teen
and 8.9 on an adult. The extra hip fold that puts it on the ground was solved per base (5.3, 21.6 and 29.3 degrees)
and is almost linear in the form variable, so `hammer.slam_low` now folds the hips by form: `body` rx
35.6 + 1.93 x `v.fc_dhip` (23.5 degrees on a chibi, 39 on a teen, 47.7 on an adult) and the neck lifts against it
(-30 - 0.6 x `v.fc_dhip`). The club head lands 0.2-0.4 px above the ground on all six bases. The rest of the body is
unchanged: the legs hang from `root`, so the feet stay planted, and the hands keep the club because the whole upper
body turns about the hips.

**Generator addition: form-scaled rotations.** A pose may carry `form` {bone: [kx, ky, kz]}, degrees per pixel of
`v.fc_dhip` added to that bone's rotation (inherited with `extends`, mirrored with `mirror`). The recipe expander, the
writer, the readers and the quality rules already handled a key's form coefficient on any channel (G4 used it for the
root's height only); the writer now also writes rotation components as `"v + k * v.fc_dhip"`. GeckoLib 4.9.3 accepts
it: `BakedAnimationsAdapter` converts only a constant rotation to radians (negating x and y) at load, and
`AnimationController.getAnimationPointAtTick` does the same at run time for a value that is not a constant (read with
`javap -c` in the jar whose SHA-256 4.2 records), so a Molang rotation is in file degrees like a constant, and a
segment between a constant key and a Molang key interpolates consistently. HeroModel already sets `v.fc_dhip`; where
it is not set, the slam plays the middle form's fold.

### Previews

Regenerated: `docs/heroes-v2/previews/anim/styles/hammer.png`, `hammer_string.gif`, `sword_shield.png` and
`sword_shield_set.gif` (the other style previews came out byte for byte). Looked at frame by frame: the shield stays on
the back through the draw's swap and swings round the left side to the front; on the sheathe it comes round the side
and settles where it hangs; at the slam's hit frame the club head is on the ground on the chibi and the adult, the
adult folded deep over it.

### Tests (374 -> 383: `tests/test_polish.py`, 9)

The shield's corners at both swaps on all six bases (mean under 3.5 px, at most 5.5) and the swap ticks unchanged;
draw and sheathe clear of the body (`grips.animation_problems`); the club head at the hit within -0.5..0.8 px of the
ground and the second hand within 2.1 px on every tick, on all six bases; the slam's hit and its timing-file frames
unchanged; the pose `form` field (inherited, mirrored, validated); the slam's body rotation written as Molang, read
back per form, deeper on the adult; FK of a sampled form-scaled rotation is the file rotation. Sabotage: with the
style sources of before the polish, the shield test fails on every base (11-18 px) and the club tests fail (the head
1.7-8.9 px above the ground, the hand 3.0 px off).

### Open items

- The club string's hits and the guard on a chibi_m (2.6-2.9 px off the handle, above).
- In game (A2, CP2): the slam's form-scaled fold depends on `v.fc_dhip`, which HeroModel sets; worth a look on an adult
  hero that the hips fold and the club meets the ground.

---

## C1: Natsuho Komezaki (2026-11-14)

HEROES_V2.md 15.2 row C1, Natsuho's half (Hinae Shirasuzu, the row's other hero, is still open). This adds her source
file, her art and her signature animations. Her kit is the slice fixture's
(`src/test/resources/heroes/slice/natsuho_komezaki.json`, copied field for field: the server definition in
`data/factionclash/heroes/` belongs to the engine side). She has one body for good (the owner's rule of 2026-11-13,
HEROES_V2 3.6): **adult, female, slim, drawn at 92%** (SLICE_PLAN 3.1). Every preview shows her only in that body.
No hit tick, marker or timing file of an existing clip changed: the shared libraries build byte for byte as before,
and her animation and timing files are new.

### What exists

| Path | What |
|---|---|
| `herosrc/heroes/natsuho_komezaki.hero.json` | the source: the kit, `art` (body, palette, parts, face, claw gauntlets, phase looks) and `animations` (style `fists`, blink `normal`, the G10 variant `cat_twist`, 95 poses of her own, 18 signatures) |
| `herosrc/parts/tails/cat_puff.json` | new part: the phase-2 puffed fur over her tail (below) |
| generated | `geo/hero`, `textures/entity/hero` (texture and glow mask), `heroes/*.visual.json`, `animations/hero/natsuho_komezaki.animation.json`, `data/factionclash/hero_timings/natsuho_komezaki.json` |
| `tools/herogen/render/hero_previews.py`, `cli.py hero-previews <ids> [--check]` | a hero's own preview set in `docs/heroes-v2/previews/heroes/<id>/`, drawn in the hero's one body only. The captions are generic (the pronoun comes from the base); `OWN_CAPTIONS` holds a hero's own lines (what its phase looks, escape and variants show) |
| `gen.py` (chain `reuse`), `lint.py` and `parts.py` (`requires`) | the generator additions the puffed tail needed |
| `anim/quality.py`, `anim/timing.py` | the class wind-up minimum counts from the action's start (below) |
| `tests/test_natsuho.py` | 12 tests (below) |

### Her look

She is the harbour cat: a cream bob with an orange accent and cream tufted cat ears, a long cat tail and calico
patches. She wears an open jacket over shorts, a belt and an obi sash, tabi boots and a choker with a bell, and a paw
crest on her back (her emblem). Her palette is the outfit's: orange `#E0823A`, cream `#F4EFE6`, charcoal, a red
trim, plus a gold glow `#FFB23E`. Her claw gauntlets are worn on both hands (`weapons/claw_gauntlet`, `pair`,
`worn`). Her face has amber cat eyes with slit pupils, flicked lashes, thin arched brows, a cat mouth with a left
fang, a soft blush and whisker paint. One face override fits her tsundere manner: her `happy` is a blushing scowl
(eyes `angry`, mouth `frown`, cheeks `blush`), so she never quite looks pleased.

- **Phase 2** (60%): her tail fur puffs up (`tails/cat_puff`, shown from phase 2).
- **Phase 3** (25%): her eyes glow.

**The puffed tail** is a chain that `reuse`s the bones of the tail an earlier part built. It has no points of its
own: it takes `tails/cat_long`'s points, so it fits whatever size that tail chose. It declares no bone. Worn with
`"phase": 2`, its boxes nest in `p2_tail_1..5` children of the tail's own bones, so it follows every key and every
spring of the tail. Each segment is a fat box with two boxes rolled about it (`fine_`) and a tapered tip of fur
tufts. A reuse without the earlier chain, or with points or copies of its own, is an error. A part's new
`requires` field names the parts it is worn over: `lint`, the geometry check and the parts sheets build
`tails/cat_puff` over `tails/cat_long`, and the static rule checks that each required part exists.

### Signature animations

Her own file has the 18 signatures and the `cat_twist` variant's `move.air_jump` (G10). The fists library and the
core cover the rest. Each move's clip has the kit's frames to the tick:

| Clip | Length | Class | Server frames (ticks) | What happens |
|---|---|---|---|---|
| `move.claw_string` | 1.2 s | light | 9 / 10 / 7, hits 9, 13, 18 | right claw raked down, left raked back, both claws down together (the bleeding finisher); upper body and hips |
| `move.pounce` | 1.9 s | heavy | 16 / 4 / 20 | the crouch on her own foot spots, the hip wiggle, the spring (`hit` 16), the landing with both claws driven down (`cast` 19), the paw lick with her eyes shut (her recovery weak point) |
| `move.hiss` | 1.8 s | area | 20 / 2 / 16 | the jolt back, the bristle (back arched, ears flat, the tail straight up and trembling: the tell), the blast (`hit` 20, `cast` 21) |
| `move.scratch_dash` | 1.75 s | heavy | 16 / 5 / 16 | the side-on sprinter's crouch with the claw trailing, the burst (`hit` 16), the rake mid-dash, the brake on deep knees (`cast` 20) |
| `move.cat_reflex` | 1.8 s | heavy | 20 / 2 / 16 | the counter stance held (the stance is the wind-up, 6.4), the riposte at its end (`hit` 20, `cast` 21), the hop back |
| `move.zoomies` | 2.8 s | heavy | 18 / 16 / 24 | the crazed crouch, the launch (`hit` 18), a frantic sprint in place to the skid (`cast` 33), a dizzy sway, then arms crossed and head turned away |
| `ultimate` | 3.1 s | ultimate | 30 / 4 / 30 | the play bow and the arched stretch, the yowl (`hit` 30, `cast` 33), down into the hunter's crouch |
| `state.frenzy` | 1.6 s | - | - | starts at the ultimate's hit (the state begins there): a full-body shake of the fur, the hunter's crouch with the tail lashing |
| `phase.2`, `phase.3` | 0.9, 1.1 s | - | - | inside the 1.0 s and 1.2 s transitions: the flustered jolt as the fur puffs out ("F-fine!"); the bowed head coming up with glowing eyes |
| `intro` | 1.5 s | - | - | arms folded and head turned away, a glance back, the guard (the server holds the intro as long as the clip) |
| `victory`, `taunt` | 2.0, 1.5 s | - | - | the purr she will deny, the smug peek and the "shoo"; the bored yawn and the "come on" curl of the claw |
| `last_stand`, `escape` | 1.6, 0.6 s | - | - | the claws struck together, then flung out ("This is MY harbour"); the startled jolt and look back, then the run (upper body over `move.run`) |
| `personal.1-3` | 2.4, 1.4, 2.6 s | - | - | the paw lick, the ear flick and head shake (the bell), the stretch and yawn |

- **Markers**: a move releases on a `hit`. Where the kit's active part is longer than its hits, a `cast` marks the
  active part's last tick: the server's active part runs from the first `hit` or `cast` to the last (4.8). That is
  how a one-hit leap, dash or charge gets the kit's active ticks.
- **Sounds** (SOUND.md 4.7, 5): only the client kinds are markers: each move's `release`, the pounce's `end`
  (the paw lick), the phases, the intro, the taunt's and the victory's emotes, and the quirks of `personal.1-3`.
  Wind-ups, impacts, the ultimate's sting and the frenzy are played by the server, so they have no marker.
- **Faces**: every move, the ultimate, the state, the phases and the end-of-fight moments key `face_ctrl`.
- **Rules**: every own clip passes the quality rules on all six bases. Her claws stay clear of her body on every tick
  (`grips.animation_problems`). Every leg pose was solved on her stance's four sole corners (planted feet). The arm
  poses were solved with a small Nelder-Mead arm solver on her own skeleton, against hand-point and claw-direction
  targets and the claw clearance, with waypoints where an arm crosses in front of her (crossed arms, wind-ups past
  the thighs). The neck stays within its 40-degree pitch.
- **Not yet in the engine**: `last_stand` and `escape` have no action code in `HeroActions` (6.13). They are ready
  for the engine side and are named in 15.2's C1 row.

### The class wind-up minimum counts from the action's start

The quality rule compared the first `hit`'s clip time with the class minimum. The server's frames, and the kit
validator, count from the action's start, the controller's 2-tick blend included (`writer.frames_of`, 4.8). So the
rule was 2 ticks stricter than the kit, and a kit at the class minimum could not be matched within one tick
(Natsuho's pounce and scratch dash: 0.8 s heavy wind-ups). The rule now adds the blend, and the timing NOTE uses the
written `frames.windup`. No library clip's result changed. `test_anim` now checks both sides of the line: a `hit` at
0.25 s on a light clip fails, and 0.30 s passes.

### Previews (`docs/heroes-v2/previews/heroes/natsuho_komezaki/`)

All are drawn in her one body. `hero-previews natsuho_komezaki --check` re-renders the set and compares it byte for
byte.

- `turnaround.png`
- `faces.png`: the 12 moods, the blink half and closed, the two talk frames, at 3/4
- `phases.png`: phases 1-3
- `sheet.png`: every own clip as a filmstrip
- a GIF per signature move (`move_*.gif`)
- `ultimate.gif`, with `state.frenzy` from the hit
- `string.gif`: `attack.1-3` and the follow-up chains
- `moments.gif`: intro, the two phases, taunt, victory, last stand, escape
- `idles.gif`: `personal.1-3`
- `locomotion.gif`: walk and run, the crouch jump into the leap, the air jump, the land roll, the wall kick, climb
  and mantle

### Tests (383 -> 395: `tests/test_natsuho.py`, 12)

- Her one body: adult, slim, 92, and no phase changes it.
- The kit is the fixture's.
- Every move's and the ultimate's `frame_ticks` equal the kit's, with the claw string's hits at 9, 13 and 18, and
  `assets.check_frames` passes on the written chain.
- Her own clips pass every rule on every base.
- The claws stay clear on every second tick.
- Every sound id is in her catalogue, and every slot named above has its marker.
- `face_ctrl` keys are present where the moment needs a face.
- The puffed tail's bones are children of the tail's bones and show from phase 2.
- Chain `reuse` takes the earlier chain's points, and fails without it.
- `lint` wears the required part, and the static rule is clean.
- The preview captions are generic for any other hero, and her own lines apply only to her.

`test_style`'s every-part-on-every-base test now wears a part over the parts it `requires`. `test_anim`'s wind-up
case moved with the rule (above). Sabotage: with the pounce's `hit` one tick late, the frames test fails
(16 / 4 / 20 against 17 / 3 / 20).

### Decisions

1. **Her kit is the fixture's verbatim**: the art pipeline does not change a kit. The engine side copies the same
   fields into her definition.
2. **`hit` then `cast` for a one-release move** (above), not a second `hit`. A second `hit` would deal damage twice:
   the markers replace the form's hits when the counts match.
3. **The puffed tail is a phase part over the real tail** (`reuse`), not a second tail. It cannot drift from the tail
   it covers, and before phase 2 its bones are simply hidden.
4. **The wind-up rule follows the server's frames** (above).
5. **The previews are a hero's own set** in `previews/heroes/<id>/`. The library previews (the style and locomotion
   sets) stay as they are.

### Open items

- **Engine side**: action codes for `last_stand` and `escape` (6.13), and her definition in `data/factionclash/heroes/`
  (the fixture's fields).
- **The counter's riposte**: the clip shows the riposte at the stance's end, as the kit's frames do. When she is
  struck during the stance, the server answers at once. Playing the riposte at that moment needs the engine to jump
  the clip, or a separate riposte action.
- **Library previews that still show her look on a second body** were made before the one-body rule, from the
  `natsuho` sample outfit in `herosrc/style/outfits.json`, not from her source:
  - the fists style set (`previews/anim/styles/fists*`, on a chibi_f and an adult_f)
  - `previews/anim/locomotion/hero_natsuho.*` (on a teen_f and a chibi_f)
  - the sample-outfit sheet `previews/parts/outfits.png` (all six bases) and `previews/parts/weapons_poses.png` (the
    sample outfit's own base, `teen_f`)

  C1 did not touch them: the libraries and the sample outfit they are drawn from build as before. They should be
  re-rendered with a style fixture in her place when next touched.
- Hinae Shirasuzu, the other half of C1 (done: the next section).

---

## C1: Hinae Shirasuzu (2026-11-14)

HEROES_V2.md 15.2 row C1, Hinae's half (Natsuho's is the section above). This adds her source file, her art, her
signature animations, the talisman ring's orbit loop (G6c's open item) and a lineup of the heroes with a source. Her kit
is the slice fixture's (`src/test/resources/heroes/slice/hinae_shirasuzu.json`, copied field for field; her server
definition belongs to the engine side). She has one body for good (the owner's rule of 2026-11-13, HEROES_V2 3.6):
**adult, female, standard build, drawn at 96%** (SLICE_PLAN 3.4). Every preview shows her only in that body. No hit
tick, marker or timing file of an existing clip changed: the shared libraries build byte for byte as before, and her
animation and timing files are new.

### What exists

| Path | What |
|---|---|
| `herosrc/heroes/hinae_shirasuzu.hero.json` | the source: the kit, `art` (body, palette, parts, face, wand and talisman fan, phase looks) and `animations` (style `focus`, blink `normal`, 56 poses of her own, 18 signatures) |
| generated | `geo/hero`, `textures/entity/hero` (texture and glow mask), `heroes/*.visual.json`, `animations/hero/hinae_shirasuzu.animation.json`, `data/factionclash/hero_timings/hinae_shirasuzu.json` |
| `anim/poses.py` | a pose's `scale` {bone: [sx, sy, sz]} (inherited, mirrored, above 0) |
| `anim/recipes.py`, `anim/quality.py` | `REST`: a channel's value at rest, so a key that leaves a scaled bone out keys it at 1 and the settle and start rules' goal for a scale is 1 (it was 0 for every channel) |
| `parts.py` | the weapon entry's `phase_glow` {phase, cubes, slot}: glowing shells round named pieces of the prop, from a phase on |
| `render/hero_previews.py`, `cli.py` | the secondary controller's `sec.loop` under every film and sheet frame, each clip drawn in its phase, the locomotion by mobility traits, a thrown basic attack in `string.gif`, the sheet's actions over their base (below), and `hero-previews --lineup [--check]` |
| `tests/test_hinae.py` | 18 tests (below) |

### Her look

The white bell shrine maiden, as the sample outfit of G6b and G6c drew her: a hime cut tied low with white paper
(`hair/hime_long`), a white kimono top with plain sleeves (`outfit_top/kimono_top`), a pleated red hakama
(`outfit_bottom/hakama`, drop 1.75), split-toe socks and clogs (`legwear/geta`), the faction sash with its bow, three
talisman strips at the belt, the purification wand in her right hand (thrust through the sash at the left hip when
carried) and the fan of talismans in her left. Palette SLICE_PLAN 3.4's (white, red, gold, cream, a pale gold glow;
`leather` for the clogs). Face: `round` eyes with a flick, lashes 2, `worried` brows, the `soft` mouth and a soft blush.
Two overrides fit a sweet-natured hero: her `angry` is a worried pout (angry eyes, the frown mouth, no bared teeth) and
her `smug` a closed-eye smile.

- **Phase 2** (60%): seven glowing talismans orbit her (`accessories/talisman_ring`, worn with `"phase": 2`: its bone
  is `p2_talisman_ring`, hidden before phase 2).
- **Phase 3** (25%): her eyes glow gold (frame 10 in the glow colour) and the wand's paper streamers turn to glowing
  gold (`weapon.phase_glow`, below).

**Why the streamers glow through shells, not the phase texture.** A `glow: "phase"` region lights up in the `_p2`
texture, and `HeroModel.getTextureResource` uses that texture whenever the phase is 2 or more (it reads
`visual.json`'s texture flag, not each phase's `texture` entry). So a glow that must wait for phase 3 needs its own
pieces: `phase_glow` {phase 3, cubes `shide_*`, slot `glow`} gives each streamer fold a shell SHELL (0.05) px larger
every way on a `p3_` bone, painted in the glow colour and written to the glow mask, in the hand and on the carried
copy. The phase look's bone rule hides them before phase 3, and Hinae needs no `_p2` texture at all. 16 cubes, 213 in
all (budget 300), 107 beyond the fine distance (130).

### The ring's orbit: `sec.loop`

`HeroAnimator` already plays `sec.loop` on the secondary controller whenever the hero's chain has one (6-tick blend,
paused beyond 32 blocks and in death). Hinae's own file has it: the ring turns once round her every 6 s
(`primitives.spin`, linear, the whole turn unwound by a pre/post key at the loop's end) and wobbles 5 degrees on two
axes a quarter cycle apart, like a floating ring. It keys only `p2_talisman_ring`'s rotation, so her moves may still
scale and lift the ring through its other channels (GeckoLib overrides per bone and channel): phase 2 unfolds it from
her chest, Seven Talismans draws it in tight and flings it out into the barrier, the ultimate widens it and flares it
at the hit. Before phase 2 the bone is hidden and the loop turns nothing visible.

**Generator addition: pose `scale`.** A pose may carry `scale` {bone: [sx, sy, sz]}; the recipe expander keys a bone a
pose leaves out at its rest value (`recipes.REST`: 1 for a scale, 0 for rotations and positions; it used 0 for every
channel, which would have collapsed a scaled bone to nothing between keys), and the settle and start rules take a
scale's goal as 1 (`quality.check_settle`, `check_start`). A key with `over` takes the key's scale as it is (a size,
not an offset). GeckoLib scales a bone about its pivot; the ring's pivot is its centre, so it grows from her chest.

### Signature animations

Her own file has the 18 signatures. The focus library and the core cover the rest (her basic attack, a thrown
talisman, is the focus library's `attack.ranged`). Each move's clip has the kit's frames to the tick, with one `hit` at
the release and a `cast` on the active part's last tick (the volley, the zone, the nova, the barrier and the sweep each
release once):

| Clip | Length | Class | Server frames (ticks) | What happens |
|---|---|---|---|---|
| `move.talisman_fan` | 1.1 s | light | 10 / 6 / 8 | the hips and chest wind round to her right, the fan cocked across the right shoulder; the left arm sweeps out flat at chest height (`hit` 10, the throw sound) and on out to her left (`cast` 15) |
| `move.sealing_circle` | 1.8 s | area | 20 / 2 / 16 | the wand raised high at her right, its bells rung with the eyes closed (the tell), a last lift, then brought down sharply to point at the ground under the target (`hit` 20, `cast` 21) |
| `move.purify` | 1.0 s | light | 12 / 2 / 8 | the wand upright before her face in a short prayer, one wide cleansing sweep out to her right (`hit` 12, the purify sound), a smaller sweep back |
| `move.exorcise` | 1.8 s | area | 20 / 2 / 16 | down on bent knees (feet planted), the hands drawn together round the upright wand and trembling with gathered light, then up with both arms flung wide (`hit` 20) |
| `move.seven_talismans` | 1.7 s | heavy | 16 / 4 / 16 | the wand before her face and the fan raised high while the ring draws in tight and rises (scale 0.55), both arms swept wide as it flies out past its size (`hit` 16) and settles (`cast` 19) |
| `move.purifying_dance` | 4.4 s | area | 20 / 40 / 30 | the kagura's opening turned to her right, ringing (the tell); the beam's edge at her right (`hit` 20, the dance sound), one slow sweep through the front with the knees dipping to her left (`cast` 59); then the kneeling prayer, her weak point: a step that sets her feet, down on the right knee, two claps (the end sound), a bow, up and a step back into her stance |
| `ultimate` | 3.3 s | ultimate | 32 / 6 / 30 | the wand at the chest in both hands with the head bowed, raised slowly overhead while the left arm opens palm up and the bells ring in a slow circle (the ring widens and rises), swept down in a wide arc to the front-right (`hit` 32: the rite blooms, the ring flares), `cast` 37, a small bow |
| `phase.2`, `phase.3` | 1.1, 1.1 s | - | - | inside the 1.2 s transitions: the pleading look as the ring unfolds from her chest; the bowed head coming up with glowing eyes, the wand swept up |
| `intro`, `victory`, `taunt` | 1.5, 2.0, 1.4 s | - | - | a polite bow, then the wand offered with an apologetic look; relief, a small bow toward the defeated and a hum; the fan waved gently "please, go home" |
| `last_stand`, `escape` | 1.6, 0.6 s | - | - | the wand swept across to bar the way, then held upright in both hands; a quick apologetic bow over the run |
| `personal.1-3` | 2.4, 2.6, 2.8 s | - | - | the bell shake (the left hand jingles the bells of the wand in her sash, which shakes on its bone, `weapon_back`), tidying up (sweeping the dust off her sleeves, smoothing the hakama, a hum), the prayer (palms together, two claps, a small bow) |
| `sec.loop` | 6.0 s | - | - | the ring's orbit (above) |

- **Markers**: the client sounds of SOUND.md 4.7 are markers in the clips their slot names: the releases of Talisman
  Fan, Purify and the dance, the dance's `end` (the claps), both phases, the intro and the three quirks (and the `hum`
  emote in the victory and the tidying). Wind-ups, impacts and the rite's release are the server's: no marker. The
  rite's bell loop follows the action code (`HeroLoopSounds`).
- **No `ultimate.loop`**: the rite's channel is its wind-up and active part (the server breaks it there,
  `MoveTiming.stops`), and the server ends the run after the kit's frames, so a `.loop` would show only for the tick
  the run ends, and it would require an `ultimate.end` the engine never sends.
- **Faces**: every clip but the orbit keys `face_ctrl`.
- **Rules**: every own clip passes the quality rules on all six bases, and the wand and the fan stay clear of her body
  on every tick (`grips.animation_problems`). The arm poses were solved on her own skeleton for grip points and prop
  directions (some in the chest's or an arm's frame: the hands together before a bowed chest, a hand brushing a
  sleeve), with the props' clearance, a minimum elbow bend where an overshoot follows (the seal's strike) and wrists
  inside G6c's limits. The dance's kneel uses the core's `kneel.r` chain: a dance step first sets the left foot on the
  relaxed stance's spot, and on the way up a step brings it back to the focus stance's, so no foot ever slides
  (`steps`, the slide rule).

### Previews (`docs/heroes-v2/previews/heroes/hinae_shirasuzu/` and `heroes/lineup.png`)

Her set, as Natsuho's, with a README: `turnaround.png`, `faces.png`, `phases.png`, `sheet.png`, a GIF per move,
`ultimate.gif`, `string.gif`, `moments.gif`, `idles.gif` and `locomotion.gif`. The preview code gained:

- the secondary controller: `sec.loop` laid over every film and sheet frame (`secondary`), so the ring orbits;
- each clip in its phase (`phase_of_clip`): a move from its kit's first phase, `phase.N` in N, the last stand in the
  last phase, and any clip keying a `pN_` bone from N (her ultimate in phase 2, so the ring shows it);
- the locomotion by mobility traits (`loco_segments`): an acrobat's set as before (Natsuho), otherwise walk and run, a
  jump and the ladder climb, and the swim for a swimmer (Hinae);
- a thrown or shot basic attack (`attack.ranged`, twice) before the style's string in `string.gif`;
- the sheet's actions now play over their base with the weapon drawn and the blend: it read the kind and the base from
  the clips parsed back from the written file, which carry no meta, so every clip was sampled alone as a base loop
  (Natsuho's sheet is re-rendered; her GIFs are unchanged);
- `hero-previews --lineup [--check]`: every hero with a source (the fallback aside) on grass in its own body at its
  own scale, with the test rig: the idle stance from the 3/4 front, the weapon drawn, and the last phase.

### Checks run

`herogen check hinae_shirasuzu` (all of 14.2) OK; `build --check --all` OK (the libraries, `_fallback`, Natsuho and
the test rig build byte for byte as committed); `lint` of her nine parts (hime_long, kimono_top, hakama, geta,
sash_bow, talisman_strips, talisman_ring, purification_wand, talisman_fan) on every base: 182 builds, 0 problems;
`hero-previews hinae_shirasuzu --check`, `hero-previews natsuho_komezaki --check` and `hero-previews --lineup --check`
OK; the Python suite, 413 tests, OK.

### Tests (395 -> 413: `tests/test_hinae.py`, 18)

- Her one body: adult, standard, 96, and no phase changes it; the kit is the fixture's.
- Every move's and the ultimate's `frame_ticks` equal the kit's with one `hit` at the wind-up's end, and
  `assets.check_frames` passes on the written chain.
- Her own clips pass every rule on every base, at most 24, no `ultimate.loop`; the props stay clear on every second
  tick.
- Every sound id is in her catalogue, every client slot has its marker and no server slot is a marker.
- `face_ctrl` in every clip but the orbit.
- The orbit keys only the ring's rotation, turns one way at a steady rate and closes; phase 2 starts the ring small
  and opens it past its size; the moves scale the ring, never turn it, and end it at 1.
- The streamer shells: 16, glowing, on `p3_` bones that show from phase 3, SHELL larger, no `_p2` texture;
  `phase_glow` refuses a glob that matches nothing and a phase other than 2 or 3, and puts shells on `p2_` bones.
- Pose `scale` is parsed, inherited and mirrored and refuses 0; a key that leaves a scaled bone out keys it at 1, and
  the settle rule passes an action that ends at 1 and fails one that ends at half size.
- The preview rules: each clip's phase, her locomotion (a swim, no wall kick), the ring's orbit at 1.5 s, and the
  lineup (both slice heroes, the test rig last, no fallback).

### Decisions

1. **Her kit is the fixture's verbatim** (as Natsuho's).
2. **The orbit is `sec.loop` on the secondary controller**, the controller `HeroAnimator` already runs, keying only
   the ring's rotation; her moves move the ring through its scale and position. No new bone or part change.
3. **Pose scale with a rest value per channel** rather than a scale-only key type: the expander's "a key keys every
   bone any key mentions" rule then holds for scale too.
4. **Phase-3 streamers as shells** (above): the engine's phase texture cannot wait for phase 3.
5. **No `ultimate.loop`** (above).
6. **Her idle quirks use what she carries**: the bells hang on the wand in her sash, so the bell shake jingles them
   there (the drawn wand is the server's weapon flag, which an idle does not change).

### Open items

- **Engine side**: her definition in `data/factionclash/heroes/` (the fixture's fields); action codes for `last_stand`
  and `escape` (6.13); confirm in game that the ring orbits (`sec.loop`) and that a move's scale keys and the orbit's
  rotation combine on the ring as GeckoLib's per-channel override says.
- **The ring at the phase change**: the ring's bone shows from the moment the phase becomes 2, and `phase.2` starts
  with it folded small, so for the action's 2-tick blend it shrinks from full size before it unfolds. If that flash
  shows in game, the engine can set the phase bit a tick after it sends `phase.2`.
- **Library previews that still show her look on a second body** (drawn from the `hinae` sample outfit, not her
  source; not touched here): the focus style set (`previews/anim/styles/focus*`, on a chibi_f) and the sample-outfit
  sheet `previews/parts/outfits.png` (all six bases). `weapons_poses.png` draws her outfit on adult_f, her own body.
- The turnaround's second caption line (the stats) runs past the right edge for both slice heroes; it is the shared
  `sheet.turnaround` layout, and widening it there would re-render every committed turnaround, so it is left.
- Not run here (Gradle is off limits in this task): the Java suites (`HeroAssetsTest`, `BuiltinHeroesTest`,
  `verifyHeroAssets`), GameTests, the in-game and campaign checks, and the jar.

---

## C1 review gate 2: Natsuho and Hinae (2026-11-18)

HEROES_V2.md 14.4 gate 2 for C1, by a separate art director. A usage-limit pause stopped the first reviewer while it
was fixing its findings; this run read its uncommitted diff and notes, checked each of its fixes, finished the
checklist and the preview cast, rebuilt both heroes and ran every check. Each finding was confirmed with a number or a
render before it was fixed. The look, the style and the kits are unchanged; no hit tick moved.

### The checklist

| Item | Verdict |
|---|---|
| Distinctness NOTEs (14.2) | none: `herogen check` compares heroes of one archetype, and the cat and the shrine maiden are alone in theirs. Measured anyway against each other and the six other slice looks (their sample outfits on their adult bodies), front and side at 4x: silhouette IoU 0.57-0.82 (the highest, 0.82, Hinae and Kagenori: long hair over a hakama), all under 0.90. Two largest colour regions: Natsuho charcoal and orange, Hinae red and near-black. Hinae and Benika (oni, C2) share them within delta-E 11.6 and 6.2, below the NOTE's 15, but different archetypes give no NOTE: C2's gate should put Benika beside Hinae (her horn, mane, open coat and large build should carry it) |
| Silhouette | distinct: Natsuho's tall odd-coloured ears, ahoge, bob and long tail over a short jacket and long legs; Hinae's blunt hime cut, the long low tail of hair and the hakama's trapezoid |
| Greyscale | both read in three clear values: Natsuho's orange jacket mid-grey between the cream top and socks (light) and the charcoal shorts and boots; Hinae's white top, dark red hakama, black hair |
| Face frames | blink (half, closed), talk A and B, angry, hurt, and the moods at 3/4: right. Natsuho's `happy` is the blushing scowl and Hinae's `angry` the worried pout, both by design (SLICE_PLAN 3.1, 3.4) |
| Clipping | `attack.1-3`, `attack.ranged` and every signature move looked at frame by frame at 5x from the 3/4 front and the right side: no limb or prop through the jacket, the sleeves, the hakama or the tail; the props' clearance rule (`grips.animation_problems`) passes on every second tick |
| Weapon | the claws on both hands in every clip; the wand in the right hand and the fan in the left, the wand at the obi when sheathed |
| Phase looks | visible: Natsuho's puffed tail (now calico, fix 4 below) and glowing eyes; Hinae's ring of seven talismans, gold eyes and gold streamers |
| One body | each hero's own previews and the lineup show her only in her adult body. The library previews did not (fix 6) |
| Existing characters | Natsuho: a calico cat girl with a bell choker, claw gauntlets, an orange open jacket and odd-coloured ears; generic catgirl marks, no famous design shares the combination. Hinae: long straight black hair tied low with white paper over a white kimono top and red hakama is the traditional shrine maiden's dress, which a well-known anime shrine maiden also wears. Her own marks keep her apart: the blunt hime cut with jaw-length side locks, red eyes and a soft blushing face, the red bow at the collar, the bell wand and the talisman fan (no bow and arrows), the orbiting talisman ring. Verdict: she reads as the genre, not as that character. Listed for the owner's screenshot review and for ROSTER_PLAN 8's concept search (step 2, still due); if more distance is wanted, the cheapest change is a small bell ornament in her hair (her "White Bell" title) |
| Moves as the kit sheet | yes. Claw string: right rake, left rake, both claws down. Pounce: crouch and hip wiggle, spring, claws down, paw lick. Hiss: jolt, bristle, blast (fixed: fix 1). Scratch dash: side-on crouch, burst, rake, braking skid. Cat reflex: the stance held, riposte, hop back. Zoomies: crazed crouch, launch, sprint, skid, dizzy sway, arms crossed. Ultimate: play bow, stretch, yowl, hunter's crouch. Talisman fan: wind to the right, flat sweep out to the left. Sealing circle: wand raised, bells rung, struck down. Purify: prayer and sweep. Exorcise: knees bent round the trembling wand, arms flung wide. Seven talismans: ring drawn in and flung out. Purifying dance: the sweep, then the kneeling prayer. Ultimate: wand raised, swept down, bow |
| Hit ticks | `herogen check` (the frames rule on the written chain) passes for both. Natsuho's timing file changed only its `source_sha256` (the hiss's tail keys); Hinae's did not change |

### Found and fixed

1. **Natsuho's hiss: the tail curled back over instead of standing up.** The kit sheet's tell is "ears flat, tail
   puffed", and her own clip description says "the tail straight up and trembling", but the six hiss poses keyed only
   `tail_1` (25-56 degrees) and `tail_2` (6-18): segments 3-5 kept their rest curve and bent back over at 42-107
   degrees from vertical, a relaxed curl. Now the bristle poses (`hiss_startle`, `hiss_arch`, `hiss_draw`,
   `hiss_blast`) key all five segments, `tail_1` 89.5-100 and the rest counter-bent (-23/-24, -58, -29, -39.5), and
   the two recovery poses scale that back by two thirds and one third: every segment stays within 29 degrees of
   straight up from 0.4 s to 0.9 s. Only `move.hiss`'s tail keys changed; its markers, frames and length did not.
2. **Hinae: a dark streak on her fringe.** Two of `hair/hime_long`'s sixteen crown strands, the two over the front
   centre (lengths 4.75 and 5 from the repeating list), reached down past the fringe's top, and their dark tips showed
   between the fringe strands as an arrow-shaped mark on her forehead: 153 px of crown strand inside the fringe's
   outline in the front view at 8x. They are now 3.5 long (the list is spelled out for all sixteen): 0 px.
3. **Natsuho's tufts stuck out like antennae.** `hair/bob`'s tufts (Natsuho's two) were 2.25 wide, 3.5 long and flared
   72 degrees, so from the front two grey spikes stood out level with her eyes. Now 2 wide, 2.75 long, flared 40, and
   0.4 px nearer the head: short flicks at the hem. No style fixture wears the tufts.
4. **Natsuho's puffed tail had no calico.** `markings/calico` painted `tails/*`, and phase 2's puffed fur is
   `tails/cat_puff`'s cubes on `p2_tail_*` bones (`p2_tails/*` in the glob's terms), so her phase-2 tail turned plain
   cream. The marking now paints `p?_tails/*` too: the fur carries the patches of the tail it covers.
5. **The turnaround's stats caption ran past the right edge** for both heroes (C1's open item), and, as
   `render-previews --check` showed, for the style fixtures Rowan and Wren too. `sheet.turnaround` now wraps it at its
   last "; " that fits and moves the views down a line; a caption that fits draws exactly as before (every other
   turnaround of the G2 set and the style set is byte for byte the same). `render/turnaround_style_rowan.png` and
   `turnaround_style_wren.png` are re-rendered.
6. **Library previews showed named slice heroes on bodies they do not have** (the owner's one-body rule, HEROES_V2
   3.6). Re-rendered with a cast that keeps the rule:
   - **Style sheets** (`previews/anim/styles/`, all seven sets): the unnamed style fixtures in their own bodies, Tansy
     (chibi_f) or Tobin (chibi_m) and Maren (adult_f) or Garrick (adult_m), each armed with the slice weapon of the
     style (the weapon entry of the sample outfit that uses it, `style_preview.armed_fixture`). Maren leaves her
     gauntlets off under the claws (`LEAVE_OFF`: they share the claw gauntlet's faces, 26 px in the guard). Every pair
     passes `lint`'s full check (builds, the coplanar rule, the hand poses).
   - **Locomotion hero GIFs** (`previews/anim/locomotion/hero_*`): each in one body. Natsuho's cat twist now plays
     on her own source in her adult body (it showed the sample outfit on a teen and a chibi); Yugen, Kagenori and
     Ambrose show their sample outfits on adult_m only (their chibi copies are gone) until their sources exist.
   - **Sample outfits sheet** (`previews/parts/outfits.png`): each outfit on its hero's one body only, from five
     sides (it showed every outfit on all six bases); `lint` still builds and checks every outfit on all six. The
     adult figures now reach the top of each row, so the label column is wider for this sheet (`_grid`'s new
     `label_w`, 215) and the notes no longer run into the first figure.
     `herosrc/style/outfits.json`: Natsuho's sample outfit's `base` is now adult_f (it was teen_f), so
     `weapons_poses.png` draws her row on her own body too.

   No library preview shows a named hero on a second body any more: the core, locomotion-group, style-pass, G2 and G3
   sets were already drawn with the fixtures and the test rig, and the parts sheets with the lint mannequin. One
   difference remains on purpose: the sample outfits are drawn at the standard build, while Natsuho, Yugen, Tobimaru
   and Ambrose are slim and Benika large. They are lint fixtures of the outfit parts, not the heroes, and the heroes'
   own previews (C1-C4) draw them in their real builds.

### Previews

Rebuilt: Natsuho's and Hinae's sets (`previews/heroes/<id>/`, every file: the tail, the fringe, the tufts and the
caption change most images) and `previews/heroes/lineup.png`; the seven style sets and their README; the four
locomotion hero GIFs and sheets and the locomotion README; `previews/parts/outfits.png`, `weapons_poses.png` and
`head_hair.png` (the hime cut's crown); the G2 set's Rowan and Wren turnarounds (the caption). Looked at: both turnarounds, face strips and phase sheets, every move, string
and ultimate as 14-frame strips at 4-5x from two sides, both sheets, the lineup, a style sheet and string, the new
outfits sheet and the hero locomotion GIFs.

### Tests (413 -> 421: `tests/test_gate2.py`, 8)

- The hiss's tail: every segment within 35 degrees of straight up from 0.4 s to 0.9 s (sabotage: the committed C1
  poses give 42-107 degrees).
- The puffed tail wears the calico: 10-90% of its texels differ from the same hero built without the marking
  (sabotage: the old glob gives 0%).
- The bob's tufts are at most 3 px long and within 3 px of the side of the head (the old 3.5 fails).
- No crown strand of the hime cut shows inside the fringe's outline from the front at 8x (the old lengths: 153 px).
- The previews' cast: every sample outfit's `base` is an adult; the outfits sheet has no per-base columns; each
  locomotion hero GIF is a source in its own body or the sample outfit on its one body; the style sheets' cast is
  style fixtures in their own bodies. `test_styles`' cast test now checks the fixtures, their weapons and the parts
  left off.

### Checks run

The Python suite (421 tests, OK); `herogen check --all --style` (OK, the test rig's usual NOTE only) and `check
natsuho_komezaki hinae_shirasuzu` (OK, no distinctness NOTE); `build --all --check` (every hero and library fresh);
`lint hair/bob hair/hime_long markings/calico tails/cat_puff` with the eight outfits (108 builds and checks, 0
problems); `render-previews --check` after re-rendering the two wrapped turnarounds; the style set (`previews`)
rendered again and byte for byte the committed one; `anim-previews --core --check`, `--styles --check` and
`--locomotion --check`; `parts-previews --check`; `hero-previews natsuho_komezaki hinae_shirasuzu --check` and
`hero-previews --lineup --check`.

### Decisions

1. **Fixtures, not adult-only slice heroes, in the style sheets.** HEROES_V2 3.6 asks library sheets that prove one
   file fits every body to use the unnamed fixtures; the chibi row is the point of those sheets. The props stay the
   slice's own, because the style's hand poses were solved for them.
2. **Each fixture in its own body.** The fixtures are unnamed and could wear any body, but drawing each in the form it
   was designed for keeps the sheets honest about what a chibi and an adult look like.
3. **No bell ornament for Hinae in this gate**: her look is SLICE_PLAN 3.4's, the call is the owner's (gate 4), and
   the recommendation is recorded above.
4. **The old C1 sections' open items stay as written**; this section closes the preview-cast and caption items.

### Open items

- **Engine side (unchanged)**: both definitions in `data/factionclash/heroes/`, the `last_stand` and `escape` action
  codes, then the in-game gate 3 (screenshots: lineup front and back, a wind-up with its decal, a phase transition, a
  face close-up) and the campaign smoke of gate 5. `verifyHeroAssets` and `BuiltinHeroesTest` should run on the
  rebuilt files before the next jar.
- **C2**: put Benika beside Hinae in the lineup (their two largest colour regions are within delta-E 11.6 and 6.2).
- **The owner's review (gate 4)**: Hinae's distance from the genre's best-known shrine maiden (above), and
  ROSTER_PLAN 8's name and concept searches for both.
- **Housekeeping**: two mistyped background commands in this run (a shell variable set inside a backgrounded
  subshell) wrote stray files to the filesystem root: `/loco.log`, `/hpn.log`, `/hph.log`, `/sp.log`,
  `/locochk.log` (one-line logs) and `/stylecheck/` (a scratch copy of the 20 style-set PNGs, identical to the
  committed ones). The sandbox's safety check would not let the run remove anything at the root, so they need
  removing by hand (`rm -r /stylecheck /loco.log /hpn.log /hph.log /sp.log /locochk.log`). None is in the repository.

---

## R6 and B5: artifact and boss token icons (2026-11-18)

R6's icon half (`ARTIFACTS.md` 1.2 and 1.3, the catalogue of section 7 with the reviewed names and the revision's
growing and engineered artifacts) and B5's token icons (`BOSS_HEROES.md` 3.4). Three runs: a usage-limit pause stopped
the first after the generator, the shape library and the specs; the second added the command line, the textures, the
contact sheet, the mock inventory, the tests and the `herogen check` coverage, and was stopped by a container restart
while its checks ran; the third (this one) read the revised catalogue (`a8143f2`: sixty-one artifacts), gave the
generator growth states, drew the eleven new artifacts, and ran every check. No hero, part, clip or timing file
changed; the shared generator gained `palette.ramp_n` only (added, nothing existing changed), so every hero builds byte
for byte as before.

### What exists

| Path | What |
|---|---|
| `tools/herogen/icons.py` | the generator: shapes resolved from grids (optional parts, growth stages), material ramps, the painter, the pulse frames, the catalogue reader and every check |
| `tools/herogen/render/iconsheet.py` | the owner's contact sheet and the mock inventory |
| `tools/herogen/palette.py` | `ramp_n`: `ramp`'s hue and chroma rules over seven tones (-3..+3) with a wider step, for the icons' materials |
| `herosrc/artifacts/shapes.json` | the shape library: 60 silhouettes (16x16 letter grids); each letter a part with a role and a profile, or a growth stage |
| `herosrc/artifacts/icons.json` | 70 materials (a tone-0 colour, a lightness step, an optional texture) and 64 icon specs |
| `src/main/resources/assets/factionclash/textures/item/` | 88 textures (64 items, 24 more growth states) and 4 `.png.mcmeta` files (generated; never edit by hand) |
| `docs/heroes-v2/previews/artifacts/sheet.png`, `inventory.png` | the contact sheet and the mock inventory for the owner |
| `tools/herogen/tests/test_icons.py` | 19 tests |

Commands: `herogen icons` (build: write the textures), `herogen icons --check` (the rules below and the committed
textures against a fresh build), `herogen icons sheet [--check]` (the two preview images). `herogen check` without
hero ids (or with `--all`) runs the icon check after the heroes; with ids it checks only those heroes.

### The file names (what the item side registers)

One texture per item, named by its item id, in `assets/factionclash/textures/item/`:

- the 61 catalogue ids exactly as `ARTIFACTS.md` 7 spells them (`knapped_flint.png` ... `marrow_torc.png`,
  `menders_brazier.png`, `starless_pearl.png`, `bloodglass_ampoule.png`, `observer_lens.png`, `hollow_star.png`,
  `spirecore.png`);
- `artificers_sigil.png` (the smithing template, 1.1);
- `wither_token.png` and `ender_dragon_token.png` (`BOSS_HEROES.md` 3.4);
- `<id>.png.mcmeta` beside the four that pulse: `hollow_star`, `spirecore`, `wither_token`, `ender_dragon_token`;
- for each of the eight growing artifacts (7.8), four textures, its growth states (1.2: empty, a third, two thirds,
  full of its current cap): `<id>.png` (empty), `<id>_1.png`, `<id>_2.png`, `<id>_3.png`.

R2 and B2 add one model per item, `assets/factionclash/models/item/<id>.json` = `{"parent": "minecraft:item/generated",
"textures": {"layer0": "factionclash:item/<id>"}}`; a growing artifact's model adds `overrides` on an item property
(say `factionclash:growth`, 0 to 1, registered with `ItemProperties.register` as the bow's `pull` is) pointing at
`models/item/<id>_1.json` .. `_3.json` at 0.33, 0.67 and 1.0, each the same generated model with its state's texture.
The glint is the item's (`enchantment_glint_override` on rare finds, signatures and tokens), never the texture's. An
upgrade level changes no texture (1.2: the name's numeral says it). Until those items are registered the textures
are simply unused.

### The look (vanilla item rules, read from the 1.21.1 client textures)

- One object on a transparent 16x16 canvas, alpha 0 or 255, lit from the top left.
- Every material is one `palette.ramp_n` ramp of seven tones; a material paints at most eight colours (its seven
  tones and the white of a sparkle), checked per material, per frame and per growth state.
- Solid parts get their darkest tone as an outline where they meet the empty canvas; cords, rods, glass and liquids
  have none (vanilla's string, feathers and bottles); a `shadow` outline darkens only the sides away from the light.
  Inside the outline the top and left edges are a tone lighter and the bottom and right edges a tone darker; domed
  parts (gems, pearls, bells) shade across their whole area with a one-pixel specular at their brightest point; gems
  sit a tone darker where they meet their setting and carry a white sparkle.
- Textures (`speckle` for stone and coal, `grain` for wood, leather and cork, `wound` for the torc's twist) are
  hashed per icon (the same hash in every growth state, so the grain does not crawl), never random.
- Gems take the eight element colours of the particle table: physical `#B4ABA0`, fire `#FF7A1A`, frost `#8FD3F7`,
  shock `#FFDD3C`, venom `#5BAE3C`, blight `#2E2A33`, arcane `#A46BF0`, radiant `#FFF0C4`.

Tiers by material richness (1.2), enforced per spec and per growth state: a component is one material (a cord or a
fitting may hold it); an advanced artifact adds a setting and a gem in its element's colour (the eight foci wear their
own element's gem, read from the catalogue's "<element> damage" stat); a greater artifact adds a second precious
material, which must differ from its body and setting, with a one-pixel white highlight; a rare find carries a gem; a
signature carries a setting, a precious part and the pulse. The catalogue's "Growing artifacts" are advanced and
"Engineered artifacts" greater (1.3). The pulse (signatures and tokens): four frames stacked (16x64), the glowing parts
dim, mid, bright, mid, the silhouette identical in every frame, `{"animation": {"frametime": 10, "interpolate": true}}`:
a slow 2 s shimmer.

**Growth states.** A shape part may carry a `stage` (1-3): below that state its pixels are drawn as its `else`, from it
on as the part its `as` names (or as itself; `.` is the empty canvas for either). So one grid holds all four states, a
spec says `"growth": true`, and the generator writes the four textures. The check wants every state to pass the tier
rules (a problem in some states only names them), each state to differ from the one before, and exactly the
catalogue's growing artifacts to have states.

Each icon says what it is made of (the `what` line on the sheet): a flint chip knotted on string, a golden locket with
a heart on its lid, a lapis lens in a gold rim, a coil of copper wire round a redstone core, an iron buckle on a
riveted strap, a golden clock hand made into a pin, a tuff disc with a copper boss, a deepslate weight with an iron
ring, an amethyst tuning fork, three feathers bound with a gold bead, a corked vial with a spider eye, a glistering
melon wedge in a gold rind; the foci are a coal heart bound in gold (fire), a packed-ice prism (frost), a copper rod
with a spark (shock), a venom phial (venom), a bone urn (blight), an ender pearl in a gold claw (arcane), a lapis
sunburst brooch (radiant) and an iron-bound whetstone (physical). Four silhouettes serve an advanced artifact and the
greater one built on it, the second material on an optional part: `belt` (Ironhide, Blackforge), `signet` (Runebound,
Deepecho), `spool` (Snarewire, Slimebound), `torc` (Marrow, Ghast-Tear).

The revision's eleven (7.8 and 7.9):

| Artifact | Icon | What grows (empty to full) |
|---|---|---|
| Bloodglass Ampoule (`bloodglass_ampoule`) | a teardrop ampoule of red glass, empty inside with glints as vanilla's glass bottle is, stoppered in gold with a pale gem | blood fills it, a third at a time |
| Veinseeker Geode (`veinseeker_geode`) | a split geode (basalt crust, pale rind, a dark hollow) in a raw-iron cradle | amethyst crystals climb its walls until only a dark heart is left |
| Vigil Lantern (`vigil_lantern`) | a slim dark-iron soul lantern with a gold ring, cap and foot | its soul-blue flame grows from a spark to a flame with a white core whose light fills the glass |
| Skyroad Pennant (`skyroad_pennant`) | a sky-blue silk pennant with a light stripe on a spruce staff, a gold finial with a gem | it lengthens from a short flag to a long streamer |
| Graveward Bell (`graveward_bell`) | a small iron handbell on a spruce handle, a gold collar, a pale gem clapper | gold notches cut round its waist, one per step |
| Rival's Cord (`rivals_cord`) | a braided red-and-white loop closed by a gold clasp with a gem, a flint chip hanging below | knots tied in it: left, right, top |
| Scarbark Amulet (`scarbark_amulet`) | a disc of dark oak bark on a leather cord, a fire gem in a dark-iron ring | the disc thickens ring by ring (each old edge stays as a growth ring) and takes pale scars |
| Hearthroot Seed (`hearthroot_seed`) | a glowing seed (radiant) in a domed copper cage on moss | a sprout grows out through the cage, two leaves, then a leafy shoot |
| Observer Lens (`observer_lens`, engineered) | an observer's stone face, one eye a starless pearl (arcane) in a diamond rim, iron corners, a redstone dot | |
| Piston Bracer (`piston_bracer`, engineered) | a piston (planked oak head, iron rod, stone body) strapped on with leather, a gem buckle, two diamond studs, a redstone dot | |
| Daylight Coil (`daylight_coil`, engineered) | a daylight detector's panel of glass cells round a sun-gem (radiant), gold wire wound round it, a diamond cap, a redstone lead | |

The three engineered ones share a language: a vanilla redstone part's own face, a diamond as their precious material
(their bench builds use one) and a redstone dot.

The boss items, as `BOSS_HEROES.md` asks:

| Item | Icon |
|---|---|
| Hollow Star (`hollow_star`, signature) | a nether star burnt black (vanilla's four-point star, charcoal), a soul-blue light caught in its heart, pinned on a bar of bone; the light pulses. The boss revision (`669357d`) lets it grow (withering), and `ARTIFACTS.md` 1.2 and `BOSS_HEROES.md` 4.13 keep its pulse with no fill states: its growth shows in the tooltip, so its icon is unchanged and has no `growth` |
| Spirecore (`spirecore`, signature) | an end crystal caged in obsidian and iron bars, hung on a chain; the crystal pulses |
| Wither Hero Token (`wither_token`) | a charcoal disc with three small bone skulls, their eye sockets soul-blue; the sockets pulse (3.4) |
| Ender Dragon Hero Token (`ender_dragon_token`) | a black egg-shaped crystal flecked with purple; the flecks pulse (3.4) |

**The hollow copy.** 3.4 gives a hollow token a grey name and no effect; it asks for no look of its own, and 15.1's
faded signature copy is the same. So there is no hollow texture: the item side can tell a hollow token or a faded
copy apart by dropping its glint (`enchantment_glint_override: false` on that stack). Should the owner want a grey
look, it is a `variant_of` spec (below) and a model override, with no code in the generator. (The boss heroes
revision in progress keeps both token ids.)

### Adding an icon later (data only)

The check reads `ARTIFACTS.md`'s catalogue section by its headings' names, not their numbers: Components, Advanced,
Greater, Rare finds and Signature, plus Growing (advanced, with growth states) and Engineered (greater). It accepts a
description after a row's "Name (`id`)". A new row under one of those headings is an error ("no icon: add a spec ...")
until its icon exists, so the design's next artifacts cannot ship without art; a row under a heading that names none
of them (a future "Tinkered forms", say) without an icon is one NOTE listing them all, and with an icon is checked by
id and name only, so a new kind of artifact can land before its art and needs no code either. To add an icon:

1. If no shape fits, add a silhouette to `herosrc/artifacts/shapes.json`: a 16-row grid of 16 letters, a part per
   letter (`role`: body, setting, precious, gem, glow, fitting, cord, accent or detail; `profile`: dome, bevel, flat
   or rod; `outline`: true, false or "shadow"). An `optional` part with `else` lets one shape serve a base artifact and
   the richer one built on it. For a growing artifact, give the pixels that change a `stage` part (`stage`, `else`,
   `as`); drawing the four states as four grids and turning each pixel that changes once into a stage letter is the
   quickest way.
2. Add a spec to `herosrc/artifacts/icons.json` under the item id: `name` (the catalogue's), `tier`, `shape`
   (optionally `flip`), `parts` (a material per letter; add a material if needed), `gem` (an element) for a gem part,
   `glow` (an element or `#RRGGBB`) for a glow part, `accent`, `"growth": true` for a growing artifact, `what` (one
   line: what it is made of).
3. `herogen icons`, `herogen icons --check`, `herogen icons sheet`, and look at the sheet.

An upgraded artifact (a tinkerer's II or III, 9.8) keeps its icon. A second look of an existing item other than its
growth states is a spec whose id starts with the item's id and an underscore and names it in `variant_of`; its texture
is written beside the item's and the item side points a model override at it (a hollow token would be
`wither_token_hollow`); a variant may not take a growth state's name. The non-catalogue items (the sigil and the two
tokens) are `icons.EXTRA_ITEMS`, the one list in code: a renamed token would change it.

### Previews (`docs/heroes-v2/previews/artifacts/`)

- `sheet.png`: the 64 items grouped by tier with the tier's name colour (yellow, aqua, light purple, gold; the sigil
  white, the tokens light purple), the growing and the engineered artifacts in groups of their own; each at 6x with
  its name in its tier's colour and its `what`, and at 1x and 2x in a vanilla-style slot (drawn, not copied); the
  pulsing ones show their four frames at 2x, the growing ones their four states at 2x (and full at 6x).
- `inventory.png`: a mock inventory at GUI scales 1, 2 and 3, six rows of nine slots placing our icons beside vanilla
  items: flint, gold nugget, lapis lazuli, copper ingot, ender pearl, coal, blaze rod, echo shard, iron ingot, feather,
  spider eye, glistering melon, nether star, end crystal, the netherite upgrade template, a glass bottle, an amethyst
  shard, a soul lantern, a phantom membrane, a bell and a lead (the growing ones shown full, the ampoule also empty).
  The vanilla textures are read at render time, read-only, from the 1.21.1 client jar in the Gradle cache
  (`~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar`, or NeoForge's client resources jar);
  nothing of vanilla is stored in the repository. Without the jar the inventory is skipped with a NOTE and `--check`
  checks the sheet only.

Looked at, at 1x, 2x and 3x beside the vanilla items and at 6-8x one by one. Changed after looking (second run):

1. **Knapped Flint** read as a pill or a battery (a rounded capsule, domed). It is now a leaf-shaped blade with its
   ridge down the middle and bevelled faces, the string knotted round its top: beside vanilla's flint it reads as the
   same stone, knapped.
2. **Lifebound Chime**'s emerald eyes were one pixel each, too small for the greater tier's highlight, which the
   painter skips on a part under four pixels: the bell showed no highlight at all. The eyes are 2x2 and domed, like the
   totem's, and the left one carries the highlight.
3. The sheet's pulse frames ran past their column (the fourth frame was cut by the next icon); the pulsing icons now
   show their four frames at 2x in place of the still 2x slot.

Changed after looking (this run, the eleven new ones):

4. **Bloodglass Ampoule** first had a solid pink body: empty, it read as a pink potion. Its inside is now transparent
   with glints, as vanilla's glass bottle is, so empty reads as empty and the blood as filling it; the blood's surface
   lost its outline (it drew a black line across the liquid).
5. **Veinseeker Geode** read as a porthole or a ring: a round crust and a bright white rind. The rind is now a grey
   lilac, the crust thicker on the shadow side, and the crystals fill the walls bottom up with light facets, leaving a
   dark heart when full.
6. **Vigil Lantern**'s flame grew as a pale block. It is now a flame with a white core that, when full, lights the
   glass round it (a darker blue halo).
7. **Rival's Cord** was drawn as a one-pixel dotted ring, then as a thin diagonal: neither read as a cord. It is a
   two-pixel braided loop like vanilla's lead, the knots dark lumps tied into it, the flint chip an arrowhead.
8. **Daylight Coil**'s sun disappeared among cream cells and its wire read as dots: the cells are pale blue glass (the
   sun-gem stands out), the wire wound round as rungs.
9. **Piston Bracer**'s diamond studs were too small for the greater tier's highlight (three pixels in two places); they
   are two 2x2 studs. **Graveward Bell**'s iron was near white, which hid its pale clapper and gold notches: a darker
   bell iron. **Hearthroot Seed**'s copper bars were outlined to their darkest tone (one pixel wide, all edge): no
   outline now.

The rest read at 2x without a label: the gold of the locket, the chime and the clock hand against the copper of the
coil, the anklet and the loupe; the lapis lens beside lapis lazuli; the starless pearl's claw beside vanilla's ender
pearl; the hollow star as a burnt nether star; the dragon token as an egg; the empty ampoule beside the glass bottle;
the vigil lantern beside the soul lantern. At GUI scale 1 the slots are as small as vanilla's and the icons hold their
silhouettes; the growth states read as more of the same object, not as different items.

### Tests (`tests/test_icons.py`, 19; the suite 421 -> 440)

- The committed icons pass every check and match a fresh build; the build is the same twice in one process and in a
  process with another hash seed (sabotage: seeding the textures from Python's `hash()` fails it; a changed pixel in a
  committed texture fails the check).
- Every catalogue item has an icon under its id with the catalogue's reviewed name and tier (12 components, 28
  advanced with 8 growing, 13 greater with 3 engineered, 6 rare finds, 2 signatures, the sigil, the two tokens: 64),
  and exactly the growing ones have their three extra state textures.
- The textures follow the vanilla item rules: RGBA, 16x16 or 16x64 for the four pulsing ones, binary alpha, the same
  silhouette in every frame, an `.mcmeta` exactly for the pulsing ones; `check_image` catches a semi-transparent
  pixel, a 32x32 texture, an `.mcmeta` on a still icon, a missing one, a shifted frame and a fast frame time.
- At most eight colours a material in every frame and state (sabotage: sparkles that paint a new colour on every
  pixel).
- Tiers by richness in every state, the foci's gems, the greater and signature highlight (exactly one white pixel on
  the precious material); seven sabotaged specs (a component with a setting and a gem, a focus with another element's
  gem or none, a greater's precious part in its body material, a tier the catalogue does not say, a component
  re-tiered as advanced, a renamed item, a gem colour that is not an element) each give their error.
- Growth: the eight growing artifacts write `<id>.png` and `_1`.._3, each state differs from the one before, the
  committed state textures are those states; stage parts resolve by state (`else` below, `as` from it on, `.` either
  way, every stage for a spec without growth) and `check_shape` refuses a stage out of 1-3, an `else` or `as` naming a
  staged or unknown part, and an optional staged part. Six sabotaged sources each give their error: a growing artifact
  without states, states on a shape without stages, states on an artifact the catalogue does not list as growing, a
  state that looks like the one before, a gem missing in state 0 only, a variant named like a growth state.
- A missing icon is an error naming the fix; a `variant_of` spec builds and passes; a variant of an unknown item or a
  spec for an unknown id fails; an artifact without an icon under a heading that names no tier is a NOTE, under a tier
  heading (Growing included) an error.
- The catalogue reader by heading names (a renumbered catalogue, a heading with no tier, a description after the id,
  Growing as advanced and Engineered as greater with their kinds, the build tree's code block and the next section
  ignored).
- The painter's light on a bevelled square and a domed disc; `palette.ramp_n`; `herogen icons --check`.
- The sheet and the inventory match a fresh render; the inventory rows mix ours and vanilla's and no vanilla texture
  is in the repository.

Two of the new rules were sabotaged in the code as well (the growing artifact's state requirement turned off; the
stage parts always drawn full): the growth tests and the committed-icons test fail.

### Checks run

In a clean copy of `HEAD` with only these files (the tree also held the hero agents' unfinished work): the Python
suite (440 tests, OK: 421 before the icons and the 19 of `test_icons.py`); `herogen icons --check` (OK, 64 items, 88
textures); `herogen icons sheet --check`; `herogen check --all --style` (heroes as before, then `OK icons`);
`build --all --check` (test_rig, `_fallback`, Natsuho and Hinae byte for byte); the existing preview checks are
untouched (no hero, part or clip changed).

### Decisions

1. **The texture is named by the item id** and the spec's key in `icons.json` is that id: no mapping table to drift.
   Growth states add `_1`, `_2`, `_3`.
2. **Growth states are stage parts in one grid**, not four shapes: the parts that do not grow cannot drift between
   states, and a state that changes nothing is an error.
3. **No hollow token texture** (3.4 asks for a grey name only); `variant_of` makes one a data change if wanted.
4. **The glint is the item's**, not painted: vanilla draws it over any texture, and a painted one would double it.
5. **Missing icons are errors in `herogen check`** under the catalogue's known headings, a NOTE under a new one.
6. **60 shapes, not about 30**: the first run drew one silhouette per object where a shared one would have blurred
   what it is made of (a phial and a vial, a torc and a ring); four shapes still serve two icons each.
7. **The Artificer's Bench is a block** (`ARTIFACTS.md` 1.1, 9.7): its textures are block faces for R5, not an item
   icon, and are not drawn here.

### Open items

- **R2, R3 and B2**: register the items, their `models/item/<id>.json` (above), the growth property and the growing
  ones' overrides, the rarity colours and the glint; then a screenshot of the icons in a real inventory for the owner.
- **The owner's review** of `sheet.png` and `inventory.png` (R6's "the sheet is sent to the owner").
- **R5**: the Artificer's Bench's block textures.
- R6's other half (the core clips `artifact.attune`, `artifact.raise`, `artifact.cast` and the `artifact` sound set)
  is not part of this task.

---

## B5: the Wither's look (2026-11-18)

HEROES_V2.md 15.8 row B5, the Wither's half of the look gate (the Ender Dragon is the other half): `BOSS_HEROES.md` 4.1,
4.2, 4.6 (hover and flight), 4.12 (her own animations), 4.13 (Hollow Star's worn part) and the LOOK section of
`kits/the_wither.txt`. It comes before her kit exists as data: the look, the parts, the phase looks, the idle quirks,
the locomotion and the moments without hit ticks. No move clip is authored: their frames come from the B1 kit fixture
the Java side writes, and the move clips are a later step against it. She has one body for good (HEROES_V2 3.6, the
owner's rule of 2026-11-13): **an adult woman, female, slim (the boss's gaunt silhouette), drawn at 104% (1.89
blocks)**. Every preview shows her only in that body. Three interruptions (usage-limit pauses and a
container restart) stopped the earlier runs; between them they built the parts, the source and the clips, fixed what the
tests, the lint and the previews found (below), and added the rebirth look, the tests and these notes; the last run
re-ran every check on the finished tree (below) and committed. No hit tick, marker or timing
file of an existing clip changed: Natsuho, the test rig, `_fallback` and the libraries build byte for byte as before,
Hinae's `visual.json` changed only its `source_sha256` (her `hair/hime_long` file gained the `ash` parameter), and her
files are new. With no definition in `data/factionclash/heroes/` she stays dormant in the game until
B1/B7 add one.

### What exists

| Path | What |
|---|---|
| `herosrc/heroes/the_wither.hero.json` | the art-only source (`"kit": "pending"`, below): `mobility` (flyer III, hover II, carrier `levitation` on `robes/smoke_gown`), `art` (body, palette, face, 12 parts, the sceptre, phase looks, the rebirth look) and `animations` (style `focus`, blink `calm`, 28 poses, 21 clips) |
| `herosrc/parts/`: `headwear/bone_crown`, `accessories/skull_familiars`, `outfit_top/ribbed_corset`, `robes/smoke_gown`, `back/cape_torn`, `markings/spine_ridge`, `armour/bone_bracers`, `weapons/wither_rose_sceptre`, `accessories/hollow_star_brooch`, `accessories/wither_rose`, `markings/soul_fire_tips` | the eleven new parts (4.2's nine, the rose of her idle quirk and the phase-3 soul fire), built with the library's generators and conventions so G8 can reuse them |
| `herosrc/parts/hair/hime_long.json` | `ash` 0..1 (default 0): the side locks, the back layer and the tail tipped in the alt slot (`hair_accent`), cut `tip_jagged_1`; `ash` 0 builds the cut exactly as before (the three groups are copies with `when`) |
| generated | `geo/hero`, `textures/entity/hero/the_wither.png`, `_glowmask`, `_p2`, `_p2_glowmask`, `_rebirth`, `_rebirth_glowmask`, `heroes/the_wither.visual.json` (no moves), `animations/hero/the_wither.animation.json`, `data/factionclash/hero_timings/the_wither.json` |
| `build.py` | the art-only mode (`art_only_problems`, `KIT_PENDING`, `locomotion_names`) and the rebirth look (`rebirth_look`, `out_paths` `rebirth`, `rebirth_glowmask`, `visual.json` `texture.rebirth`) |
| `face.py` | `iris_glow` (the irises, pupils and highlights of every open frame in the glow mask) and the `sockets` face paint |
| `parts.py`, `rig.py`, `visual.py` | a part's `artifact` (a signature's worn part, `visual.json` `bones.artifacts`), and a mirrored cube on a nested bone mirrors its whole bone chain |
| `anim/library.py` | `copy` (an alias) in a hero's own file: her `move.hover` is her `move.idle`, her `move.drift` her `move.walk` |
| `assets.py` | `check_visual`: a `texture.rebirth` flag needs both rebirth files |
| `lint.py` | a part worn over a hairstyle is linted on the mannequin wearing that hair (below) |
| `render/scene.py`, `render/hero_previews.py` | the rebirth texture (`look`), a flyer's and hover hero's locomotion set, the rebirth moment, her own captions |
| `tests/test_wither.py` | 20 tests (below) |

### The art-only source (`"kit": "pending"`)

`herogen check` compares `visual.json`'s moves with the definition, or with the slice fixture when there is none (G7),
and her kit exists in neither yet. The smallest honest answer: a source with `"kit": "pending"` holds **no kit and fakes
none**. `build.art_only_problems` refuses (and `build` fails on) a pending source with moves, states, an ultimate or a
basic attack, and an own clip that is a kit clip (`move.<id>` that is not a locomotion name, `ultimate*`, `state.*`)
or carries a `hit` or `cast` marker. `locomotion_names` is the core's `move.*` clips plus the six G10b adds
(MOBILITY_AND_WORK 4.1: `move.hover`, `move.drift`, ...), which a hero may override. Her `visual.json` lists no moves;
`assets.definition_of` finds no definition, so the move-list and frame rules have nothing to compare, and every other
rule of 14.2 runs as for any hero. Every other hero builds exactly as before (the mode is only read when `kit` is
`pending`). The move step removes `"kit": "pending"`, copies the fixture's kit fields in and authors the move clips.

### Her look

The vanilla Wither made into a woman, read at once: charcoal and bone, three skulls, a soul-blue glow.

- **Hair**: `hair/hime_long` at its longest (length 26, locks 11) in charcoal `#2A282D`, with `ash` 1: the side locks,
  the back layer and the tail end in ash grey (`hair_accent` `#8E8A93`), cut jagged so they fray like smoke. The tie in
  `metal`.
- **Head**: `headwear/bone_crown`, a low circlet of seven short bone slabs over the front half of the head (the hair
  hides the back half) with three prongs at the front, the middle one tallest, bone-white points, and a soul-blue gem at
  the foot of the middle prong (glow).
- **Her side heads**: `accessories/skull_familiars` (below).
- **Body**: `outfit_top/ribbed_corset`, a high-collared charcoal bodice with long fitted sleeves, a busk over the chest
  and on it a sternum and five pairs of ribs in bone: from the front the stays read as a ribcage, and they stay drawn
  beyond the fine distance (the ribcage is how she reads from afar). `robes/smoke_gown`, a floor-length gown of hinged
  panels on the skirt bones in two depths, each fading to ash at a jagged hem, with smoke wisps hanging from it; her
  flight carrier. `back/cape_torn`, a charcoal cape split down the middle, its inside and the split's edges lined in
  soul blue, the hems torn, a few tattered strips. `markings/spine_ridge`, seven bone vertebrae down her back between
  the cape's halves. `armour/bone_bracers`, bone forearm shells with ridged rims and three short spines each.
  `legwear/tights_boots` in leather, hidden by the gown. Modest throughout: a high collar, long sleeves, a floor-length
  gown (ROSTER_PLAN's rule).
- **Weapon**: `weapons/wither_rose_sceptre` in her right hand: a slender black shaft with a bone ferrule and collar, two
  thorns, and a wither rose for a head (five black petals round a glowing soul-blue heart, two dark leaves); at her
  right hip, head down and swung back, when sheathed (`hip_same`).
- **Palette** (4.2's): skin `#E3DFDA`, skin_shade `#ABA59F`, eyes `#9FE8FF`, primary `#232127`, secondary `#E6E0D2`,
  accent `#3CC6E8`, trim `#5E5A63`, metal `#34313A` (the gown), leather `#29262B`, glow `#7FE2FF`.
- **Face**: `sharp` eyes, `glossy` irises with `pupil: none` and the new `iris_glow` (the soul-blue irises glow in every
  open frame), lashes 2, `thin_arched` brows, the `soft` mouth, no blush; the new `sockets` face paint, a charcoal
  shadow under each eye fading at both ends with a fainter row under the outer half (a skull's sockets echoed, never a
  bruise). Blink `calm`. Overrides: `happy` is a thin half-lidded smile (eyes `half`, mouth `smile`), `angry` the
  glowing frame, `flustered` without a blush.
- **Budget**: 286 cubes (127 beyond the fine distance, the cap 130), 88 bones, a 256x128 texture. 4.2 asked for about
  240 cubes on 256x256: the hair took 92 (the ash tips are extra strands) and the sceptre 26, and everything still fits
  4.3's limits; the smaller sheet is enough for all of it.

| Part | Cubes | Notes |
|---|---|---|
| `hair/hime_long` (ash 1) | 92 | 10 beyond the fine distance |
| `robes/smoke_gown` | 27 | panels on `skirt_*`, the hem band glows in the phase look |
| `accessories/skull_familiars` | 26 | two skulls, their jaws, the smoke |
| `weapons/wither_rose_sceptre` | 26 | in the hand and the hip copy |
| `outfit_top/ribbed_corset` | 14 | all beyond the fine distance (the ribcage) |
| `markings/spine_ridge` | 14 | seven vertebrae with their processes |
| `markings/soul_fire_tips` | 13 | phase 3 only, on `p3_` bones |
| `headwear/bone_crown` | 12 | the gem glows |
| `armour/bone_bracers` | 12 | |
| `back/cape_torn` | 10 | the cape chain |
| `accessories/wither_rose` | 7 | inside her left hand at rest |
| `accessories/hollow_star_brooch` | 6 | on `artifact_hollow_star` |

### The skull familiars

`accessories/skull_familiars` declares its own bones `familiar_r` and `familiar_l` (parent `chest`, pivots at the
skulls' centres, 12 px either side of the head's centre at eye height) and a jaw bone each (`familiar_jaw_r`/`_l`,
hinged at the back, so a clip can snap the jaws). Each skull is 6 x 6 x 6 px: a charcoal cranium, a bone brow, dark
sockets with a glowing core (glow), a nasal hole, bone teeth top and bottom, and three smoke wisps hanging back and
down from under it (ash fading to `hair_accent`, cut jagged). **The bob**: `HeroAnimator` plays `sec.loop` on the
secondary controller whenever the chain has one, so the design's `sec.familiars` is her `sec.loop` (4 s): the two
skulls bob out of step and drift a little, keying **only their position**. No other clip of hers keys their position,
so every clip may turn them (their rotation) to aim with her: the idle looks about, the bicker turns them to each
other, the intro and phase 2 tip them up, and the move clips will aim them (4.12: "every move clip keys their
rotation"). Phase 3 hides both bones (they are adds from then on, 4.3).

### Phase looks and the rebirth

- **Phase 2 (the wither armour)**: the phase palette turns `secondary` to `#D6F2FF` (the bone goes a cold blue-white)
  and `trim` to soul blue; the rib stays, the spine, the crown's gem and the gown's hem band are `glow: phase` and light
  up in `_p2.png` and its glow mask. The faint blue shimmer of `soul` particles on her outline is the engine's (a
  particle, not a texture: open below).
- **Phase 3**: `hide` the two familiars, `show` the soul fire, `eyes_glow`. `markings/soul_fire_tips` (worn with
  `"phase": 3`, `requires` `hair/hime_long`, its `length` and `locks` matching the hair's) puts tongues of glowing soul
  fire at the hair's tips: a ring round the tail's end just outside its flared strands and three on each side lock, on
  `p3_` children of the hair's bones, so they follow every key and spring of the hair.
- **The rebirth** (new, `art.rebirth` `{palette}`): `build.rebirth_look` paints her phase-2 look again with the slots the
  rebirth names (hers: `metal` `#CDEBFA`, the gown, and `accent` `#F1FBFF`, the cape's lining, the gem, the skulls'
  eyes, the rose's heart) and writes every texel that changed to the glow mask too, as `the_wither_rebirth.png` and
  `the_wither_rebirth_glowmask.png`, with `visual.json` `texture.rebirth: true`: the gown and the lining blaze the
  blue-white of vanilla's spawn charge. `HeroModel` must show those two textures while the rebirth plays (the engine's
  hook, open below; until then the files are simply unused). A palette naming an unknown slot, an empty one, or one
  that recolours nothing is an error. A hero without `art.rebirth` gets no file and no key (every other hero's
  `visual.json` is unchanged). The previews draw the rebirth clip and a cell of `phases.png` with it.

**Why a texture and not shells or a phase**: the phase texture shows from phase 2 for good (`HeroModel` reads
`visual.json`'s flag, 4.7), and the rebirth is a 2-second moment between phases 2 and 3; glowing shells (Hinae's
`phase_glow`) would double the gown's 27 cubes and still need a hook to show them. A second texture costs no cube and
the hook is one line beside `getTextureResource`'s phase rule.

### Hollow Star's worn part

A part with `"artifact": "<id>"` is a signature artifact's `worn_part` (ARTIFACTS 12.7 and 16.4): all its cubes must sit
on the bone it declares, `artifact_<id>` (or below it), and `visual.json` `bones.artifacts` maps the artifact id to
those bones (`{"hollow_star": ["artifact_hollow_star"]}`). `accessories/hollow_star_brooch` is a nether star burnt
black at the front of her collar: eight points (a cross and a smaller saltire behind it) round a dark gem heart with a
faint soul-blue light. **The engine's hook** (R3): `HeroModel` hides the bones of `bones.artifacts` unless the hero
wears that artifact. Until R3 they are drawn always, which is right for her (a boss hero comes with her signature in
her first slot), and the previews draw it.

### The floating rule

Every pose of hers that floats carries a root position of 4 px (a hand's breadth at her scale): her pointed toes hang
about 2 px above the ground in the idle, and the gown's hem 2-3 px. `test_she_floats` measures the lowest sole of every
own clip on every tick (actions over the clip they play on) on her body: at least 2 px everywhere, the rebirth's
sink into the soul sand the one exception. Found on the way: the half-turn (`personal.3`) keys the root to turn her, and its fold poses
carried no lift, so for its 5 s she dropped to the ground with her pointed toes 1.4 px through it (the fold poses now
carry the 4 px); the take-off's gather sank to 3 px (toes at 1.5 px; now 3.5); `move.fly_hover`'s swinging knee
reached 1.6 px on the bob (now a pixel higher, 5 px: she is in the air there); and the rebirth's kneel left her
kneeling 6.7 px over the ground with her folded thighs pushed out through the front of the gown (the gown hangs from
the hips on the skirt bones and does not fold with a kneel), so the rebirth is now a sink: the root goes 14 px down
and she sinks into the soul sand to her thighs, the hips level and the legs hanging straight inside the gown, curled
over from the waist up (a first try pitched the hips, which swung the gown's hem forward off the legs), the gown's hem
going into the sand with her.

### Her clips (no hit ticks)

| Clip | Length | What happens |
|---|---|---|
| `move.idle` (= `move.hover`) | 6.0 s | floating a hand's breadth up: a 0.6 px bob every 3 s, breathing, the gown's panels billowing in a quarter-cycle wave, the legs drifting inside it, two glances, the familiars looking about |
| `combat.idle` | 3.0 s | the focus style's casting stance above the waist (so every focus action settles on it), floating, a quicker bob |
| `move.walk` (= `move.drift`), `move.run` | 1.6, 1.2 s | she never walks: a lean into the drift with the gown trailing and a slow bank; the fast glide with the arms swept back |
| `move.takeoff`, `move.fly`, `move.fly_fast`, `move.fly_hover`, `move.glide`, `move.fly_land` | 0.6 s, loops 2.0 / 1.0 / 1.6 / 2.4 s, 0.4 s | G10's flight set adapted to a floating gown (the gown is the carrier: no wings, no running legs): the gather (the tell: arms open down and out, the hem lifting) and the rise into the flight lean; the flight, nearly level when fast, everything streaming; upright in the air with the knees under the gown; the arms spread in the glide; the flare and settle back to her hover height |
| `sec.loop` | 4.0 s | the familiars' bob (above) |
| `personal.1` | 3.2 s | the familiars bicker: they turn to face each other across her and snap their jaws in turn while she glances from one to the other, then lifts her chin and shuts her eyes; they jerk back to the front and dip, chastened |
| `personal.2` | 3.6 s | she turns a wither rose: the left hand comes up palm up and the rose rises out of her fingers (`accessories/wither_rose`'s bone `rose_l` slides out of the hand), turned slowly once round with a thin half-lidded smile, then put away |
| `personal.3` | 5.0 s | a slow half-turn with her arms folded: round on her floating spot (the root turned 150 degrees), her back (the torn cape and the spine) to the front for a moment with a cold glance over her shoulder, then back |
| `intro` | 2.2 s | chin up, a smug half-smile, the left hand opened toward the challenger while the familiars cackle, then the sceptre levelled |
| `phase.2` | 1.2 s | inside the transition: arched back, the arms flung down and out, eyes blazing, the skulls tipped up with their jaws wide |
| `rebirth` | 2.4 s | no action code yet: she sinks into the soul sand to her thighs and curls over, arms round herself, then rises higher than her hover, arms flung up and wide, blazing (drawn with the rebirth look), and floats down into her stance |
| `phase.3` | 1.4 s | she grabs the skulls at the sides of her head and flings them away, eyes blazing |
| `last_stand` | 1.8 s | no action code yet: the sceptre thrust up overhead, the left arm flung down and out, then set in her stance, determined. She fights to the death: no `escape` clip |

`face_ctrl` is keyed in the quirks and the moments. The sounds of 4.11 (her `quirks.*` and the moment slots) are not
markers yet: her sound set (B6) does not exist, and a marker must name a sound of her catalogue; the move step adds
them with the set. Every own clip passes the quality rules on all six bases, and the sceptre stays clear of her body on
every second tick (`grips.animation_problems`).

### Generator additions and fixes (shared code; every other hero builds byte for byte as before)

1. **Art-only sources** (above).
2. **The rebirth look** (above).
3. **`face.iris_glow`** and the **`sockets`** face paint (above).
4. **A part's `artifact`** (above).
5. **Mirrored nested phase bones**: a mirrored cube on a nested bone (`fine_p3_hair_r`, the soul fire on the right lock)
   now mirrors its whole chain (`p3_hair_l` under `hair_l`, then `fine_p3_hair_l` under it); before, the left cube hung
   from the right-hand bone.
6. **`copy` in a hero's own file** (`anim/library.py` resolves the hero's aliases as it does a library's).
7. **`hair/hime_long` `ash`** (above).
8. **`lint` wears a required hairstyle**: a part with `requires` naming a hair (the soul fire over `hair/hime_long`) is
   built on the mannequin wearing that hair in place of its short crop; before, the two hairs clashed on `hair_top` and
   the part could not be linted. Parts that require a tail (Natsuho's puffed fur) are built as before.
9. **`armour/bone_bracers`' spines on a short forearm**: on a chibi the three spines stood 0.25 px apart and z-fought
   (`lint`: 70 px of coplanar faces on chibi_m); their span now shrinks to a fifth of the bracer at each end, so they
   stand 0.9 px apart there (a spine is 0.7 wide), and the adult's are where they were within 0.2 px.
10. **Previews**: `loco_segments` gives a flyer or hover hero her drift, hover and flight set (no jumps or ladder);
    `phase_of_clip` draws a rebirth in the phase before the last; the moments add the rebirth; `Shot.look`,
    `HeroAssets.textures(phase, look)` and `look_of_clip` draw it with the rebirth texture; `phases.png` adds a rebirth
    cell for a hero with one. Her own captions are in `OWN_CAPTIONS`.

### Previews (`docs/heroes-v2/previews/heroes/the_wither/` and `heroes/lineup.png`)

Laid out like Natsuho's and Hinae's, with a README: `turnaround.png`, `faces.png`, `phases.png` (phases 1-3 front and
back, the rebirth, phase 3 at night), `sheet.png`, `string.gif` (the focus string with her sceptre; her thrown-skull
basic attack comes with the kit), `moments.gif`, `idles.gif` and `locomotion.gif`. No move GIFs and no `ultimate.gif`
(no kit yet). The lineup now has her beside Natsuho, Hinae and the test rig, at her own scale (the tallest: 1.89
blocks). The parts sheets (`previews/parts/`) show the new parts on the six bases: `head_headwear.png` (the crown),
`body_tops_robes.png` (the corset, the gown), `body_armour_back.png` (the bracers, the torn cape),
`details_accessories.png` (the familiars, the rose, the brooch, the spine, the soul fire over the hime cut) and
`weapons.png` (the sceptre). Looked at: the turnaround and head close-ups at 8x beside Hinae's (the same hair from
above), the faces, the phases with the rebirth, the three quirks as 8-frame strips at 4-7x, the locomotion, moments
and idle films frame by frame.

### Tests (440 -> 460: `tests/test_wither.py`, 20)

Her one body (adult, slim, 104, no phase changes it); the budget; the art-only source (no moves, no kit clip, no hit or
cast; a pending source with a move, a `hit` marker or an `ultimate` is refused); her clips pass every rule on every
base, at most 24, no `escape`; the sceptre clear of her body; she floats (every own clip's lowest sole at least 2 px
up, the rebirth's sink going into the ground; sabotage: the idle without its lift stands on the ground); `move.hover`
and `move.drift` are aliases; `sec.loop` keys only the familiars' position and no other clip does, and the bicker turns
them to each other; the rose lies inside her hand at rest and comes out in `personal.2`; the familiars (6x6x6, their
bones, 12 px out, glowing eyes, hidden in phase 3); the glow mask (gem, eyes, sceptre's heart; the stays and the spine
only in the phase texture); the ash tips keep the hime cut (`ash` 0 unchanged); the soul fire on `p3_` bones outside
the tail's ends, its left copy on the left lock; the brooch on its artifact bone (a cube elsewhere refused); the face
(pupil-less glowing irises, the overrides, never a blush); the rebirth look (the gown recoloured and glowing, the
bodice unchanged, blue-white; sabotage: without `art.rebirth` no file and no flag, a missing file reported, bad
palettes refused); the bracers' spines a spine's width apart on every base (sabotage: the old span gives 0.25 px on a
chibi); `lint` wears a required hairstyle and still wears a required tail over its own hair; the preview rules (the
rebirth in phase 2 with its look, the last stand in 3, the flight set, the moments, the lineup).
`test_details`' slice-column test pinned the library's markings to the slice's three; it now lets B5's two through
by name, so any other new marking still fails it.

**At the commit** (the finished tree): the full suite (460 tests, OK), `check --all --style` (every hero, the style
fixtures and the icons), `build --all --check` (the libraries, `_fallback`, the test rig, Natsuho and Hinae as
committed, her files fresh), `lint` (268 builds and checks, 0 problems), `render-previews --check`, `parts-previews
--check`, `anim-previews --locomotion --check`, `hero-previews --check` for her, Natsuho and Hinae, and `hero-previews
--lineup --check`; the style preview set (`previews`) re-rendered byte for byte as committed.

### Decisions

1. **Art only, no fake kit**: `"kit": "pending"` and an empty move list, refused the moment anything kit-like appears;
   no check of another hero was weakened.
2. **`sec.loop` is `sec.familiars`**: the controller the engine already runs, keying only the skulls' position.
3. **The rebirth look is a texture**, not cubes and not a phase (above).
4. **The brooch is drawn until R3's hook exists**: correct for her, since she comes with Hollow Star.
5. **256x128** for her texture: everything fits, half the memory of 4.2's 256x256.
6. **The rebirth sinks rather than kneels** (4.12 says "kneeling into soul sand"): a floating gown on the skirt bones
   cannot fold round a kneel without the thighs pushing through it, and in game the sand hides her to the thighs either
   way, so the sink reads as the kneel without the clipping.
7. **The gown is metal-slot**: so the rebirth can blaze it without recolouring the bodice and cape (primary); the hair's
   tie (also `metal`) and the brooch go blue-white with it, a small flourish of the same charge.

### Open items

- **The move step** (against the B1 kit fixture, `BOSS_HEROES.md` 4.3 and 4.12): remove `"kit": "pending"` and copy the
  fixture's kit fields into the source; author `move.skull_volley`, `move.ruin_skull`, `move.withering_pulse`,
  `move.rose_bloom`, `move.soul_dash`, `move.grave_grasp`, `move.unbound_heads`, `move.stray_skulls`, `ultimate`
  (Birth Blast) with its `ultimate.loop` for the 3 s channel (the loop the engine plays while the channel lasts; check
  `MoveTiming.stops` and the `ultimate.end` rule as Hinae's section explains), `ultimate.awakened` (Skullfall),
  `state.wither_armour`, `victory` (she turns her back, the familiars cackle), `taunt` (a slow beckoning finger) and the
  `work.mine` override (her palm to the stone), each with the kit's frames to the tick (`hit` at the release, `cast` on
  the active part's last tick), keying the familiars' rotation so they aim with her (never their position, which is
  `sec.loop`'s); `attack.ranged` for her thrown skull if the focus library's does not read as one; `string.gif` then
  shows it. The sound markers of 4.11 once B6 writes her sound set (`quirks.*` in `personal.1-3`, the intro, the
  phases, the rebirth slot). Her kit sheet's ANIMATIONS section lists the same.
- **Engine hooks** (B1/B3/R3): an action code for `rebirth` (kit-gaps) and for `last_stand`; `HeroModel` showing
  `_rebirth.png` and `_rebirth_glowmask.png` while the rebirth plays (`visual.json` `texture.rebirth`); hiding
  `bones.artifacts` unless the artifact is worn (R3); the phase-2 `soul` particle shimmer on her outline and the smoke
  and soul sparks from her hem (`vfx_feet`, 4.2); the hover's lift is in the clips (4 px), so the hitbox and the
  hover height of MOBILITY 1.11 (0.6-1.0 blocks) are the engine's; her definition in `data/factionclash/heroes/` (B7).
- **Gate 3/4**: in-game screenshots (lineup, a phase transition, the rebirth, a face close-up) once she has a
  definition; ROSTER_PLAN 8's concept search on the look (the vanilla boss is the intended reference; nothing here
  comes from another creator's design).
- Not run here (Gradle is off limits in this task): the Java suites (`HeroAssetsTest`, `BuiltinHeroesTest`,
  `verifyHeroAssets`; they will see her files, which pass the Python mirror of their rules), GameTests, the in-game
  and campaign checks, and the jar.

## B5: the Ender Dragon's look (2026-11-19)

HEROES_V2.md 15.8 row B5, the Ender Dragon's half of the look gate (the Wither's is above): `BOSS_HEROES.md` 5.1, 5.2,
5.6 (her flight, the circle and the perch), 5.12 (her own animations), 5.13 (Spirecore's worn part) and the LOOK
section of `kits/the_ender_dragon.txt`. Like the Wither's, it comes before her kit exists as data: the look, the parts,
the phase looks, the idle quirks, the locomotion and the moments without hit ticks; no move clip is authored (their
frames come from the B1 kit fixture). She has one body for good (HEROES_V2 3.6): **an adult woman, female, standard
build, drawn at 108% (1.97 blocks, under the 1.98 cap)**, and every preview shows her only in that body. The vanilla
Ender Dragon is the reference for what she must read as (black scales, purple eyes and membranes, end-stone spines);
every shape here is the library's own (generators, rig v3, style pass 2), and nothing is taken from another creator's
design. No hit tick, marker or timing file of an existing clip changed: Natsuho, Hinae, the Wither, the test rig,
`_fallback` and the libraries build byte for byte as before, and her files are new. With no definition in
`data/factionclash/heroes/` she stays dormant in the game until B1/B7 add one.

### What exists

| Path | What |
|---|---|
| `herosrc/heroes/the_ender_dragon.hero.json` | the art-only source (`"kit": "pending"`, as the Wither's): `mobility` (flyer III, carrier `wings` on `wings/dragon`), `art` (body, palette, face, 12 parts, the claws, phase looks) and `animations` (style `fists`, blink `calm`, the `dragon_wings` variant, 15 poses, 10 own clips) |
| `herosrc/anim/variants/dragon_wings.json` | flight on worn dragon wings (G10's variant mechanism): 13 poses and 8 clips, the core flight set opened through the part's bones, plus `move.circle` |
| `herosrc/parts/`: `wings/dragon`, `horns/swept_dragon`, `tails/scaled`, `markings/scales`, `markings/dorsal_spines`, `outfit_top/scale_bodice`, `outfit_bottom/long_skirt`, `armour/spined_pauldrons`, `weapons/dragon_claws`, `accessories/spirecore_pendant` | the ten new parts of 5.2, built with the library's generators and conventions so G8 can reuse them (5.2 names them as G8's planned dragon wings, swept horns, scaled tail, long skirt and scales) |
| `herosrc/parts/hair/long_layered.json` | `under` 0..1 (default 0): the inner ring drawn as a long under-layer in the alt slot (`hair_accent`); `under` 0 builds the style exactly as before |
| generated | `geo/hero`, `textures/entity/hero/the_ender_dragon.png`, `_glowmask`, `_p2`, `_p2_glowmask`, `heroes/the_ender_dragon.visual.json` (no moves), `animations/hero/the_ender_dragon.animation.json`, `data/factionclash/hero_timings/the_ender_dragon.json` |
| `gen.py` | a chain's `crest`: a spine on the up side of each segment (below) |
| `paint.py` | the stripes pattern's `gap` (thin lines: the membranes' veins) |
| `face.py` | the `scales` face paint and `throat_glow` (below) |
| `build.py` | `LOCOMOTION_BOSS`: `move.circle` is a locomotion name (5.6, kit-gaps "Boss heroes" 10) |
| `render/hero_previews.py` | the wing loops laid over every preview of a winged hero, a winged flyer's locomotion set, `wings.png`, her captions |
| `tests/test_ender_dragon.py` | 19 tests (below) |

### Her look

The vanilla Ender Dragon made into a woman, read at once: black scales, purple eyes and membranes, end-stone spines.

- **Hair**: `hair/long_layered` (length 17, locks 11) in near-black `#1B1720` with the new `under` 1: the inner ring
  of strands falls as a long purple under-layer (`hair_accent` `#7A3FB8`) that shows at the sides and under the outer
  layer when she turns, swept back from the horns.
- **Horns**: `horns/swept_dragon`, two horns from the temples, each a run of five tapering segments along a curve (from
  above the side lock, back along the head and up past the crown), black (`primary`) with the last two segments purple
  (`accent`); with `glow` 1 the tips are `glow: phase` (lit from phase 2). Head-local, so they fit every form.
- **Face**: `cat` eyes, `glossy` irises with a `slit` pupil and `iris_glow` (the purple irises glow in every open
  frame), lashes 3, `bold` brows, the `soft` mouth with `fang: both`, no blush; the new `scales` face paint on both
  cheekbones (two rows of small dark scales from the edge of the face, deep and faint by turns) and the new
  `throat_glow` (the inside of every open mouth frame is drawn in the glow colour and lit: her roar and her angry face
  show a purple throat; a closed mouth shows nothing). Blink `calm`. Overrides: `happy` a closed-eye smile (eyes
  `happy`, mouth `smile`, no cheeks), `angry` narrowed slits with an open mouth (`talk_b`), `flustered` without a
  blush (no mood of hers blushes).
- **Body**: `outfit_top/scale_bodice`, a high-collared black bodice with long sleeves, a scales pattern in the dark
  purple `secondary`, a collar plate and placket in `accent`, a girdle over the hips;
  `outfit_bottom/long_skirt`, a floor-length skirt of fourteen panels on the skirt bones, lined in `secondary`, a
  two-texel `accent` hem, the front panel cut to the knee (the split) with three purple under-panels behind it on
  `skirt_f`; `legwear/boots` in leather. `armour/spined_pauldrons`: dark plates on the shoulders, each with three pale
  end-stone spines (`trim`). `markings/scales`: scale paint on the head's sides, forearms and shins (paint only, no
  cubes). Modest throughout: a high collar, long sleeves, a floor-length skirt (ROSTER_PLAN's rule).
- **Wings**: `wings/dragon` (below).
- **Tail**: `tails/scaled`, the rig's five-bone tail chain from the small of the back to the ground and trailing behind,
  4 x 3.6 px at the root and tapering, a scales pattern in `metal` on every segment, and a short tip chain of two bones
  on `tail_5` (`tail_tip_1`, `tail_tip_2`, declared for the procedural lag) ending in a point; a crest of pale
  end-stone spines on top (the new `crest`), one per segment but the tip's point. `s` scales it with the legs.
- **Dorsal spines**: `markings/dorsal_spines`, a row of seven end-stone spines from the nape down the back to the tail
  (a peaked cutout each, on fine_ bones); a second copy with `shell` 1 worn in phase 3 (glowing shells 0.12 px
  larger, on `p3_` bones): the spines ignite.
- **Weapon**: `weapons/dragon_claws`, a pair worn on her hands (`style` fists, `sheathed: worn`: they are her hands,
  never sheathed): a black shell over the back of the hand, an obsidian plate, a purple glowing seam (the inner glow),
  three hooked obsidian talons and a thumb claw.
- **Spirecore**: `accessories/spirecore_pendant` (below).
- **Palette** (5.2's colours): skin `#E9DDE6`, skin_shade `#B9A3B8`, hair `#1B1720`, hair_accent `#7A3FB8`, eyes
  `#D08BFF`, primary `#1C1A22`, secondary `#3B2A4F`, accent `#C77DFF`, trim `#E3E2B4` (end stone), metal `#2A2433`,
  leather `#2E2632`, glow `#E3A8FF`. `herogen check` NOTEs that primary and secondary differ by 9% in greyscale: by
  design (black and a very dark purple; the purple reads by hue and the accents carry the contrast).
- **Budget**: 234 cubes (115 beyond the fine distance, the cap 130), 98 bones, a 256x256 texture (5.2's sheet: the
  wings and the tail need the room). 5.2 asked for about 270 cubes.

| Part | Cubes | Beyond the fine distance |
|---|---|---|
| `hair/long_layered` (under 1) | 84 | 8 |
| `wings/dragon` | 24 | 20 |
| the base body | 21 | 21 |
| `outfit_bottom/long_skirt` | 18 | 18 |
| `weapons/dragon_claws` (pair) | 14 | 14 |
| `markings/dorsal_spines` (and its phase-3 shells) | 7 + 7 | 0 |
| `tails/scaled` | 13 | 7 |
| `armour/spined_pauldrons` | 12 | 6 |
| `horns/swept_dragon` | 10 | 8 |
| `accessories/spirecore_pendant` | 10 | 1 |
| `legwear/boots` | 8 | 6 |
| `outfit_top/scale_bodice` | 6 | 6 |

### The wings

`wings/dragon` is a jointed frame in one plane parallel to her back, `depth` px behind it (default 6; a wearer with short
hair may bring it in to 1.75), on the rig's `wing_r` and `wing_l` (a short leather root on the rig bone bridges the gap): an arm (humerus and forearm in one bone, `wing_arm_r`) from the
shoulder blade to the wrist, a knuckle with a pale claw at the wrist, three black finger bones (`wing_hand_r` carries the
first, `wing_f2_r` and `wing_f3_r` the others) and between them the membrane (`secondary`, purple) as half-webs, each a
triangle from the wrist (`cutout: tri_up`) lying next to its finger, so the fan closes into a narrow fold and opens
without a gap. Thin veins (stripes with a `gap`, in `hair_accent`) are `glow: phase`: they light up from phase 2. The
2D design (a wing in its plane, angles from straight down, the open and folded angles of each bone) was solved offline
in a scratch script and written out as numbers; `k` shrinks the wing with the torso on the smaller forms and `span`
scales it.

- **Folded at rest.** The part's rest pose is the fold: the arm up and out so the wrist and its claw stand above the
  shoulder, the fingers and webs hanging down behind and beside the arm to the waist. Any clip that does not key the
  part's bones shows them folded. A first version lay 1.75 px behind the back, under her long hair, and from behind
  the wings could not be seen at all; the plane is now 6 px back, over the hair (the hair falls between the back and
  the folded wings), and from behind they read as two dark fans with purple webs. The last web (`web3`) is narrower
  than first drawn (26 degrees, not 38) so the two folded fans never meet at the spine (`lint` found 127 px of
  coplanar faces where they overlapped), and the five webs (0.15 px thick) now sit apart in depth from each other and
  from every finger's faces: once the wings stood clear of the hair, flush faces near the wrist showed 2-18 px of
  coplanar faces on the adult, teen and chibi bases.
- **Opened by their own bones.** A clip opens a wing by turning the part's bones about z (right wing: arm -50, hand
  +133, f2 -27, f3 -53; mirrored), about 2.2 blocks across at her scale (5.2). The flight clips are a G10 variant,
  `dragon_wings` (below), so any hero wearing these wings may fly with them.
- **The wing loops (design A).** The wings controller plays the shared loops on the rig's `wing_r` and `wing_l` by
  `DATA_LOCO`'s mode (`HeroClips.wingLoop`) and overrides any other controller there. Her clips therefore never key
  `wing_r`/`wing_l` (only the part's child bones), and the loops never key the part's bones: the two compose (the loop
  beats or holds the whole wing, her clip opens or folds it). In the air the shared `sec.wings_flap` and
  `sec.wings_glide` work as they are (the variant turns the open arm +30 about y in the glide and the circle, +16 in
  powered flight, against their forward sweep, so the open wing lies out from the back). **On the ground** the shared
  `sec.wings_fold` turns worn wings in toward the spine (`wings.folded`, `wing_r` ry 32), which suits wings that stand
  out from the back but crossed her already folded fans behind her head (the right wing's inner edge 3.4 px past the
  spine). She has her own `sec.wings_fold` (her file comes first in the chain, so `HeroAnimator` and the previews play
  it): the rig bones at the bind angle, a 2-degree breath and a ruffle at 2.6 s (the wrists lifting out and the fans
  flaring back), keying only `wing_r` and `wing_l` like the shared one. Her ground poses that open the wings had been
  tuned against the shared loop's turn (the arm turned 28 degrees forward to cancel it); with her own loop they carry
  the whole turn themselves: spread wings cupped 8 degrees forward, the perch's half-mantled wings 12, the preen's
  left wing swung 48-52 degrees round her side.

### The flight variant (`herosrc/anim/variants/dragon_wings.json`)

| Clip | Length | What happens |
|---|---|---|
| `move.takeoff` | 0.6 s | the tell: a deep crouch with the wings half opening and rising (0.2 s), the push with the wings full open for the stroke (0.3 s, `vfx:takeoff_dust`), the rise into the cruise; settles to `move.fly` |
| `move.fly` | 1.6 s loop | the cruise lean, the wings open (the flap loop beats them), a slow bob |
| `move.fly_fast` | 0.8 s loop | streamlined and nearly level, the wings half swept back |
| `move.fly_hover` | 1.6 s loop | upright, the legs dangling and treading, the wings open, a bob on each stroke |
| `move.glide` | 2.4 s loop | pitched into the glide, the arms back along her sides, the wings spread wide (the glide loop holds them), a slow roll |
| `move.gust` | 0.4 s | one strong stroke lifts her nearer upright, the knees tucked (`vfx:gust`), then the glide |
| `move.fly_land` | 0.4 s | the wings swept wide and forward to brake as the body tips back, touch-down at 0.18 s with the wings folding in, the knees take the weight, up into the stance with the wings folded by 0.4 s |
| `move.circle` | 2.4 s loop | **new** (5.6: an aerial window's `pattern: circle` round her target, kit-gaps 10): the glide banked into a turn to her left, the inner wing low, her head turned in to watch, the bank breathing deeper and back |

The variant's poses (`dwing.open`, `cruise`, `fast`, `hover`, `glide`, `bank`, `crouch`, `push`, `rise`, `flare`,
`touch`, `give`, `lift`) extend the core's flight poses and add the part's wing bones. `move.circle` is a locomotion
name (`build.LOCOMOTION_BOSS`): a hero may override it, no move id may take it, and an art-only source may carry it.
The engine plays it under the glide loop while she circles (the aerial pattern's hook, open below).

### Phase looks

- **Phase 2**: the veins of the membranes and the horn tips glow (both `glow: phase`, drawn in `_p2.png` and its glow
  mask; the base texture keeps them dark).
- **Phase 3**: `eyes_glow` and the dorsal spines ignite (the `shell` copy on `p3_` bones, whose texels are in the glow
  mask). The breath motes from her mouth are the engine's (5.2: particles at `vfx_head`, the head's centre, open
  below); the `phase.3` clip marks the puff (`vfx:breath_motes` at 0.8 s).
- The claws' glowing seams and Spirecore's crystal glow in every phase; her irises glow in every open eye frame
  (`iris_glow`); her throat glows whenever her mouth is open (`throat_glow`).

### Spirecore's worn part

`accessories/spirecore_pendant` (`"artifact": "spirecore"`): a caged end crystal on a chain at her collarbone. The
chains, the bail and the caps hang from `artifact_spirecore`; the crystal (glowing) and the cage's bars sit on
`artifact_spirecore_cage`, a child bone at the crystal's centre, which her `sec.loop` turns once round every 6 s (the
secondary controller plays `sec.loop` whenever the chain has one; a whole turn unwound at the loop's end). `visual.json`
`bones.artifacts` is `{"spirecore": ["artifact_spirecore"]}` (corrected by the review below: the file lists the part's
declared artifact bone only; the cage is its child, and GeckoLib's `GeoBone.setHidden` hides a bone's children too):
R3's hook in `HeroModel` hides it unless she wears Spirecore ("drawn only while worn", 5.13). Until then it is drawn
always, which is right for her (a boss hero comes with her signature in her first slot).

### Her clips (no hit ticks)

| Clip | Length | What happens |
|---|---|---|
| `sec.loop` | 6.0 s | Spirecore's cage turning (above) |
| `sec.wings_fold` | 4.0 s | her ground wing loop (above) |
| `personal.1` | 3.4 s | she preens her left wing: a look over the left shoulder, the wing half opened and swung forward round her, the membrane combed twice with her right claws, shaken out and folded away, pleased with herself |
| `personal.2` | 3.6 s | she curls her tail round her feet: the tail swings round her left side (the B5 review's correction; it said right) until its tip rests before her toes, like a cat's round its paws, a content closed-eye smile down at it, then it slides back |
| `personal.3` | 3.8 s | she gazes up at the sky: chest lifted, head tipped back (the neck past -25 degrees), the wings easing a little open as if to feel the air, half-closed eyes, a faint smile |
| `intro` | 1.4 s | 5.4: she lands with her wings spread (the knees give), folds them, then lifts her chin with her head tilted and one claw raised lazily, and settles into her stance |
| `phase.2` | 1.4 s | wings spread wide, chest arched and head thrown back: the roar, her throat glowing purple |
| `phase.3` | 1.4 s | head bowed over her clenched claws with the wings drawn in and trembling, then she looks up with glowing eyes as the wings snap open, a puff of dragon's breath (`vfx:breath_motes`) |
| `last_stand` | 1.8 s | no action code yet: the right claw raised high, the left flung out, the wings spread wide, chin up, then set in her stance. She fights to the death: no `escape` clip |
| `rest.perch` | 6.0 s | 5.6's perch (planted): standing tall at the edge, the wings half mantled over her shoulders, breathing slowly, looking out over the land one way and the other, the tail's end swaying. A first deep crouch put the skirt through her thighs and the tail up in the air; she now stands |

`face_ctrl` is keyed in the quirks and the moments. Her own file holds 10 clips and the variant 8 (18 of the 24 the
own file may hold). The sounds of 5.11 are not markers yet (her sound set, B6, does not exist). Every own clip passes
the quality rules on all six bases, and her claws stay clear of her body on every second tick
(`grips.animation_problems`).

### Generator additions (shared code; every other hero builds byte for byte as before)

1. **`crest` on a chain** (`gen.py`, `_crest`): `{"size": [across, out, along share], "slot", "material", "taper",
   "last"}` puts a box on the side of each segment that faces world up most, on the fine_ copy of the segment's bone
   with the segment's rotation and pivot, named `<part>#<chain><i>_crest`; the last segment of a chain is left bare
   unless `last` is true (her rig chain has one on `tail_5`, the tip chain ends bare in its point). A chain without
   `crest` builds as before.
2. **The stripes pattern's `gap`** (`paint.py`): one line of `size` texels every `size + gap`; without it the stripes
   are as wide as their gaps, as before.
3. **`scales` face paint and `throat_glow`** (`face.py`): above. `throat_glow` defaults to false.
4. **`move.circle` a locomotion name** (`build.py` `LOCOMOTION_BOSS`).
5. **`hair/long_layered` `under`**: the original inner group gets `when: 1 - under` and a copy with `when: under` is
   appended at the end of the strands (so the strands before it keep their jitter seeds), longer and in the alt slot.
   `under` 0 builds the style exactly as before (`test_hair_under_layer`).
6. **Previews** (`render/hero_previews.py`): `wings()` lays the wing loop of a clip's mode (`wing_loop_of`: the flap
   loop for powered flight, the glide loop for the glide and the circle, the fold loop otherwise) over every frame of a
   winged hero, as the wings controller does in game; the winged flyer's locomotion segments (walk > run, the flight
   set, glide > gust > glide > circle when she has the circle, the perch when she has it); `wings.png` (folded at rest
   and under the fold loop, spread on the ground, the glide, the top and bottom of the beat, from five views). A
   model without `wing_r` is untouched: the Wither's segments and every other hero's previews are as committed.

### Previews (`docs/heroes-v2/previews/heroes/the_ender_dragon/` and `heroes/lineup.png`)

Laid out like the Wither's, with a README: `turnaround.png`, `wings.png`, `faces.png`, `phases.png`, `sheet.png`,
`string.gif` (the fists string with her talons; her own claw string comes with the kit), `moments.gif`, `idles.gif` and
`locomotion.gif`. No move GIFs and no `ultimate.gif` (no kit yet). The lineup has her beside the others at her own
scale (1.97 blocks, the tallest). The parts sheets (`previews/parts/`) show the new parts on the six bases.
Looked at: the turnaround and wings sheet at 4-7x with and without her hair (which is how the hidden wings were found),
the faces, the phases, the quirks, the moments and the locomotion frame by frame.

### Tests (460 -> 479: `tests/test_ender_dragon.py`, 19)

Her one body (adult, female, standard, 108, 1.97 blocks, no phase changes it); the budget (at most 300 cubes, 130
beyond the fine distance, 100 bones, 256x256); the art-only source (no moves or kit clip; `move.circle` allowed and,
sabotaged out of `LOCOMOTION_BOSS`, refused; a `state.perched` clip refused); her clips pass every rule on every base,
at most 24, no `escape`; the claws clear of her body; the flight clips open the wings through the part's bones and
never key `wing_r`/`wing_l`, and only the loops key those; the quirks (the preen reaches the wing, the tail's tip curls
forward, the gaze lifts the neck past -25 degrees); the wings fold above the hips and behind the back and open 2.05-2.4
blocks (sabotage: without the hand's turn under 1.6); under her own fold loop the fans stay over her hair, above the
hips and each on its side of the spine, and every beat of the shared flight loops stays off the ground (sabotage: the
shared fold loop crosses them past the spine); the glow mask (veins and horn tips only in the phase texture, thin
lines not whole webs; the claws' seams and the crystal always; the seven phase-3 shells on `p3_` bones); the horns;
the tail chain, its tip chain and the six crest spines (sabotage: the chain without `crest` has none); the skirt split
to the knee with three under-panels; the pendant on its artifact bones and the cage's turn; the hair's under-layer
(`under` 0 identical to no parameter); the face (cat slit glowing irises, both fangs, no mood blushing, the scales on both
cheeks, the throat lit in mouth frame 3 and dark in frame 0); the stripes' `gap`; the crest on an inline chain; the
preview rules (the wing loop by mode; the Wither's segments unchanged). Besides the sabotage steps inside the tests,
four sabotages were run in memory (no file edited) and each failed its test: the crest without `last` (5 spines, not
6), her ground loop at the shared fold angles (the right fan 3.4 px past the spine), the `flustered` override removed
(a blush), the wing plane back at 1.75 px (the fans under the hair's back panel).
`test_details`' slice-column test lets her two markings through by name, as it does the Wither's.

**At the commit** (the finished tree): the full suite (479 tests, OK), `check --all --style` (every hero, the style
fixtures and the icons), `build --all --check` (the libraries, `_fallback`, the test rig, Natsuho, Hinae and the Wither
as committed, her files fresh), `lint` (2570 builds and checks, 0 problems), `render-previews --check`,
`parts-previews --check`, `anim-previews --locomotion --check`, `hero-previews --check` for her, Natsuho, Hinae and the
Wither, and `hero-previews --lineup --check`; the style preview set (`previews`) re-rendered byte for byte as committed.

### Decisions

1. **Art only, no fake kit**, as the Wither's: `"kit": "pending"`, no move clip; her perch and her circle are a base
   loop and a locomotion clip (no hit tick), and the kit's perched state (`state.perched`) is refused until the kit
   exists.
2. **Wings folded by the part, opened by her clips; the loops on the rig bones** (design A, above): the only way for
   her clips and the wings controller to compose, since the controller overrides `wing_r`/`wing_l`.
3. **Her own ground wing loop**: the shared one is right for wings that stand out from the back; hers are folded by the
   part, so she holds them at the bind angle. A hero that wears `wings/dragon` later needs the same (the part's and the
   variant's comments say so).
4. **The wings over the hair**: 6 px behind the back, so a woman with long hair still reads as winged from behind.
5. **The claws are her hands** (`sheathed: worn`): there is nothing to sheathe, so the sheathed copy is the worn one.
6. **The palette's dark pair** (the NOTE above) is by design.
7. **No crouch for the perch**: standing at the edge reads as perched in game (she stands on the highest block), and a
   crouch pushed the thighs through the skirt.

### Open items

- **The move step** (against the B1 kit fixture, `BOSS_HEROES.md` 5.3 and 5.12): remove `"kit": "pending"`, copy the
  fixture's kit fields in and author `move.ender_fireball` (on the ground and in the air), `move.dragon_breath`,
  `move.wing_gust`, `move.void_dive`, `move.raise_crystal`, `move.perch` and `state.perched`, `move.void_roar`,
  `move.crescent_tail`, `move.end_gravity`, `ultimate` with its `ultimate.loop` for the breath sweep (`MoveTiming.stops`
  and the `ultimate.end` rule, as Hinae's section explains), `ultimate.awakened`, `victory` (a slow wing stretch) and
  `taunt` (a beckoning claw), each with the kit's frames to the tick. Her clips key the part's wing bones, never
  `wing_r`/`wing_l` (design A). Her own file holds 10 clips and the variant's 8 count toward the 24 the own file may
  hold: the move step will exceed it (about 13 more clips), so either the cap grows for boss heroes (HEROES_V2 4.3 and
  `quality`'s budget) or some flight clips fall back to the core's.
- **Engine hooks** (B1/B3/R3/A12): `HeroModel` hiding `bones.artifacts` unless worn (Spirecore, R3); a mode or code
  that plays `rest.perch` (her perch out of fights) and `move.circle` (the aerial window's circle pattern, kit-gaps 10);
  an action code for `last_stand`; the phase-3 breath motes (particles from `vfx_head`, the head's centre, while phase
  3 lasts); the sound markers of 5.11 once B6 writes her sound set; her definition in `data/factionclash/heroes/` (B7).
- **Gate 3/4**: in-game screenshots (lineup, a phase transition, the flight and the wings controller's loops in game, a
  face close-up) once she has a definition; ROSTER_PLAN 8's concept search on the look.
- **Her kit sheet** (`kits/the_ender_dragon.txt`, ANIMATIONS) says her wings "fold, beat and glide on the shared wing
  loops"; they beat and glide on them, and fold on her own ground loop (above). Left for the sheet's next kit version
  (the kit sheets are the design's files, not this step's).
- Not run here (Gradle is off limits in this task): the Java suites (`HeroAssetsTest`, `BuiltinHeroesTest`,
  `verifyHeroAssets`; they will see her files, which pass the Python mirror of their rules), GameTests, the in-game
  and campaign checks, and the jar.

---

## B5 review gate 2: the Wither, the Ender Dragon and the icons (2026-11-19)

HEROES_V2.md 14.4 gate 2 for B5's two boss looks and for R6's and B5's icons, by a separate art director, before they go
to the owner. The looks come before their kits (`"kit": "pending"`), so the checklist's move items (no clipping in
`attack.1-3`, the weapon in every style animation) apply to the clips that exist: the idles, the locomotion, the
moments and the style's string in their previews. Each finding was confirmed with a number or a render before it was
fixed. The approved style is unchanged; no hit tick, marker or timing file of any clip moved (neither hero has one,
and no library clip changed). Where this section and the two B5 sections above differ, this one holds.

How clipping was measured: for two parts, points sampled inside every cube of the first (a 5 x 5 x 5 grid, 5% in from
the faces), in the clip's pose at each sampled tick with the secondary and wing loops laid over it, that lie inside a
cube of the second (5% in from its faces). A count that stays at its rest value is a fixed overlap by design (the
dragon's tail root through the skirt's back panels, about 40; the short wing roots under her hair, about 150).

### The checklist

| Item | Verdict |
|---|---|
| Reads as the vanilla boss, made into a woman (squint test at 1x) | **The Wither**: yes, at 1x and from every side: two dark cubes with glowing blue eyes at head height either side of a pale face (the three heads), a white ribcage on charcoal, bone bracers, soul-blue lining; from behind the spine and the torn cape. **The Ender Dragon**: the horns with purple tips, purple slit eyes, black dress and the tail with its pale crest read; the wings did not (finding 1). After the fix the purple fans show beside her arms from the front, from 3/4 and from behind, and spread they read as the boss's wings |
| Silhouette (front and side at 4x, `distinct.py`'s frame) | the two bosses 0.62; the Wither against Natsuho, Hinae and the six other slice looks (their sample outfits on their one body) 0.51-0.68; the dragon 0.55-0.77 (the highest, 0.77 and 0.76, Kagenori and Hinae: long hair over a floor-length hakama or skirt). All under the NOTE's 0.90 |
| Colour regions | both bosses' two largest regions are near-black (the Wither's gown `metal` #34313A and hair #2A282D, the dragon's `primary` #1C1A22 and hair #1B1720): delta-E 11.1 and 8.6, under the NOTE's 15, but they are different archetypes, so `check` gives no NOTE. Their accents carry them apart (bone white and soul blue against purple and end-stone), and so do the silhouettes (0.62). Tobimaru (ninja, navy) is as dark (12.9/9.6 and 13.5/8.3; silhouettes 0.52 and 0.63) |
| Greyscale | the Wither in three clear values (charcoal, ash grey, bone white with a pale face); the dragon was black on black, the wings invisible (finding 1): her membranes are now a mid grey against the dress. `check` still NOTEs her primary and secondary at 9% (black and the very dark purple of the bodice's scales), by design |
| Face frames | blink (half, closed), talk A and B, angry (the Wither's glowing frame; the dragon's narrowed slits with the glowing throat), hurt, every mood at 3/4: right. Neither face blushes in any mood, as 4.2 and 5.2 ask |
| Clipping | **The Wither**: her cape's tatters hung inside the smoke gown's flare on every tick of the idle, the hover and most moments (finding 4); the bracers' lower rims and the boots touch the gown at rest (48 and up to 51 points: an arm against a floor-length gown, legs inside it), left. **The dragon**: the tail curl went through the skirt's side panels (finding 2); the sky gaze pushed her tipped-back hair through the wing arms (finding 3); her wings never enter the skirt or the tail; the claws keep clear of her body (`grips`), but their talons dip into the skirt's side panels at the walk's arm swing (up to 143 points, hands at mid-thigh beside a floor-length skirt), left; the open takeoff below |
| Weapon | the sceptre in her right hand drawn, at her right hip head down when sheathed; the claws on both hands always (they are her hands) |
| Phase looks | the Wither's are visible by day (blue-white bone, the glowing hem, no familiars, burning tips, the rebirth). The dragon's phase 2 is light only (veins and horn tips in the phase texture's glow mask, "faint" by 5.2's design) and did not show in `phases.png` by day: it now gets a night cell (finding 6) |
| Worn signature parts | the Hollow Star brooch at the Wither's collar and the caged Spirecore at the dragon's collarbone, both on their artifact bones; the notes misdescribed `bones.artifacts` (finding 7) |
| Existing characters | Compared with the canonical bosses (the intended reference) and with the fan "Wither girl" and "Ender Dragon girl" designs of player skins and resource packs, found with two web searches ("Minecraft Wither girl anime character design three skulls"; "Ender Dragon girl anime Minecraft mob girl design horns wings purple"): no official character; fan Wither girls wear a wither-skeleton skull as a mask or hat with gothic outfits, and fan dragon girls share the genre's marks (black scales, purple horns and eyes, wings, a tail) without a defining combination ours repeats. Ours keep their own marks: the Wither's floating skull familiars, bone crown, ribcage bodice, smoke gown, torn split cape and wither-rose sceptre; the dragon's swept horns with glowing tips, scale bodice with a split long skirt, spined end-stone pauldrons, the caged-crystal pendant and the purple under-layer. Verdict: both read as the vanilla bosses, not as another creator's design. ROSTER_PLAN 8's own concept search and its record (`herosrc/clearance/`, not created yet) remain for gate 4 |
| Modest | high collars, long sleeves, a floor-length gown and a floor-length skirt split only to the knee |
| Budgets (4.3) | the Wither 286 cubes (127 beyond the fine distance), 88 bones, 256x128; the dragon 234 (115), 98 bones, 256x256 (her texture uses 136 rows: 8 over 256x128). Each own file 18-21 clips of 24 |
| One body | each hero's previews and the lineup show her only in her adult body; the parts sheets show the parts on the lint mannequin, not on a named hero |
| Icons | at GUI scales 1, 2 and 3 beside vanilla items every icon holds its silhouette; the tiers differ by richness (one material; a setting and gem; a second precious material with its highlight; the pulse) and name colour; `herogen icons --check` confirms the 61 catalogue names, ids and tiers, the sigil and the tokens. Pairwise, by silhouette overlap and colour: the four shared silhouettes are by design and their second material tells them apart (girdles, signets, spools), except Ghast-Tear Torc against Marrow Torc (finding 5) |

### Found and fixed

1. **The dragon's wings did not read as wings.** The membranes painted the `secondary` slot, #3B2A4F, 9% from her
   black `primary` in greyscale: from behind the folded wings merged with her hair and dress, from the front nothing
   showed, and spread they read as black sticks with dark webs. The owner asked for "purple ... membranes". The
   wings now map `alt` to `hair_accent` (#7A3FB8, the purple of her hair's under-layer) and the veins (`detail`) to
   `accent` (#C77DFF), lighter lines that still glow only from phase 2. The part is unchanged (its default `alt`
   stays `secondary`); only her source's slot mapping moved.
2. **Her tail curled through her skirt** (`personal.2`): `dragon.curl` swung `tail_1` 58 degrees round with its end
   tilted 6 degrees in, so the first three segments ran inside the side panels: 195 sample points at the hold
   against 40 at rest. The tail now leaves her back tilted out (`tail_1` x 18), wraps a little further round her left
   side (`tail_3`-`tail_5` +4 to +8 degrees; the side it always took, which the B5 notes called her right) and the
   last two segments tip down so the tip still rests before her toes, on the ground: at most 52 points on any tick
   (the root's 40).
3. **Her sky gaze put her hair through her wings** (`personal.3`): the head tips back 32 degrees and the long hair
   swung back through the folded wing arms, 390 points (154 at rest with the roots). `dragon.gaze` now draws the wing
   arms back 22 degrees and 6 more degrees open as the head goes back ("the wings easing open as if to feel the
   air"): at most 154 (152 at rest).
4. **The Wither's tatters hung inside her gown.** `back/cape_torn`'s strips hung straight from inside the cape's
   lower edge, 2.5 px above the hem down to 3.5 px below it, while the cape tilts back 5 degrees and the gown flares
   12: 112 points at rest and inside the gown on every tick of the idle, the hover and most moments, showing as blue
   specks of lining through the gown's back panels. The strips now start on the cape's back face (`chest_d / 2 +
   3.9`), are shorter (3, 4 and 2.5 px) and tilt back 16-20 degrees, so they end at the hem: 0 at rest, none or a brush (under 50 points) in
   `personal.1`, `personal.2`, the intro, the last stand, the flight and the glide, and in the idle on 8 ticks of 30
   (all 30 before). They still touch the gown at the peaks of its billow, in the hover flight and the
   half-turn (below).
5. **Ghast-Tear Torc looked like Marrow Torc.** The greater torc's ghast tears were two pixels wide, so the
   outline took all but the highlight: grey tears (#97A4A8) on the slot's grey, 12 pixels from the advanced torc. The
   torc shape's tears are now three pixels wide with a `shadow` outline: pale drops lit from the top left (16
   pixels apart; Marrow Torc, which has no tears, is unchanged). Data only (`herosrc/artifacts/shapes.json`).
6. **`phases.png` now draws an earlier phase at night** when it comes from the phase texture
   (`hero_previews.night_phases`): the dragon's phase 2 (veins and horn tips) and the Wither's (the soul-blue
   armour). Heroes whose phases keep the base texture (Natsuho, Hinae) draw exactly as before.
7. **Docs**: the dragon's section and HEROES_V2's R3 cell said `bones.artifacts` lists Spirecore's cage bone too;
   the file lists `artifact_spirecore` only, which is enough: GeckoLib's `GeoBone.setHidden` (4.9.3, read with
   `javap`) hides a bone's children with it. Corrected in place.

### Left as they are (and why)

- **The dragon's folded wings from the front** show only their purple edges beside her arms: the wrists sit just
  inside her hair's outline. Raising them above her shoulders would mean re-solving the fold's geometry (and the open
  pose's angles and the 2.2-block span with it); with the purple membranes she reads as winged from 3/4 and behind,
  so this is the owner's call at gate 4.
- **The take-off's first frames**: the wings controller switches to the shared flap loop as the take-off starts,
  and for about 0.1 s the loop's up-beat turns the still-folded fans so their knuckles and claws sink into her hair
  (up to 1,998 points at 0.03 s). The engine or the move step can start the flap from the take-off's open pose.
- **The Wither's tatters** touch the gown's back panels when the gown billows to its peak (the hover idle, the hover
  flight, the half-turn). A full fix makes the cape's lower bone follow `skirt_b` in her clips; left for the move
  step, which re-touches her clips anyway.
- Her claws in the skirt at the walk's arm swing, the bracers and boots against the gown, the tail in the skirt's
  back panels while the skirt streams in flight (about 145): arms and tails against floor-length skirts, small and
  brief.
- The dragon's phase 2 by day: faint by design (5.2); the night cell shows it.

### Previews

Rebuilt: both heroes' sets (`previews/heroes/the_wither/`, `previews/heroes/the_ender_dragon/`, every file), the
lineup, the parts sheets (the cape's tatters), the icon sheet (`previews/artifacts/sheet.png`; `inventory.png` does not
show either torc and is unchanged). Looked at: both turnarounds, `wings.png`, the phases, the faces at 3/4, the idles,
moments and locomotion as frame strips at 4-6x from two or three sides, a 1x squint strip of both, greyscale
turnarounds, the icon sheet, the inventory at GUI scales 1-3 and the shared-silhouette icon pairs at 12x.

### Tests (479 -> 485: `tests/test_boss_review.py`, 6)

- The dragon's webs paint `hair_accent`, at least 15% from `primary` in greyscale (sabotage: the old `secondary`
  mapping fails).
- `personal.2`: the tail at most 70 points inside the skirt on any tick (sabotage: the old curl gives 195).
- `personal.3`: the wings, roots aside, at most 150 points inside the hair (sabotage: the old gaze gives 302).
- The Wither's six tatters: none inside the gown at rest, none lower than 1.5 px under the cape's hem (sabotage: the
  old strands give 112).
- Ghast-Tear Torc's tears: at least 12 pixels with at least four light ones, and 14 pixels from Marrow Torc
  (sabotage: the old shape gives 10 pixels).
- `night_phases`: phase-texture phases and the last get a night cell, base-texture phases only the last.

The sabotages were run by putting the committed (pre-review) sources back in place and running the file: the five
look tests failed as listed, then the sources were restored.

### Checks run

At the commit (the finished tree): the full suite (485 tests, OK); `check --all --style` (every hero, the style
fixtures and the icons); `build --all --check` (the libraries, `_fallback`, the test rig, Natsuho and Hinae as
committed, both bosses fresh); `icons --check` and `icons sheet --check`; `hero-previews --check` for both bosses,
Natsuho and Hinae, and `--lineup --check`; `parts-previews --check`; `render-previews --check`; `anim-previews --core`,
`--styles` and `--locomotion --check`; the style preview set (`previews`) re-rendered byte for byte; `lint` (2570
builds and checks, 0 problems; run again after the review was interrupted, before the commit).

---

## No tail between the legs: the Ender Dragon's tail in flight and the tail rule (2026-11-19)

The owner, with a screenshot of `the_ender_dragon/wings.png` (the glide and the two beat rows): "her tail downwards
while she is flying, and it kinda looks like a male genitalia like this, can you make it so her tail will not go
downwards when she flies". In her glide, her beat, her hover, the take-off, the landing, the circle, the gust and the
glide's start the scaled tail hung straight down from the small of her back between her legs, and from the front and
3/4 it read as something obscene. Fixed for her, and made impossible to come back for any hero with a tail: `herogen
check` refuses it (`HANDOFF.md` owner request 12). No hit tick, marker or timing of any clip moved; Natsuho, Hinae,
the Wither, the test rig, `_fallback` and the libraries build byte for byte as before.

### Why it hung

The library's tail follow-through (`anim/secondary.py`, the `tail` spring group, with the flight clips' `wind`) is baked
once, on the reference skeleton, whose tail leaves the small of the back and rises like a cat's (`tail_2` to `tail_5`
straight up). The angles are written relative to each bone, so a tail built another way turns into another shape. Her
`tails/scaled` falls from the small of her back to the ground and trails behind: the same keys that sweep a cat's tail
back in the wind (the glide's `tail_2` -80 degrees, `tail_3` -59) folded hers down and forward, under her. Measured with
the rule below on B5's build: 44 spine points in the zone in the glide, the beat and the fast flight, 43-46 in the
take-off, the landing, the circle, the gust and the glide's start, 12 in the shot-down clip's kip-up, and the tip within
6-18 degrees of plumb below her knees in all of them.

### The fix: `animations.tail_trail` (`anim/tailtrail.py`)

A hero source may list clips whose tail chain is re-keyed **on her own skeleton** (one body for good, HEROES_V2 3.6), so
the tail trails behind her in the air like the vanilla dragon's:

- **Direction**: straight behind her in the world (+z, the way she flies from), raised `lift` degrees (hers: 4), or her
  body's line (hips toward feet) when that points behind and higher (a dive head first trails the tail up behind her).
  Never along the body's line otherwise: lying on her back that line runs toward her feet.
- **Life**: a travelling wave up and down (`wave`: 9 degrees, 1.6 s, 0.7 rad from one link to the next: an S-curve that
  moves down the tail) and a side sway (`sway`: 4 degrees, 2.4 s); a loop's periods are rounded so it closes exactly.
- **Solve**: `tail_1` to `tail_5` turned root first so each link points along its direction (within half a degree,
  `test_solve_points_every_link_along_the_trail`); the tip chain (`tail_tip_1`, `tail_tip_2`) is left to the engine's
  procedural lag as before. Keys every 2 ticks, simplified within half a degree.
- **Blends**: `ramp` [t0, t1] from the clip's own tail to the trail (the take-off leaves the ground at 0.2-0.42 s), `fade`
  back (the landing touches down at 0.1-0.3 s; the kip-up of the shot-down clip swings it clear at 1.1-1.18 s and lowers
  it at 1.3-1.45 s). A one-shot that settles on a re-keyed loop (the take-off on `move.fly`, the jump on `move.leap`, the
  glide's start and the gust on `move.glide`) runs its wave on that loop's clock and ends on its first key exactly
  (`quality.check_settle`).
- **Copies**: a clip her own file does not have yet is copied from her style or the core first, every other track and
  event unchanged (`move.jump` and `move.leap`). Her own file holds 23 clips (the cap 24).

Her list: the take-off, the flight, the fast flight, the hover, the landing, the glide's start, the glide, the gust,
the circle, the shot-down clip, the jump, the leap and the fall. Measured after: in every airborne frame no tail point
lower than 0.5 px under the crotch, the tip 2-13 px above it and more than 15 px behind her hips; from the front only the
root shows at the small of her back, from the side the tail streams back over the skirt with its crest up.

**Three more clips in the `dragon_wings` variant** (any wearer of `wings/dragon` gets them):

- `move.glide_start` (pose `dwing.unfurl`): copying the core's glide start into her file showed it never fitted her: it
  opened the arms wide and left the wings folded, and ended on the core glide's spread arms, not on this variant's glide
  (arms back along her sides): a pop into the glide. Now out of the fall the body tips forward, the arms sweep back, the
  wings unfold (half open at 0.14 s) and spread full with a small overshoot into the glide.
- `move.fall` (poses `dwing.fall_a`, `dwing.fall_b`) and `move.downed` (pose `dwing.sprawl`): copied in, the core's fall
  and shot-down clip came under her claw rule (`grips.animation_problems`, `test_claws_clear_of_her_body`): the fall's
  flail threw her clawed hands over her head (1.1 px into it) and the sprawl flung them over her rolling head (2.2 px).
  She played both from the core before, unchecked. Her fall now flails the arms out to the sides at shoulder height with
  the wings half open; her sprawl flings the arms out lower with the elbows nearly straight (bent, the claws met the
  forearms). Everything else is the core's recipe (the kip, the stars, the timing).

### The tail rule (`tails.py`, run by `herogen check` for every hero whose model has a tail)

- **The zone**, per frame in world space: below the crotch (the lowest point of the base hips), between the outer edges
  of the two legs (measured along the hips' own left-right axis, so a spread leg widens it) and from half a pixel in
  front of the hips' back face forward (along the hips' own front-back axis). Sampled: five points along the centre
  line of every tail cube (its spine), every phase's tail cubes included. A tail that trails behind leaves the zone
  through the back plane; a thick root brushing it with a corner is not a tail between the legs, a segment through it
  is (`BACK_TOL` 0.5 px: the cat tails' roots run down the seat by up to 0.4 px).
- **The frames**: every tick of every airborne clip the hero plays (MOBILITY_AND_WORK 4.1 by its mobility traits:
  everyone's jump, leap, fall and ladder climb; the acrobat's air jump, wall kick and mantle; the climber's cling and
  climb down; the glider's and flyer's glide, glide start, gust and shot-down clip; the flyer's take-off, flight, fast
  flight, hover and landing and the boss flyer's circle; the hover hero's hover and drift; riding with a mount carrier;
  the default dive with a `dive` move; a source without a mobility block has everyone's), and every tick of any other
  clip of its chain where the base body is more than 1 px off the ground (a jump inside a move, an emote's hop, a
  floating hero's idle), each with the secondary and wing loops over it and actions over their base.
- **The stance**: the turnaround's pose passes the same zone, and from the turnaround's front view (every phase look)
  the tail may show below the crotch between the legs over at most one model pixel's area (36 px at 6x): Natsuho's tail
  behind her shows as a hairline through the gap between her thighs (7 px, 11 in phase 2).
- **A NOTE** for a tail tip pointing within 20 degrees of plumb below the knees in any of those frames.

Before the fix the dragon fails 9 clips (40-46 spine points in the take-off, the flight, the fast flight, the landing,
the glide's start, the glide, the gust and the circle, 11 in the kip-up of the shot-down clip) and gets the NOTE on 9;
`tests/test_tails.py` keeps B5's source as the negative case. After it, nothing. Natsuho (acrobat III, climber I: her
jumps, leaps, falls, wall kicks, air jumps with her cat twist, the mantle, the climb and the cling, her escape and every
frame off the ground) passes with nothing changed, and so do the test rig and the style fixtures with tails
(`style_tansy`'s cat tail, `style_tobin`'s fluffy one); her rest pose passes too. Checked regardless of their traits,
those four models pass every airborne clip of the library. The live check's note that Natsuho's tail hangs straight
down in game while the previews show its S-curve was answered by the checkpoint 2 captures (`HANDOFF.md`, "Checkpoint
2 in game"): the engine does not bend it; seen from straight behind, her S-curve reads as a column in game and in the
previews alike.

Not played by the dragon, so not checked for her: the core's air jump (an acrobat's), riding clips (a mount carrier's)
and default dive (a `dive` move without its own clip) put her tail between her legs (32-40 spine points). Her kit's
`void_dive` is a dive: its own clip comes with the move step; should it fall back to `move.air_dive`, `check` refuses
her until her `tail_trail` lists it.

### Left as it is

- **The tail against the skirt in flight**: the floor-length skirt streams back in the same wind as the tail, and the
  tail's root rides along its back panel (the crest spines nearest the root inside the panel): at most 244 sample points
  at the glide's worst tick, 230 in the fast flight and 272 in the circle (the hanging tail's was about 145; 40 at rest).
  From the side the tail now emerges over the skirt's top line. Lifting the trail to 16 degrees only brings the glide to
  138 and raises the tail like a cat's; the clean fix is the skirt keeping lower than the tail in her flight (a wind
  without the skirt group, or the skirt's back panel keyed in her variant), left for the move step with her other
  flight work.
- **The default dive** (`move.air_dive`, which the owner's list names): it carries the ground slam's hit marker, so her
  art-only source refuses it in her own file (`kit pending: move.air_dive has a hit or cast marker`; tried with a
  trail and a fade over the rise) and it is a move-step clip. Until then she does not play it (no `dive` move), and
  the day her source gets the kit with `void_dive`, `check` covers `move.air_dive` for her and refuses the core's (32-40
  spine points) until her own `move.void_dive` exists and her `tail_trail` lists the clip she dives with.
- **The bind pose** (no clip at all, the shape the game shows if a tail chain loses its keys): both tails pass the
  zone and the front view (Natsuho 7 px of hairline, the dragon 0), and with the bones the engine's sway owns reset to
  bind (Natsuho's `tail_1`-`tail_5`, the dragon's two tip bones) as well: should a tail chain ever lose its keys in
  game, it still does not hang between the legs.

### Tests (485 -> 496: `tests/test_tails.py`, 11)

The owner's three rows of the wings sheet fail before (at least 10 spine points each) and pass after; `check` refuses
B5's glide, beat and take-off (and NOTEs the glide's tip) and accepts the fix; her whole chain passes with no NOTE; the
tail stays level with or above the crotch and its tip at least 15 px behind her in the glide, the beat, the hover, the
fast flight and the circle; the loops close, the one-shots hand over to their loops' first keys, the copied clips keep
every other track and event; her file within the 24-clip cap; the solve within half a degree at four body pitches (and
up behind her in a dive); the spec's errors; the clip set by mobility; Natsuho passes; a sabotage of her stance (the
tail swung forward under her) fails the zone and the front view.

### Previews

Rebuilt for her (`previews/heroes/the_ender_dragon/`): `wings.png` (the same rows: the glide and the top and bottom of
the beat now show the tail streaming behind her from every view), `locomotion.gif` (the take-off, the flight, the
hover, the landing, the glide, the gust and the circle) and `sheet.png` (her own file's clips, now with the jump, the
leap, the fall, the glide's start and the shot-down clip). Her turnaround, faces, phases, string, moments and idles,
the lineup, Natsuho's, Hinae's and the Wither's previews and every library sheet are unchanged (their checks pass).
Looked at: the wings sheet, the locomotion GIF frame by frame, close front, 3/4 front, side and rear views of the
glide, the beat, the hover, the take-off, the landing, the jump, the leap, the fall, the circle, the gust, the glide's
start and the kip, at 3-7x.

### Checks run

On the finished tree: the full suite (496 tests, OK); `check --all --style` (every hero, the tail rule included, the
style fixtures and the icons: no problem, and no tail NOTE for any hero); `build --all --check` (the libraries,
`_fallback`, the test rig, Natsuho, Hinae and the Wither as committed, her files fresh; her timing file only gains the
five clips copied in, each with the core's timing, and no existing clip's timing changed); `icons --check` and
`icons sheet --check`; `hero-previews --check` for both bosses, Natsuho and Hinae, and `--lineup --check`;
`parts-previews --check`; `render-previews --check`; `anim-previews --core`, `--styles` and `--locomotion --check`; the
style preview set (`previews`) re-rendered byte for byte; `lint` (2570 builds and checks, 0 problems).

### Open items

- **The move step**: her move clips that leave the ground (the in-air fireball, the void dive, the wing gust, the
  perch's take-off) must keep the tail behind her: list them in her `tail_trail` (or key the tail in their recipes).
  `check` sees their frames off the ground, and `move.air_dive` as soon as her source has a dive move. Her own file
  holds 23 clips of the 24 the budget allows: the move step's dozen clips need the cap raised for boss heroes (HEROES_V2
  4.3) or flight clips moved back to the core's.
- **B7 before the move step**: her definition must not reach the game with `void_dive` before the move step has
  given her a dive clip with the trail; the engine would play the core's `move.air_dive`, tail between the legs, and
  the Java checks do not run the tail rule.
- **In game** (gate 3): see her tail stream behind her in flight. Her `visual.json` hands only the tip chain
  (`tail_tip_1`, `tail_tip_2`) to the engine's procedural sway, so nothing in `HeroModel` overrides her trail keys.
  (Natsuho's `tails/cat_long` hands its whole chain to that sway; the checkpoint 2 captures found her tail in game as
  the previews draw it.)
- **The skirt in flight** (above).

---

## C2: Benika Akagane (2026-11-19)

HEROES_V2.md 15.2 row C2, Benika's half (Yugen Kagaribi's is still open). This adds her source file, her art, her
signature animations and the preview pieces she needed. Her kit is the slice fixture's
(`src/test/resources/heroes/slice/benika_akagane.json`, copied field for field; her server definition belongs to the
engine side). She has one body for good (the owner's rule, HEROES_V2 3.6): **adult, female, large build, drawn at
108%** (SLICE_PLAN 3.3), 52 px tall (1.97 blocks). Every preview shows her only in that body. She has no tail. No hit
tick, marker or timing file of an existing clip changed: the libraries, `_fallback`, Natsuho, Hinae, the test rig,
the Wither and the Ender Dragon build byte for byte as before, and her animation and timing files are new.

### What exists

| Path | What |
|---|---|
| `herosrc/heroes/benika_akagane.hero.json` | the source: the kit, `art` (body, palette, parts, face with three overrides, the iron club, phase looks) and `animations` (style `hammer`, blink `normal`, 55 poses of her own, 18 signatures) |
| generated | `geo/hero` (61 bones, 217 cubes of the 300 budget, 95 beyond the fine distance), `textures/entity/hero` (256x128 texture, glow mask, `_p2` texture and its glow mask), `heroes/*.visual.json`, `animations/hero/benika_akagane.animation.json`, `data/factionclash/hero_timings/benika_akagane.json` |
| `render/hero_previews.py` | the locomotion of a charger and a leaper, `state.X` clips drawn in the boss phase that enters them, the state a phase enters chained after its transition, and a followup that never plays alone shown after its parent (below) |
| `tests/test_benika.py` | 16 tests (below) |

### Her look

The iron club oni, as the sample outfit of G6b and G6c drew her, with five changes. She wears a wild black mane with
red tips (`hair/wild_mane`, length 20 and volume 1.15, up from 15 and 1: a big build needs a big mane), a single
curved horn, an open red coat over chest wraps (`outfit_top/open_coat`, length 0.8, down from 1.3, so the coat ends
above the hakama), a short black hakama (drop 1.0, from 0.85), gauntlets, wrapped feet, the faction sash, a rope belt,
the gourd at her right hip and red stripes on her forearms. The iron club rides on her back. The palette is SLICE_PLAN
3.3's, with two changes:

- **`trim` is her own slot now.** The sample painted the horn in `primary` and the stripes in `hair_accent`, and set
  `trim` to the accent's gold. The horn and the stripes now use `trim` (#B0281F), and the coat, hakama and gauntlet
  edges use `accent` (the same gold as before). Nothing else uses `trim`, so phase 2 can recolour exactly those two
  pieces.
- **`secondary` #211C1C** (was #262121), a little darker, so that `primary` and `secondary` sit more than 15% apart
  in greyscale (the NOTE `check` gave on the sample's pair).

The face uses `sharp` eyes, lashes 1, no flick, `bold` brows, the `wide` mouth and both fangs. Three overrides suit a
battle maniac: `happy` (happy eyes), `focused` (focused eyes) and `determined` (angry eyes) all bare the fang grin.

- **Phase 2** (60%): the phase palette sets `trim` to ember #FF6A24, so in the `_p2` texture the horn and the arm
  stripes flare orange, and the horn's glow region lights them at night. The sample's phase 2 changed nothing by day.
- **Phase 3** (25%): her eyes glow (frame 10 in the glow colour), and her `phase.3` clip puffs steam from the horn
  (`vfx:horn_steam`).

Red top over a black hakama keeps her apart from Hinae (white top over a red hakama). The two share their two largest
colour regions within delta-E 6.2 (black hair against black hair) and 11.6 (her red coat against Hinae's red hakama),
under the NOTE's 15, but their archetypes differ (oni and shrine), so `check` gives no NOTE. Front and side silhouettes
at 4x overlap 0.68 with Hinae and 0.56 with Natsuho (the NOTE is at 0.90). The lineup puts her beside Hinae: the
horn, the mane, the open coat and the large build carry the difference.

### Signature animations

Her own file has the 18 signatures. The hammer library and the core cover the rest: her basic string `attack.1-3`,
the locomotion, the hurt and the death. Each move's clip has the kit's frames to the tick. The `hit` is on the
release, and a `cast` marks the active part's last tick. Crash Waves has a `hit` on each of its three rings
(`waves: 3`, `interval: 0.6`: MovePlan takes the markers as the form's hit ticks when the counts match).

| Clip | Length | Class | Server frames (ticks) | What happens |
|---|---|---|---|---|
| `move.overhead_slam` | 2.25 s | area | 20 / 3 / 24 | the knees give and the club dips off the shoulder, then rises for a slow second over her head, tipped back and trembling (the tell); whipped down and driven into the ground (`hit` 20, the fire cone), `cast` 22. The club sticks: two hauls while it stays in the ground (the punish window), pried out, back on her shoulder |
| `move.shoulder_charge` | 2.3 s | heavy | 18 / 12 / 18 | the left shoulder turned to the target in a deep crouch, a snort with the head bobbing (the tell); the burst (`hit` 18) and a driving run in place while the server carries her, braking on deep knees (`cast` 29); a hop back to her footing and a roll of the shoulder |
| `move.ground_stomp` | 2.7 s | area | 20 / 20 / 16 | the weight onto the left leg, the right knee hauled high and out like a sumo's (the tell), the foot slammed down on its own spot (`hit` 20, the ring of fire) into a deep squat with the club planted; held while the ring rolls out (`cast` 39), up again |
| `move.gourd_swig` | 1.8 s | light | 24 / 2 / 12 | the club kept on her shoulder; the left hand takes the gourd from her hip and tips it up, the head back, a long drink with the gulps bobbing her head, lowered with an "Ahh!" (`hit` 24: the heal), `cast` 25, a wipe of the mouth and the gourd hung back. Upper body |
| `move.grab_throw` | 3.0 s | heavy | 18 / 20 / 24 | crouched, the left hand drawn back open (the tell), the lunge and the grab (`hit` 18), the enemy hoisted overhead and shaken while she laughs, drawn back and hurled down (`cast` 37, the throw sound), the arm following through, a laugh with the fist on her hip |
| `move.meteor_club` | 2.7 s | heavy | 20 / 12 / 24 | a deep gathering crouch with the club overhead (the tell), the launch (`hit` 20, the leap sound), knees tucked in the air with the club cocked back, the crash club-first into a deep squat (`cast` 31; the ground stomp follows); hauled, pried out, back on her shoulder |
| `move.crash_waves` | 3.5 s | area | 20 / 36 / 16 | the ultimate's followup. It starts on the ultimate's crash pose: the club wrenched free, hoisted and smashed down three times (`hit` 20, 32 and 44, a ring each), the last blow held while the rings roll out (`cast` 55), up laughing |
| `ultimate` | 5.3 s | ultimate | 36 / 40 / 32 | Iron Mountain Crash: feet set wide, the club raised as high as she can reach with a shout, tipped back with the whole body drawn like a bow and trembling, a deep crouch and the launch (`hit` 36); up high and arched back (the root lifted 10 px), folding forward as she drops, crashing club-first into a deep squat (`cast` 75; Crash Waves follows from here); stuck in the crater, a laugh, pried out |
| `state.rage` | 1.6 s | - | - | hunched and shaking, then the arms flung wide and the chest thrown out in a roar, back on guard with the angry face Rage keeps (played as phase 2's transition ends) |
| `phase.2`, `phase.3` | 1.1, 1.1 s | - | - | inside the 1.2 s transitions: a hand clapped to her brow under the blazing horn, then thrown back laughing ("Now THIS is a fight!"); the wrist dragged across her mouth, then both hands on the club with the glowing eyes up and a wide fang grin |
| `intro`, `victory`, `taunt` | 1.5, 2.2, 1.5 s | - | - | the club planted head-first and leant on, then back on her shoulder and the left fist squeezed till the knuckles crack; a big laugh with the club on her shoulder, then "Next!" with a pointing hand; the club's head slapped into her open palm twice |
| `last_stand` | 1.6 s | - | - | bent over panting with a hand on her knee and the club propped, then straight up with a wild grin, the club hoisted high and the fist pumped |
| `personal.1-3` | 2.6, 3.0, 3.2 s | - | - | the shoulder roll (both shoulders, then the neck cracked each way); a sip from the gourd with a contented sigh and a blush (her sweet tooth: the gourd is not sake); the club spin (taken from her back, twirled once round at her side, rested on her shoulder, slung back) |

- **No `escape`**: she never escapes (SLICE_PLAN 3.3), so the clip would never play. Her chain has none (the hammer
  library and the core have none either), as the Wither's and the Ender Dragon's have none.
- **No `ultimate.loop`**: as Hinae's, the engine never sends `ultimate.end`.
- **Markers**: the client sounds of SOUND.md 4.7 are markers in the clips their slots name: the releases of the slam
  (`club_swing`), the throw (`throw_whoosh`), the meteor and the ultimate (`meteor_leap`), both phases (`horn_flare`,
  `steam`), the intro and the three quirks (`shoulder_roll`, `gourd_sip`, `club_spin`). The wind-ups, the impacts and
  the server's releases (the stomp, the gourd swig) are not markers.
- **Faces**: every clip keys `face_ctrl`.
- **Rules**: every own clip passes the quality rules on all six bases (no foot slides, planted feet within 0.5 px,
  the soles on the ground, the joint limits), and the club stays clear of her body and above the ground on every
  tick (`grips.animation_problems`). The arm poses were solved on her own skeleton for grip points and the club's
  direction. The legs of every planted pose were solved in 2-D (the hips' and knees' rotations) so that each sole
  stays on its spot in the hammer stance. The airborne poses lift the root (`root` in the key) and use the `floor`
  fix. The club stuck in the ground stays put while she hauls on it: its head moves 0.57 px in the slam and 0.19 px
  in the meteor.
- **Out of the crater**: between two keyed poses the arms' angles blend, not the club's path, so the club swung
  through her waist or her left shin on the way up out of the ground (1.4 to 2.1 px deep, on odd ticks only: a
  check of every second tick missed it). The climb out of the crash (in the ultimate and at the start of Crash
  Waves) and each hoist after a ring now pass through two solved waypoints a tick apart (`ben.cw_free`,
  `ben.cw_free2`, then `ben.cw_lift`), so every tick is a solved pose and the club stays clear.
- **Props that follow her hand**: the gourd (in the swig and `personal.2`) and the carried club (`weapon_back`, in
  `personal.3`) are keyed on their own bones every tick (0.05 s), solved so that the prop stays in the hand (within
  1.2 px for the gourd and 1.0 px for the club) and rests at its place on her hip or back at the clip's ends. The
  weapon flag stays sheathed, because an idle never changes it. The keys come from a solve in the author's scratch
  tools; the source holds the keys, and nothing in the generator changed for them.

### Generator additions (`render/hero_previews.py`; every other hero's previews render byte for byte as before)

- **Locomotion by traits**: a charger also shows `move.charge_run > move.skid` (its traversal charge), and a leaper
  shows `move.crouch_jump > move.leap > move.land_heavy` in place of the plain jump. Benika is both. Hinae's and
  Natsuho's segments are unchanged.
- **A state clip's phase**: `phase_of_clip` draws `state.X` in the first boss phase whose `states` lists it (her
  `state.rage` in phase 2).
- **A phase's state after its transition**: `moments.gif` plays `phase.N` and then, at the transition's end, the clip
  of each state that phase enters, as `HeroBoss` starts the state when the transition ends. The ultimate's state is
  no longer drawn when it is a phase's state.
- **A followup that never plays alone** (move weight 0, Crash Waves): `ultimate.gif` chains it after the ultimate,
  starting one tick after the ultimate's last `hit` or `cast`, where the move runner starts a followup; its own
  `move_crash_waves.gif` is that same chain, since the clip alone would pop from the stance into the crater.
- Captions for her phase looks (`OWN_CAPTIONS`).

### Previews (`docs/heroes-v2/previews/heroes/benika_akagane/` and `heroes/lineup.png`)

Her set, as Natsuho's and Hinae's, with a README: `turnaround.png`, `faces.png`, `phases.png`, `sheet.png`, a GIF per
move, `ultimate.gif`, `string.gif`, `moments.gif`, `idles.gif` and `locomotion.gif`. The lineup now has her first
(alphabetical), beside Hinae, at 108% (1.97 blocks, as tall as the Ender Dragon).

### Tests (496 -> 512: `tests/test_benika.py`, 16)

- Her one body: adult, female, large, 108, and no phase changes it; the kit is the fixture's.
- Every move's and the ultimate's frames equal the kit's (Crash Waves with its three ring hits 12 ticks apart), and
  `assets.check_frames` passes on the written chain.
- Her own clips pass every rule on every base, at most 24, no `ultimate.loop` and no `escape`; the club stays clear of
  her body on every tick (every second tick, as Hinae's test samples, missed the crater's odd ticks).
- Every client slot has its marker, no server slot is a marker, and the ultimate's client release is checked too.
- `face_ctrl` in every clip; her happy, focused and determined faces bare the fang grin.
- Crash Waves starts on the ultimate's crash pose (within 2 degrees on every bone).
- Phase 2 flares the horn and the stripes: only those two use `trim`, the `_p2` trim is ember (brighter by more
  than 120 summed RGB) and the `_p2` glow mask has more lit texels.
- The gourd stays in her hand from the grab to the release and rests on her hip at the clip's ends; the carried club
  stays in her hand through the spin (and points up, down, forward and back on the way round), with no weapon markers.
- The preview rules: her locomotion (a charger and a leaper; Hinae's list unchanged), `state.rage` drawn in phase 2
  after the transition, Crash Waves shown after the ultimate, and the lineup (her in, the test rig last).

Sabotage: with the preview code as it was before (the charger and leaper branch only, no state or followup
handling), the locomotion test or the two shot tests fail; restored, they pass.

### Checks run

`herogen check benika_akagane` (all of 14.2, the tail rule included) OK; `build --check --all` OK (the libraries,
`_fallback`, Natsuho, Hinae, the test rig, the Wither and the Ender Dragon build byte for byte as committed); `lint`
of her eleven parts (wild_mane, single_curved, open_coat, hakama, gauntlets, foot_wraps, sash_obi, rope_belt, gourd,
arm_stripes, iron_club) on every base: 246 builds and checks, 0 problems; `sfxgen check` 0 errors; `hero-previews
benika_akagane --check`, `hero-previews natsuho_komezaki hinae_shirasuzu the_wither the_ender_dragon --check` and
`hero-previews --lineup --check` OK; the Python suite, 512 tests, OK.

### Decisions

1. **Her kit is the fixture's verbatim** (as Natsuho's and Hinae's).
2. **The `trim` slot for the horn and the stripes**, so phase 2 shows by day through the phase palette alone, without
   a new part or glow shells.
3. **No `escape` clip**: she never escapes.
4. **Crash Waves starts on the ultimate's crash** and the previews show it only after the ultimate, the only way it
   plays.
5. **Dense keys for the props that follow her hand** rather than a new parent-switch feature: GeckoLib cannot reparent
   a bone mid-clip, and keying the prop's own bone every tick keeps the engine as it is.
6. **Her idle quirks use what she carries**: the gourd at her hip and the club on her back.

### Open items

- **Engine side**: her definition in `data/factionclash/heroes/` (the fixture's fields); the `last_stand` action code
  (6.13); the `vfx:horn_steam` puff in `phase.3` and the other `vfx:` markers of her moves; Rage's 105% size (the
  state's `size`) applied to the model.
- **The gourd's sway**: `visual.json` hands the gourd to the engine's procedural secondary sway, which adds to the
  keys in the swig and `personal.2`. If the gourd wobbles off her hand in game, the engine can skip the sway while an
  action keys the gourd's bone.
- **Followup films**: `string.gif` shows Meteor Club > Ground Stomp with the stomp after the whole meteor clip (the
  shared followup film, which Natsuho's previews also use); in game the stomp starts at the end of the meteor's active
  part. Moving that film to the active end would re-render Natsuho's `string.gif`, so it is left for a deliberate
  shared change.
- Not run here (Gradle and Java are off limits in this task, a Java workflow runs beside it): the Java suites
  (`HeroAssetsTest`, `BuiltinHeroesTest`, `verifyHeroAssets`), GameTests, the in-game and campaign checks (gates 3
  and 5), and the jar.

## C2: Yugen Kagaribi (2026-11-25)

HEROES_V2.md 15.2 row C2, Yugen's half (Benika's is above). This adds his source file, his art, his signature
animations, three parts and the generator pieces he needed. His kit is the slice fixture's
(`src/test/resources/heroes/slice/yugen_kagaribi.json`, copied field for field; his server definition belongs to the
engine side). He has one body for good (the owner's rule, HEROES_V2 3.6): **adult, male, slim build, drawn at 100%**
(SLICE_PLAN 3.2). Every preview shows him only in that body. No hit tick, marker or timing file of an existing clip
changed: the libraries, `_fallback`, Natsuho, Hinae, Benika, the test rig, the Wither and the Ender Dragon build byte
for byte as before, and his animation and timing files are new.

### What exists

| Path | What |
|---|---|
| `herosrc/heroes/yugen_kagaribi.hero.json` | the source: the kit, `art` (body, palette, parts, face with three overrides and the `liner` mark, the folding fan, phase looks) and `animations` (style `focus`, blink `calm`, the G10 variant `fox_lope`, 42 poses of his own, 18 signatures and `sec.loop`) |
| `herosrc/parts/tails/fox_nine.json` | nine fox tails in one fan: three full chains and six level-of-detail copies (below) |
| `herosrc/parts/accessories/orbit_lanterns.json` | three paper lanterns on an orbit a clip can tilt and widen (below) |
| `herosrc/parts/markings/foxfire_tips.json` | the tips of the three tails phase 3 brings back, in their own slot and lit in the phase glow mask |
| generated | `geo/hero` (95 bones of the 100 budget, 298 cubes of the 300, 127 of the 130 beyond the fine distance), `textures/entity/hero` (256x256 texture, glow mask, `_p2` texture and its glow mask), `heroes/*.visual.json`, `animations/hero/yugen_kagaribi.animation.json`, `data/factionclash/hero_timings/yugen_kagaribi.json` |
| `gen.py` | chain fields `angles` and `lod_from` (`lod_points`, `lod_sections`) |
| `render/scene.py` | `hidden()` follows `HeroModel.plan`: phase looks that show bones, and `p1_` bones only in phase 1 |
| `tails.py` | the tail rule covers every tail chain, not only `tail_1`'s |
| `face.py` | the `liner` face paint |
| `render/hero_previews.py`, `anim/loco_preview.py` | a runner's own sprint in his locomotion film, his captions; `hero_yugen.gif` of G10 from his own source |
| `render/partsheet.py` | the orbit lanterns' band on the accessories sheet; `previews/parts/head_tails_wings.png` and `details_accessories.png` re-rendered |
| `tests/test_yugen.py` | 21 tests (below) |

### His look

The lantern fox of the sample outfit (G6b, G6c), with the changes below. Long silver-white hair half tied up with a
red cord, fox ears, a plum court robe with silver-white sleeves and a red trim, his crest (a plum blossom) on the back
and the sleeves, a white fox mask worn on the side of his head, the faction sash, prayer beads, white socks and dark
shoes, and the folding fan at his right hip. The palette is SLICE_PLAN 3.2's (plum `primary` #6B2148, silver-white
`secondary` and `hair`, foxfire blue `accent` and `glow`, red `trim`).

- **Nine tails in one fan** (`tails/fox_nine`, size 1.15), in place of the sample's three (`tails/fox`, count 3). The
  kit's phases show three, six and nine. Three wearings of `tails/fox` (one per phase) would cost 173 bones, so the
  fan is built once: the middle tail on the rig's `tail_1..5`, the pair at +-37.5 degrees on coarser three-bone chains
  (`tail_1_b`, `_c`), and six level-of-detail copies at +-18.75, +-56.25 and +-75 degrees (`fine_tail_1_d` .. `_i`),
  each on ONE bone with all its segments: a whole tail for one bone, moving as one piece, and gone beyond the fine
  distance.
- **The phase looks** hide and show their roots: phase 1 hides the six copies (three tails: the middle one and the
  pair); phase 2 shows the four outer copies and hides the middle tail (six, fanned wide); phase 3 shows the middle
  tail and the inner pair (nine). Beyond the fine distance phases 1 and 3 keep three tails and phase 2 two (the pair).
- **Foxfire tips** (`markings/foxfire_tips`): the tips of the three tails phase 3 brings back (the middle one and the
  inner pair) are repainted in their own slot, `metal` (white like the other tips; nothing else wears `metal`). Phase
  3's palette turns `metal` foxfire blue (#7FC8FF) in the `_p2` texture and the tips are lit in its glow mask, so the
  three tails that come back come back burning. Phase 3 also lights his eyes (frame 10 in the glow colour).
- **The lanterns** (`accessories/orbit_lanterns`, in place of G6c's `accessories/foxfire_lanterns`): `lantern_ring`
  (under `root`, at the top of his chest, so the lanterns stay level when he bows) holds `lantern_orbit`, which his
  `sec.loop` turns once round him every 6 s, and that holds the three lanterns `fine_lantern_1..3`. A move moves,
  tilts and widens the ring (the ultimate stands it up behind him as a wheel) and turns or scales a lantern, while the
  orbit keeps turning in the ring's plane. Five bones, one fewer than the G6c part, with one key track for the orbit
  instead of three. The paper glows in every phase (lit in the base glow mask as well as the phase one).
- **The face**: `narrow` eyes with lashes and an upward flick, `thin_arched` brows, the `soft` mouth, one highlight and
  a new face paint, `liner` (`face.py`): a kitsune's red eye-liner along the outer half of the lower lid, fading
  toward the nose, on the row under the eye that every eye frame leaves as skin, so it shows with the eyes shut too
  (the eye frames reach the face's edge, so an outward flick past the eye had no room). His overrides keep him
  smiling: `calm` smiles, `focused` keeps the focused eyes with the smile, and `angry` is focused eyes over a flat
  mouth: he never shouts.
- **Smaller changes**: the socks end lower (`sock` 0.2, from 0.4), since the cuffs poked through the robe at the
  knees in the deep bows.

Plum and silver-white keep him apart from the others: their two largest colour regions differ from Benika's by
delta-E 39.8 and 79.8, Hinae's by 47.5 and 78.0 and Natsuho's by 36.5 and 69.8 (the NOTE is at 15). Front and side
silhouettes at 4x overlap 0.66 with Benika, 0.69 with Hinae and 0.58 with Natsuho (the NOTE is at 0.90). He is the
only fox with a source, so `check` gives no NOTE.

### Signature animations

His own file has the 18 signatures and `sec.loop`; the focus library and the core cover the rest: his basic string
`attack.1-3`, his thrown basic attack (`attack.ranged`), the locomotion, the hurt and the death. Each move's clip has
the kit's frames to the tick: the `hit` on the release and a `cast` on the active part's last tick; Fan Cut has a
`hit` on each of its two strikes and the ultimate one on each of its nine shots (MovePlan takes the markers as the
form's hit ticks when the counts match). He fights with the folding fan: closed it is a baton, and it snaps open for
each release (below, "The open fan").

| Clip | Length | Class | Server frames (ticks) | What happens |
|---|---|---|---|---|
| `move.foxfire_volley` | 1.1 s | light | 10 / 6 / 8 | his tails flare wide and lift (the tell) while the closed fan is cocked across before his left shoulder and the chest turns away, a held beat, then the fan snaps open and sweeps out flat to the front right as five flames fly (`hit` 10), on out to his right (`cast` 15); the fan snaps shut and he settles |
| `move.lantern_ring` | 4.7 s | area | 20 / 60 / 16 | the closed fan raised upright before his face like a prayer stick, eyes shut, while the lanterns sink to his hips and spread wide (the tell); the fan snaps open and sweeps down and out (`hit` 20, the ring of lantern fire), and he stands composed inside it fanning himself while it pulses (`cast` 79); the fan shuts and the lanterns rise back into their orbit |
| `move.fan_cut` | 0.9 s | light | 8 / 6 / 6 | the fan snaps open and is drawn back high beside his head, slashed down and across (`hit` 8), whipped round and swept flat out to the front right (`hit` 13, the knockback), carried on out and tipped up, shut as he settles |
| `move.vanish` | 0.7 s | light | 8 / 2 / 6 | the knees dip and the left sleeve sweeps up before his face, a sly glance over it; the hop back (`hit` 8: invisible, carried 6 blocks back), knees tucked under the sleeve (`cast` 9), down soft on bent knees with the sleeve swept away |
| `move.mirage` | 1.7 s | heavy | 16 / 4 / 16 | the fan opens beside his face as a screen with only his narrowed eyes showing past it, the lanterns drawn in close and bright, his body wavering side to side like a heat shimmer (the tell); the fan sweeps aside low and the left arm opens palm up (`hit` 16, the two mirages step out of him), the lanterns flung wide (`cast` 19); a smug tilt of the head |
| `move.foxfire_rain` | 3.7 s | area | 20 / 40 / 16 | the closed fan pointed straight up and the left palm raised, head tipped back, the lanterns climbing high (the tell); the fan snaps open and sweeps down to point at the ground ahead (`hit` 20, the rain begins), then beats time with the falling flames every half second (`cast` 59); the fan shuts and the lanterns come down |
| `ultimate` | 4.8 s | ultimate | 32 / 36 / 30 | Ninefold Lantern: a half step back with the fan opened beside his face as a screen while the lanterns gather behind him and the ring stands up into a wheel (`lantern_ring` tipped 90 degrees, 9 px behind him); the fan swept up high and the left arm opened, the wheel blazing ("Nine lanterns, one bill"); nine flicks of the open fan, forward right and forward left in turn, one flame each (`hit` 32, 36 .. 64), `cast` 67; the fan snapped shut with a small courteous bow while the wheel lies back down into his orbit |
| `phase.2`, `phase.3` | 1.1, 1.1 s | - | - | inside the 1.2 s transitions: the six tails fan out from behind him (from folded together where his three were, past their place, settling) while he laughs behind his left sleeve; the three tails come back burning into the middle of the fan while he flings his arms up and open with the fan wide, the eyes glowing and the lanterns flaring and rising |
| `intro`, `victory`, `taunt` | 1.5, 2.0, 1.5 s | - | - | a slow courteous bow with the closed fan across his chest and the left hand behind his back, up again with the closed fan to his lips, eyes smiling; fanning himself lazily, laughing softly with his eyes closed, then the fan snapped shut and tapped twice into his left palm ("Paid in full."); the closed fan to his lips and the left hand held out palm up, beckoning with curling fingers |
| `last_stand` | 1.6 s | - | - | the fan snapped shut and held point down at his side, the smile gone for once; then flicked open into a guard before his chest with two fingers raised beside it, the lanterns drawn in close and blazing |
| `escape` | 0.6 s | - | - | over the run ("Mm. A poor bargain."): the left sleeve swept up before his face as he turns away, a sidelong glance back over it with a smirk, then the arms back into the run; upper body only |
| `personal.1-3` | 2.4, 2.6, 2.2 s | - | - | the sleeve laugh (the sleeve to his mouth, the head tipped, the shoulders shaking); the lantern spin (one finger up drawing a slow circle, and each lantern whirls once round on itself and bobs); the tail-fan flick (a smug glance back over his left shoulder as all his tails flare wide and lift, then settle) |
| `sec.loop` | 6.0 s | - | - | the lanterns' orbit on the secondary controller: once round him every 6 s, wobbling a little like a floating ring; it keys only `lantern_orbit` |
| `move.sprint` | - | - | - | G10's `fox_lope` variant (MOBILITY_AND_WORK 15.2), named in `animations.variants`: his runner's sprint burst is the low fox lope |

- **The open fan**: the folding fan part is one closed slat stack, and it opens by scale: `weapon_r` scales 3x along
  the slats' width and `fine_weapon_r` (the cord and tassel) 1/3 back, keyed with the rotations. `Skeleton.world`
  ignores scale, so `grips.animation_problems` checks the closed fan only; `test_yugen` samples the open fan's slats
  at their scaled size on every tick of every clip that opens it (the volley, Lantern Ring, Fan Cut, Mirage, the
  ultimate) and finds them clear of his body.
- **A fan beside the face, not over the mouth**: a fan held across the mouth needs the right wrist past its limit on
  his skeleton, so Mirage and the ultimate hold the open fan up beside his face as a screen (his eyes show past its
  edge), and the intro and the taunt put the closed fan to his lips.
- **The wheel**: tilted 90 degrees, the orbit turns in the wheel's plane, so the lanterns would hang sideways; each
  lantern is turned back by the ring's tilt (`fine_lantern_N` rotated by -90 degrees) at the wheel's start, so they
  face him as they climb and tumble a little as the wheel turns.
- **No `ultimate.loop`**: as Hinae's and Benika's.
- **Markers**: the client sounds of SOUND.md 4.7 are markers in the clips their slots name: the releases of the volley
  (`foxfire_volley`), Fan Cut (`fan_cut`) and Vanish (`vanish`), the ultimate's nine shots (`ninefold_shot` on each
  hit), both phases (`tails_fan`, `lantern_flare`), the intro (`intro`), the victory and the sleeve laugh (`laugh`)
  and the lantern spin (`lantern_spin`). The wind-ups, the impacts and the server's releases are not markers.
- **Faces**: every clip keys `face_ctrl`.
- **Rules**: every own clip passes the quality rules on all six bases (no foot slides, planted feet within 0.5 px, the
  soles on the ground, the joint limits), the closed fan stays clear of his body and above the ground on every tick,
  and `herogen check` passes the tail rule for all nine tails in every clip. The arm poses were solved on his own
  skeleton for grip points and the fan's direction; the idle quirks name `move.idle` as their base (`settle_to`,
  `plays_over`).

### Generator additions (every other hero builds and previews byte for byte as before)

- **`gen.py`, chain `angles`**: the copies' fan angles listed in build order, in place of `fan` (the first keeps the
  bone names, the others take `_b`, `_c` .. in that order), so a phase look can show the copies a few at a time.
  The count must equal `copies`.
- **`gen.py`, chain `lod_from`** (with `lod_points` and `lod_sections`, by default the copies'): copies from that index
  on are each built on one bone, `fine_` + the first bone's name + the copy's suffix, holding every segment; such a
  copy declares no other bones and leaves no `chain_points` behind. `lod_from` outside 1..copies or a points/sections
  mismatch is an error.
- **`render/scene.py`**: the preview renderer hid a phase look's `hide` list but ignored its `show` list, and drew a
  `p1_` bone in every phase. `hidden()` now follows `HeroModel.plan`: a `pN_` bone from phase N on (a `p1_` bone in
  phase 1 only), then each phase look up to the current phase shows and hides its lists in phase order, the later
  look winning. The other heroes' looks show only their own `pN_` bones from that phase on (drawn then anyway) and
  no hero has a `p1_` bone, so their previews render byte for byte as before.
- **`tails.py`**: the tail rule took only the rig's `tail_1` and its descendants, so a fox's copies (`tail_1_b` ..,
  children of `body`) were never checked. It now takes every tail chain root (`tail_1`, `tail_1_b` .., `fine_` and
  `pN_` prefixes) with every bone below it, and `tip_down` checks each chain's tip. Natsuho, the Wither and the Ender
  Dragon pass as before.
- **`face.py`**: the `liner` face paint (above).
- **`render/hero_previews.py`**: a runner whose own file overrides `move.sprint` shows `run > sprint` in
  `locomotion.gif` (his fox lope); his captions (`OWN_CAPTIONS`). Hinae's, Natsuho's and Benika's segments are
  unchanged.
- **`anim/loco_preview.py`**: G10's `hero_yugen.gif` (the fox lope) is drawn from his own source in his one body
  (before, from the sample outfit); `previews/anim/locomotion/hero_yugen.*` and its README line are re-rendered.
- **`render/partsheet.py`**: the orbit lanterns are drawn in the lanterns' band (as `accessories/foxfire_lanterns`);
  the parts sheets `head_tails_wings.png` and `details_accessories.png` are re-rendered with the three new parts (the
  other sheets are unchanged). `tests/test_details.py` lists `markings/foxfire_tips` beside B5's markings.

### Previews (`docs/heroes-v2/previews/heroes/yugen_kagaribi/` and `heroes/lineup.png`)

His set, as Natsuho's, Hinae's and Benika's, with a README: `turnaround.png`, `faces.png`, `phases.png`, `sheet.png`,
a GIF per move (Mirage in phase 2 and Foxfire Rain in phase 3, their first kit phases), `ultimate.gif`, `string.gif`
(the thrown basic attack, the focus string, Fan Cut > Vanish and Vanish > Foxfire Volley, their followups),
`moments.gif`, `idles.gif` and `locomotion.gif` (walk > run, run > the fox lope, crouch jump > leap > heavy landing,
climb). The lineup now has him last of the heroes, before the test rig.

### Tests (512 -> 533: `tests/test_yugen.py`, 21)

- His one body: adult, male, slim, 100, and no phase changes it; the kit is the fixture's; the budgets (95 bones,
  298 cubes, 127 beyond the fine distance) are within 100 / 300 / 130.
- Every move's and the ultimate's frames equal the kit's (Fan Cut's two strikes and the ultimate's nine shots 4 ticks
  apart), and `assets.check_frames` passes on the written chain.
- His own clips pass every rule on every base, at most 24, no `ultimate.loop`, and `escape`, `last_stand`,
  `move.sprint` and `sec.loop` are there; the closed fan stays clear of his body on every tick; the open fan (scaled)
  stays clear on every tick and opens in the volley, Lantern Ring, Fan Cut, Mirage and the ultimate.
- Every client slot has its marker (the ultimate's nine), no server slot is a marker; `face_ctrl` in every clip; his
  calm and focused faces smile, and the liner is painted.
- One fan of nine: the six level-of-detail copies are single `fine_` bones with every segment's cubes, nine secondary
  chains, the fan's angles in build order.
- The phase looks show three, six and nine tails, and the full-detail pair stays beyond the fine distance in every
  phase; `scene.hidden` follows `HeroModel.plan` (show lists, `p1_` bones, Natsuho's phases unchanged).
- The foxfire tips: only the marking wears `metal`, the `_p2` tips are bluer, the `_p2` glow mask lights more texels,
  and the tip cubes are on `tail_5`, `fine_tail_1_d` and `fine_tail_1_e`.
- The tail rule sees every tail: a level-of-detail tail turned down between the legs is refused, and his clips pass.
- The lanterns' orbit keys only `lantern_orbit`; the ultimate stands the ring up (-90 degrees, more than 6 px behind
  him) and turns each lantern back by 90 degrees.
- `gen.py`: `angles` and `lod_from` build what they say, and a wrong count, a `lod_from` out of range and mismatched
  `lod_points` are errors.
- The `liner` paints six texels on row 16 only; his locomotion film has the sprint (Hinae's list unchanged); the
  lineup takes him.

Sabotage (each restored afterwards, the diff clean): the old `scene.hidden` fails the two phase tests; the old
`tail_bones` fails the tail test; `lod_from` ignored puts him over the bone budget (the class fails to set up); no
sprint segment fails the locomotion test; a `liner` that paints nothing fails the liner test.

### Checks run

`herogen check yugen_kagaribi` (all of 14.2, the tail rule for all nine tails included) OK; `check --all --style` OK
(every hero, the style fixtures and the icons; its one NOTE, the test rig's `lunge`, is old); `build --check --all` OK
(the libraries, `_fallback`, Natsuho, Hinae, Benika, the test rig, the Wither and the Ender Dragon build byte for byte
as committed); `lint` of his twelve parts (half_up_long, fox ears, fox_nine, foxfire_tips, court_robe, fox_mask,
sash_obi, socks_shoes, prayer_beads, orbit_lanterns, crest, folding_fan) on every base: 260 builds and checks, 0
problems; `sfxgen check` 0 errors; `hero-previews yugen_kagaribi --check`, `hero-previews natsuho_komezaki
hinae_shirasuzu benika_akagane the_wither the_ender_dragon --check`, `hero-previews --lineup --check`, `anim-previews
--locomotion --check`, `--core --check` and `--styles --check`, `parts-previews --check`, `render-previews --check` and
`icons --check` OK; the Python suite, 533 tests, OK. (A first run of the suite caught `test_benika`'s locomotion test
calling the new sprint branch with a bare stub hero; the branch now asks only a hero with an `animations` block for its
own clips.)

### Decisions

1. **His kit is the fixture's verbatim** (as the other slice heroes').
2. **One fan of nine tails with level-of-detail copies**, shown three, six and nine by the phase looks, rather than a
   part per phase: three fans would cost 173 bones. The price: beyond the fine distance phase 2 shows two tails, and a
   level-of-detail tail moves as one piece. In game the turn sway (`HeroModel.swayChain`) leaves one-bone chains to
   the clips, so those six tails follow only the clips' keys, not the body's turns.
3. **Phase 2 hides the middle tail**, so six read as a wide fan with a gap, and phase 3 fills the middle with the
   three that come back burning: the foxfire tips are on exactly the tails phase 3 adds.
4. **The fan opens by scale**, keyed with the rotations, rather than a second fan part swapped in: no extra bones, and
   GeckoLib plays scale keys. Its clearance has its own test because the rules' skeleton ignores scale.
5. **His own lantern part** with a ring and an orbit, so one key track turns the orbit and a move can still tilt and
   widen the ring.
6. **An escape clip**: he escapes when a fight is lost (SLICE_PLAN 3.2: a schemer's life is his best asset), so he
   has one, a sleeve and a smirk over the run.

### Open items

- **Engine side**: his definition in `data/factionclash/heroes/` (the fixture's fields); the `last_stand` and `escape`
  action codes (6.13); the `vfx:` markers of his moves (the flames, the ring, the rain, the mirages, the wheel).
- **In game**: the open fan's scale keys and the wheel's lanterns (they tumble a little as the wheel turns) want a look
  in the client, and whether the six level-of-detail tails look too stiff in a turn without the procedural sway.
- **The Java checker, read, not run**: `HeroAssetCheck` checks every channel alike (scale keys included), counts the
  cubes under `fine_` bones and their children as level of detail (the Python count, 127) and has no rule on secondary
  chains, so nothing in it should refuse Yugen's scale keys or one-bone `fine_` tails; `HeroAssetsTest` and
  `BuiltinHeroesTest` still need a run.
- **Followup films**: as Benika's, `string.gif` starts each followup after the whole first clip (the shared followup
  film); in game it starts at the end of the first move's active part.
- Not run here (Gradle and Java are off limits in this task): the Java suites (`HeroAssetsTest`, `BuiltinHeroesTest`,
  `verifyHeroAssets`, which also hold the 100 / 300 / 130 budgets), GameTests, the in-game and campaign checks (gates
  3 and 5), and the jar.
