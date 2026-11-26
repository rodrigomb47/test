"""C3: Kagenori Tsurumine, the still crane samurai, the fifth slice hero with his own source, art and signature
animations.

His one body (the owner's rule), his moves' frames equal to his kit's (Three Cuts' three strikes, Chained Lightning
Draw's three jumps), his sound markers, his clips' rules on every base and both blades clear of his body; the iai
trick his draws rest on (the held blade laid exactly into its scabbard, so a move may sheathe and draw while the
server's weapon flag stays drawn) holding in every hold; his long high ponytail kept off his face in the deep folds;
his phase looks (the helmet from phase 2, the short sword, the glowing eyes and edges from phase 3) and the moments
that put them on; and the generator pieces he needed: the kabuto's `lames` param, the leaper II landing roll in the
hero previews and G10's crane-leap GIF drawn from his own source."""
import copy
import json
import os
import unittest

import numpy as np

import helpers  # noqa: F401
import assets
import build
import grips
from anim import library
from anim import quality
from anim.clip import form_var
from anim.skeleton import Skeleton
from util import HerogenError, ROOT

HID = "kagenori_tsurumine"
FIXTURE = os.path.join(ROOT, "src", "test", "resources", "heroes", "slice", HID + ".json")
# the iai holds: (clip, first tick, last tick) where the long blade rests home in its scabbard in his hand
HOLDS = (("move.iai_stance", 6, 26), ("move.thunderclap_draw", 6, 22), ("move.thunderclap_draw", 36, 39),
         ("ultimate", 6, 36), ("intro", 5, 25))
OWN = {"move.three_cut", "move.iai_stance", "move.thunderclap_draw", "move.crane_step", "move.guard_splitter",
       "move.chain_draw", "move.falling_crane", "ultimate", "state.twin_sky", "phase.2", "phase.3", "intro",
       "victory", "taunt", "last_stand", "personal.1", "personal.2", "personal.3"}


def _ticks(s):
    return int(round(float(s) * 20))


def _cube(model, name):
    return [c for c in model.cubes if c.name == name][0]


class KagenoriTest(unittest.TestCase):
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
        cls.d = form_var(cls.model.env)

    def lookup(self, name):
        for b in (self.own, library.style("katana"), library.core()):
            if name in b.by_name:
                return b.by_name[name]
        return None

    def poses(self, name):
        return grips.clip_poses(self.own.by_name[name], self.lookup, self.d, 1)

    def gap(self, pose, held, sheathed):
        """How far (px) a held cube lies from its sheathed twin in a pose: the Hausdorff distance of their sample
        points (the sheathed copy's box has its axes in another order, so the points are compared as sets)."""
        w = self.sk.world(pose)
        a, b = _cube(self.model, held), _cube(self.model, sheathed)
        ra, ta = w[a.bone]
        rb, tb = w[b.bone]
        pa = grips._samples(a) @ ra.T + ta
        pb = grips._samples(b) @ rb.T + tb
        dist = np.linalg.norm(pa[:, None, :] - pb[None, :, :], axis=2)
        return float(max(dist.min(axis=1).max(), dist.min(axis=0).max()))

    # -------------------------------------------------------------- body, kit, frames, rules

    def test_one_body_for_good(self):
        """An adult man of ordinary size, standard build, drawn at 102% (SLICE_PLAN 3.5; the owner's one-body rule of
        HEROES_V2 3.6): no phase changes the body."""
        art = self.hero["art"]
        self.assertEqual((art["form"], art["base"], art["build"], art["scale"]), ("adult", "male", "standard", 102))
        self.assertEqual((self.visual["form"], self.visual["base"], self.visual["build"]),
                         ("adult", "adult_m", "standard"))
        for ph in art.get("phases", []):
            self.assertFalse({"form", "base", "build"} & set(ph), "a phase changes the body")
        self.assertNotIn("tail_1", self.model.bones)          # no tail: the tail rule has nothing to hold

    def test_kit_is_the_fixtures(self):
        for k, v in self.kit.items():
            if k != "_comment":
                self.assertEqual(self.hero[k], v, k)

    def test_budgets(self):
        """Two swords, a helmet from phase 2 and the short sword's shells from phase 3 inside HEROES_V2 4.3's budgets
        (100 bones, 300 cubes, 130 cubes outside fine_ bones) and SLICE_PLAN 3.9's 72 bones."""
        problems, g = assets.check_geometry(HID, self.files, self.visual)
        self.assertEqual(problems, [])
        self.assertLessEqual(len(g.order), 72)
        self.assertLessEqual(len(g.cubes), 300)
        import validate
        self.assertLessEqual(validate.lod_cubes(g), 130)

    def test_move_frames_equal_the_kit(self):
        """Every move's clip and the ultimate's have the kit's wind-up, active and recovery to the tick (from the
        action's start, the 2-tick blend included): the release on a `hit`, the active part's last tick on a `cast`;
        Three Cuts has a `hit` per strike and Chained Lightning Draw one per jump (the markers replace the form's hit
        ticks: MovePlan)."""
        timing = self.own.timing_json()["animations"]
        items = [("move." + m["id"], m) for m in self.kit["moves"]] + [("ultimate", self.kit["ultimate"])]
        for name, m in items:
            want = {k: _ticks(v) for k, v in m["frames"].items()}
            self.assertEqual(timing[name]["frame_ticks"], want, name)
            hits = [e["tick"] for e in timing[name]["events"] if e["type"] == "hit"]
            w, a = want["windup"], want["active"]
            if m["form"] == "combo":
                self.assertEqual(hits, [w, w + (a - 1) // 2, w + a - 1], name)
            elif m["form"] == "chain":
                self.assertEqual(hits, [w + 3 * i for i in range(m["count"])], name)
            else:
                self.assertEqual(hits, [w], name)
        self.assertEqual([e["tick"] for e in timing["move.three_cut"]["events"] if e["type"] == "hit"], [9, 13, 17])
        definition, _ = assets.definition_of(HID)
        files = dict(self.files, **library.core().files(), **library.style("katana").files())
        self.assertEqual(assets.check_frames(definition, assets.Chain.of(self.visual, files)), [])

    def test_own_clips_pass_every_rule_on_every_base(self):
        """Every own clip passes the quality rules on all six bases; he never escapes (SLICE_PLAN 3.5: he does not
        turn his back), so no escape clip; no ultimate.loop (the engine never sends ultimate.end)."""
        self.assertEqual(quality.check_built(self.own, self.lookup, list(self.b.model.bones), own_file=True), [])
        names = {c.name for c in self.own.clips}
        self.assertEqual(names - {"move.leap"}, OWN)
        self.assertIn("move.leap", names)                    # G10's crane leap (variant crane_leap)
        self.assertNotIn("escape", names)
        self.assertNotIn("ultimate.loop", names)

    def test_both_blades_clear_of_his_body(self):
        """The long sword and the short sword (in his left hand from phase 3; checked in every clip, since any move
        may play in phase 3) clear of his body on every tick of his own clips."""
        self.assertTrue(grips.prop_cubes(self.model, "l"))   # the short sword is a prop of the left hand
        self.assertEqual(grips.animation_problems(self.hero, self.b, self.own, self.lookup, every=1), [])

    def test_the_blade_is_home_in_every_iai_hold(self):
        """The iai trick: in a move the server keeps the weapon drawn, so his draws sheathe the blade by laying the
        held copy exactly where the sheathed one would be (the hand on the hilt). On every tick of every hold the
        held hilt lies within 0.7 px of the sheathed copy's and the guard within 1.0 px (the blade's roll is left to
        the wrist: the guard, 2.3 by 2.5, sits turned 20 degrees off the sheathed copy's edge-up roll, which the wrist
        cannot reach; measured 0.58 and 0.81)."""
        for name, t0, t1 in HOLDS:
            poses = self.poses(name)
            for i in range(t0, t1 + 1):
                t, pose, drawn = poses[i]
                self.assertTrue(drawn, (name, t))
                for k, most in (("0", 0.7), ("2", 1.0)):
                    gap = self.gap(pose, "weapon_r:weapons/katana#" + k, "weapon_back:r:weapons/katana#" + k)
                    self.assertLess(gap, most, (name, t, k))
        # and it does leave the scabbard: the riposte, the thunderclap and the heaven-splitting draw
        for name, tick in (("move.iai_stance", 28), ("move.thunderclap_draw", 24), ("ultimate", 38)):
            pose = self.poses(name)[tick][1]
            self.assertGreater(self.gap(pose, "weapon_r:weapons/katana#0", "weapon_back:r:weapons/katana#0"), 8.0)

    def test_the_short_sword_is_drawn_from_its_scabbard_in_phase_3(self):
        """phase.3 opens with his left hand on the short sword's hilt still in its scabbard (its held copy laid on the
        sheathed one) and draws it out by its end."""
        poses = self.poses("phase.3")
        held, sheathed = "p3_weapon_l:p3_weapons/katana#0", "p3_weapon_back:l:p3_weapons/katana#0"
        for i in range(0, 11):
            self.assertLess(self.gap(poses[i][1], held, sheathed), 0.2, poses[i][0])
        self.assertGreater(self.gap(poses[17][1], held, sheathed), 8.0)

    def test_the_ponytail_stays_off_his_face(self):
        """His long high ponytail hangs (the bake's `hang`): in a deep fold forward it would swing down over his face.
        No strand point of it comes in front of the face on any tick of his own clips (head space: within the face's
        width and height, nearer the front than z -1)."""
        m = self.model
        hp = np.array(m.bones["head"].pivot, float)
        p2 = np.array(m.bones["tail_hair_2"].pivot, float)
        d = np.array([0, -1, 0.1]) / np.linalg.norm([0, -1, 0.1])
        for clip in self.own.clips:
            for t, pose, _ in grips.clip_poses(clip, self.lookup, self.d, 1):
                w = self.sk.world(pose)
                rh, th = w["head"]
                r2, t2 = w["tail_hair_2"]
                for f in (0.2, 0.4, 0.6, 0.8, 1.0):
                    q = rh.T @ (r2 @ (p2 + d * 14.4 * f) + t2 - th) - hp
                    self.assertFalse(abs(q[0]) < 6 and -1 < q[1] < 11 and q[2] < -1, (clip.name, t, f))

    def test_his_arms_and_blades_clear_the_helmet(self):
        """From phase 2 he wears the kabuto, and every move may play under it: on every tick of his own clips and his
        basic string, no point of either forearm, hand or sleeve, nor of either blade, lies more than 0.5 px inside a
        cube of the helmet (a sleeve may brush a lame's tip). phase.2 is left out (its left hand holds the helmet),
        and so are the idle quirks, which play out of a fight. Guard Splitter's wind-up, first solved over the bare
        head, held the sword hand 1.25 px inside the helmet's bowl."""
        m = self.model
        hat = [c for c in m.cubes if c.bone in grips._subtree(m, {"p2_hat"})]
        self.assertGreater(len(hat), 20)
        movers = []
        for side in ("r", "l"):
            arm = grips._subtree(m, {"forearm_" + side, "hand_" + side}) - grips._subtree(m, {"weapon_" + side})
            movers += [c for c in m.cubes if c.bone in arm and not c.part.startswith("weapons/")]
            movers += grips.prop_cubes(m, side)
        samples = [(c.bone, grips._samples(c)) for c in movers]
        frames = [(b, np.array(b.box), np.array(b.pivot, float) if b.rotation is not None else None,
                   grips.file_matrix(b.rotation) if b.rotation is not None else None) for b in hat]
        clips = [c for c in self.own.clips if c.name != "phase.2" and not c.name.startswith("personal.")]
        clips += [library.style("katana").by_name["attack.%d" % i] for i in (1, 2, 3)]
        worst = (0.0, None)
        for clip in clips:
            for t, pose, _ in grips.clip_poses(clip, self.lookup, self.d, 1):
                w = self.sk.world(pose)
                pts = np.concatenate([p @ w[bone][0].T + w[bone][1] for bone, p in samples])
                for b, box, pv, fm in frames:
                    rb, tb = w[b.bone]
                    local = (pts - tb) @ rb
                    if fm is not None:
                        local = pv + (local - pv) @ fm
                    depth = float(grips._depth_inside(local, box).max())
                    if depth > worst[0]:
                        worst = (depth, (clip.name, t, b.name))
        self.assertLessEqual(worst[0], 0.5, worst)

    def test_sound_markers(self):
        """His client-played sounds are markers in the clips their slots name (SOUND.md 4.7, 5): the client
        `release` of Three Cuts (one per cut), Still Water, Crane Step, Guard Splitter and Falling Crane, Thunderclap
        Draw's `end` (the re-sheathe), the phases, the intro and the quirks; the server's slots (wind-ups, impacts,
        the thunderclap and the heaven split) are never markers."""
        with open(os.path.join(ROOT, "herosrc", "sfx", HID + ".sfx.json")) as f:
            sfx = json.load(f)
        sounds = {}
        for c in self.own.clips:
            for e in c.events:
                if e.kind == "sound":
                    sounds.setdefault(c.name, []).append(e.value)
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
            if mid == "basic":
                continue
            clip = "ultimate" if mid == ult else "move." + mid
            for slot in ("release", "end"):
                if slot not in slots:
                    continue
                if plays(slots[slot]) == "client":
                    self.assertIn(pre + slots[slot], sounds.get(clip, []), (mid, slot))
                else:
                    self.assertNotIn(pre + slots[slot], sounds.get(clip, []), (mid, slot))
        self.assertEqual(sounds["move.three_cut"].count(pre + "katana_swing"), 3)
        self.assertNotIn(pre + "thunderclap", sounds.get("move.thunderclap_draw", []))
        self.assertIn(pre + hm["phases"]["2"], sounds["phase.2"])
        self.assertIn(pre + hm["phases"]["3"], sounds["phase.3"])
        self.assertIn(pre + hm["intro"], sounds["intro"])
        for n, s in hm["quirks"].items():
            self.assertIn(pre + s, sounds[n], n)
        server = {pre + s for m in hm["moves"].values() for k, s in m.items() if k in ("windup", "impact")}
        server |= {pre + hm["states"]["twin_sky"]}
        for name, got in sounds.items():
            self.assertFalse(set(got) & server, name)

    def test_faces(self):
        """face_ctrl in every clip; his happy is his focused eyes with a faint smile (SLICE_PLAN 3.5); the moves leave
        the eyes to the mood (the wind-up's focused eyes, phase 3's glow), and the ultimate closes them for its
        stillness and opens them before the cut."""
        for c in self.own.clips:
            if c.name != "move.leap":
                self.assertIn(("face_ctrl", "position"), c.tracks, c.name)
        moods = self.visual["face"]["moods"]
        self.assertEqual((moods["happy"]["eyes"], moods["happy"]["mouth"]), (8, 1))
        tr = self.own.by_name["ultimate"].tracks[("face_ctrl", "position")]
        eyes = {round(t, 2): tr.sample(t)[0] for t in (0.3, 0.6, 1.2, 1.7, 1.8, 1.9)}
        self.assertEqual(eyes[0.3], 0)                       # the mood's
        for t in (0.6, 1.2, 1.7):
            self.assertEqual(eyes[t], 3, t)                  # closed (frame 2, +1)
        self.assertEqual(eyes[1.8], 0)                       # open again before the hit at 1.90
        for n in ("move.three_cut", "move.iai_stance", "move.crane_step", "move.guard_splitter", "move.chain_draw",
                  "move.falling_crane"):
            tr = self.own.by_name[n].tracks[("face_ctrl", "position")]
            self.assertTrue(all(k.v[0] == 0 for k in tr.keys), n)

    # -------------------------------------------------------------- phase looks

    def test_phase_looks(self):
        """Phase 2 puts the plain helmet on (its bones show from phase 2); phase 3 lights his eyes and both blades'
        edges and brings the short sword into his left hand; the helmet lies inside the fine distance's budget with
        nine lames."""
        from render import hero_previews as hp
        bones = self.visual["bones"]["phase"]
        hat = [b for b in bones if b.startswith("p2_")]
        self.assertTrue(hat)
        self.assertTrue(all(bones[b] == 2 for b in hat))
        self.assertEqual(bones.get("p3_weapon_l"), 3)
        glow = [c for c in self.model.cubes if c.name.endswith("glow")]
        self.assertEqual({c.bone for c in glow}, {"p3_weapon_r", "p3_weapon_l"})
        self.assertTrue(any(p.get("eyes_glow") for p in self.hero["art"]["phases"] if p["phase"] == 3))
        a = hp.assets(HID)
        hidden = {ph: a.hidden(phase=ph, drawn=True) for ph in (1, 2, 3)}
        self.assertTrue(set(hat) <= set(hidden[1]) and not set(hat) & set(hidden[2]))
        self.assertIn("p3_weapon_l", hidden[2])
        self.assertNotIn("p3_weapon_l", hidden[3])

    def test_phase_2_puts_the_helmet_on(self):
        """phase.2 starts with the helmet held up over his head (p2_hat lifted) and sets it down on it."""
        tr = self.own.by_name["phase.2"].tracks[("p2_hat", "position")]
        self.assertGreater(tr.sample(0.0)[1], 4.0)
        self.assertEqual(tuple(round(v, 6) for v in tr.sample(0.35)), (0.0, 0.0, 0.0))
        self.assertLessEqual(self.own.by_name["phase.2"].length, 1.2)          # inside the 1.2 s transition
        self.assertLessEqual(self.own.by_name["phase.3"].length, 1.4)          # inside the 1.4 s transition

    def test_kabuto_lames_param(self):
        """The kabuto's `lames` (the neck guard's bottom row; the rows above have one and two fewer): the default, 11,
        is the helmet as G6a built it (nine, ten and eleven lames 4.4 px wide); fewer are wider, so the guard keeps its
        span with fewer cubes (his 9: six cubes fewer); outside 7..11 is an error."""
        def kabuto(lames):
            h = copy.deepcopy(self.hero)
            for p in h["art"]["parts"]:
                if p["part"] == "headwear/kabuto":
                    if lames is None:
                        p.pop("params", None)
                    else:
                        p["params"] = {"lames": lames}
            m = build.assemble(h)
            return [c for c in m.cubes if c.part == "headwear/kabuto"]

        default, eleven, nine = kabuto(None), kabuto(11), kabuto(9)
        self.assertEqual([(c.name, c.origin, c.size) for c in default], [(c.name, c.origin, c.size) for c in eleven])
        self.assertEqual(len(default) - len(nine), 6)
        self.assertEqual(len(kabuto(9)), len([c for c in self.model.cubes if c.part == "headwear/kabuto"]))
        with self.assertRaises(HerogenError):
            kabuto(12)
        with self.assertRaises(HerogenError):
            kabuto(6)

    # -------------------------------------------------------------- previews

    def test_locomotion_follows_his_traits(self):
        """A leaper of tier II lands in the landing roll (MOBILITY_AND_WORK 1.6, 4.1: 'roll for leaper II+'), his
        crane leap in between; Benika and Yugen (leapers of tier I) keep the heavy landing; he has no own sprint."""
        from render import hero_previews as hp

        class A:
            def __init__(self, mob):
                self.hero = {"mobility": mob}
                self.visual = {"base": "adult_m"}

        mine = [n for s in hp.loco_segments(A(self.hero["mobility"])) for n, _ in s.items]
        self.assertEqual(mine, ["move.walk", "move.run", "move.crouch_jump", "move.leap", "move.land_roll",
                                "move.climb"])
        for other in ("benika_akagane", "yugen_kagaribi"):
            theirs = [n for s in hp.loco_segments(A(build.load_hero(other)["mobility"])) for n, _ in s.items]
            self.assertIn("move.land_heavy", theirs, other)
            self.assertNotIn("move.land_roll", theirs, other)
        self.assertEqual(hp.mobility_tier(A(self.hero["mobility"]), "leaper"), 2)
        self.assertEqual(hp.mobility_tier(A(self.hero["mobility"]), "acrobat"), 0)

    def test_previews_draw_him_in_his_own_phases(self):
        """state.twin_sky is drawn in phase 3 (the phase that enters it), his last stand in phase 3 with both blades,
        Falling Crane in phase 3, Guard Splitter in phase 2; his captions name his phase looks."""
        from render import hero_previews as hp
        a = hp.assets(HID)
        self.assertEqual(hp.phase_of_clip(a, "state.twin_sky"), 3)
        self.assertEqual(hp.phase_of_clip(a, "last_stand"), 3)
        self.assertEqual(hp.phase_of_clip(a, "move.falling_crane"), 3)
        self.assertEqual(hp.phase_of_clip(a, "move.guard_splitter"), 2)
        self.assertEqual(hp.phase_of_clip(a, "phase.2"), 2)        # the helmet shows while he puts it on
        self.assertIn("helmet", hp.captions(a)["phase.2"])

    def test_g10_crane_leap_gif_is_drawn_from_his_source(self):
        from anim import loco_preview
        hero_id, bases, _, segs = loco_preview.HERO_GIFS["hero_kagenori"]
        self.assertEqual((hero_id, bases), (HID, None))
        self.assertEqual([n for n, _ in segs[0].items], ["move.crouch_jump", "move.leap", "move.land_roll"])

    def test_the_lineup_takes_him(self):
        from render import hero_previews as hp
        ids = hp.lineup_ids()
        self.assertIn(HID, ids)
        self.assertEqual(ids[-1], "test_rig")


if __name__ == "__main__":
    unittest.main()
