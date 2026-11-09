# Faction Clash — notes for Claude

A NeoForge 21.1.209 / Minecraft 1.21.1 / Java 21 mod: autonomous factions with player-like NPC workers, scouts,
recruitment, raids and pacts. Owner: rodrigomb47. **Before doing anything substantial, read `docs/HANDOFF.md`**
(state of the project, history of requests, architecture map, how to verify in a real campaign, open items).
README.md describes the mod for players; BALANCE.md lists every tunable and PROPOSED constant.

## Working rules (the owner's, keep them)
- Develop and push only on branch `claude/minecraft-mod-build-9amjyg`
  (`git push -u origin claude/minecraft-mod-build-9amjyg`, retry up to 4x with 2/4/8/16 s backoff on network errors).
  No pull request unless the owner asks.
- Commits: `git commit --author="rodrigomb47 <rodrigo_caderno@hotmail.com>" ...` and do NOT override user.name or
  user.email. The committer must stay the configured identity (Claude <noreply@anthropic.com>), so commits are
  signed and GitHub shows them as verified. End every commit message with:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and the current session's `Claude-Session:` line (the
  session gives it). Never put model identifiers anywhere else (code, comments, docs).
- Report to the owner in **Portuguese** (pt-BR). Code, comments, docs and commit messages stay in English.
- The owner welcomes improvements you think are worth it (new roles, affixes, fixes), as long as you say what you
  added. Mod-wide behaviour stays "player-like": NPCs break blocks at player speed, use vanilla loot tables, walk
  (no teleporting except stuck recovery), and only know what scouts saw.
- When behaviour changes, update README.md / BALANCE.md, regenerate `docs/factionclash-common.toml` (copy the file
  the mod writes on first start), rebuild `download/factionclash-0.1.0.jar` and put its SHA-256 on README line 13.

## Build and test
- Compile: `./gradlew build --console=plain -q -x test 2>&1 | grep -E "error|warning: \[" | head`
- Unit tests (pure logic in `src/test/java/com/factionclash/logic`): `./gradlew test --console=plain`
- GameTests (real headless server): `timeout 900 ./gradlew runGameTestServer --console=plain > gt.log 2>&1; grep -E
  "required tests passed|failed" gt.log` → "All N required tests passed :)". Counts at handoff: 139 unit, 92 GameTests.
- GameTest facts: the test world is FLAT near y=-60 (no mine layer is legal there; heightmaps need
  `skyAccess = true`); platforms are ~29 blocks apart and never cleared; `GameTestHelper.relativePos` is not the
  inverse of `absolutePos` (spawn at known relative positions); every test that runs a job gets its own
  `batch = "..."`, static checks share `workers_static`; register test bases with `GameTestBases.register(...)`
  (transient testHalf/testRadius) and always clean up.
- A change is verified only when it also works in a multi-day campaign on a dedicated server: see
  `docs/HANDOFF.md` → "Campaign verification" (`tools/setup_server.sh`, `tools/days.sh`, `tools/sim.py`).
