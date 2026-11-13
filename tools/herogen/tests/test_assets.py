"""G7: herogen check covers HEROES_V2.md 14.2 (assets.py: the file-level rules the Java HeroAssetsTest mirrors;
distinct.py: the distinctness NOTEs), with the three negative tests of SLICE_PLAN.md 6.3: a marker moved by 3 ticks, a
model over the cube budget and a missing eye frame each fail with a clear message."""
import copy
import json
import os
import shutil
import tempfile
import unittest

import numpy as np
from PIL import Image

import helpers  # noqa: F401  (puts tools/herogen on sys.path)

import assets
import build
import distinct
from anim import library as animlib
from util import ROOT

SLICE_STYLES = {"natsuho_komezaki": "fists", "yugen_kagaribi": "focus", "benika_akagane": "hammer",
                "hinae_shirasuzu": "focus", "kagenori_tsurumine": "katana", "tobimaru_usugumo": "twin_blades",
                "ilsabet_frostmere": "sword_shield", "ambrose_nettlewick": "staff"}


def copy_root(hero_id="test_rig"):
    """A temporary repository root with what HeroAssetsTest reads for one hero: its committed outputs, the
    animation and timing files of its chain, its server definition and HeroActions.java."""
    tmp = tempfile.mkdtemp(prefix="herogen_assets_")
    paths = list(assets.committed_files(hero_id))
    d, dp = assets.definition_of(hero_id)
    if dp:
        paths.append(dp)
    paths.append(assets.ACTIONS_JAVA)
    for p in paths:
        os.makedirs(os.path.join(tmp, os.path.dirname(p)), exist_ok=True)
        shutil.copyfile(os.path.join(ROOT, p), os.path.join(tmp, p))
    return tmp


def edit_json(root, path, fn):
    full = os.path.join(root, path)
    with open(full, encoding="utf-8") as f:
        data = json.load(f)
    fn(data)
    with open(full, "w", encoding="utf-8") as f:
        json.dump(data, f)


def lib_chain(style, own=None):
    """The real style library and core as written, under a synthetic own file {name: (anim, timing)}."""
    entries = []
    own = own or {}
    entries.append(("t_hero", {"animations": {n: v[0] for n, v in own.items()}},
                    {"animations": {n: v[1] for n, v in own.items()}}))
    for built in (animlib.style(style), animlib.core()):
        entries.append((built.file, json.loads(json.dumps(built.animation_json())),
                        json.loads(json.dumps(built.timing_json()))))
    return assets.Chain(entries)


def clip_with_frames(frames, kind="action"):
    """A minimal written animation and its timing entry with these frames (seconds, from the action's start)."""
    ft = {k: int(round(v / assets.TICK)) for k, v in frames.items()}
    total = sum(ft.values())
    anim = {"animation_length": round(total * assets.TICK - 0.1, 3), "bones": {}}
    timing = {"length": anim["animation_length"], "ticks": total - 2, "kind": kind, "transition_ticks": 2,
              "total_ticks": total, "frames": dict(frames), "frame_ticks": ft, "events": []}
    return anim, timing


# ------------------------------------------------------------------ the code table and the committed heroes

class ActionTableTest(unittest.TestCase):
    def test_the_table_is_read_from_the_java_source(self):
        t = assets.action_table()
        want = {1: "attack.1", 4: "attack.ranged", 5: "guard", 15: "eat", 16: "intro", 17: "phase.2", 18: "phase.3",
                22: "spared", 23: "founding", 24: "state.1", 26: "state.3", 32: "work.mine", 38: "work.fish_wait",
                47: "work.eat", 48: "emote.greet", 57: "emote.shrug", 58: "personal.1", 60: "personal.3",
                64: "move.0", 75: "move.11", 76: "ultimate", 77: "ultimate.end", 78: "ultimate.awakened"}
        for code, name in want.items():
            self.assertEqual(t[code], name, code)
        self.assertNotIn(0, t)
        self.assertNotIn(27, t)
        self.assertNotIn(79, t)

    def test_a_table_it_cannot_read_is_a_clear_error(self):
        tmp = tempfile.mkdtemp()
        try:
            p = os.path.join(tmp, assets.ACTIONS_JAVA)
            os.makedirs(os.path.dirname(p))
            with open(os.path.join(ROOT, assets.ACTIONS_JAVA), encoding="utf-8") as f:
                src = f.read()
            with open(p, "w", encoding="utf-8") as f:
                f.write(src.replace("String[] FIXED", "String[] FIXED_RENAMED"))
            with self.assertRaisesRegex(Exception, "cannot read the action code table.*FIXED"):
                assets.action_table(tmp)
        finally:
            shutil.rmtree(tmp)


class CommittedTest(unittest.TestCase):
    def test_the_committed_heroes_pass(self):
        for hid in build.hero_ids():
            problems, notes = assets.check_committed(hid)
            self.assertEqual(problems, [], hid)
        # the test rig's hand-written definition has a move without a clip: a NOTE, not an error
        _, notes = assets.check_committed("test_rig")
        self.assertTrue(any("lunge" in n and "telegraph" in n for n in notes), notes)

    def test_visual_json_carries_the_move_list_and_the_asymmetry(self):
        hero = build.load_hero("test_rig")
        hero["moves"] = [{"id": "lunge", "form": "dash"}, {"id": "spin", "form": "nova"}]
        hero["art"] = copy.deepcopy(hero["art"])
        hero["art"].setdefault("face", {})["eyepatch"] = "r"
        files, _ = build.build_files("test_rig", hero)
        visual = json.loads(files[build.out_paths("test_rig")["visual"]])
        self.assertEqual(visual["moves"], ["lunge", "spin"])
        self.assertTrue(visual["face"]["asymmetric"])
        committed = json.loads(assets.committed_files("test_rig")[build.out_paths("test_rig")["visual"]])
        self.assertEqual(committed["moves"], [])
        self.assertFalse(committed["face"]["asymmetric"])


# ------------------------------------------------------------------ SLICE_PLAN 6.3: the three negative tests

class NegativeTest(unittest.TestCase):
    """Each breaks one committed file of the test rig in a copy of the repository and runs the check
    `herogen check` runs on what the mod ships (assets.check_committed)."""

    def setUp(self):
        self.root = copy_root("test_rig")

    def tearDown(self):
        shutil.rmtree(self.root)

    def problems(self):
        return assets.check_committed("test_rig", self.root)[0]

    def test_clean_copy_passes(self):
        self.assertEqual(self.problems(), [])

    def test_a_marker_moved_by_three_ticks_fails(self):
        path = animlib.out_paths("_style_katana")["animation"]

        def move_hit(d):
            tl = d["animations"]["attack.1"]["timeline"]
            tl["0.65"] = tl.pop("0.5")          # the hit 3 ticks late; the timing file still says tick 10
        edit_json(self.root, path, move_hit)
        p = self.problems()
        self.assertTrue(any("_style_katana: attack.1: markers" in m for m in p), p)

    def test_a_timing_file_moved_by_three_ticks_fails(self):
        path = animlib.out_paths("_style_katana")["timing"]

        def move(d):
            a = d["animations"]["attack.1"]
            a["frames"]["windup"] = 0.65
            a["frame_ticks"]["windup"] = 13
        edit_json(self.root, path, move)
        p = self.problems()
        self.assertTrue(any("attack.1: wind-up 0.65 s in the timing file, first hit at 0.50 s" in m for m in p), p)

    def test_a_model_over_the_cube_budget_fails(self):
        path = build.out_paths("test_rig")["geo"]

        def grow(d):
            bones = d["minecraft:geometry"][0]["bones"]
            body = next(b for b in bones if b["name"] == "body")
            extra = copy.deepcopy(body["cubes"][0])
            body["cubes"] += [copy.deepcopy(extra) for _ in range(120)]
        edit_json(self.root, path, grow)
        p = self.problems()
        self.assertTrue(any("cubes (budget 300)" in m for m in p), p)
        self.assertTrue(any("cubes outside fine_ bones (budget 130)" in m for m in p), p)

    def test_a_missing_eye_frame_fails(self):
        path = os.path.join(self.root, build.out_paths("test_rig")["texture"])
        img = np.array(Image.open(path).convert("RGBA"))
        img[10:20, 64:84] = 0                  # eye frame 4 (row 1, column 0 of the 4 x 3 grid) erased
        Image.fromarray(img, "RGBA").save(path)
        p = self.problems()
        self.assertTrue(any("eyes frame 4 is not fully opaque (missing frame?)" in m for m in p), p)

    def test_a_blank_skin_eye_frame_fails(self):
        path = os.path.join(self.root, build.out_paths("test_rig")["texture"])
        img = np.array(Image.open(path).convert("RGBA"))
        img[10:20, 64:84] = img[9, 64]          # eye frame 4 painted over with frame 0's bottom-row skin
        Image.fromarray(img, "RGBA").save(path)
        p = self.problems()
        self.assertTrue(any("eyes frame 4 is blank skin (missing frame?)" in m for m in p), p)

    def test_a_missing_chain_file_fails(self):
        os.remove(os.path.join(self.root, animlib.out_paths("_style_katana")["timing"]))
        p = self.problems()
        self.assertTrue(any("_style_katana.json: missing" in m for m in p), p)

    def test_broken_json_and_geometry_version_fail(self):
        full = os.path.join(self.root, build.out_paths("test_rig")["visual"])
        with open(full, "rb") as f:
            data = f.read()
        with open(full, "wb") as f:
            f.write(data[:-10])
        p = self.problems()
        self.assertTrue(any("test_rig.visual.json: not valid JSON" in m for m in p), p)
        with open(full, "wb") as f:
            f.write(data)
        edit_json(self.root, build.out_paths("test_rig")["geo"], lambda d: d.update(format_version="1.8.0"))
        p = self.problems()
        self.assertTrue(any("format_version '1.8.0'" in m for m in p), p)

    def test_visual_texture_and_regions_must_match(self):
        def bad(d):
            d["texture"]["height"] = 256
            d["face"]["regions"]["mouth"]["v"] = 31
        edit_json(self.root, build.out_paths("test_rig")["visual"], bad)
        p = self.problems()
        self.assertTrue(any("visual.json texture 256x256, the geometry 256x128" in m for m in p), p)
        self.assertTrue(any("face.regions.mouth" in m for m in p), p)

    def test_a_definition_with_phases_needs_the_phase_clips(self):
        dpath = assets.DEF_DIR + "/test_rig.json"
        edit_json(self.root, dpath, lambda d: d["boss"].update(phases=[{"at": 100}, {"at": 60}, {"at": 25}]))
        p = self.problems()
        self.assertTrue(any("action code 17 (phase.2, 3 boss phases) does not resolve through test_rig, "
                            "_style_katana, _core" in m for m in p), p)
        self.assertTrue(any("action code 18 (phase.3" in m for m in p), p)


# ------------------------------------------------------------------ the file-level rules one by one

def written(name, keys, loop=False, length=1.0):
    a = {"animation_length": length, "bones": {"face_ctrl": {"position": keys}}}
    if loop:
        a["loop"] = True
    return {"animations": {name: a}}


class FileRulesTest(unittest.TestCase):
    def test_face_keys_jump_and_stay_whole(self):
        good = written("x", {"0.0": {"vector": [0, 0, 0]}, "0.2": {"pre": [0, 0, 0], "post": [5, 4, 0]},
                             "0.5": {"vector": [5, 4, 0]}})
        self.assertEqual(assets.check_face_keys("f", good), [])
        slide = written("x", {"0.0": {"vector": [0, 0, 0]}, "0.2": {"vector": [5, 4, 0]}})
        self.assertTrue(any("without a jump" in m for m in assets.check_face_keys("f", slide)))
        frac = written("x", {"0.0": {"vector": [0.5, 0, 0]}})
        self.assertTrue(any("not whole frame numbers" in m for m in assets.check_face_keys("f", frac)))
        high = written("x", {"0.0": {"vector": [13, 0, 0]}})
        self.assertTrue(any("in range" in m for m in assets.check_face_keys("f", high)))
        wrong_pre = written("x", {"0.0": {"vector": [0, 0, 0]}, "0.2": {"pre": [3, 0, 0], "post": [5, 0, 0]}})
        self.assertTrue(any("without a jump" in m for m in assets.check_face_keys("f", wrong_pre)))

    def test_channels_ascend_start_at_zero_and_loops_close(self):
        self.assertEqual(assets.check_channels("f", written("x", {"0.0": {"vector": [0, 0, 0]},
                                                                  "1.0": {"vector": [0, 0, 0]}}, loop=True)), [])
        desc = written("x", {"0.0": {"vector": [0, 0, 0]}, "0.6": {"vector": [1, 0, 0]}, "0.3": {"vector": [2, 0, 0]}})
        self.assertTrue(any("do not ascend" in m for m in assets.check_channels("f", desc)))
        late = written("x", {"0.1": {"vector": [0, 0, 0]}})
        self.assertTrue(any("not 0" in m for m in assets.check_channels("f", late)))
        after = written("x", {"0.0": {"vector": [0, 0, 0]}, "1.2": {"vector": [0, 0, 0]}})
        self.assertTrue(any("after the end" in m for m in assets.check_channels("f", after)))
        open_loop = written("x", {"0.0": {"vector": [0, 0, 0]}, "1.0": {"vector": [3, 0, 0]}}, loop=True)
        self.assertTrue(any("does not close" in m for m in assets.check_channels("f", open_loop)))
        no_end = written("x", {"0.0": {"vector": [0, 0, 0]}, "0.8": {"vector": [0, 0, 0]}}, loop=True)
        self.assertTrue(any("without a key at the end" in m for m in assets.check_channels("f", no_end)))
        molang = written("x", {"0.0": {"vector": [0, "1 + 2 * v.fc_dhip", 0]},
                               "1.0": {"vector": [0, "1 + 2 * v.fc_dhip", 0]}}, loop=True)
        self.assertEqual(assets.check_channels("f", molang), [])

    def test_the_written_libraries_pass_the_file_rules(self):
        for built in [animlib.core()] + [animlib.style(s) for s in build.library_styles()]:
            a = json.loads(json.dumps(built.animation_json()))
            self.assertEqual(assets.check_channels(built.file, a) + assets.check_face_keys(built.file, a), [],
                             built.file)

    def test_codes_follow_the_definition(self):
        table = assets.action_table()
        chain = lib_chain("katana")
        visual = {"moves": [], "states": []}
        self.assertEqual(assets.check_codes(visual, None, chain, table), [])
        thrown = {"basic_attack": {"style": "thrown"}, "boss": {"phases": [{"at": 100}]}}
        p = assets.check_codes(visual, thrown, chain, table)
        self.assertEqual(len(p), 1)
        self.assertIn("action code 4 (attack.ranged, a ranged basic attack (thrown))", p[0])
        self.assertEqual(assets.check_codes(visual, thrown, lib_chain("focus"), table), [])
        with_ult = {"ultimate": {"id": "big", "awakened": {"id": "bigger"}}}
        p = assets.check_codes(visual, with_ult, chain, table)
        self.assertEqual(sorted(m.split(" (")[0] for m in p), ["action code 76", "action code 78"])
        p = assets.check_codes({"moves": ["cut"], "states": ["rage"]}, None, chain, table)
        self.assertTrue(any("action code 64 (move.cut, move slot 0)" in m for m in p), p)
        self.assertTrue(any("action code 24 (state.rage, state slot 1)" in m for m in p), p)
        loop = lib_chain("katana", {"ultimate": ({}, {}), "ultimate.loop": ({}, {})})
        p = assets.check_codes(visual, {"ultimate": {"id": "u"}}, loop, table)
        self.assertTrue(any("action code 77 (ultimate.end" in m for m in p), p)

    def test_move_and_state_lists_follow_the_definition(self):
        table = assets.action_table()
        definition = {"moves": [{"id": "a", "frames": {"windup": 0.5}}, {"id": "b"}, {"id": "c"}],
                      "states": [{"name": "rage"}]}
        own = {"move.a": ({}, {}), "move.c": ({}, {})}
        chain = lib_chain("katana", own)
        p, notes = assets.check_lists({"moves": ["a", "c"], "states": ["rage"]}, definition, chain, table)
        self.assertEqual(p, [])
        self.assertEqual(len(notes), 1)
        self.assertIn("move b has no clip", notes[0])
        p, _ = assets.check_lists({"moves": ["c", "a"], "states": ["rage"]}, definition, chain, table)
        self.assertTrue(any("visual.json moves ['c', 'a']" in m for m in p), p)
        p, _ = assets.check_lists({"moves": ["a", "c"], "states": []}, definition, chain, table)
        self.assertTrue(any("visual.json states []" in m for m in p), p)
        # a move whose definition gives frames may not be left without a clip
        p, _ = assets.check_lists({"moves": ["c"], "states": ["rage"]}, definition,
                                  lib_chain("katana", {"move.c": ({}, {})}), table)
        self.assertTrue(any("move a: the definition gives frames but the chain has no move.a" in m for m in p), p)
        too_many = {"moves": ["m%d" % i for i in range(13)], "states": []}
        p, _ = assets.check_lists(too_many, None, chain, table)
        self.assertTrue(any("12 move slots" in m for m in p), p)

    def test_frames_within_one_tick(self):
        definition = {"moves": [{"id": "pounce", "frames": {"windup": 0.8, "active": 0.2, "recovery": 1.0}}]}
        ok = lib_chain("fists", {"move.pounce": clip_with_frames({"windup": 0.85, "active": 0.2, "recovery": 0.95})})
        self.assertEqual(assets.check_frames(definition, ok), [])
        late = lib_chain("fists", {"move.pounce": clip_with_frames({"windup": 0.95, "active": 0.2,
                                                                    "recovery": 0.85})})
        p = assets.check_frames(definition, late)
        self.assertTrue(any("move pounce: windup 19 ticks in t_hero (move.pounce, its first hit or cast at tick 19 of "
                            "the action), 16 in the definition's frames (0.80 s)" in m for m in p), p)
        self.assertTrue(any("recovery 17 ticks" in m for m in p), p)
        anim, timing = clip_with_frames({"windup": 0.8, "active": 0.2, "recovery": 1.0})
        del timing["frame_ticks"]
        p = assets.check_frames(definition, lib_chain("fists", {"move.pounce": (anim, timing)}))
        self.assertTrue(any("has no hit or cast marker" in m for m in p), p)
        gen = {"moves": [{"id": "pounce"}], "generated": {"moves": [{"id": "pounce", "frames": {"windup": 0.6}}]}}
        p = assets.check_frames(gen, ok)
        self.assertTrue(any("generated frames" in m for m in p), p)
        ult = {"ultimate": {"id": "u", "frames": {"windup": 1.5, "active": 0.2, "recovery": 1.5}}}
        p = assets.check_frames(ult, lib_chain("fists", {"ultimate": clip_with_frames({"windup": 1.5, "active": 0.3,
                                                                                         "recovery": 1.5})}))
        self.assertTrue(any("the ultimate: active 6 ticks" in m for m in p), p)


# ------------------------------------------------------------------ the slice kits (format-2 fixtures)

class SliceKitTest(unittest.TestCase):
    """The eight slice kits against their style libraries as written: with every per-hero clip present, every action
    code each kit can be sent resolves (the ranged basic attacks of Yugen, Hinae and Ambrose included: their styles
    have attack.ranged), the move and state lists match and each move's frames hold; with one clip removed, the check
    names it."""

    def own_clips(self, d):
        own = {}
        for m in d.get("moves", []):
            own["move." + m["id"]] = clip_with_frames(m["frames"])
        own["ultimate"] = clip_with_frames(d["ultimate"]["frames"])
        for s in d.get("states", []):
            own["state." + s["name"]] = ({}, {})
        for n in range(2, len(d["boss"]["phases"]) + 1):
            own["phase.%d" % n] = ({}, {})
        return own

    def visual(self, d):
        return {"moves": [m["id"] for m in d["moves"]], "states": [s["name"] for s in d.get("states", [])]}

    def test_the_slice_kits_resolve_with_their_clips(self):
        table = assets.action_table()
        self.assertEqual(sorted(assets.slice_ids()), sorted(SLICE_STYLES))
        for hid, style in sorted(SLICE_STYLES.items()):
            d, _ = assets.definition_of(hid)
            chain = lib_chain(style, self.own_clips(d))
            visual = self.visual(d)
            problems = assets.check_codes(visual, d, chain, table)
            lists, _ = assets.check_lists(visual, d, chain, table)
            problems += lists + assets.check_frames(d, chain)
            self.assertEqual(problems, [], hid)
            names = {n for _, n, _ in assets.required_actions(visual, d, chain, table)}
            self.assertEqual("attack.ranged" in names, d["basic_attack"]["style"] == "thrown", hid)
            self.assertIn("phase.3", names, hid)

    def test_a_missing_signature_is_named(self):
        table = assets.action_table()
        d, _ = assets.definition_of("hinae_shirasuzu")
        own = self.own_clips(d)
        del own["move.purify"]
        del own["phase.2"]
        chain = lib_chain("focus", own)
        visual = self.visual(d)
        p = assets.check_codes(visual, d, chain, table)
        self.assertTrue(any("(move.purify, move slot 2)" in m for m in p), p)
        self.assertTrue(any("(phase.2, 3 boss phases)" in m for m in p), p)
        lists, _ = assets.check_lists(visual, d, chain, table)
        self.assertTrue(any("move purify: the definition gives frames" in m for m in lists), lists)

    def test_a_built_hero_with_a_kit_passes_and_its_moved_hit_fails(self):
        from test_anim import signature_hero
        hero = signature_hero()
        clip = animlib.hero(hero).by_name["move.quick_cut"]
        from anim.writer import frames_of
        hero["moves"][0]["frames"] = dict(frames_of(clip))
        files, b = build.build_files(hero["id"], hero)
        files = dict(files)
        files.update(animlib.core().files())
        files.update(animlib.style("katana").files())
        problems, notes = assets.check_files(hero["id"], files, hero)
        self.assertEqual(problems, [])
        self.assertEqual(notes, [])
        hero["moves"][0]["frames"]["windup"] += 0.15          # the definition 3 ticks later than the clip's hit
        problems, _ = assets.check_files(hero["id"], files, hero)
        self.assertTrue(any("move quick_cut: windup 19 ticks" in m and "22 in the definition" in m
                            for m in problems), problems)


# ------------------------------------------------------------------ distinctness NOTEs

class DistinctTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from render.scene import HeroAssets

        def look(hero, archetype, hid=None):
            a = HeroAssets.build(hero)
            return distinct.Look(hid or a.id, archetype, a, a.built.ramps)
        rig = build.load_hero("test_rig")
        recoloured = copy.deepcopy(rig)
        recoloured["id"] = "t_recoloured"
        recoloured["art"]["palette"] = dict(rig["art"]["palette"], hair="#E8D27A", primary="#2E8B57",
                                            accent="#D8D8E8", secondary="#5A3A8A")
        cls.rig = look("test_rig", "samurai")
        cls.twin = look("test_rig", "samurai", "t_twin")
        cls.recoloured = look(recoloured, "samurai")
        cls.tansy = look("style_tansy", "samurai")
        cls.other = look("style_maren", "knight")

    def test_regions_are_palette_slots(self):
        distinct.measure([self.rig, self.tansy])
        self.assertEqual(self.tansy.regions[0][0], "hair")
        self.assertEqual(self.tansy.regions[0][1], self.tansy.ramps["_hex"]["hair"])
        self.assertNotIn("sash", [r[0] for r in self.rig.regions])       # the faction tint is not the hero's
        self.assertEqual(len(self.rig.masks), 2)

    def test_a_copy_gets_both_notes(self):
        notes = distinct.notes([self.rig, self.twin])
        self.assertEqual(len(notes), 2, notes)
        self.assertIn("silhouettes overlap 100%", notes[0])
        self.assertIn("two largest colour regions are both within delta-E 15", notes[1])

    def test_a_recolour_keeps_the_silhouette_note_only(self):
        notes = distinct.notes([self.rig, self.recoloured])
        self.assertEqual(len(notes), 1, notes)
        self.assertIn("silhouettes overlap", notes[0])

    def test_different_heroes_or_archetypes_get_none(self):
        self.assertEqual(distinct.notes([self.rig, self.tansy]), [])
        self.assertEqual(distinct.notes([self.twin, self.other]), [])
        distinct.measure([self.rig, self.tansy])
        self.assertLess(distinct.iou(self.rig, self.tansy), 0.6)


if __name__ == "__main__":
    unittest.main()
