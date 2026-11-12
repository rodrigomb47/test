"""G3, the animation generator: GeckoLib keyframe semantics, the writer (checked against a port of GeckoLib's own
parser), timing and markers, determinism, the quality rules on every body form, poses, recipes, the secondary bake
and the hero's own file."""
import hashlib
import json
import math
import os
import subprocess
import sys
import unittest

import numpy as np

from helpers import HEROGEN

import build
from anim import library, poses, quality, recipes, secondary, timing
from anim import skeleton as S
from anim.clip import (EASINGS, GECKO_EASINGS, TICK, Clip, Event, Key, Track, dense_track, ease, fmt_time, layered,
                       simplify)
from anim.writer import animation_file, clip_timing, frames_of
from rig import BASE_IDS
from util import HerogenError, ROOT, dumps_json


def lib_json(built):
    """The written animation and timing documents, parsed back with their key order kept."""
    files = built.files()
    paths = library.out_paths(built.file)
    a = json.loads(files[paths["animation"]], object_pairs_hook=lambda pairs: pairs)
    t = json.loads(files[paths["timing"]])
    return a, t


def as_dict(pairs):
    if isinstance(pairs, list) and pairs and isinstance(pairs[0], tuple):
        return {k: as_dict(v) for k, v in pairs}
    if isinstance(pairs, list):
        return [as_dict(v) for v in pairs]
    return pairs


# ------------------------------------------------------------------ a port of GeckoLib 4.9.3's keyframe reader

def gecko_value(x, variables):
    """MathParser for what herogen writes: a number, or a Molang expression of numbers, + - * and variables (an
    unknown variable reads 0, as MolangQueries.getVariableFor creates it)."""
    if not isinstance(x, str):
        return float(x)
    tokens = x.replace("*", " * ").replace("+", " + ").replace(" - ", " - ").split()
    terms, sign, i = [], 1.0, 0
    while i < len(tokens):
        tok = tokens[i]
        if tok in ("+", "-"):
            sign = 1.0 if tok == "+" else -1.0
            i += 1
            continue
        value = variables.get(tok, 0.0) if tok[0].isalpha() else float(tok)
        while i + 2 < len(tokens) + 1 and i + 1 < len(tokens) and tokens[i + 1] == "*":
            nxt = tokens[i + 2]
            value *= variables.get(nxt, 0.0) if nxt[0].isalpha() else float(nxt)
            i += 2
        terms.append(sign * value)
        sign = 1.0
        i += 1
    return sum(terms)


def gecko_entries(channel_pairs, variables=None):
    """BakedAnimationsAdapter.getKeyframes + addBedrockKeyframes: [(time, vector, easing)] in file order, with
    Molang components evaluated for `variables` (GeckoLib evaluates them every frame)."""
    variables = variables or {}

    def vec(v):
        return [gecko_value(x, variables) for x in v]
    out = []
    for key, value in channel_pairs:
        t = float(key)
        if t == 0 and out:
            raise ValueError("a 0 key that is not the first one")
        if isinstance(value, list) and value and isinstance(value[0], tuple):
            d = dict(value)
            if "vector" in d:
                out.append((t, vec(d["vector"]), d.get("easing", "linear")))
                continue
            if "pre" in d:
                out.append((t if t == 0 else t - 0.001, vec(d["pre"]), "linear"))
            if "post" in d:
                out.append((t, vec(d["post"]), d.get("lerp_mode", "linear")))
        else:
            out.append((t, vec(value), "linear"))
    return out


def gecko_sample(entries, t):
    """BakedAnimationsAdapter.buildKeyframeStack + AnimationController.getAnimationPointAtTick + EasingType."""
    tick = t * 20.0
    prev_t, prev_v = None, None
    acc = 0.0
    for et, ev, ez in entries:
        start_t = prev_t if prev_t is not None else 0.0
        length = (et - start_t) * 20.0
        start_v = prev_v if prev_v is not None else ev
        if tick < acc + length:
            f = (tick - acc) / length
            name = ez.lower()
            fn = next(fn for k, fn in EASINGS.items() if k.lower() == name)
            w = fn(f)
            return [a + (b - a) * w for a, b in zip(start_v, ev)]
        acc += length
        prev_t, prev_v = et, ev
    return list(entries[-1][1])


# ------------------------------------------------------------------ clip semantics

class ClipSemanticsTest(unittest.TestCase):
    def test_easings_are_geckolibs(self):
        self.assertAlmostEqual(ease("easeInSine", 0.5), 1 - math.cos(math.pi / 4))
        self.assertAlmostEqual(ease("easeOutSine", 0.5), math.sin(math.pi / 4))
        self.assertAlmostEqual(ease("easeInOutCubic", 0.25), 4 * 0.25 ** 3)
        self.assertGreater(max(ease("easeOutBack", i / 100) for i in range(101)), 1.05)    # Back overshoots
        for name in EASINGS:
            self.assertIn(name.lower(), GECKO_EASINGS)
            self.assertLess(abs(ease(name, 0.0)), 0.001)              # expo starts at 2^-10, as GeckoLib's does
        # GeckoLib's `step` is 2 steps: the value sits half-way for the second half, which would flash a third face
        # frame between two others; herogen writes jumps as pre/post keys instead
        self.assertEqual(ease("step", 0.4), 0.0)
        self.assertEqual(ease("step", 0.6), 0.5)

    def test_track_follows_geckolib(self):
        tr = Track([Key(0.5, (2, 0, 0)), Key(1.0, (4, 0, 0), "easeInQuad"), Key(1.5, (0, 0, 0), pre=(9, 9, 9))])
        self.assertEqual(tr.sample(0.0)[0], 2.0)                      # before the first key: its value
        self.assertAlmostEqual(tr.sample(0.75)[0], 2 + 2 * 0.25)      # the arriving key's easing
        self.assertAlmostEqual(tr.sample(1.2)[0], 4 + 5 * 0.2 / 0.499)  # toward the pre value (0.001 s early)
        self.assertEqual(tr.sample(1.5)[0], 0.0)                       # the jump
        self.assertEqual(tr.sample(3.0)[0], 0.0)                       # after the last key: its value

    def test_simplify_keeps_the_curve(self):
        dense = dense_track(lambda t: (10 * math.sin(2 * math.pi * t), 0, 0), 0, 1)
        simple = simplify(dense, 0.1)
        self.assertLess(len(simple.keys), len(dense.keys))
        for i in range(201):
            t = i / 200
            self.assertLess(abs(simple.sample(t)[0] - dense.sample(t)[0]), 0.1 + 1e-9)

    def test_a_jump_at_zero_is_its_post_value(self):
        """GeckoLib puts a pre key at 0 at 0 too, so a jump at 0 shows its post value from the start."""
        tr = Track([Key(0.0, (3, 0, 0), pre=(1, 0, 0)), Key(1.0, (5, 0, 0))])
        self.assertEqual(tr.sample(0.0)[0], 3.0)
        c = Clip("j", 1.0, "once")
        c.tracks[("face_ctrl", "position")] = tr
        key = animation_file([c])["animations"]["j"]["bones"]["face_ctrl"]["position"]["0.0"]
        self.assertEqual(key, {"vector": [3.0, 0.0, 0.0]})

    def test_time_format(self):
        self.assertEqual([fmt_time(t) for t in (0, 0.05, 0.1, 1, 24, 13.1)], ["0.0", "0.05", "0.1", "1.0", "24.0",
                                                                             "13.1"])


# ------------------------------------------------------------------ the writer

class WriterTest(unittest.TestCase):
    def test_geckolib_reads_what_herogen_means(self):
        """Every channel of every library animation, read back through the port of GeckoLib's parser, gives the
        values herogen sampled, at every tick; a form-scaled channel (G4) on a chibi, a teen and an adult with the
        variable HeroModel sets, and on a model that sets none (the middle-form value)."""
        from anim.clip import FORM_VAR
        for built in (library.core(), library.style("katana")):
            doc, _ = lib_json(built)
            anims = dict(dict(doc)["animations"])
            for clip in built.clips:
                bones = dict(dict(anims[clip.name])["bones"])
                for (bone, ch), tr in clip.tracks.items():
                    for d in ((0.0, -6.25, 1.75, 6.25) if tr.scaled() else (0.0,)):
                        entries = gecko_entries(dict(bones[bone])[ch], {FORM_VAR: d} if d else {})
                        for i in range(int(round(clip.length / TICK)) + 1):
                            t = i * TICK
                            got, want = gecko_sample(entries, t), tr.sample(t, d)
                            tol = 2e-3 + abs(d) * 5e-4          # the coefficient is written to 3 decimals too
                            self.assertTrue(all(abs(a - b) < tol for a, b in zip(got, want)),
                                            "%s %s.%s at %.2f (d %g): %s vs %s" % (clip.name, bone, ch, t, d, got,
                                                                                   want))

    def test_keyframe_maps_are_in_time_order(self):
        doc, _ = lib_json(library.core())
        idle = dict(dict(dict(doc)["animations"])["move.idle"])
        for bone, chans in idle["bones"]:
            for ch, keys in chans:
                times = [float(k) for k, _ in keys]
                self.assertEqual(times, sorted(times), "%s.%s" % (bone, ch))
                self.assertEqual(keys[0][0], "0.0")
        self.assertEqual(idle["animation_length"], 24)
        self.assertIs(idle["loop"], True)

    def test_action_fields_and_markers(self):
        doc, _ = lib_json(library.style("katana"))
        attack = as_dict(dict(dict(doc)["animations"])["attack.1"])
        self.assertNotIn("loop", attack)                                # play once
        # events are written 2 ticks (the action controller's blend) after their pose: writer.py
        self.assertEqual(attack["timeline"], {"0.5": "hit;vfx:slash_arc"})
        self.assertEqual(attack["sound_effects"], {"0.4": {"effect": "minecraft:entity.player.attack.sweep"}})
        face = attack["bones"]["face_ctrl"]["position"]
        jumps = [v for v in face.values() if "pre" in v]
        self.assertGreaterEqual(len(jumps), 3)
        for v in face.values():
            for x in v.get("post", v.get("vector", [])) + v.get("pre", []):
                self.assertEqual(x, int(x))

    def test_easing_names_are_geckolibs(self):
        for built in (library.core(), library.style("katana")):
            for clip in built.clips:
                for tr in clip.tracks.values():
                    for k in tr.keys:
                        self.assertIn(k.ease.lower(), GECKO_EASINGS)


# ------------------------------------------------------------------ timing and markers

class TimingTest(unittest.TestCase):
    def test_attack_hit_marker_and_frames(self):
        """The cut lands at clip time 0.40 s (tick 8); the action controller blends 2 ticks into the clip first, so
        from the action's start (the server's frame) the hit is at 0.50 s, tick 10."""
        self.assertEqual(katana_clip("attack.1").hits(), [0.4])
        _, t = lib_json(library.style("katana"))
        a = t["animations"]["attack.1"]
        self.assertEqual((a["transition_ticks"], a["ticks"], a["total_ticks"]), (2, 18, 20))
        self.assertEqual(a["hits"], [0.5])
        self.assertEqual([e for e in a["events"] if e["type"] == "hit"], [{"t": 0.5, "tick": 10, "type": "hit"}])
        self.assertEqual(a["frames"], {"windup": 0.5, "active": 0.05, "recovery": 0.45})
        self.assertEqual(a["frame_ticks"], {"windup": 10, "active": 1, "recovery": 9})
        self.assertEqual(sum(a["frame_ticks"].values()), a["total_ticks"])
        self.assertEqual(a["class"], "light")
        ci = t["animations"]["combat.idle"]
        self.assertEqual((ci["kind"], ci["transition_ticks"], ci["total_ticks"]), ("base", 4, 64))

    def test_events_fire_on_the_tick_their_pose_shows(self):
        """A port of GeckoLib 4.9.3's controller clock (AnimationController.process, processCurrentAnimation,
        adjustTick): the clock runs at speed s from the start; the controller blends until it reaches the
        transition length T, restarts it at 0 and plays the clip, and fires a keyframe event once clip tick + T >=
        the event's tick. The written hit fires on the frame on which the clip shows the cut, and that is the timing
        file's wind-up divided by the speed (4.8)."""
        built = library.style("katana")
        doc, tim = lib_json(built)
        attack = as_dict(dict(dict(doc)["animations"])["attack.1"])
        frames = tim["animations"]["attack.1"]["frame_ticks"]
        T = tim["animations"]["attack.1"]["transition_ticks"]
        hit_file_tick = round(float(next(iter(attack["timeline"]))) * 20)
        pose_hit_tick = round(katana_clip("attack.1").hits()[0] * 20)
        step = 0.05                                                 # render frames between game ticks
        for speed in (1.0, 2.0, 0.5, 1.25):
            fired = shown = running_since = None
            wall = 0.0
            while fired is None or shown is None:
                wall += step
                if running_since is None:
                    if wall * speed < T:
                        continue                                    # TRANSITIONING: no events, no clip time
                    running_since = wall                            # RUNNING from this frame, clip tick 0
                clip_tick = (wall - running_since) * speed
                if fired is None and clip_tick + T >= hit_file_tick - 1e-9:
                    fired = wall
                if shown is None and clip_tick >= pose_hit_tick - 1e-9:
                    shown = wall
            self.assertAlmostEqual(fired, shown, delta=step + 1e-9, msg="speed %s" % speed)
            self.assertAlmostEqual(shown, frames["windup"] / speed, delta=2 * step + 1e-9, msg="speed %s" % speed)
        self.assertEqual(frames["windup"], T + pose_hit_tick)
        # the bug this guards against: an event at its pose time fires T ticks before the pose shows
        self.assertEqual(hit_file_tick - pose_hit_tick, T)

    def test_timing_files_agree_with_the_animation_files(self):
        for built in [library.core()] + [library.style(s) for s in build.library_styles()]:
            files = built.files()
            p = library.out_paths(built.file)
            self.assertEqual(quality.check_timing(json.loads(files[p["animation"]]), json.loads(files[p["timing"]])),
                             [], built.file)

    def test_a_broken_marker_fails(self):
        files = library.style("katana").files()
        p = library.out_paths("_style_katana")
        anim, tim = json.loads(files[p["animation"]]), json.loads(files[p["timing"]])
        moved = json.loads(json.dumps(anim))
        moved["animations"]["attack.1"]["timeline"] = {"0.6": "hit;vfx:slash_arc"}
        self.assertTrue(quality.check_timing(moved, tim))
        self.assertTrue(any("wind-up" in m or "markers" in m for m in quality.check_timing(moved, tim)))
        early = library.style("katana").by_name["attack.1"].copy()
        early.events = [Event(0.3, "instruction", "hit")]
        self.assertTrue(any("wind-up minimum" in m for m in quality.check_markers(early)))
        ex = recipes.Expander(library.pose_library(), library.reference_skeleton())
        with self.assertRaises(HerogenError):
            ex.expand("x", {"length": 1.0, "secondary": False, "keys": [{"t": 0.42, "marker": "hit"}]})
        with self.assertRaises(HerogenError):          # on the last tick: GeckoLib ends the clip before firing it
            ex.expand("x", {"length": 1.0, "secondary": False, "keys": [{"t": 1.0, "sound": "minecraft:x"}]})
        last = library.style("katana").by_name["attack.1"].copy()
        last.events = [Event(0.9, "sound", "minecraft:x")]
        self.assertTrue(any("never fire" in m for m in quality.check_markers(last)))

    def test_frames_of_several_hits(self):
        c = Clip("move.flurry", 1.5, "once", {"kind": "action", "class": "light"})
        c.events = [Event(0.5, "instruction", "hit"), Event(0.7, "instruction", "hit"), Event(0.6, "sound", "x")]
        self.assertEqual(frames_of(c), {"windup": 0.6, "active": 0.25, "recovery": 0.75})      # + the 2-tick blend
        self.assertEqual(clip_timing(c)["frame_ticks"], {"windup": 12, "active": 5, "recovery": 15})

    def test_move_frames_come_from_the_signature(self):
        hero = signature_hero()
        gen = timing.generated(hero)
        self.assertEqual(gen["notes"], [])
        move = gen["moves"][0]
        self.assertEqual((move["id"], move["anim"], move["file"], move["class"]),
                         ("quick_cut", "move.quick_cut", "t_signature", "heavy"))
        self.assertEqual(move["frames"], {"windup": 0.95, "active": 0.05, "recovery": 0.9})
        self.assertEqual(move["transition_ticks"], 2)
        hero["moves"].append({"id": "missing", "form": "melee"})
        self.assertTrue(any("no animation move.missing" in n for n in timing.generated(hero)["notes"]))


def signature_hero():
    """An in-memory hero (a mannequin on the katana style) with one signature move."""
    return {
        "format": 2, "id": "t_signature", "gender": "female",
        "art": {"base": "female", "form": "teen", "parts": []},
        "moves": [{"id": "quick_cut", "form": "dash", "class": "heavy"}],
        "animations": {
            "style": "katana",
            "signatures": {"move.quick_cut": {
                "length": 1.8, "class": "heavy", "bones": ["waist", "chest", "neck", "arm_*", "forearm_*", "hand_*"],
                "keys": [{"t": 0.0, "pose": "katana.stance"},
                         {"t": 0.6, "pose": "katana.windup", "ease": "easeOutCubic", "face": "focused"},
                         {"t": 0.85, "pose": "katana.cut", "ease": "easeInQuad", "marker": "hit"},
                         {"t": 1.1, "pose": "katana.follow", "face": "none"},
                         {"t": 1.8, "pose": "katana.stance"}]}}}}


# ------------------------------------------------------------------ determinism

def library_digest():
    return {k: hashlib.sha256(v).hexdigest() for k, v in sorted(build.library_files().items())}


class DeterminismTest(unittest.TestCase):
    def test_library_builds_byte_identically_twice(self):
        a = library_digest()
        library.clear_caches()
        b = library_digest()
        self.assertEqual(a, b)
        self.assertIn("src/main/resources/assets/factionclash/animations/hero/_core.animation.json", a)

    def test_identical_across_processes(self):
        code = ("import sys, json, hashlib; sys.path.insert(0, %r); import build;"
                "print(json.dumps({k: hashlib.sha256(v).hexdigest() for k, v in sorted(build.library_files().items())}))"
                ) % HEROGEN
        runs = []
        for seed in ("1", "2"):
            env = dict(os.environ, PYTHONHASHSEED=seed)
            out = subprocess.run([sys.executable, "-c", code], env=env, capture_output=True, text=True, check=True)
            runs.append(json.loads(out.stdout))
        self.assertEqual(runs[0], runs[1])
        self.assertEqual(runs[0], library_digest())

    def test_committed_libraries_are_fresh(self):
        self.assertEqual(build.check_library_files(build.library_files(), ROOT), [])


# ------------------------------------------------------------------ the library on every form

def core_clip(name):
    return library.core().by_name[name]


def katana_clip(name):
    return library.style("katana").by_name[name]


class LibraryRulesTest(unittest.TestCase):
    def test_the_quality_rules_pass_on_all_six_bases(self):
        core = library.core()
        self.assertEqual(quality.check_built(core, core.by_name.get), [])
        kat = library.style("katana")
        self.assertEqual(quality.check_built(kat, lambda n: kat.by_name.get(n) or core.by_name.get(n)), [])

    def test_generated_set(self):
        self.assertEqual(sorted(core_clip(n).name for n in ("move.idle", "move.walk", "move.run")),
                         ["move.idle", "move.run", "move.walk"])
        self.assertEqual((core_clip("move.walk").length, core_clip("move.run").length,
                          core_clip("move.idle").length), (1.0, 0.6, 24.0))
        kat = library.style("katana").by_name
        self.assertTrue({"attack.1", "combat.idle"} <= set(kat))
        # G4: the core's restyled reactions, re-expanded over the katana stance
        self.assertEqual(sorted(set(kat) - {"attack.1", "combat.idle"}), sorted(library.restyled_recipes()))

    def test_one_file_serves_every_form(self):
        """Every keyed bone is a rig bone, so it has the same name and a pivot from the rig on every base."""
        rig_names = quality.rig_bone_names()
        for built in (library.core(), library.style("katana")):
            for clip in built.clips:
                self.assertEqual(set(clip.bones()) - rig_names, set(), clip.name)
        for base in BASE_IDS:
            sk = S.for_base(base)
            for clip in library.core().clips:
                self.assertTrue(all(sk.has(b) for b in clip.bones()), (base, clip.name))

    def test_walk_phase_on_every_form(self):
        walk = core_clip("move.walk")
        for base in BASE_IDS:
            sk = S.for_base(base)
            for t, right_forward in ((0.0, True), (0.5, False)):
                w = sk.world(walk.sample(t))
                foot = {s: sk.apply(w, "foot_" + s, sk.pivot["foot_" + s])[2] for s in "rl"}
                hand = {s: sk.apply(w, "hand_" + s, sk.pivot["hand_" + s])[2] for s in "rl"}
                # forward is -z: the right foot leads at 0 with the right hand back, and the other way at 0.5
                self.assertEqual(foot["r"] < foot["l"], right_forward, (base, t))
                self.assertEqual(hand["r"] > hand["l"], right_forward, (base, t))

    def test_run_has_a_flight_phase(self):
        run = core_clip("move.run")
        sk = S.for_base("teen_f")
        lows = [sk.lowest_sole(run.sample(i * TICK)) for i in range(13)]
        self.assertGreater(max(lows), 0.4)
        self.assertGreater(min(lows), -0.6)

    def test_idle_breathes_every_three_seconds(self):
        tr = core_clip("move.idle").tracks[("chest", "position")]
        for t in (0.0, 0.7, 2.2):
            for k in range(1, 8):
                self.assertAlmostEqual(tr.sample(t)[1], tr.sample(t + 3 * k)[1], delta=0.02)
        self.assertAlmostEqual(tr.sample(1.5)[1] - tr.sample(0.0)[1], 0.14, delta=0.02)

    def test_idle_ear_twitches(self):
        idle = core_clip("move.idle")
        tw = idle.meta["twitches"]
        self.assertGreaterEqual(len(tw), 3)
        self.assertEqual({s for _, s in tw}, {"r", "l"})
        for t, side in tw:
            tr = idle.tracks[("ear_" + side, "rotation")]
            before, peak = tr.sample(t - TICK), tr.sample(t)
            self.assertLess(max(abs(x) for x in before), 3.0)
            self.assertGreater(max(abs(x) for x in peak), 20.0)          # up in one tick, ~24 degrees
            self.assertLess(max(abs(x) for x in tr.sample(t + 6 * TICK)), 3.0)
        gaps = [b - a for (a, _), (b, _) in zip(tw, tw[1:])]
        self.assertTrue(all(0.25 <= g <= 6.05 for g in gaps), gaps)

    def test_idle_leaves_the_head_to_the_procedural_look(self):
        for clip in library.core().clips:
            self.assertNotIn("head", clip.bones())

    def test_attack_is_upper_body_and_settles_on_combat_idle(self):
        attack, ci = katana_clip("attack.1"), katana_clip("combat.idle")
        lower = {"root", "body", "leg_r", "leg_l", "shin_r", "shin_l", "foot_r", "foot_l"}
        self.assertEqual(set(attack.bones()) & lower, set())
        self.assertEqual(attack.meta["settle_to"], "combat.idle")
        for bc, tr in attack.tracks.items():
            goal = ci.tracks[bc].sample(0.0) if bc in ci.tracks else (0.0, 0.0, 0.0)
            self.assertTrue(all(abs(a - b) < 0.01 for a, b in zip(tr.keys[-1].v, goal)), bc)

    def test_the_cut_sweeps_down_through_the_hit(self):
        attack, ci = katana_clip("attack.1"), katana_clip("combat.idle")
        sk = S.for_base("adult_f")

        def hand(t):
            w = sk.world(layered(ci, attack, t, t % ci.length))
            return sk.apply(w, "hand_r", sk.pivot["hand_r"])

        head_y = sk.env["head_y"]
        self.assertGreater(hand(0.3)[1], head_y - 3)            # cocked up by the head at the end of the wind-up
        self.assertLess(hand(0.4)[1], hand(0.3)[1] - 8)          # thrown down by the hit
        self.assertLess(hand(0.4)[2], hand(0.3)[2] - 4)          # and forward (-z)


class QualityRulesTest(unittest.TestCase):
    def test_joint_limit_signs(self):
        c = Clip("bad", 1.0, "once")
        c.track("forearm_r").set(0, (20, 0, 0))                 # the elbow bent backward
        c.track("shin_l").set(0, (-20, 0, 0))                   # the knee bent forward
        c.track("waist").set(0, (0, 50, 0))
        found = " ".join(quality.check_joints(c))
        self.assertIn("forearm_r flex", found)
        self.assertIn("shin_l flex", found)
        self.assertIn("waist twist", found)
        good = Clip("good", 1.0, "once")
        good.track("forearm_r").set(0, (-90, 0, 0))
        good.track("shin_l").set(0, (90, 0, 0))
        self.assertEqual(quality.check_joints(good), [])

    def test_open_loop_and_drifting_feet_fail(self):
        c = core_clip("move.idle").copy("broken")
        c.tracks[("root", "position")] = Track([Key(0, (0, 0, 0)), Key(12, (1.2, 0, 0)), Key(24, (0.4, 0, 0))])
        msgs = quality.check_keys(c) + quality.check_feet(c)
        self.assertTrue(any("does not close" in m for m in msgs))
        self.assertTrue(any("a foot moves" in m for m in msgs))

    def test_face_slides_and_odd_frames_fail(self):
        c = Clip("f", 1.0, "once")
        c.track("face_ctrl", "position").put(Key(0, (0, 0, 0)))
        c.track("face_ctrl", "position").put(Key(0.5, (5, 0, 0)))          # a slide, not a jump
        c.track("face_ctrl", "position").put(Key(0.8, (2.5, 0, 0), pre=(5, 0, 0)))
        msgs = " ".join(quality.check_face(c))
        self.assertIn("slides", msgs)
        self.assertIn("not a frame number", msgs)

    def test_root_motion_fails(self):
        c = Clip("slide", 1.0, "once")
        c.track("root", "position").set(0, (0, 0, 0))
        c.track("root", "position").set(1, (0, 0, -3))
        self.assertTrue(quality.check_root(c))


# ------------------------------------------------------------------ poses and recipes

class PoseTest(unittest.TestCase):
    def test_sign_conventions(self):
        sk = S.for_base("adult_f")

        def at(bone, rot, probe, point):
            w = sk.world({bone: {"rotation": rot}})
            return sk.apply(w, probe, point)

        hand = sk.pivot["hand_r"]
        self.assertLess(at("arm_r", (-90, 0, 0), "hand_r", hand)[2], -8)       # forward swing: negative rx
        self.assertLess(at("arm_r", (0, 0, 30), "hand_r", hand)[0], hand[0] - 3)  # raise sideways: +rz on the right
        self.assertGreater(at("shin_r", (90, 0, 0), "foot_r", sk.pivot["foot_r"])[2], 5)   # knee flex: +rx
        self.assertLess(at("forearm_r", (-90, 0, 0), "hand_r", hand)[2], -5)    # elbow flex: -rx
        chest_front = np.array([0.0, sk.pivot["chest"][1], -3.0])
        self.assertLess(at("body", (0, 30, 0), "chest", chest_front)[0], -1)    # +ry turns toward the right (-x)

    def test_mirror_extends_and_fixes(self):
        lib = library.pose_library()
        relaxed = lib.get("stance.relaxed")
        self.assertEqual(relaxed.rotation["arm_l"], (0.0, 0.0, -8.0))
        windup = lib.get("katana.windup")
        self.assertEqual(windup.rotation["leg_r"], lib.get("katana.stance").rotation["leg_r"])
        chans = poses.resolve(lib.get("katana.stance"), library.form_skeletons())
        for s in "rl":
            leg, shin, foot = (chans[b + s]["rotation"] for b in ("leg_", "shin_", "foot_"))
            self.assertAlmostEqual(foot[0], -(leg[0] + shin[0]))
        for sk in library.form_skeletons():
            self.assertLess(abs(sk.lowest_sole(chans)), 0.3)

    def test_unknown_pose_and_field_fail(self):
        lib = library.pose_library()
        with self.assertRaises(HerogenError):
            lib.get("no.such.pose")
        with self.assertRaises(HerogenError):
            poses.parse("x", {"bones": {}, "colour": 1}, lib.get)


class RecipeTest(unittest.TestCase):
    def setUp(self):
        extra = {"t.a": {"bones": {"chest": [0, 0, 0]}}, "t.b": {"bones": {"chest": [10, 0, 0]}}}
        self.ex = recipes.Expander(library.pose_library().with_specs(extra, "test"), library.reference_skeleton(),
                                   forms=library.form_skeletons())

    def test_overshoot_and_settle(self):
        c = self.ex.expand("t", {"length": 1.0, "secondary": False, "kind": "base", "keys": [
            {"t": 0, "pose": "t.a"}, {"t": 0.5, "pose": "t.b", "overshoot": 0.2, "ease": "easeOutCubic"},
            {"t": 0.8, "pose": "t.b", "settle": 0.1}]})
        tr = c.tracks[("chest", "rotation")]
        self.assertAlmostEqual(tr.sample(0.5)[0], 12.0)            # 20% past the pose
        self.assertAlmostEqual(tr.sample(0.6)[0], 10.0)            # back on it
        self.assertEqual(tr.keys[1].ease, "easeOutCubic")

    def test_face_and_marker_keys(self):
        c = self.ex.expand("t", {"length": 1.0, "secondary": False, "class": "light", "keys": [
            {"t": 0.0, "pose": "t.a"}, {"t": 0.45, "face": "angry", "marker": "hit;sfx:minecraft:x;vfx:y"},
            {"t": 0.8, "face": {"eyes": 2}}, {"t": 1.0, "pose": "t.a"}]})
        fc = c.tracks[("face_ctrl", "position")]
        self.assertEqual(fc.sample(0.44), (0.0, 0.0, 0.0))
        self.assertEqual(fc.sample(0.45), (5.0, 5.0, 1.0))           # angry: eyes 4, mouth 4, cheeks 0 (+1 each)
        self.assertEqual(fc.sample(0.9), (3.0, 0.0, 0.0))
        self.assertEqual(fc.keys[-1].v, (0.0, 0.0, 0.0))             # the action settles on "no override"
        self.assertEqual(sorted((e.kind, e.value) for e in c.events),
                         [("instruction", "hit"), ("instruction", "vfx:y"), ("sound", "minecraft:x")])
        with self.assertRaises(HerogenError):
            self.ex.expand("t", {"length": 1.0, "keys": [{"t": 0.2, "face": {"eyes": 12}}]})
        with self.assertRaises(HerogenError):
            self.ex.expand("t", {"length": 1.0, "speed": 2})

    def test_layers_add_or_replace(self):
        base = {"length": 3.0, "loop": True, "secondary": False, "keys": [{"t": 0, "pose": "t.b"}, {"t": 3, "pose": "t.b"}]}
        added = self.ex.expand("t", dict(base, layers=[{"primitive": "breathe"}]))
        replaced = self.ex.expand("t", dict(base, layers=[{"primitive": "breathe", "add": False}]))
        self.assertAlmostEqual(added.tracks[("chest", "rotation")].sample(1.5)[0], 10 - 0.9, places=2)
        self.assertAlmostEqual(replaced.tracks[("chest", "rotation")].sample(1.5)[0], -0.9, places=2)
        with self.assertRaises(HerogenError):
            self.ex.expand("t", dict(base, length=2.0, layers=[{"primitive": "breathe"}]))   # 2 s is not 3 s periods


# ------------------------------------------------------------------ the secondary bake

class SecondaryTest(unittest.TestCase):
    def test_a_chain_lags_and_swings_through(self):
        """Turn the body 30 degrees in 0.1 s and hold: the tail stays behind, then swings past and settles."""
        c = Clip("turn", 2.0, "once")
        c.track("body").put(Key(0, (0, 0, 0)))
        c.track("body").put(Key(0.1, (0, 30, 0)))
        sk = library.reference_skeleton()
        groups = [g for g in secondary.load_groups(sk) if g.name == "tail"]
        offs = secondary.simulate(c, sk, groups)["tail_1"]
        yaw = [o[1] for o in offs]
        self.assertLess(min(yaw[:6]), -3.0)                       # left behind the turn (opposite sign)
        self.assertGreater(max(yaw[6:20]), 0.5)                   # swings through
        self.assertLess(abs(yaw[-1]), 0.5)                         # and settles

    def test_baked_keys_are_sparse_and_loops_close(self):
        walk = core_clip("move.walk")
        for b in ("tail_3", "sleeve_r", "skirt_f"):
            tr = walk.tracks[(b, "rotation")]
            if b != "skirt_f":                  # spring only (the skirt panels also carry the walk's own keys)
                self.assertTrue(all(abs(k.t * 10 - round(k.t * 10)) < 1e-6 for k in tr.keys), b)   # every 2 ticks
                self.assertLessEqual(len(tr.keys), 11)
            self.assertEqual(tr.keys[0].v, tr.keys[-1].v)

    def test_authored_twitch_is_not_smeared(self):
        idle = core_clip("move.idle")
        t, side = idle.meta["twitches"][0]
        v = idle.tracks[("ear_" + side, "rotation")].sample(t)
        self.assertAlmostEqual(abs(v[2]), 24.0, delta=1.0)

    def test_sleeves_hang(self):
        """A wide sleeve keeps hanging when the forearm is raised (hang 1.0)."""
        ci = katana_clip("combat.idle")
        sk = library.reference_skeleton()
        w = sk.world(ci.sample(0.0))
        r, _ = w["sleeve_r"]
        down = r @ np.array([0.0, -1.0, 0.0])
        self.assertLess(down[1], -0.9)

    def test_an_action_starts_on_its_base(self):
        """attack.1 starts where combat.idle starts on every channel, baked chain bones included: the action's bake
        runs over the base as authored, so the base's own follow-through is not added a second time."""
        attack, ci = katana_clip("attack.1"), katana_clip("combat.idle")
        for bc, tr in attack.tracks.items():
            goal = ci.tracks[bc].sample(0.0) if bc in ci.tracks else (0.0, 0.0, 0.0)
            self.assertLess(max(abs(a - b) for a, b in zip(tr.sample(0.0), goal)), 0.5, bc)

    def test_a_double_bake_fails_the_start_rule(self):
        """Sabotage: baking the attack over the baked combat.idle (the G3 bug) hangs the sleeve twice at the start,
        and the start rule says so."""
        from unittest import mock
        kat = library.style("katana")
        with open(library.style_recipe_path("katana")) as f:
            recipe = json.load(f)["animations"]["attack.1"]
        ex = recipes.Expander(library.pose_library(), library.reference_skeleton(), kat.by_name.get, "combat.idle",
                              library.form_skeletons())
        with mock.patch.object(secondary, "authored_view", lambda clip: clip):
            bad = ex.expand("attack.1", recipe)
        msgs = quality.check_start(bad, kat.by_name["combat.idle"])
        self.assertTrue(any("sleeve_r.rotation starts" in m for m in msgs), msgs)
        self.assertEqual(quality.check_start(kat.by_name["attack.1"], kat.by_name["combat.idle"]), [])

    def test_an_action_bakes_only_the_chains_it_moves(self):
        """The swing keys the upper body, so it bakes the sleeves, ears, hair, ponytail and cape, never the skirt
        panels or the tail: those keep the walk's follow-through when a hero swings while walking (GeckoLib
        overrides per bone)."""
        bones = set(katana_clip("attack.1").bones())
        self.assertTrue({"sleeve_r", "ear_r", "tail_hair_3", "cape_1"} <= bones, bones)
        self.assertEqual({b for b in bones if b.startswith(("skirt", "tail_"))} - {"tail_hair_1", "tail_hair_2",
                                                                                   "tail_hair_3"}, set())

    def test_a_raised_sleeve_stays_around_the_forearm(self):
        """With the forearm raised overhead the bell sleeve does not hang 85 degrees off it (its open end would face
        the viewer as a disc): hang_fade keeps it within the lag of the forearm through the wind-up, and it hangs
        again once the forearm comes down."""
        attack, ci = katana_clip("attack.1"), katana_clip("combat.idle")
        sk = library.reference_skeleton()

        def angles(t):
            w = sk.world(layered(ci, attack, t, t % ci.length))
            fa = w["forearm_r"][0] @ np.array([0.0, -1.0, 0.0])
            sl = w["sleeve_r"][0] @ np.array([0.0, -1.0, 0.0])
            return math.degrees(math.acos(np.clip(fa @ sl, -1, 1))), -sl[1]

        for t in (0.15, 0.2, 0.25, 0.3):
            self.assertLess(angles(t)[0], 35.0, t)                 # wind-up: around the raised forearm
        self.assertGreater(angles(0.7)[1], 0.95)                    # recovery: hanging again
        self.assertGreater(angles(0.0)[1], 0.99)                    # the stance: hanging

    def test_part_chains_go_to_visual_json(self):
        files, _ = build.build_files("test_rig")
        v = json.loads(files[build.out_paths("test_rig")["visual"]])
        self.assertEqual(v["animation_chain"], ["test_rig", "_style_katana", "_core"])
        chains = {tuple(s["chain"]): s for s in v["secondary"]}
        self.assertEqual(chains[("tail_1", "tail_2", "tail_3", "tail_4", "tail_5")]["stiffness"], 0.3)


# ------------------------------------------------------------------ a hero's own file

class HeroFileTest(unittest.TestCase):
    def test_every_file_of_every_chain_is_written(self):
        files = build.library_files()
        for hid in build.hero_ids():
            hero = build.load_hero(hid)
            own, _ = build.build_files(hid)
            for f in library.chain_of(hero):
                path = library.out_paths(f)["animation"]
                self.assertTrue(path in files or path in own, (hid, f))
                json.loads((files.get(path) or own[path]).decode())

    def test_signature_hero_file(self):
        hero = signature_hero()
        built = library.hero(hero)
        self.assertEqual(sorted(built.by_name), ["move.quick_cut"])
        clip = built.by_name["move.quick_cut"]
        self.assertEqual(clip.meta["settle_to"], "combat.idle")
        core, kat = library.core(), library.style("katana")

        def lookup(n):
            return built.by_name.get(n) or kat.by_name.get(n) or core.by_name.get(n)

        self.assertEqual(quality.check_built(built, lookup, own_file=True), [])
        doc = animation_file(built.clips)
        self.assertEqual(doc["animations"]["move.quick_cut"]["timeline"], {"0.95": "hit"})    # 0.85 + the blend

    def test_own_file_budget(self):
        hero = signature_hero()
        r = hero["animations"]["signatures"]["move.quick_cut"]
        hero["animations"]["signatures"] = {"move.m%d" % i: r for i in range(25)}
        built = library.hero(hero)
        msgs = quality.check_built(built, lambda n: library.style("katana").by_name.get(n), own_file=True,
                                   feet=False)
        self.assertTrue(any("at most 24" in m for m in msgs))


class RenderPoseTest(unittest.TestCase):
    def test_a_pose_position_moves_the_render(self):
        from render import raster
        from geo import GeoModel
        import io
        from PIL import Image
        files, _ = build.build_files("_fallback")
        geo = GeoModel(json.loads(files[build.out_paths("_fallback")["geo"]]))
        tex = np.array(Image.open(io.BytesIO(files[build.out_paths("_fallback")["texture"]])).convert("RGBA"))
        bounds = (-12, 12, -2, 60)
        a = raster.render(geo, tex, raster.View(scale=2), bounds=bounds)
        b = raster.render(geo, tex, raster.View(scale=2), bounds=bounds, pose={"root": {"position": (0, 4, 0)}})
        top = [int(np.argmax(im[:, :, 3].any(axis=1))) for im in (a, b)]
        self.assertEqual(top[0] - top[1], 8)                      # 4 px up at scale 2


if __name__ == "__main__":
    unittest.main()
