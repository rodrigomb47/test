"""G5: the style libraries of the slice (HEROES_V2 13.6 "Style libraries", 15.2 row G5): fists, focus, hammer, katana,
twin_blades, sword_shield and staff, each with the full list, its string reading as its weapon, the props clear of
the body through every tick of every clip on all six bases, and the generator rules G5 added (`plays_over`,
`arms_over` and the style file's `restyle_arms`, the settle-after-the-next-key guard)."""
import json
import unittest

import numpy as np

from helpers import HEROGEN  # noqa: F401  (sets the path)

import build
import grips
import lint
from anim import library, quality, recipes
from anim.clip import form_var, layered
from anim.skeleton import Skeleton
from rig import BASE_IDS
from util import HerogenError, dumps_json

SLICE_STYLES = ("fists", "focus", "hammer", "katana", "twin_blades", "sword_shield", "staff")
FULL_LIST = ("combat.idle", "combat.run", "draw", "sheathe", "attack.1", "attack.2", "attack.3", "guard", "guard.loop",
             "guard.hit", "guard.hit.loop", "dodge", "intro", "victory", "taunt")
RANGED = ("focus", "twin_blades", "staff")             # the styles whose heroes have a basic ranged attack
WORK = {"hammer": "work.mine", "katana": "work.chop"}  # style versions where the work matters (7.3)
OUTFIT_OF = {"fists": "natsuho", "focus": "hinae", "hammer": "benika", "katana": "kagenori",
             "twin_blades": "tobimaru", "sword_shield": "ilsabet", "staff": "ambrose"}


class _Built:
    def __init__(self, model):
        self.model = model


def outfit(oid):
    return [o for o in lint.load_outfits() if o["id"] == oid][0]


def lookup_in(lib):
    core = library.core()
    return lambda n: lib.by_name.get(n) or core.by_name.get(n)


def world_at(style, oid, name, t, base="teen_f"):
    """The model and the world transforms of an outfit playing a style clip at clip time t (an action layered over
    the base clip it plays over)."""
    lib = library.style(style)
    hero = lint.outfit_hero(outfit(oid), base)
    m = build.assemble(hero)
    d = form_var(m.env)
    c = lib.by_name[name]
    over = lookup_in(lib)(c.meta["over"]) if c.meta.get("kind") == "action" else None
    pose = layered(over, c, t, t % over.length, d=d) if over is not None else c.sample(t, d)
    return m, Skeleton.from_model(m).world(pose)


def point(m, w, bone):
    return Skeleton.apply(w, bone, m.bones[bone].pivot)


def axis(w, bone, v):
    return w[bone][0] @ np.array(v, dtype=float)


def hits(clip):
    return [e.t for e in clip.events if e.kind == "instruction" and e.value == "hit"]


class LibraryListTest(unittest.TestCase):
    def test_every_slice_style_has_the_full_list(self):
        for style in SLICE_STYLES:
            lib = library.style(style)
            with self.subTest(style=style):
                for name in FULL_LIST:
                    self.assertIn(name, lib.by_name)
                self.assertEqual("attack.ranged" in lib.by_name, style in RANGED)
                if style in WORK:
                    self.assertIn(WORK[style], lib.by_name)
                for name in ("attack.1", "attack.2", "attack.3") + (("attack.ranged",) if style in RANGED else ()):
                    c = lib.by_name[name]
                    self.assertEqual(c.meta.get("class"), "light", name)
                    self.assertTrue(hits(c), name)
                    self.assertEqual(c.meta["settle_to"], "combat.idle", name)
                for name in library.restyled_recipes():
                    self.assertIn(name, lib.by_name)         # the core's reactions over the style's own stance
                # the run with the weapon drawn is combat.run: a sheathed hero runs the core's move.run
                self.assertNotIn("move.run", lib.by_name)
                self.assertEqual(lib.by_name["combat.run"].meta["kind"], "base")

    def test_the_quality_rules_pass_on_all_six_bases(self):
        for style in SLICE_STYLES:
            lib = library.style(style)
            with self.subTest(style=style):
                self.assertEqual(quality.check_built(lib, lookup_in(lib)), [])
                self.assertEqual(quality.check_timing(json.loads(dumps_json(lib.animation_json())),
                                                      json.loads(dumps_json(lib.timing_json()))), [])

    def test_the_string_hits_on_the_server_tick(self):
        """Each string attack's `hit` (and the twin blades' second hit) is in the timing file at the tick the pose
        shows it: clip time plus the action controller's 2-tick blend (4.8)."""
        for style in SLICE_STYLES:
            lib = library.style(style)
            timing = lib.timing_json()["animations"]
            for name in ("attack.1", "attack.2", "attack.3"):
                c = lib.by_name[name]
                t = timing[name]
                with self.subTest(style=style, name=name):
                    self.assertEqual(t["frame_ticks"]["windup"], int(round(hits(c)[0] / 0.05)) + 2)
                    self.assertEqual(len(t["hits"]), len(hits(c)))
        self.assertEqual(len(hits(library.style("twin_blades").by_name["attack.2"])), 2)

    def test_draw_and_sheathe_swap_the_weapon_flag(self):
        for style in SLICE_STYLES:
            lib = library.style(style)
            draw, sheathe = lib.by_name["draw"], lib.by_name["sheathe"]
            with self.subTest(style=style):
                self.assertEqual((draw.meta["over"], draw.meta["settle_to"]), ("move.idle", "combat.idle"))
                self.assertEqual((sheathe.meta["over"], sheathe.meta["settle_to"]), ("combat.idle", "move.idle"))
                self.assertEqual([e.value for e in draw.events if e.kind == "instruction"], ["weapon:drawn"])
                self.assertEqual([e.value for e in sheathe.events if e.kind == "instruction"], ["weapon:sheathed"])
                self.assertEqual(quality.check_settle(draw, lib.by_name["combat.idle"]), [])
                self.assertEqual(quality.check_settle(sheathe, library.core().by_name["move.idle"]), [])

    def test_strikes_leave_the_legs_to_the_walk(self):
        """Every string attack but the hammer's slam keys no leg bone (a hero swings while the base controller walks
        it); the slam bends the knees with the soles kept on the ground."""
        legs = ("leg_r", "leg_l", "shin_r", "shin_l", "foot_r", "foot_l", "root")
        for style in SLICE_STYLES:
            lib = library.style(style)
            for name in ("attack.1", "attack.2", "attack.3", "guard", "taunt"):
                if (style, name) == ("hammer", "attack.3"):
                    continue
                keyed = {b for b, _ in lib.by_name[name].tracks}
                self.assertFalse(keyed & set(legs), "%s %s" % (style, name))
        slam = library.style("hammer").by_name["attack.3"]
        self.assertIn(("shin_r", "rotation"), slam.tracks)
        self.assertTrue(slam.tracks[("root", "position")].scaled(), "the root drop is form-scaled")


class StringReadsTest(unittest.TestCase):
    """On the teen (the form the poses are solved on): each string reads as its weapon."""

    def test_the_hammer_slam_drives_the_club_head_down(self):
        m, w0 = world_at("hammer", "benika", "attack.3", 0.0)
        m, w = world_at("hammer", "benika", "attack.3", 0.55)
        tip, grip = point(m, w, "vfx_tip_r"), point(m, w, "weapon_r")
        self.assertLess(tip[1], grip[1] - 8, "the head is below the hands")
        self.assertLess(tip[1], point(m, w0, "vfx_tip_r")[1] - 25, "from over the shoulder to the ground")

    def test_the_side_swing_crosses_the_front(self):
        m, w = world_at("hammer", "benika", "attack.2", 0.25)
        self.assertGreater(axis(w, "weapon_r", (0, 0, -1))[2], 0.5, "wound up: the head behind")
        m, w = world_at("hammer", "benika", "attack.2", 0.45)
        d = axis(w, "weapon_r", (0, 0, -1))
        self.assertLess(d[2], -0.6, "the head swept forward")
        self.assertGreater(d[0], 0.3, "and across to the left")
        self.assertTrue(-0.2 < d[1] < 0.6, "about level, rising a little")

    def test_the_shield_bash_drives_the_shield_forward(self):
        m, w0 = world_at("sword_shield", "ilsabet", "attack.3", 0.25)
        m, w = world_at("sword_shield", "ilsabet", "attack.3", 0.45)
        self.assertLess(point(m, w, "weapon_l")[2], point(m, w0, "weapon_l")[2] - 5)
        self.assertGreater(axis(w, "weapon_l", (1, 0, 0)) @ np.array([0, 0, -1.0]), 0.6, "its face leads")

    def test_casts_point_at_the_target(self):
        fwd = np.array([0.0, 0.0, -1.0])
        m, w = world_at("staff", "ambrose", "attack.1", 0.4)
        self.assertGreater(axis(w, "weapon_r", (0, 0, -1)) @ fwd, 0.8)
        m, w = world_at("focus", "yugen", "attack.1", 0.4)
        self.assertGreater(axis(w, "weapon_r", (0, 0, -1)) @ fwd, 0.6)
        m, w = world_at("twin_blades", "tobimaru", "attack.ranged", 0.4)
        self.assertGreater(axis(w, "weapon_l", (0, 0, -1)) @ fwd, 0.8, "the kunai thrown at the target")

    def test_the_twin_blades_alternate_hands(self):
        """attack.2: the kunai stabs forward on the first hit, the reversed blade cuts across on the second."""
        m, w0 = world_at("twin_blades", "tobimaru", "attack.2", 0.22)
        m, w1 = world_at("twin_blades", "tobimaru", "attack.2", 0.4)
        self.assertLess(point(m, w1, "weapon_l")[2], point(m, w0, "weapon_l")[2] - 8)
        m, w2 = world_at("twin_blades", "tobimaru", "attack.2", 0.5)
        m, w3 = world_at("twin_blades", "tobimaru", "attack.2", 0.6)
        self.assertGreater(point(m, w3, "weapon_r")[0], point(m, w2, "weapon_r")[0] + 8, "swept across to the left")

    def test_work_strokes_hold_the_tool_like_the_core(self):
        """The hammer's two-handed work.mine strikes forward with the pick's head (item_r's -z) at belly height, the
        left hand on the handle; the katana's work.chop lays the axe flat into the trunk, edge leading left."""
        fwd = np.array([0.0, 0.0, -1.0])
        m, w = world_at("hammer", "benika", "work.mine", 0.38)
        self.assertGreater(axis(w, "item_r", (0, 0, -1)) @ fwd, 0.7)
        self.assertLess(point(m, w, "item_r")[2], -6)
        self.assertLess(np.linalg.norm(point(m, w, "item_l") - point(m, w, "item_r")), 5.0)
        m, w = world_at("katana", "kagenori", "work.chop", 0.4)
        d = axis(w, "item_r", (0, 0, -1))
        self.assertLess(abs(d[1]), 0.4, "flat")
        self.assertGreater(axis(w, "item_r", (0, -1, 0))[0], 0.4, "the edge leads to the left")
        for style, name in WORK.items():
            c = library.style(style).by_name[name]
            self.assertEqual((c.meta["over"], c.meta["settle_to"]), ("move.idle", "move.idle"))
            self.assertFalse({b for b, _ in c.tracks} & {"leg_r", "leg_l", "body", "root"})


class PropClearanceTest(unittest.TestCase):
    def test_props_clear_the_body_through_every_clip_on_every_base(self):
        """grips.animation_problems over every second tick of every clip of the style (the restyled reactions
        included) for every sample outfit on all six bases: no prop more than 1 px into the body, none more than
        1.5 px into the ground (lying reactions aside), a two-handed club held by both hands."""
        core = library.core()
        for o in lint.load_outfits():
            hero0 = lint.outfit_hero(o, "teen_f")
            style, _ = grips.style_of_weapon(hero0)
            lib = library.style(style)
            for base in BASE_IDS:
                hero = lint.outfit_hero(o, base)
                with self.subTest(outfit=o["id"], base=base):
                    got = grips.animation_problems(hero, _Built(build.assemble(hero)), lib,
                                                   lambda n, lib=lib: lib.by_name.get(n) or core.by_name.get(n),
                                                   every=2)
                    self.assertEqual(got, [])

    def test_a_guard_folded_into_the_face_fails(self):
        """Sabotage: the claws' restyled stagger over the guard (without `restyle_arms`) folds them into the head."""
        recipe = dict(library.restyled_recipes()["stagger"])
        lib = library.style("fists")
        ex = recipes.Expander(library.style_poses("fists"), library.reference_skeleton(), lookup_in(lib),
                              "combat.idle", library.form_skeletons())
        bad = ex.expand("stagger", recipe)

        class One:
            clips = [bad]
            by_name = {"stagger": bad}
        hero = lint.outfit_hero(outfit("natsuho"), "chibi_f")
        got = grips.animation_problems(hero, _Built(build.assemble(hero)), One, lookup_in(lib), every=2)
        self.assertTrue(any("into head" in p for p in got), got)

    def test_the_combat_stance_passes_the_coplanar_rule(self):
        """`herogen lint` checks a weapon part in the first frame of its style's combat.idle (HeroAssets.stance) with
        the coplanar rule. The hair kit's back_base lies on the head's back plane and z-fights through the hem when
        hair_back is at rest (an open item of the hair kit), so a combat stance keeps the head bowed a little and
        the bake hangs the hair off it (the first focus, hammer and staff stances held the head level and failed);
        a wrist flexed with no sideways turn laid the hand's sides on the forearm's (the first sword_shield stance)."""
        import cli
        for style in SLICE_STYLES:
            pid = outfit(OUTFIT_OF[style])["art"]["weapon"]["prop"]
            hero = lint.lint_hero(pid, "teen_f")
            files, b = build.build_files(hero["id"], hero)
            with self.subTest(style=style, prop=pid):
                self.assertEqual(cli.tie_problems(hero["id"], files, b, hint=False), [])
                hair = library.style(style).by_name["combat.idle"].tracks.get(("hair_back", "rotation"))
                self.assertIsNotNone(hair, "the hair rests on the head's back plane")


class GeneratorRulesTest(unittest.TestCase):
    def test_arms_over_moves_the_arm_offsets_onto_another_pose(self):
        """`arms_over`: a key with `over` adds its arm offsets to that pose's arms, the @stance keys keep the
        stance (the restyled death of a style lowers the weapon arm and still ends on the guard)."""
        lib = library.style("katana")
        death = lib.by_name["death"]
        core_death = library.core().by_name["death"]
        a = death.tracks[("arm_r", "rotation")].sample(0.52)
        b = core_death.tracks[("arm_r", "rotation")].sample(0.52)
        self.assertLess(max(abs(x - y) for x, y in zip(a, b)), 12.0, "the core's arms, near enough")
        hurt = lib.by_name["hurt.heavy"]          # not in restyle_arms: offsets over the guard
        stance = recipes.stance_of(lib.by_name["combat.idle"])
        f = hurt.tracks[("forearm_r", "rotation")].sample(0.1)
        self.assertLess(abs(f[0] - stance["forearm_r"]["rotation"][0]), 30.0)
        ex = recipes.Expander(library.pose_library(), library.reference_skeleton(), lookup_in(lib), "combat.idle",
                              library.form_skeletons())
        with self.assertRaises(HerogenError):
            ex.expand("x", {"length": 0.5, "arms_over": "no.such_pose", "keys": [{"t": 0, "pose": "@stance"}]})

    def test_a_settle_key_after_the_next_pose_key_fails(self):
        """The first G5 draft held each wind-up with `settle`, whose extra key landed after the hit and pulled the
        arm back to the wind-up for a frame (fists attack.3 at 0.50 s, focus attack.1 at 0.45 s)."""
        ex = recipes.Expander(library.style_poses("fists"), library.reference_skeleton(),
                              lookup_in(library.style("fists")), "combat.idle", library.form_skeletons())
        bad = {"length": 1.0, "class": "light", "keys": [
            {"t": 0, "pose": "fists.stance"},
            {"t": 0.25, "pose": "fists.rake_wind"},
            {"t": 0.35, "pose": "fists.rake_wind", "settle": -0.06},
            {"t": 0.45, "pose": "fists.rake", "marker": "hit"},
            {"t": 1.0, "pose": "fists.stance"}]}
        with self.assertRaises(HerogenError):
            ex.expand("attack.3", bad)
        ok = json.loads(json.dumps(bad))
        del ok["keys"][2]["settle"]
        ex.expand("attack.3", ok)

    def test_a_deep_stance_stands_on_every_form(self):
        """The twin blades' crouch (knees 26) uses the form-scaled floor fix: the soles touch y 0 on every base, where
        the three-form compromise left a chibi's 0.64 px in the ground."""
        from anim import skeleton as S
        c = library.style("twin_blades").by_name["combat.idle"]
        for base in BASE_IDS:
            sk = S.for_base(base)
            pose = c.sample(0.0, sk.d)
            low = min(float(p[1]) for pts in sk.soles(pose).values() for p in pts)
            self.assertAlmostEqual(low, 0.0, delta=0.1, msg=base)

    def test_the_previews_show_every_slice_style_with_its_weapon(self):
        from anim import style_preview
        self.assertEqual(sorted(style_preview.HEROES), sorted(SLICE_STYLES))
        for style, rows in style_preview.HEROES.items():
            self.assertEqual([b.split("_")[0] for _, b in rows], ["chibi", "adult"], style)
            for oid, base in rows:
                hero = lint.outfit_hero(outfit(oid), base)
                self.assertEqual(grips.style_of_weapon(hero)[0], style)


if __name__ == "__main__":
    unittest.main()
