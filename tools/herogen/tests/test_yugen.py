"""C2: Yugen Kagaribi, the lantern fox, the fourth slice hero with his own source, art and signature animations.

His one body (the owner's rule), his moves' frames equal to his kit's (Fan Cut's two strikes, the ultimate's nine
shots 0.2 s apart), his sound markers, his clips' rules on every base and his fan clear of his body, open or shut; his
tails, three, six and nine (one fan of nine built once, shown by his phase looks; the middle three tipped with foxfire
in phase 3); his lanterns' orbit (sec.loop) and the wheel they stand up into for the ultimate; and the generator pieces
he needed: a chain's explicit fan `angles` and level-of-detail copies (`lod_from`), the preview renderer's phase looks
that show as well as hide (HeroModel.plan), the tail rule over every tail chain (a fox's copies were never checked),
the `liner` face paint and the runner's sprint in the hero previews."""
import copy
import io
import json
import os
import unittest

import numpy as np
from PIL import Image

import helpers  # noqa: F401
from helpers import mannequin
import assets
import build
import face
import gen
import grips
import tails
from anim import library
from anim import quality
from anim.clip import TICK, form_var
from anim.skeleton import Skeleton
from render.scene import HeroAssets
from rot import file_matrix
from util import HerogenError, ROOT

HID = "yugen_kagaribi"
FIXTURE = os.path.join(ROOT, "src", "test", "resources", "heroes", "slice", HID + ".json")
MIDDLE3 = ("tail_1", "fine_tail_1_d", "fine_tail_1_e")          # hidden in phase 2, burning in phase 3
ROOTS = {"tail_1": 0.0, "tail_1_b": -37.5, "tail_1_c": 37.5, "fine_tail_1_d": -18.75, "fine_tail_1_e": 18.75,
         "fine_tail_1_f": -56.25, "fine_tail_1_g": 56.25, "fine_tail_1_h": -75.0, "fine_tail_1_i": 75.0}


def _ticks(s):
    return int(round(float(s) * 20))


class YugenTest(unittest.TestCase):
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
        for b in (self.own, library.style("focus"), library.core()):
            if name in b.by_name:
                return b.by_name[name]
        return None

    # -------------------------------------------------------------- body, kit, frames, rules

    def test_one_body_for_good(self):
        """An adult man of ordinary size, slim build, drawn at 100% (SLICE_PLAN 3.2; the owner's one-body rule of
        HEROES_V2 3.6): no phase changes the body."""
        art = self.hero["art"]
        self.assertEqual((art["form"], art["base"], art["build"], art["scale"]), ("adult", "male", "slim", 100))
        self.assertEqual((self.visual["form"], self.visual["base"], self.visual["build"]), ("adult", "adult_m", "slim"))
        for ph in art.get("phases", []):
            self.assertFalse({"form", "base", "build"} & set(ph), "a phase changes the body")

    def test_kit_is_the_fixtures(self):
        for k, v in self.kit.items():
            if k != "_comment":
                self.assertEqual(self.hero[k], v, k)

    def test_budgets(self):
        """Nine tails and three lanterns inside HEROES_V2 4.3's budgets: at most 100 bones, 300 cubes, 130 cubes
        outside fine_ bones (what is drawn beyond the fine distance)."""
        problems, g = assets.check_geometry(HID, self.files, self.visual)
        self.assertEqual(problems, [])
        self.assertLessEqual(len(g.order), 100)
        self.assertLessEqual(len(g.cubes), 300)
        import validate
        self.assertLessEqual(validate.lod_cubes(g), 130)

    def test_move_frames_equal_the_kit(self):
        """Every move's clip and the ultimate's have the kit's wind-up, active and recovery to the tick (from the
        action's start, the 2-tick blend included): the release on a `hit`, the active part's last tick on a `cast`;
        Fan Cut has a `hit` per strike (its combo's two), the ultimate one per shot of its barrage, 0.2 s apart (the
        markers replace the form's hit ticks: MovePlan)."""
        timing = self.own.timing_json()["animations"]
        items = [("move." + m["id"], m) for m in self.kit["moves"]] + [("ultimate", self.kit["ultimate"])]
        for name, m in items:
            want = {k: _ticks(v) for k, v in m["frames"].items()}
            self.assertEqual(timing[name]["frame_ticks"], want, name)
            hits = [e["tick"] for e in timing[name]["events"] if e["type"] == "hit"]
            if m["form"] == "barrage":
                gap = _ticks(m["interval"])
                self.assertEqual(hits, [want["windup"] + i * gap for i in range(m["count"])], name)
            elif m["form"] == "combo":
                self.assertEqual(hits, [want["windup"], want["windup"] + want["active"] - 1], name)
            else:
                self.assertEqual(hits, [want["windup"]], name)
        definition, _ = assets.definition_of(HID)
        files = dict(self.files, **library.core().files(), **library.style("focus").files())
        self.assertEqual(assets.check_frames(definition, assets.Chain.of(self.visual, files)), [])

    def test_own_clips_pass_every_rule_on_every_base(self):
        self.assertEqual(quality.check_built(self.own, self.lookup, list(self.b.model.bones), own_file=True), [])
        self.assertLessEqual(len(self.own.clips), 24)
        self.assertNotIn("ultimate.loop", self.own.by_name)
        for n in ("escape", "last_stand", "move.sprint", "sec.loop"):
            self.assertIn(n, self.own.by_name)       # he escapes; caught again he stands; his fox lope (G10)

    def test_fan_clear_of_his_body(self):
        """The fan through every tick of his own clips, on his own body."""
        self.assertEqual(grips.animation_problems(self.hero, self.b, self.own, self.lookup, every=1), [])

    def test_the_open_fan_clear_of_his_body(self):
        """The open fan is the closed one scaled across its width (weapon_r's y, its fine child scaled back so the cord
        keeps its length); the skeleton ignores scale, so grips sees it shut: here every tick where it is open, scaled
        as GeckoLib draws it, stays out of the body. And it does open: in the volley, the cuts, the ultimate."""
        body = [c for c in self.model.cubes if c.part == "base" and c.bone in grips.BODY_BONES
                and c.bone not in ("hand_r", "arm_r", "forearm_r")]
        fan = grips.prop_cubes(self.model, "r")
        piv = np.array(self.model.bones["weapon_r"].pivot, float)
        opened = set()
        for clip in self.own.clips:
            for t, pose, drawn in grips.clip_poses(clip, self.lookup, self.d, 1):
                sy = (pose.get("weapon_r", {}).get("scale") or (1, 1, 1))[1]
                if not drawn or sy < 1.05:
                    continue
                opened.add(clip.name)
                w = self.sk.world(pose)
                for c in fan:
                    pts = grips._samples(c)
                    if c.bone == "weapon_r":
                        pts = piv + (pts - piv) * np.array([1.0, sy, 1.0])
                    r, tt = w[c.bone]
                    wp = pts @ r.T + tt
                    for bc in body:
                        rb, tb = w[bc.bone]
                        local = (wp - tb) @ rb
                        if bc.rotation is not None:
                            pv = np.array(bc.pivot, float)
                            local = pv + (local - pv) @ file_matrix(bc.rotation)
                        self.assertLessEqual(float(grips._depth_inside(local, bc.box).max()), grips.BODY_DEPTH,
                                             (clip.name, t, bc.bone, c.name))
        for n in ("move.foxfire_volley", "move.fan_cut", "ultimate", "move.lantern_ring"):
            self.assertIn(n, opened)

    def test_sound_markers(self):
        """His client-played sounds are markers in the clips their slots name (SOUND.md 4.7, 5): each move's client
        `release` (the volley, the fan cut, the vanish; the ultimate's `ninefold_shot` on every one of its nine
        shots), the phases, the intro and the quirks; the server's slots (wind-ups, impacts) are never markers."""
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
            if mid == "basic" or "release" not in slots:
                continue
            clip = "ultimate" if mid == ult else "move." + mid
            if plays(slots["release"]) == "client":
                self.assertIn(pre + slots["release"], sounds.get(clip, []), mid)
            else:
                self.assertNotIn(pre + slots["release"], sounds.get(clip, []), mid)
        self.assertEqual(sounds["ultimate"].count(pre + "ninefold_shot"), self.kit["ultimate"]["count"])
        self.assertIn(pre + hm["phases"]["2"], sounds["phase.2"])
        self.assertIn(pre + hm["phases"]["3"], sounds["phase.3"])
        self.assertIn(pre + hm["intro"], sounds["intro"])
        for n, s in hm["quirks"].items():
            self.assertIn(pre + s, sounds[n], n)
        server = {pre + s for m in hm["moves"].values() for k, s in m.items() if k in ("windup", "impact")}
        for name, got in sounds.items():
            self.assertFalse(set(got) & server, name)

    def test_faces_where_the_moment_needs_one(self):
        for c in self.own.clips:
            if not c.name.startswith("sec."):
                self.assertIn(("face_ctrl", "position"), c.tracks, c.name)

    def test_he_is_always_smiling(self):
        """'Smiles most when he is lying, and he is always smiling': calm and focused (every wind-up) smile; only his
        anger drops the smile (narrowed eyes, a straight mouth). His red eye-liner (`liner` face paint, trim)."""
        moods = self.visual["face"]["moods"]
        self.assertEqual(moods["calm"]["mouth"], 1)
        self.assertEqual((moods["focused"]["eyes"], moods["focused"]["mouth"]), (8, 1))
        self.assertEqual((moods["angry"]["eyes"], moods["angry"]["mouth"]), (8, 0))
        marks = self.hero["art"]["face"]["marks"]
        self.assertEqual([(m["type"], m["style"], m["color"]) for m in marks], [("face_paint", "liner", "trim")])

    # -------------------------------------------------------------- the tails

    def test_one_fan_of_nine_tails(self):
        """tails/fox_nine builds the nine tails once, at their listed angles: the first three full chains on the rig's
        tail bones and two coarser ones, the other six level-of-detail copies on one fine_ bone each (all segments on
        it); every copy has its own spring chain."""
        bones = self.model.bones
        for root in ROOTS:
            self.assertIn(root, bones)
        self.assertEqual([b for b in bones if b.startswith("tail_") and b[5].isdigit() and len(b) == 6],
                         ["tail_1", "tail_2", "tail_3", "tail_4", "tail_5"])
        for c in "defghi":
            kids = [b for b, x in bones.items() if x.parent == "fine_tail_1_" + c]
            self.assertEqual(kids, [], "a level-of-detail copy is one bone")
            self.assertEqual(len([cu for cu in self.model.cubes if cu.bone == "fine_tail_1_" + c]), 7)
        chains = [s["chain"] for s in self.model.secondary if s.get("part") == "tails/fox_nine"]
        self.assertEqual(len(chains), 9)
        # the fan: seen from behind, each tail's far end leans out by its angle (negative angles to his left, +x)
        pv = np.array(bones["tail_1"].pivot, float)
        lean = {}
        for root, ang in ROOTS.items():
            own = {root} | {b for b in bones if root.startswith("tail_1_") and b.startswith("tail_") and
                            b.endswith(root[-2:])} | ({"tail_2", "tail_3", "tail_4", "tail_5"} if root == "tail_1"
                                                      else set())
            far = None
            for cu in self.model.cubes:
                if cu.bone in own:
                    c = np.array(cu.origin, float) + np.array(cu.size, float) / 2
                    if cu.rotation is not None:
                        c = file_matrix(cu.rotation) @ (c - np.array(cu.pivot, float)) + np.array(cu.pivot, float)
                    if far is None or np.linalg.norm(c - pv) > np.linalg.norm(far - pv):
                        far = c
            lean[root] = np.degrees(np.arctan2(far[0] - pv[0], far[1] - pv[1]))
        order = sorted(ROOTS, key=lambda r: ROOTS[r])
        self.assertEqual(sorted(ROOTS, key=lambda r: -lean[r]), order)
        self.assertLess(abs(lean["tail_1"]), 3.0)

    def test_phase_looks_show_three_six_and_nine(self):
        """Phase 1 shows three tails (the middle one and the pair at +-37.5), phase 2 six (that pair and the two outer
        pairs: two wings, the middle three hidden), phase 3 nine; the renderer follows HeroModel.plan (each look
        shows and hides its lists, the later look winning)."""
        a = HeroAssets.build(HID)
        want = {1: {"tail_1", "tail_1_b", "tail_1_c"},
                2: {"tail_1_b", "tail_1_c", "fine_tail_1_f", "fine_tail_1_g", "fine_tail_1_h", "fine_tail_1_i"},
                3: set(ROOTS)}
        for ph, shown in want.items():
            hidden = set(a.hidden(phase=ph))
            self.assertEqual({r for r in ROOTS if r not in hidden}, shown, ph)
        # beyond the fine distance: the full-detail pair stays in every phase
        for ph in (1, 2, 3):
            far = set(a.hidden(phase=ph, far=True))
            self.assertNotIn("tail_1_b", far)
            self.assertNotIn("tail_1_c", far)

    def test_scene_hidden_follows_heromodel_plan(self):
        """A later look's `show` wins over an earlier look's `hide` (and the reverse); a p1_ bone shows only in phase
        1; heroes whose looks only show pN_ bones of their own phase (Natsuho's puffed tail) are unchanged.
        Sabotage: the old rule (hides only, never shows) leaves Yugen's six and nine hidden."""
        a = HeroAssets.build(HID)
        old = set(b for ph in a.visual["phases"] if ph["phase"] <= 2 for b in ph.get("hide", []))
        self.assertTrue({"fine_tail_1_f", "fine_tail_1_h"} <= old)        # what the old rule would hide in phase 2
        self.assertNotIn("fine_tail_1_f", a.hidden(phase=2))
        fake = copy.deepcopy(a.visual)
        fake["bones"]["phase"] = {"p1_cape": 1, "p2_halo": 2}
        fake["phases"] = [{"phase": 2, "show": [], "hide": ["x"]}, {"phase": 3, "show": ["x"], "hide": []}]
        a.visual = fake
        self.assertIn("p2_halo", a.hidden(phase=1))
        self.assertNotIn("p1_cape", a.hidden(phase=1))
        self.assertIn("p1_cape", a.hidden(phase=2))
        self.assertIn("x", a.hidden(phase=2))
        self.assertNotIn("x", a.hidden(phase=3))
        nat = HeroAssets.build("natsuho_komezaki")
        self.assertIn("p2_tail_1", nat.hidden(phase=1))
        self.assertNotIn("p2_tail_1", nat.hidden(phase=2))

    def test_foxfire_tips_burn_from_phase_3_only(self):
        """The three tails hidden through phase 2 (the middle one and the pair at +-18.75) have their tips in their own
        slot (`metal`, nothing else uses it): white like the other tips in the base texture, foxfire blue in the
        phase texture and lit in its glow mask; since the phase texture shows from phase 2, where those tails are
        hidden, the tips burn only in phase 3."""
        users = [p["part"] for p in self.hero["art"]["parts"] if "metal" in (p.get("color"), p.get("alt"),
                                                                            p.get("trim"), p.get("detail"))]
        self.assertEqual(users, ["markings/foxfire_tips"])
        self.assertNotIn("metal", self.hero["art"]["weapon"].values())
        p3 = [ph for ph in self.hero["art"]["phases"] if ph["phase"] == 3][0]
        self.assertEqual(set(p3["palette"]), {"metal"})
        paths = build.out_paths(HID)
        tex = np.asarray(Image.open(io.BytesIO(self.files[paths["texture"]])).convert("RGBA")).astype(int)
        tex2 = np.asarray(Image.open(io.BytesIO(self.files[paths["p2"]])).convert("RGBA")).astype(int)
        glow = np.asarray(Image.open(io.BytesIO(self.files[paths["glowmask"]])).convert("RGBA"))
        glow2 = np.asarray(Image.open(io.BytesIO(self.files[paths["p2_glowmask"]])).convert("RGBA"))
        changed = np.argwhere(np.abs(tex - tex2).sum(axis=2) > 0)
        self.assertGreater(len(changed), 20)
        # every changed texel lies on a tip cube of the three tails, and is bluer in the phase texture
        tip_cubes = [c for c in self.model.cubes if c.name in ("tails/fox_nine#tail4_0", "tails/fox_nine#tail2_0_d",
                                                                "tails/fox_nine#tail2_0_e")]
        self.assertEqual(len(tip_cubes), 3)
        self.assertEqual(sorted(c.bone for c in tip_cubes), ["fine_tail_1_d", "fine_tail_1_e", "tail_5"])
        v, u = changed[0]
        self.assertGreater(tex2[v, u, 2] - tex2[v, u, 0], tex[v, u, 2] - tex[v, u, 0])
        self.assertGreater(int((glow2[..., 3] > 0).sum()) - int((glow[..., 3] > 0).sum()), 20)

    def test_the_tail_rule_sees_every_tail(self):
        """tails.tail_bones takes every tail chain, a fox's copies and their level-of-detail copies included (before
        C2 only tail_1's chain counted, and the copies hang from `body`). Sabotage: a copy swung down and forward
        under him is in the zone (the old rule would not have looked at it); his whole chain passes."""
        a = HeroAssets.build(HID)
        rule = tails.TailRule(a.built.model)
        for root in ROOTS:
            self.assertIn(root, rule.bones)
        self.assertEqual(len(rule.tips), 9)
        self.assertEqual(rule.tip[0], "tail_5")
        pose = dict(a.stance())
        self.assertEqual(rule.hits(rule.sk.world(pose)), {})
        for root in ("tail_1_b", "fine_tail_1_h"):
            bad = dict(pose)
            bad[root] = {"rotation": (-100.0, 0.0, ROOTS[root])}        # folded onto the middle, swung under
            hits = rule.hits(rule.sk.world(bad))
            self.assertTrue(any(b == root or b.endswith(root[-2:]) for b in hits), (root, hits))
        old = {"tail_1"}
        self.assertNotIn("tail_1_b", old)          # the pre-C2 roots

        chain = [a.built.animations, library.style("focus"), library.core()]

        def lookup(name):
            for built in chain:
                if name in built.by_name:
                    return built.by_name[name]
            return None
        self.assertEqual(tails.check(a, a.built.model, lookup)[0], [])

    # -------------------------------------------------------------- the lanterns

    def test_the_lanterns_orbit_on_the_secondary_controller(self):
        """sec.loop turns the lanterns once round him (lantern_orbit's rotation, a whole turn unwound at the end) and
        keys nothing else; no other clip keys lantern_orbit, so a move moves, tilts and widens the ring through
        lantern_ring and turns or scales each lantern on its own fine_ bone."""
        loop = self.own.by_name["sec.loop"]
        self.assertEqual(loop.loop, "loop")
        self.assertEqual(set(loop.tracks), {("lantern_orbit", "rotation")})
        tr = loop.tracks[("lantern_orbit", "rotation")]
        self.assertAlmostEqual(tr.keys[-1].pre[1], 360.0, places=3)
        ys = [tr.sample(i * TICK)[1] for i in range(int(round(loop.length / TICK)))]
        self.assertTrue(all(b > a for a, b in zip(ys, ys[1:])))
        for c in self.own.clips:
            if c.name != "sec.loop":
                self.assertFalse([k for k in c.tracks if k[0] == "lantern_orbit"], c.name)
        bones = self.model.bones
        self.assertEqual(bones["lantern_ring"].parent, "root")
        self.assertEqual(bones["lantern_orbit"].parent, "lantern_ring")
        for n in (1, 2, 3):
            self.assertEqual(bones["fine_lantern_%d" % n].parent, "lantern_orbit")

    def test_the_ultimate_stands_the_lanterns_up_into_a_wheel(self):
        """During the nine shots the ring is tipped up (lantern_ring -90 degrees about x) behind him and raised, each
        lantern turned back upright on it; by the clip's end the ring is back in its orbit."""
        ult = self.own.by_name["ultimate"]
        for i in range(9):
            t = 1.5 + 0.2 * i - 2 * TICK
            p = ult.sample(t, self.d)
            self.assertAlmostEqual(p["lantern_ring"]["rotation"][0], -90.0, delta=1.0)
            self.assertGreater(p["lantern_ring"]["position"][2], 6.0)          # behind him (+z)
            self.assertAlmostEqual(p["fine_lantern_1"]["rotation"][0], 90.0, delta=1.0)
        end = ult.sample(ult.length, self.d)
        self.assertLess(max(abs(v) for v in end["lantern_ring"]["rotation"]), 0.5)
        self.assertLess(max(abs(v) for v in end["lantern_ring"]["position"]), 0.05)

    # -------------------------------------------------------------- generator pieces

    def test_chain_angles_and_level_of_detail_copies(self):
        """gen.chains: `angles` lists the copies' fan angles in build order (the first keeps the bone names, the rest
        take _b, _c ...), `lod_from` builds the copies from that index each on one fine_ bone holding every segment;
        a wrong count of angles or an lod_from out of range is refused. Without them a fan builds as before."""
        hero = mannequin("adult_m", [{"part": "tails/fox_nine", "color": "hair", "alt": "hair_accent"}])
        files, b = build.build_files(hero["id"], hero)
        m = b.model
        self.assertIn("fine_tail_1_d", m.bones)
        self.assertNotIn("fine_tail_2_d", m.bones)
        self.assertEqual(m.bones["fine_tail_1_d"].parent, "body")
        self.assertEqual(m.bones["tail_2_b"].parent, "tail_1_b")
        # the first listed angle keeps the names: tail_1's chain is the unrotated tail of tails/fox
        ref = mannequin("adult_m", [{"part": "tails/fox", "color": "hair", "alt": "hair_accent"}])
        _, rb = build.build_files(ref["id"], ref)
        for n in ("tail_1", "tail_3", "tail_5"):
            self.assertEqual(tuple(m.bones[n].pivot), tuple(rb.model.bones[n].pivot), n)
        # tail_1_b at -37.5 degrees: its last pivot lies on the hero's left (+x), tail_1_c's mirrored
        self.assertGreater(m.bones["tail_3_b"].pivot[0], 1.0)
        self.assertAlmostEqual(m.bones["tail_3_b"].pivot[0], -m.bones["tail_3_c"].pivot[0], places=3)
        import parts as partsmod
        base = copy.deepcopy(partsmod.load_part("tails/fox_nine"))
        for bad in ({"angles": [0, 10]}, {"lod_from": 0}, {"lod_from": 10}):
            spec = copy.deepcopy(base)
            spec["chains"][0].update(bad)
            with self.assertRaises(HerogenError, msg=str(bad)):
                self._build_with_part(spec)

    def _build_with_part(self, spec):
        import parts as partsmod
        real = partsmod.load_part

        def fake(pid, *a, **k):
            return copy.deepcopy(spec) if pid == "tails/fox_nine" else real(pid, *a, **k)
        partsmod.load_part = fake
        try:
            hero = mannequin("adult_m", [{"part": "tails/fox_nine", "color": "hair", "alt": "hair_accent"}])
            build.build_files(hero["id"], hero)
        finally:
            partsmod.load_part = real

    def test_liner_face_paint(self):
        """The `liner` face paint: a stroke in its colour under the outer half of each lower lid (row 16, the row every
        eye frame leaves as skin), fading toward the nose, and nothing else of the skin layer changes; an unknown
        style still fails and names it."""
        import palette
        art = copy.deepcopy(self.hero["art"])
        ramps = palette.resolve(art["palette"], "male")
        ramps = ramps[0] if isinstance(ramps, tuple) else ramps
        with_l = face.face_spec(art["face"], "male", art["palette"])
        art["face"]["marks"] = []
        without = face.face_spec(art["face"], "male", art["palette"])
        a = face.skin_layer(with_l, face.FaceColours(ramps, with_l, art["palette"])).astype(int)
        b = face.skin_layer(without, face.FaceColours(ramps, without, art["palette"])).astype(int)
        diff = np.argwhere(np.abs(a - b).sum(axis=2) > 0)
        self.assertEqual(sorted({int(r) for r, _ in diff}), [16])
        self.assertEqual(len(diff), 6)                         # three texels under each eye
        with self.assertRaises(HerogenError) as cm:
            face._marks([{"type": "face_paint", "style": "kohl"}])
        self.assertIn("liner", str(cm.exception))

    # -------------------------------------------------------------- previews

    def test_locomotion_follows_his_traits(self):
        """A runner whose own file overrides the sprint (his fox lope, G10's variant) shows it after the run; a leaper's
        crouch jump, leap and heavy landing; Hinae (a runner without an override) keeps her set."""
        from render import hero_previews as hp
        a = hp.assets(HID)
        clips = [n for s in hp.loco_segments(a) for n, _ in s.items]
        for n in ("move.walk", "move.run", "move.sprint", "move.crouch_jump", "move.leap", "move.land_heavy",
                  "move.climb"):
            self.assertIn(n, clips)
        hin = hp.assets("hinae_shirasuzu")
        theirs = [n for s in hp.loco_segments(hin) for n, _ in s.items]
        self.assertEqual(theirs, ["move.walk", "move.run", "move.jump", "move.leap", "move.land", "move.swim",
                                  "move.climb"])

    def test_the_lineup_takes_him(self):
        from render import hero_previews as hp
        ids = hp.lineup_ids()
        self.assertIn(HID, ids)
        self.assertEqual(ids[-1], "test_rig")


if __name__ == "__main__":
    unittest.main()
