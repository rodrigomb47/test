"""B5: the Wither's look (BOSS_HEROES.md 4.2, 4.6, 4.12, 4.13), before her kit exists as data.

Her one body; the art-only source (`"kit": "pending"`: no moves, no fake kit clips, nothing compared with a
definition until one exists); the floating rule (no foot of hers touches the ground in any clip but the rebirth's
sink); her own clips' rules on every base and the sceptre clear of her body; the parts of 4.2 and the generator
pieces they needed: the skull familiars (their own bones, the bob of sec.loop on their position only, hidden in phase
3), the crown's gem and the familiars' eyes in the glow mask, the corset's ribcage, the ash tips of `hair/hime_long`
(`ash`), the soul fire on them from phase 3, the hollow star brooch on its artifact bone (`visual.json`
`bones.artifacts`), the wither rose hidden in her hand at rest, the iris glow and the socket shadows of her face; a
hero's recipe alias (`copy`); and the preview set's rules for a flyer who hovers."""
import copy
import json
import unittest

import numpy as np

import build
import grips
from anim import library
from anim import quality
from anim import skeleton as S
from anim.clip import TICK
from parts import apply_part
from rig import Model
from util import HerogenError

HID = "the_wither"
FAMILIARS = ("familiar_r", "familiar_l")


class WitherTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.hero = build.load_hero(HID)
        cls.files, cls.b = build.build_files(HID)
        cls.own = cls.b.animations
        cls.visual = json.loads(cls.files[build.out_paths(HID)["visual"]])
        cls.model = cls.b.model

    def lookup(self, name):
        for b in (self.own, library.style("focus"), library.core()):
            if name in b.by_name:
                return b.by_name[name]
        return None

    def cubes(self, part):
        return [c for c in self.model.cubes if c.part == part]

    # ------------------------------------------------------------------ body and source

    def test_one_body_for_good(self):
        """An adult woman, slim (the boss's gaunt silhouette), drawn at 104% (BOSS_HEROES 4.2; HEROES_V2 3.6): no
        phase changes the body."""
        art = self.hero["art"]
        self.assertEqual((art["form"], art["base"], art["build"], art["scale"]), ("adult", "female", "slim", 104))
        self.assertEqual((self.visual["form"], self.visual["base"], self.visual["build"]), ("adult", "adult_f", "slim"))
        for ph in art.get("phases", []):
            self.assertFalse({"form", "base", "build", "scale"} & set(ph), "a phase changes the body")

    def test_budget(self):
        """4.3's limits: at most 300 cubes, 130 beyond the fine distance (the ribs among them: the ribcage is how
        she reads from afar), 100 bones; the texture fits 256x128."""
        lod = [c for c in self.model.cubes if not any(b.startswith("fine_") for b in self._chain(c.bone))]
        self.assertLessEqual(len(self.model.cubes), 300)
        self.assertLessEqual(len(lod), 130)
        self.assertTrue(any("#rib" in c.name for c in lod))
        self.assertLessEqual(len(self.model.bones), 100)
        self.assertEqual((self.model.tex_w, self.model.tex_h), (256, 128))

    def _chain(self, bone):
        out = []
        while bone:
            out.append(bone)
            bone = self.model.bones[bone].parent
        return out

    def test_art_only_source(self):
        """The kit is pending: no moves in the source or visual.json, no definition to compare with yet, and the
        own file holds locomotion overrides and moments only, with no hit or cast marker."""
        self.assertEqual(self.hero.get("kit"), build.KIT_PENDING)
        self.assertEqual(self.hero.get("moves"), [])
        self.assertEqual(self.visual["moves"], [])
        self.assertEqual(build.art_only_problems(self.hero, self.own), [])
        for clip in self.own.clips:
            self.assertEqual(clip.hits(), [], clip.name)
            if clip.name.startswith("move."):
                self.assertIn(clip.name, build.locomotion_names())

    def test_art_only_refuses_a_kit(self):
        """Sabotage: a move in the source, a move clip that is not locomotion, a hit marker or an ultimate clip
        each break the art-only build."""
        hero = copy.deepcopy(self.hero)
        hero["moves"] = [{"id": "skull_volley"}]
        self.assertTrue(build.art_only_problems(hero, self.own))
        hero = copy.deepcopy(self.hero)
        sig = hero["animations"]["signatures"]
        sig["move.skull_volley"] = dict(sig["phase.2"])
        with self.assertRaises(HerogenError):
            build.build_files(HID, hero)
        hero = copy.deepcopy(self.hero)
        hero["animations"]["signatures"]["phase.2"]["keys"][1]["marker"] = "hit"
        with self.assertRaises(HerogenError):
            build.build_files(HID, hero)
        hero = copy.deepcopy(self.hero)
        hero["animations"]["signatures"]["ultimate"] = dict(hero["animations"]["signatures"]["phase.2"])
        with self.assertRaises(HerogenError):
            build.build_files(HID, hero)

    # ------------------------------------------------------------------ clips

    def test_own_clips_pass_every_rule_on_every_base(self):
        self.assertEqual(quality.check_built(self.own, self.lookup, list(self.model.bones), own_file=True), [])
        self.assertLessEqual(len(self.own.clips), 24)
        self.assertNotIn("escape", self.own.by_name)          # she fights to the death (4.7)

    def test_sceptre_clear_of_her_body(self):
        self.assertEqual(grips.animation_problems(self.hero, self.b, self.own, self.lookup, every=2), [])

    def _lowest_sole(self, name, sk):
        clip = self.own.by_name[name]
        base = None
        if clip.meta.get("kind") == "action":
            base = self.lookup(clip.meta.get("over", clip.meta["settle_to"]))
        pose_at = quality._pose_fn(clip, base, sk.d)
        n = int(round(clip.length / TICK))
        return min(sk.lowest_sole(pose_at(i * TICK)) for i in range(n + 1))

    def test_she_floats(self):
        """BOSS_HEROES 4.2: every idle and move a hand's breadth up, her feet never touch the ground: in every own
        clip (actions over the clip they play on) the lowest sole stays at least 2 px above the ground on her body,
        but in the rebirth, where she sinks into the soul sand."""
        sk = S.for_base("adult_f", "slim")
        for clip in self.own.clips:
            if clip.name in ("sec.loop", "rebirth"):
                continue
            self.assertGreaterEqual(self._lowest_sole(clip.name, sk), 2.0, clip.name)
        self.assertLess(self._lowest_sole("rebirth", sk), 0.5)
        # sabotage: the idle without its lift stands on the ground
        hero = copy.deepcopy(self.hero)
        del hero["animations"]["poses"]["wither.float"]["position"]
        lib = library.hero(hero)
        clip = lib.by_name["move.idle"]
        self.assertLess(min(sk.lowest_sole(clip.sample(i * TICK)) for i in range(int(clip.length / TICK))), 1.0)

    def test_hover_is_her_idle(self):
        """`copy` in a hero's own file (B5): move.hover is move.idle under its own name, move.drift move.walk."""
        for alias, src in (("move.hover", "move.idle"), ("move.drift", "move.walk")):
            a, s = self.own.by_name[alias], self.own.by_name[src]
            self.assertEqual(a.length, s.length)
            self.assertEqual(sorted(a.tracks), sorted(s.tracks))
            for bc in s.tracks:
                self.assertEqual(a.tracks[bc].sample(0.7), s.tracks[bc].sample(0.7))

    def test_familiars_bob_on_the_secondary_controller(self):
        """sec.loop keys only the familiars' position (the controller after the action one wins on what it keys),
        and no other clip of hers keys their position: every clip may turn them."""
        loop = self.own.by_name["sec.loop"]
        self.assertEqual(sorted(loop.tracks), [("familiar_l", "position"), ("familiar_r", "position")])
        for clip in self.own.clips:
            if clip.name != "sec.loop":
                for b in FAMILIARS:
                    self.assertNotIn((b, "position"), clip.tracks, clip.name)
        bicker = self.own.by_name["personal.1"]
        yaw = bicker.tracks[("familiar_r", "rotation")].sample(0.8)[1]
        self.assertLess(yaw, -45)                        # the right skull turned to face the left one
        self.assertGreater(bicker.tracks[("familiar_l", "rotation")].sample(0.8)[1], 45)

    def test_rose_comes_out_of_her_hand(self):
        """The wither rose lies inside her left hand at rest (nothing shows) and personal.2 slides it out."""
        hand = [c for c in self.model.cubes if c.bone == "hand_l" and c.part == "base"][0]
        lo, hi = np.array(hand.origin), np.array(hand.origin) + np.array(hand.size)
        for c in self.cubes("accessories/wither_rose"):
            self.assertTrue(np.all(np.array(c.origin) >= lo - 1e-6) and
                            np.all(np.array(c.origin) + np.array(c.size) <= hi + 1e-6), c.name)
        rose = self.own.by_name["personal.2"].tracks[("rose_l", "position")]
        self.assertLess(rose.sample(1.5)[1], -2.0)
        self.assertEqual(rose.sample(0.0), (0.0, 0.0, 0.0))

    # ------------------------------------------------------------------ parts

    def test_skull_familiars(self):
        """Her side heads: 6 x 6 x 6 px wither skulls on their own bones under the chest, at eye height about 12 px
        either side of the head's centre, eyes glowing, hidden in phase 3."""
        for b in FAMILIARS:
            self.assertEqual(self.model.bones[b].parent, "chest")
        cranium = [c for c in self.model.cubes if c.name.split("#")[-1] in ("cranium", "cranium:l")]
        jaws = [c for c in self.model.cubes if c.name.split("#")[-1] in ("jaw", "jaw:l")]
        self.assertEqual((len(cranium), len(jaws)), (2, 2))
        for c, j in zip(sorted(cranium, key=lambda c: c.origin[0]), sorted(jaws, key=lambda c: c.origin[0])):
            self.assertEqual((c.size[0], c.size[2]), (6.0, 6.0))
            self.assertAlmostEqual(c.origin[1] + c.size[1] - j.origin[1], 6.15, delta=0.01)   # 6 px tall, jaw in
            self.assertTrue(self.model.bones[j.bone].parent in FAMILIARS)
        xs = sorted(c.origin[0] + 3 for c in cranium)
        self.assertEqual(xs, [-12.0, 12.0])
        p3 = [p for p in self.visual["phases"] if p["phase"] == 3][0]
        self.assertEqual(sorted(p3["hide"]), ["familiar_l", "familiar_r"])
        eyes = [c for c in self.model.cubes if c.part == "accessories/skull_familiars" and c.material == "glow"]
        self.assertEqual(len(eyes), 4)

    def test_glow_mask(self):
        """The crown's gem, the familiars' eyes and the sceptre's heart are in the glow mask; the stays, the spine and
        the hem light up only in the phase texture (the wither armour)."""
        glow = self.b.glow[:, :, 3]
        for part in ("headwear/bone_crown", "weapons/wither_rose_sceptre", "accessories/skull_familiars"):
            cs = [c for c in self.model.cubes if c.part == part and c.material == "glow"]
            self.assertTrue(cs, part)
            for c in cs:
                self.assertTrue(any(glow[v:v + h, u:u + w].any() for (u, v, w, h) in self._rects(c)), c.name)
        p2 = self.b.p2_glow[:, :, 3]
        for part, cube in (("outfit_top/ribbed_corset", "sternum"), ("markings/spine_ridge", "v1")):
            c = [c for c in self.cubes(part) if c.name.split("#")[-1] == cube][0]
            rects = self._rects(c)
            self.assertTrue(any(p2[v:v + h, u:u + w].any() for (u, v, w, h) in rects), part)
            self.assertFalse(any(glow[v:v + h, u:u + w].any() for (u, v, w, h) in rects), part)

    def _rects(self, cube):
        out = []
        for f in (cube.uv or {}).values():
            if f is None:
                continue
            u, v, w, h = f
            out.append((int(u), int(v), max(1, int(w)), max(1, int(h))))
        return out

    def test_ash_tips_keep_the_hime_cut(self):
        """`hair/hime_long` `ash` 1 gives the side locks, the back layer and the tail tips in the alt colour, cut
        jagged; `ash` 0 builds the cut exactly as before (Hinae's committed files are rebuilt byte for byte by
        `build --check`)."""
        def hair(ash):
            m = Model("x", "adult_f", "standard")
            apply_part(m, {"part": "hair/hime_long", "color": "hair", "trim": "secondary", "alt": "hair_accent",
                           "params": {"ash": ash}}, 0)
            return [c for c in m.cubes if c.part == "hair/hime_long"]
        plain, ash = hair(0), hair(1)
        self.assertEqual(len(plain), len(ash))
        self.assertEqual(sorted(c.name.split("#")[1] for c in plain), sorted(c.name.split("#")[1] for c in ash))
        tipped = [c for c in ash if c.tip]
        self.assertGreaterEqual(len(tipped), 9 + 8 + 6)
        self.assertFalse([c for c in plain if c.tip and c.name.split("#")[1].startswith(("sidelock", "back", "tail"))])

    def test_soul_fire_from_phase_3(self):
        """Her ash tips burn in phase 3: glowing tongues on p3_ children of the hair's bones (fine), outside the tail's
        flared ends (they were inside it when centred on the tail's root radius)."""
        fire = self.cubes("markings/soul_fire_tips")
        self.assertTrue(fire)
        for c in fire:
            self.assertIn("p3_", c.bone)
        self.assertEqual(self.visual["bones"]["phase"].get("p3_tail_hair_2"), 3)
        self.assertEqual(self.visual["bones"]["phase"].get("p3_hair_l"), 3)
        # the left lock's fire hangs from the left lock (the mirror of a nested phase bone mirrors its whole chain)
        self.assertEqual(self.model.bones["fine_p3_hair_l"].parent, "p3_hair_l")
        self.assertEqual(self.model.bones["p3_hair_l"].parent, "hair_l")
        tail = [c for c in self.cubes("hair/hime_long") if "#tail" in c.name]
        fire = [c for c in fire if "#tail_fire" in c.name]
        r_tail = max(np.hypot(c.pivot[0], c.pivot[2] - 6.7) for c in tail)
        r_fire = min(np.hypot(c.pivot[0], c.pivot[2] - 6.7) for c in fire)
        self.assertGreater(r_fire, r_tail + 1.0)

    def test_brooch_is_drawn_only_while_worn(self):
        """Hollow Star's worn part (ARTIFACTS 12.7, 16.4): every cube on its artifact bone, listed in visual.json
        `bones.artifacts` for HeroModel to hide unless she wears it; a cube elsewhere is refused."""
        self.assertEqual(self.visual["bones"]["artifacts"], {"hollow_star": ["artifact_hollow_star"]})
        for c in self.cubes("accessories/hollow_star_brooch"):
            self.assertIn("artifact_hollow_star", self._chain(c.bone))
        from parts import load_part
        bad = copy.deepcopy(load_part("accessories/hollow_star_brooch"))
        bad["cubes"][0]["bone"] = "chest"
        m = Model("x", "adult_f", "slim")
        with self.assertRaises(HerogenError):
            apply_part(m, {"part": dict(bad, id="inline/brooch"), "color": "metal", "alt": "accent"}, 0)

    def test_face(self):
        """Sharp, pupil-less glowing irises (in the glow mask in every open frame), the socket shadows under the
        eyes, and the overrides: happy a thin half-lidded smile, angry blazing, never a blush."""
        face = self.hero["art"]["face"]
        self.assertEqual((face["eyes"], face["pupil"], face["iris_glow"]), ("sharp", "none", True))
        moods = self.visual["face"]["moods"]
        self.assertEqual(moods["happy"]["eyes"], 1)
        self.assertEqual(moods["angry"]["eyes"], 10)
        self.assertTrue(all(m["cheeks"] != 1 for m in moods.values()), "a blush")
        u, v, w, h = 64, 0, 20, 10            # eye frame 0 (open)
        self.assertTrue(self.b.glow[v:v + h, u:u + w, 3].any())

    def test_rebirth_look(self):
        """The rebirth (BOSS_HEROES 4.2): `art.rebirth` recolours the gown (metal) and the soul-fire accents in her
        phase-2 look and every recoloured texel blazes, written to the_wither_rebirth.png and its glow mask
        (visual.json `texture.rebirth`); the corset's charcoal stays. A hero without it gets no file and no key
        (sabotage: without `art.rebirth` the files and the flag are gone, and check_visual reports a missing
        file)."""
        import assets
        paths = build.out_paths(HID)
        self.assertTrue(self.visual["texture"]["rebirth"])
        self.assertIn(paths["rebirth"], self.files)
        self.assertIn(paths["rebirth_glowmask"], self.files)
        rb, rbg, p2 = self.b.rebirth, self.b.rebirth_glow[:, :, 3], self.b.p2

        def texels(cubes):
            out = np.zeros(rbg.shape, bool)
            for c in cubes:
                for (u, v, w, h) in self._rects(c):
                    out[v:v + h, u:u + w] = True
            return out & (rb[:, :, 3] > 0)
        gown = texels([c for c in self.cubes("robes/smoke_gown") if c.slot == "metal"])
        bodice = texels([c for c in self.cubes("outfit_top/ribbed_corset") if c.slot == "primary"])
        self.assertGreater(gown.sum(), 200)
        changed = np.any(rb != p2, axis=2)
        self.assertGreater(changed[gown].mean(), 0.85)          # the panels (their ash tips stay)
        self.assertGreater((rbg > 0)[gown].mean(), 0.85)
        self.assertEqual(changed[bodice].sum(), 0)
        self.assertTrue(np.all(rb[changed][:, 2] >= rb[changed][:, 0]), "blue-white, never warm")
        files = dict(self.files)
        del files[paths["rebirth"]]
        self.assertTrue(any("rebirth" in m for m in assets.check_visual(HID, files, self.visual, None)))
        hero = copy.deepcopy(self.hero)
        del hero["art"]["rebirth"]
        files, b = build.build_files(HID, hero)
        self.assertNotIn(paths["rebirth"], files)
        self.assertNotIn("rebirth", json.loads(files[paths["visual"]])["texture"])
        for bad in ({"palette": {"gown": "#FFFFFF"}}, {"palette": {}}, {"palette": {"metal": "#FFFFFF"}, "x": 1}):
            hero = copy.deepcopy(self.hero)
            hero["art"]["rebirth"] = bad
            with self.assertRaises(HerogenError):
                build.build(hero)

    def test_bracer_spines_stand_apart_on_every_form(self):
        """`armour/bone_bracers`' three spines stand at least a spine's width apart along the forearm on every base
        (on a chibi's short forearm they were 0.25 px apart and z-fought; the lint found it)."""
        from rig import BASE_IDS

        def spacing(base_id):
            m = Model("x", base_id, "standard")
            apply_part(m, {"part": "armour/bone_bracers", "color": "secondary", "trim": "trim"}, 0)
            ys = sorted(c.origin[1] for c in m.cubes if "#spine" in c.name and not c.name.endswith(":l"))
            width = min(c.size[0] for c in m.cubes if "#spine" in c.name)
            return min(b - a for a, b in zip(ys, ys[1:])), width
        for base_id in BASE_IDS:
            gap, width = spacing(base_id)
            self.assertGreaterEqual(gap, width, base_id)

    def test_lint_wears_a_required_hairstyle(self):
        """A part worn over a hairstyle (the soul fire over `hair/hime_long`) is linted and drawn on the sheets on the
        mannequin wearing that hair instead of its own short crop (two hairs on one head were refused); a part worn
        over a tail (Natsuho's puffed fur) still wears the mannequin's hair and the tail."""
        import lint
        for sheet in (False, True):
            parts = [e["part"] for e in lint.lint_hero("markings/soul_fire_tips", "chibi_f", sheet=sheet)["art"]["parts"]]
            self.assertEqual(parts, ["hair/hime_long", "markings/soul_fire_tips"])
        parts = [e["part"] for e in lint.lint_hero("tails/cat_puff", "adult_f")["art"]["parts"]]
        self.assertEqual(parts, ["hair/short_crop", "tails/cat_long", "tails/cat_puff"])
        build.build_files("lint_x", dict(lint.lint_hero("markings/soul_fire_tips", "teen_m"), id="lint_x"))

    # ------------------------------------------------------------------ previews

    def test_preview_rules(self):
        from render import hero_previews
        a = hero_previews.assets(HID)
        self.assertEqual(hero_previews.phase_of_clip(a, "rebirth"), 2)
        self.assertEqual(hero_previews.phase_of_clip(a, "last_stand"), 3)
        self.assertEqual(hero_previews.phase_of_clip(a, "phase.3"), 3)
        labels = [s.label for s in hero_previews.loco_segments(a)]
        self.assertTrue(any(lb.startswith("takeoff") for lb in labels))
        self.assertTrue(any(lb.startswith("hover") for lb in labels))
        self.assertFalse(any("jump" in lb or "climb" in lb for lb in labels))
        shots = hero_previews.gif_shots(a)["moments.gif"]
        self.assertEqual([s.actions[0] for s in shots], ["intro", "phase.2", "rebirth", "phase.3", "last_stand"])
        self.assertEqual([s.look for s in shots], [None, None, "rebirth", None, None])
        self.assertIsNone(hero_previews.look_of_clip(a, "phase.2"))
        self.assertIn(HID, hero_previews.lineup_ids())


if __name__ == "__main__":
    unittest.main()
