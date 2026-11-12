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
| `herosrc/rig/rig_v1.json` | bones (61 with every option, 33 required), pivots, heights, joint limits, UV template, face layout, budgets (72 bones, 140 cubes) |
| `herosrc/bases/*.json` | the six bases (dims) and `_template.json` (body cubes shared by all six) |
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
