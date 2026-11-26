"""C3: Ambrose Nettlewick, the cauldron warlock, the sixth slice hero with his own source, art and signature
animations.

His one body (the owner's rule), his moves' frames equal to his kit's (Potion Barrage's three vials on the barrage's own
8-tick spacing), his sound markers, his clips' rules on every base, the staff clear of his body and his arms, staff and
props clear of his wide hat and his crow on every tick; the light ranged moves and the ultimate that may play over the
broom keying the upper body only; his phase looks (the bristles from phase 2, the lifted brim and the glowing eyes from
phase 3) and the moments that show them; his crow (its glances on the secondary controller, flapping beside him on the
broom) and the props kept in his hands; and the generator pieces he needed: the crow's wing bones, the lifted brim and
the field kit parts, the rider's locomotion film and G10's broom GIF drawn from his own source."""
import copy
import json
import os
import unittest

import numpy as np

import helpers  # noqa: F401
import assets
import build
import grips
import parts
from anim import library
from anim import quality
from anim.clip import form_var
from anim.skeleton import Skeleton
from util import ROOT

HID = "ambrose_nettlewick"
FIXTURE = os.path.join(ROOT, "src", "test", "resources", "heroes", "slice", HID + ".json")
SIGS = {"move.potion_barrage", "move.cauldron", "move.hex_mark", "move.fume_step", "move.broom_hops",
        "move.great_brew", "ultimate", "phase.2", "phase.3", "intro", "victory", "taunt", "last_stand", "escape",
        "personal.1", "personal.2", "personal.3"}
# his own broom set (G10's broom variant with the crow flying beside him), the variant's cast from the broom and the
# crow's glances on the secondary controller
BROOM = {"move.takeoff", "move.ride", "move.fly_land"}
OWN = SIGS | BROOM | {"move.ride_cast", "sec.loop"}
# while he rides, the staff in his hand and on his back is hidden (the weapon is the carrier on the mount bone)
RIDING = BROOM | {"move.ride_cast"}
UPPER = {"waist", "chest", "neck"}


def _ticks(s):
    return int(round(float(s) * 20))


class AmbroseTest(unittest.TestCase):
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
        for b in (self.own, library.style("staff"), library.core()):
            if name in b.by_name:
                return b.by_name[name]
        return None

    def assets(self):
        if not hasattr(AmbroseTest, "_assets"):
            from render import scene
            AmbroseTest._assets = scene.HeroAssets.build(HID)
        return AmbroseTest._assets

    def keyed(self, name):
        return {bone for bone, _ in self.own.by_name[name].tracks}

    # -------------------------------------------------------------- body, kit, frames, rules

    def test_one_body_for_good(self):
        """An adult man of ordinary size, slim build, drawn at 100% (SLICE_PLAN 3.8; the owner's one-body rule of
        HEROES_V2 3.6): no phase changes the body. No tail."""
        art = self.hero["art"]
        self.assertEqual((art["form"], art["base"], art["build"], art["scale"]), ("adult", "male", "slim", 100))
        self.assertEqual((self.visual["form"], self.visual["base"], self.visual["build"]), ("adult", "adult_m", "slim"))
        for ph in art.get("phases", []):
            self.assertFalse({"form", "base", "build"} & set(ph), "a phase changes the body")
        self.assertNotIn("tail_1", self.model.bones)

    def test_kit_is_the_fixtures(self):
        for k, v in self.kit.items():
            if k != "_comment":
                self.assertEqual(self.hero[k], v, k)

    def test_budgets(self):
        """The staff-broom, the hat with its phase-3 brim, the crow and the field kit inside HEROES_V2 4.3's budgets (100
        bones, 300 cubes, 130 cubes outside fine_ bones) and the own file's 24 clips."""
        problems, g = assets.check_geometry(HID, self.files, self.visual)
        self.assertEqual(problems, [])
        self.assertLessEqual(len(g.order), 100)
        self.assertLessEqual(len(g.cubes), 300)
        import validate
        self.assertLessEqual(validate.lod_cubes(g), 130)
        self.assertLessEqual(len(self.own.clips), 24)

    def test_move_frames_equal_the_kit(self):
        """Every move's clip and the ultimate's have the kit's wind-up and recovery to the tick and the active part
        within one (from the action's start, the 2-tick blend included): the release on a `hit`, the active part's
        last tick on a `cast`; Potion Barrage has a `hit` per vial on the barrage's own schedule (ticks 12, 20, 28:
        0.4 s apart, so its active part counts 17 ticks against the kit's 16, inside the one-tick rule)."""
        timing = self.own.timing_json()["animations"]
        items = [("move." + m["id"], m) for m in self.kit["moves"]] + [("ultimate", self.kit["ultimate"])]
        for name, m in items:
            want = {k: _ticks(v) for k, v in m["frames"].items()}
            got = timing[name]["frame_ticks"]
            hits = [e["tick"] for e in timing[name]["events"] if e["type"] == "hit"]
            casts = [e["tick"] for e in timing[name]["events"] if e["type"] == "cast"]
            if m["form"] == "barrage":
                gap = _ticks(m["interval"])
                self.assertEqual(hits, [want["windup"] + gap * i for i in range(m["count"])], name)
                self.assertEqual(got["windup"], want["windup"], name)
                self.assertEqual(got["recovery"], want["recovery"], name)
                self.assertLessEqual(abs(got["active"] - want["active"]), 1, name)
            else:
                self.assertEqual(got, want, name)
                self.assertEqual(hits, [want["windup"]], name)
                self.assertEqual(casts, [want["windup"] + want["active"] - 1], name)
        definition, _ = assets.definition_of(HID)
        files = dict(self.files, **library.core().files(), **library.style("staff").files())
        self.assertEqual(assets.check_frames(definition, assets.Chain.of(self.visual, files)), [])

    def test_own_clips_pass_every_rule_on_every_base(self):
        """Every own clip passes the quality rules on all six bases; he escapes (SLICE_PLAN 3.8: a researcher cannot
        be repeated), so he has an escape clip; no ultimate.loop (the engine never sends ultimate.end)."""
        self.assertEqual(quality.check_built(self.own, self.lookup, list(self.b.model.bones), own_file=True), [])
        names = {c.name for c in self.own.clips}
        self.assertEqual(names, OWN)
        self.assertNotIn("ultimate.loop", names)

    def test_staff_clear_of_his_body(self):
        """The staff in his right hand (with its phase-2 bristles) and the props of his field kit clear of his body on
        every tick of his own clips, the riding ones aside (there the hand's and the back's copies are hidden: the
        staff is the carrier on the mount bone)."""
        probs = grips.animation_problems(self.hero, self.b, self.own, self.lookup, every=1)
        self.assertEqual([p for p in probs if p.split(":")[0] not in RIDING], [])

    def test_clear_of_his_hat_and_crow(self):
        """His arms, the staff and the props never go more than 0.5 px into his hat (the cone, the band, the brim and
        phase 3's lifted brim, all at once: a 17.5 px brim the body check does not know) or into the crow on his
        shoulder, nor the staff or a prop into his head, on any tick of his own clips (each over its base)."""
        m = self.model
        sub = lambda roots: set(grips._subtree(m, [r for r in roots if r in m.bones]))
        staff = sub(["weapon_r"])
        props = sub(["vial_l", "notebook_l", "pencil_r"])
        arms = sub(["arm_r", "arm_l"]) - staff - props
        groups = {"arms": arms, "staff": staff, "props": props, "hat": sub(["hat"]), "crow": sub(["crow"]),
                  "head": {"head"}}
        cubes = {k: [c for c in m.cubes if c.bone in v] for k, v in groups.items()}
        self.assertTrue(all(cubes.values()))
        pairs = [("arms", "hat"), ("staff", "hat"), ("props", "hat"), ("arms", "crow"), ("staff", "crow"),
                 ("props", "crow"), ("staff", "head"), ("props", "head")]
        worst = {}
        for clip in self.own.clips:
            if clip.name in RIDING:
                continue
            for t, pose, _ in grips.clip_poses(clip, self.lookup, self.d, every=1):
                w = self.sk.world(pose)
                for g, o in pairs:
                    for c in cubes[g]:
                        r, tr = w[c.bone]
                        pts = grips._samples(c) @ r.T + tr
                        for b in cubes[o]:
                            rb, tb = w[b.bone]
                            local = (pts - tb) @ rb
                            if b.rotation is not None:
                                pv = np.array(b.pivot, dtype=float)
                                local = pv + (local - pv) @ grips.file_matrix(b.rotation)
                            dep = float(grips._depth_inside(local, b.box).max())
                            if dep > worst.get((clip.name, g, o), (0.0, 0.0))[0]:
                                worst[(clip.name, g, o)] = (dep, t)
        self.assertEqual({k: v for k, v in worst.items() if v[0] > 0.5}, {})

    def test_moves_that_play_over_the_broom_key_the_upper_body(self):
        """Potion Barrage and Hex Mark (light ranged moves he may cast from the broom), the ultimate (a rain he may
        wind up in the air) and the escape (over the run or the take-off) key no leg, hip or root bone, so the ride's
        seat stays under them; the barrage and the hex leave the right arm to the base too (the staff upright on the
        ground, the hand on the shaft on the broom) and throw with the left."""
        legs = {"root", "body", "leg_r", "leg_l", "shin_r", "shin_l", "foot_r", "foot_l"}
        for name in ("move.potion_barrage", "move.hex_mark", "ultimate", "escape"):
            self.assertFalse(self.keyed(name) & legs, name)
            self.assertTrue(self.keyed(name) & UPPER, name)
        for name in ("move.potion_barrage", "move.hex_mark"):
            self.assertFalse(self.keyed(name) & {"arm_r", "forearm_r", "hand_r"}, name)
            self.assertIn("arm_l", self.keyed(name))

    def test_sound_markers(self):
        """His client-played sounds are markers in the clips their slots name (SOUND.md 4.7, 5): the client `release`
        of the barrage (one per vial), Hex Mark, Fume Step and Broom Hops, the phases, the intro and the quirks; the
        server's slots (wind-ups, impacts, the brew's and the ultimate's releases) are never markers."""
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
        self.assertEqual(sounds["move.potion_barrage"].count(pre + "vial_throw"), 3)
        self.assertIn(pre + hm["phases"]["2"], sounds["phase.2"])
        self.assertIn(pre + hm["phases"]["3"], sounds["phase.3"])
        self.assertIn(pre + hm["intro"], sounds["intro"])
        for n, s in hm["quirks"].items():
            self.assertIn(pre + s, sounds[n], n)
        server = set()
        for m in hm["moves"].values():
            for k, v in m.items():
                if k in ("windup", "impact", "broken"):
                    server |= {pre + x for x in (v if isinstance(v, list) else [v])}
        for name, got in sounds.items():
            self.assertFalse(set(got) & server, name)

    def test_faces(self):
        """Every signature clip keys face_ctrl; his calm and focused faces smile and his angry face is a frown (an
        indignant scholar, never a snarl); the moves key the mouth and leave the eyes to the mood (phase 3's glow)."""
        for name in SIGS:
            self.assertIn("face_ctrl", self.keyed(name), name)
        moods = self.visual["face"]["moods"]
        self.assertEqual(moods["calm"]["mouth"], 1)
        self.assertEqual(moods["focused"]["mouth"], 1)
        self.assertEqual(moods["angry"]["mouth"], 5)
        for m in self.kit["moves"]:
            clip = self.own.by_name["move." + m["id"]]
            tr = clip.tracks.get(("face_ctrl", "position"))
            if tr is None:
                continue
            for k in tr.keys:
                self.assertEqual(round(k.v[0]), 0, m["id"])

    # -------------------------------------------------------------- phase looks

    def test_phase_looks(self):
        """Phase 2: the broom bristles on the staff (its phase cubes, from phase 2). Phase 3: the eyes glow and the hat's
        brim is turned up (headwear/witch_brim_up on p3_ bones; the phase look hides the hat's own brim)."""
        phases = self.visual["bones"]["phase"]
        self.assertEqual(phases.get("p2_weapon_r"), 2)
        self.assertEqual(phases.get("p3_brim_up"), 3)
        looks = {p["phase"]: p for p in self.hero["art"]["phases"]}
        self.assertTrue(looks[3].get("eyes_glow"))
        self.assertEqual(looks[3].get("hide"), ["brim"])
        a = self.assets()
        self.assertNotIn("brim", a.hidden(phase=2))
        self.assertIn("p3_brim_up", a.hidden(phase=2))
        self.assertIn("brim", a.hidden(phase=3))
        self.assertNotIn("p3_brim_up", a.hidden(phase=3))

    def test_the_lifted_brim(self):
        """The lifted brim is the hat's brim turned up at the front: its front edge at least 2 px higher than the hat's
        own, its back edge within 1.5 px of it, the same two slabs."""
        def edges(bone):
            w = self.sk.world({})
            ys = {}
            for c in self.model.cubes:
                if c.bone != bone:
                    continue
                r, t = w[c.bone]
                pts = grips._samples(c) @ r.T + t
                front, back = pts[pts[:, 2] < -7.5], pts[pts[:, 2] > 7.5]
                if len(front):
                    ys.setdefault("front", []).append(front[:, 1].mean())
                if len(back):
                    ys.setdefault("back", []).append(back[:, 1].mean())
            return {k: float(np.mean(v)) for k, v in ys.items()}
        hat, up = edges("brim"), edges("p3_brim_up")
        self.assertGreater(up["front"] - hat["front"], 2.0)
        self.assertLess(abs(up["back"] - hat["back"]), 1.5)
        self.assertEqual(len([c for c in self.model.cubes if c.bone == "p3_brim_up"]), 2)

    def test_phase_3_flicks_the_brim_up(self):
        """phase.3 starts with the lifted brim turned down over his eyes and ends with it up (the bone at rest), with an
        overshoot past it on the way."""
        tr = self.own.by_name["phase.3"].tracks[("p3_brim_up", "rotation")]
        clip = self.own.by_name["phase.3"]
        self.assertGreater(tr.sample(0.3)[0], 12)
        self.assertLess(min(tr.sample(t)[0] for t in np.arange(0.5, 0.9, 0.05)), -3)
        self.assertAlmostEqual(tr.sample(clip.length)[0], 0.0, places=3)

    # -------------------------------------------------------------- the crow and the props

    def test_the_crows_glances_are_sec_loop(self):
        """sec.loop keys only the crow's head (its glances), and no other clip keys that bone, so the glances play
        under every clip while the clips move the crow's body and wings."""
        self.assertEqual({b for b, _ in self.own.by_name["sec.loop"].tracks}, {"crow_head"})
        for c in self.own.clips:
            if c.name != "sec.loop":
                self.assertNotIn("crow_head", {b for b, _ in c.tracks}, c.name)

    def test_the_crow_flies_beside_him_on_the_broom(self):
        """On the broom the crow is off his shoulder (more than 4 px from its perch) with its wings beating; the
        take-off lifts it off the perch and the landing puts it back."""
        ride = self.own.by_name["move.ride"]
        pos = ride.tracks[("crow", "position")]
        self.assertGreater(min(np.linalg.norm(pos.sample(t)) for t in np.arange(0, 2.0, 0.1)), 4.0)
        wing = ride.tracks[("crow_wing_l", "rotation")]
        zs = [wing.sample(t)[2] for t in np.arange(0, 0.25, 0.025)]
        self.assertGreater(max(zs) - min(zs), 40)
        take = self.own.by_name["move.takeoff"].tracks[("crow", "position")]
        self.assertLess(np.linalg.norm(take.sample(0.0)), 0.01)
        land = self.own.by_name["move.fly_land"]
        self.assertLess(np.linalg.norm(land.tracks[("crow", "position")].sample(land.length)), 0.01)

    def test_the_broom_set_is_the_variants_body(self):
        """His take-off, ride and landing are G10's broom variant with the crow added: every channel the variant keys
        is the same on every tick."""
        hero = copy.deepcopy(self.hero)
        hero["animations"]["signatures"] = {}
        variant = library.hero(hero, None)
        for name in BROOM:
            mine, theirs = self.own.by_name[name], variant.by_name[name]
            for bc, tr in theirs.tracks.items():
                if bc[0].startswith("crow"):
                    continue
                for t in np.arange(0, mine.length + 1e-6, 0.05):
                    self.assertTrue(np.allclose(mine.tracks[bc].sample(t), tr.sample(t), atol=1e-3), (name, bc, t))

    def test_the_crow_has_wing_bones(self):
        """accessories/crow (C3): the folded wings hang from their own bones at their tops, where they were before."""
        for side in ("r", "l"):
            bone = self.model.bones["crow_wing_" + side]
            self.assertEqual(bone.parent, "crow")
            cube = [c for c in self.model.cubes if c.name == "accessories/crow#wing_" + side][0]
            self.assertEqual(cube.bone, "crow_wing_" + side)
            self.assertAlmostEqual(bone.pivot[1], cube.origin[1] + cube.size[1], places=3)
        env = self.model.env
        wing_r = [c for c in self.model.cubes if c.name == "accessories/crow#wing_r"][0]
        self.assertTrue(np.allclose(wing_r.origin, (env["shoulder_x"] + 0.3 - 1.65, env["chest_top"] + 1.6, -1.4)))

    def test_the_field_kit_hides_in_his_hands(self):
        """accessories/field_kit: at rest every cube of the vial, the notebook and the pencil lies inside the hand it
        is kept in (nothing shows), and the clips bring them out: the vial at each barrage throw (back in the next
        tick: thrown), the notebook and the pencil in the notebook quirk."""
        w = self.sk.world({})
        hands = {"vial_l": "hand_l", "notebook_l": "hand_l", "pencil_r": "hand_r"}
        for bone, hand in hands.items():
            hc = [c for c in self.model.cubes if c.bone == hand][0]
            rh, th = w[hand]
            for c in self.model.cubes:
                if c.bone != bone:
                    continue
                r, t = w[c.bone]
                pts = grips._samples(c) @ r.T + t
                local = (pts - th) @ rh
                self.assertTrue(np.all(local >= np.array(hc.origin) - 1e-6), c.name)
                self.assertTrue(np.all(local <= np.array(hc.origin) + np.array(hc.size) + 1e-6), c.name)
        bar = self.own.by_name["move.potion_barrage"].tracks[("vial_l", "position")]
        for hit in (0.5, 0.9, 1.3):
            self.assertLess(bar.sample(hit)[1], -1.5)
            self.assertGreater(bar.sample(hit + 0.05)[1], -0.05)
        note = self.own.by_name["personal.1"]
        self.assertLess(note.tracks[("notebook_l", "position")].sample(1.0)[1], -1.5)
        self.assertLess(note.tracks[("pencil_r", "position")].sample(1.0)[1], -1.0)

    # -------------------------------------------------------------- previews

    def test_his_locomotion_film_rides_the_broom(self):
        """A flyer on a mount carrier flies in the ride mode: his locomotion film shows the take-off, the ride with a
        cast from it and the landing, and the glide, with the carrier shown; no move.fly* (he never plays them). A
        flyer without a mount (the Wither) keeps the flight set."""
        from render import hero_previews as hp

        class A:
            def __init__(self, hero, visual, bones=()):
                self.hero, self.visual = hero, visual
                self.geo = type("G", (), {"bones": {b: None for b in bones}})()
                self.chain = None

        segs = hp.loco_segments(A(self.hero, self.visual))
        clips = [n for s in segs for n, _ in s.items]
        self.assertIn("move.ride", clips)
        self.assertIn("move.glide", clips)
        self.assertFalse([n for n in clips if n.startswith("move.fly") and n != "move.fly_land"])
        ride = [s for s in segs if "move.ride" in [n for n, _ in s.items]][0]
        self.assertTrue(ride.riding)
        self.assertEqual([a[0] for a in ride.actions], ["move.ride_cast"])
        wither = build.load_hero("the_wither")
        wsegs = hp.loco_segments(A(wither, {"carrier": None}))
        self.assertIn("move.fly", [n for s in wsegs for n, _ in s.items])

    def test_his_sheet_draws_the_broom_clips_riding(self):
        """sheet.png draws his flight clips (take-off, ride, landing) and the cast over the ride with the carrier on the
        mount bone, as in game, and every other row on foot; a hero without a mount carrier never rides there."""
        import tempfile
        from unittest import mock
        from render import hero_previews as hp
        a = self.assets()
        calls = []

        def fake_render(view, pose=None, riding=False, **kw):
            calls.append(riding)
            return np.zeros((4, 4, 4), dtype=np.uint8)

        names = hp.own_clips(a)
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(a, "render", side_effect=fake_render):
            hp.sheet(a, os.path.join(tmp, "sheet.png"), per_clip=2)
        self.assertEqual(len(calls), 2 * len(names))
        rows = {n for i, n in enumerate(names) if any(calls[2 * i:2 * i + 2])}
        self.assertEqual(rows, RIDING)
        for i, n in enumerate(names):
            self.assertEqual(calls[2 * i], calls[2 * i + 1], n)
        with open(os.path.join(ROOT, "src", "main", "resources", "assets", "factionclash", "heroes",
                               "kagenori_tsurumine.visual.json")) as f:
            other = type("A", (), {"visual": json.load(f)})()
        self.assertTrue(hp.rides_mount(a))
        self.assertFalse(hp.rides_mount(other))

    def test_previews_draw_him_in_his_own_phases(self):
        from render import hero_previews as hp
        a = self.assets()
        self.assertEqual(hp.phase_of_clip(a, "move.broom_hops"), 2)
        self.assertEqual(hp.phase_of_clip(a, "move.great_brew"), 3)
        self.assertEqual(hp.phase_of_clip(a, "phase.3"), 3)

    def test_g10_broom_gif_is_drawn_from_his_source(self):
        from anim import loco_preview
        entry = loco_preview.HERO_GIFS["hero_ambrose"]
        self.assertEqual((entry[0], entry[1]), (HID, None))

    def test_the_lineup_takes_him(self):
        from render import hero_previews as hp
        self.assertIn(HID, hp.lineup_ids())

    def test_parts_are_linted(self):
        """The three parts he added or changed build on every base (the static lint)."""
        for pid in ("headwear/witch_brim_up", "accessories/field_kit", "accessories/crow"):
            self.assertTrue(parts.load_part(pid))


if __name__ == "__main__":
    unittest.main()
