"""C1: Hinae Shirasuzu, the second slice hero with her own source, art and signature animations.

Her one body (the owner's rule), her moves' frames equal to her kit's, her sound markers, her clips' rules on every
base and her props clear of her body; the phase-2 ring of talismans (scaled and lifted by her moves, turned by the
secondary controller's orbit loop); the phase-3 glow of her wand's streamers (glowing shells on p3_ bones); and the
generator pieces they needed: pose `scale`, the scale's rest value in the settle rule, the weapon entry's
`phase_glow`, and the preview set's phase and locomotion rules."""
import copy
import json
import os
import unittest

import assets
import build
import grips
from anim import library
from anim import poses as P
from anim import quality
from anim.clip import TICK
from anim.recipes import Expander
from parts import SHELL, apply_weapon
from rig import Model
from util import HerogenError, ROOT

HID = "hinae_shirasuzu"
FIXTURE = os.path.join(ROOT, "src", "test", "resources", "heroes", "slice", HID + ".json")
RING = "p2_talisman_ring"


def _ticks(s):
    return int(round(float(s) * 20))


class HinaeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.hero = build.load_hero(HID)
        cls.files, cls.b = build.build_files(HID)
        cls.own = cls.b.animations
        cls.visual = json.loads(cls.files[build.out_paths(HID)["visual"]])
        with open(FIXTURE) as f:
            cls.kit = json.load(f)

    def lookup(self, name):
        for b in (self.own, library.style("focus"), library.core()):
            if name in b.by_name:
                return b.by_name[name]
        return None

    def test_one_body_for_good(self):
        """An adult of ordinary size, standard build, drawn at 96% (SLICE_PLAN 3.4; the owner's one-body rule of
        HEROES_V2 3.6): no phase changes the body."""
        art = self.hero["art"]
        self.assertEqual((art["form"], art["base"], art["build"], art["scale"]), ("adult", "female", "standard", 96))
        self.assertEqual((self.visual["form"], self.visual["base"]), ("adult", "adult_f"))
        for ph in art.get("phases", []):
            self.assertFalse({"form", "base", "build"} & set(ph), "a phase changes the body")

    def test_kit_is_the_fixtures(self):
        for k, v in self.kit.items():
            if k != "_comment":
                self.assertEqual(self.hero[k], v, k)

    def test_move_frames_equal_the_kit(self):
        """Every move's clip and the ultimate's have the kit's wind-up, active and recovery to the tick (from the
        action's start, the 2-tick blend included): the release on a `hit`, the active part's last tick on a `cast`
        (one hit each: a volley, a zone, a nova, a barrier and a sweep release once)."""
        timing = self.own.timing_json()["animations"]
        items = [("move." + m["id"], m) for m in self.kit["moves"]] + [("ultimate", self.kit["ultimate"])]
        for name, m in items:
            want = {k: _ticks(v) for k, v in m["frames"].items()}
            self.assertEqual(timing[name]["frame_ticks"], want, name)
            hits = [e["tick"] for e in timing[name]["events"] if e["type"] == "hit"]
            self.assertEqual(hits, [want["windup"]], name)
        definition, _ = assets.definition_of(HID)
        files = dict(self.files, **library.core().files(), **library.style("focus").files())
        self.assertEqual(assets.check_frames(definition, assets.Chain.of(self.visual, files)), [])

    def test_own_clips_pass_every_rule_on_every_base(self):
        self.assertEqual(quality.check_built(self.own, self.lookup, list(self.b.model.bones), own_file=True), [])
        self.assertLessEqual(len(self.own.clips), 24)
        self.assertNotIn("ultimate.loop", self.own.by_name)        # the engine never sends ultimate.end (no loop)

    def test_props_clear_of_her_body(self):
        """The wand and the fan of talismans through every second tick of her own clips, on her own body."""
        self.assertEqual(grips.animation_problems(self.hero, self.b, self.own, self.lookup, every=2), [])

    def test_sound_markers(self):
        """Her client-played sounds are markers in the clips their slot names (SOUND.md 4.7, 5): each move's
        client `release`, the dance's `end`, the phases, the intro and the quirks; every id is in her catalogue."""
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
        for mid, slots in hm["moves"].items():
            if mid == "basic" or mid == self.kit["ultimate"]["id"] or "release" not in slots:
                continue
            self.assertIn(pre + slots["release"], sounds["move." + mid], mid)
        self.assertIn(pre + hm["moves"]["purifying_dance"]["end"], sounds["move.purifying_dance"])
        self.assertIn(pre + hm["phases"]["2"], sounds["phase.2"])
        self.assertIn(pre + hm["phases"]["3"], sounds["phase.3"])
        self.assertIn(pre + hm["intro"], sounds["intro"])
        for n in ("personal.1", "personal.2", "personal.3"):
            self.assertIn(pre + hm["quirks"][n], sounds[n], n)
        # server-played slots are never markers: the wind-ups, impacts and the rite's release
        server = {pre + s for m in hm["moves"].values() for k, s in m.items() if k in ("windup", "impact")}
        server.add(pre + hm["moves"]["thousand_bell_rite"]["release"])
        for name in ("move.sealing_circle", "move.exorcise", "move.seven_talismans", "ultimate"):
            self.assertFalse(sounds.get(name, set()) & server, name)

    def test_faces_where_the_moment_needs_one(self):
        for c in self.own.clips:
            if not c.name.startswith("sec."):
                self.assertIn(("face_ctrl", "position"), c.tracks, c.name)

    def test_the_ring_orbits_on_the_secondary_controller(self):
        """sec.loop turns the phase-2 ring once round her and closes (the turn unwound by a pre/post key at the end),
        keying nothing but the ring's rotation; the ring's bone is a phase-2 bone."""
        self.assertEqual(self.visual["bones"]["phase"][RING], 2)
        loop = self.own.by_name["sec.loop"]
        self.assertEqual(loop.loop, "loop")
        self.assertEqual(set(loop.tracks), {(RING, "rotation")})
        tr = loop.tracks[(RING, "rotation")]
        last = tr.keys[-1]
        self.assertAlmostEqual(last.t, loop.length)
        self.assertIsNotNone(last.pre)
        self.assertAlmostEqual(last.pre[1], 360.0, places=3)
        self.assertEqual(tuple(round(v, 6) for v in last.v), tuple(round(v, 6) for v in tr.keys[0].v))
        ys = [tr.sample(i * TICK)[1] for i in range(int(round(loop.length / TICK)))]
        self.assertTrue(all(b > a for a, b in zip(ys, ys[1:])), "the ring turns one way at a steady rate")

    def test_moves_scale_the_ring_but_never_turn_it(self):
        """phase.2 starts with the ring folded small (it shows from the transition's start) and opens it to full
        size; seven_talismans and the ultimate draw it in and flare it out; none keys its rotation (sec.loop's: a
        later controller would override it anyway), and each ends on its size, 1."""
        p2 = self.own.by_name["phase.2"]
        sc = p2.tracks[(RING, "scale")]
        self.assertLess(sc.sample(0.0)[0], 0.2)
        self.assertGreater(max(sc.sample(i * TICK)[0] for i in range(22)), 1.05)
        for name in ("phase.2", "move.seven_talismans", "ultimate"):
            c = self.own.by_name[name]
            self.assertNotIn((RING, "rotation"), c.tracks, name)
            self.assertIn((RING, "scale"), c.tracks, name)
            self.assertEqual(tuple(round(v, 4) for v in c.tracks[(RING, "scale")].sample(c.length)), (1.0, 1.0, 1.0))
        st = self.own.by_name["move.seven_talismans"].tracks[(RING, "scale")]
        self.assertLess(st.sample(0.6)[0], 0.75)                 # drawn in for the wind-up
        self.assertGreater(st.sample(0.75)[0], 1.15)             # flung out past its size at the release

    def test_streamers_glow_from_phase_3(self):
        """The wand's paper streamers get glowing shells SHELL px round them on p3_ bones, in the glow colour, in the
        hand and on the carried copy; the phase look shows them from phase 3 only (no _p2 texture is needed)."""
        m = self.b.model
        shells = [c for c in m.cubes if c.name.endswith("glow") and "purification_wand" in c.name]
        self.assertEqual(len(shells), 16)                          # 8 streamer folds, in the hand and in the sash
        for c in shells:
            self.assertTrue(c.bone.startswith(("p3_", "fine_p3_")), c.bone)
            self.assertTrue(c.glow)
        phase_bones = self.visual["bones"]["phase"]
        self.assertTrue(any(b.startswith("p3_weapon") and n == 3 for b, n in phase_bones.items()))
        self.assertFalse(self.visual["texture"]["p2"])
        first = [c for c in m.cubes if c.name.endswith("#7") and "weapon_r:" in c.name][0]
        shell = [c for c in shells if "weapon_r:" in c.name and c.name.endswith("#7glow")][0]
        for a, b in zip(first.size, shell.size):
            self.assertAlmostEqual(b - a, 2 * SHELL)


class PhaseGlowTest(unittest.TestCase):
    def test_a_glob_that_matches_nothing_fails(self):
        m = Model("t", "teen_f")
        with self.assertRaises(HerogenError):
            apply_weapon(m, {"prop": "weapons/purification_wand", "phase_glow": {"phase": 3, "cubes": ["nope_*"]}})

    def test_phase_must_be_2_or_3(self):
        m = Model("t", "teen_f")
        with self.assertRaises(HerogenError):
            apply_weapon(m, {"prop": "weapons/purification_wand", "phase_glow": {"phase": 1, "cubes": ["shide_*"]}})

    def test_shells_on_phase_bones(self):
        m = Model("t", "teen_f")
        apply_weapon(m, {"prop": "weapons/purification_wand", "sheathed": "obi",
                         "phase_glow": {"phase": 2, "cubes": ["bell_?"]}})
        shells = [c for c in m.cubes if c.name.endswith("glow")]
        self.assertEqual(len(shells), 6)
        self.assertTrue(all(c.bone.startswith(("p2_", "fine_p2_")) for c in shells))


class ScaleTest(unittest.TestCase):
    def lib(self, specs):
        return P.Library(specs)

    def test_scale_is_parsed_inherited_and_mirrored(self):
        lib = self.lib({"a": {"scale": {"arm_r": [0.5, 0.5, 0.5]}, "mirror": True},
                        "b": {"extends": "a", "bones": {"neck": [4, 0, 0]}}})
        ch = lib.get("b").chans()
        self.assertEqual(ch["arm_r"]["scale"], (0.5, 0.5, 0.5))
        self.assertEqual(ch["arm_l"]["scale"], (0.5, 0.5, 0.5))

    def test_a_scale_of_zero_fails(self):
        with self.assertRaises(HerogenError):
            self.lib({"a": {"scale": {"arm_r": [0, 1, 1]}}}).get("a")

    def test_an_action_settles_its_scale_on_one(self):
        """A key that leaves a scaled bone out keys it at 1 (its rest), and the settle rule's goal for a scale is 1:
        an action ending at half size fails it."""
        lib = library.pose_library().with_specs({"big": {"scale": {"neck": [1.5, 1.5, 1.5]}}}, "t")
        ex = Expander(lib, library.reference_skeleton(), lambda n: library.core().by_name.get(n), "move.idle",
                      library.form_skeletons())
        clip = ex.expand("t", {"length": 1.0, "keys": [{"t": 0, "pose": "@stance"}, {"t": 0.4, "pose": "big"},
                                                       {"t": 1.0, "pose": "@stance"}]})
        sc = clip.tracks[("neck", "scale")]
        self.assertEqual(tuple(sc.sample(0.0)), (1.0, 1.0, 1.0))
        self.assertEqual(tuple(round(v, 6) for v in sc.sample(0.4)), (1.5, 1.5, 1.5))
        self.assertEqual(quality.check_settle(clip, library.core().by_name["move.idle"]), [])
        bad = copy.deepcopy(clip)
        bad.tracks[("neck", "scale")].keys[-1].v = (0.5, 0.5, 0.5)
        self.assertTrue(quality.check_settle(bad, library.core().by_name["move.idle"]))


class PreviewRulesTest(unittest.TestCase):
    def test_clips_are_drawn_in_their_phase_and_her_locomotion_follows_her_traits(self):
        from render import hero_previews
        a = hero_previews.assets(HID)
        self.assertEqual(hero_previews.phase_of_clip(a, "move.talisman_fan"), 1)
        self.assertEqual(hero_previews.phase_of_clip(a, "move.seven_talismans"), 2)
        self.assertEqual(hero_previews.phase_of_clip(a, "move.purifying_dance"), 3)
        self.assertEqual(hero_previews.phase_of_clip(a, "ultimate"), 2)          # it keys the phase-2 ring
        self.assertEqual(hero_previews.phase_of_clip(a, "last_stand"), 3)
        labels = [s.label for s in hero_previews.loco_segments(a)]
        self.assertTrue(any(lb.startswith("swim") for lb in labels))
        self.assertFalse(any("wall_kick" in lb for lb in labels))
        pose = hero_previews.secondary(a, {}, 1.5)
        self.assertAlmostEqual(pose[RING]["rotation"][1], 90.0, delta=1.0)

    def test_the_lineup_takes_the_heroes_with_a_source_and_the_test_rig_last(self):
        from render import hero_previews
        ids = hero_previews.lineup_ids()
        self.assertEqual(ids[-1], "test_rig")
        self.assertIn(HID, ids)
        self.assertIn("natsuho_komezaki", ids)
        self.assertNotIn("_fallback", ids)


if __name__ == "__main__":
    unittest.main()
