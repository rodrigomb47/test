"""C4: Ilsabet Frostmere, the rime vow knight, the seventh slice hero with her own source, art and signature
animations.

Her one body (the owner's rule), her moves' frames equal to her kit's (Oath Strike's two cuts), her sound markers, her
clips' rules on every base and the sword and the shield clear of her body; the visor that snaps down in phase 2 and
stays down (the helm's new hinge bone, held down by her `sec.loop` on the secondary controller, which plays after
every other controller, while `phase.2` swings the visor shut on it); the stills that draw what `sec.loop` holds;
the standard that rises on her back in phase 3; her arms and props clear of the helm's and the standard's thick cubes
(the sampled depth cannot see their thin plates) and, by the exact box overlap (grips.box_overlap, tested here on
made-up cubes), clear of the whole helm, her head and the standard, the blade clear of the shield."""
import json
import os
import unittest

import numpy as np

import helpers  # noqa: F401
import assets
import build
import grips
import validate
from anim import library
from anim import quality
from anim.clip import form_var
from anim.skeleton import Skeleton
from util import ROOT

HID = "ilsabet_frostmere"
FIXTURE = os.path.join(ROOT, "src", "test", "resources", "heroes", "slice", HID + ".json")
OWN = {"move.shield_bash", "move.oath_strike", "move.rime_wall", "move.turning_sweep", "move.frost_lance_charge",
       "move.frozen_standard", "move.call_the_guard", "ultimate", "state.bulwark", "phase.2", "phase.3", "intro",
       "victory", "taunt", "last_stand", "personal.1", "personal.2", "personal.3", "sec.loop"}
VISOR_UP = 75.0          # the helm's visor_up: the visor's rest is raised by this much about its hinge


def _ticks(s):
    return int(round(float(s) * 20))


class IlsabetTest(unittest.TestCase):
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
        for b in (self.own, library.style("sword_shield"), library.core()):
            if name in b.by_name:
                return b.by_name[name]
        return None

    def cubes_of(self, roots):
        bones = set(grips._subtree(self.model, roots))
        return [c for c in self.model.cubes if c.bone in bones]

    def depth(self, pose, mine, theirs):
        """How far (px) any sample point of the cubes `mine` lies inside any cube of `theirs` in a pose."""
        w = self.sk.world(pose)
        worst = 0.0
        for c in mine:
            r, t = w[c.bone]
            pts = grips._samples(c) @ r.T + t
            for b in theirs:
                rb, tb = w[b.bone]
                local = (pts - tb) @ rb
                if b.rotation is not None:
                    pv = np.array(b.pivot, dtype=float)
                    local = pv + (local - pv) @ grips.file_matrix(b.rotation)
                worst = max(worst, float(grips._depth_inside(local, b.box).max()))
        return worst

    # -------------------------------------------------------------- body, kit, frames, rules

    def test_one_body_for_good(self):
        """An adult woman of ordinary size, standard build, drawn at 104% (SLICE_PLAN 3.7; the owner's one-body rule
        of HEROES_V2 3.6): no phase changes the body; she has no tail."""
        art = self.hero["art"]
        self.assertEqual((art["form"], art["base"], art["build"], art["scale"]), ("adult", "female", "standard", 104))
        self.assertEqual((self.visual["form"], self.visual["base"], self.visual["build"]),
                         ("adult", "adult_f", "standard"))
        for ph in art.get("phases", []):
            self.assertFalse({"form", "base", "build"} & set(ph), "a phase changes the body")
        self.assertNotIn("tail_1", self.model.bones)

    def test_kit_is_the_fixtures(self):
        for k, v in self.kit.items():
            if k != "_comment":
                self.assertEqual(self.hero[k], v, k)

    def test_budgets(self):
        """The helm from phase 2, the standard and the blade's frost shells from phase 3, the sword and the kite
        shield in both their copies inside HEROES_V2 4.3's budgets (100 bones, 300 cubes, 130 cubes outside fine_
        bones) and SLICE_PLAN 3.9's 72 bones."""
        problems, g = assets.check_geometry(HID, self.files, self.visual)
        self.assertEqual(problems, [])
        self.assertLessEqual(len(g.order), 72)
        self.assertLessEqual(len(g.cubes), 300)
        self.assertLessEqual(validate.lod_cubes(g), 130)

    def test_move_frames_equal_the_kit(self):
        """Every move's clip and the ultimate's have the kit's wind-up, active and recovery to the tick (from the
        action's start, the 2-tick blend included): the release on a `hit`, the active part's last tick on a `cast`;
        Oath Strike has a `hit` per cut (ticks 9 and 15: the markers replace the form's hit ticks, MovePlan)."""
        timing = self.own.timing_json()["animations"]
        items = [("move." + m["id"], m) for m in self.kit["moves"]] + [("ultimate", self.kit["ultimate"])]
        for name, m in items:
            want = {k: _ticks(v) for k, v in m["frames"].items()}
            self.assertEqual(timing[name]["frame_ticks"], want, name)
            hits = [e["tick"] for e in timing[name]["events"] if e["type"] == "hit"]
            w, a = want["windup"], want["active"]
            self.assertEqual(hits, [w, w + a - 1] if m["form"] == "combo" else [w], name)
        self.assertEqual([e["tick"] for e in timing["move.oath_strike"]["events"] if e["type"] == "hit"], [9, 15])
        definition, _ = assets.definition_of(HID)
        files = dict(self.files, **library.core().files(), **library.style("sword_shield").files())
        self.assertEqual(assets.check_frames(definition, assets.Chain.of(self.visual, files)), [])

    def test_own_clips_pass_every_rule_on_every_base(self):
        """Every own clip passes the quality rules on all six bases; she never escapes (SLICE_PLAN 3.7: her vow is to
        stand between the enemy and her people), so no escape clip; no ultimate.loop."""
        self.assertEqual(quality.check_built(self.own, self.lookup, list(self.b.model.bones), own_file=True), [])
        names = {c.name for c in self.own.clips}
        self.assertEqual(names, OWN)

    def test_sword_and_shield_clear_of_her_body(self):
        """The longsword and the kite shield clear of her body on every tick of her own clips."""
        self.assertTrue(grips.prop_cubes(self.model, "l"))   # the shield is a prop of the left hand
        self.assertEqual(grips.animation_problems(self.hero, self.b, self.own, self.lookup, every=1), [])

    def test_clear_of_the_helm_and_the_standard(self):
        """Her arms, the sword and the shield at most 0.5 px inside the helm (its visor held down) and the standard's
        pole and banner on every tick of her own clips and her style's string and guard (phases 2 and 3 wear them;
        they are checked in every clip, since a phase-1 move also plays in phases 2 and 3). A point's depth is its
        distance to a cube's nearest face, so this sees only cubes thicker than 1 px (the helm's two lower skull slabs,
        the standard's finial): a crossing of the thin plates and the pole (the cheek, back and visor plates, the
        banner, the 0.7 px pole) stays under the limit whatever it is (artgen.md, C4, open items)."""
        arms = self.cubes_of(["arm_r", "arm_l"])
        props = {c.bone for c in self.cubes_of(["weapon_r", "weapon_l"])}
        arms = [c for c in arms if c.bone not in props]
        mine = {"arms": arms, "sword": self.cubes_of(["weapon_r"]), "shield": self.cubes_of(["weapon_l"])}
        theirs = {"helm": self.cubes_of(["p2_hat", "p2_visor_hinge"]), "standard": self.cubes_of(["p3_back",
                                                                                               "p3_banner"])}
        self.assertTrue(theirs["helm"] and theirs["standard"])
        names = [c.name for c in self.own.clips if c.name not in ("sec.loop", "phase.2")]
        names += ["attack.1", "attack.2", "attack.3", "guard", "guard.loop", "guard.hit"]
        for name in names:
            for t, pose, _ in grips.clip_poses(self.lookup(name), self.lookup, self.d, 1):
                pose = dict(pose, p2_visor_hinge={"rotation": (VISOR_UP, 0.0, 0.0)})
                for g, cs in mine.items():
                    for o, os_ in theirs.items():
                        self.assertLess(self.depth(pose, cs, os_), 0.5, (name, round(t, 2), g, o))

    def test_box_overlap_sees_thin_plates(self):
        """grips.box_overlap (C4): the exact overlap of two cubes over their 15 separating axes. Against a kite-shield
        plate 0.45 px thin (7 x 9), a 0.44 x 1.4 blade through its middle reads 4.2 px (half the plate's height and
        half the blade's width), a 0.1 px graze 0.1, a crossing 1 px in from the rim 1.0 and a blade over the top 0;
        the plate turned 90 degrees on its own pivot reads as turned. The sampled depth cannot pass 0.225 there."""
        from rig import Cube
        world = {"b": (np.eye(3), np.zeros(3))}

        def cube(origin, size, **kw):
            return Cube("c", "b", tuple(origin), tuple(size), "metal", "metal", **kw)

        def overlap(a, b):
            return grips.box_overlap(grips._oriented_box(a, world), grips._oriented_box(b, world))
        plate = cube((-0.225, -3.5, -4.5), (0.45, 7.0, 9.0))
        self.assertAlmostEqual(overlap(cube((-7.5, -0.7, -0.22), (15.0, 1.4, 0.44)), plate), 4.2, places=6)
        self.assertAlmostEqual(overlap(cube((0.125, -0.7, -0.22), (15.0, 1.4, 0.44)), plate), 0.1, places=6)
        self.assertAlmostEqual(overlap(cube((-7.5, 2.5, -0.22), (15.0, 1.4, 0.44)), plate), 1.0, places=6)
        self.assertEqual(overlap(cube((-7.5, 3.6, -0.22), (15.0, 1.4, 0.44)), plate), 0.0)
        turned = cube((-0.225, -3.5, -4.5), (0.45, 7.0, 9.0), rotation=(0.0, 90.0, 0.0), pivot=(0.0, 0.0, 0.0))
        along_z = cube((-0.22, -0.7, -7.5), (0.44, 1.4, 15.0))
        self.assertAlmostEqual(overlap(along_z, turned), 4.2, places=6)
        self.assertAlmostEqual(overlap(along_z, plate), 0.445, places=6)
        blade = cube((-7.5, -0.7, -0.22), (15.0, 1.4, 0.44))
        local = grips._samples(blade)
        self.assertLessEqual(float(grips._depth_inside(local, plate.box).max()), 0.225 + 1e-9)

    def test_clear_of_the_helm_head_and_standard_exactly(self):
        """With the exact overlap (grips.worst_overlap), on every tick of her own clips (but sec.loop): her arms (the
        gauntlets included), the sword and the shield at most 0.5 px into the helm (its visor held down on the hinge;
        phase.2 turns the visor itself), her head and the standard's pole and banner; the sword at most 0.75 px into
        the shield (the blade passes close over its top, never through it). Her style's guard and sheathe are shared
        and not hers to fix (artgen.md, C4, open items)."""
        arm_bones = set(grips._subtree(self.model, ["arm_r", "arm_l"]))
        mine = [c for c in self.model.cubes if c.bone in arm_bones]
        sword, shield = self.cubes_of(["weapon_r"]), self.cubes_of(["weapon_l"])
        theirs = self.cubes_of(["p2_hat", "p2_visor_hinge", "p3_back", "p3_banner"])
        theirs += [c for c in self.model.cubes if c.bone == "head" and c.part == "base"]
        for clip in self.own.clips:
            if clip.name == "sec.loop":
                continue
            for t, pose, drawn in grips.clip_poses(clip, self.lookup, self.d, 1):
                pose = dict(pose, p2_visor_hinge={"rotation": (VISOR_UP, 0.0, 0.0)})
                world = self.sk.world(pose)
                v, k = grips.worst_overlap(world, mine, theirs)
                self.assertLessEqual(v, 0.5, (clip.name, round(t, 2), k))
                if drawn:
                    v, k = grips.worst_overlap(world, sword, shield)
                    self.assertLessEqual(v, 0.75, (clip.name, round(t, 2), k))

    # -------------------------------------------------------------- the visor

    def test_the_helms_visor_hangs_from_a_hinge(self):
        """visored_helm (C4): the visor hangs from `visor_hinge`, a cubeless bone at the visor's own pivot, so a
        `sec.loop` can hold the visor down on the hinge while a clip turns the visor itself."""
        m = self.model
        self.assertEqual(m.bones["p2_visor"].parent, "p2_visor_hinge")
        self.assertEqual(tuple(m.bones["p2_visor_hinge"].pivot), tuple(m.bones["p2_visor"].pivot))
        self.assertFalse([c for c in m.cubes if c.bone == "p2_visor_hinge"])
        self.assertEqual(self.visual["bones"]["phase"].get("p2_visor_hinge"), 2)

    def test_sec_loop_holds_the_visor_down(self):
        """Her sec.loop keys only the hinge's rotation, at +visor_up on every tick (the visor down), and no other clip
        keys the hinge; lowered, the visor's plates cover her face (in front of it below the lash line) while at rest
        they do not (the face rule of 14.2 passes on the rest pose)."""
        loop = self.own.by_name["sec.loop"]
        self.assertEqual({b for b, _ in loop.tracks if b != "face_ctrl"}, {"p2_visor_hinge"})
        for i in range(int(round(loop.length / 0.05)) + 1):
            self.assertEqual(tuple(round(v, 6) for v in loop.sample(i * 0.05, self.d)["p2_visor_hinge"]["rotation"]),
                             (VISOR_UP, 0.0, 0.0))
        for c in self.own.clips:
            if c.name != "sec.loop":
                self.assertFalse([k for k in c.tracks if k[0] == "p2_visor_hinge"], c.name)
        visor = [c for c in self.model.cubes if c.bone == "p2_visor"]
        head_y = self.model.bones["head"].pivot[1]

        def front_of_face(pose):
            w = self.sk.world(pose)
            n = 0
            for c in visor:
                r, t = w[c.bone]
                pts = grips._samples(c) @ r.T + t
                n += int(np.sum((pts[:, 2] < -5.0) & (pts[:, 1] > head_y + 1.5) & (pts[:, 1] < head_y + 5.5)
                                & (np.abs(pts[:, 0]) < 4.5)))
            return n
        self.assertEqual(front_of_face({}), 0)
        self.assertGreater(front_of_face({"p2_visor_hinge": {"rotation": (VISOR_UP, 0.0, 0.0)}}), 20)
        problems, _ = assets.check_geometry(HID, self.files, self.visual)     # the face rule on the rest geometry
        self.assertFalse([p for p in problems if "in front of the face" in p])

    def test_phase_2_snaps_the_visor_shut(self):
        """phase.2 starts with the visor raised against the held hinge (-visor_up), snaps it down with an overshoot
        between 0.38 and 0.45 s, and ends with it shut (0); it fits the 1.2 s transition, and phase.3 the 1.4 s."""
        tr = self.own.by_name["phase.2"].tracks[("p2_visor", "rotation")]
        self.assertAlmostEqual(tr.sample(0.0)[0], -VISOR_UP, places=3)
        self.assertAlmostEqual(tr.sample(0.38)[0], -VISOR_UP, places=3)
        self.assertGreater(tr.sample(0.45)[0], 0.0)              # slammed past shut
        self.assertAlmostEqual(tr.sample(self.own.by_name["phase.2"].length)[0], 0.0, places=6)
        self.assertLessEqual(self.own.by_name["phase.2"].length, 1.2)
        self.assertLessEqual(self.own.by_name["phase.3"].length, 1.4)

    def test_stills_draw_what_sec_loop_holds(self):
        """HeroAssets.stance() lays the channels sec.loop holds still over the stance (the secondary controller plays
        all the time): her stills show the visor down; no other hero's sec.loop holds anything, so their stills are
        as before."""
        from render.scene import HeroAssets
        a = HeroAssets.build(HID)
        self.assertEqual(a.held(), {"p2_visor_hinge": {"rotation": (VISOR_UP, 0.0, 0.0)}})
        self.assertEqual(tuple(a.stance()["p2_visor_hinge"]["rotation"]), (VISOR_UP, 0.0, 0.0))
        self.assertEqual(tuple(a.stance(True)["p2_visor_hinge"]["rotation"]), (VISOR_UP, 0.0, 0.0))
        for other in ("hinae_shirasuzu", "yugen_kagaribi", "ambrose_nettlewick"):
            self.assertEqual(HeroAssets.build(other).held(), {}, other)

    # -------------------------------------------------------------- the standard and the blade

    def test_phase_looks(self):
        """Phase 2 puts the visored helm on (its bones show from phase 2) and hides her hair under it; phase 3 raises
        the standard on her back and lights the blade's edge (glowing shells on p3_ bones)."""
        from render import hero_previews as hp
        bones = self.visual["bones"]["phase"]
        helm = [b for b in bones if b.startswith("p2_")]
        std = [b for b in bones if b.startswith("p3_") and b != "p3_weapon_r"]
        self.assertTrue(helm and std)
        self.assertTrue(all(bones[b] == 2 for b in helm) and all(bones[b] == 3 for b in std))
        glow = [c for c in self.model.cubes if c.name.endswith("glow")]
        self.assertTrue(glow)
        self.assertEqual({c.bone for c in glow}, {"p3_weapon_r"})
        a = hp.assets(HID)
        hidden = {ph: set(a.hidden(phase=ph, drawn=True)) for ph in (1, 2, 3)}
        self.assertTrue(set(helm) <= hidden[1] and not set(helm) & hidden[2])
        self.assertTrue(set(std) <= hidden[2] and not set(std) & hidden[3])
        # the helm closes over her head: from phase 2 her hair (the braided crown would poke through it) is hidden
        hair = {c.bone for c in self.model.cubes if c.part == "hair/braid_crown"}
        self.assertTrue(hair)
        self.assertFalse(hair & hidden[1])
        self.assertTrue(hair <= hidden[2] and hair <= hidden[3])

    def test_phase_3_raises_the_standard(self):
        """phase.3 starts with the standard low in its socket (14 px down) and its banner furled, raises it with a
        small overshoot and unfurls the banner, and ends with both at rest."""
        clip = self.own.by_name["phase.3"]
        pole = clip.tracks[("p3_back", "position")]
        banner = clip.tracks[("p3_banner", "scale")]
        self.assertLess(pole.sample(0.0)[1], -10.0)
        self.assertGreater(max(pole.sample(t)[1] for t in (0.45, 0.5, 0.55)), 0.5)
        self.assertLess(banner.sample(0.0)[1], 0.3)
        self.assertAlmostEqual(pole.sample(clip.length)[1], 0.0, places=6)
        self.assertAlmostEqual(banner.sample(clip.length)[1], 1.0, places=6)

    # -------------------------------------------------------------- sounds and faces

    def test_sound_markers(self):
        """Her client-played sounds are markers in the clips their slots name (SOUND.md 4.7, 5): the client
        `release` of Oath Strike (one per cut), Rime Wall and Turning Sweep, Frost Lance Charge's `end` (the shield
        planted), the phases, the intro and the quirks; the server's slots (wind-ups, impacts, Call the Guard's horn,
        the ultimate's release) are never markers."""
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
            if "release" in slots:
                if plays(slots["release"]) == "client":
                    self.assertIn(pre + slots["release"], sounds.get(clip, []), mid)
                else:
                    self.assertNotIn(pre + slots["release"], sounds.get(clip, []), mid)
            if "end" in slots:                                # an `end` is always the client's keyframe
                self.assertIn(pre + slots["end"], sounds.get(clip, []), mid)
        self.assertEqual(sounds["move.oath_strike"].count(pre + "sword_swing"), 2)
        self.assertNotIn("move.call_the_guard", sounds)       # its horn is the server's (a sting)
        self.assertIn(pre + hm["phases"]["2"], sounds["phase.2"])
        self.assertIn(pre + hm["phases"]["3"], sounds["phase.3"])
        self.assertIn(pre + hm["intro"], sounds["intro"])
        for n, s in hm["quirks"].items():
            self.assertIn(pre + s, sounds[n], n)
        server = {pre + s for m in hm["moves"].values() for k, s in m.items() if k in ("windup", "impact")}
        server |= {pre + hm["states"]["bulwark"], pre + "resolve"}
        for name, got in sounds.items():
            self.assertFalse(set(got) & server, name)

    def test_faces(self):
        """face_ctrl in every clip but sec.loop; her angry face is stern (focused eyes over a frown, never bared
        teeth) and her smug one a plain smile (a righteous knight is never smug); the moves leave the eyes to the
        mood (the wind-up's focused eyes)."""
        for c in self.own.clips:
            if c.name != "sec.loop":
                self.assertIn(("face_ctrl", "position"), c.tracks, c.name)
        moods = self.visual["face"]["moods"]
        self.assertEqual((moods["angry"]["eyes"], moods["angry"]["mouth"]), (8, 5))
        self.assertEqual((moods["smug"]["eyes"], moods["smug"]["mouth"]), (0, 1))
        for m in self.kit["moves"]:
            tr = self.own.by_name["move." + m["id"]].tracks[("face_ctrl", "position")]
            self.assertTrue(all(k.v[0] == 0 for k in tr.keys), m["id"])

    # -------------------------------------------------------------- previews

    def test_locomotion_follows_her_traits(self):
        """A charger III and snowstride I: walk and run, the charging run and its skid, a plain jump (she has no
        leaping trait) and the ladder climb; snowstride has no clip of its own."""
        from render import hero_previews as hp

        class A:
            def __init__(self, mob):
                self.hero = {"mobility": mob}
                self.visual = {"base": "adult_f"}

        mine = [n for s in hp.loco_segments(A(self.hero["mobility"])) for n, _ in s.items]
        self.assertEqual(mine, ["move.walk", "move.run", "move.charge_run", "move.skid", "move.jump", "move.leap",
                                "move.land", "move.climb"])

    def test_previews_draw_her_in_her_own_phases(self):
        """state.bulwark is drawn in phase 2 (the phase that enters it, after its transition), Frost Lance Charge in
        phase 2, Frozen Standard and Call the Guard in phase 3, her last stand in phase 3; her captions name her
        phase looks."""
        from render import hero_previews as hp
        a = hp.assets(HID)
        self.assertEqual(hp.phase_of_clip(a, "state.bulwark"), 2)
        self.assertEqual(hp.phase_of_clip(a, "move.frost_lance_charge"), 2)
        self.assertEqual(hp.phase_of_clip(a, "move.frozen_standard"), 3)
        self.assertEqual(hp.phase_of_clip(a, "move.call_the_guard"), 3)
        self.assertEqual(hp.phase_of_clip(a, "last_stand"), 3)
        self.assertIn("visor", hp.captions(a)["phase.2"])
        self.assertIn("standard", hp.captions(a)["phase.3"])

    def test_the_lineup_takes_her(self):
        from render import hero_previews as hp
        ids = hp.lineup_ids()
        self.assertIn(HID, ids)
        self.assertEqual(ids[-1], "test_rig")


if __name__ == "__main__":
    unittest.main()
