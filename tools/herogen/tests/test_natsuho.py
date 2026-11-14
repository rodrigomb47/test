"""C1: Natsuho Komezaki, the first slice hero with her own source, art and signature animations.

Her one body (the owner's rule), the phase-2 puff over her tail (a chain laid over the tail's own bones), her moves'
frames equal to her kit's, her sound markers, her clips' rules on every base and her claws clear of her body."""
import json
import os
import unittest

import assets
import build
import grips
import lint
from anim import library
from anim import quality
from parts import apply_part
from rig import Model
from util import HerogenError, ROOT

HID = "natsuho_komezaki"
FIXTURE = os.path.join(ROOT, "src", "test", "resources", "heroes", "slice", HID + ".json")


def _ticks(s):
    return int(round(float(s) * 20))


class NatsuhoTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.hero = build.load_hero(HID)
        cls.files, cls.b = build.build_files(HID)
        cls.own = cls.b.animations
        with open(FIXTURE) as f:
            cls.kit = json.load(f)

    def test_one_body_for_good(self):
        """An adult of ordinary size, slim, drawn at 92% (SLICE_PLAN 3.1; the owner's one-body rule of HEROES_V2 3.6):
        the form never changes with a phase."""
        art = self.hero["art"]
        self.assertEqual((art["form"], art["base"], art["build"], art["scale"]), ("adult", "female", "slim", 92))
        visual = json.loads(self.files[build.out_paths(HID)["visual"]])
        self.assertEqual((visual["form"], visual["base"]), ("adult", "adult_f"))
        for ph in art.get("phases", []):
            self.assertFalse({"form", "base", "build"} & set(ph), "a phase changes the body")

    def test_kit_is_the_fixtures(self):
        """The source's kit fields are the slice fixture's (the authored hero the engine side will define)."""
        for k, v in self.kit.items():
            if k != "_comment":
                self.assertEqual(self.hero[k], v, k)

    def test_move_frames_equal_the_kit(self):
        """Every move's clip, and the ultimate's, has the kit's wind-up, active and recovery to the tick (the server's
        frame of reference: the action's start, the 2-tick blend included), its release on a `hit` and, where the
        kit's active part is longer than its hits, a `cast` on the active part's last tick."""
        timing = self.own.timing_json()["animations"]
        items = [("move." + m["id"], m) for m in self.kit["moves"]] + [("ultimate", self.kit["ultimate"])]
        for name, m in items:
            ft = timing[name]["frame_ticks"]
            want = {k: _ticks(v) for k, v in m["frames"].items()}
            self.assertEqual(ft, want, name)
        hits = {name: [e["tick"] for e in timing[name]["events"] if e["type"] == "hit"] for name, _ in items}
        self.assertEqual(hits["move.claw_string"], [9, 13, 18])          # a combo of three: one hit per strike
        self.assertEqual(hits["move.pounce"], [16])                       # a leap: one release
        definition, _ = assets.definition_of(HID)
        self.assertEqual(assets.check_frames(definition, assets.Chain.of(
            json.loads(self.files[build.out_paths(HID)["visual"]]), dict(self.files, **library.core().files(),
                                                                         **library.style("fists").files()))), [])

    def test_own_clips_pass_every_rule_on_every_base(self):
        style, core = library.style("fists"), library.core()

        def lookup(name):
            for b in (self.own, style, core):
                if name in b.by_name:
                    return b.by_name[name]
            return None
        self.assertEqual(quality.check_built(self.own, lookup, list(self.b.model.bones), own_file=True), [])
        self.assertLessEqual(len(self.own.clips), 24)

    def test_claws_clear_of_her_body(self):
        """The claws through every second tick of her own clips, on her own body (grips.animation_problems)."""
        style, core = library.style("fists"), library.core()

        def lookup(name):
            for b in (self.own, style, core):
                if name in b.by_name:
                    return b.by_name[name]
            return None
        self.assertEqual(grips.animation_problems(self.hero, self.b, self.own, lookup, every=2), [])

    def test_sound_markers(self):
        """Her client-played sounds are markers in the clips their slot names (SOUND.md 4.7, 5): each move's
        `release`, the pounce's `end`, the phases, the intro and the quirks; every id is in her catalogue."""
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
                if not s.startswith("minecraft:"):          # the air jump's vanilla gust (G10)
                    self.assertTrue(s.startswith(pre) and s[len(pre):] in sfx["sounds"], s)
        hm = sfx["hero"]
        for mid, slots in hm["moves"].items():
            if mid == "basic" or mid == self.kit["ultimate"]["id"]:
                continue
            self.assertIn(pre + slots["release"], sounds["move." + mid], mid)
        self.assertIn(pre + hm["moves"]["pounce"]["end"], sounds["move.pounce"])
        self.assertIn(pre + hm["phases"]["2"], sounds["phase.2"])
        self.assertIn(pre + hm["phases"]["3"], sounds["phase.3"])
        self.assertIn(pre + hm["intro"], sounds["intro"])
        self.assertIn(pre + hm["quirks"]["personal.1"], sounds["personal.1"])
        self.assertIn(pre + hm["quirks"]["personal.3"], sounds["personal.3"])

    def test_faces_where_the_moment_needs_one(self):
        """Every move, the ultimate, the phases and the end-of-fight moments key face_ctrl."""
        for c in self.own.clips:
            if c.name.startswith(("move.", "ultimate", "phase.", "intro", "victory", "taunt", "last_stand",
                                  "escape", "state.", "personal.1", "personal.3")):
                self.assertIn(("face_ctrl", "position"), c.tracks, c.name)

    def test_tail_puff_follows_the_tail(self):
        """Phase 2's puffed fur sits in p2_ children of the tail's own bones (so every key and spring of the tail
        moves it) and shows from phase 2 on."""
        visual = json.loads(self.files[build.out_paths(HID)["visual"]])
        m = self.b.model
        for i in range(1, 6):
            b = m.bones["p2_tail_%d" % i]
            self.assertEqual(b.parent, "tail_%d" % i)
            self.assertEqual(visual["bones"]["phase"]["p2_tail_%d" % i], 2)
        puff = [c for c in m.cubes if c.part == "tails/cat_puff"]
        self.assertTrue(puff)
        self.assertTrue(all(c.bone.startswith(("p2_tail_", "fine_p2_tail_")) for c in puff))


class ChainReuseTest(unittest.TestCase):
    def test_reuse_takes_the_earlier_chains_points(self):
        m = Model("t", "teen_f")
        apply_part(m, {"part": "tails/cat_long", "params": {"size": 1.3}}, 0)
        apply_part(m, {"part": "tails/cat_puff", "phase": 2}, 1)
        pts = m.chain_points[("tail_1", "tail_2", "tail_3", "tail_4", "tail_5")]
        for i in range(5):
            self.assertEqual(tuple(m.bones["p2_tail_%d" % (i + 1)].pivot), tuple(pts[i]))

    def test_reuse_without_the_chain_fails(self):
        m = Model("t", "teen_f")
        with self.assertRaises(HerogenError):
            apply_part(m, {"part": "tails/cat_puff", "phase": 2}, 0)

    def test_lint_wears_the_required_part(self):
        hero = lint.lint_hero("tails/cat_puff", "adult_f")
        self.assertEqual([p["part"] for p in hero["art"]["parts"]][-2:], ["tails/cat_long", "tails/cat_puff"])
        self.assertEqual(lint.static_check("tails/cat_puff", lint.part_ids()), [])


class PreviewCaptionsTest(unittest.TestCase):
    def test_captions_fit_any_hero(self):
        """hero-previews draws generic captions (the pronoun from the base) and a hero's own lines only for it."""
        from render import hero_previews

        class Stub:
            pass
        a = Stub()
        a.visual, a.hero = {"base": "adult_m"}, {"id": "someone_else"}
        c = hero_previews.captions(a)
        self.assertEqual((c["side"], c["phase.2"], c["air_jump"]), ("his right side", "phase.2", "air_jump"))
        a.visual, a.hero = {"base": "adult_f"}, {"id": HID}
        c = hero_previews.captions(a)
        self.assertEqual(c["side"], "her right side")
        self.assertEqual(c["air_jump"], "air_jump (her cat twist)")


if __name__ == "__main__":
    unittest.main()
