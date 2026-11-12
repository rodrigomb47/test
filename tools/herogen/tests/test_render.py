"""G2, the preview renderer: geometry against the G1 conventions, vanilla entity lighting, cut-out, tint and glow,
the perspective camera, golden images of a synthetic fixture, the animation file reader and the controller
emulation, HeroFace's frame choice and blinks, the scene rules read from visual.json, GIF writing and the CLI."""
import io
import json
import os
import tempfile
import unittest

import numpy as np
from PIL import Image

from helpers import HEROGEN

import build
import face as F
from anim import library
from anim.clip import TICK
from geo import GeoModel
from render import animfile, gif, raster
from render.face_sim import CLOSED, HALF, FaceSim
from render.scene import HeroAssets
from rig import load_rig
from uvpack import to_file_uv

GOLDEN = os.path.join(HEROGEN, "tests", "golden")
UPDATE = os.environ.get("HEROGEN_UPDATE_GOLDEN") == "1"


# ------------------------------------------------------------------ a synthetic fixture (independent of the art)

def _cube(origin, size, uv_at, faces=("north", "south", "east", "west", "up", "down"), **extra):
    """A cube with a vanilla skin net of 2 texels per pixel at uv_at."""
    sx, sy, sz = (int(round(2 * s)) for s in size)
    u0, v0 = uv_at
    layout = {"up": (u0 + sz, v0, sx, sz), "down": (u0 + sz + sx, v0, sx, sz), "east": (u0, v0 + sz, sz, sy),
              "north": (u0 + sz, v0 + sz, sx, sy), "west": (u0 + sz + sx, v0 + sz, sz, sy),
              "south": (u0 + 2 * sz + sx, v0 + sz, sx, sy)}
    uv = {}
    for f in faces:
        rect = layout[f]
        if rect[2] <= 0 or rect[3] <= 0:
            continue
        a, b = to_file_uv(f, rect)
        uv[f] = {"uv": a, "uv_size": b}
    d = {"origin": list(origin), "size": list(size), "uv": uv}
    d.update(extra)
    return d


def fixture():
    """(GeoModel, texture, glow mask): a body, an arm on a rotated bone, a cube with its own rotation, an inflated
    cube, a zero-thickness plane, a tinted bone and a cut-out hole."""
    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "body", "parent": "root", "pivot": [0, 6, 0], "cubes": [_cube((-4, 0, -2), (8, 12, 4), (0, 0))]},
        {"name": "arm", "parent": "body", "pivot": [5, 11, 0], "rotation": [10, 0, 25],
         "cubes": [_cube((4, 4, -1), (2, 7, 2), (0, 40))]},
        {"name": "horn", "parent": "body", "pivot": [0, 12, 0],
         "cubes": [_cube((-1, 12, -1), (2, 5, 2), (20, 40), rotation=[30, 20, -15], pivot=[0, 12, 0]),
                   _cube((-6, 2, -2.5), (2, 2, 1), (40, 40), inflate=0.25)]},
        {"name": "plane", "parent": "body", "pivot": [0, 8, -2.05],
         "cubes": [_cube((-3, 7, -2.05), (6, 3, 0), (0, 64), faces=("north",))]},
        {"name": "sash", "parent": "body", "pivot": [0, 5, 0], "cubes": [_cube((-4.5, 5, -2.5), (9, 1, 5), (0, 80))]},
    ]
    doc = {"format_version": "1.12.0", "minecraft:geometry": [{"description": {
        "identifier": "geometry.test", "texture_width": 128, "texture_height": 128}, "bones": bones}]}
    geo = GeoModel(json.loads(json.dumps(doc)))
    yy, xx = np.mgrid[0:128, 0:128]
    tex = np.zeros((128, 128, 4), dtype=np.uint8)
    tex[..., 0] = (xx * 2) % 256
    tex[..., 1] = (yy * 2) % 256
    tex[..., 2] = np.where((xx // 2 + yy // 2) % 2, 200, 60)
    tex[..., 3] = 255
    tex[10:14, 12:14, 3] = 0                                    # a hole in the body's front (cut-out)
    tex[64:70, 0:12, :3] = (240, 240, 250)                      # the plane: light
    tex[80:92, 0:30, :3] = (200, 200, 200)                      # the sash: grey (tinted)
    glow = np.zeros_like(tex)
    glow[40:44, 20:28] = (255, 220, 90, 255)                    # the horn glows
    return geo, tex, glow


def _png_bytes(arr):
    buf = io.BytesIO()
    Image.fromarray(arr, "RGBA").save(buf, format="PNG")
    return buf.getvalue()


class GoldenTest(unittest.TestCase):
    """The renderer against committed images of the synthetic fixture (HEROGEN_UPDATE_GOLDEN=1 rewrites them)."""

    CASES = {
        "fixture_front": dict(view=dict(yaw=0.0, pitch=0.0, scale=4)),
        "fixture_34_pose": dict(view=dict(yaw=-34.0, pitch=12.0, scale=4),
                                pose={"arm": {"rotation": (-40.0, 10.0, 0.0)},
                                      "body": {"rotation": (0.0, 15.0, 0.0), "position": (0.0, 1.0, 0.0)}}),
        "fixture_back_tint_glow": dict(view=dict(yaw=150.0, pitch=20.0, scale=4), tint=True, glow=True,
                                       brightness=0.3),
        "fixture_perspective": dict(view=dict(yaw=-60.0, pitch=25.0, scale=4, distance=40.0,
                                              target=(0.0, 8.0, 0.0))),
    }

    @classmethod
    def setUpClass(cls):
        cls.geo, cls.tex, cls.glow = fixture()

    def render(self, case):
        c = self.CASES[case]
        view = raster.View(**c["view"])
        return raster.render(self.geo, self.tex, view, pose=c.get("pose"),
                             tint={"sash": (74, 118, 196)} if c.get("tint") else None,
                             glow=self.glow if c.get("glow") else None, brightness=c.get("brightness", 1.0))

    def test_golden_images(self):
        for case in sorted(self.CASES):
            with self.subTest(case=case):
                img = self.render(case)
                path = os.path.join(GOLDEN, case + ".png")
                if UPDATE or not os.path.exists(path):
                    os.makedirs(GOLDEN, exist_ok=True)
                    with open(path, "wb") as f:
                        f.write(_png_bytes(img))
                    if not UPDATE:
                        self.fail("%s: golden image written, run again" % case)
                want = np.array(Image.open(path).convert("RGBA"))
                self.assertEqual(img.shape, want.shape)
                diff = np.abs(img.astype(int) - want.astype(int)).max(axis=2)
                # identical here; a different NumPy may flip a pixel on an edge, never more than 0.3% of them
                self.assertLessEqual(int((diff > 2).sum()), max(2, img.shape[0] * img.shape[1] * 3 // 1000), case)

    def test_render_is_deterministic(self):
        a, b = self.render("fixture_34_pose"), self.render("fixture_34_pose")
        self.assertTrue(np.array_equal(a, b))


class LightingTest(unittest.TestCase):
    """minecraft_mix_light with vanilla's two world light directions (Lighting.DIFFUSE_LIGHT_0/1)."""

    def test_values_per_direction(self):
        self.assertAlmostEqual(raster.vanilla_light((0, 1, 0)), 1.0)
        self.assertAlmostEqual(raster.vanilla_light((0, -1, 0)), 0.4)
        z = 0.7 / np.sqrt(1.53) * 0.6 + 0.4
        x = 0.2 / np.sqrt(1.53) * 0.6 + 0.4
        for n in ((0, 0, 1), (0, 0, -1)):
            self.assertAlmostEqual(raster.vanilla_light(n), z)
        for n in ((1, 0, 0), (-1, 0, 0)):
            self.assertAlmostEqual(raster.vanilla_light(n), x)
        self.assertAlmostEqual(z, 0.7395, places=3)
        self.assertAlmostEqual(x, 0.4970, places=3)

    def test_entity_turn(self):
        """Body yaw 0 faces south; the model's front (render -z) then points to +z; yaw 90 faces west (-x)."""
        np.testing.assert_allclose(raster.entity_matrix(0) @ np.array([0, 0, -1.0]), [0, 0, 1], atol=1e-12)
        np.testing.assert_allclose(raster.entity_matrix(90) @ np.array([0, 0, -1.0]), [-1, 0, 0], atol=1e-12)

    def test_flat_cube_fix_is_geckolibs(self):
        """RenderUtil.fixInvertedFlatCube: x flips when y or z size is 0, y when x or z is 0, z when x or y is 0."""
        np.testing.assert_allclose(raster.fix_inverted_flat_cube((6, 3, 0), (-0.3, -0.4, -0.8)), (0.3, 0.4, -0.8))
        np.testing.assert_allclose(raster.fix_inverted_flat_cube((0, 3, 2), (-0.3, -0.4, -0.8)), (-0.3, 0.4, 0.8))
        np.testing.assert_allclose(raster.fix_inverted_flat_cube((2, 3, 4), (-0.3, -0.4, -0.8)), (-0.3, -0.4, -0.8))

    def test_every_pixel_is_its_texel_times_its_light(self):
        """Over the whole fixture: colour = round(texel x vanilla light of its face x tint), with the light taken
        from the world normal (so it does not change when the camera goes round)."""
        geo, tex, _ = fixture()
        tint = {"sash": (74, 118, 196)}
        seen = {}
        for yaw in (0.0, -34.0, 120.0):
            fr = raster.render(geo, tex, raster.View(yaw, 15.0, 3), tint=tint, frame=True)
            on = fr.ids >= 0
            ys, xs = np.nonzero(on)
            for y, x in zip(ys, xs):
                q = fr.quads[fr.ids[y, x]]
                u, v = fr.texels[y, x]
                mult = raster.quad_light(q) * np.array(tint.get(q.bone, (255, 255, 255))) / 255.0
                want = np.clip(np.rint(tex[v, u, :3] * mult), 0, 255)
                self.assertTrue(np.array_equal(fr.rgba[y, x, :3], want), (yaw, q.bone, q.face))
                key = (q.bone, q.face, tuple(np.round(q.pts[0], 4)))
                seen.setdefault(key, set()).add(round(raster.quad_light(q), 6))
        self.assertTrue(all(len(v) == 1 for v in seen.values()))
        body_front = [v for k, v in seen.items() if k[:2] == ("body", "north")]
        self.assertEqual(body_front, [{round(raster.vanilla_light((0, 0, 1)), 6)}])

    def test_flat_planes_part_from_the_surface_behind_them(self):
        """What GeckoLib does to a zero-thickness plane in front of a face: pitched down, the plane's world normal has
        a negative y that fixInvertedFlatCube makes positive, so the plane is lit like a face looking UP while the
        body front behind it gets darker (the hero face planes, artgen.md G2)."""
        geo, _, _ = fixture()
        for pitch, same in ((0.0, True), (-20.0, True), (20.0, False)):
            qs = raster.quads(geo, pose={"body": {"rotation": (pitch, 0.0, 0.0)}})
            front = next(q for q in qs if q.bone == "body" and q.face == "north")
            plane = next(q for q in qs if q.bone == "plane")
            self.assertEqual(abs(raster.quad_light(front) - raster.quad_light(plane)) < 1e-9, same, pitch)
        self.assertGreater(raster.quad_light(plane), raster.quad_light(front) + 0.25)

    def test_turned_entity_lights_its_sides(self):
        geo, tex, _ = fixture()
        q = next(q for q in raster.quads(geo) if q.bone == "body" and q.face == "north")
        self.assertAlmostEqual(raster.quad_light(q, 0.0), 0.7395, places=3)
        self.assertAlmostEqual(raster.quad_light(q, 180.0), 0.7395, places=3)
        self.assertAlmostEqual(raster.quad_light(q, 90.0), 0.4970, places=3)

    def test_cutout_and_brightness(self):
        geo, tex, glow = fixture()
        fr = raster.render(geo, tex, raster.View(0, 0, 4), frame=True)
        hole = [(y, x) for y, x in zip(*np.nonzero(fr.ids >= 0)) if tuple(fr.texels[y, x]) == (12, 10)]
        self.assertFalse(hole, "a texel with alpha 0 is never drawn")
        dark = raster.render(geo, tex, raster.View(0, 0, 4), brightness=0.25)
        on = fr.rgba[..., 3] > 0
        self.assertLess(dark[on][:, :3].mean(), fr.rgba[on][:, :3].mean() * 0.3)

    def test_glow_ignores_the_light_map(self):
        geo, tex, glow = fixture()
        view = raster.View(-20, 10, 4)
        fr = raster.render(geo, tex, view, brightness=0.25, frame=True)
        lit = raster.render(geo, tex, view, brightness=0.25, glow=glow)
        horn = np.zeros(fr.ids.shape, dtype=bool)
        for y, x in zip(*np.nonzero(fr.ids >= 0)):
            u, v = fr.texels[y, x]
            horn[y, x] = glow[v, u, 3] > 0
        self.assertTrue(horn.any())
        self.assertGreater(lit[horn][:, 0].mean(), fr.rgba[horn][:, 0].mean() + 60)
        self.assertTrue(np.array_equal(lit[~horn], fr.rgba[~horn]))


class GeometryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.a = HeroAssets.committed("test_rig")
        cls.geo, cls.tex = cls.a.geo, cls.a.tex

    def test_front_view_height(self):
        img = raster.render(self.geo, self.tex, raster.View(scale=4), hidden=["p2_halo", "weapon_r", "weapon_l"],
                            pad=0)
        ys = np.argwhere(img[:, :, 3] > 0)[:, 0]
        self.assertAlmostEqual((ys.max() - ys.min() + 1) / 4.0, 59.75, delta=1.0,
                               msg="feet to ear tips (rig v3 adult 52 + the cat ears)")
        colours = {tuple(p[:3]) for p in img.reshape(-1, 4) if p[3]}
        self.assertGreater(len(colours), 20)

    def test_hidden_bones_hide_their_children(self):
        full = raster.render(self.geo, self.tex, raster.View(scale=2), hidden=["weapon_r", "weapon_l"], pad=0,
                             bounds=(-12, 12, 0, 64))
        bare = raster.render(self.geo, self.tex, raster.View(scale=2), hidden=["weapon_r", "weapon_l", "halo"],
                             pad=0, bounds=(-12, 12, 0, 64))
        self.assertGreater(int((full[:, :, 3] > 0).sum()), int((bare[:, :, 3] > 0).sum()))
        self.assertIn("p2_halo", raster.hidden_set(self.geo, ["halo"]))

    def test_sheathed_blade_points_back_and_down(self):
        qs = [q for q in raster.quads(self.geo) if q.bone == "weapon_back"]
        pts = np.concatenate([q.pts for q in qs])
        lowest = pts[pts[:, 1].argmin()]
        highest = pts[pts[:, 1].argmax()]
        self.assertGreater(lowest[2], highest[2], "the scabbard end is behind the hilt (+z is back)")

    def test_views(self):
        """yaw -90 shows the hero's left side (file +x) to the camera, 180 its back."""
        left = raster.View(-90, 0, 1).to_view(np.array([raster.to_render((10, 0, 0))]))[0]
        front = raster.View(0, 0, 1).to_view(np.array([raster.to_render((0, 0, -10))]))[0]
        back = raster.View(180, 0, 1).to_view(np.array([raster.to_render((0, 0, 10))]))[0]
        for p in (left, front, back):
            self.assertLess(p[2], -9.9, "nearer the camera (smaller view z)")

    def test_perspective_approaches_orthographic(self):
        hidden = self.a.hidden()
        ortho = raster.render(self.geo, self.tex, raster.View(-30, 10, 3), hidden=hidden, pad=0)
        far = raster.render(self.geo, self.tex, raster.View(-30, 10, 3, distance=1e6), hidden=hidden, pad=0)
        self.assertEqual(ortho.shape, far.shape)
        self.assertLess(int(((ortho[..., 3] > 0) != (far[..., 3] > 0)).sum()), 0.002 * ortho[..., 3].size)
        near = raster.View(0, 0, 3, distance=40.0, target=(0, 30, 0))
        fr = raster.render(self.geo, self.tex, near, hidden=hidden, frame=True)
        # close up, the front of the head (nearer) is drawn larger than its back
        qs = raster.quads(self.geo, hidden)
        head = [q for q in qs if q.bone == "head"]
        front = next(q for q in head if q.face == "north")
        back = next(q for q in head if q.face == "south")

        def width(q):
            v = near.to_view(q.pts)
            w = near.w(v[:, 2])
            x = v[:, 0] / w
            return x.max() - x.min()
        self.assertGreater(width(front), width(back) * 1.1)
        self.assertTrue((fr.ids >= 0).any())


# ------------------------------------------------------------------ animation files and the controllers

class AnimFileTest(unittest.TestCase):
    def test_reader_matches_the_generator(self):
        """Every channel of the written libraries, read back by the renderer, gives what the generator built."""
        for built in (library.core(), library.style("katana")):
            doc = json.loads(built.files()[library.out_paths(built.file)["animation"]])
            clips = animfile.parse_file(doc)
            self.assertEqual(sorted(clips), sorted(built.by_name))
            for clip in built.clips:
                got = clips[clip.name]
                self.assertEqual((got.length, got.loop), (clip.length, clip.loop), clip.name)
                for i in range(int(round(clip.length / TICK)) + 1):
                    t = i * TICK
                    want, have = clip.sample(t), got.sample(t)
                    for bone, chans in want.items():
                        for ch, v in chans.items():
                            np.testing.assert_allclose(have[bone][ch], v, atol=2e-3,
                                                       err_msg="%s %s.%s %.2f" % (clip.name, bone, ch, t))

    def test_events_keep_their_file_time(self):
        chain = HeroAssets.committed("test_rig").chain
        attack = chain.get("attack.1")
        hits = [e.t for e in attack.events if e.kind == "instruction" and e.value == "hit"]
        self.assertEqual(hits, [0.5])                               # clip 0.40 + the 2-tick blend
        self.assertEqual(chain.file_of("attack.1"), "_style_katana")
        self.assertEqual(chain.file_of("move.walk"), "_core")

    def test_playback_blends_then_plays(self):
        chain = HeroAssets.committed("test_rig").chain
        pb = animfile.Playback(chain, "combat.idle", "attack.1", action_at=0.3)
        base, act = chain.get("combat.idle"), chain.get("attack.1")
        self.assertIsNone(pb.action_phase(0.2))
        self.assertEqual(pb.pose(0.2)["arm_r"]["rotation"], base.sample(0.2)["arm_r"]["rotation"])
        self.assertEqual(pb.action_phase(0.35), "blend")
        start, first = base.sample(0.3)["arm_r"]["rotation"], act.sample(0.0)["arm_r"]["rotation"]
        np.testing.assert_allclose(pb.pose(0.35)["arm_r"]["rotation"],
                                   [(a + b) / 2 for a, b in zip(start, first)], atol=1e-9)
        np.testing.assert_allclose(pb.pose(0.3 + 0.1 + 0.25)["arm_r"]["rotation"],
                                   act.sample(0.25)["arm_r"]["rotation"])
        # a bone the action does not key keeps the base's motion
        unkeyed = [b for b in base.bones() if b not in act.bones()]
        self.assertTrue(unkeyed)
        b0 = unkeyed[0]
        self.assertEqual(pb.pose(0.6)[b0], base.sample(0.6)[b0])
        self.assertEqual(pb.action_phase(0.3 + 0.1 + 0.9 + 0.05), "done")

    def test_the_hit_fires_on_the_frame_of_the_cut(self):
        chain = HeroAssets.committed("test_rig").chain
        pb = animfile.Playback(chain, "combat.idle", "attack.1", action_at=0.3)
        fired = [at for at, kind, v in pb.events(0.0, 2.0) if v == "hit"]
        cut = 0.3 + pb.blend + library.style("katana").by_name["attack.1"].hits()[0]   # the pose of the cut
        self.assertEqual(len(fired), 1)
        self.assertAlmostEqual(fired[0], 0.3 + 0.5)
        self.assertAlmostEqual(pb.windup_until(), fired[0])
        self.assertAlmostEqual(cut, fired[0])
        # the frame at 0.80 s shows it, the one before does not (a film labels the events of (t - dt, t])
        self.assertIn("hit", [v for _, _, v in pb.events(0.75 + 1e-6, 0.80 + 1e-6)])
        self.assertNotIn("hit", [v for _, _, v in pb.events(0.70 + 1e-6, 0.75 + 1e-6)])

    def test_authored_face_rule(self):
        f = animfile.authored_face
        self.assertEqual(f({"face_ctrl": {"position": (9.0, 1.0, 0.0)}}), (8, 0, None))
        self.assertEqual(f({"face_ctrl": {"position": (4.96, 2.4, 1.03)}}), (4, None, 0))
        self.assertEqual(f({"face_ctrl": {"position": (0.5, 0.0, 0.0)}}), (None, None, None))
        self.assertEqual(f({}), (None, None, None))


# ------------------------------------------------------------------ HeroFace

def _visual(profile="normal", moods=None):
    from face import BLINK_OVER, BLINK_PROFILES
    p = BLINK_PROFILES[profile]
    table = {m: {"eyes": e, "mouth": mo, "cheeks": c} for m, (e, mo, c) in F.MOOD_FRAMES.items()}
    table.update(moods or {})
    return {"face": {"moods": table, "blink": {"profile": profile, "interval": p["interval"], "ticks": p["ticks"],
                                                "double_chance": 0.15, "over_eyes": BLINK_OVER}}}


class FaceSimTest(unittest.TestCase):
    def test_a_blink_is_half_closed_closed_half(self):
        fs = FaceSim(_visual(), "hero#0")
        start = fs.blinks_until(400)[0][0]
        seq = [fs.frames((start + k) * TICK)[0] for k in range(-1, 5)]
        self.assertEqual(seq, [0, HALF, CLOSED, CLOSED, HALF, 0])

    def test_intervals_follow_the_profile(self):
        for profile in ("normal", "nervous", "calm", "sleepy"):
            v = _visual(profile)
            fs = FaceSim(v, "x#1")
            lo, hi = v["face"]["blink"]["interval"]
            blinks = fs.blinks_until(20 * 600)
            self.assertGreater(len(blinks), 10)
            prev_end = 0
            doubles = 0
            for s, e in blinks:
                gap = s - prev_end
                if gap == 2:
                    doubles += 1
                else:
                    self.assertGreaterEqual(gap, int(round(lo / TICK)), profile)
                    self.assertLessEqual(gap, int(round(hi / TICK)), profile)
                self.assertEqual(e - s, sum(v["face"]["blink"]["ticks"]))
                prev_end = e
            self.assertGreater(doubles, 0, profile)

    def test_seeded_and_never_in_step(self):
        a1 = FaceSim(_visual(), "a#0").blinks_until(2000)
        a2 = FaceSim(_visual(), "a#0").blinks_until(2000)
        b = FaceSim(_visual(), "b#0").blinks_until(2000)
        self.assertEqual(a1, a2)
        self.assertNotEqual(a1, b)

    def test_no_blink_over_happy_eyes_or_in_a_windup(self):
        happy = FaceSim(_visual(), "h#0", "happy")
        calm = FaceSim(_visual(), "h#0", "calm")
        ticks = range(0, 2000)
        self.assertTrue(any(calm.frames(k * TICK)[0] == CLOSED for k in ticks))
        self.assertFalse(any(happy.frames(k * TICK)[0] in (HALF, CLOSED) for k in ticks))
        self.assertFalse(any(calm.frames(k * TICK, blink_ok=False)[0] in (HALF, CLOSED) for k in ticks))

    def test_priorities(self):
        fs = FaceSim(_visual(), "p#0", "angry")
        self.assertEqual(fs.frames(0.0), F.MOOD_FRAMES["angry"])
        self.assertEqual(fs.frames(0.0, {"face_ctrl": {"position": (7.0, 0.0, 2.0)}}), (6, 4, 1))
        self.assertEqual(fs.frames(0.0, defeated=True)[:2], (CLOSED, 0))
        self.assertIn(fs.frames(0.0, talking=True)[1], (2, 3, 0))


# ------------------------------------------------------------------ the scene rules (visual.json)

class SceneTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.a = HeroAssets.committed("test_rig")

    def test_face_shift_matches_the_frame_regions(self):
        rig = load_rig()
        for plane, n in (("eyes", 12), ("mouth", 8), ("cheeks", 4)):
            u0, v0, _, _ = F.frame_rect(rig, plane, 0)
            for k in range(n):
                u, v, _, _ = F.frame_rect(rig, plane, k)
                args = {"eyes": 0, "mouth": 0, "cheeks": 0}
                args[plane] = k
                self.assertEqual(self.a.face_shift(**args)[plane], (u - u0, v - v0))
        with self.assertRaises(ValueError):
            self.a.face_shift(eyes=12)

    def test_hidden_bones_by_phase_weapon_and_distance(self):
        a = self.a
        self.assertIn("p2_halo", a.hidden(phase=1))
        self.assertNotIn("p2_halo", a.hidden(phase=2))
        self.assertIn("weapon_r", a.hidden())
        self.assertIn("weapon_back", a.hidden(drawn=True))
        self.assertNotIn("weapon_r", a.hidden(drawn=True))
        self.assertTrue(set(a.bones("fine")) <= set(a.hidden(far=True)))
        self.assertIs(a.textures(2)[0], a.tex_p2)
        self.assertIs(a.textures(1)[0], a.tex)

    def test_tint_reaches_only_the_sash(self):
        a = self.a
        v = raster.View(0, 0, 3)
        plain = a.render(v, a.stance(), tint=None, frame=True)
        tinted = a.render(v, a.stance(), tint=(255, 0, 0), frame=True)
        sash = np.zeros(plain.ids.shape, dtype=bool)
        for y, x in zip(*np.nonzero(plain.ids >= 0)):
            sash[y, x] = plain.quads[plain.ids[y, x]].bone in a.bones("tint")
        self.assertTrue(sash.any())
        self.assertTrue(np.array_equal(plain.rgba[~sash], tinted.rgba[~sash]))
        self.assertEqual(int(tinted.rgba[sash][:, 1:3].max()), 0)

    def test_committed_and_built_render_alike(self):
        built = HeroAssets.build("test_rig")
        v = raster.View(-34, 10, 2)
        self.assertTrue(np.array_equal(built.render(v, built.stance()), self.a.render(v, self.a.stance())))


# ------------------------------------------------------------------ GIFs and the command line

class GifTest(unittest.TestCase):
    def test_gif_decodes_to_its_frames(self):
        a = HeroAssets.committed("test_rig")
        actor = gif.Actor(a, a.playback("move.walk"), a.face_sim("calm"), raster.View(-55, 10, 2))
        frames = gif.film([actor], gif.ticks(0.0, 0.3, 20), "move.walk", events=False)
        self.assertEqual(len(frames), 6)
        with tempfile.TemporaryDirectory() as tmp:
            p1, p2 = os.path.join(tmp, "a.gif"), os.path.join(tmp, "b.gif")
            gif.write_gif(frames, p1)
            gif.write_gif(frames, p2)
            with open(p1, "rb") as f1, open(p2, "rb") as f2:
                self.assertEqual(f1.read(), f2.read())
            g = Image.open(p1)
            for i, f in enumerate(frames):
                g.seek(i)
                got = np.asarray(g.convert("RGB")).astype(int)
                want = np.asarray(f.convert("RGB")).astype(int)
                self.assertLess(np.abs(got - want).mean(), 3.0, "frame %d" % i)

    def test_cli_preview(self):
        import cli
        with tempfile.TemporaryDirectory() as tmp:
            out = os.path.join(tmp, "p.png")
            self.assertEqual(cli.main(["preview", "test_rig", "--committed", "--action", "attack.1", "--frames",
                                       "3", "--view", "side", "--scale", "2", "--out", out]), 0)
            self.assertGreater(Image.open(out).width, 100)


if __name__ == "__main__":
    unittest.main()
