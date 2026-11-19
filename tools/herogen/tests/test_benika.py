"""C2: Benika Akagane, the iron club oni, the third slice hero with her own source, art and signature animations.

Her one body (the owner's rule), her moves' frames equal to her kit's (Crash Waves, the ultimate's followup, with a hit
per ring), her sound markers, her clips' rules on every base and her club clear of her body; the phase-2 look (the
horn and the arm stripes in their own slot, flaring ember in the phase texture and glowing in its mask) and her
grinning faces; the gourd and the carried club that follow her hand (keyed on their own bones); and the generator pieces
she needed: the hero previews' locomotion for a charger and a leaper, her Rage drawn after phase 2's transition and
Crash Waves (a followup that never plays alone) drawn after the ultimate."""
import json
import os
import unittest

import numpy as np

import assets
import build
import grips
import palette
from anim import library
from anim import quality
from anim.clip import TICK, layered
from anim.skeleton import Skeleton
from rot import rotate_points
from util import ROOT

HID = "benika_akagane"
FIXTURE = os.path.join(ROOT, "src", "test", "resources", "heroes", "slice", HID + ".json")
UPPER = ("body", "waist", "chest", "neck")


def _ticks(s):
    return int(round(float(s) * 20))


class BenikaTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.hero = build.load_hero(HID)
        cls.files, cls.b = build.build_files(HID)
        cls.own = cls.b.animations
        cls.visual = json.loads(cls.files[build.out_paths(HID)["visual"]])
        with open(FIXTURE) as f:
            cls.kit = json.load(f)
        cls.model = cls.b.model
        cls.sk = Skeleton.from_model(cls.model)
        cls.d = cls.sk.d

    def lookup(self, name):
        for b in (self.own, library.style("hammer"), library.core()):
            if name in b.by_name:
                return b.by_name[name]
        return None

    def pose_at(self, name, t):
        clip = self.own.by_name[name]
        over = self.lookup(clip.meta["over"]) if clip.meta.get("over") else None
        if over is None:
            return clip.sample(t, self.d)
        bt = t % over.length if over.loop == "loop" else min(t, over.length)
        return layered(over, clip, t, bt, d=self.d)

    # -------------------------------------------------------------- body, kit, frames, rules

    def test_one_body_for_good(self):
        """An adult of ordinary size, large build, drawn at 108% (SLICE_PLAN 3.3; the owner's one-body rule of
        HEROES_V2 3.6): no phase changes the body."""
        art = self.hero["art"]
        self.assertEqual((art["form"], art["base"], art["build"], art["scale"]), ("adult", "female", "large", 108))
        self.assertEqual((self.visual["form"], self.visual["base"], self.visual["build"]), ("adult", "adult_f", "large"))
        for ph in art.get("phases", []):
            self.assertFalse({"form", "base", "build"} & set(ph), "a phase changes the body")

    def test_kit_is_the_fixtures(self):
        for k, v in self.kit.items():
            if k != "_comment":
                self.assertEqual(self.hero[k], v, k)

    def test_move_frames_equal_the_kit(self):
        """Every move's clip and the ultimate's have the kit's wind-up, active and recovery to the tick (from the
        action's start, the 2-tick blend included): the release on a `hit`, the active part's last tick on a `cast`;
        Crash Waves has a `hit` on each of its three rings, 0.6 s apart (the wave form's hit ticks, so the markers
        replace them: MovePlan)."""
        timing = self.own.timing_json()["animations"]
        items = [("move." + m["id"], m) for m in self.kit["moves"]] + [("ultimate", self.kit["ultimate"])]
        for name, m in items:
            want = {k: _ticks(v) for k, v in m["frames"].items()}
            self.assertEqual(timing[name]["frame_ticks"], want, name)
            hits = [e["tick"] for e in timing[name]["events"] if e["type"] == "hit"]
            if m.get("waves", 1) > 1:
                gap = _ticks(m["interval"])
                self.assertEqual(hits, [want["windup"] + i * gap for i in range(m["waves"])], name)
            else:
                self.assertEqual(hits, [want["windup"]], name)
        definition, _ = assets.definition_of(HID)
        files = dict(self.files, **library.core().files(), **library.style("hammer").files())
        self.assertEqual(assets.check_frames(definition, assets.Chain.of(self.visual, files)), [])

    def test_own_clips_pass_every_rule_on_every_base(self):
        self.assertEqual(quality.check_built(self.own, self.lookup, list(self.b.model.bones), own_file=True), [])
        self.assertLessEqual(len(self.own.clips), 24)
        self.assertNotIn("ultimate.loop", self.own.by_name)
        self.assertNotIn("escape", self.own.by_name)         # she never escapes (kit: a battle maniac)

    def test_club_clear_of_her_body(self):
        """The club through every tick of her own clips, on her own body (every second tick missed the club passing
        through her waist on the odd ticks of the climbs out of the crater and the hoists after each ring)."""
        self.assertEqual(grips.animation_problems(self.hero, self.b, self.own, self.lookup, every=1), [])

    def test_sound_markers(self):
        """Her client-played sounds are markers in the clips their slot names (SOUND.md 4.7, 5): each move's client
        `release` (the ultimate's leap too: `meteor_leap` plays on the client), the phases, the intro and the quirks;
        the server's slots (wind-ups, impacts, the stomp's and the swig's releases) are never markers."""
        with open(os.path.join(ROOT, "herosrc", "sfx", HID + ".sfx.json")) as f:
            sfx = json.load(f)
        sounds = {}
        for c in self.own.clips:
            for e in c.events:
                if e.kind == "sound":
                    sounds.setdefault(c.name, set()).add(e.value)
        pre = "factionclash:hero.%s." % HID
        for clip in sounds.values():
            for s in clip:
                self.assertTrue(s.startswith(pre) and s[len(pre):] in sfx["sounds"], s)
        hm = sfx["hero"]

        def plays(name):
            snd = sfx["sounds"][name]
            if "plays" in snd:
                return snd["plays"]
            return "client" if snd.get("kind") in ("voice", "emote", "swing", "cast", "foley", "quirk",
                                                   "loop") else "server"

        ult = self.kit["ultimate"]["id"]
        for mid, slots in hm["moves"].items():
            if mid == "basic" or "release" not in slots:
                continue
            clip = "ultimate" if mid == ult else "move." + mid
            if plays(slots["release"]) == "client":
                self.assertIn(pre + slots["release"], sounds.get(clip, set()), mid)
            else:
                self.assertNotIn(pre + slots["release"], sounds.get(clip, set()), mid)
        self.assertIn(pre + hm["phases"]["2"], sounds["phase.2"])
        self.assertIn(pre + hm["phases"]["3"], sounds["phase.3"])
        self.assertIn(pre + hm["intro"], sounds["intro"])
        for n in ("personal.1", "personal.2", "personal.3"):
            self.assertIn(pre + hm["quirks"][n], sounds[n], n)
        server = {pre + s for m in hm["moves"].values() for k, s in m.items() if k in ("windup", "impact")}
        for name, got in sounds.items():
            self.assertFalse(got & server, name)

    def test_faces_where_the_moment_needs_one(self):
        for c in self.own.clips:
            self.assertIn(("face_ctrl", "position"), c.tracks, c.name)

    def test_crash_waves_follows_the_crash(self):
        """Crash Waves (the ultimate's followup) starts on the pose the ultimate crashes into, so the server can play
        it straight after the ultimate's active part without a jump (the 2-tick blend carries nothing)."""
        ult = self.own.by_name["ultimate"]
        cw = self.own.by_name["move.crash_waves"]
        timing = self.own.timing_json()["animations"]["ultimate"]
        crash = (timing["frame_ticks"]["windup"] + timing["frame_ticks"]["active"] - 1 - 2) * TICK
        a, b = ult.sample(crash, self.d), cw.sample(0.0, self.d)
        for bone in ("body", "chest", "arm_r", "forearm_r", "hand_r", "arm_l", "leg_r", "shin_r"):
            for i in range(3):
                self.assertAlmostEqual(a[bone]["rotation"][i], b[bone]["rotation"][i], delta=2.0,
                                       msg="%s at the crash" % bone)

    # -------------------------------------------------------------- look

    def test_phase_two_flares_the_horn_and_the_stripes(self):
        """The horn and the arm stripes are painted in their own slot (`trim`, nothing else uses it), which phase 2
        turns ember, so the phase reads by day; both glow in the phase texture's mask only."""
        art = self.hero["art"]
        parts = {p["part"]: p for p in art["parts"]}
        self.assertEqual(parts["horns/single_curved"]["color"], "trim")
        self.assertEqual(parts["markings/arm_stripes"]["color"], "trim")
        users = [p["part"] for p in art["parts"] if "trim" in (p.get("color"), p.get("trim"), p.get("alt"),
                                                                 p.get("detail"))]
        self.assertEqual(sorted(users), ["horns/single_curved", "markings/arm_stripes"])
        self.assertNotIn("trim", (art["weapon"].get("color"), art["weapon"].get("trim"), art["weapon"].get("alt")))
        p2 = [ph for ph in art["phases"] if ph["phase"] == 2][0]
        before, after = palette.parse_hex(art["palette"]["trim"]), palette.parse_hex(p2["palette"]["trim"])
        self.assertGreater(sum(after) - sum(before), 120)             # much brighter: ember, not crimson
        paths = build.out_paths(HID)
        from PIL import Image
        import io
        glow = np.asarray(Image.open(io.BytesIO(self.files[paths["glowmask"]])).convert("RGBA"))
        glow2 = np.asarray(Image.open(io.BytesIO(self.files[paths["p2_glowmask"]])).convert("RGBA"))
        self.assertGreater(int((glow2[..., 3] > 0).sum()) - int((glow[..., 3] > 0).sum()), 20)

    def test_her_faces_grin(self):
        """A battle maniac's faces: happy, focused (every wind-up) and determined show the fang grin."""
        moods = self.visual["face"]["moods"]
        for m in ("happy", "focused", "determined"):
            self.assertEqual(moods[m]["mouth"], 7, m)
        self.assertEqual(moods["focused"]["eyes"], 8)
        self.assertEqual(self.hero["art"]["face"]["fang"], "both")

    # -------------------------------------------------------------- followers

    def _gourd_tie(self, w):
        e = self.model.env
        p = (-(e["hips_w"] / 2 - 1.0), e["waist_y"] - 3.65, -e["hips_d"] / 2 - 1.9)
        return self.sk.apply(w, "gourd", p)

    def test_the_gourd_moves_with_her_hand(self):
        """The swig and the idle sip key the gourd's own bone so it stays in the hand from the grab to the release
        (its tie within 1.2 px of where the hand took it, every tick), and hangs at rest outside that window."""
        for name, hand, grab, release in (("move.gourd_swig", "hand_l", 0.25, 1.55),
                                          ("personal.2", "hand_r", 0.35, 2.3)):
            w0 = self.sk.world(self.pose_at(name, grab))
            Rh, Th = w0[hand]
            local = Rh.T @ (self._gourd_tie(w0) - Th)
            worst = 0.0
            for i in range(_ticks(grab), _ticks(release) + 1):
                w = self.sk.world(self.pose_at(name, i * TICK))
                Rh, Th = w[hand]
                worst = max(worst, float(np.linalg.norm(Rh @ local + Th - self._gourd_tie(w))))
            self.assertLess(worst, 1.2, name)
            lifted = self.sk.world(self.pose_at(name, (grab + release) / 2))
            rest = self.sk.world(self.pose_at(name, 0.0))
            self.assertGreater(float(np.linalg.norm(self._gourd_tie(lifted) - self._gourd_tie(rest))), 8.0, name)
            clip = self.own.by_name[name]
            for t in (0.0, clip.length):
                g = clip.sample(t, self.d).get("gourd", {})
                self.assertLess(max(abs(v) for v in g.get("rotation", (0, 0, 0))), 0.5, name)
                self.assertLess(max(abs(v) for v in g.get("position", (0, 0, 0))), 0.05, name)

    def test_the_club_spin_keeps_the_carried_club_in_her_hand(self):
        """The idle club spin keys the carried copy's bone (weapon_back) so the club on her back comes into her hand,
        turns once round and goes back: every tick of the carry, the carried copy lies where the hand's copy would
        (its cubes' centres within 1.0 px), and the weapon flag is never touched (an idle never draws)."""
        clip = self.own.by_name["personal.3"]
        self.assertFalse([e for e in clip.events if e.kind == "instruction" and e.value.startswith("weapon:")])
        hand, back = {}, {}
        for c in self.model.cubes:
            if ":weapons/iron_club#" not in c.name:
                continue
            x0, y0, z0, x1, y1, z1 = c.box
            pts = np.array([(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)], dtype=float)
            if c.rotation is not None:
                pts = np.array(rotate_points(pts, c.rotation, c.pivot))
            key = c.name.split("#")[-1]
            if c.name.startswith("weapon_r:"):
                hand[key] = (c.bone, pts.mean(0))
            elif c.name.startswith("weapon_back:"):
                back[key] = (c.bone, pts.mean(0))
        worst, dirs = 0.0, []
        for i in range(_ticks(0.45), _ticks(2.65) + 1):
            w = self.sk.world(self.pose_at("personal.3", i * TICK))
            gap = max(float(np.linalg.norm(self.sk.apply(w, hand[k][0], hand[k][1]) -
                                           self.sk.apply(w, back[k][0], back[k][1]))) for k in hand)
            worst = max(worst, gap)
            R, _ = w["weapon_r"]
            dirs.append(R @ np.array([0.0, 0.0, -1.0]))
        self.assertLess(worst, 1.0)
        # it turns once round: the club points up, forward, down and back during the spin
        ups = [d[1] for d in dirs]
        fwd = [-d[2] for d in dirs]
        self.assertGreater(max(ups), 0.8)
        self.assertLess(min(ups), -0.8)
        self.assertGreater(max(fwd), 0.8)
        self.assertLess(min(fwd), -0.8)
        rest = clip.sample(0.0, self.d).get("weapon_back", {})
        self.assertLess(max(abs(v) for v in rest.get("position", (0, 0, 0))), 0.05)

    # -------------------------------------------------------------- previews

    def test_locomotion_follows_her_traits(self):
        """C2 (render/hero_previews.loco_segments): a charger gets the charging run and the skid, a leaper the crouch
        jump, the leap and the heavy landing (in place of the plain jump); Hinae (a runner and swimmer) keeps her
        set."""
        from render import hero_previews as hp

        class A:
            def __init__(self, mob):
                self.hero = {"mobility": mob}
                self.visual = {"base": "adult_f"}

        mine = hp.loco_segments(A(self.hero["mobility"]))
        clips = [n for s in mine for n, _ in s.items]
        for n in ("move.walk", "move.run", "move.charge_run", "move.skid", "move.crouch_jump", "move.leap",
                  "move.land_heavy", "move.climb"):
            self.assertIn(n, clips)
        self.assertNotIn("move.jump", clips)
        hinae = build.load_hero("hinae_shirasuzu")
        theirs = [n for s in hp.loco_segments(A(hinae["mobility"])) for n, _ in s.items]
        self.assertEqual(theirs, ["move.walk", "move.run", "move.jump", "move.leap", "move.land", "move.swim",
                                  "move.climb"])

    def test_rage_is_drawn_in_phase_two_after_its_transition(self):
        """C2 (render/hero_previews): a state a boss phase enters (Rage, from phase 2) is drawn in that phase's look,
        plays after the phase's transition in moments.gif (as HeroBoss starts it when the transition ends), and is
        not the ultimate's (ultimate.gif); Natsuho's frenzy, which no phase enters, still follows her ultimate's
        hit."""
        from render import hero_previews as hp
        a = hp.assets(HID)
        self.assertEqual(hp.phase_of_clip(a, "state.rage"), 2)
        shots = hp.gif_shots(a)
        p2 = [s for s in shots["moments.gif"] if s.actions[0] == "phase.2"][0]
        self.assertEqual(p2.actions, ["phase.2", "state.rage"])
        self.assertAlmostEqual(p2.at["state.rage"], hp.LEAD + 1.2)
        self.assertEqual(p2.phase, 2)
        self.assertNotIn("state.rage", shots["ultimate.gif"][0].actions)
        nat = hp.gif_shots(hp.assets("natsuho_komezaki"))
        self.assertEqual(nat["ultimate.gif"][0].actions, ["ultimate", "state.frenzy"])

    def test_crash_waves_is_shown_after_the_ultimate(self):
        """C2: a move that only ever follows another (weight 0: Crash Waves after Iron Mountain Crash) is filmed after
        it, starting one tick after the ultimate's last hit or cast (the end of its active part, where the move runner
        starts the followup), in its own GIF and in ultimate.gif."""
        from render import hero_previews as hp
        a = hp.assets(HID)
        shots = hp.gif_shots(a)
        want = hp.LEAD + max(a.chain.get("ultimate").hits()) + TICK
        for name in ("move_crash_waves.gif", "ultimate.gif"):
            s = shots[name][0]
            self.assertEqual(s.actions, ["ultimate", "move.crash_waves"], name)
            self.assertAlmostEqual(s.at["move.crash_waves"], want, msg=name)
        self.assertEqual(shots["move_ground_stomp.gif"][0].actions, ["move.ground_stomp"])   # weight 1: alone

    def test_the_lineup_takes_her(self):
        from render import hero_previews as hp
        ids = hp.lineup_ids()
        self.assertIn(HID, ids)
        self.assertEqual(ids[-1], "test_rig")


if __name__ == "__main__":
    unittest.main()
