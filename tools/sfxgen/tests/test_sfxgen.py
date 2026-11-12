"""sfxgen: determinism, the measurements and the checks (a clicky sound must fail), the recipe format, variants,
loops, sounds.json and the index, markers, and the committed catalogue's consistency."""
import hashlib
import json
import math
import os
import subprocess
import sys
import tempfile
import unittest

import numpy as np

from helpers import SFXGEN

import analyze
import build
import check
import codec
import dsp
import recipe
import vanilla
import voice

SR = 44100


def tiny_set(extra=None):
    """A small catalogue file (one generated sound with variants, one vanilla reference, one alias)."""
    data = {
        "format": 1, "set": "cue", "title": "test",
        "cues": {},
        "sounds": {
            "ping": {"kind": "hit", "subtitle": "Ping", "variants": 2, "vary": {"pitch": [-1, 1]},
                     "layers": [{"type": "bell", "partials": "crystal", "f0": 1800, "dur": 0.4, "decay": 0.2},
                                {"type": "noise", "colour": "pink", "dur": 0.2,
                                 "filter": {"mode": "bp", "f": [800, 3000], "q": 1.5}, "env": {"perc": [0.005, 0.18]},
                                 "gain": -8}]},
            "hiss": {"kind": "windup", "subtitle": "Hiss",
                     "vanilla": [{"event": "minecraft:entity.cat.hiss", "pitch": 1.1}]},
            "ping.low": {"kind": "hit", "subtitle": "Ping", "alias": [{"sound": "ping", "pitch": 0.8}]},
        },
    }
    if extra:
        data["sounds"].update(extra)
    return data


def write_set(folder, data):
    path = os.path.join(folder, data["set"] + ".sfx.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f)
    return path


class Fixture(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.src = self.tmp.name
        write_set(self.src, tiny_set())
        self.cat = recipe.load_all(self.src)

    def tearDown(self):
        self.tmp.cleanup()


class DeterminismTest(Fixture):
    def test_render_and_encode_twice_byte_identical(self):
        a = build.render_all(self.cat, self.src, jobs=1)
        b = build.render_all(recipe.load_all(self.src), self.src, jobs=1)
        self.assertEqual([r[4] for r in a], [r[4] for r in b])
        self.assertTrue(all(len(r[4]) > 1000 for r in a))

    def test_another_process_gives_the_same_bytes(self):
        """Seeds come from SHA-256 of the event name, never from Python's randomised hash()."""
        code = ("import sys; sys.path.insert(0, %r); import recipe, codec, hashlib; c = recipe.load_all(%r); "
                "s = c.lookup('ping', 'cue'); x, sr = recipe.Renderer(c).final(s, 1); "
                "print(hashlib.sha256(codec.encode_ogg(x, sr)).hexdigest())") % (SFXGEN, self.src)
        env = dict(os.environ, PYTHONHASHSEED="12345")
        out = subprocess.run([sys.executable, "-c", code], capture_output=True, text=True, env=env, check=True)
        s = self.cat.lookup("ping", "cue")
        x, sr = recipe.Renderer(self.cat).final(s, 1)
        self.assertEqual(out.stdout.strip(), hashlib.sha256(codec.encode_ogg(x, sr)).hexdigest())

    def test_variants_differ(self):
        r = recipe.Renderer(self.cat)
        s = self.cat.lookup("ping", "cue")
        a, _ = r.final(s, 0)
        b, _ = r.final(s, 1)
        self.assertFalse(len(a) == len(b) and np.allclose(a, b))


class MeasureTest(unittest.TestCase):
    def setUp(self):
        t = np.arange(SR) / SR
        self.sine = 0.5 * np.sin(2 * math.pi * 440 * t) * np.exp(-t * 2)

    def test_cut_and_phase_jump_are_clicks(self):
        y = self.sine.copy()
        y[int(0.4006 * SR):] = 0.0  # one layer stops dead while another goes on
        y += 0.05 * np.sin(2 * math.pi * 200 * np.arange(SR) / SR)
        found = analyze.clicks(y, SR)
        self.assertEqual(len(found), 1)
        self.assertAlmostEqual(found[0], 0.4006, delta=0.001)
        t = np.arange(SR) / SR
        z = 0.5 * np.sin(2 * math.pi * 440 * t + (t > 0.5) * 2.0) * np.exp(-t * 2)
        self.assertTrue(analyze.clicks(z, SR))

    def test_clean_sounds_and_textures_are_not_clicks(self):
        self.assertEqual(analyze.clicks(self.sine, SR), [])
        saw = dsp.drive(0.5 * dsp.osc(np.full(SR, 55.0), SR, "saw"), 2.5)
        self.assertEqual(analyze.clicks(saw, SR), [])
        hit = np.zeros(SR)
        hit[2000:2000 + 4410] = np.random.default_rng(3).standard_normal(4410) * np.exp(-np.arange(4410) / 400.0)
        self.assertEqual(analyze.clicks(hit, SR), [], "an onset from silence is an attack, not a click")

    def test_loudness_orders_and_normalise_hits_the_target(self):
        quiet = 0.05 * self.sine
        self.assertLess(analyze.loudness(quiet, SR)[0], analyze.loudness(self.sine, SR)[0] - 20)
        y = dsp.normalize(self.sine, SR, -20.0)
        self.assertAlmostEqual(analyze.loudness(y, SR)[0], -20.0, delta=0.4)
        self.assertLessEqual(analyze.db(analyze.true_peak(y)), -0.9)

    def test_limiter_keeps_peaks_under_the_ceiling(self):
        x = np.concatenate([self.sine * 0.2, np.full(50, 0.99), self.sine * 0.2])
        y = dsp.limit(x * 3, SR, -1.0)
        self.assertLessEqual(float(np.max(np.abs(y))), 10 ** (-1 / 20.0) + 1e-9)

    def test_normalize_keeps_the_true_peak_under_the_ceiling(self):
        """A burst at a quarter of the rate, 45 degrees off its samples, peaks 3 dB between them: limiting the
        samples once leaves the true peak over the ceiling, so normalize must limit again."""
        n = int(0.05 * SR)
        burst = np.sin(2 * math.pi * (SR / 4.0) * np.arange(n) / SR + math.pi / 4) * np.hanning(n)
        x = np.concatenate([0.05 * self.sine[:SR // 4], burst, 0.05 * self.sine[:SR // 4]])
        y = dsp.normalize(x, SR, -8.0)
        self.assertLessEqual(analyze.db(analyze.true_peak(y)), -1.5 + 0.05)

    def test_dc_block_and_fades(self):
        y = dsp.fade(dsp.dc_block(self.sine + 0.2, SR), SR)
        self.assertLess(abs(float(np.mean(y))), 1e-3)
        self.assertEqual(y[0], 0.0)
        self.assertEqual(y[-1], 0.0)


class CheckCatchesTest(Fixture):
    def _measure(self, x):
        return analyze.measure(x, SR)

    def _errors(self, x, sound=None):
        s = sound or self.cat.lookup("ping", "cue")
        rep = check.Report()
        m = self._measure(x)
        m["samples"] = len(x)
        check.check_audio(s, 0, x, SR, m, 1, rep)
        return rep.errors

    def test_the_rendered_sound_passes(self):
        s = self.cat.lookup("ping", "cue")
        x, sr = recipe.Renderer(self.cat).final(s, 0)
        y, sr2 = codec.decode(codec.encode_ogg(x, sr), sr)
        rep = check.Report()
        m = analyze.measure(y, sr2)
        m["samples"] = len(y)
        check.check_audio(s, 0, y, sr2, m, 1, rep)
        self.assertEqual(rep.errors, [])

    def test_a_clicky_sound_fails(self):
        """The classic recipe bug: a layer that stops dead (hard edge, no envelope) while another goes on."""
        cat = recipe.Catalogue([recipe.SoundSet("x.sfx.json", tiny_set({"clicky": {
            "kind": "hit", "subtitle": "c", "layers": [
                {"type": "tone", "freq": 610, "dur": 0.3, "hard": True},
                {"type": "tone", "freq": 300, "dur": 0.6, "env": {"swell": [0.3, 0.3]}, "gain": -6}]}}))])
        s = cat.lookup("clicky", "cue")
        x, sr = recipe.Renderer(cat).final(s, 0)
        y, sr2 = codec.decode(codec.encode_ogg(x, sr), sr)
        errs = self._errors(y, s)
        self.assertTrue(any("clicks inside" in e for e in errs), errs)
        found = analyze.clicks(x, sr)
        self.assertEqual(len(found), 1)
        self.assertAlmostEqual(found[0], 0.3 + recipe.LEAD_SAMPLES / sr, delta=0.001, msg="where the layer stops")

    def test_a_dead_stop_at_the_end_fails(self):
        t = np.arange(SR // 2) / SR
        y = 0.5 * np.sin(2 * math.pi * 330 * t) * np.exp(-t * 2)
        y[:100] *= np.linspace(0, 1, 100)
        y = np.concatenate([y[: int(0.3 * SR) + 7], np.zeros(2000)])  # content stops at a loud sample
        errs = self._errors(y)
        self.assertTrue(any("no fade-out" in e for e in errs), errs)

    def test_hard_edges_fail(self):
        s = self.cat.lookup("ping", "cue")
        x, _ = recipe.Renderer(self.cat).final(s, 0)
        y = x[: len(x) // 3]  # stops dead
        errs = self._errors(y)
        self.assertTrue(any("edge sample" in e or "no fade-out" in e for e in errs), errs)
        t = np.arange(SR // 4) / SR
        z = 0.5 * np.sin(2 * math.pi * 440 * t + 1.3)  # starts on a step
        z[-200:] *= np.linspace(1, 0, 200)
        errs = self._errors(z)
        self.assertTrue(any("edge sample" in e or "no fade-in" in e for e in errs), errs)

    def test_clipping_dc_and_loudness_fail(self):
        t = np.arange(SR // 2) / SR
        loud = dsp.fade(np.clip(3 * np.sin(2 * math.pi * 300 * t), -1, 1), SR)
        errs = self._errors(loud)
        self.assertTrue(any("true peak" in e for e in errs), errs)
        self.assertTrue(any("loudness" in e for e in errs), errs)
        off = dsp.fade(0.1 * np.sin(2 * math.pi * 300 * t), SR) + 0.02
        off[0] = off[-1] = 0.0
        errs = self._errors(off)
        self.assertTrue(any("DC offset" in e for e in errs), errs)

    def test_sub_only_sound_is_inaudible(self):
        t = np.arange(SR // 2) / SR
        sub = dsp.fade(0.6 * np.sin(2 * math.pi * 35 * t), SR, 0.01, 0.05)
        errs = self._errors(sub)
        self.assertTrue(any("small speakers" in e for e in errs), errs)


class FormatTest(unittest.TestCase):
    def test_bad_recipes_are_refused(self):
        def problems(extra):
            return recipe.validate_file(tiny_set(extra))
        self.assertEqual(problems(None), [])
        self.assertTrue(problems({"x": {"kind": "hit", "subtitle": "x", "layers": [{"type": "kazoo"}]}}))
        self.assertTrue(problems({"x": {"kind": "hit", "subtitle": "x", "layers": [{"type": "tone"}],
                                        "vanilla": [{"event": "minecraft:entity.cat.hiss"}]}}))
        self.assertTrue(problems({"x": {"kind": "hit", "layers": [{"type": "tone"}]}}))  # no subtitle
        self.assertTrue(problems({"x": {"kind": "boom", "subtitle": "x", "layers": [{"type": "tone"}]}}))
        self.assertTrue(problems({"x": {"kind": "hit", "subtitle": "x", "variants": 2, "each": [{}],
                                        "layers": [{"type": "tone"}]}}))
        self.assertTrue(problems({"Bad Name": {"kind": "hit", "subtitle": "x", "layers": [{"type": "tone"}]}}))
        self.assertTrue(problems({"x": {"kind": "hit", "subtitle": "x", "vanilla": [{"event": "entity.cat.hiss"}]}}))

    def test_event_names(self):
        self.assertEqual(recipe.event_id("cue", "telegraph.heavy"), "hero.cue.telegraph.heavy")
        self.assertEqual(recipe.event_id("natsuho_komezaki", "bell"), "hero.natsuho_komezaki.bell")
        self.assertEqual(recipe.event_id("natsuho_komezaki", "voice"), "hero.voice.natsuho_komezaki")
        self.assertEqual(recipe.file_stem("cue", "telegraph.heavy"), "hero/cue/telegraph_heavy")

    def test_vary_spreads_variants_evenly(self):
        cat = recipe.Catalogue([recipe.SoundSet("x.sfx.json", tiny_set({"many": {
            "kind": "hit", "subtitle": "m", "variants": 4, "vary": {"layers.0.f0": [1000, 2000]},
            "layers": [{"type": "bell", "f0": 1500}]}}))])
        s = cat.lookup("many", "cue")
        vals = sorted(recipe.variant_spec(s, k)[0]["layers"][0]["f0"] for k in range(4))
        self.assertEqual(vals, [1125.0, 1375.0, 1625.0, 1875.0])

    def test_each_overrides_per_variant(self):
        cat = recipe.Catalogue([recipe.SoundSet("x.sfx.json", tiny_set({"syl": {
            "kind": "voice", "subtitle": "v", "variants": 2, "each": [{"layers.0.vowel": "a"}, {"layers.0.vowel": "o",
                                                                                                "pitch": 2}],
            "layers": [{"type": "vox", "vowel": "e", "dur": 0.08}]}}))])
        s = cat.lookup("syl", "cue")
        spec0, sh0 = recipe.variant_spec(s, 0)
        spec1, sh1 = recipe.variant_spec(s, 1)
        self.assertEqual((spec0["layers"][0]["vowel"], sh0), ("a", 0.0))
        self.assertEqual((spec1["layers"][0]["vowel"], sh1), ("o", 2.0))

    def test_loops_are_seamless(self):
        x = dsp.noise(SR * 3, np.random.default_rng(1), "pink")
        y = recipe.make_loop(x, SR, 2.0, 0.3)
        self.assertEqual(len(y), 2 * SR)
        self.assertAlmostEqual(float(y[0]), float(x[2 * SR]), places=9)  # the loop start continues its own end
        self.assertEqual(analyze.clicks(np.concatenate([y[-2000:], y[:2000]]), SR), [])


class OutputsTest(Fixture):
    def test_sounds_json_entries(self):
        sj = build.sounds_json(self.cat, [])
        self.assertEqual([e["name"] for e in sj["hero.cue.ping"]["sounds"]],
                         ["factionclash:hero/cue/ping_1", "factionclash:hero/cue/ping_2"])
        self.assertEqual(sj["hero.cue.hiss"]["sounds"], [{"name": "minecraft:entity.cat.hiss", "type": "event",
                                                          "pitch": 1.1}])
        self.assertEqual(sj["hero.cue.ping.low"]["sounds"], [{"name": "factionclash:hero.cue.ping", "type": "event",
                                                              "pitch": 0.8}])
        self.assertEqual(sj["hero.cue.ping"]["subtitle"], "subtitles.factionclash.hero.cue.ping")

    def test_index_carries_what_s2_needs(self):
        rendered = build.render_all(self.cat, self.src, jobs=1)
        idx = build.index_json(self.cat, rendered)
        e = idx["events"]["factionclash:hero.cue.ping"]
        for k in ("plays", "category", "range", "pitch", "volume", "subtitle", "variants", "length", "kind"):
            self.assertIn(k, e)
        self.assertEqual(idx["events"]["factionclash:hero.cue.hiss"]["refs"], ["minecraft:entity.cat.hiss"])

    def test_ranges_are_what_the_game_gives(self):
        """A vanilla reference reaches 16 blocks x its play volume (whatever its kind's default), an alias its
        target's range; giving either a range is refused."""
        cat = recipe.Catalogue([recipe.SoundSet("x.sfx.json", tiny_set({
            "horn": {"kind": "sting", "subtitle": "h", "volume": [2.0, 2.0],
                     "vanilla": [{"event": "minecraft:item.goat_horn.sound.5"}]}}))])
        self.assertEqual(cat.lookup("hiss", "cue").range, 16, "a windup's default 24 does not apply to vanilla")
        self.assertEqual(cat.lookup("horn", "cue").range, 32)
        self.assertEqual(cat.lookup("ping.low", "cue").range, cat.lookup("ping", "cue").range)
        self.assertTrue(recipe.validate_file(tiny_set({"far": {
            "kind": "windup", "subtitle": "f", "range": 48, "vanilla": [{"event": "minecraft:entity.cat.hiss"}]}})))
        self.assertTrue(recipe.validate_file(tiny_set({"loud": {
            "kind": "hit", "subtitle": "l", "volume": [1.5, 1.5], "layers": [{"type": "tone"}]}})))

    def test_same_subtitle_text_shares_one_key(self):
        """The overlay merges equal components only: a sound and a companion that say the same thing share a key."""
        cat = recipe.Catalogue([recipe.SoundSet("x.sfx.json", tiny_set({
            "ping.echo": {"kind": "hit", "subtitle": "Ping", "layers": [{"type": "tone", "dur": 0.2}]},
            "pong": {"kind": "hit", "subtitle": "Pong", "layers": [{"type": "tone", "dur": 0.2}]}}))])
        sj = build.sounds_json(cat, [])
        self.assertEqual(sj["hero.cue.ping.echo"]["subtitle"], sj["hero.cue.ping"]["subtitle"])
        self.assertEqual(sj["hero.cue.ping.low"]["subtitle"], "subtitles.factionclash.hero.cue.ping")
        self.assertEqual(sj["hero.cue.pong"]["subtitle"], "subtitles.factionclash.hero.cue.pong")
        subs = build.subtitles(cat)
        self.assertEqual(sorted(subs.values()).count("Ping"), 1)
        self.assertEqual(set(subs), {e["subtitle"] for e in sj.values()})

    def test_hit_at_reaches_the_index(self):
        cat = recipe.Catalogue([recipe.SoundSet("x.sfx.json", tiny_set({
            "swell": {"kind": "telegraph", "subtitle": "s", "hit_at": 0.3,
                      "layers": [{"type": "tone", "dur": 0.3, "env": {"swell": [0.28, 0.02]}}]}}))])
        idx = build.index_json(cat, [])
        self.assertEqual(idx["events"]["factionclash:hero.cue.swell"]["hit_at"], 0.3)
        self.assertNotIn("hit_at", idx["events"]["factionclash:hero.cue.ping"])
        self.assertTrue(recipe.validate_file(tiny_set({"bad": {
            "kind": "telegraph", "subtitle": "b", "hit_at": 0.3, "vanilla": [{"event": "minecraft:entity.cat.hiss"}]}})))


def hero_set(voice_loudness=-26.0, line=None, windup_plays=None):
    """A minimal hero set: a voice, a move whose wind-up is `bark` (plays as given)."""
    v = {"talk": "voice", "rate": 2, "melody": [0, 2], "fallback_blip": "harp",
         "hurt": {"event": "minecraft:entity.player.hurt"}, "death": {"event": "minecraft:entity.player.death"}}
    if line is not None:
        v["line_loudness"] = line
    bark = {"kind": "windup", "subtitle": "Bark", "layers": [{"type": "tone", "dur": 0.3, "freq": 300}]}
    if windup_plays:
        bark["plays"] = windup_plays
    return {"format": 1, "set": "test_hero", "title": "t",
            "hero": {"voice": v, "moves": {"bite": {"class": "heavy", "windup": "bark"}},
                     "phases": {"2": "bark", "3": "bark"}},
            "sounds": {"voice": {"kind": "voice", "subtitle": "Talks", "loudness": voice_loudness, "variants": 4,
                                 "layers": [{"type": "vox", "f0": 300, "dur": 0.08, "vowel": "a"}],
                                 "each": [{"layers.0.vowel": v} for v in ("a", "e", "o", "u")]},
                       "bark": bark}}


class HeroRulesTest(unittest.TestCase):
    def test_a_server_slot_needs_a_server_sound(self):
        """The server plays a wind-up; a client sound there would reach nobody else."""
        for plays, bad in (("client", True), ("server", False)):
            cat = recipe.Catalogue([recipe.SoundSet("test_hero.sfx.json", hero_set(windup_plays=plays))])
            rep = check.Report()
            check.check_hero(cat, cat.sets["test_hero"], rep, None)
            self.assertEqual(any("moves.bite.windup" in e and "plays: client" in e for e in rep.errors), bad,
                             rep.errors)

    def test_a_spoken_line_must_sit_at_its_loudness(self):
        """Players hear lines: a line 3 dB off its voice.line_loudness fails, on target passes."""
        cat = recipe.Catalogue([recipe.SoundSet("test_hero.sfx.json", hero_set())])
        lm, _t = voice.line_loudness(recipe.Renderer(cat), cat.sets["test_hero"])
        self.assertGreater(lm, -26.0 + 3, "a line measures well above one syllable")
        for line, bad in ((lm + 3.0, True), (lm, False)):
            cat = recipe.Catalogue([recipe.SoundSet("test_hero.sfx.json", hero_set(line=round(line, 2)))])
            rep = check.Report()
            check.check_voices(cat, rep)
            self.assertEqual(any("spoken line" in e for e in rep.errors), bad, rep.errors)

    def test_bare_marker_ids_are_vanilla(self):
        """A bare id reads as Minecraft reads it (minecraft:), as hero files write look sounds."""
        with tempfile.TemporaryDirectory() as tmp:
            with open(os.path.join(tmp, "h.hero.json"), "w", encoding="utf-8") as f:
                json.dump({"look": {"sound": "item.shield.block"}, "x": {"sound": "item.shield.blok"},
                           "k": {"marker": "hit;sfx:factionclash:hero.cue.nothing"}}, f)
            saved = check.ANIM_SOURCES
            check.ANIM_SOURCES = [tmp]
            try:
                rep = check.Report()
                check.check_markers(recipe.load_all(), rep)
            finally:
                check.ANIM_SOURCES = saved
        self.assertFalse(any("item.shield.block " in e for e in rep.errors), rep.errors)
        self.assertTrue(any("minecraft:item.shield.blok" in e for e in rep.errors), rep.errors)
        self.assertTrue(any("hero.cue.nothing" in e for e in rep.errors), rep.errors)


class CatalogueTest(unittest.TestCase):
    """The committed catalogue (herosrc/sfx) is consistent: no structural errors, every reference resolves, vanilla
    ids exist in 1.21.1, every anim marker names a real sound, and sounds.json matches the catalogue."""

    @classmethod
    def setUpClass(cls):
        cls.cat = recipe.load_all()

    def test_catalogue_rules(self):
        rep = check.Report()
        check.check_catalogue(self.cat, rep)
        check.check_markers(self.cat, rep)
        self.assertEqual(rep.errors, [])

    def test_vanilla_ids_are_checked(self):
        ids, _src = vanilla.sound_ids()
        self.assertIsNotNone(ids)
        self.assertIn("minecraft:entity.cat.hiss", ids)
        bad = recipe.Catalogue([recipe.SoundSet("x.sfx.json", tiny_set({"oops": {
            "kind": "hit", "subtitle": "o", "vanilla": [{"event": "minecraft:entity.cat.roar"}]}}))])
        rep = check.Report()
        check.check_catalogue(bad, rep)
        self.assertTrue(any("entity.cat.roar" in e for e in rep.errors), rep.errors)

    def test_sounds_json_is_current(self):
        from paths import SOUNDS_JSON
        want = build.dumps(build.sounds_json(self.cat, []))
        with open(SOUNDS_JSON, encoding="utf-8") as f:
            self.assertEqual(f.read(), want)

    def test_every_slice_hero_has_a_set(self):
        heroes = ["natsuho_komezaki", "yugen_kagaribi", "benika_akagane", "hinae_shirasuzu", "kagenori_tsurumine",
                  "tobimaru_usugumo", "ilsabet_frostmere", "ambrose_nettlewick"]
        for h in heroes:
            self.assertIn(h, self.cat.sets)
            self.assertIn("voice", self.cat.sets[h].sounds)
            self.assertIn("ultimate", [m["class"] for m in self.cat.sets[h].hero["moves"].values()])


if __name__ == "__main__":
    unittest.main()
