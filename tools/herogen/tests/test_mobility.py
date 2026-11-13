"""G10, the mobility set (MOBILITY_AND_WORK 4.1-4.3): the 27 locomotion animations of milestone 1 and the wing loops in
the core library, base one-shots that end on the loop they lead into, full turns that unwind (primitives.spin), the
roll on the ground, the riding rule on the rig's `mount` bone, the mounts/broom carrier and the weapon that is a
carrier, the hero variants (herosrc/anim/variants) and the previews' controller queue."""
import copy
import os
import unittest

from helpers import HEROGEN, mannequin

from anim import library, primitives, quality, recipes
from anim import skeleton as S
from anim.clip import TICK, Clip, Key, Track
from anim.writer import animation_file, file_time
from render.animfile import parse_file
from rig import BASE_IDS, Model
from util import HerogenError, dumps_json

# MOBILITY_AND_WORK 4.1 without G10b's six (hover, drift, swim_fast, tread, swim_down, surface_leap)
LOOPS = ["move.leap", "move.climb_idle", "move.climb_down", "move.glide", "move.fly", "move.fly_fast",
         "move.fly_hover", "move.sprint", "move.charge_run", "move.winded", "move.ride"]
ONE_SHOTS = {"move.jump": "move.leap", "move.crouch_jump": "move.leap", "move.air_jump": "move.leap",
             "move.wall_kick": "move.leap", "move.mantle": "move.idle", "move.land": "move.idle",
             "move.land_heavy": "move.idle", "move.land_roll": "move.idle", "move.glide_start": "move.glide",
             "move.gust": "move.glide", "move.takeoff": "move.fly", "move.fly_land": "move.idle",
             "move.skid": "move.idle", "move.downed": "move.idle"}
ACTIONS = {"move.ride_cast": "move.ride", "move.air_dive": "move.idle"}
LENGTHS = {"move.jump": 0.3, "move.crouch_jump": 0.3, "move.air_jump": 0.35, "move.wall_kick": 0.3,
           "move.mantle": 0.5, "move.land": 0.3, "move.land_heavy": 0.5, "move.land_roll": 0.5,
           "move.glide_start": 0.4, "move.gust": 0.4, "move.takeoff": 0.6, "move.fly_land": 0.4, "move.skid": 0.4,
           "move.downed": 2.0, "move.ride_cast": 0.6}
WINGS = ["sec.wings_fold", "sec.wings_flap", "sec.wings_glide"]
MOBILITY = LOOPS + list(ONE_SHOTS) + list(ACTIONS)
SLICE = {"natsuho": ("cat_twist", ["move.air_jump"]), "yugen": ("fox_lope", ["move.sprint"]),
         "kagenori": ("crane_leap", ["move.leap"]),
         "ambrose": ("broom", ["move.takeoff", "move.ride", "move.ride_cast", "move.fly_land"])}


def core():
    return library.core()


def clip(name):
    return core().by_name[name]


def outfit(oid, base=None):
    import lint
    o = [x for x in lint.load_outfits() if x["id"] == oid][0]
    return lint.outfit_hero(o, base or o["base"])


class MobilitySetTest(unittest.TestCase):
    def test_the_milestone_one_set_is_complete(self):
        self.assertEqual(len(MOBILITY), 27)
        for n in LOOPS + WINGS:
            self.assertEqual((clip(n).meta["kind"], clip(n).loop), ("base", "loop"), n)
        for n in ONE_SHOTS:
            self.assertEqual((clip(n).meta["kind"], clip(n).loop), ("base", "once"), n)
        for n in ACTIONS:
            self.assertEqual((clip(n).meta["kind"], clip(n).loop), ("action", "once"), n)
        for n, length in LENGTHS.items():
            self.assertAlmostEqual(clip(n).length, length, msg=n)
        self.assertGreaterEqual(clip("move.air_dive").length, 0.8)
        self.assertEqual(clip("move.air_dive").meta["class"], "heavy")

    def test_one_shots_end_on_the_loop_they_lead_into(self):
        """MOBILITY_AND_WORK 4.1: a one-shot on the base controller ends on the first frame of the loop queued after it
        (GeckoLib's blend into it then shows nothing); actions settle on their base as usual."""
        for n, loop in ONE_SHOTS.items():
            self.assertEqual(clip(n).meta["settle_to"], loop, n)
            self.assertEqual(quality.check_settle(clip(n), clip(loop)), [], n)
        for n, loop in ACTIONS.items():
            self.assertEqual(clip(n).meta["settle_to"], loop, n)

    def test_a_one_shot_off_its_loop_fails(self):
        c = clip("move.jump").copy()
        tr = c.tracks[("leg_r", "rotation")]
        last = tr.keys[-1]
        tr.put(Key(last.t, (last.v[0] + 12.0, last.v[1], last.v[2])))
        self.assertTrue(quality.check_settle(c, clip("move.leap")))
        lib = library.core()
        problems = quality.check_built(type(lib)("x", [c], ""), lambda n: lib.by_name.get(n))
        self.assertTrue(any("leg_r" in p for p in problems), problems)

    def test_the_quality_rules_pass_on_all_six_bases(self):
        known = quality.rig_bone_names()
        for n in MOBILITY + WINGS:
            c = clip(n)
            target = over = None
            if c.meta.get("settle_to"):
                target = clip(c.meta["settle_to"])
            if c.meta.get("kind") == "action":
                over = clip(c.meta["over"])
            self.assertEqual(quality.check_clip(c, target, known, True, over=over), [], n)

    def test_wing_loops_key_only_the_wings(self):
        """The secondary controller's loops (MOBILITY_AND_WORK 4.3) override per bone, so they key only the wings and
        play over any base or action clip; no locomotion clip keys a wing."""
        for n in WINGS:
            self.assertEqual(clip(n).bones(), ["wing_l", "wing_r"], n)
        for n in MOBILITY:
            self.assertFalse(any(b.startswith("wing_") for b in clip(n).bones()), n)
        flap = clip("sec.wings_flap")
        up, down = flap.sample(0.0)["wing_r"]["rotation"], flap.sample(0.2)["wing_r"]["rotation"]
        self.assertGreater(up[2] - down[2], 60.0, "a full beat")

    def test_no_root_motion(self):
        for n in MOBILITY:
            self.assertEqual(quality.check_root(clip(n)), [], n)

    def test_crouch_jump_is_a_tell(self):
        """The leap's wind-up crouches deep with the feet planted on every base until the push."""
        c = clip("move.crouch_jump")
        for base in BASE_IDS:
            sk = S.for_base(base)
            start = sk.soles(c.sample(0.0, sk.d))
            for t in (0.05, 0.1, 0.15, 0.2):
                soles = sk.soles(c.sample(t, sk.d))
                drift = max(float(((a - b) ** 2).sum()) ** 0.5 for s in "rl" for a, b in zip(soles[s], start[s]))
                self.assertLess(drift, 0.6, (base, t))
            hips_drop = -c.sample(0.2, sk.d)["root"]["position"][1]
            self.assertGreater(hips_drop, 0.18 * sk.env["hip_y"], base)

    def test_markers(self):
        self.assertIn("vfx:air_ring", clip("move.air_jump").instructions()[0.0])
        self.assertIn("hit", clip("move.air_dive").instructions()[1.0])
        self.assertIn("vfx:stars", clip("move.downed").instructions()[0.1])
        doc = animation_file([clip("move.air_jump")])["animations"]["move.air_jump"]
        # base clips write their events after the base controller's 4-tick blend (HEROES_V2 4.8)
        self.assertEqual(list(doc["sound_effects"]), ["0.2"])
        self.assertEqual(file_time(clip("move.air_jump"), 0.0), 0.2)

    def test_sounds_are_vanilla_events_or_in_the_catalogue(self):
        # the 1.21.1 event list sfxgen checks markers against (read directly: tools/sfxgen has its own `build`)
        with open(os.path.join(os.path.dirname(HEROGEN), "sfxgen", "vanilla_sound_ids.txt"), encoding="utf-8") as f:
            ids = {line.strip() for line in f if line.strip() and not line.startswith("#")}
        clips = [clip(n) for n in MOBILITY] + [c for v, _ in SLICE.values() for c in self._variant_clips(v)]
        sounds = {e.value for c in clips for e in c.events if e.kind == "sound"}
        self.assertTrue(sounds)
        for s in sounds:
            self.assertIn(s if ":" in s else "minecraft:" + s, ids)

    @staticmethod
    def _variant_clips(name):
        hero = mannequin("teen_f")
        hero["animations"] = {"variants": [name]}
        return library.hero(hero).clips


class SpinTest(unittest.TestCase):
    def test_the_flip_turns_once_and_unwinds(self):
        """move.air_jump turns the tuck forward once about the hips (body and both legs) and unwinds with a jump key
        (the same orientation), so it ends on move.leap's pose instead of turning back the long way."""
        c = clip("move.air_jump")
        tr = c.tracks[("body", "rotation")]
        jumps = [k for k in tr.keys if k.pre is not None]
        self.assertEqual(len(jumps), 1)
        k = jumps[0]
        self.assertAlmostEqual(k.pre[0] - k.v[0], 360.0, delta=0.5)
        self.assertGreater(tr.sample(k.t - 0.06)[0] - tr.sample(0.08)[0], 180.0)
        # written as a Bedrock pre/post key and read back the same way
        back = parse_file(dumps_json(animation_file([c])))["move.air_jump"]
        btr = back.tracks[("body", "rotation")]
        self.assertAlmostEqual(btr.sample(k.t - 0.001)[0], k.pre[0], delta=0.1)
        self.assertAlmostEqual(btr.sample(k.t)[0], k.v[0], delta=0.1)
        for leg in ("leg_r", "leg_l"):
            self.assertTrue(any(kk.pre is not None for kk in c.tracks[(leg, "rotation")].keys), leg)

    def test_a_layer_keeps_the_jump(self):
        lib = library.pose_library()
        ex = recipes.Expander(lib, library.reference_skeleton(), forms=library.form_skeletons())
        c = ex.expand("t", {"length": 0.5, "loop": False, "kind": "base", "secondary": False, "planted": False,
                            "ground": False,
                            "keys": [{"t": 0, "pose": "rest"}, {"t": 0.5, "pose": "rest"}],
                            "layers": [{"primitive": "spin", "params": {"bones": {"body": [360, 0, 0]},
                                                                         "t0": 0.1, "t1": 0.4}}]})
        tr = c.tracks[("body", "rotation")]
        k = [k for k in tr.keys if k.pre is not None]
        self.assertEqual(len(k), 1)
        self.assertAlmostEqual(k[0].t, 0.4)
        self.assertAlmostEqual(k[0].pre[0], 360.0, delta=1e-6)
        self.assertAlmostEqual(tr.sample(0.5)[0], 0.0, delta=1e-6)
        with self.assertRaises(HerogenError):
            primitives.spin(primitives.Ctx(0.5, library.reference_skeleton()), {"body": [360, 0, 0]}, 0.3, 0.2)

    def test_the_roll_stays_on_the_ground(self):
        """The landing roll turns the tucked body over on the ground: on every base the lowest point of the whole
        body, head included, stays within 0.6 px of the ground while it rolls (the root follows it, form-scaled)."""
        c = clip("move.land_roll")
        globs = ["body", "waist", "chest", "neck", "head", "arm_*", "forearm_*", "hand_*", "leg_*", "shin_*", "foot_*"]
        for base in BASE_IDS:
            sk = S.for_base(base)
            for i in range(0, 9):
                t = round(i * TICK, 4)
                low = sk.lowest_body(c.sample(t, sk.d), globs)[0]
                self.assertTrue(-0.6 <= low <= 0.75, (base, t, low))


class MountTest(unittest.TestCase):
    def test_the_mount_bone_is_the_seat(self):
        for base in BASE_IDS:
            m = Model("t", base)
            b = m.catalogue["mount"]
            self.assertEqual(b.parent, "root")
            self.assertAlmostEqual(b.pivot[1], m.env["hip_y"] - m.env["leg_d"] / 2.0)

    def test_the_broom_part_is_a_carrier(self):
        import build
        from render.scene import HeroAssets
        hero = mannequin("teen_m", parts=[{"part": "mounts/broom"}])
        files, b = build.build_files(hero["id"], hero)
        a = HeroAssets(hero["id"], {**files, **library.core().files()}, b.hero)
        self.assertEqual(a.visual["bones"]["mount"], ["mount"])
        self.assertEqual(a.visual["carrier"]["part"], "mounts/broom")
        self.assertEqual(a.visual["carrier"]["kind"], "broom")
        self.assertIn("mount", a.hidden())
        self.assertNotIn("mount", a.hidden(riding=True))
        self.assertFalse(a.carrier_is_weapon())
        mounted = [c for c in b.model.cubes if c.bone.endswith("mount")]
        self.assertTrue(mounted)
        # the handle's top is the seat
        handle = [c for c in mounted if c.name.endswith("#handle")][0]
        self.assertAlmostEqual(handle.origin[1] + handle.size[1], b.model.bones["mount"].pivot[1], places=3)

    def test_the_staff_is_a_weapon_carrier(self):
        import build
        from render.scene import HeroAssets
        hero = outfit("ambrose")
        files, b = build.build_files(hero["id"], hero)
        a = HeroAssets(hero["id"], {**files, **library.core().files()}, b.hero)
        self.assertEqual(a.visual["carrier"]["part"], "weapon")
        self.assertTrue(a.carrier_is_weapon())
        riding = a.hidden(riding=True)
        for bone in a.bones("weapon_drawn") + a.bones("weapon_sheathed"):
            self.assertIn(bone, riding)

    def test_one_carrier(self):
        import build
        hero = outfit("ambrose")
        hero = copy.deepcopy(hero)
        hero["art"]["parts"].append({"part": "mounts/broom"})
        with self.assertRaises(HerogenError):
            build.build_files(hero["id"] + "_x", hero)
        bad = mannequin("teen_m", parts=[{"part": {"id": "inline/m", "category": "mounts", "cubes": [
            {"name": "c", "bone": "body", "origin": [0, 10, 0], "size": [1, 1, 1]}]}}])
        with self.assertRaises(HerogenError):
            build.build_files(bad["id"], bad)

    def test_the_rider_sits_on_the_mount(self):
        """MOBILITY_AND_WORK 4.1: the riding poses keep the rider on the mount locator, on every base; thighs lifted
        off it fail."""
        for n in ("move.ride",):
            self.assertEqual(quality.check_mount(clip(n)), [])
        self.assertEqual(quality.check_mount(clip("move.ride_cast"), clip("move.ride")), [])
        c = clip("move.ride").copy()
        for side in ("r", "l"):
            tr = c.tracks[("leg_" + side, "rotation")]
            for k in tr.keys:
                k.v = (k.v[0] + 25.0, k.v[1], k.v[2])
        self.assertTrue(quality.check_mount(c))


class VariantTest(unittest.TestCase):
    def test_variants_go_into_the_heros_own_file(self):
        hero = mannequin("teen_f")
        hero["animations"] = {"variants": ["cat_twist"]}
        own = library.hero(hero)
        self.assertEqual([c.name for c in own.clips], ["move.air_jump"])
        self.assertTrue(any(k.pre is not None for k in own.by_name["move.air_jump"].tracks[("root", "rotation")].keys))
        # the hero's own override wins over the variant's
        hero["animations"]["overrides"] = {
            "move.air_jump": {"length": 0.35, "settle_to": "move.leap", "ground": False, "planted": False,
                              "keys": [{"t": 0, "pose": "leap.b"}, {"t": 0.35, "pose": "leap.a"}]}}
        own = library.hero(hero)
        self.assertNotIn(("root", "rotation"), own.by_name["move.air_jump"].tracks)
        with self.assertRaises(HerogenError):
            library.hero(dict(hero, animations={"variants": ["no_such_variant"]}))

    def test_the_slice_variants_pass_on_their_bases(self):
        """Each slice hero's variant, built into its own file from the sample outfit, passes every animation rule
        on its own base and on a chibi (one file serves every form)."""
        import build
        for oid, (variant, names) in SLICE.items():
            o = outfit(oid)
            self.assertEqual(o["animations"]["variants"], [variant])
            for base in (o["art"]["form"] + "_" + o["gender"][0], "chibi_" + o["gender"][0]):
                hero = outfit(oid, base)
                files, b = build.build_files(hero["id"], hero)
                own = b.animations
                self.assertEqual(sorted(c.name for c in own.clips), sorted(names))
                chain = [own, library.style(library.style_of(hero)), library.core()]

                def lookup(n, chain=chain):
                    for x in chain:
                        if n in x.by_name:
                            return x.by_name[n]
                    return None
                self.assertEqual(quality.check_built(own, lookup, list(b.model.bones), own_file=True), [],
                                 (oid, base))

    def test_the_cat_twists_round_its_vertical_axis(self):
        hero = mannequin("teen_f")
        hero["animations"] = {"variants": ["cat_twist"]}
        c = library.hero(hero).by_name["move.air_jump"]
        k = [k for k in c.tracks[("root", "rotation")].keys if k.pre is not None][0]
        self.assertAlmostEqual(k.pre[1], 360.0, delta=0.5)
        self.assertNotIn(("body", "rotation"), [bc for bc, tr in c.tracks.items() if any(
            kk.pre is not None for kk in tr.keys)])

    def test_the_broom_rides_side_saddle(self):
        hero = outfit("ambrose")
        own = library.hero(hero)
        ride = own.by_name["move.ride"].sample(0.0)
        self.assertLess(ride["leg_r"]["rotation"][1], -30.0, "both thighs turned to the left side")
        self.assertLess(ride["leg_l"]["rotation"][1], -30.0)
        self.assertEqual(quality.check_mount(own.by_name["move.ride"]), [])
        self.assertEqual(own.by_name["move.takeoff"].meta["settle_to"], "move.ride")


class PlaybackTest(unittest.TestCase):
    def test_a_one_shot_hands_over_to_its_loop_without_a_pop(self):
        from anim import loco_preview
        from render.animfile import AnimChain
        chain = AnimChain([("_core", dumps_json(library.core().animation_json()).encode())])
        pb = loco_preview.LocoPlayback(chain, [("move.idle", 0.0), ("move.jump", 0.3), ("move.leap", 0.8)])
        before = pb.pose(0.8 - 1e-4)
        after = pb.pose(0.8 + 2 * TICK)
        worst = max(abs(a - b) for bone in ("leg_r", "arm_l", "body")
                    for a, b in zip(before[bone]["rotation"], after[bone]["rotation"]))
        self.assertLess(worst, 1.0)


if __name__ == "__main__":
    unittest.main()
