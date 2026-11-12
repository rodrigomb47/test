# Reference survey: anime-style blocky models by other creators

Written 2026-11-12 for the rig v2 art direction pass, after the owner's feedback on the rig v2 previews ("the head
feels too big and their faces [are] quite similar to each other") and the owner's request to also look at anime
models made by other people. It complements the WineFox study (the owner's own reference, measured separately) so
that our style is a synthesis of several creators and not one creator's look.

**What this file contains and what it does not.** Numbers measured from public models, and construction techniques
described in prose. No geometry, texture, animation data, pixel pattern or character design from any source is copied,
converted, traced or embedded in this repository or in the generator. Measurements were taken with throwaway scripts
in the session scratchpad. Private orthographic renders, made only so the techniques could be seen, stayed in the
scratchpad and are not committed. Most community anime models are fan art of commercial characters (Touhou Project,
Blue Archive, Senko-san). We learn from how they are built and never from who they depict.

Units: Bedrock model pixels (px; 16 px = 1 block), feet at y 0. "Texels" are texture pixels. "Heads tall" = crown
height (top of the head cube, without hair or ears) divided by the head cube's height. "Head share" is the inverse.

---

## 1. Sources

| # | Creator(s) | Work and URL | Licence (checked) | Format | What it shows |
|---|---|---|---|---|---|
| A | **Gakuto1112** | CatMaid, https://github.com/Gakuto1112/CatMaid | MIT (LICENSE in the repository) | Figura avatar: `.bbmodel` + Lua | An original cat-eared maid on the vanilla player body. 72 cubes, 23 emote animations. Eye, mouth and complexion textures in separate strips at double resolution. Chained tail, tiered skirt. |
| A | Gakuto1112 | SenkoSan, https://github.com/Gakuto1112/SenkoSan | MIT for the avatar files. The character is a fan work of the anime *Sewayaki Kitsune no Senko-san*, whose design belongs to its owners. | Figura | A fox girl with a huge tail made of a few big boxes. 72 cubes, plus 14 costume overlays of 2-30 cubes each. 24 animations, mostly chores and interaction emotes. |
| A | Gakuto1112 | Figura Blue Archive Crafters (FBAC), https://github.com/Gakuto1112/FiguraBlueArchiveCrafters | Custom licence: MIT for the author's own model data and programs. Character designs belong to NEXON Games / Yostar. No commercial use, and the official fan-work guidelines apply. | Figura with a build pipeline | **51 characters built on one shared base** (24 drawn cubes plus a 4-cube camera helper) plus per-character parts, scripts and a build tool. This is the closest analogue to herogen. It shows how a whole cast stays distinct on one body. |
| B | **OvOla2** | An-An avatar, https://github.com/OvOla2/AnAn-Figura-avatar | CC BY-SA 4.0. The character is the AOSC community mascot. | Figura | A rabbit-eared hoodie girl on the vanilla body. 91 cubes, 44 of them hair. Physics ears. Brows and eyes as planes. Hair at 2 texels/px and ears at 4 texels/px on a 1 texel/px body. |
| C | **Touhou Little Maid art team**: Succinum, Pajinyi, Hoishi, ZeniCrow, Paulzzh, Tian_mi (YurisCat is also credited in the README) | TLM default model pack, https://github.com/TartaricAcid/TouhouLittleMaid, branch `default-model-pack`, `assets/touhou_little_maid` | Art CC BY-NC-SA 4.0 (README), code MIT. The characters are fan works of ZUN's Touhou designs. | Bedrock geometry 1.10 (box UV) + JS procedural animations | **166 geometry files in one chibi house style** (138 listed maids, plus props). Painted faces with a blink plane. Detail spent on hats, skirts and wings, never on limbs. The repository owner also publishes WineFox, but these are different artists, and the WineFox entries of the pack were not used. |
| D | **Sazuki**, **Hoishi**, **Succinum**, **Pajinyi** (one model each) | The "authors and credits" pack in the same branch, `assets/authors_and_credits` | CC BY-NC-SA 4.0 (the pack's licence) | Bedrock geometry | Self-portrait original characters, four individual styles: Sazuki (401-cube kemonomimi maid on geta), Clocklalier by Hoishi (55 cubes, bat wings), Fledge by Succinum (125 cubes, peaked cap and military uniform), Pajinyi (152 cubes, mecha parts). |
| E | **YSM built-in team**: 哥斯拉 ("Godzilla", model), 蓝玫瑰 (texture and model), animators 端木一动不动, 甜粽子, 星屑海螺, 艾雷克亚 | Yes Steve Model built-ins, https://github.com/YesSteveModel/YesSteveModel, branch `dev/1.20`, `src/main/resources/assets/ysm/builtin/{default,misc}` | **CC0** per each asset's `ysm.json` (the repository code is Apache-2.0) | Bedrock geometry 1.12 (per-face UV) + Bedrock animations with Molang | Teen and adult proportions (5-5.7 heads). Includes a **male** model (Default Boy). Eyes are built from small cubes. Hair is a mass of 1 px sticks. 50 state animations, 57 per-item arm animations and parallel layers. The WineFox outfits in the same folder were not used. The same team's `qingluka` (TLM `geckolib` pack, credited to godzilla256 and 星屑海螺) is counted here. |

Five independent creators or teams (A-E), plus one model each by three more TLM artists. Their styles cover chibi
(C), teen on the vanilla body (A, B) and adult (E), female and male (E Default Boy, C Rinnosuke, D Fledge). Outfits
include maid, shrine and kimono, witch, school and military uniforms, coats, hoodies, swimsuits and mecha.

**Looked at but not used:**
- Elaina69/Yes-Steve-Model-Repo (3.6k stars). It has no licence, its 1116 files are all encrypted `.ysm` or zip
  files, and it holds mostly fan models of commercial IPs. Not opened.
- KabanFriends/Touhou-Figura-Pack. A port of source C's models to Figura (CC BY-NC-SA). It duplicates C. Not opened.
- Gilgames32/vixen. Its assets are CC BY-SA 4.0, but the characters are furry foxes, not anime. Not opened.
- Monsters & Girls (b_art_ek, GeckoLib, on CurseForge). There is no source repository and no licence stated. Not
  opened.
- LittleMaidModelLoader (SistrScarlet). Under the "LittleMaid licence" (non-commercial), with Java-coded models. Not
  opened.
- Sketchfab and yesstevemodel.github.io could not be reached from this session (their hosts are outside the session's
  allowed network). Blockbench showcases on Sketchfab are therefore not covered.

---

## 2. Proportions

| Model (source) | Head cube (w x h x d) | Crown (px) | Heads tall | Head share | Hip (leg top) y, share | Torso + neck | Arms (w x length), rest pose, hand at | Head / shoulder width | In-game scale |
|---|---|---|---|---|---|---|---|---|---|
| TLM maids, median of 98 models with an 8 px head (C) | 8 x 8 x 8 | 26 | **3.25** (quartiles 3.09-3.25) | 0.31 | 9, 0.35 | about 9 | 2 x 8 (often 2 cubes), straight down, at the hip | about 0.75-0.8 (the head is wider than the shoulders) | 1 |
| TLM Rinnosuke, a male (C) | 8.5 cube | 26.25 | 3.1 | 0.32 | 9, 0.34 | | 4 x 8 | | 1 |
| Sazuki (D) | 7 x 7 x 7 | 25.1 | 3.6 | 0.28 | | | | | 1 |
| CatMaid, SenkoSan, An-An, FBAC (A, B) | 8 x 8 x 8 | 32 | **4.0** | 0.25 | 12, 0.375 | 12 | 3-4 x 12, straight, at the hip line | 0.5 | 1 |
| YSM Alex (E) | 8 x 8 x 8 | 32 | 4.0 | 0.25 | 12, 0.375 | 12 | | 0.5 | 1 |
| YSM Default, female (E) | 7 x 7 x 7 | 40.05 (hair to 43.3) | **5.7** | 0.175 | 21.15, **0.53** | 11 + 2 neck | 3.25 x 13, **A-pose 18 degrees**, hand at y 18 (upper thigh) | 7 / 13.5 = 0.52 | **0.7**, so about 28 px (1.75 blocks) in the world and the head draws 4.9 px |
| YSM Default Boy, male (E) | 7 x 8 x 8 | 40.05 | **5.0** | 0.20 | 18.95, 0.47 | 7, plus a 5 px coat skirt | 3.25 x 15, A-pose 15 degrees, hand at y 17 | 7 / 14.5 = 0.48 | none in its manifest |
| YSM Default Controllers, female (E) | 7 x 7 x 7 | 37.2 | 5.3 | 0.19 | 17.5, 0.47 | | 2.5 x 13, A-pose 13 degrees | | 0.7 |
| qingluka (E) | about 9 | 36.7 | 4.0 | 0.25 | | | | | 1 |
| **Our rig v2 (today)** | 10 x 10 x 10 | 23 / 28 / 33 | **2.3 / 2.8 / 3.3** | 0.43 / 0.36 / 0.30 | 7 / 11 / 14 (0.30 / 0.39 / 0.42) | 5.5 / 6.5 / 8 | at the hip line, upper thigh, mid thigh | about 1.0 on the chibi | 1 |

What the numbers say:
- Every reference form is about **one head-height longer** than ours at the same "feel". Their chibi (TLM, 3.25)
  sits where our adult sits (3.3). Their teen is the vanilla 4.0 and their adult is 5.0-5.7. Our heads are big
  relative to the body, and also in world size: a 10 px head is larger than a vanilla player's 8 px head, while
  YSM's adult head draws at 4.9 px after its 0.7 scale. This matches the owner's "the head feels too big".
- **Authored big, drawn small.** YSM authors its adults at about 40 px with a 7 px head, then scales the whole model
  by 0.7 in game. Detail stays fine, about 0.7 world px per model px, and the figure stays player-sized.
- Legs carry the height: 35% of the crown height on the chibi, 37.5% on the vanilla teen and 47-53% on the adults
  (thigh and shin about equal, plus a 2-3 px foot).
- Arms rest in a slight A-pose (13-18 degrees) on every YSM model. Vanilla-based and TLM models hang them straight.

---

## 3. Cube budgets, textures, quads

| Source | Models | Cubes: min / median / p90 / max | Quads (rendered faces), examples | Textures |
|---|---|---|---|---|
| TLM pack (C) | 166 | 1 / **70** / 173 / 343 | Meiling 83 cubes = 302 quads, Marisa 305 = 1550 | 128x128 (124 models), 64x64 (25), 256x256 (8), 512x512 (6), all at 1 texel/px |
| FBAC (A) | 51 | 28 (the base, 24 drawn) / **90** / p75 118 / 264 | Mika 230 = 1041, the base 28 = 111 | a 64x64 skin for every character, plus small strips (eyes 6 x 24-126, mouths 16x8, halo 34x17) |
| CatMaid, SenkoSan, An-An (A, B) | 3 | 72 / 72 / 91 | 346 / 291 / 487 | CatMaid: five small atlases (main 144x97). SenkoSan: one tall 62x971 atlas holding every costume. An-An: 128x128 body at 1 texel/px, hair 2 texels/px, ears 4 texels/px |
| YSM built-ins (E) | 4 | Alex 47, Boy **156**, Default **172**, Controllers **247** | 936 / 1032 / 1482 | 128x128 (Default, Boy), 256x256 (Controllers), 64x64 (Alex), all at 1 texel/px |
| Credits pack (D) | 4 | 55 / 125 / 152 / **401** (Sazuki) | Sazuki 2394 | 128x128 |
| WineFox (the owner's reference, other study) | | 276-1436 | | 256x256 |
| Our test rig | 1 | 73 (budget 140) | 421 | 256x128 at double density |

Where the cubes go (share of all cubes across a pack):
- **TLM:** body and clothing 22%, the head bone 18% (hats, painted-hair extras, ribbons), skirts 13%, wings 9%,
  named hair 8%, arms 7%, legs 4%, tails 4%, ears 3%, face 2%. Arms are usually 2-4 cubes and legs 1-2.
- **FBAC:** body and clothing 24%, hair 18%, arms 15% (one outlier carries a 192-cube weapon arm), ears 9%, legs 9%,
  head extras 8.5%, halos 5.5%.
- **YSM:** hair is 42-61% of the model (Default 93 of 172, Boy 65 of 156, Controllers 151 of 247). Then skirt or coat
  about 21 each, body 23-31, face 10-16, legs 10-19, arms 6-12.

Budget reading for our NPC case:
- Multi-character packs, the closest thing to our cast, sit at **70-250 cubes**: the TLM median is 70, the FBAC median
  90 and the YSM built-ins 156-247.
- Models above 300 cubes are single-avatar showpieces: Sazuki 401, TLM Marisa 305 and WineFox's 276-1436. They are
  made to be seen one at a time.
- HEROES_V2 §11 estimates 0.1-0.15 ms of client CPU per hero within the 140-cube budget (typical 80-110), roughly
  1 µs per cube, because GeckoLib re-submits every quad of every visible model each frame. At that rate, 50 heroes at 300 cubes cost about 15 ms per
  frame before LOD. At 220 cubes with half of them on `fine_` bones (hidden beyond 20 blocks, §4.9), a crowd of
  10 near and 40 far heroes costs about 10 x 220 + 40 x 110 = 6600 cube-renders, about 6.6 ms.
- **Suggested budget, for the style task to decide:** typical **180-240 cubes**, cap **300**, at least **45% on
  `fine_` bones** (hair sticks, frills, trims, small accessories), a far silhouette of **120 cubes or fewer**, and
  quads at most about 6 x cubes. This reaches YSM's level of hair and outfit detail, where hair makes the silhouette.
  It stays under the showpieces, which are not meant for crowds. The task's 300-500 target fits a leader or boss
  seen alone, so the 300 cap can be raised for leaders only if the client measurement in §11 allows it.
- Spend the cubes the way the packs do: on hair mass, headwear, ears, tails, sleeves and skirts. Not on limbs and
  feet: TLM limbs are 2-4 cubes, YSM feet are 3 cubes, while Sazuki spends 31 cubes on each geta, which cannot be
  seen at NPC distance.

---

## 4. Faces and expressions

| Source | Face | Eyes (each) | Eye centre, height above the chin | Gap between the eyes | Brows | How expressions switch |
|---|---|---|---|---|---|---|
| Gakuto1112 (A) | 8x8 | a **3x3 px zero-thickness plane** on the face, textured at **2 texels/px** (6x6-texel cells) | **0.31** of the face | 2 px = **0.25** of the width | in the eye texture | UV offset from Lua. CatMaid's eye strip is 72x24 (12 x 4 cells). FBAC has one strip per character, 6 texels tall and 24-126 wide, so **4-21 eye frames per character**. A mouth plane uses its own strip, plus a complexion plane (blush, pale). Triggers: damage, low health, eating, action wheel, random blink. |
| An-An (B) | 8x8 | 2x2 px planes | 0.25 | | 3x2 px planes above the eyes | eye-tracking script |
| TLM (C) | 8x8, painted at 1 texel/px | 1-2 px wide, 2-3 px tall, in rows 4-6 from the top, at columns 1-2 and 5-6 | about **0.31** | 2 px = 0.25 | usually none | A `blink` bone holds a zero-thickness 8x8 plane painted with closed eyes, shown by `blink.js`. 8 models swap a hurt face. Otherwise the face is static. The painted **fringe covers rows 0-3**. |
| YSM Default / Boy (E) | 7x7 / 7x8 at 1 texel/px, with eyes built from **cubes** | eye base 1.4 x 2 x 1 px (iris), a **highlight cube** 0.4 x 0.3 to 1.2 x 1.05 px set in front, a 2 x 2 px **eyelid cube** (1 px or 0.025 px deep) | **0.29 / 0.25** | 3 px = 0.43 / 2.8 px = 0.40 | **cubes**: a 2.25-2.3 x 1 px inner piece plus a 1.1 x 1 px outer piece set 0.3 px lower, which makes an angled brow | A **parallel animation layer** (4 s loop) scales the eyelid cubes for the blink and nudges the brows. The eye-base bones follow the look direction through Molang. |
| **Ours** | 10x10 at 20x20 texels | 5-7 texels (2.5-3.5 px) | **0.45** | 2 or 4 texels = **0.1-0.2** | 1 texel rows in the eye frames | 12 eye, 8 mouth and 4 cheek frames by UV |

What the numbers say:
- **References place the eyes low.** In every source the eye centre sits a quarter to a third of the way up the face
  (0.25-0.31), with the brows just above the middle. The fringe or forehead takes the whole top half. Ours sits at
  0.45, with the brows at 0.75. The low-eyes convention leaves a large covered forehead, which is part of what makes
  a big blocky head read as "cute" and not as "big".
- **References space the eyes wider.** Their gap is 0.25-0.43 of the face width, ours 0.1-0.2.
- **How a cast is told apart on identical heads** (FBAC: 51 characters, one base head; TLM: 138 maids, all 8x8 faces):
  1. a different fringe silhouette on every character (centre part, side-swept, jagged, blunt, a covered eye);
  2. eye colour plus **eye shape**: FBAC draws a separate eye set per character, so shapes, highlight positions and
     lash weights all differ, with 3-10 colours inside a 3x3-px eye;
  3. brow shape and angle (YSM's two-cube angled brows);
  4. headwear, ears and halos, which change the head's outline;
  5. side locks framing the face differently.
  Hair colour alone is not enough: TLM faces with the same eye drawing look alike even in different colours.
- Double-density faces (ours, and Gakuto's eye strips) are the rich end of the field. TLM and YSM keep the face at
  1 texel/px. YSM gets sub-pixel detail from tiny cubes (a 0.4 x 0.3 px highlight) instead.

---

## 5. Hair, ears and tails

**Hair**:

| Model | Hair cubes | Median strand (x, y, z) | Thinnest side 1 px or less | Tilted | Median tilt | Depth layers (0.5 px bins) |
|---|---|---|---|---|---|---|
| YSM Default (E) | 93 | 1 x 3 x 1 | 93 of 93 | 69% | 14 degrees | 25 |
| YSM Boy (E) | 65 | 1 x 2 x 1 | 55 of 65 | 71% | 22 degrees | 17 |
| YSM Controllers (E) | 151 | 1 x 3 x 1 | 141 of 151 | 75% | 21 degrees | 39 |
| TLM Marisa (C) | 67 named, plus more under the head | 1 x 2 x 1, with 50 zero-thickness planes | 67 of 67 | 100% | 25 degrees | 15 |
| Sazuki (D) | 102 | 1 x 2 x 1.5 | 70 of 102 | 100% | 28 degrees | 10 |
| An-An (B) | 44: 12 bangs, 21 crown bits of 1 x 1 x 1, 4 side, 7 back | | | | | |
| Ours (style guide) | | 2-2.5 wide x 0.75-0.9 deep | | untilted | | 2 |

Techniques:
- **Hair is a mass of thin sticks.** Each strand is 1 px wide and 1 px deep, 2-3 px long. 70-100% of the strands are
  tilted 14-28 degrees, at 10-40 different depths. A shell 0.5 px outside the head (8 px around a 7 px head on YSM)
  carries the base colour.
- The silhouette comes from the tilts and the staggered lengths, not from wide slabs. Wide untilted slabs side by
  side give the "barcode" stripes our previews show.
- Long hair hangs from a bone with physics. YSM rotates `FLongHair` and `MLongHair` from velocity through Molang.
  Figura uses physbone scripts.
- TLM keeps most of the hair painted on the head and adds a few strands, braids or planes only where they change the
  outline. That is a cheap option for distant or minor characters.

**Ears**:

| Model | Cubes per ear | Height | Construction |
|---|---|---|---|
| CatMaid | 4 | 3 px | a small stepped cat ear |
| SenkoSan | 7 | 6.2 px | a stepped pyramid, 1.75 x 2 x 1.5 px at the base |
| An-An | 5 | 7 px | a long rabbit ear, 2 px wide, on a physics bone |
| Sazuki | 11 | 5.25 px | 1 px sticks fanned out |

Ears sit on the top of the head, slightly behind the centre, and every one has its own pivot so it can twitch.

**Tails**:

| Model | Cubes | Construction |
|---|---|---|
| CatMaid | 20 | a chain of 5 sections of 4 cubes (a 1 x 8 x 1 core), 14 px behind the body |
| SenkoSan | 11 | a big fluffy tail of a few **big** boxes (the largest 10 x 7 x 10 px, as wide as the torso), 20 px behind the body |
| Sazuki | 28 | two branches of 14 thin sticks |
| TLM pack | 0 at the median, 8.5 at p90 | |

A big tail reads best as a few large boxes plus a lighter tip, not as many sticks.

---

## 6. Outfits

- **Flared skirt from hinged panels (YSM Default).** A core box 9.2 x 3.2 x 6 px with four panels hinged at the waist:
  front and back slabs 10.5 x 3 x 1.1 px in 2 tiers, tilted 20 and 12.5 degrees outward, and side slabs 1 x 3 x 6 px
  tilted 17.5 degrees. 21 cubes in all. The panels can swing with the legs.
- **Tiered box skirts** (CatMaid 12 cubes, TLM). Stacked boxes, each wider than the one above, with the apron as a
  zero-thickness plane in front and a back ribbon. TLM has a `sittingRotationSkirt` bone that opens the skirt when
  the model sits.
- **Coat skirts for men** (YSM Boy). A 21-cube coat skirt below a 7 px torso, the same hinge idea as the skirt.
- **Frills and cuffs.** Thin flat rings, 3.05 x 0.35 x 3.05 px, stacked around the arm (Sazuki, 10 per sleeve).
  Sleeve and cuff overlays 0.1-0.2 px bigger than the forearm (YSM).
- **Costume swaps as overlay models** (SenkoSan: 14 costumes of 2-30 cubes each, one shared texture atlas). The same
  idea as our parts library.
- **Shared base plus parts** (FBAC). One base of 24 drawn cubes: the vanilla body, a second skin layer as inflated
  cubes, eye planes and a halo. Each character only adds parts: median 90 cubes in total, 64x64 skin. This confirms herogen's
  approach and shows a whole cast of 51 staying distinct through hair, headwear and eyes.
- **Where detail goes:** collars, ribbons, buttons as 0.5 px planes, belts, hats with brims, wings. Limbs stay simple
  boxes in every source.

---

## 7. Animations

| Source | Count | What |
|---|---|---|
| TLM (C) | about 10 procedural scripts per model, picked by bone name | head look (`head/default`), `beg`, `music_shake`, `blink`, `hurt` (8 models), arm idle and swing, leg walk, sit, `skirt_rotation` (the skirt opens when sitting), tail sway, wing flap, ear shake, float |
| YSM Default (E) | **50 main + 57 arm + 8 extra**, plus compatibility sets for other mods (guns 64, blades 53, parkour 38, boat 23 and others) | **States:** idle (3 s), walk (1 s, 19-25 keys per bone), run, sneak, sneaking, swim, swim_stand, fly, elytra_fly, climb, climbing, ladder up/down/still, sit, sleep, ride, ride_pig, boat, jump, attacked, death, riptide, swing_hand, use_mainhand/offhand. **8 parallel layers and 8 pre-parallel layers:** blink with brows (a 4 s loop), long-hair and chest physics from Molang, eye look. **Arm per held item:** hold, swing and use for sword, axe, pickaxe, shovel, hoe, bow, crossbow, shield, fishing rod, spear, mace, throwable potion, eat, drink, spyglass, brush, goat horn. **Extra:** 8 emote slots. |
| YSM Boy (E) | 24 | states plus 8 parallel layers |
| CatMaid, SenkoSan, An-An (A, B) | 23 / 24 / 11 | Emotes: ear bend, tail wag, meow, pat head, sit, sleep, shake, afraid, refuse, holding a cake. SenkoSan adds chores: broom, vacuum, cloth, hair cut, tea, naginata attacks. Movement uses the vanilla player animation, plus scripted physics for hair, tail and ears. |
| FBAC base (A) | 2 | the character's special skill, halo idle |

For our heroes (HEROES_V2 §4.4-4.8 and §7.3) the YSM taxonomy is a good checklist:
- state animations for every movement the NPC can do;
- an arm layer per held item type, which our workers need for pickaxe, axe, hoe, shovel, fishing rod, bow, shield,
  eat and drink;
- parallel layers for the blink, breathing and secondary motion (hair, tail, ears, skirt panels);
- a few emotes.
TLM shows that much of the secondary motion can be procedural by bone name, which is cheap for 50 NPCs.

---

## 8. What to adopt, what to avoid

**Adopt**, by synthesis across the sources (no single creator's look):
1. **Longer bodies and a smaller head share.** Targets: about 3.25 heads for the chibi (head share 0.30), 4 for the
   teen (0.25) and 5-5.5 for the adult (0.18-0.20). Legs at 35% / 38% / 47-50% of the crown height, and a slight
   A-pose (10-15 degrees). Two equivalent routes:
   - **(a) Keep the 10 px head and its 20x20-texel face.** Lengthen the forms to about 32.5 / 40 / 52 px and draw
     them at a per-form model scale of about 0.71 / 0.7 / 0.63, as YSM does (authored at 40 px, drawn at 0.7). The
     world heights stay at today's 23 / 28 / 33 px, so the hitboxes do not change, while the head drops to about
     7 / 7 / 6.3 world px. All head parts and face frames stay valid.
   - **(b) Shrink the head to 8 px with a 16x16-texel face.** The forms become 26 / 32 / 42 px, drawn at about
     0.9 / 0.88 / 0.79 for the same world heights and head sizes.
   Route (a) changes the least in herogen. Either way, the hitbox must keep using the drawn height (HEROES_V2 §3.6).
2. **Lower, wider-set eyes.** Eye centre at 0.28-0.33 of the face height (ours 0.45). Brows just above the middle. The
   fringe covers the top 40-50%. Eye gap 0.25-0.35 of the face width (ours 0.1-0.2).
3. **Per-hero face identity beyond colour.**
   - A family of eye shapes (round, narrow, upturned, drooping, sleepy, sharp) with different lash weights and
     highlight positions, chosen per hero.
   - Brow shapes and angles: two-piece angled brows, thick and thin.
   - Eye height and spacing varied by ±1 texel.
   - A fringe template per hero, with no two cast members sharing a fringe.
   This is what FBAC does for 51 characters on one head.
4. **Hair from many thin tilted sticks.** 1 px wide, 2-3 px long, tilted 10-30 degrees, at 10+ depth layers, over a
   shell. Hair can be 40-60% of a hero's cubes, most of it on `fine_` bones. Use far fewer wide slabs.
5. **Silhouette pieces get the cubes:** big boxy tails with a light tip, stepped or stick ears on their own pivots,
   skirts and coat tails from waist-hinged panels, frill rings, hats. Limbs and feet stay at 1-4 cubes per segment.
6. **Budget:** typical 180-240 cubes, cap 300, at least 45% `fine_`, far silhouette 120 cubes or fewer (section 3).
7. **Texture density:** 1 texel/px is the norm for bodies. Spend double density on the face, hair and ears, as Gakuto
   and An-An do. A 256x128 texture at double density is already generous next to the 128x128 references.
8. **Animation layers:** blink, breathing, look and physics as parallel layers. Per-item arm poses for every work tool.

**Avoid:**
- Copying any character or any pixel pattern (eyes, fringes) from these sources. Most of them are fan works of
  commercial IPs, and several are non-commercial share-alike.
- Painted-only static faces (TLM). Our heroes need moods.
- Detail where nobody sees it: 31-cube sandals, 0.05 px decorations (SenkoSan's hair-ornament lines are 0.05-0.1 px).
- Showpiece budgets (400+ cubes) on crowd NPCs.
- Opening unlicensed community dumps (encrypted YSM collections).

---

## 9. Method

- Repositories were cloned read-only and shallow into the session scratchpad. Only the needed paths were checked out
  (sparse checkout and blob filter for YSM and FBAC).
- Throwaway Python scripts read the files and printed numbers only:
  - cube counts per bone group, with bone rotations applied for bounds;
  - head cubes, crown height, hip height;
  - texel density, from per-face UV sizes against cube sizes;
  - hair stick sizes, tilts and depth layers;
  - quads with a non-zero area;
  - animation names and lengths;
  - eye frame strip sizes.
- Nothing from the sources was written into `/home/user/test` except this prose and these numbers.
- "Share" figures come from name-based bone classes (hair, ear, tail, skirt and so on), so they are approximate. The
  per-model figures in the tables are exact.
