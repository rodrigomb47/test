"""B5: the Ender Dragon's look (BOSS_HEROES.md 5.1, 5.2, 5.6, 5.12, 5.13), before her kit exists as data.

Her one body; the art-only source (`"kit": "pending"`, as the Wither's: no moves, no fake kit clips); her own clips'
rules on every base and her claws clear of her body; the parts of 5.2 and the generator pieces they needed: the
dragon wings folded at rest and opened through their own bones under the shared wing loops (about 2.2 blocks across),
the membrane veins that glow from phase 2, the swept horns with their tips, the scaled tail with its tip chain and the
pale crest (gen.py `crest`), the dorsal spines that light up in phase 3, the long skirt split to the knee, the
Spirecore pendant on its artifact bone turning on sec.loop, the purple under-layer of `hair/long_layered` (`under`),
the cheek scales and the throat glow of her face, the stripes' `gap`; the flight variant `dragon_wings` and her circle
(`move.circle`, a locomotion name); and the preview set's rules for a winged hero."""
import copy
import json
import unittest

import numpy as np

import build
import grips
import paint
from anim import library
from anim import quality
from anim import skeleton as S
from parts import apply_part, load_part
from rig import Model
from rot import file_matrix
from util import HerogenError

HID = "the_ender_dragon"
WING_BONES = ("wing_arm_r", "wing_hand_r", "wing_f2_r", "wing_f3_r")


def _cube_points(c):
    x0, y0, z0 = c.origin
    sx, sy, sz = c.size
    pts = np.array([(x0 + a, y0 + b, z0 + d) for a in (0, sx) for b in (0, sy) for d in (0, sz)], dtype=float)
    if c.rotation is not None:
        m = file_matrix(c.rotation)
        pv = np.array(c.pivot, dtype=float)
        pts = (m @ (pts - pv).T).T + pv
    return pts


class EnderDragonTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.hero = build.load_hero(HID)
        cls.files, cls.b = build.build_files(HID)
        cls.own = cls.b.animations
        cls.visual = json.loads(cls.files[build.out_paths(HID)["visual"]])
        cls.model = cls.b.model

    def lookup(self, name):
        for b in (self.own, library.style("fists"), library.core()):
            if name in b.by_name:
                return b.by_name[name]
        return None

    def cubes(self, part):
        return [c for c in self.model.cubes if c.part == part]

    def _chain(self, bone):
        out = []
        while bone:
            out.append(bone)
            bone = self.model.bones[bone].parent
        return out

    def world_points(self, cubes, pose=None):
        """Every corner of these cubes where a pose ({bone: (rx, ry, rz)}) puts them (file space)."""
        sk = S.Skeleton.from_model(self.model)
        w = sk.world({b: {"rotation": tuple(v)} for b, v in (pose or {}).items()})
        out = []
        for c in cubes:
            r, t = w[c.bone]
            out.append((r @ _cube_points(c).T).T + t)
        return np.concatenate(out)

    def open_pose(self):
        """The variant's open pose (the part's fold undone), both sides."""
        spec = library.variant("dragon_wings")[1]["dwing.open"]["bones"]
        out = {}
        for b, v in spec.items():
            out[b] = v
            out[b[:-2] + "_l"] = [v[0], -v[1], -v[2]]
        return out

    # ------------------------------------------------------------------ body and source

    def test_one_body_for_good(self):
        """An adult woman, standard build, drawn at 108% (BOSS_HEROES 5.2; HEROES_V2 3.6): 1.97 blocks, under the
        1.98 cap, and no phase changes the body."""
        art = self.hero["art"]
        self.assertEqual((art["form"], art["base"], art["build"], art["scale"]), ("adult", "female", "standard", 108))
        self.assertEqual((self.visual["form"], self.visual["base"], self.visual["build"]),
                         ("adult", "adult_f", "standard"))
        blocks = self.visual["height_px"] * self.visual["render_scale"] * self.visual["scale"] / 16.0
        self.assertAlmostEqual(blocks, 1.97, delta=0.005)
        self.assertLess(blocks, 1.98)
        for ph in art.get("phases", []):
            self.assertFalse({"form", "base", "build", "scale"} & set(ph), "a phase changes the body")

    def test_budget(self):
        """4.3's limits: at most 300 cubes (5.2 asks about 270), 130 beyond the fine distance, 100 bones, and the
        256x256 texture 5.2 asks for (wings and tail)."""
        lod = [c for c in self.model.cubes if not any(b.startswith("fine_") for b in self._chain(c.bone))]
        self.assertLessEqual(len(self.model.cubes), 300)
        self.assertLessEqual(len(lod), 130)
        self.assertLessEqual(len(self.model.bones), 100)
        self.assertEqual((self.model.tex_w, self.model.tex_h), (256, 256))

    def test_art_only_source(self):
        """The kit is pending: no moves anywhere, and the own file holds locomotion overrides (move.circle among
        them, a locomotion name now) and moments only, with no hit or cast marker."""
        self.assertEqual(self.hero.get("kit"), build.KIT_PENDING)
        self.assertEqual(self.hero.get("moves"), [])
        self.assertEqual(self.visual["moves"], [])
        self.assertEqual(build.art_only_problems(self.hero, self.own), [])
        self.assertIn("move.circle", build.locomotion_names())
        for clip in self.own.clips:
            self.assertEqual(clip.hits(), [], clip.name)
            if clip.name.startswith("move."):
                self.assertIn(clip.name, build.locomotion_names())
        # sabotage: without the circle among the locomotion names her circle reads as a kit clip
        saved = build.LOCOMOTION_BOSS
        try:
            build.LOCOMOTION_BOSS = ()
            self.assertTrue(any("move.circle" in p for p in build.art_only_problems(self.hero, self.own)))
        finally:
            build.LOCOMOTION_BOSS = saved
        hero = copy.deepcopy(self.hero)
        hero["animations"]["signatures"]["state.perched"] = dict(hero["animations"]["signatures"]["rest.perch"])
        with self.assertRaises(HerogenError):
            build.build_files(HID, hero)

    # ------------------------------------------------------------------ clips

    def test_own_clips_pass_every_rule_on_every_base(self):
        self.assertEqual(quality.check_built(self.own, self.lookup, list(self.model.bones), own_file=True), [])
        self.assertLessEqual(len(self.own.clips), 24)
        self.assertNotIn("escape", self.own.by_name)          # she fights to the death (5.7)
        for name in ("personal.1", "personal.2", "personal.3", "intro", "phase.2", "phase.3", "last_stand",
                     "rest.perch", "sec.loop", "move.takeoff", "move.fly", "move.fly_fast", "move.fly_hover",
                     "move.glide", "move.gust", "move.fly_land", "move.circle"):
            self.assertIn(name, self.own.by_name)

    def test_claws_clear_of_her_body(self):
        self.assertEqual(grips.animation_problems(self.hero, self.b, self.own, self.lookup, every=2), [])

    def test_flight_clips_open_the_wings_and_never_key_the_loops_bones(self):
        """The variant's flight clips open the wings through the part's own bones and never key wing_r or wing_l
        (the wings controller's loops own those), and the loops key only those two: she flies on the shared flap and
        glide loops, and only her ground loop is her own (below)."""
        for name in ("move.takeoff", "move.fly", "move.fly_fast", "move.fly_hover", "move.glide", "move.gust",
                     "move.fly_land", "move.circle"):
            clip = self.own.by_name[name]
            keyed = {b for b, _ in clip.tracks}
            self.assertFalse({"wing_r", "wing_l"} & keyed, name)
            self.assertTrue(set(WING_BONES) <= keyed, name)
        for loop in ("sec.wings_fold", "sec.wings_flap", "sec.wings_glide"):
            self.assertEqual({b for b, _ in library.core().by_name[loop].tracks}, {"wing_r", "wing_l"})
        for loop in ("sec.wings_flap", "sec.wings_glide"):
            self.assertNotIn(loop, self.own.by_name)              # she flies on the shared loops
        self.assertEqual({b for b, _ in self.own.by_name["sec.wings_fold"].tracks}, {"wing_r", "wing_l"})
        # the cruise is open: the hand turned the part's fold away
        self.assertGreater(self.own.by_name["move.fly"].tracks[("wing_hand_r", "rotation")].sample(0.0)[2], 120)
        # the landing ends folded (move.idle does not key the wing bones, so the settle goal is the rest pose)
        land = self.own.by_name["move.fly_land"]
        self.assertEqual(land.tracks[("wing_hand_r", "rotation")].sample(land.length), (0.0, 0.0, 0.0))

    def test_quirks(self):
        """personal.1 preens the left wing (half open and swung forward round her), personal.2 curls the tail round
        her feet (its tip ends up in front of her), personal.3 tips her head back to the sky."""
        preen = self.own.by_name["personal.1"]
        self.assertGreater(preen.tracks[("wing_hand_l", "rotation")].sample(1.2)[2], -100)
        self.assertLess(preen.tracks[("wing_hand_l", "rotation")].sample(1.2)[2], -40)
        self.assertNotIn(("wing_hand_r", "rotation"), preen.tracks)
        curl = self.own.by_name["personal.2"]
        sk = S.Skeleton.from_model(self.model)
        tip = [c for c in self.cubes("tails/scaled") if c.bone == "tail_tip_2" and c.name.endswith("_0")][0]
        end = np.array(tip.pivot)

        def tip_at(t):
            pose = {b: {"rotation": curl.tracks[(b, "rotation")].sample(t)}
                    for b, ch in curl.tracks if ch == "rotation"}
            r, tr = sk.world(pose)["tail_tip_2"]
            return r @ end + tr
        self.assertGreater(tip_at(0.0)[2], 10.0)              # behind her at rest
        self.assertLess(tip_at(2.0)[2], 4.0)                  # curled round to her feet
        self.assertLess(self.own.by_name["personal.3"].tracks[("neck", "rotation")].sample(2.0)[0], -25)

    # ------------------------------------------------------------------ parts

    def test_wings_fold_on_her_back_and_open_about_2_2_blocks(self):
        """wings/dragon: folded at rest against her back (the wrist above the shoulder, nothing below the hips or in
        front of the chest), and opened by its own bones (the variant's open pose) about 2.2 blocks across at her
        scale (BOSS_HEROES 5.2). Sabotage: the open pose without the hand's turn spans far less."""
        for b in WING_BONES:
            self.assertIn(b, self.model.bones)
            self.assertIn(b[:-2] + "_l", self.model.bones)
        self.assertEqual(self.model.bones["wing_arm_r"].parent, "wing_r")
        cubes = self.cubes("wings/dragon")
        e = self.model.env
        folded = self.world_points(cubes)
        self.assertGreater(folded[:, 1].min(), e["hip_y"])
        self.assertGreater(folded[:, 2].min(), e["chest_d"] / 2 - 0.2)
        self.assertGreater(folded[:, 1].max(), e["shoulder_y"] + 5)
        scale = self.visual["render_scale"] * self.visual["scale"] / 16.0
        opened = self.world_points(cubes, self.open_pose())
        span = (opened[:, 0].max() - opened[:, 0].min()) * scale
        self.assertGreater(span, 2.05)
        self.assertLess(span, 2.4)
        pose = self.open_pose()
        pose["wing_hand_r"] = [0, 0, 0]
        pose["wing_hand_l"] = [0, 0, 0]
        half = self.world_points(cubes, pose)
        self.assertLess((half[:, 0].max() - half[:, 0].min()) * scale, 1.6)

    def test_wings_under_the_loops(self):
        """On the ground the wings controller plays her own sec.wings_fold (her file comes first): it keys only the
        rig's wing bones and keeps both folded fans flat over her hair (behind the hair's back panel but for the
        short root on the rig bone, above the hips, each on its own side of the spine) through the breath and the
        ruffle; G10's flap and glide loops keep every beat of the open wing clear of the ground. Sabotage: the shared
        fold loop (wings.folded turns worn wings in toward the spine) crosses her two fans behind her head, which is
        why she has her own."""
        cubes = self.cubes("wings/dragon")
        right = [c for c in cubes if not c.name.endswith(":l")]
        fans = [c for c in cubes if "#root" not in c.name]           # the root bridges the hair to her back
        e = self.model.env
        hair_back = max(self.world_points([c])[:, 2].max() for c in self.cubes("hair/long_layered")
                        if c.name.endswith("#back_base"))
        own = self.own.by_name["sec.wings_fold"]
        self.assertEqual(sorted({b for b, _ in own.tracks}), ["wing_l", "wing_r"])

        def under(loop, t, open_=False):
            pose = dict(self.open_pose()) if open_ else {}
            for b in ("wing_r", "wing_l"):
                pose[b] = loop.tracks[(b, "rotation")].sample(t)
            return pose
        for k in range(17):
            t = own.length * k / 16.0
            pts = self.world_points(cubes, under(own, t))
            self.assertGreater(pts[:, 1].min(), e["hip_y"] - 1, t)
            self.assertGreater(self.world_points(fans, under(own, t))[:, 2].min(), hair_back, t)
            self.assertLess(self.world_points(right, under(own, t))[:, 0].max(), 0.0, t)
        for name in ("sec.wings_flap", "sec.wings_glide"):
            loop = library.core().by_name[name]
            for k in range(9):
                pts = self.world_points(cubes, under(loop, loop.length * k / 8.0, True))
                self.assertGreater(pts[:, 1].min(), 2.0, (name, k))
        shared = library.core().by_name["sec.wings_fold"]
        self.assertGreater(self.world_points(right, under(shared, 0.0))[:, 0].max(), 1.5)

    def test_glow_mask(self):
        """The membrane's veins and the horn tips glow only in the phase texture (from phase 2); the claws' inner
        plates and the pendant's crystal always; the eyes in every open frame (iris glow); the dorsal spines' shells
        are a phase-3 copy on p3_ bones."""
        base = self.b.glow[:, :, 3] > 0
        p2 = self.b.p2_glow[:, :, 3] > 0

        def texels(cubes):
            out = np.zeros(base.shape, dtype=bool)
            for c in cubes:
                for (u, v, w, h) in c.uv.values():
                    out[v:v + h, u:u + w] = True
            return out
        webs = texels([c for c in self.cubes("wings/dragon") if "#web" in c.name])
        tips = texels([c for c in self.cubes("horns/swept_dragon") if "#seg3" in c.name or "#seg4" in c.name])
        self.assertFalse((base & webs).any())
        self.assertGreater((p2 & webs).sum(), 40)
        self.assertLess((p2 & webs).sum(), webs.sum() * 0.5)          # thin veins, not whole webs
        self.assertFalse((base & tips).any())
        self.assertGreater((p2 & tips).sum(), 10)
        crystal = texels([c for c in self.cubes("accessories/spirecore_pendant") if c.name.endswith("#crystal")])
        self.assertTrue((base & crystal).any())
        shells = [c for c in self.cubes("markings/dorsal_spines") if c.bone.startswith(("p3_", "fine_p3_"))]
        self.assertEqual(len(shells), 7)
        self.assertTrue((base & texels(shells)).any())
        p3 = [p for p in self.visual["phases"] if p["phase"] == 3][0]
        self.assertTrue(p3["eyes_glow"])
        for c in shells:
            self.assertTrue(any(b.startswith("p3_") for b in self._chain(c.bone)), c.name)

    def test_horns_sweep_back_from_the_temples(self):
        """horns/swept_dragon: on horn_r and horn_l, rising from the temples over the head and sweeping back past
        it, the last two segments purple (accent)."""
        segs = self.cubes("horns/swept_dragon")
        self.assertEqual(len(segs), 10)
        hy = self.model.env["head_y"]
        pts = self.world_points([c for c in segs if c.bone.endswith("horn_r")])
        self.assertGreater(pts[:, 1].max(), hy + 12.5)               # above the hair's top
        self.assertGreater(pts[:, 2].max(), 7.5)                     # past the back of the head
        self.assertLess(pts[:, 0].max(), -2.5)                       # on the right side
        self.assertEqual({c.slot for c in segs if "#seg4" in c.name}, {"accent"})

    def test_tail_chain_and_crest(self):
        """tails/scaled: the rig's five tail bones and a two-bone tip chain on tail_5, its tip declared for the
        procedural lag; pale crest spines stand on top of every segment but the tip's point (gen.py `crest`, `last`
        on the rig chain); sabotage: the chain without a crest has none."""
        bones = [b for b in self.model.bones if b.startswith("tail_")]
        self.assertEqual(sorted(bones), ["tail_1", "tail_2", "tail_3", "tail_4", "tail_5", "tail_tip_1", "tail_tip_2"])
        self.assertEqual(self.model.bones["tail_tip_1"].parent, "tail_5")
        self.assertIn(["tail_tip_1", "tail_tip_2"], [s["chain"] for s in self.visual["secondary"]])
        crests = [c for c in self.cubes("tails/scaled") if c.name.endswith("_crest")]
        self.assertEqual(len(crests), 6)
        self.assertEqual({c.slot for c in crests}, {"trim"})
        for c in crests:
            seg = [s for s in self.cubes("tails/scaled") if s.name == c.name[:-len("_crest")] + "_0"][0]
            self.assertEqual(c.bone, "fine_" + seg.bone)
            self.assertGreater(_cube_points(c)[:, 1].mean(), _cube_points(seg)[:, 1].mean(), c.name)
        part = copy.deepcopy(load_part("tails/scaled"))
        part["id"] = "inline/tail"
        for ch in part["chains"]:
            ch.pop("crest", None)
        m = Model("t", "adult_f")
        apply_part(m, {"part": part, "color": "primary", "alt": "metal", "trim": "trim"}, 0)
        self.assertFalse([c for c in m.cubes if c.name.endswith("_crest")])

    def test_skirt_split_to_the_knee(self):
        """outfit_bottom/long_skirt: the panels reach the ankles but the front one, which stops at the knee (the
        split), with the purple under-panels behind it."""
        e = self.model.env
        panels = [c for c in self.cubes("outfit_bottom/long_skirt") if "#panel" in c.name]
        lows = {c.name: self.world_points([c])[:, 1].min() for c in panels}
        front = [n for n in lows if n.endswith("#panel7")]
        self.assertEqual(len(front), 1)
        self.assertAlmostEqual(lows[front[0]], e["knee_y"], delta=1.5)
        for n, low in lows.items():
            if n not in front:
                self.assertLess(low, e["ankle_y"] + 2.5, n)
        under = [c for c in self.cubes("outfit_bottom/long_skirt") if "#under" in c.name]
        self.assertEqual(len(under), 3)
        self.assertEqual({c.slot for c in under}, {"secondary"})

    def test_pendant_turns_on_its_artifact_bone(self):
        """accessories/spirecore_pendant is Spirecore's worn part: every cube on artifact_spirecore or below it,
        listed in visual.json bones.artifacts (R3 hides them unless she wears it); sec.loop turns the cage once
        round and keys nothing else."""
        self.assertEqual(sorted(self.visual["bones"]["artifacts"]), ["spirecore"])
        bones = set(self.visual["bones"]["artifacts"]["spirecore"])
        for c in self.cubes("accessories/spirecore_pendant"):
            self.assertIn("artifact_spirecore", self._chain(c.bone), c.name)
            self.assertTrue(set(self._chain(c.bone)) & bones, c.name)
        loop = self.own.by_name["sec.loop"]
        self.assertEqual(sorted(loop.tracks), [("artifact_spirecore_cage", "rotation")])
        tr = loop.tracks[("artifact_spirecore_cage", "rotation")]
        self.assertAlmostEqual(abs(tr.sample(loop.length * 0.5 - 1e-3)[1]), 180, delta=4)

    def test_hair_under_layer(self):
        """hair/long_layered `under` 1 draws its inner ring as a long purple under-layer (hair_accent) with the same
        cubes; `under` 0, the default, builds the style exactly as before (no other hero changes)."""
        def build_hair(params):
            m = Model("t", "adult_f")
            apply_part(m, {"part": "hair/long_layered", "color": "hair", "params": params}, 0)
            return m
        plain, zero, under = build_hair({}), build_hair({"under": 0}), build_hair({"under": 1})
        key = lambda m: [(c.name, c.bone, c.origin, c.size, c.rotation, c.slot) for c in m.cubes]
        self.assertEqual(key(plain), key(zero))
        self.assertEqual(len(plain.cubes), len(under.cubes))
        inner = [c for c in under.cubes if "#inner" in c.name]
        self.assertEqual(len(inner), 10)
        self.assertEqual({c.slot for c in inner}, {"hair_accent"})
        self.assertTrue(all(c.slot == "hair" for c in plain.cubes if "#inner" in c.name))

    def test_face(self):
        """Cat eyes with slit pupils and glowing purple irises, both fangs, no blush, the cheek scales on both sides
        and the purple throat: the open mouth's inside glows (her angry face shouts with it); no mood blushes."""
        f = self.hero["art"]["face"]
        self.assertEqual((f["eyes"], f["pupil"], f["iris_glow"], f["fang"], f["blush"]), ("cat", "slit", True,
                                                                                         "both", False))
        self.assertTrue(f["throat_glow"])
        self.assertIn({"type": "face_paint", "style": "scales", "color": "primary"}, f["marks"])
        moods = self.visual["face"]["moods"]
        self.assertEqual(moods["angry"]["mouth"], 3)
        self.assertEqual(moods["happy"]["eyes"], 3)
        self.assertTrue(all(m["cheeks"] != 1 for m in moods.values()), "a blush")    # flustered without one too
        rig = self.model.rig
        import face as facemod
        u, v, w, h = facemod.frame_rect(rig, "mouth", 3)
        self.assertTrue((self.b.glow[v:v + h, u:u + w, 3] > 0).any())
        u0, v0, w0, h0 = facemod.frame_rect(rig, "mouth", 0)
        self.assertFalse((self.b.glow[v0:v0 + h0, u0:u0 + w0, 3] > 0).any())
        hu, hv = rig["uv_template"]["head_front"][:2]
        head = self.b.image[hv:hv + 20, hu:hu + 20]
        for col in (0, 19):
            self.assertGreater(len({tuple(head[r, col, :3]) for r in range(17, 20)}), 1)   # scales on the skin

    def test_stripes_gap_draws_thin_lines(self):
        """The stripes pattern's `gap` (the membrane's veins): one texel line every size + gap texels; without it
        the stripes are as wide as their gaps, as before."""
        thin = {"type": "stripes", "dir": "v", "size": 1, "gap": 4, "slots": ["accent"]}
        wide = {"type": "stripes", "dir": "v", "size": 1, "slots": ["accent"]}
        hits_thin = [paint.Painter.pattern_at(thin, "north", (x, 0, 0), 1)[0] for x in range(10)]
        hits_wide = [paint.Painter.pattern_at(wide, "north", (x, 0, 0), 1)[0] for x in range(10)]
        self.assertEqual(sum(1 for s in hits_thin if s), 2)
        self.assertEqual(sum(1 for s in hits_wide if s), 5)

    def test_crest_follows_its_segment(self):
        """gen.py `crest`: a box on the side of each segment that faces up most, on the fine_ copy of the segment's
        bone with the segment's rotation and pivot, the last segment left bare unless asked."""
        part = {"id": "inline/t", "category": "tails", "chains": [{
            "name": "t", "bones": ["tail_1", "tail_2"], "parent": "body",
            "points": [[0, "hip_y", 2], [0, "hip_y - 2", 6], [0, "hip_y - 3", 10]],
            "sections": [[2, 2], [1.6, 1.6]], "crest": {"size": [0.5, 1.0, 0.5]}}],
            "slots": {"main": "primary", "trim": "trim"}}
        m = Model("t", "adult_f")
        apply_part(m, {"part": part}, 0)
        crests = [c for c in m.cubes if c.name.endswith("_crest")]
        self.assertEqual([c.bone for c in crests], ["fine_tail_1"])
        seg = [c for c in m.cubes if c.bone == "tail_1" and not c.name.endswith("_crest")][0]
        self.assertEqual((crests[0].rotation, crests[0].pivot), (seg.rotation, seg.pivot))
        self.assertGreater(_cube_points(crests[0])[:, 1].min(), _cube_points(seg)[:, 1].mean())

    # ------------------------------------------------------------------ previews

    def test_preview_rules(self):
        """A winged hero's previews play the wings controller's loop by the clip's mode; a wingless pose is left
        alone; her locomotion set has the flight with the flap loop, the glide and the circle with the glide loop,
        and her perch; the Wither's set is what it was (no circle, no wings)."""
        from render import hero_previews as hp
        from render.scene import HeroAssets
        self.assertEqual(hp.wing_loop_of("move.fly"), "sec.wings_flap")
        self.assertEqual(hp.wing_loop_of("move.circle"), "sec.wings_glide")
        self.assertEqual(hp.wing_loop_of("personal.1"), "sec.wings_fold")
        a = HeroAssets.build(self.hero)
        pose = hp.wings(a, {}, 0.0, "sec.wings_fold")
        self.assertEqual(set(pose), {"wing_r", "wing_l"})
        labels = [s.label for s in hp.loco_segments(a)]
        self.assertTrue(any("circle" in x for x in labels))
        self.assertTrue(any("perch" in x for x in labels))
        fly = [s for s in hp.loco_segments(a) if s.label.startswith("takeoff")][0]
        self.assertEqual(fly.wings["move.fly"], "sec.wings_flap")
        w = HeroAssets.build("the_wither")
        self.assertEqual(hp.wings(w, {"neck": {"rotation": (1, 2, 3)}}, 0.3), {"neck": {"rotation": (1, 2, 3)}})
        self.assertEqual([s.label for s in hp.loco_segments(w)],
                         ["walk > run", "hover > drift (hover)",
                          "takeoff (the tell) > fly > fly_fast > fly_hover > fly_land", "glide"])
        self.assertIn(HID, hp.lineup_ids())


if __name__ == "__main__":
    unittest.main()
