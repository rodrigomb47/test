"""G4, the core animation library: the set of HEROES_V2 13.6 and appendix D, form-scaled keys (written as Molang,
read back, exact on every base), the floor fix and the root that follows it, body contact, the slide rule,
restyled reactions, action loops and held clips, skirt panels over the thighs, the new primitives and the core
previews' playback."""
import json
import math
import unittest

from helpers import HEROGEN  # noqa: F401  (puts tools/herogen on the path)

from anim import library, poses, primitives, quality, recipes
from anim import skeleton as S
from anim.clip import FORM_VAR, HIP_MID, TICK, Clip, Key, Track, form_var, form_var_of_geo, layered
from anim.writer import animation_file, molang
from geo import GeoModel
from render.animfile import Playback, parse_file
from rig import BASE_IDS
from util import HerogenError, dumps_json

WORK = ["work.mine", "work.chop", "work.till", "work.plant", "work.harvest", "work.fish_cast", "work.fish_wait",
        "work.fish_wait.loop", "work.fish_reel", "work.cook", "work.smith", "work.build", "work.read", "work.pray",
        "work.sweep", "work.tend", "work.eat"]
EMOTES = ["emote.greet", "emote.bow", "emote.wave", "emote.laugh", "emote.angry", "emote.sad", "emote.think",
          "emote.cheer", "emote.talk", "emote.shrug"]
REACTIONS = ["hurt.heavy", "stagger", "stun", "stun.loop", "interrupt", "dodge", "death", "defeated",
             "defeated.loop", "intro", "victory"]
MOVEMENT = ["move.idle", "move.walk", "move.run", "move.swim", "move.fall", "move.climb", "move.crouch", "rest.sit",
            "rest.sleep", "carry.loop"]
PERSONAL = ["personal.1", "personal.2", "personal.3"]
EXTRA = ["eat", "spared", "enrage", "founding"]       # action codes 15, 22, 19, 23 (appendix D) the core also serves


def core():
    return library.core()


def clip(name):
    return core().by_name[name]


def body_low(sk, pose, globs=quality.BODY_CONTACT):
    return sk.lowest_body(pose, globs)[0]


class CoreSetTest(unittest.TestCase):
    def test_the_design_list_is_complete(self):
        names = set(core().by_name)
        for n in MOVEMENT + WORK + EMOTES + REACTIONS + PERSONAL + EXTRA:
            self.assertIn(n, names)
        self.assertEqual(len([n for n in names if n.startswith("work.")]), 17)       # 16 of 7.3 + the wait loop
        self.assertGreaterEqual(len(names), 45)

    def test_kinds_and_loops(self):
        for n in ("move.swim", "move.fall", "move.climb", "move.crouch", "rest.sit", "rest.sleep", "carry.loop"):
            self.assertEqual((clip(n).meta["kind"], clip(n).loop), ("base", "loop"), n)
        for n in ("stun.loop", "defeated.loop", "work.fish_wait.loop"):
            self.assertEqual((clip(n).meta["kind"], clip(n).loop), ("action", "loop"), n)
        self.assertEqual((clip("death").meta["kind"], clip("death").loop), ("action", "hold"))
        for n in WORK + EMOTES + PERSONAL + ["hurt.heavy", "stagger", "interrupt", "dodge", "intro", "victory"]:
            if not n.endswith(".loop"):
                self.assertEqual((clip(n).meta["kind"], clip(n).loop), ("action", "once"), n)

    def test_one_shots_lead_into_their_loops(self):
        """stun, defeated, work.fish_cast and work.fish_wait end on the first frame of the loop that plays next; every
        other one-shot ends on move.idle's first frame (GeckoLib snaps when the action controller stops, 4.8)."""
        for n, loop in (("stun", "stun.loop"), ("defeated", "defeated.loop"), ("work.fish_cast", "work.fish_wait.loop"),
                        ("work.fish_wait", "work.fish_wait.loop")):
            self.assertEqual(clip(n).meta["settle_to"], loop)
            self.assertEqual(quality.check_settle(clip(n), clip(loop)), [])
        for n in WORK + EMOTES + PERSONAL + ["hurt.heavy", "stagger", "interrupt", "dodge", "spared"]:
            if clip(n).loop == "once" and clip(n).meta["settle_to"] == "move.idle":
                self.assertEqual(quality.check_settle(clip(n), clip("move.idle")), [], n)

    def test_the_eat_code_is_work_eat(self):
        a, b = clip("eat"), clip("work.eat")
        self.assertEqual(json.dumps(animation_file([a])["animations"]["eat"], sort_keys=True),
                         json.dumps(animation_file([b])["animations"]["work.eat"], sort_keys=True))

    def test_markers_and_faces(self):
        self.assertIn("vfx:stars", clip("stagger").instructions()[0.4])
        self.assertIn("vfx:sparks", [e.value for e in clip("work.smith").events])
        self.assertEqual([e.value for e in clip("work.fish_cast").events if e.kind == "sound"],
                         ["minecraft:entity.fishing_bobber.throw"])
        for n in ("rest.sleep", "move.fall", "work.eat", "emote.laugh", "emote.talk"):
            self.assertIn(("face_ctrl", "position"), clip(n).tracks, n)
        eyes = clip("rest.sleep").tracks[("face_ctrl", "position")].sample(3.0)[0]
        self.assertEqual(eyes, 12.0)                                  # eye frame 11 (sleeping) + 1

    def test_the_existing_clips_are_unchanged(self):
        """G3's idle, walk and run keep their keys (the G2 and G3 previews show them)."""
        lens = {n: sum(len(t.keys) for t in clip(n).tracks.values()) for n in ("move.idle", "move.walk", "move.run")}
        self.assertTrue(all(v > 100 for v in lens.values()), lens)
        for n in ("move.idle", "move.walk", "move.run"):
            self.assertFalse(clip(n).scaled(), n)


class FormScaledKeysTest(unittest.TestCase):
    def test_form_var(self):
        self.assertEqual([form_var(S.for_base(b).env) for b in ("chibi_f", "teen_f", "adult_f")],
                         [12 - HIP_MID, 20 - HIP_MID, 24.5 - HIP_MID])
        import build
        files, _ = build.build_files("test_rig")
        geo = GeoModel(json.loads(files[build.out_paths("test_rig")["geo"]]))
        self.assertEqual(form_var_of_geo(geo), S.for_base("adult_f").d)

    def test_key_value_scales_with_the_form(self):
        tr = Track([Key(0, (0, 2, 0), k=(0, -0.5, 0)), Key(1, (0, 4, 0))])
        self.assertEqual(tr.sample(0, 0)[1], 2)
        self.assertEqual(tr.sample(0, 4)[1], 0)
        self.assertAlmostEqual(tr.sample(0.5, 4)[1], 2.0)                 # interpolated values and coefficients
        self.assertEqual(tr.sample_k(0.5)[1], -0.25)
        self.assertTrue(tr.scaled())

    def test_molang_round_trip(self):
        self.assertEqual(molang(-3.5, 0.25), "-3.5 + 0.25 * %s" % FORM_VAR)
        self.assertEqual(molang(1.2, -0.5), "1.2 - 0.5 * %s" % FORM_VAR)
        self.assertEqual(molang(1.2, 0.0), 1.2)
        c = Clip("s", 1.0, "once")
        c.tracks[("root", "position")] = Track([Key(0, (0, -3.5, 0), k=(0, -0.75, 0)), Key(1, (0, 0, 0))])
        doc = json.loads(dumps_json(animation_file([c])))
        self.assertEqual(doc["animations"]["s"]["bones"]["root"]["position"]["0.0"]["vector"][1],
                         "-3.5 - 0.75 * %s" % FORM_VAR)
        back = parse_file(doc)["s"].tracks[("root", "position")]
        for d in (0.0, -6.25, 6.25):
            self.assertAlmostEqual(back.sample(0.4, d)[1], c.tracks[("root", "position")].sample(0.4, d)[1])

    def test_the_core_writes_scaled_roots_and_nothing_else(self):
        doc = json.loads(dumps_json(core().animation_json()))
        seen = 0
        for name, a in doc["animations"].items():
            for bone, chans in a["bones"].items():
                for ch, keys in chans.items():
                    for key in keys.values():
                        for x in key.get("vector", key.get("post", [])):
                            if isinstance(x, str):
                                seen += 1
                                self.assertEqual((bone, ch), ("root", "position"), name)
                                self.assertIn(FORM_VAR, x)
        self.assertGreater(seen, 50)

    def test_kneel_touches_the_ground_on_every_base(self):
        """kneel.r: the right knee, the tucked toes and the left sole within 0.45 px of the ground on all six bases,
        though the hip drop is 5 px on a chibi and 11 on an adult (one constant would miss by 3 px)."""
        chans = poses.resolve(library.pose_library().get("kneel.r"), library.form_skeletons())
        drops = []
        for b in BASE_IDS:
            sk = S.for_base(b)
            pose = {k: {c: v for c, v in cs.items() if not c.startswith("k:")} for k, cs in chans.items()}
            r = list(pose["root"]["position"])
            r[1] += chans["root"]["k:position"][1] * sk.d
            pose["root"]["position"] = tuple(r)
            drops.append(r[1])
            lows = {k: min(float(p[1]) for p in v) for k, v in sk.body_points(pose).items()}
            for part in ("shin_r", "foot_r", "foot_l"):
                self.assertLess(abs(lows[part]), 0.45, (b, part, lows[part]))
        self.assertGreater(max(drops) - min(drops), 5.0)

    def test_without_the_variable_a_form_gets_the_compromise(self):
        """A model that does not set FORM_VAR reads 0 and shows the middle form's value: the chibi's and the adult's
        errors are then about equal and opposite, as G3's constant offsets were."""
        tr = clip("rest.sit").tracks[("root", "position")]
        exact = {b: tr.sample(0.0, S.for_base(b).d)[1] for b in ("chibi_f", "adult_f")}
        mid = tr.sample(0.0, 0.0)[1]
        self.assertAlmostEqual(mid - exact["chibi_f"], exact["adult_f"] - mid, delta=0.01)


class ContactTest(unittest.TestCase):
    def _poses(self, name, base_id, over="move.idle"):
        c, sk = clip(name), S.for_base(base_id)
        o = clip(over)
        return sk, [layered(o, c, t, t % o.length, d=sk.d) if c.meta["kind"] == "action" else c.sample(t, sk.d)
                    for t in [i * TICK for i in range(int(round(c.length / TICK)) + 1)]]

    def test_sit_and_sleep_rest_on_the_ground(self):
        for name in ("rest.sit", "rest.sleep"):
            for b in BASE_IDS:
                sk, ps = self._poses(name, b)
                lows = [body_low(sk, p) for p in ps]
                self.assertLess(max(abs(x) for x in lows), 0.6, (name, b, min(lows), max(lows)))

    def test_death_lies_on_the_ground(self):
        for b in BASE_IDS:
            sk, ps = self._poses("death", b)
            self.assertLess(abs(body_low(sk, ps[-1])), 0.6, b)
            chest = sk.world(ps[-1], only=["chest"])["chest"][0]
            self.assertLess(abs(chest[1, 1]), 0.2, b)                # the torso lies flat (its up axis level)

    def test_kneels_hold_their_knee_on_the_ground(self):
        for name, t in (("stagger", 1.0), ("defeated.loop", 0.0), ("work.plant", 0.6)):
            for b in BASE_IDS:
                sk, ps = self._poses(name, b)
                pose = ps[int(round(t / TICK))]
                knee = min(float(p[1]) for p in sk.body_points(pose, ["shin_r"])["shin_r"])
                self.assertLess(abs(knee), 0.7, (name, b, knee))

    def test_crouch_soles_stay_on_the_ground(self):
        for b in BASE_IDS:
            sk, ps = self._poses("move.crouch", b)
            lows = [sk.lowest_sole(p) for p in ps]
            self.assertLess(max(abs(x) for x in lows), 0.6, (b, min(lows), max(lows)))
            hips = [sk.world(p, only=["body"])["body"][1][1] + sk.pivot["body"][1] for p in ps]
            self.assertLess(max(hips), sk.env["hip_y"] - 0.04 * sk.env["hip_y"], b)   # it really crouches

    def test_a_sliding_foot_fails(self):
        c = Clip("slide", 1.0, "once", {"kind": "base", "stepping": True, "ground": True})
        c.tracks[("root", "position")] = Track([Key(0, (0, 0, 0)), Key(1, (0, 0, 0))])
        c.tracks[("leg_r", "rotation")] = Track([Key(0, (0, 0, 0)), Key(1, (-8, 0, 0))])      # skates forward
        self.assertTrue(any("slides" in p for p in quality.check_feet(c)))
        lift = Clip("lift", 1.0, "once", {"kind": "base", "stepping": True, "ground": False})     # lifts in place
        lift.tracks[("leg_r", "rotation")] = Track([Key(0, (0, 0, 0)), Key(0.5, (-40, 0, 0)), Key(1, (0, 0, 0))])
        lift.tracks[("shin_r", "rotation")] = Track([Key(0, (0, 0, 0)), Key(0.5, (80, 0, 0)), Key(1, (0, 0, 0))])
        lift.tracks[("foot_r", "rotation")] = Track([Key(0, (0, 0, 0)), Key(0.5, (-40, 0, 0)), Key(1, (0, 0, 0))])
        self.assertFalse(any("slides" in p for p in quality.check_feet(lift, bases=("teen_f",))))

    def test_a_floating_seat_fails(self):
        c = Clip("hover", 0.5, "loop", {"kind": "base", "ground": True, "contact": "body"})
        c.tracks[("root", "position")] = Track([Key(0, (0, 2, 0)), Key(0.5, (0, 2, 0))])
        self.assertTrue(any("floats" in p for p in quality.check_feet(c)))


class RestyleTest(unittest.TestCase):
    def test_reactions_settle_on_each_style_stance(self):
        kat = library.style("katana")
        stance = recipes.stance_of(kat.by_name["combat.idle"])
        for name, r in library.restyled_recipes().items():
            c = kat.by_name[name]
            self.assertEqual(c.meta["over"], "combat.idle", name)
            if c.loop == "once" and c.meta["settle_to"] == "combat.idle":
                self.assertEqual(quality.check_settle(c, kat.by_name["combat.idle"]), [], name)
                arm = c.tracks[("arm_r", "rotation")].sample(c.length)
                self.assertTrue(all(abs(a - b) < 0.02 for a, b in zip(arm, stance["arm_r"]["rotation"])), name)

    def test_a_core_copy_differs_from_the_style_copy(self):
        a = clip("hurt.heavy").tracks[("forearm_r", "rotation")].sample(0.0)
        b = library.style("katana").by_name["hurt.heavy"].tracks[("forearm_r", "rotation")].sample(0.0)
        self.assertGreater(max(abs(x - y) for x, y in zip(a, b)), 30)       # the katana's bent sword arm

    def test_over_needs_a_stance(self):
        ex = recipes.Expander(library.pose_library(), library.reference_skeleton())
        with self.assertRaises(HerogenError):
            ex.expand("x", {"length": 1.0, "loop": True, "keys": [{"t": 0, "pose": "@stance"}]})


class SkirtTest(unittest.TestCase):
    def test_skirts_follow_the_thighs_of_a_sit(self):
        r = clip("rest.sit").tracks
        self.assertLess(r[("skirt_f", "rotation")].sample(0.0)[0], -50)      # laid forward over the thighs
        self.assertGreater(r[("skirt_b", "rotation")].sample(0.0)[0], 20)    # flared out behind the seat
        # legs that stay put leave the panels to the follow-through bake (no authored skirt keys)
        self.assertNotIn(("skirt_f", "rotation"), clip("hurt.heavy").meta["authored"])


class PrimitiveTest(unittest.TestCase):
    def ctx(self, length=1.0, loop=False):
        return primitives.Ctx(length, library.reference_skeleton(), "t", loop, library.form_skeletons())

    def test_oscillate(self):
        c = primitives.oscillate(self.ctx(1.2, True), {"neck": [10, 0, 0]}, period=0.4)
        tr = c.tracks[("neck", "rotation")]
        self.assertAlmostEqual(tr.sample(0.1)[0], 10, delta=0.2)
        self.assertAlmostEqual(tr.sample(0.3)[0], -10, delta=0.2)
        p = primitives.oscillate(self.ctx(1.0), {"neck": [10, 0, 0]}, period=0.5, shape="pulse",
                                 window=[0, 0.1, 0.9, 1])
        self.assertGreaterEqual(min(p.tracks[("neck", "rotation")].sample(i / 40)[0] for i in range(41)), -1e-6)
        circ = primitives.oscillate(self.ctx(1.0, True), {"neck": [5, 0, 5]}, period=1.0, shape="circle")
        x, _, z = circ.tracks[("neck", "rotation")].sample(0.3)
        self.assertAlmostEqual(math.hypot(x, z), 5, delta=0.2)
        with self.assertRaises(HerogenError):
            primitives.oscillate(self.ctx(1.0, True), {"neck": [1, 0, 0]}, period=0.3)     # not whole periods

    def test_flinch_peaks_and_returns(self):
        c = primitives.flinch(self.ctx(1.0), t=0.2, bones={"chest": [-10, 0, 0]}, rise=0.1, decay=0.15)
        tr = c.tracks[("chest", "rotation")]
        self.assertAlmostEqual(tr.sample(0.3)[0], -10, delta=0.3)
        self.assertEqual(tr.sample(0.1)[0], 0.0)
        self.assertLess(abs(tr.sample(0.95)[0]), 0.6)

    def test_crouch_root_is_form_scaled(self):
        c = primitives.crouch_cycle(self.ctx(1.2, True))
        self.assertTrue(c.tracks[("root", "position")].scaled())
        self.assertFalse(primitives.walk_cycle(self.ctx(1.0, True)).tracks[("root", "position")].scaled())

    def test_carry_leaves_the_arms(self):
        c = primitives.carry_cycle(self.ctx(1.0, True))
        self.assertNotIn(("arm_r", "rotation"), c.tracks)
        self.assertIn(("leg_r", "rotation"), c.tracks)


class PlaybackTest(unittest.TestCase):
    def test_the_written_core_plays_per_form(self):
        """The written file, read back, plays the form-scaled sit at each form's own height."""
        clips = parse_file(json.loads(dumps_json(core().animation_json())))
        chain = type("C", (), {"get": staticmethod(clips.get)})()
        for b in ("chibi_f", "adult_m"):
            sk = S.for_base(b)
            pb = Playback(chain, "rest.sit", d=sk.d)
            self.assertLess(abs(body_low(sk, pb.pose(1.0))), 0.6, b)

    def test_sequence_blends_from_the_pose_it_cuts(self):
        from anim.core_preview import SequencePlayback
        chain = type("C", (), {"get": staticmethod(core().by_name.get)})()
        pb = SequencePlayback(chain, "move.idle", [("stun", 0.0), ("stun.loop", 0.5)], d=0.0)
        before = pb.pose(0.499)["neck"]["rotation"]
        after = pb.pose(0.501)["neck"]["rotation"]
        self.assertLess(max(abs(a - b) for a, b in zip(before, after)), 1.0)       # no pop into the loop


if __name__ == "__main__":
    unittest.main()
