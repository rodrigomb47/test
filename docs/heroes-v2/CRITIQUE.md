# Heroes v2: critic pass (2026-11-12)

Scope: `HEROES_V2.md`, `ROSTER_PLAN.md`, `SLICE_PLAN.md` and `roster/`. I checked them against the GeckoLib sources in
`gl/`, the published GeckoLib jar, the decompiled 1.21.1 sources (`../mcsrc/`) and the repository code. The repository
was not changed. The three research reports were read but not edited; where they differ from the design (48 bones, a
blocked host, 0.02 px face planes, about 64 GameTests), the design wins, as it says.

## Verdict

The design is sound and close to implementable. These parts hold up:
- the GeckoLib choice and how it is bundled;
- the face-frame approach to blinking and expressions;
- the synced-int animation drive;
- the personality formulas (I recomputed every worked number and checked every "today" constant in the code);
- the boss toolkit, which uses vanilla hooks throughout.

The defects I found were inconsistencies and gaps, not wrong directions. All of them are fixed below except the items
under "Still open".

## What was verified (facts, not assumptions)

**GeckoLib download.**
- `dl.cloudsmith.io` answered (HTTP 200) on 2026-11-12. It had been refused on 2026-11-11.
- `software.bernie.geckolib:geckolib-neoforge-1.21.1:4.9.3` exists and is the latest version.
- The jar is 634,531 bytes. Its SHA-1 `5cfe8354...54d5ac` matches the published `.sha1`. Its SHA-256 is `20a1995e...592d1c47`.
- The POM has no dependencies. The `.module` variants have no dependencies either.
- The jar's `neoforge.mods.toml` declares MIT, `neoforge [21.1.150,)` and `minecraft [1.21,)`, plus one mixin config and
  an access transformer. The MIT text ships inside the jar as `GECKOLIB_LICENSE`.
- Bundling MIT code in an all-rights-reserved jar is fine as long as that notice is kept.

**GeckoLib behaviour** (checked in the sources):
- Fallback lookup goes through `getAnimationResourceFallbacks`.
- Later controllers override earlier ones per bone and per channel.
- Missing bones are skipped while `crashIfBoneMissing()` is false, its default.
- Nothing blends when a controller stops. Bones snap, and only bones no controller keys reset to the rest pose.
- Baked UVs are normalised.
- `step` easing exists; easing names are lower case internally.
- `timeline`, `sound_effects` and `particle_effects` keys are parsed.
- One `GeoModel` switches between baked models per entity.
- `GeoEntityRenderer` applies `LivingEntity.getScale()`.
- The `_glowmask` suffix is appended to the current texture, so a `_p2` texture gets `_p2_glowmask`.

**Minecraft 1.21.1:**
- The `Display` and `BlockDisplay` setters are private, so the access transformer list is needed.
- `Player.disableShield()` is public.
- `isBlocking()` needs the shield raised for 5 ticks.

**Repository constants match the design's "today" column:**
- raid interval 36000;
- counter-attack at 1.5x (`partyPower*2 >= defence*3`);
- attack odds 2/3;
- garrison a third (`(n+2)/3`);
- fall back at half the party;
- soldier cap 12, home swordsmen 3, `rangedShare` 0.4;
- trader at 14 people;
- house when free beds <= 1;
- storage at 70%;
- repair budget 384;
- trader trips every 12000 ticks;
- `FactionNpc(EntityType<? extends FactionNpc>)` can be subclassed.

**Personality numbers.** Every worked number in section 5.5 recomputes exactly from the appendix A defaults.

**Roster.**
- `check_roster.py` passes: 200 rows, exactly 100 female and 100 male, every role split within one.
- The outputs regenerate byte-identically from `roster_data.py`.

**Slice kits.**
- Every move's frames meet its class minimum.
- Every followup string is 4 moves or fewer.
- Every trait is within ±20 of its temperament default.
- Draft rule 5 holds for runs A and B.

## Changes made

### HEROES_V2.md

**Build (4.2, A0, 16, 17).**
- Recorded the verified coordinates, hashes, licence file and `mods.toml` facts.
- Added the `.module` file to the vendored files, a hand-written `maven-metadata.xml` and `metadataSources`.
- The network approval is now "only if the host is refused again" everywhere.

**Director and heroes (3.2).** The director's soldier sort would have put heroes into raid parties. New rule: a hero
counts as a soldier but is never sorted into a raid; leaders join by the `LeaderBiases` rule. Added GameTest
`heroNotDraftedIntoRaidByDefault`.

**Hitbox (3.6).** `generic.scale` also scales the hitbox, so a state size on top of a large hero broke the 1.98 cap. The
cap is now an explicit `getDefaultDimensions` clamp.

**Bones (4.3).**
- The rig has 61 bones, but the budget said at most 48, and a nine-tailed hero needs about 67.
- Defined the 33 required bones, made the others optional, set extra tails to 2 bones each and raised the budget to 72.
- Cubes (140) stay the performance budget.

**Animation (4.5, 4.8, 13.6, 14.2).**
- GeckoLib does not blend when the action controller stops. Action animations now must end on the idle pose
  (a settle key, enforced by the checker).
- Documented the optional-bone and model-switch behaviour.
- Accepted the late-tracker desync explicitly.

**Faces (4.6, 10.1, 13.3, 13.5).**
- The plane positions were missing. Added a layout table: eyes at y 27-31, cheeks at 26-27, mouth at 24-26.
- Fixed z-fighting by construction: the head front is transparent under the opaque planes, which the 0.02 px offset did
  not prevent beyond about 30 blocks.
- The UV shift is now normalised.
- `face_ctrl` values are used only when they are whole numbers.
- `DATA_STATE` bit 6 covers wind-ups as well as channels.
- Defined the eye texel layout and cut eye width to 4-6 texels so the eyes clear the side locks.
- The bangs example covered the eyes. Fixed it, and the checker now rejects anything in front of the face below y 30.

**Tint (4.7).** `HeroTintLayer`, which re-rendered the model, became a per-bone vertex colour in `renderCubesOfBone`.
It is the same effect with no second draw.

**Leaders (5.1, 5.2, 5.4, 5.5, 5.6, decision 8).**
- `dye_primary` now comes from the banner's base dye. The nearest dye by Lab distance maps dark primaries to gray or
  black.
- The draft also requires distinct dyes. Without that, two factions can build in the same wool.
- `t_eff` is clamped to 0..1. At k=2 the products became nonsense.
- The vague "miners' layer bias" is now a multiplier on `MinePlanner`'s `GOLD_WANTED` and `DIAMONDS_WANTED`.
- Added an `economy_focus` to roles table, switched off at k=0.
- "k=0 reproduces today" now says director numbers. `leaders.enabled=false` reproduces the campaign.
- The leader workplace rule excludes travelling jobs (scout, trader).

**Boss mode.**
- *Health (6.2, 12.4, 12.10).* Added a per-role boss health factor (1.0-1.3). Fragile roles were ending fights in two
  thirds of a vanguard's time. The example is now tied to a reference duel.
- *Decals (6.4).*
  - A disc is two squares forming an octagon.
  - The interpolation starts one tick after spawn.
  - Brightness is overridden.
  - The full access transformer list is given.
  - The ultimate's darkening uses `ServerBossEvent.setDarkenScreen`.
- *Move classes (6.4).* Added a default class for every form. A damaging move can no longer be authored lighter than its
  form; it was a fairness loophole.
- *Grab (6.5).* The hold is done with velocity, never a teleport.
- *Parry and weak points (6.9).* The parry window now uses exact vanilla ticks. Every weak point has a named vanilla
  event hook.
- *Decoys (6.11).* Added a second tell: decoys make no footstep sounds. Shadows can be switched off or dropped by shader
  packs.
- *Weight (12.7).* Weight 0 is allowed for moves used only as followups or reactions.

**Textures (13.2).** Fractional cube sizes need whole-texel per-face UVs. Box UV would put faces on half texels.

**Checks and tests (14.2-14.5).**
- Added distinctness NOTEs: silhouette overlap and palette distance within an archetype.
- Added `HeroFaceRulesTest`; the face logic is now pure logic.
- Added 12 talent GameTests. The slice criteria promised a test for every talent, but only 3 were listed.
- Added four test-only fixture heroes: rare, legendary, bow and summoner. The slice is all epic, so without them those
  engine paths go untested.
- New count: 55 plus about 26 from v1, so about 81 new GameTests.

**Work breakdown (15).**
- The critical path omitted F1 and F2. It is now F1 → F2 → A1 → A3 → A5 → A6 → V1 → V3.
- Fixed the G3 and A2 circular "done when".
- Split G6 into G6a, G6b and G6c.
- `sim.py` lines and the operator test hooks are now assigned to tasks.
- L5 is now size L.
- V3 lists every run.
- Content batches are 4 heroes per authoring task, not 8. Eight full heroes do not fit one session.

### SLICE_PLAN.md

**Colours.** The claim "delta-E over 25 for every pair" was false: two pairs were about 22. Ilsabet's faction primary
became `#5E9BD1` and Ambrose's became nettle green `#4E7A2E` with a green banner. The minimum is now 27.5, and all eight
banner dyes differ.

**Criterion 15.** "45-150 s of hitting" could not hold: the design numbers gave 18-36 s. It is now 20-60 s, with a
computed table of 24-39 s per hero under the role factor.

**Criterion 23.** Updated for the talent tests.

**Coverage.** Added a note on what the eight heroes do not cover and where it is covered instead.

**Kit values out of the format's ranges:**
- the ultimate leap range was 12 (the maximum is 10);
- one sweep lasted 0.6 s (the minimum is 1.0);
- a taunt in a friendly form would have been left out;
- a damaging cone was marked heavy instead of area.

**Other.** Leader court duties, budgets, test counts, prerequisite and checkpoint 1 now match the design.

### Roster

**Names.** Ten working names matched a known character, or a real performer in the same art, in their own archetype
(the review records only row numbers). They were renamed:

| Row | New name |
|---|---|
| 5 | Torakichi Fumizuki |
| 75 | Sumire Ayatsuki |
| 82 | Kotoha Fujinami |
| 84 | Kikuzo Hanaoka |
| 86 | Gosuke Banba |
| 129 | Faelan Oakshade |
| 132 | Lazare Vespertine |
| 169 | Ignaz Coggleby |
| 178 | Sorane Hoshizaki |
| 180 | Akito Hoshinaga |

**Looks.** Five looks repeated two or three defining marks of a famous design. Rows 46, 96, 98, 188 and 189 were changed
(hair, outfit colours or prop), and their palettes stay unique within their archetype.

**Checker.** `check_roster.py` now rejects reuse of the rejected name tokens; a negative test was run.

**Generated files.** `gen_roster_md.py` now says 4 heroes per authoring task and adds a "what a search looks for" step to
section 8. `ROSTER_PLAN.md` and `roster.json` were regenerated, and the checker prints OK.

## Still open

1. **Names are only desk-reviewed.** Clearance steps 1-2 (web name and concept searches) have not run for any row or any
   faction name. Low-risk rows to search first: 3, 7, 53, 76, 112, 118, 145, 163, 166. In each, a single common name or
   a public-domain surname coincides with a known figure; the concept differs.
2. **Art quality is unproven.** It is still the largest risk. Nothing is generated yet; checkpoint 2 (Natsuho finished
   in game) is the real test. Plan for some hand polish in Blockbench if the generated look falls short.
3. **GeckoLib facts are read, not compiled.** A0 must build with the vendored jar and run both test suites on the
   headless server. If the host is refused again, the owner must approve it once.
4. **Every number is PROPOSED:** boss health, the role factors, poise, the knobs and the talent tiers. They are tuned in
   the viewer session and the campaign runs.
5. **Performance is unmeasured:** render cost per hero, heap with 200 baked models, reload time, jar size.
6. **Owner questions are unanswered:** the look, owner-made heroes, review cadence, and the network prompt if needed.
7. **Scope is very large.** After milestone 2 there are about 48 authoring tasks plus review, clearance and smoke tasks
   for 192 heroes.
8. **The roster leans Japanese:** 93 of 200 heroes are Japanese-themed (animal-kin, yokai and court Japan). This fits
   the request's references, but the owner may want more Western or modern heroes.
9. **Research reports are stale** in the places listed at the top. They are inputs, not specifications; refresh them
   if they move to the repository with the design.
10. **Late trackers** see a wind-up start from frame 0 (cosmetic, accepted).
11. **A6 may need splitting** into A6a and A6b; the split is pre-defined in the task row.
