# Heroes v2: animation technology research

Scope: how Faction Clash heroes get animated 3D models with combat animations and facial animation (blinking,
expressions) on NeoForge 21.1.209 / Minecraft 1.21.1 / Java 21, for about 200 original heroes. Written 2026-11-11.
Facts were checked against GeckoLib's own source code (its `1.21.1` branch, read through raw.githubusercontent.com), the
ModDevGradle README, the decompiled Minecraft/NeoForge 21.1 sources in the scratchpad (`mcsrc/`), web search, and a
Gradle resolution test on a scratch copy of the build files. Everything that could not be checked is marked
**unverified**. Nothing in `/home/user/test` was changed.

---

## 0. Summary

- **Recommendation: GeckoLib 4.9.3 for NeoForge 1.21.1** (`software.bernie.geckolib:geckolib-neoforge-1.21.1`, MIT),
  embedded in the Faction Clash jar with ModDevGradle's `jarJar`. Players and servers install nothing extra. It gives
  Bedrock-format models (`.geo.json`), keyframed animations (`.animation.json`) with 30+ easings, several
  animation controllers running at once, sound, particle and custom "marker" keyframes, Molang expressions,
  per-face UVs, glow masks, items drawn on bones, and animations triggered from the server. The files open in
  Blockbench, so an artist (or the owner) can polish any hero later.
- **Only heroes use it.** Workers keep today's vanilla `PlayerModel` renderer. A campaign has a few heroes, not 100,
  so the client cost stays small (section 10).
- **The model.** One shared skeleton for all heroes, with two-segment limbs, a face, hair, ears, tail, cape, skirt,
  wings, weapon and effect-anchor bones. A Python generator builds each hero's `.geo.json` from a base body plus
  a parts library, and paints a 128x128 texture.
- **Animations** resolve per name through a fallback chain: the hero's own file, then its weapon-style library,
  then the core library. GeckoLib supports this directly with `GeoModel.getAnimationResourceFallbacks`. Each hero
  ships only its signature animations (skills, ultimate, personality idles).
- **Faces use texture frames, not eyelid geometry.** The head has two flat planes, `eyes` and `mouth`. The renderer
  shifts their UVs to pick a frame from an expression strip: 12 eye frames and 8 mouth frames. Blinking is a
  client-side timer (half, closed, half: 0.2 s, random 2-6 s apart, sometimes a double blink). The mood comes from
  one synced byte. Animators can also key expressions inside an animation through a cube-less `face_ctrl` bone
  with step easing.
- **Server sync.** The server stays authoritative and drives everything through vanilla `SynchedEntityData`: one
  int `DATA_ACTION` (action code, sequence, playback speed) plus mood and flag bytes. The client plays the
  animation whose name equals the action (`skill.2`, `ultimate`, `work.mine`). Wind-up timings in the server kit
  are **generated from the animation's `hit` markers**, so they match by construction. A unit test checks both
  files.
- **Blocker found by the feasibility check.** This cloud session cannot reach `dl.cloudsmith.io`, which hosts
  GeckoLib (403 from the session's network policy; the Gradle output is in section 12). Fix: allow that host in
  the environment once. Then vendor the jar and POM into a local Maven folder in the repository (`libs/maven`), so
  the build works offline for good.
- **Fallback if GeckoLib is ruled out:** vanilla `HierarchicalModel` plus vanilla keyframe animations
  (`AnimationDefinition`), with NeoForge 21.1's own JSON animation loader (`assets/<ns>/neoforge/animations/entity/`)
  and our own geometry loader. It has no dependency, but it is roughly 2000 lines of engine work, has no event
  keyframes, and only adds animations together (no override layers). AzureLib and playerAnimator are not
  recommended (section 2).

---

## 1. What the hero system needs from the animation technology

| Need | Why |
|---|---|
| Custom geometry per hero (hair, animal ears, tails, horns, wings, skirts, capes, big weapons), elbows and knees | "Unique appearance", "more advanced type of skin" |
| Several animations at once: legs walk while the arms attack; ears and tail move on their own; face separate | Combat while moving, lively idles |
| Server-triggered one-shots in sync with gameplay (wind-ups, hits, casts, hurt, death) | "Fighting one of them is like fighting a boss": readable telegraphs, timed hits |
| Facial animation: blink, expressions, talking mouth | Owner asked explicitly |
| Sound and visual effects tied to animation frames (sword whoosh at the swing, sparks at the weapon tip) | Boss feel |
| Glowing parts (eyes in phase 2, runes, magic weapons) and a phase-2 look | Boss phases |
| Held vanilla items on bones (pickaxe, axe, hoe, fishing rod) for non-combat work | Heroes also work, like every NPC |
| 200 heroes stay manageable: shared skeleton, shared animation libraries, per-hero parts | Owner's target |
| Works on a dedicated server, inside one jar, offline builds | CLAUDE.md rules, owner's players install one file |
| Acceptable client cost with many heroes visible | Wars |

---

## 2. Options evaluated

| | **GeckoLib 4.9.x** | **AzureLib 3.1.x** | **playerAnimator / Player Animation Library** | **Vanilla keyframes + own model loader** |
|---|---|---|---|---|
| What it is | Animation and rendering engine for mod entities, items, blocks and armour; Bedrock geo and animation JSON | A GeckoLib fork by one maintainer, with a rewritten animation API in 3.x | Keyframe animation of the **player's** own model (mixins into the player renderer) | MC 1.21.1's `HierarchicalModel` + `AnimationDefinition`/`KeyframeAnimations` (used by Warden, Breeze, Camel…); NeoForge 21.1 adds a JSON loader (`AnimationLoader`, folder `neoforge/animations/entity`) |
| 1.21.1 NeoForge build | Yes. The 1.21.1 branch is at **4.9.3** (`gradle/libs.versions.toml`: `geckolib = "4.9.3"`, `neoforge-range = "[21.1.150,)"`, built with moddevgradle 2.0.141). CurseForge has 4.9.2 (2026-07-01); Modrinth lists 4.9.3 | Yes, 3.1.10 / 3.1.11 (`azurelib-neo-1.21.1-3.1.11.jar`) | Old builds (2.0.x for 1.21.1); the original author no longer maintains it; successors are also player-only | Built in |
| Maven | `https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/`, `software.bernie.geckolib:geckolib-neoforge-1.21.1:<ver>` (official wiki) | `https://maven.azuredoom.com/mods` (wiki); NeoForge artifact name **unverified** | `https://maven.kosmx.dev/` | none |
| Licence | **MIT** (`modLicense=MIT` in the 1.21.1 branch; MIT `LICENSE` on main) | MIT (Modrinth) | MIT | Minecraft code; NeoForge loader is LGPL-2.1 but ships with NeoForge, not with us |
| Jar size | ≈ 0.6 MB (CurseForge: 4.8 = 606 KB, 4.9.2 = 615.8 KB) | similar | small | 0 |
| Custom geometry | Yes (per-face UV, rotated cubes, mirror, inflate; geometry format 1.12.0) | Yes | **No**, only the vanilla player limbs | Box-UV `ModelPart` only, unless we write our own cube renderer |
| Layered animations | Yes. Controllers run in order and **override** per bone and channel (checked in `AnimationProcessor.tickAnimation`) | Yes | Limited | **Additive only** (`KeyframeAnimations.animate` adds to the pose) |
| Server-triggered | Yes. `GeoEntity.triggerAnim` sends `EntityAnimTriggerPacket` to every tracking player. 4.8+ adds stateless `playAnimation(name)` from the server. Or our own synced data | Yes ("commands") | Client-side API | Entity events or synced data (our code) |
| Events in animations | Sound, particle, custom instruction keyframes | Yes | No | **No** (we would write a sidecar) |
| Glow, items on bones, armour | `AutoGlowingGeoLayer` (`_glowmask.png`), `BlockAndItemGeoLayer`, `ItemArmorGeoLayer` | Yes | n/a | Our code |
| Tooling | Official Blockbench plugin; huge user base; long-lived | Blockbench via GeckoLib format | Emote editors | Blockbench "Modded Entity" Java export; a Bedrock-to-NeoForge-JSON converter would be needed |
| Risk | Host blocked in this environment (fixable); 4.x is the last line for 1.21.1 (bug fixes only, stable API) | **API churn**: 3.0 to 3.1 removed the old animation system, and mods built against 3.0.x crash on 3.1+ (seen on a mod's patch page); single maintainer | Wrong tool: players only, no custom parts, no face | 1500-2500 lines of engine code to own; slower start |
| **Verdict** | **Recommended** | Not chosen | Rejected | Fallback only |

Why GeckoLib over a hand-written engine: the owner's art goal is high, there are 200 heroes, and Claude generates the
assets with code. The value is in the runtime features (override layers, transitions, easings, event keyframes,
Molang, glow, per-face UV) and in an asset format that people can edit in Blockbench. GeckoLib gives all of it for
about 600-900 lines of integration code instead of an engine. The Bedrock file format is shared by GeckoLib 4,
GeckoLib 5 and Blockbench, so the assets survive a later engine or Minecraft-version change.

---

## 3. Adding GeckoLib to the build

### 3.1 How the repository resolves dependencies today

- `settings.gradle`: plugin repositories are the Gradle Plugin Portal and `https://maven.neoforged.net/releases`.
- `build.gradle`: `repositories { mavenCentral() }`; the NeoForge ModDevGradle plugin `net.neoforged.moddev` 2.0.148
  adds its own repositories for NeoForge and Minecraft artifacts. All of these are cached in `~/.gradle/caches`.
- In this cloud session only `maven.neoforged.net`, Maven Central (`repo1.maven.org`) and `raw.githubusercontent.com`
  answered. These failed: `dl.cloudsmith.io` (proxy 403), `maven.azuredoom.com`, `maven.kosmx.dev`,
  `api.modrinth.com`, `cdn.modrinth.com`, `cursemaven.com`, `mediafilez.forgecdn.net`, `github.com` (403).
  GeckoLib is on neither reachable Maven (checked: 404 on Maven Central and on the NeoForged Maven).

### 3.2 Lines to add (recommended form: vendored, offline-safe)

**Step 1, once.** Allow `dl.cloudsmith.io` in this cloud environment's network settings. The main session can
propose it with the environment tools: a refused request is on record in section 12. Then download the GeckoLib
jar and POM once and copy them into the repository:

```
libs/maven/software/bernie/geckolib/geckolib-neoforge-1.21.1/4.9.3/geckolib-neoforge-1.21.1-4.9.3.jar
libs/maven/software/bernie/geckolib/geckolib-neoforge-1.21.1/4.9.3/geckolib-neoforge-1.21.1-4.9.3.pom
libs/maven/software/bernie/geckolib/geckolib-neoforge-1.21.1/maven-metadata.xml
libs/maven/LICENSE-geckolib.txt          (MIT notice, copied from the GeckoLib repository)
```

Check the jar against the `.sha1` that Cloudsmith publishes next to it, and record the SHA-256 in
`docs/HANDOFF.md`. If the POM lists dependencies, vendor them too or check that they are on Maven Central. This is
**unverified**: GeckoLib 4 for 1.21 bundles its own Molang parser and should need nothing beyond NeoForge.

**`gradle.properties`**

```properties
geckolib_version=4.9.3
geckolib_version_range=[4.9.3,4.10)
```

**`build.gradle`**: `exclusiveContent` makes Gradle look for this group only in the local folder, so builds never
touch the network for it, offline or online:

```groovy
repositories {
    exclusiveContent {
        forRepository {
            maven { name = 'VendoredGeckoLib'; url = file('libs/maven') }
        }
        filter { includeGroup 'software.bernie.geckolib' }
    }
    mavenCentral()
}

dependencies {
    // GeckoLib is embedded in our jar (Jar-in-Jar): players and servers install only Faction Clash.
    jarJar(implementation("software.bernie.geckolib:geckolib-neoforge-${minecraft_version}")) {
        version {
            strictly project.geckolib_version_range   // versions FML may pick if another mod also embeds GeckoLib
            prefer project.geckolib_version           // the version we build and test with
        }
    }
    // ... existing junit lines unchanged
}
```

Also add `geckolib_version_range : geckolib_version_range` to `replaceProperties` in `generateModMetadata`.

**`src/main/templates/META-INF/neoforge.mods.toml`**: add the dependency, so a GeckoLib installed separately at a
version outside our range gives a clear error instead of a crash:

```toml
[[dependencies.${mod_id}]]
    modId="geckolib"
    type="required"
    versionRange="${geckolib_version_range}"
    ordering="NONE"
    side="BOTH"
```

**Alternative without vendoring** (simpler, but needs the host every 24 h): replace `exclusiveContent` with a
normal `maven { url = 'https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/'; content { includeGroup
'software.bernie.geckolib' } }`. Because of the version range, Gradle re-lists versions when its dynamic-version
cache expires (24 h by default). Builds then fail when the host is unreachable, unless `--offline` is used or
`resolutionStrategy.cacheDynamicVersionsFor` is raised. That is why vendoring is recommended.

Do **not** use `jarJar files('libs/geckolib.jar')`. For a plain file, ModDevGradle uses the file name as the artifact
id and the MD5 as the version. FML then cannot recognise it as the same mod as another GeckoLib, and two copies of
mod id `geckolib` would collide.

### 3.3 Checks once it resolves

- `./gradlew build` → `unzip -l build/libs/factionclash-0.1.0.jar | grep jarjar` shows
  `META-INF/jarjar/metadata.json` and `META-INF/jarjar/geckolib-neoforge-1.21.1-4.9.3.jar`.
- `runGameTestServer`: all GameTests still pass with GeckoLib loaded on a headless server. Dev runs on 1.21.1 load
  `implementation` dependencies that are mods (GeckoLib has a `neoforge.mods.toml`), so no
  `additionalRuntimeClasspath` is needed.
- Dedicated server from `tools/setup_server.sh` with only our jar: the log lists the `geckolib` mod; a client (Xvfb
  `runClientViewer`) joins with only our jar.
- Licence: ship `META-INF/licenses/geckolib-LICENSE.txt` in our jar and add a "Credits" line to the README.
  Our "All Rights Reserved" licence is compatible with bundling MIT code as long as its notice is kept.

---

## 4. Architecture: server and client split

```
common (both sides)                                   client only (Dist.CLIENT)
------------------------------------------------      ---------------------------------------------------
npc/HeroEntity extends FactionNpc implements GeoEntity   client/hero/HeroModel extends GeoModel<HeroEntity>
  DATA_HERO   String   hero id                          client/hero/HeroRenderer extends GeoEntityRenderer<HeroEntity>
  DATA_ACTION Integer  action|seq|speed|aux              + AutoGlowingGeoLayer (<texture>_glowmask.png)
  DATA_MOOD   Byte     expression the server wants        + BlockAndItemGeoLayer (tools at item_r / item_l)
  DATA_VFLAGS Byte     weapon drawn, super armour,        + face UV-frame override (section 8)
                       awakened, talking, channelling   client/hero/HeroFace       (blink, mood, talk; per entity)
  registerControllers(...)  (controllers, section 7)    client/hero/HeroVfx        (marker, sound, particle keyframes)
  getAnimatableInstanceCache() = GeckoLibUtil.createInstanceCache(this)
hero/HeroActions  (action codes <-> animation names; pure Java, unit-tested)
hero/HeroAbilityRunner (server: wind-up, hits, recovery; sets DATA_ACTION)
```

- A separate entity type, `factionclash:hero`, is registered next to `faction_npc`, so workers and their renderer do not
  change. `HeroEntity` reuses everything `FactionNpc` does (jobs, inventory, hunger, factions). Its renderer is
  registered in `ClientSetup.onRenderers`.
- **GeckoLib is on both sides** (embedded), so `implements GeoEntity` is safe on a dedicated server. Animation
  work happens only on the client: GeckoLib processes animations inside the renderer, so the **server never
  evaluates animations**. Controller handlers must not touch client-only classes; they read synced fields and
  call into `client/hero` only through methods that exist only on the client side.
- **Size per hero**: the vanilla `generic.scale` attribute (1.20.5+). It scales the hitbox and reach, and GeckoLib's
  `GeoEntityRenderer` applies `livingEntity.getScale()` (checked in `actuallyRender`). Suggested range 0.85-1.2.
- **Culling**: wings, tails and long weapons stick out of the 0.6x1.8 box. `HeroEntity.getBoundingBoxForCulling()`
  inflates it by about 1.5 blocks, so a hero does not vanish when only its box leaves the screen.
- **Death**: `HeroRenderer.getDeathMaxRotation()` returns 0, so the vanilla tip-over is off. The `death`
  animation plays, holding its last frame. `HeroEntity.tickDeath` keeps the body for 40 ticks instead of vanilla's
  20.

---

## 5. The shared skeleton ("hero rig v1")

Units are Bedrock pixels (1/16 block). Overall height is 32 px like the player model, drawn at 15/16 as players are,
with anime proportions (longer legs: legs 13, torso 11, head 8). Two base bodies (female, male) in three builds
(slim, standard, large) share **the same bone names and pivots**, so every library animation fits every hero.
A hero may leave out optional bones; GeckoLib ignores animation channels for missing bones
(`crashIfBoneMissing()` is false by default).

```
root                                 whole-body offset (bob, dodge slide)
├─ body (hips, pivot 0,12,0)
│  ├─ waist ─ chest
│  │   ├─ neck ─ head                head turns to look (yaw/pitch set in setCustomAnimations)
│  │   │   ├─ face ─ eyes, mouth     flat planes in front of the face (section 8)
│  │   │   ├─ face_ctrl              no cubes: position.x/.y carry authored eye/mouth frame keys
│  │   │   ├─ hair_top, bangs, hair_back, hair_l, hair_r, tail_hair_1-3 (ponytail chain)
│  │   │   ├─ ear_l, ear_r           animal or elf ears (twitch, flatten when hurt)
│  │   │   └─ horn_l, horn_r, hat, halo
│  │   ├─ arm_r ─ forearm_r ─ hand_r ─ item_r (locator), weapon_r
│  │   ├─ arm_l ─ forearm_l ─ hand_l ─ item_l (locator), weapon_l
│  │   └─ back ─ cape_1 ─ cape_2 ; wing_l, wing_r ; weapon_back (sheathed copy)
│  ├─ skirt ─ skirt_f, skirt_b, skirt_l, skirt_r   (panels or coat tails)
│  └─ tail_1 ─ tail_2 ─ tail_3 (+ tail_b_*, tail_c_* for multi-tailed heroes)
├─ leg_r ─ shin_r ─ foot_r
├─ leg_l ─ shin_l ─ foot_l
└─ vfx_tip_r, vfx_tip_l, vfx_chest, vfx_head, vfx_feet   (locators, no cubes; GeoBone.getWorldPosition with tracking on)
```

Naming conventions the renderer uses (no per-hero code):

| Prefix or name | Meaning |
|---|---|
| `fine_*` | Detail hidden beyond 20 blocks (hair strands, frills, charms): level of detail |
| `p1_*` / `p2_*` | Shown only before / after the hero's phase change (awakened flag): extra tails, unfurled wings, horns grow |
| `weapon_r/l` vs `weapon_back` | Weapon in hand while `weapon drawn`, otherwise the sheathed copy |
| `item_r/l` | Where vanilla items (work tools) are drawn; hidden while the signature weapon is drawn |

Budgets (enforced by the validator): at most 48 bones, at most 140 cubes (typical 80-110), texture 128x128 (256x128
for winged heroes or very large weapons), at most 20 animations in a hero's own file.

---

## 6. Organising 200 heroes' assets

### 6.1 Files in the mod jar (all bundled; GeckoLib loads every `geo/` and `animations/` JSON of every namespace on resource reload)

```
src/main/resources/assets/factionclash/
  geo/hero/<hero_id>.geo.json                     generated: base body + parts merged; format_version 1.12.0
  geo/hero/_fallback.geo.json                     used if a hero id is unknown on the client
  animations/hero/_core.animation.json            movement, rest, work, emotes, hurt/stagger/stun, death, dodge, guard
  animations/hero/_style_<style>.animation.json   one per weapon style (about 16): stance, run, combo attack.1-3, guard, intro, victory
  animations/hero/<hero_id>.animation.json        signature: skill.1-4, ultimate (.loop/.end), personal.1-3, any override
  textures/entity/hero/<hero_id>.png              128x128, layout from the rig's UV template
  textures/entity/hero/<hero_id>_glowmask.png     optional (GeckoLib AutoGlowingGeoLayer)
  textures/entity/hero/<hero_id>_p2.png           optional phase-2 texture (+ _p2_glowmask.png)
  heroes/<hero_id>.visual.json                    client facts: style, blink range, mouth style, voice pitch
src/main/resources/data/factionclash/heroes/<hero_id>.json   server: kit, personality, stats, generated wind-up timings
herosrc/                (repository only, not shipped)  bases, parts library, palettes, face painters, per-hero visual specs
tools/herogen/          Python generator and validator (extends tools/herotool)
```

`HeroModel` (client):

```java
public ResourceLocation getModelResource(HeroEntity h)     { return id("geo/hero/" + h.heroId() + ".geo.json"); }
public ResourceLocation getTextureResource(HeroEntity h)   { return id("textures/entity/hero/" + h.heroId() + (h.awakened() && h.visual().hasP2() ? "_p2" : "") + ".png"); }
public ResourceLocation getAnimationResource(HeroEntity h) { return id("animations/hero/" + h.heroId() + ".animation.json"); }
public ResourceLocation[] getAnimationResourceFallbacks(HeroEntity h) {
    return new ResourceLocation[]{ id("animations/hero/_style_" + h.visual().style() + ".animation.json"),
                                   id("animations/hero/_core.animation.json") };
}
```

`GeoModel.getAnimation` (checked in source) looks up the primary file, then each fallback in order, per animation
name. It returns null for a name found nowhere. It throws when the last file it looked at is not loaded. To keep
this simple, the generator always writes a hero's own file (an empty `"animations": {}` is fine), and a unit test
checks that every file in every chain exists. Unknown hero ids use `_fallback`.

### 6.2 Generator pipeline (Claude-run, deterministic, outputs committed)

1. Hand-tuned sources in `herosrc/`: 6 base bodies, a parts library (about 30 hair styles, 8 ear types, 8 tails,
   6 horn types, 5 wing types, skirts/coats/capes/aprons/hakama/armour plates, about 30 weapons), each part
   declaring its attach bone and its UV block.
2. A hero visual spec: base, build, scale, parts with parameters (length, colour slots), weapon, style, palette,
   eye style, glow regions, phase-2 changes, and recipes for the signature animations (parameters for library
   primitives such as arc, spin count, leap height, pose set).
3. `herogen build` writes the merged `.geo.json`, paints the texture (body regions, HD face, expression strips,
   palette, markings), writes the hero's animation file, and writes the timing block into the server data file.
4. `herogen check` (and a Java unit test over `src/main/resources`, no Minecraft classes) checks: JSON syntax;
   geometry 1.12.0 without rotated face UVs (GeckoLib warns on 1.14.0 / 1.21.0); budgets; bone names come from the
   rig or are declared parts; every action code the server can send resolves to an animation for that hero
   through the chain; `hit` markers equal the server wind-ups (section 9); `face_ctrl` channels use `step` easing;
   texture size matches `texture_width/height`; all files present.
5. Visual review: `/fc admin hero preview <id> [animation]` spawns a still hero (AI off) playing an animation in a
   loop; `/fc admin hero lineup <from>` places 10 heroes in a row. Screenshots come through the existing Xvfb client
   (HANDOFF section 4), giving a contact sheet per batch of 20 heroes.

### 6.3 Size estimates (rough; measure after the first 10 heroes)

| Item | Per hero | 200 heroes |
|---|---|---|
| `.geo.json` (80-110 cubes) | 30-60 KB raw | ≈ 1 MB in the zip (JSON compresses about 10x) |
| Own animations (6-12) | 40-120 KB raw | ≈ 1.5 MB in the zip |
| PNG 128x128 (+ glow, p2) | 5-20 KB | ≈ 2-4 MB |
| Libraries (core + 16 styles) | | ≈ 0.3 MB in the zip |
| GeckoLib | | 0.6 MB |
| **Jar** | | **≈ 5-9 MB** (today 0.9 MB) |

Client memory: GeckoLib bakes every model and animation at resource reload, including heroes not in the world.
Rough estimate: 2 KB per cube and 10-40 KB per baked animation, so **30-60 MB for 200 heroes** (**unverified**:
measure with a heap histogram). If it ever exceeds about 100 MB: move hero geometry out of the scanned `geo/`
folder and bake lazily on first sight.

---

## 7. Controllers and the animation catalogue

Four layers, in this order. A later controller overrides an earlier one on the bones and channels its current
animation keys; bones it does not key keep the earlier result.

| Controller | Transition | Plays | Driven by |
|---|---|---|---|
| `base` | 4 ticks | `move.idle` / `combat.idle` / `personal.N` (rare idles), `move.walk` / `move.run` / `move.swim` / `move.fall` / `move.climb` / `move.crouch`, `rest.sit` / `rest.sleep`, `carry.loop` | Client state: speed, `onGround`, `isInWater`, pose, weapon-drawn flag, mood |
| `action` | 2 ticks | Everything in the action table below, plus `death` | `DATA_ACTION` (new sequence number = new action), `isDeadOrDying()`, vanilla `swinging` for work hits |
| `secondary` | 6 ticks | `sec.tail`, `sec.ears`, `sec.cape`, `sec.hair` loops (key only those bones) | Always on; Molang (`query.ground_speed`, `query.life_time`) scales sway with speed |
| face (Java, not a controller) | | eyes/mouth frame choice | Section 8 |

Procedural touches in `HeroModel.setCustomAnimations` (cheap, every frame): head look; cape lift and tail lag from
velocity and turn rate; a light hit flinch from `hurtTime` (torso and head kick of a few degrees, skipped under
super armour); ears flatten while hurt or angry; `fine_`, `p1_`/`p2_`, weapon and item bone visibility. GeckoLib bones are shared by all
instances of one model, so visibility is set on every frame. Heroes are unique, so normally there is one instance.

Action codes (one byte; the animation name is the code's name):

| Code range | Names | Notes |
|---|---|---|
| 1-4 | `attack.1`, `attack.2`, `attack.3`, `attack.ranged` | Basic combo; from the style library unless overridden |
| 8-11 | `skill.1` … `skill.4` | Signature, in the hero's file |
| 12-13 | `ultimate`, `ultimate.end` | `ultimate.loop` played automatically if it exists, for channelled ultimates |
| 16-21 | `dodge`, `guard`, `guard.hit`, `stagger`, `stun`, `interrupt` | `guard` and `stun` have `.loop` |
| 24-28 | `intro`, `taunt`, `phase` (awaken), `victory`, `defeated` | Boss moments; `intro` when first meeting an enemy hero or player |
| 32-47 | `work.mine`, `work.chop`, `work.till`, `work.plant`, `work.fish_cast`, `work.fish_reel`, `work.cook`, `work.smith`, `work.build`, `work.read`, `work.pray`, `work.sweep`, … | Non-combat skills and jobs |
| 48-63 | `emote.greet`, `emote.bow`, `emote.wave`, `emote.laugh`, `emote.angry`, `emote.sad`, `emote.think`, `emote.cheer`, `emote.talk` … | Dialogue and diplomacy moments |

Animations use `loop: false` for one-shots, `hold_on_last_frame` for `death`/`defeated`, and `loop: true` for `.loop`.

---

## 8. Facial animation (blinking and expressions)

### 8.1 Choice: texture frames on two face planes, plus a few bones

Minecraft-style anime faces are 2D pixel art on a flat head. Geometric eyelids, made as cubes in front of the eyes,
read as stickers. They also z-fight, and they cannot draw the shapes anime faces need: closed arcs for happy eyes,
crossed eyes for pain, wide eyes for surprise. Texture frames draw exactly those. Bones are still used where depth
helps: ears twitch and flatten, ponytail and tails sway, an android hero's visor slides, a mask lifts.

**Texture layout** (part of the rig's UV template, the same for every hero):

- Head front at double density: the 8x8 px face maps to **16x16 texels**, so anime eyes have room for a highlight,
  iris colour and lashes. The rest of the body is at 1 texel per px, like vanilla skins.
- `eyes` plane: 8x4 px (16x8 texels, eyes and brows), 0.02 px in front of the face. `mouth` plane: 4x2 px (8x4 texels).
  Both are opaque, so they fully cover the base face. Bangs sit in front of them.
- Eye strip at u 64-127, v 0-23: 12 frames of 16x8 in a 4x3 grid:
  `0 open · 1 half · 2 closed · 3 happy (^ ^) · 4 angry · 5 sad · 6 surprised · 7 pain (> <) · 8 focused · 9 smug · 10 glowing (phase 2, paired with the glow mask) · 11 sleeping`.
- Mouth strip at u 64-127, v 24-27: 8 frames of 8x4:
  `0 neutral · 1 smile · 2 talk A (small open) · 3 talk B / shout (wide) · 4 grit teeth · 5 frown · 6 cat mouth (ω) · 7 fang grin`.
- Each hero's frames are painted in its own style (a stoic hero's "happy" is a faint smile). The logic is shared;
  personality lives in the art.

**Renderer code** (about 40 lines; GeckoLib's hooks were checked in `GeoRenderer`):

```java
// HeroRenderer extends GeoEntityRenderer<HeroEntity>
private float du, dv;                                    // UV shift for the bone being drawn
@Override public void renderCubesOfBone(PoseStack ps, GeoBone bone, VertexConsumer buf, int light, int overlay, int colour) {
    String n = bone.getName();
    if (n.equals("eyes"))       { du = face.eyeDu();   dv = face.eyeDv(); }    // face = this entity's HeroFace, set in preRender
    else if (n.equals("mouth")) { du = face.mouthDu(); dv = face.mouthDv(); }
    super.renderCubesOfBone(ps, bone, buf, light, overlay, colour);
    du = dv = 0;
}
@Override public void createVerticesOfQuad(GeoQuad quad, Matrix4f pose, Vector3f normal, VertexConsumer buf, int light, int overlay, int colour) {
    if (du == 0 && dv == 0) { super.createVerticesOfQuad(quad, pose, normal, buf, light, overlay, colour); return; }
    for (GeoVertex v : quad.vertices()) {
        Vector4f p = pose.transform(new Vector4f(v.position().x(), v.position().y(), v.position().z(), 1f));
        buf.addVertex(p.x(), p.y(), p.z(), colour, v.texU() + du, v.texV() + dv, overlay, light, normal.x(), normal.y(), normal.z());
    }
}
```

The glow layer re-renders the model through the same renderer, so the glow mask's eye frames line up as well.
Iris/shader packs see ordinary UVs.

**Plan B** if the UV override ever conflicts with something: one flat plane per frame under `face`, shown or hidden
per frame (`GeoBone.setHidden`). It needs no renderer code but has more cubes.

### 8.2 Who decides the frame (client `HeroFace`, per entity)

Priority, highest first:

1. **Dead or defeated** → eyes `closed`, mouth `neutral`.
2. **Authored keys**: if the running action animation keys `face_ctrl`, its `position.x` = eye frame + 1 and
   `position.y` = mouth frame + 1 (0 = no override), with `"easing": "step"` (GeckoLib has `EasingType.STEP`). This
   is how an ultimate shouts at 0.4 s, or a victory animation winks.
3. **Talking** (`VFLAGS.talking`, set while a dialogue line is shown): the mouth cycles `talk A`, `talk B`, `neutral`
   every 2-3 ticks with a little noise; the eyes follow the mood.
4. **Mood** (`DATA_MOOD`, set by the server from situation and personality): `calm, happy, angry, focused, hurt,
   tired, sad, smug, surprised, asleep, awakened`. A fixed table maps each to an eye and mouth frame. Changes hold
   at least 10 ticks, so the face never flickers.
5. **Blink**, layered over 2-4 when the eye frame is open, focused, angry, sad or smug: the next blink comes after a
   random 2.5-6 s (a nervous personality 1.5-3.5 s, a calm one 4-7 s; from `visual.json`). A blink is `half` 1 tick,
   `closed` 2 ticks, `half` 1 tick (0.2 s), with a 15 % chance of a second blink 6 ticks later. The random source is
   seeded from the entity UUID, so two heroes never blink in step.
6. **Distance**: beyond 16 blocks the face stays on the mood frames (no blink, no talk), to save work.

No network traffic for blinking. Mood and talking are a byte and a bit already in synced data.

---

## 9. Server-triggered animations in sync with gameplay

### 9.1 One synced int per hero

```
DATA_ACTION (Integer):  bits 0-7 action code | 8-15 sequence (+1 per new action) | 16-23 speed x64 (64 = 1.0) | 24-31 aux (combo step, variant)
```

- The server's `HeroAbilityRunner` sets it at the **start of the wind-up**. Vanilla sends changed entity data in the
  same tick, to every player tracking the hero (about 10 bytes).
- The client's `action` controller sees a new sequence number. It calls `forceAnimationReset()`, sets the speed
  (`setAnimationSpeed`) and returns `state.setAndContinue(raw(name))`, where `raw` comes from a per-name cache of
  `RawAnimation.begin().thenPlay(name)` (`.thenLoop(name + ".loop")` when that animation exists). When the
  animation finishes it returns `PlayState.STOP`, and the bones blend back to `base`.
- Channels and loops (`guard`, `stun`, `ultimate.loop`) keep playing until the server sends the next action, or
  code 0 to stop.
- **Why synced data rather than GeckoLib's `triggerAnim`**: GeckoLib's trigger also works (`EntityAnimTriggerPacket`
  to tracking players). But it needs names registered in advance per controller, and it cannot carry a speed. A
  player who starts tracking the hero mid-cast also misses it, whereas synced data reaches new trackers.
  Synced data keeps one source of truth with the server's state machine, carries speed and variant, and does not
  depend on GeckoLib's networking: the fallback engine would use the same field. GeckoLib 4.8's stateless
  `playAnimation(name)` from the server is a second alternative.

### 9.2 Timings agree by construction

- Every combat animation carries markers in its Bedrock `timeline` (GeckoLib custom instruction keyframes):
  `"timeline": {"0.20": "vfx:charge", "0.45": "hit;vfx:slash_arc", "0.70": "hit"}`. Several instructions at one time
  are joined with `;`, and our marker handler splits them, so the JSON never has duplicate keys.
- The generator writes the server kit from those markers: `windup` = first `hit`; `hits` and `hit_interval` from
  the following ones; `recovery` = animation length minus the last hit. The kit JSON is server data and the
  animation is a client asset, but they come from one source. A unit test parses both from `src/main/resources`
  and fails on a difference above 1 tick.
- **Speed changes** (haste, slow, level-based attack speed): the server divides wind-up and recovery by `s` and
  sends `speed = s`. The client plays at that speed, so the `hit` marker still lands on the server's hit tick.
  Speed is clamped to 0.5-2.0.
- **Latency**: the client starts the animation one tick plus ping after the server. The damage the server applies
  at the wind-up's end reaches the client after the same delay, so the swing and the red hurt flash line up on
  screen.
- **Telegraphs** (boss readability): wind-ups of 0.75 s or more keep the server-side ground telegraph particles
  from the v1 design. The animation's anticipation pose and a `vfx:charge` marker add the visual cue.
- **Interrupts**: when a stun or a knockback above 0.5 cancels a wind-up, the server sends `interrupt` or `stagger`.
  The 2-tick transition cuts the swing cleanly.
- **Movement skills**: `dash` and `leap` motion stays physical on the server (no root motion). The animation is
  authored in place with the same duration, and the generator takes the leap's airtime from the animation length.
- **Hurt**: light hits are a procedural flinch on the client from vanilla `hurtTime` (no packet). Heavy hits and
  poise breaks are a server `stagger`. During `ultimate`, `intro` and `phase` the server sets `super armour` and
  the client skips the flinch.
- **Death**: client `isDeadOrDying()` → `death` (hold last frame), eyes closed. The server keeps the body 40 ticks.
- **Work**: existing NPC code already calls `swing(hand)` for block breaking (vanilla sends the swing packet).
  When the action controller is idle, a fresh swing plays `work.mine` / `work.chop` / `work.till` / `attack.1`
  depending on the held tool. Longer jobs (fishing, cooking, smithing, praying) send their `work.*` code
  through `DATA_ACTION`.
- **Effects on the client only** (cosmetic): sound keyframes (`sound_effects`) through
  `setSoundKeyframeHandler`; particle keyframes and `vfx:*` markers spawn particles at locator bones (`vfx_tip_r`
  world position; bone matrix tracking switched on for locators only). Everything that affects gameplay
  (damage, projectiles, areas) stays on the server.

### 9.3 Testable on the server

GameTests cannot see client animations, but they can check the server side: a hero using a skill sets `DATA_ACTION`
with the right code and a new sequence number at wind-up start, and the hit lands exactly `windup` ticks later. A
stun during the wind-up sends `interrupt` and refunds half the cooldown. Death keeps the body for 40 ticks. Unit
tests cover `HeroActions` packing, the name table and the marker/kit agreement.

---

## 10. Performance

- **Where the cost is.** Only on the client, and only for heroes being drawn: GeckoLib evaluates animations
  inside the renderer, so heroes off-screen or out of range cost nothing. Per hero per frame it (a) samples
  keyframes for the animated bones (cheap) and (b) transforms and emits every cube's vertices in Java, like
  vanilla `ModelPart`. A vanilla player model is 12 cubes, about 288 vertices. A hero is 80-140 cubes,
  about 1900-3400 vertices, so **roughly 8-12 player models' worth of vertex work**. The server cost is zero
  apart from a few bytes of synced data per action.
- **Rough estimate** (**unverified**, no profiler run is possible here): 20 heroes in view is in the range of a
  busy village or raid in vanilla. 100 heroes in view (about 300k vertices per frame) would likely cost several
  milliseconds a frame on a mid-range CPU.
- **Design choices that keep it low:**
  1. Workers keep the vanilla renderer; only heroes, and later bosses, use GeckoLib. A campaign starts with one hero per
     faction, and more join through recruitment and tokens, so a 50-100-hero scene is a corner case.
  2. Cube budget 140, typical 80-110.
  3. Level of detail: `fine_` bones hidden beyond 20 blocks (target: 30-40 % fewer cubes); face frozen beyond 16
     blocks; locator tracking off for heroes further than 24 blocks.
  4. Vanilla frustum culling with the inflated culling box (section 4).
- Third-party "GeckoLib optimisation" mods exist, which shows the cost can matter in big packs. Sodium-type mods
  probably do not speed up GeckoLib entities (**unverified**). GeckoLib's own build tests against Iris (Iris is in
  its `libs.versions.toml`).
- **Measurement plan** (step 4 of section 13): a debug system property makes `HeroRenderer.render` log the average
  microseconds per hero every 600 frames. Spawn 1, 16 and 64 heroes with `/fc admin hero lineup`. Run on the Xvfb
  client here (CPU side only; software GL makes GPU numbers meaningless) and then on the owner's PC during the
  Phase 5 play session. Set the cube budget and level-of-detail distances from those numbers.

---

## 11. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| `dl.cloudsmith.io` blocked in this environment (proven) | Cannot fetch GeckoLib | Allow the host once; vendor jar + POM into `libs/maven` with `exclusiveContent` (section 3.2) |
| Gradle re-lists dynamic versions every 24 h | Builds break offline | Vendored local repository (local files are not cached or re-checked) |
| Licence | Must keep the MIT notice | Ship `META-INF/licenses/geckolib-LICENSE.txt`, README credits; all hero art and models are original and generated by our tools |
| Jar size | 0.9 MB → about 5-9 MB | Fine for any host; JSON compresses well; budgets in the validator |
| Client memory for 200 baked heroes (estimated 30-60 MB) | Larger heap | Measure; lazy baking if above about 100 MB |
| Another mod embeds or the user installs a different GeckoLib | Version clash | Jar-in-Jar picks one copy within all declared ranges; our `neoforge.mods.toml` range gives a clear error outside `[4.9.3,4.10)`; test once with a standalone GeckoLib 4.9.x installed |
| GeckoLib 4 is the last line for 1.21.1 | No new features | Stable API is an advantage on a fixed MC version. A move to MC 1.21.5+ means GeckoLib 5 (new group `com.geckolib`, render-state API): rewrite about 600-900 lines of renderer and model code; the assets stay as they are |
| Dedicated-server class loading | Crash if client classes are touched in common code | Controller handlers read only synced data; face, VFX and renderer code under `client/` behind `Dist.CLIENT` registration; GameTests on the headless server catch mistakes |
| Missing animation or file | GeckoLib throws on a missing file in the chain | Generator always writes every file; unit test resolves every action for every hero; `_fallback` model for unknown ids |
| Art quality at 200 heroes (the biggest real risk) | Samey or ugly heroes | Large parts library, per-hero signature animations with tuned parameters, HD faces, contact-sheet review per batch of 20, Blockbench-editable files for later hand polishing |
| AzureLib-style API break | n/a | Not using AzureLib; GeckoLib pinned and vendored |

---

## 12. Feasibility check performed

Scratch copy only, in `(working folder) gradlecheck/`:
the repository's `build.gradle`, `settings.gradle`, `gradle.properties`, wrapper and mods.toml template, with the
Cloudsmith repository and the `jarJar(implementation(...)) { version { strictly '[4.9.3,4.10)'; prefer '4.9.3' } }`
dependency added.

- `./gradlew dependencies --configuration compileClasspath`: the build script configured without errors, so the
  `jarJar` syntax is accepted by ModDevGradle 2.0.148 and NeoForge 21.1.209 resolved from the cache. The result
  was `software.bernie.geckolib:geckolib-neoforge-1.21.1:{strictly [4.9.3,4.10); prefer 4.9.3} FAILED`.
- `./gradlew dependencyInsight --dependency geckolib`: *"Could not GET
  'https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/software/bernie/geckolib/geckolib-neoforge-1.21.1/maven-metadata.xml'.
  Received status code 403 from server: Forbidden"*. The session proxy's status page confirms a policy refusal of
  CONNECT to `dl.cloudsmith.io:443`. The remaining step is to allow that host in the environment's network settings;
  nothing else in the build blocks GeckoLib.
- GeckoLib sources used for the API facts in this document are saved in `.../heroes2/gl/` (1.21.1 branch:
  `GeoEntity`, `GeoModel`, `AnimationController`, `AnimationProcessor`, `GeoRenderer`, `GeoEntityRenderer`,
  `GeckoLibNetworking`, `EntityAnimTriggerPacket`, `StatelessGeoEntity`, `AnimatableTexture`, `AutoGlowingGeoLayer`,
  `BlockAndItemGeoLayer`, `EasingType`, `DefaultAnimations`, `libs.versions.toml`, `neoforge.mods.toml`, `changelog.txt`).

---

## 13. Implementation order (for the hero plan)

1. **Dependency**: allow the host, vendor GeckoLib 4.9.3, add the build lines (section 3), mods.toml entry, licence
   file. Verify: build, embedded jar present, 145 unit tests and 108 GameTests still pass, dedicated server and Xvfb
   client start with only our jar.
2. **Skeleton and one test hero**: rig v1 bases, UV template, `_core` library, `HeroEntity`, `HeroModel`,
   `HeroRenderer`, synced fields, face frames and blink. Screenshot it idle, walking, blinking and per mood.
3. **Actions**: `HeroActions`, `HeroAbilityRunner` hooks, markers, generated timings, speed, interrupts, death,
   work swings; GameTests and unit tests from section 9.3.
4. **Generator and validator** (`tools/herogen`), the first 3 style libraries, 5 pilot heroes (half female, half
   male), contact sheet, performance measurement (section 10); set budgets from it.
5. **Scale up** in batches of 20 heroes: generate, `herogen check`, unit tests, contact sheet, fix; then the
   campaign check per CLAUDE.md (heroes leading factions over several days on the dedicated server).

---

## 14. Sources

- GeckoLib source, branch `1.21.1` (read with raw.githubusercontent.com): `gradle/libs.versions.toml`,
  `gradle.properties` (`modLicense=MIT`, group `software.bernie.geckolib`), `changelog.txt` (v4.9.3), and the classes
  listed in section 12. Main-branch `gradle.properties` (GeckoLib 5, group `com.geckolib`) and `LICENSE` (MIT).
- GeckoLib installation wiki: https://github.com/bernie-g/geckolib/wiki/Installation-(Geckolib4) (repository URL
  and `software.bernie.geckolib:geckolib-neoforge-${minecraft_version}:${geckolib_version}`).
- GeckoLib project and files: https://github.com/bernie-g/geckolib ,
  https://www.curseforge.com/minecraft/mc-mods/geckolib/files/7009924 (4.8, 606 KB) ,
  https://www.curseforge.com/minecraft/mc-mods/geckolib/files/8350073 (4.9.2, 2026-07-01) ,
  https://modrinth.com/mod/geckolib/version/Grwn5rUB , https://modrinth.com/mod/geckolib/version/z4VahJSu (5.0 alpha for 1.21.5) ,
  https://www.curseforge.com/minecraft/mc-mods/geckolib/files/7684556/dependencies (5.4.4 for 1.21.11).
- ModDevGradle README, Jar-in-Jar section: https://raw.githubusercontent.com/neoforged/ModDevGradle/main/README.md
- AzureLib: https://modrinth.com/mod/azurelib/version/3.0.39?dep=1 , https://www.curseforge.com/minecraft/mc-mods/azurelib/files/8267374 ,
  https://moddedmc.wiki/en/project/azurelib/latest , API-break evidence: https://www.modpackindex.com/mod/202891/eldritch-end-geckolib-patch
- playerAnimator: https://awesome.ecosyste.ms/projects/github.com%2Fkosmx%2Fminecraftplayeranimator ,
  https://moddex.gg/mod/playeranimator , https://www.curseforge.com/minecraft/mc-mods/player-animation-library/files/7611367
- GeckoLib performance add-ons (evidence that cost matters in big packs): https://www.curseforge.com/minecraft/mc-mods/geckolib-animation-optimizer ,
  https://modrinth.com/mod/geckolibbetterfps
- Minecraft 1.21.1 / NeoForge 21.1 decompiled sources in the scratchpad (`mcsrc/`): `HierarchicalModel`,
  `KeyframeAnimations`, `AnimationChannel`, `AnimationState`, `ModelPart`, NeoForge `AnimationLoader`,
  `AnimationParser`, `AnimationTarget`, `RegisterJsonAnimationTypesEvent`.
- Repository: `/home/user/test/build.gradle`, `settings.gradle`, `gradle.properties`, `neoforge.mods.toml` template,
  `FactionNpcRenderer`, `FactionNpc` (synced-data pattern), `ModEntities`, `docs/heroes/HERO_FORMAT.md` sections 10 and 24.
