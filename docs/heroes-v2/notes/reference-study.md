# Reference study: the owner's style reference models (proportions, budgets, techniques)

Written 2026-11-12 for the art pipeline (HEROES_V2.md sections 4, 13 and 14.2). It answers the owner's feedback on
the rig v2 previews ("the head feels too big and their faces [are] quite similar to each other") with measurements
of the models behind the owner's style screenshots, and turns them into recommendations for our rig. The style guide
in `artgen.md` ("Style pass") is the current state; this file is input for the next pass (re-proportioned forms, hair,
eyes, ears, outfits).

## 0. Sources, licence and method

- **Models**: the eight character files (Blockbench 4.10 `.bbmodel`, Bedrock entity geometry with Bedrock
  animations) of the public "WineFox" model repository by TartaricAcid (the mascot of the Touhou Little Maid and Yes
  Steve Model mods). They are variants of one fox-girl character: New Year (shrine-maiden style, the one in the
  screenshots), school uniform, Taisho-era maid, hanfu, shop clerk, kung fu, astronaut and witch. They were built as
  Yes Steve Model (YSM) player models, which explains the animation names in section 9.
- **Screenshots**: the owner's three images (three body forms on grass, a face close-up, a violin pose).
- **Licence**: the models are CC BY-NC-SA 4.0 with extra terms (no violent or suggestive use, no NetEase
  distribution). **Nothing from them is in our repository or generator**: no geometry, texture, animation data or
  character design was copied, converted or traced. They were used only to measure. This file holds aggregate numbers
  (sizes, ratios, counts) and techniques described in words; our heroes stay original, and none of them is a fox
  shrine maiden or a maid in this costume.
- **Method**: Python (`json` only, run in the session scratchpad, not committed) parsed each file: cube boxes from
  `from`/`to` in the rest pose (cube rotations ignored for bounding boxes, so boxes are good to about 0.5 px), bone
  pivots, cube rotations and inflates, per-face UV rectangles against the texture size, and the animations' bones,
  channels and lengths. Cubes were sorted into parts by the name of their nearest named bone (hair, ears, tail, face,
  head accessories, upper body, arms, skirt, legs). Texture pixels were sampled only to count colours per face. Our own
  numbers come from `tools/herogen` (`build.assemble` on the six style fixtures). Screenshot numbers were read off
  2x crops and carry about 10% error from perspective.
- Units: Bedrock pixels (px, 1/16 block); **H** = the head cube's height; **visual head (Vh)** = chin to hair top;
  **head share** = visual head / height to the hair top.

---

## 1. Findings in brief

1. **The bodies, not the heads, are what is off.** The reference's tall form has a 7 px head cube on a body that
   reaches 38.6 px to the head top (cube share 0.18). With the hair its visual head is 10 px of 41.6 (share 0.24) and
   the body below the chin is **3.2 visual heads** long. Our rig v2 fixtures have shares of 0.35 (adult), 0.42-0.43
   (teen) and 0.50-0.54 (chibi), with bodies of **1.9, 1.3-1.4 and 0.8-1.0 visual heads**. The reference's three
   forms in the screenshot measure about 3.6 (tall), 3.0 (mid) and 2.0 (chibi). Our bodies are 1.6-2.3 times too short
   for their heads.
2. **The reference's head reads big because of the hair, not the face.** The hair mass is 1.9 times the head cube's
   width and rises 0.42 H above it. Inside it sits a small face (7x7 px) with low, wide-set eyes. Our hair mass is about
   1.2 times our 10 px head, so our heads read as a big face with a thin hair shell.
3. **Many more cubes.** The character models use 276-471 cubes (the witch variant has 1342, but about 930 of those
   are three copies of a staff with glowing rings). Hair alone uses 87-116 cubes, 18-32% of each model. Our fixtures
   use 54-76 cubes in total, with 22-31 for hair.
4. **Almost everything is rotated.** 63-80% of the reference's cubes have a rotation: hair strands about 10 degrees,
   skirt panels about 15, shoulders 55-80, and every ear and tail cube. Our fixtures have 0-4 rotated cubes each. That,
   more than the texture, is why ours look boxy.
5. **Detail is geometry, not texels.** Everything is mapped at 1 texel per pixel, the face included (7x7 texels). The
   fine face detail is built from cubes: four stacked sub-pixel cubes per eye and mouths made of 0.1-0.7 px slivers.
   The 256x256 textures are only 19-24% used (12-15k texels), 400-900 distinct UV rectangles per model are a single
   texel (flat colour), and 1-48% of faces share a UV rectangle with another face.
6. **Painting is flat.** Hair faces have a median of 2-3 colours, and the whole hair uses a palette of 5-11 tones.
   Skirt faces have 1-2 colours. The volume comes from the geometry and Minecraft's per-direction face shading, not from
   painted gradients or strand lines.
7. **Expressions are toggled cube groups, not texture frames.** Mouth and face variants hide inside the head and are
   pushed out by 1 px (or scaled from 0 to 1), chosen by a variable. A blink squashes the eye group to zero height for
   about 0.1 s while the dark lash bar drops 1.5 px to draw the closed lid.
8. **Animations**: 28-145 per model. About 25 are core (idle as a 24-30 s loop with baked variations, walk 1.0 s, run
   0.67 s, jump 0.75 s, hurt 0.5 s, death 1.0 s held, swing 0.375 s, sit, sleep, swim, climb, fly). On top of those
   come per-item hold, use and swing sets, 8 emote slots, and up to 16 always-on parallel layers: blink, ear twitch,
   hair and tail physics, eye gaze, expression select.
9. **All eight variants share one head**: the same hair (87 cubes in six of them), ears (22 in all eight), tail (17
   in seven) and face (46 cubes in four, the same 12 eye cubes in all). The reference varies its outfits, not its
   heads. Our 200 different characters need variety in the head
   itself: hair silhouette, face parameters and headwear (section 11.4).

---

## 2. The models measured

"Character" cubes leave out a hidden alternative form (a small fox, 13-88 cubes, swapped in by scaling) and GUI
backdrops.

| Variant | Character cubes | Bones (with cubes) | Texture | Animations | Notes |
|---|---|---|---|---|---|
| New Year (shrine-maiden style) | 323 | 138 (78) | 256x256 | 85 | the outfit in the owner's screenshots |
| School uniform | 335 | 113 (71) | 256x256 | 96 | side hat (31) and flower (39) |
| Taisho maid | 454 | 195 (131) | 2 x 256x256 | 103 | 7 whole-face expressions, mask (55), skirt as 12 bone chains |
| Hanfu | 471 | 116 (74) | 512x512 | 28 | 117-cube layered long skirt; texture 7% used, still 1 texel/px |
| Shop clerk | 276 | 100 (59) | 2 x 128x128 | 55 | the leanest; texture 78% used |
| Kung fu | 420 | 154 (96) | 6 x 256x256 | 60 | |
| Astronaut | 513 | 149 (88) | 256x256 | 145 | helmet 123, hair 116 |
| Witch | 1342 | 291 (224) | 6 x 256x256 | 140 | about 930 cubes are three staff copies with glowing rings |

The New Year, school uniform, Taisho maid and hanfu variants were measured in detail. The shop clerk serves as the
lean example, and the rest only for counts. Extra textures in a file are colour variants on the same UV layout
(the kung fu variant has six): every face maps the first texture.

---

## 3. Proportions

### 3.1 The reference model (tall form; New Year, school uniform, maid and clerk share this body)

| Measure | px | in H (7 px) | share of height to head top |
|---|---|---|---|
| Head cube | 7 x 7 x 7 | 1 | 0.18 |
| Height to head top / hair top / ear tips | 38.6 / 41.6 / 43.4 | 5.5 / 5.9 / 6.2 | 1 |
| Visual head (chin to hair top) | 10.0 | 1.42 | head share 0.24 |
| Legs (hip pivot to ground) | 18.9 | 2.7 | 0.49 |
| Hip pivot to shoulder pivot | 10.2 | 1.45 | 0.26 |
| Shoulder pivot to chin (neck included) | 2.6 | 0.37 | 0.07 |
| Arm, shoulder pivot to hand bottom | 14.3 | 2.0 | hand at mid-thigh; arms rest 15-18 degrees outward |
| Torso core width (chest box) | 5.0-5.1 | 0.73 | waist band 8-8.8; sloped shoulder slabs widen it |
| Shoulder span (outer arm edges) | 11.1 | 1.6 | |
| Upper arm / forearm | 3.05 x 3.6 / 3 x 3.6 | 0.44 x 0.51 | thick limbs |
| Hand | 3 x 3 x 3 | 0.43 | a chunky "mitten" cube |
| Thigh / shin | 3.4 x 4 / 3.35 x 4 | 0.49 x 0.57 | |
| Foot (shoe shell) | 4 wide x 7 deep x 2-5 | 0.57 x 1.0 | wider and much longer than the leg |
| Hair crown mass (w x d, top above head) | 13.4 x 10.9, +2.95 | 1.9 x 1.55, +0.42 | hair is 1.2x the shoulder span |
| Long hair reaches | the hip (y 19.5) | | |
| Ears (height, rise over head top) | 6.6, +4.7 | 0.94, +0.68 | about 1.8 px clear of the hair |
| Tail (cross-section, length along the curve) | 6.6, about 30 | 0.94, 4.3 | as wide as the head, as long as legs plus torso |

The hanfu variant uses a second body with a torso 1.7 px longer (head top 40.4, 5.8 H) and shorter arms (hand at the
hip). The absolute in-game scale of these models was not checked (38.6 px would be 2.4 blocks at 1:1). Only ratios are
used below.

### 3.2 The three forms in the owner's screenshot

| Form | Visual head share | Body below chin, in visual heads | Notes |
|---|---|---|---|
| tall | 0.22 | 3.6 | same as the model within the error (model: 0.24 and 3.2) |
| mid | 0.25 | 3.0 | |
| chibi | 0.32 | 2.0 | hands just below the hip line |

The heads look the same size on all three; the forms differ below the neck. Rig v2 already follows this rule (one head
for every form).

### 3.3 Ours (rig v2 style fixtures, measured with `build.assemble`)

| Fixture (base) | Chin (head_y) | Hair top | Visual head | Head share | Body below chin, in Vh | Cubes (hair) | Rotated cubes |
|---|---|---|---|---|---|---|---|
| Maren, Garrick (adult) | 23 | 35.25 | 12.25 | 0.35 | 1.88 | 76, 65 (22) | 4 |
| Rowan (teen_m) | 18 | 31.0 | 13.0 | 0.42 | 1.38 | 66 (23) | 1 |
| Wren (teen_f) | 18 | 31.75 | 13.75 | 0.43 | 1.31 | 54 (22) | 0 |
| Tobin (chibi_m) | 13 | 26.25 | 13.25 | 0.51 | 0.98 | 66 (28) | 0 |
| Tansy (chibi_f) | 13 | 28.5 | 15.5 | 0.54 | 0.84 | 74 (31) | 4 |

Head cube 10 px on every form (cube share 0.30, 0.36, 0.43). In head units, our adult's legs are 1.4 H against the
reference's 2.7 H, its torso 0.7 H against 1.45 H, its arms 0.9 H against 2.0 H, and its limbs 0.25-0.35 H thick
against 0.44-0.57 H.

---

## 4. Cube budgets per part (reference)

| Variant | Hair | Ears | Tail | Face | Head accessories | Upper body | Arms, sleeves, hands | Skirt, apron | Legs, shoes | Total |
|---|---|---|---|---|---|---|---|---|---|---|
| New Year | 89 | 22 | 17 | 46 | 22 (hair bow) | 52 | 14 | 39 | 20 | 323 |
| School uniform | 87 | 22 | 17 | 46 | 70 (hat, flower) | 39 | 12 | 20 | 20 | 335 |
| Taisho maid | 87 | 22 | 17 | 118 | 55 (mask) | 40 | 34 | 43 | 36 | 454 |
| Hanfu | 87 | 22 | 17 | 46 | 0 | 112 | 22 | 117 | 46 | 471 |
| Shop clerk | 87 | 22 | 17 | 46 | 0 | 25 | 27 | 20 | 30 | 276 |

The head cube and neck add 2 to each total. The face counts are eyes 12 (both eyes and lash bars) plus mouths 34; the
maid adds 77 cubes of whole-face expressions. Other observations:
- Up to 122 cubes per model have a side under 0.5 px: mouth slivers, frills, eye parts. (The maid has 1; it gets the
  same effect with negative inflate.)
- 10-201 cubes per model are inflated. Negative inflate (-0.2 to -0.35) shrinks a cube of 1 px or more below 1 px
  while its faces still map whole texels. A tiny positive inflate (0.005-0.01) stops overlapping coplanar pieces from
  z-fighting.
- 100-195 bones per model: wrappers, locators and chain segments.

---

## 5. Construction techniques

### 5.1 Hair (87-116 cubes, the largest part)

- **Cap**: five slabs 1 px thick (top, back, two sides, a front-top lip) form a shell 0.5 px outside the head cube.
  The face side stays open.
- **Crown mass**: about 30-40 strand cubes. Strands are 1 px thick, 2-3 px wide and 2-7 px long. About 70% of them
  are rotated 4-80 degrees (median about 10) so they fan outward and backward from the crown. The top reads round, the
  mass ends up 1.9 H wide, and the hair top sits 0.42 H above the head.
- **Bangs**: 8 cubes, 1-2 px wide, 2 px long and 1 px deep, tilted 7-12 degrees in alternate directions so the tips
  zigzag. They cover the forehead and the brow line. One centre strand dips between the eyes down to the eye tops;
  the side ones end on the lash bar.
- **Side locks** (face framing): 10-12 cubes per side in a stepped taper (1 px wide, 1-4 px per step, 1-2 px deep),
  about 9.6 px long from the temple to the shoulder, tilted 3-17 degrees, each side on its own bone.
- **Long hair**: three chains (back, left, right), each with 2 bones and 3 cubes per bone. Each bone has one wide
  panel 1 px thick (back panel 9 x 7 px, 1.3 H wide) plus 2-3 strands. The side chains flare outward by 11-32 degrees.
  The chains hang to the hip and are driven by physics.
- **Painting**: flat, 2-3 tones per face and 5-11 in the whole hair: light top faces, darker undersides, darker tips.
  There are no painted strand lines; the cube edges are the strands.

### 5.2 Face (46 cubes, 118 with whole-face expressions)

- The head front is plain skin: a 7x7 texel face with 4 soft tones (a light upper area, a blush tone). No features are
  painted on it.
- **Eye** (per eye, 4 cubes, each front face 0.01-0.05 px in front of the one below): a 2x2 px white (2 texels, one
  shaded), an iris 1.4 x 2 px in two texels (dark saturated top, light bottom), a pupil of about 0.55 x 0.9 px (one
  dark texel) and a highlight of about 0.5 x 0.3 px (one white texel). The eye is 0.29 of the face wide and 0.29
  high. Its centre sits 0.29 of the head height above the chin, so the eyes are low. The gap between the eyes is
  2.8 px (0.4 of the face), and the eyes reach almost to the face edges.
- **Lash bar** (called "eyebrow" in the file, 2 cubes per eye): a dark navy bar 2 px wide straddling the eye top, plus
  a 1.1 px outer flick a step lower that overhangs the face edge by about 0.9 px. There are no separate brows; the
  bangs cover where they would be, and the lash bar does their expressive job (it tilts 5 degrees for sad and drops for
  closed eyes).
- **Mouth**: tiny, 1-2 px wide, 0.3-0.8 px above the chin, drawn with 3-17 thin cubes (0.1-0.7 px wide, 0.15 px
  tall, rotated to form curves) per mouth shape: smile, small smile, laugh, surprise, fear. There is no nose.
- The result is a classic chibi-anime face: a big forehead under the bangs, big low eyes far apart, and a dot of a
  mouth just above the chin.

### 5.3 Ears (11 cubes per ear)

Each ear is a wedge built from 6-7 stacked slabs 1-1.3 px thick whose depth steps down from about 7 px to 3 px, then
tilted as a group. On top of that come a flat inner-ear plate (0.03 px thick) in a light colour, a thin fur plate and
two dark tip cubes. The ear is 0.94 H tall and rises 0.68 H over the head top, standing on the top corners and
overhanging the head side by about 0.3 H. A parallel animation flicks one ear 24 degrees for 1-2 frames every 4 s.

### 5.4 Tail (17 cubes on a 7-bone chain)

Segment boxes are about 6.6 x 5.7 x 6-8 px, nearly a head-cube each. Every box is rotated about the chain, so 2-3
overlapping boxes round the cross-section. The tail tapers through 5 x 4 x 4.5, 4 x 3 x 3 and 2 x 2 x 2 to the tip.
It rests tilted 32.5 degrees and is about 30 px long along its curve: in the rest pose it spans 28 px in height
and 24 in depth, hanging from the lower back to the ground and curling 21 px behind. It is painted with many tones
(a fur gradient plus a light tip). Physics variables drive it, and authored keys add to that (when hurt, the tail
swings up and puffs to scale 1.1).

### 5.5 Upper body (25-112 cubes)

A narrow torso core (5 px, 0.73 H) with **sloped shoulders**: thin slabs rotated 55-80 degrees round the shoulder
line instead of leaving a box corner. A waist band or obi is 8-8.8 px wide. The top garment is layered panels about
1 px thick (collar, crossed lapels, front panels). A chest or waist bow has 13-15 cubes: a 2x2x2 knot, two 2.4 x 1.5
loops, and two tails on 2-bone chains.

### 5.6 Sleeves and hands (12-34 cubes for both arms)

The arm core is 3 x 6-7 x 3.6. A bell sleeve is three slabs fanned outward at about 20, 40 and 65 degrees, which
makes it about 2.5 times the arm width (7.5 px). A cuff band (5.4 x 2 x 4) is tilted 7.5 degrees, and a frill row is
six small cubes with negative inflate. The 3 px hand cube shows under the sleeve. The hanfu sleeves hang 5 px wide in
11 cubes per arm.

### 5.7 Skirts (20-117 cubes)

- **Maid**: 12 radial panels (front, back, left, right, plus a front overlay). Each panel is a **3-bone chain** (hip,
  middle, hem) with one 1 px slab of 3-7 x 4-8 px per bone: 43 cubes on 16 bones. The skirt flares from about 8 px at
  the waist to 18.4 px at the hem (2.3x) and ends below the knee. Walk, run, jump (about 40 skirt bones keyed), sit
  and sleep bend the chains.
- **Hakama**: front and back aprons of 1.04 px pleat panels rotated 9-16 degrees (39 cubes), ending at the knee.
- **School skirt**: 20 cubes in four panel groups, ending at mid-thigh.
- **Hanfu**: 117 cubes of layered panels 1.45 px thick down to the ankles.

### 5.8 Legs and shoes (20-46 cubes)

The legs are plain boxes. The shoes are shells of 6-14 cubes per foot: a sole slab, a toe cap, a heel, and 1 px
straps or sandal thongs. Each shoe is 0.3-0.6 px larger than the leg all round and about 7 px deep.

### 5.9 Accessories and tricks

- Head accessories cost 13-123 cubes: a mask worn on the side of the head (55), a witch hat (86), a flower (39), a
  side hat (31), a helmet (123). Accessories carry much of each variant's identity.
- A bone name prefix (`ysmGlow`) marks emissive parts: glowing irises, glowing bows, magic rings.
- An alternative form (a small fox, 13-88 cubes) sits under the root. One variable scales the body to 0 and the fox
  to 1.
- "Molang bones" are bones with no cubes whose rotation channel carries script assignments: a way to run physics
  variables every frame.
- In-hand props are model parts that animations show or hide: a staff, an instrument (the violin screenshot), guns.

---

## 6. Bone hierarchy and naming

```
Root ─ AllBody (whole body; scaled to swap in the fox form)
       ├─ UpBody (waist, about 19.7) ─ UpperBody (chest, about 24)
       │   ├─ AllHead (neck) ─ Head ─ Eyes ─ EyeBrow (L/R lash bars), Eyelid (L/R eye ─ iris ─ pupil, highlight)
       │   │                         Mouth (one group per mouth shape) / Expression_1..7 (whole-face variants)
       │   │                         Hair ─ BaseHair, SideHair L/R, Bangs, LongHair (2-bone chains: back, left, right)
       │   │                         Ear ─ L/R; accessories (mask, hat, flower); shoulder locators
       │   ├─ Arm ─ L/R Arm ─ ForeArm ─ Hand ─ HandLocator (+ waist, sheath and blade locators)
       │   ├─ Tail ─ Tail2 ... Tail7
       │   └─ skirt panel groups ─ 3-bone chains
       ├─ DownBody ─ L/R Leg ─ LowerLeg ─ Foot
       └─ locators: Backpack, Elytra, Rifle, Pistol, waist L/R
```

- **Wrapper bones** with `M` and `F` prefixes (`MAllBody`, `MUpBody`, `MHead`, `MTail`, `MBangs`, and
  `FLongHair` ─ `MLongHair` ─ `LongHair`) share their child's pivot. Several animation layers write the same bones
  (the main animation, the parallel physics layer, procedural head look), and a later layer overrides an earlier one
  per bone and channel. With a wrapper, each layer owns its own bone and the rotations compose. 11-32 wrappers per
  model.
- **Locators** (9-14 per model) are where items, sheathed weapons, guns, a backpack, the elytra and shoulder pets
  attach.
- Names are fixed by the YSM convention (`Head`, `LeftArm`, `RightForeArm`, `LeftLowerLeg`, ...), so shared
  animations play on every variant. Rig v2 uses the same idea with its own names.
- 100-195 bones per model, 59-131 of them with cubes.

---

## 7. Expressions and blinking

| What | How the reference does it |
|---|---|
| Blink | Each eye's whole group (white, iris, pupil, highlight) is scaled to 0 in height over 0.083 s, held 0.083 s and reopened over 0.083 s (about 0.25 s in all), every 3-6.8 s in a looping parallel layer. The lash bars drop 1.5 px at the same time and become the closed lid line. |
| Mouth shapes | 4-5 mouth groups sit 1 px **behind the face plane, inside the head** (invisible). An animation sets `position.z = -1` to push the wanted one out. idle shows the smile, hurt the surprise mouth, low food the fear mouth, sleep the small smile. |
| Whole-face expressions (maid, kung fu, astronaut) | 7 groups of 3-27 cubes (alternative eyes and mouths, sweat drop and anger marks on the head) chosen by a variable `v.exp`: the chosen group is scaled to 1 and pushed out, the others are scaled to 0, and the default mouth group is scaled to 0 while an expression shows. |
| Brows / mood | The lash bars move down 0.2-1.5 px and roll 5 degrees for sad or hungry; the iris is scaled to 0.7 for worried. |
| Gaze | The iris shifts with head yaw and pitch (yaw/180 px, pitch/720 px) and the pupil, its child, adds yaw/360 and pitch/360 more, so the eyes lead the turn. |
| Sparkle | The highlight jitters sideways (a sine of the animation time) during part of the blink loop. |
| Texture frames / UV offsets | **Not used.** One texture per skin; the extra textures are colour variants. |

Cost: 46-118 cubes per face. With texture frames, our design (HEROES_V2 4.6) gets 12 eye, 8 mouth and 4 cheek frames
from 3 planes.

---

## 8. Texture and texel density

| Measure | Reference | Ours (rig v2) |
|---|---|---|
| Texel density, body | 1.0 texel/px (median 0.83-1.11 per part) | 1 |
| Texel density, face front | 1.0 (7x7 texels on 7 px) | 2 (20x20 on 10 px) |
| Effective face detail | sub-pixel cubes down to 0.3-0.5 px with one texel each (about 2-3x) | 2x texels |
| Hair, ears, tail | 1.0 | 2 (head parts) |
| Texture size / texels actually used | 256x256 / 12-15k (19-24%); 128x128 / 12.8k (78%); 512x512 / 19.5k (7%) | 256x128, 41-51% painted |
| Faces per model / distinct single-texel UV rectangles | 1650-2950 / 400-900 | all faces painted as islands |
| Faces sharing a UV rectangle | 1-48% (maid 1425 of 2940) | none |
| Colours per face (median) | hair 2-3, skirt 1-2, torso 1-2; tail 3-4.5 (fur) | painted ramps, strand lines every 4 texels |

The reference's real texel need is about a 128x128 texture. Its 256 and 512 sizes are mostly empty space, not a
higher density.

---

## 9. Animations

Counts per model: 28, 55, 60, 85, 96, 103, 140, 145. Grouped (union over the eight files):

| Group | Names (lengths) |
|---|---|
| Locomotion | `idle` (24-30 s loop with baked weight shifts and head turns), `idle2` and `new_idle_1-4` (rare idles, 6-24 s), `walk` (1.0 s), `run` (0.67 s), `sneak`/`sneaking`, `jump` (0.75 s), take-off, fall (1.0 s loop), small landing (0.12 s), landing roll (0.58 s), sudden stop (1.25 s), `swim` (1.33 s), `swim_stand`, `climb`/`climbing` (1.0 s), `ladder_up/down/stillness`, `fly`, `elytra_fly`, `hover` |
| Rest and riding | `sit`, `sleep`, `chair`, `ride`, `ride_pig`, `boat`, vehicles |
| Hurt and death | `attacked` (0.5 s: lean back, surprised mouth, tail swings up and puffs), `death` (1.0 s, held) |
| Combat | `swing_hand` (0.375 s), `attack_empty`, `sword_attack_01-03` (1.0 s each, a combo), `sword_idle_attack_01-03`, `sword_jump_attack` (0.54 s), `swing:<item>` (sword, spear, mace, bow, fishing rod, potion), `hold_mainhand:<item>` (sword, axe, pickaxe, shovel, hoe, bow, crossbow, shield, spear, mace, fishing rod, potion; long holds), `use_mainhand:<item>` (bow draw, crossbow, shield block, eat 1.6 s, drink, spyglass, brush, goat horn), the same for the off hand |
| Emotes | `extra0-7` (the emote wheel), `dance1-4` (up to 36 s, about 10k baked keys), `action3-8`, `beg`, `focus`, `game_win`, `game_lost` |
| Always-on layers | `pre_parallel0-7`, `parallel0-7`: blink (3-6.8 s loop), ear twitch (4 s), hair, tail and coat physics (Molang springs), eye gaze, expression select, part toggles (mask), hiding the alternative form |
| Mod integrations | `tac:*` (33 gun animations), `carryon:*` (4), vehicles; `gui`, `head:default` |

How big they are (maid variant): walk keys 28 bones (436 keys); idle 23 bones (837 keys over 30 s); jump 60 bones
(the skirt chains); sneak 67; death 17; hurt 16 (7 of them the tail chain). The tail, hair and skirt move in most
locomotion animations.

---

## 10. The three screenshots

- **Three forms on grass**: one outfit and hairstyle on three bodies; the heads look the same size and the forms
  differ below the neck (numbers in 3.2). The hair mass is about 1.9 times the face width, and the ears add about 25%
  height above the hair. The wide red sleeves end at upper-thigh height on the tall form (at the hip line on the
  chibi), with a white cuff and pink 3 px hands. There is an obi with a clasp and a big bow, a white apron over a dark
  hakama ending at the knee, and white socks in sandals with red straps. The tall form's legs below the hakama are
  about a quarter of its height (0.23 in the screenshot, 0.29 in the model).
- **Close-up**: a plain skin face; 2x2 eyes under a thick dark lash bar with an outer flick; a two-tone red iris
  (dark top, orange bottom) with a white highlight; a dot of a mouth, no nose. The bangs are chunky 1 x 2 px strands in
  3-4 gold tones in a zigzag. The side locks hang in front of the shoulders past the chin. The ears have dark tips and
  a white inner fur plate, a polearm hangs on the back, and each sleeve is about as wide as the face.
- **Violin**: a prop held in both hands, the head tilted onto it. The tail is huge (its cross-section about the
  torso's width) and curls from the lower back to shoulder height, layered orange with darker bands and a cream tip.
  The long hair sweeps back over it in strand bundles. The body is visibly long against the head, about 4.5-5 visual
  heads in all.

---

## 11. Recommendations for our rig

### 11.1 Proportions per form (rig v3, PROPOSED)

One head cube for every form, as in rig v2 and the reference, but **8 px instead of 10**. The bodies grow to the
reference's lengths, and the whole model is drawn at a **render scale of 0.75** (instead of 15/16), so the adult
stays about 1.9 blocks tall and passes 2-block doorways. With an 8 px cube plus about 2.5 px of hair, the visual head
is about 10.5 px, or 7.9 world pixels (1/16 block) at 0.75. That is about the size of a vanilla player's head (8 px
drawn at 15/16 = 7.5), so the heroes get a vanilla-sized head on a longer body. (An 8 px head at 0.75 equals a 6 px
head at 1.0. The 0.75 scale keeps the parts library in whole and half pixels at the reference's granularity: 1 px
strands, 0.5 px texels on the face.)

| Form | Chin (head_y) | Head top | Hair top | Head share (target, reference) | Body below chin | Legs (hip_y) | Shoulder pivot | Hand bottom | Height at 0.75 |
|---|---|---|---|---|---|---|---|---|---|
| adult | 33 | 41 | 43.5 | 0.24 (0.22-0.24) | 3.1 Vh | 19 (thigh 9, shin 8, ankle 2) | 30 | 15 (mid-thigh) | 1.92 blocks |
| teen | 30 | 38 | 40.5 | 0.26 (0.25) | 2.9 Vh | 16.5 | 27 | 13 (upper-mid thigh) | 1.78 blocks |
| chibi | 21 | 29 | 31.5 | 0.33 (0.32) | 2.0 Vh | 10 | 18.5 | 8.5 (just below the hip) | 1.36 blocks |

- Shoulder pivot to chin: 3 px (adult, teen), 2.5 (chibi), neck included. Hip to shoulder: 11, 10.5, 8.5.
- The rest pose or the idle pose holds the arms 10-15 degrees out (an A-pose, as the reference's 15-18 degrees), so
  wide sleeves clear the skirt and the stance reads softer.
- The hitbox follows from `visual.json` (`height_px` x scale); eye height = (head_y + 2.75) x scale. A2 must check two
  things: items drawn at `item_r`/`item_l` would also shrink to 0.75 (they may need a 1/0.75 counter-scale to keep
  vanilla tool size), and vfx locators move with the scale.
- If the owner prefers bigger heads than the reference, raise the share by shortening the bodies, never by growing
  the head cube: an adult at 0.27 has its chin at about 28.5 (legs 16, torso 9.5).

### 11.2 Widths (standard build, reference ratios times 8 px; female / male)

| Base | Chest core | Waist | Hips | Upper arm | Leg | Hand | Shoe shell |
|---|---|---|---|---|---|---|---|
| adult | 6 / 7.5 | 5 / 6.5 | 6.5 / 7 | 3 / 3.5 | 3.5 / 4 | 2.5-3 cube | 4 x 6.5 |
| teen | 5.5 / 6.5 | 4.5 / 5.5 | 6 / 6.5 | 2.75 / 3 | 3 / 3.5 | 2.5 | 3.5 x 6 |
| chibi | 5 / 5.5 | 4.5 / 5 | 5.5 / 6 | 2.5 | 2.75 / 3 | 2.5 | 3.5 x 5.5 |

Shoulders get sloped slabs (section 5.5); the hair mass should be about 1.1-1.2 times the shoulder span on the adult and
wider than the shoulders on the chibi.

### 11.3 Face at 8 px (16x16 texels at double density)

Keep the double-density face, now 16x16 texels on 8 px. Rows, top = 0:

| Rows | Height above chin | Content |
|---|---|---|
| 0-5 | 8.0-5.0 px | forehead under the bangs (bang tips end on rows 4-6; one centre strand may dip to row 7) |
| 6-7 | 5.0-4.0 | lash bar, 2 rows, in a dark tone of the hair hue (or near-black navy); the outer flick drops to row 8 and may overhang the face edge by one texel onto a side lock |
| 8-12 | 4.0-1.5 | eye body: whites only beside the iris (1 texel); iris 3-4 texels, dark saturated top and light bottom (2-3 tones); 2x2 pupil; 1-2 white highlight texels |
| 13 | 1.5-1.0 | lower lash texel at the outer corner, blush |
| 14 | 1.0-0.5 | mouth line, 2-4 texels wide (open mouths use rows 13-15) |
| 15 | 0.5-0 | chin |

- Eyes 5 texels wide (0.31 of the face) with a gap of 4-7 texels (a variety lever; the reference's is 0.4 of the
  face) and outer margins of 0-1 texel. The eye centre sits about 0.34 of the head height above the chin (ours today:
  about 0.45, and gaps of 0.1-0.3).
- Separate brows are optional: a lash-bar-only face (the reference's look, bangs over the brow line) or 1-row brows on
  rows 4-5 for stern or older characters.
- Plain skin on the head front with 2-3 soft tones; no nose.

### 11.4 Making faces distinct (the "faces quite similar" feedback)

Our six faces share one eye structure, position and size, one mouth, and one fringe line over the brows. Only the
iris colour and lash weight change. The reference gives no help here: all its variants share one face. At game
distance a hero is told apart first by head silhouette, then by colours, then by the face. Levers, each a face or
part parameter the generator varies:
1. **Silhouette first**: hair style families with clearly different outlines (twin tails, side tail, bun, braid, hime
   cut, spiky short, long straight, wavy, bob, undercut), headwear (hats, hoods, helmets, masks, horns, hairpins,
   flowers; the reference spends 13-123 cubes here), ears, and the ahoge.
2. **Eye shape templates** (at least 6): round (5-6 rows), almond (4 rows, outer corner up), droopy (lash slopes
   down outward), sharp (narrow 3-4 rows, outer corner up 1-2 rows), sleepy (3 rows under a heavy lid line), wide or
   sparkly (6 rows, two highlights). Widths 4-6 texels.
3. **Placement**: gap 4-7 texels; eye line plus or minus 1 row.
4. **Lash and brow**: weight 1-3 rows; flick up, down or none; lower lash or not; merged or separate brows and their
   angle.
5. **Iris**: hue, 2 or 3 tones, pupil (round, slit, none for a blank stare, a special shape for one or two heroes),
   highlight pattern (one or two, upper outer or upper inner).
6. **Default mouth**: small smile, flat line, cat mouth, fang, slight frown, open.
7. **Marks**: freckles, a beauty mark, a scar across the eye or cheek, face paint, blush strength, eye bags; glasses
   or an eyepatch as 6-10 cubes.
8. **Skin tones** and the face-front shading.
9. A **distinctness check** (HEROES_V2 14.2 already lists distinctness NOTEs): compare each pair of heroes on (a) the
   calm 16x16 face after palette normalisation and (b) the front and side head silhouettes (masks of the head parts),
   and warn when both are under a threshold.

### 11.5 Cube budget and level of detail (PROPOSED, with the reasoning)

**Budget**: at most **300 cubes** per hero (validator error), typical 200-280, and at most **100 bones**. The
reference's 300-500 shows what that detail level looks like. We stay at the low end because up to about 50 heroes may
be on screen and GeckoLib transforms every vertex on the CPU each frame. Three LOD tiers:

| Tier | When | Visible cubes | What stays |
|---|---|---|---|
| full | the nearest 12 heroes within 20 blocks | at most 300 (typical 200-280) | everything |
| LOD1 (`fine_` hidden, as in 4.9) | beyond 20 blocks, or not among the nearest 12 | at most 110-120 | body, cap and big locks, ears, tail chain, sleeve and skirt cores, weapon |
| LOD2 (new, for example a `mid_` prefix or a core whitelist) | beyond 40-48 blocks, or not among the nearest 30 | at most 40 | body boxes, head, cap, 4-6 big locks, ear and tail blocks, sleeve and skirt shells |

At 20 blocks a 1 px strand at 0.75 scale covers about 1.8 screen pixels at 1080p, and at 40 blocks about 0.9, so
LOD1 hides detail that no longer resolves and LOD2 keeps only shapes of 2-3 px or more.

**Frame cost estimate.** Our own documents give a guess, not a measurement: HEROES_V2 11 has 0.1-0.15 ms for 140
cubes, and `research-animation.md` 10 says several ms for about 300k vertices. That is roughly 0.4-1.1 microseconds
per visible cube (24 vertices).

| Scene | Visible cubes | Estimate |
|---|---|---|
| 50 heroes at a reference-like 450 cubes, no LOD | 22,500 | 9-25 ms: unplayable |
| 50 heroes, proposed: 12 full x 280 + 18 LOD1 x 110 + 20 LOD2 x 40 | 6,140 | 2.5-6.8 ms |
| 20 heroes in a town: 12 x 280 + 8 x 110 | 4,240 | 1.7-4.7 ms (11's target is 3 ms; tune N) |
| 50 heroes at today's rig v2 (70 cubes) | 3,500 | 1.4-3.9 ms |

So the proposal costs about 1.75 times today's worst case for about four times the detail on the heroes the player
looks at. Two further points:
1. Leave out faces that can never be seen (inner faces of the hair cap, faces glued to the head or the body, the
   backs of panels lying on cloth). This saves an estimated 15-30% of quads. **Unverified**: confirm that GeckoLib 4.9
   skips a face with no UV entry before relying on it.
2. The per-hero measurement in HEROES_V2 14.7 (lineups of 1, 16 and 64) decides the final N and distances. If the
   numbers are high, tighten the LOD rules, not the art.

Share of the 300 (typical 260): hair 60-100 (half of it `fine_`), face 3 planes plus 0-6 cubes (marks, glasses,
fang), head and neck 2, ears 0-24, tails 0-20 each (multi-tail heroes up to 45), upper body 20-40, arms and sleeves
16-40, skirt or coat 0-45, legs and shoes 16-30, head accessories 0-40, weapon in hand plus sheathed copy 10-40 (the
hidden copy costs no frame time), back items, capes and wings 0-30.

### 11.6 Part recipes (numbers for the 8 px head)

- **Hair** (60-100 cubes): cap slabs 1 px thick, 0.5 px outside the head. A crown of 25-40 strands, 1 px thick, 2-3.5
  px wide and 3-8 px long, **rotated 5-30 degrees** (median about 10) outward and back from the crown, in 2 depth
  layers. The mass is 13-15 px wide (1.6-1.9 H), 11-13 px deep and 2.5-3 px over the head. Bangs: 6-10 cubes, 1-2.5 px
  wide and 2-3 px long, tilted alternately 7-12 degrees, tips ending on the lash rows. Side locks: 8-12 cubes per side
  in a stepped taper about 11 px long, temple to shoulder, on their own bones. Long hair: 2-3 chains of 2-3 bones with
  3 cubes per bone, one panel 1-1.3 H wide plus strands, side chains flared 10-30 degrees, down to the hip for long
  styles. Painting: 2-3 flat tones per face, light top faces, dark undersides and tips, **no painted strand lines**.
  Strands of one size and colour share one painted island.
- **Ears** (8-12 cubes per ear): a stack of 1-1.3 px slabs stepping down in depth, tilted as a group, plus a light
  inner plate and dark or light tip cubes. About 7.5 px tall, rising about 5.5 px over the head (2 px clear of the
  hair), on the top corners and overhanging the side by about 2 px.
- **Big tails** (12-20 cubes): a 4-6 bone chain with 2-3 overlapping boxes per bone, rotated about the chain. The
  cross-section is about 7.5 px (0.95 H) and the length 24-32 px along the curve, from a narrow root through a full
  body to a light tapered tip. Rest pose tilted about 30 degrees back. Slender tails keep 3 bones and 1-2 px.
- **Upper body**: a narrow core (0.7-0.8 H), **sloped shoulder slabs** at 55-80 degrees, a waist band, collar and
  lapel panels about 1 px thick, and a bow as knot plus loops plus tails on 2-bone chains.
- **Sleeves** (10-14 cubes per arm): a bell sleeve of three slabs fanned at about 20, 40 and 65 degrees to about 2.5x
  the arm width, a tilted cuff band, an optional frill row of small cubes with negative inflate, and the hand cube
  showing beneath.
- **Skirts and coats** (20-45 cubes): 6-12 panels, each a 2-3 bone chain of 1 px slabs, flaring to about 2-2.3 times
  the waist width at the hem; pleats as panels rotated 9-16 degrees; hem height per style (mid-thigh, knee, below the
  knee, ankle).
- **Shoes**: a shell 0.3-0.6 px larger than the leg, about 6.5-7 px deep (longer than the leg is deep), sole, toe
  cap, heel and straps (4-8 cubes per foot).
- **General**: per-cube rotation becomes a normal tool in the parts format (today 3 uses in the whole library).
  Negative inflate builds sub-pixel pieces whose faces still map whole texels; an inflate of 0.005-0.01 separates
  coplanar overlaps. The clear-face validator (no geometry in front of the eyes) must test rotated corners.

### 11.7 Bones

- Raise the bone budget to **100** (typical 60-90). The reference uses 100-195, and skirt chains alone take 12-24.
- Add **sway wrappers** for chains that get both authored keys and procedural or secondary motion. GeckoLib
  controllers override per bone and channel just like the reference's layers, so `secondary` (sway) and `action` (an
  ultimate that flicks the tail) cannot both drive `tail_1`. A parent `tail_1_sway` with the same pivot fixes that;
  the same applies to hair chains, side locks, skirt panels and ears.
- Longer chains where they show: 4-6 bones for big tails (3 today), 2-3 per skirt panel, 2-3 per long hair chain.

### 11.8 Expressions

Keep the **texture frames on three planes** (HEROES_V2 4.6). The reference's cube faces would cost 46-118 cubes per
hero, 2,300-5,900 cubes for 50 heroes, and give fewer expressions. Borrow its look:
- **Blink and closed frames**: draw the closed eye where the lash bar drops, a thick dark arc about 3 rows lower
  across the eye's middle, rather than a thin line at the top. Keep the 4-tick blink (half, closed 2, half).
- Sad and angry come from tilting and lowering the lash bar (brows when present).
- The eye structure of 11.3: two-tone iris, pupil, one or two highlight texels, whites only beside the iris.
- Optional and cheap: an always-on **ear twitch** in `sec.ears` (one ear, about 24 degrees for 2 ticks, every 3-6 s),
  and a low-health or tired **idle variant** chosen by mood (the reference switches its idle pose and face when food is
  low).
- Not recommended: gaze shift. With one eyes plane it would move the whites too; it needs a separate iris plane
  (2 more cubes) and is low priority.

### 11.9 Animations that matter for NPC heroes

Our planned set (HEROES_V2 4.4-4.5) already covers the reference's core. Priorities, with the reference's lengths as
a starting point:
1. **Every hero (`_core` and the style libraries)**: idle as a long loop (20-30 s with baked weight shifts, a look
   around, an ear flick) instead of a short loop plus rare idles; walk (1.0 s); run (0.6-0.7 s); combat idle; hurt
   (0.5 s: lean back, ears back, tail up, surprised mouth frame); death (1.0 s, held); a 3-hit melee combo per weapon
   style; ranged draw, hold and release; block (enter, loop, hit); work by tool (mine, chop, dig, till, fish cast and
   reel); eat and drink; sit; sleep; swim; climb and ladder; fall and land.
2. **Polish**: a jump take-off and landing (small and roll), a stop skid, crouch, carry, emotes (wave, cheer or
   victory, taunt, sad or defeat, bow, laugh), rare idles (stretch, tail swish).
3. **Always-on secondary layer**: blink (Java), ear twitch, tail, hair, sleeve and skirt sway from speed and turn
   rate. The reference's locomotion animations key the skirt and hair chains (jump keys about 40 skirt bones), so our
   walk, run and jump should key the skirt panels and the long hair too, not leave them all to `secondary`.
4. **Not needed**: vehicles, guns, GUI poses, elytra, board games and props, carrying a player.

### 11.10 Texture and painting

- Default texture **256x256** for heroes with long hair, big tails or many layers; 256x128 still fits small designs.
  Estimate for 260 cubes with 2x head parts: hair about 11k texels, body about 5k, face frames about 3k, tail and ears
  about 3k, about 22k in all and about 29k with packing waste, against 32k in 256x128: too tight.
- **Island dedup** in `uvpack`: cubes with the same size, material, shade and paint parameters paint the same
  pixels, so they share one island (the reference shares rectangles on up to 48% of faces). Small solid details
  (trims, studs, frills, ties) can map a single texel or a small swatch, as the reference's 400-900 single-texel
  rectangles do.
- **Flatter painting**: 2-3 tones per face on hair and cloth, light top faces and dark undersides, and no strand
  lines or barcode-like stripes. Let rotations and Minecraft's face shading give the volume. Keep a richer ramp for
  fur tails and for metal.

### 11.11 What not to take

No part of the reference's character design goes into a hero: no fox girl in a shrine-maiden or maid outfit, no
copied hair arrangement, ear or tail shapes, palette, mask or bow designs. The numbers above are general proportions
of a chibi-anime figure, and the techniques (rotated strands, stacked ear slabs, chained skirt panels, wrapper bones,
layered eyes) are generic modelling methods.

---

## 12. Open questions

- **Owner**: is the reference's tall-form share (0.24) the target for our adults, or should heroes stay a bit
  heavier-headed (0.26-0.27)? Is the 0.75 render scale acceptable? It makes heroes' heads about vanilla-player sized.
- **A1/A2**: does the item layer need a counter-scale under the 0.75 model scale? Does GeckoLib skip faces without UV
  (11.5)?
- **G2 measurement** (HEROES_V2 14.7): the per-cube cost on the Xvfb client and the owner's PC, which sets N (full
  detail heroes) and the LOD distances.
- **Validator**: the clear-face check with rotated cubes; a quad budget next to the cube budget; the distinctness
  check of 11.4.
