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
| `attack.1` | `_style_katana` | 0.9 s | wind-up to 0.20 s (easeOutCubic) and a stretch to 0.30 s, the shoulder sweeps about 130 degrees through the cut, which lands at **0.40 s** (`hit;vfx:slash_arc`, tick 8), follow-through to 0.55 s, back on `combat.idle` at 0.90 s; a sweep sound at 0.30 s; the face goes focused at 0.05 s, angry eyes with a shout at the hit, no override from 0.70 s; upper body only (waist, chest, neck, arms) |

The idle keys `neck`, never `head`: the head is left to the procedural head look of `HeroModel.setCustomAnimations`.
`attack.1` keys no leg, `body` or `root` bone, so a hero can swing while the base controller walks it (the references
also split the arm layer from the locomotion). Its timing file says `frames: windup 0.4, active 0.05, recovery 0.45`
(ticks 8 / 1 / 9): class light, at the 6.4 minimums (0.4 / 0.3).

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
| sleeves | `sleeve_r`, `sleeve_l` | 0.5 / 0.7 | 1.0 | 85 | 34 / 85 / 79 (mostly hanging) |
| skirt | `skirt_f/b/r/l` | 0.6 / 0.7 | 0.5 | 12 | 1.5 / 5.6 / 1.5 |
| cape | `cape_1`, `cape_2` (chain) | 0.35 / 0.6 | 0.7 | 45 | 2 / 10 / 11 |

The tail's first tuning (0.3 / 0.55, the cat tail part's own numbers) resonated with the walk's 2 Hz bob and pinned
the tip at its limit; 0.4 / 0.75 moves it to 2.2 Hz and a 21 degree peak. A wide sleeve still lags for a tick or two
in the fastest part of the cut (the shoulder turns about 130 degrees in 0.1 s), so its open end faces the viewer for a
frame: physically right for a whipping sleeve, and the open end could be painted darker (G6b).

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
