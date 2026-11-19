"""No tail between the legs (owner, 2026-11-19, looking at the Ender Dragon's wings sheet: "her tail downwards while
she is flying, and it kinda looks like a male genitalia like this, can you make it so her tail will not go downwards
when she flies").

- tails.py, the rule `herogen check` applies to every hero with a tail: no tail spine point below the crotch between
  the legs, from the back plane forward, in any frame of an airborne clip the hero plays or any frame off the ground,
  nor in the stance; from the front no more than a hairline of tail between the legs; a NOTE for a tip pointing
  straight down below the knees. The negative tests: the dragon's glide and beat before the fix fail, after it pass.
- anim/tailtrail.py, the fix: her tail chain re-keyed on her own skeleton to trail behind her in the air.
"""
import copy
import math
import unittest

import numpy as np

import helpers  # noqa: F401
import build
import tails
from anim import library as animlib
from anim import tailtrail
from anim.skeleton import Skeleton
from render import hero_previews as hp
from render.scene import HeroAssets
from util import HerogenError

FLIGHT = ["move.glide", "move.fly", "move.fly_hover", "move.takeoff"]


def assets(hero):
    a = HeroAssets.build(hero)
    chain = [a.built.animations, animlib.style(animlib.style_of(a.built.hero)), animlib.core()]

    def lookup(name):
        for built in chain:
            if name in built.by_name:
                return built.by_name[name]
        return None
    return a, lookup


def untrailed():
    """The Ender Dragon as B5 committed her: no tail_trail."""
    hero = copy.deepcopy(build.load_hero("the_ender_dragon"))
    del hero["animations"]["tail_trail"]
    return hero


def sheet_rows(a):
    """The flight rows of wings.png the owner looked at: the glide under the glide loop, the top and the bottom of
    the beat under the flap loop (hero_previews.wings_sheet)."""
    def clip_pose(name, t):
        return a.chain.get(name).sample(t, a.form_var())
    return {"glide": hp.wings(a, clip_pose("move.glide", 0.0), 0.0, "sec.wings_glide"),
            "beat top": hp.wings(a, clip_pose("move.fly", 0.0), 0.0, "sec.wings_flap"),
            "beat bottom": hp.wings(a, clip_pose("move.fly", 0.2), 0.2, "sec.wings_flap")}


class DragonTailTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixed = assets("the_ender_dragon")
        cls.old = assets(untrailed())

    def test_the_owners_rows_fail_before_and_pass_after(self):
        """The glide and both beat rows of the wings sheet: the old tail hangs between her legs (dozens of spine
        points in the zone), the trailing one has none."""
        for which, expect in ((self.old, True), (self.fixed, False)):
            a, _ = which
            rule = tails.TailRule(a.built.model)
            for row, pose in sheet_rows(a).items():
                hits = rule.hits(rule.sk.world(pose))
                if expect:
                    self.assertGreaterEqual(sum(hits.values()), 10, row)
                else:
                    self.assertEqual(hits, {}, row)

    def test_check_refuses_the_old_flight_and_accepts_the_fix(self):
        a, lookup = self.old
        problems, notes = tails.check(a, a.built.model, lookup, names=FLIGHT)
        failing = {p.split(":")[0] for p in problems}
        self.assertTrue({"move.glide", "move.fly", "move.takeoff"} <= failing, problems)
        self.assertTrue(any(n.startswith("move.glide:") and "straight down" in n for n in notes), notes)
        a, lookup = self.fixed
        self.assertEqual(tails.check(a, a.built.model, lookup, names=FLIGHT), ([], []))

    def test_every_clip_she_plays_in_the_air_passes(self):
        """Her whole chain (every airborne clip a flyer plays and every frame off the ground), the stance and the
        front view: no problem, no NOTE."""
        a, lookup = self.fixed
        self.assertEqual(tails.check(a, a.built.model, lookup), ([], []))

    def test_tail_streams_behind_level_with_or_above_the_hips(self):
        """In the glide, the beat, the hover, the fast flight and the circle her tail stays no lower than a pixel under the
        crotch and its tip rides behind her (15 px or more behind the hips' centre)."""
        a, lookup = self.fixed
        rule = tails.TailRule(a.built.model)
        for name, t, pose, _ in tails.frames(a, lookup, ["move.glide", "move.fly", "move.fly_hover",
                                                          "move.fly_fast", "move.circle"]):
            w = rule.sk.world(pose)
            crotch, c, lat, lo, hi, back_ax, back = rule.zone(w)
            for b, pts in rule.points.items():
                r, tt = w[b]
                self.assertGreater(float(((r @ pts.T).T + tt)[:, 1].min()), crotch - 1.0, (name, t, b))
            tb, _, far = rule.tip
            r, tt = w[tb]
            self.assertGreater(float((r @ far + tt - c)[2]), 15.0, (name, t))   # behind her in the world (+z)

    def test_trail_keys_close_and_hand_over(self):
        """The loops close exactly, the take-off ends on move.fly's first tail key and the jump on move.leap's, the
        landing ends on its own (move.idle's) keys; the clips copied in from the core (the jump, the leap) keep every
        other track and event (no marker moves)."""
        built = self.fixed[0].built.animations
        core = animlib.core()
        for name in ("move.fly", "move.glide", "move.fly_hover", "move.leap", "move.fall"):
            c = built.by_name[name]
            for b in tailtrail.CHAIN:
                tr = c.tracks[(b, "rotation")]
                self.assertEqual(tr.keys[0].v, tr.keys[-1].v, (name, b))
        for one, loop in (("move.takeoff", "move.fly"), ("move.jump", "move.leap"), ("move.glide_start", "move.glide")):
            for b in tailtrail.CHAIN:
                end = built.by_name[one].tracks[(b, "rotation")].keys[-1].v
                self.assertEqual(end, built.by_name[loop].tracks[(b, "rotation")].sample(0.0), (one, b))
        for name in ("move.jump", "move.leap"):
            mine, theirs = built.by_name[name], core.by_name[name]
            self.assertEqual([(e.t, e.kind, e.value) for e in mine.events],
                             [(e.t, e.kind, e.value) for e in theirs.events], name)
            other = {k for k in theirs.tracks if k[0] not in tailtrail.CHAIN}
            for k in other:
                self.assertEqual([(x.t, x.v) for x in mine.tracks[k].keys],
                                 [(x.t, x.v) for x in theirs.tracks[k].keys], (name, k))

    def test_own_file_within_the_budget(self):
        """The trail copies the core's jump and leap into her file and the variant now has her fall and shot-down clip:
        23 clips, the cap 24."""
        self.assertLessEqual(len(self.fixed[0].built.animations.clips), 24)


class TailTrailTest(unittest.TestCase):
    def test_solve_points_every_link_along_the_trail(self):
        """Each link of the chain ends up pointing along the wanted direction (within half a degree), whatever the
        body's pitch."""
        hero = build.load_hero("the_ender_dragon")
        sk = Skeleton.from_model(build.assemble(hero))
        for pitch in (0.0, 40.0, 62.0, 110.0):
            pose = {"body": {"rotation": (pitch, 0.0, 0.0)}}
            r_body = sk.world(pose, only=["body"])["body"][0]
            d = tailtrail.trail_direction(r_body, 4.0)
            angles = tailtrail.solve(sk, pose, d, [(0.0, 0.0)] * 5)
            full = dict(pose, **{b: {"rotation": v} for b, v in angles.items()})
            w = sk.world(full)
            for i, b in enumerate(tailtrail.CHAIN):
                nxt = tailtrail.CHAIN[i + 1] if i + 1 < 5 else "tail_tip_1"
                v = (w[b][0] @ sk.pivot[nxt] + w[b][1]) - (w[b][0] @ sk.pivot[b] + w[b][1])
                ang = math.degrees(math.acos(float(np.dot(v / np.linalg.norm(v), d))))
                self.assertLess(ang, 0.5, (pitch, b))
            if pitch < 100:
                self.assertAlmostEqual(math.degrees(math.asin(d[1])), 4.0, places=3)
            else:
                self.assertGreater(d[1], 0.2)        # head first: the tail follows the body's line up behind her

    def test_spec_is_checked(self):
        with self.assertRaises(HerogenError):
            tailtrail.check_spec({"clips": {}}, "x")
        with self.assertRaises(HerogenError):
            tailtrail.check_spec({"clips": {"move.fly": {"ramp": [0.3, 0.1]}}}, "x")
        with self.assertRaises(HerogenError):
            tailtrail.check_spec({"clips": {"move.fly": {}}, "wobble": 3}, "x")


class TailRuleTest(unittest.TestCase):
    def test_who_plays_what(self):
        """The airborne clips follow MOBILITY_AND_WORK 4.1: an acrobat climber plays the air jump and the cling but no
        flight; a flyer the flight set and the shot-down clip; nobody the dive without a dive move."""
        class A:
            def __init__(self, mob, moves=()):
                self.hero = {"mobility": mob, "moves": list(moves)}

            def bones(self, group):
                return []
        n = tails.airborne_clips(A({"primary": {"trait": "acrobat"}, "secondary": {"trait": "climber"}}))
        self.assertIn("move.air_jump", n)
        self.assertIn("move.climb_idle", n)
        self.assertNotIn("move.fly", n)
        self.assertNotIn("move.air_dive", n)
        f = tails.airborne_clips(A({"primary": {"trait": "flyer"}}, [{"id": "d", "form": "dive"}]))
        self.assertTrue({"move.fly", "move.glide", "move.downed", "move.circle", "move.air_dive"} <= set(f))
        self.assertNotIn("move.air_jump", f)
        self.assertEqual(set(tails.airborne_clips(A(None))), set(tails.EVERYONE))

    def test_natsuho_passes(self):
        """Natsuho's cat tail (acrobat III, climber I: her jumps, leaps, falls, wall kicks, air jumps, mantle, the
        climb and every frame off the ground, her escape included) and her stance in both phases."""
        a, lookup = assets("natsuho_komezaki")
        self.assertEqual(tails.check(a, a.built.model, lookup)[0], [])

    def test_a_tail_swung_between_the_legs_fails(self):
        """Sabotage on Natsuho's stance: her tail swung down and forward under her (tail_1 -100 degrees) sits in the
        zone and shows from the front."""
        a, lookup = assets("natsuho_komezaki")
        rule = tails.TailRule(a.built.model)
        pose = dict(a.stance())
        self.assertEqual(rule.hits(rule.sk.world(pose)), {})
        pose["tail_1"] = {"rotation": (-100.0, 0.0, 0.0)}
        self.assertGreater(sum(rule.hits(rule.sk.world(pose)).values()), 5)
        self.assertGreater(tails.front_pixels(a, rule, pose), tails.FRONT_PX)
