"""Conformance with GeckoLib 4.9.3 itself, not with herogen's own reading of it.

The functions below port the parts of GeckoLib 4.9.3 that decide where a texel lands, read from the published jar
(geckolib-neoforge-1.21.1-4.9.3.jar, SHA-256 20a1995e...1c47, the hash HEROES_V2.md 4.2 records) with `javap -c` on
2026-11-12:
- BakedModelFactory.Builtin.constructCube: origin x becomes -(origin.x + size.x); cube and bone rotations become
  (-rx, -ry, rz) in radians; pivots become (-px, py, pz);
- BakedModelFactory.VertexSet: the eight corners and the vertex order of each quad (quadWest ... quadDown), and
  verticesForQuad (no mirror: WEST -> quadWest, EAST -> quadEast, UP -> quadUp, DOWN -> quadDown);
- Builtin.buildQuad (per-face UV): GeoQuad.build(vertices, uv, uv_size, rotation, ..., mirror=false, direction);
- GeoQuad.build: u1 = (u + w) / W, v1 = (v + h) / H; without mirror u and u1 are swapped; FaceUV.Rotation.NONE gives
  the four vertices (u, v), (u1, v), (u1, v1), (u, v1);
- RenderUtil.rotateMatrixAroundBone / rotateMatrixAroundCube: rotations about Z, then Y, then X are multiplied onto
  the pose (so a vertex is rotated about X first);
- GeoRenderer.renderCube skips null quads (a face missing from the per-face uv), so one-faced planes are legal;
- loading.json.raw records: the keys GeckoLib reads (Gson ignores any other key silently, so a typo would drop data);
- FormatVersion: 1.12.0, 1.14.0 and 1.21.0 are accepted.
If GeckoLib is upgraded, re-read those classes and update this port; every other herogen test trusts it.
"""
import json
import math
import unittest

import numpy as np

from helpers import HEROGEN  # noqa: F401

import build
from geo import GeoModel
from render import ortho
from rig import Cube
from uvpack import from_file_uv, texel_point, to_file_uv

GECKO_FORMATS = ("1.12.0", "1.14.0", "1.21.0")
GECKO_KEYS = {   # loading.json.raw.* records (plus the geometry root and the description)
    "root": {"format_version", "minecraft:geometry"},
    "geometry": {"description", "bones"},
    "description": {"identifier", "texture_width", "texture_height", "visible_bounds_width", "visible_bounds_height",
                    "visible_bounds_offset", "preserve_model_pose"},
    "bone": {"bind_pose_rotation", "cubes", "debug", "inflate", "locators", "mirror", "name", "neverRender", "parent",
             "pivot", "poly_mesh", "render_group_id", "reset", "rotation", "texture_meshes"},
    "cube": {"inflate", "mirror", "origin", "pivot", "rotation", "size", "uv"},
    "face": {"material_instance", "uv", "uv_size", "uv_rotation"},
}
FACES = ("north", "south", "east", "west", "up", "down")


# ------------------------------------------------------------------ the port

def gecko_quads(origin, size, inflate=0.0):
    """Baked-space corners of each quad in GeckoLib's vertex order (VertexSet; units stay pixels)."""
    ox, oy, oz = -(origin[0] + size[0]), origin[1], origin[2]
    sx, sy, sz = size
    i = inflate
    blb = (ox - i, oy - i, oz - i)
    brb = (ox - i, oy - i, oz + sz + i)
    tlb = (ox - i, oy + sy + i, oz - i)
    trb = (ox - i, oy + sy + i, oz + sz + i)
    tlf = (ox + sx + i, oy + sy + i, oz - i)
    trf = (ox + sx + i, oy + sy + i, oz + sz + i)
    blf = (ox + sx + i, oy - i, oz - i)
    brf = (ox + sx + i, oy - i, oz + sz + i)
    return {"west": [trb, tlb, blb, brb], "east": [tlf, trf, brf, blf], "north": [tlb, tlf, blf, blb],
            "south": [trf, trb, brb, brf], "up": [trb, trf, tlf, tlb], "down": [blb, blf, brf, brb]}


def gecko_uvs(uv, uv_size):
    """GeoQuad.build without mirror and with Rotation.NONE: the texel-space UV of each of the four vertices."""
    u, v = uv
    u1, v1 = u + uv_size[0], v + uv_size[1]
    u, u1 = u1, u                      # !mirrorUV swaps the u bounds
    return [(u, v), (u1, v), (u1, v1), (u, v1)]


def unbake(p):
    """Baked space -> file space (GeckoLib mirrors x)."""
    return (-p[0], p[1], p[2])


def point_at_uv(verts, uvs, tu, tv):
    """The 3D point of a quad (a rectangle with axis-aligned UVs) at texel-space (tu, tv)."""
    v0, v1, v2 = (np.array(verts[k], dtype=float) for k in (0, 1, 2))
    (a0, b0), (a1, _), (_, b2) = uvs[0], uvs[1], uvs[2]
    s = (tu - a1) / (a0 - a1)          # vertices 0 and 1 share v and differ in u
    t = (tv - b0) / (b2 - b0)          # vertices 1 and 2 share u and differ in v
    return v1 + s * (v0 - v1) + t * (v2 - v1)


def axis_rotation(axis, angle):
    """Right-handed rotation about a unit axis (Rodrigues), as JOML's Axis.XP/YP/ZP.rotation builds it."""
    x, y, z = axis
    c, s = math.cos(angle), math.sin(angle)
    k = np.array([[0, -z, y], [z, 0, -x], [-y, x, 0]], dtype=float)
    return np.eye(3) + s * k + (1 - c) * (k @ k)


def gecko_rotation(file_deg):
    """The rotation GeckoLib applies to a vertex for file-degree rotations (baked (-x, -y, z), posed Z then Y then
    X)."""
    rx, ry, rz = (math.radians(a) for a in file_deg)
    return axis_rotation((0, 0, 1), rz) @ axis_rotation((0, 1, 0), -ry) @ axis_rotation((1, 0, 0), -rx)


# ------------------------------------------------------------------ tests

class GeckoLibConformanceTest(unittest.TestCase):
    CUBE = Cube(name="c", bone="body", origin=(-1.5, 10.0, -2.0), size=(3.0, 5.0, 2.0), slot="primary",
                material="cloth")

    def _rect(self, face):
        w = {"north": 3, "south": 3, "up": 3, "down": 3, "east": 2, "west": 2}[face]
        h = {"north": 5, "south": 5, "east": 5, "west": 5, "up": 2, "down": 2}[face]
        return (10, 20, w, h)

    def test_texels_land_where_the_painter_paints_them(self):
        """Every texel centre of every face maps, through GeckoLib's baking, to the 3D point uvpack.texel_point gives
        (the point the painter shades and the renderer draws)."""
        c = self.CUBE
        quads = gecko_quads(c.origin, c.size)
        for face in FACES:
            rect = self._rect(face)
            uv, size = to_file_uv(face, rect)
            uvs = gecko_uvs(uv, size)
            verts = [unbake(p) for p in quads[face]]
            u, v, w, h = rect
            for j in range(h):
                for i in range(w):
                    got = point_at_uv(verts, uvs, u + i + 0.5, v + j + 0.5)
                    want = texel_point(c, face, i, j, rect)
                    np.testing.assert_allclose(got, want, atol=1e-9, err_msg="%s texel %d,%d" % (face, i, j))

    def test_face_normals_point_outward(self):
        """The 'east' face GeckoLib builds is on the hero's right (file -x), 'west' on the left, 'north' at the front."""
        c = self.CUBE
        quads = gecko_quads(c.origin, c.size)
        x0, y0, z0, x1, y1, z1 = c.box
        expect = {"east": (0, x0), "west": (0, x1), "down": (1, y0), "up": (1, y1), "north": (2, z0), "south": (2, z1)}
        for face, (axis, value) in expect.items():
            for p in quads[face]:
                self.assertAlmostEqual(unbake(p)[axis], value, msg=face)

    def test_preview_renderer_maps_corners_like_geckolib(self):
        """ortho._face_corners (TL, TR, BR, BL) with UVs (u, v), (u+w, v), (u+w, v+h), (u, v+h) of the net rectangle
        puts every UV corner on the same 3D corner as GeckoLib does."""
        c = self.CUBE
        quads = gecko_quads(c.origin, c.size)
        for face in FACES:
            rect = self._rect(face)
            uv, size = to_file_uv(face, rect)
            gecko = {tuple(round(a, 6) for a in uvc): tuple(round(a, 6) for a in unbake(p))
                     for uvc, p in zip(gecko_uvs(uv, size), quads[face])}
            u, v, w, h = from_file_uv(face, uv, size)
            mine = dict(zip([(u, v), (u + w, v), (u + w, v + h), (u, v + h)], ortho._face_corners(face, c.box)))
            for uvc, p in mine.items():
                self.assertEqual(gecko[tuple(float(a) for a in uvc)], tuple(round(float(a), 6) for a in p), face)

    def test_rotation_order_and_signs(self):
        """ortho emulates GeckoLib's rotations: baked angles (-rx, -ry, rz), a vertex turned about X, then Y, then Z
        (checked with angles whose order matters)."""
        for rot in ((55, 0, 0), (0, 0, -35), (30, 40, 0), (20, -60, 75), (-10, 25, 140)):
            mine = ortho.rotation_matrix(-rot[0], -rot[1], rot[2])
            np.testing.assert_allclose(mine, gecko_rotation(rot), atol=1e-12, err_msg=str(rot))

    def test_written_geometry_uses_only_keys_geckolib_reads(self):
        for hid in build.hero_ids():
            with self.subTest(hero=hid):
                files, _ = build.build_files(hid)
                data = json.loads(files[build.out_paths(hid)["geo"]])
                self.assertIn(data["format_version"], GECKO_FORMATS)
                self.assertLessEqual(set(data), GECKO_KEYS["root"])
                for geo in data["minecraft:geometry"]:
                    self.assertLessEqual(set(geo), GECKO_KEYS["geometry"])
                    self.assertLessEqual(set(geo["description"]), GECKO_KEYS["description"])
                    for b in geo["bones"]:
                        self.assertLessEqual(set(b), GECKO_KEYS["bone"], b["name"])
                        for c in b.get("cubes", []):
                            self.assertLessEqual(set(c), GECKO_KEYS["cube"], b["name"])
                            self.assertIsInstance(c["uv"], dict, "per-face UV, never box UV")
                            for face, fuv in c["uv"].items():
                                self.assertIn(face, FACES)
                                self.assertLessEqual(set(fuv), GECKO_KEYS["face"], b["name"])
                                self.assertEqual((len(fuv["uv"]), len(fuv["uv_size"])), (2, 2))
                            if "rotation" in c:
                                self.assertIn("pivot", c, "a rotated cube carries its pivot")
                anim = json.loads(files[build.out_paths(hid)["animation"]])
                self.assertIsInstance(anim["animations"], dict)

    def test_written_cubes_round_trip_through_the_port(self):
        """Every face of the built test rig, baked the GeckoLib way, has its texel (0, 0) where the painter put it."""
        files, b = build.build_files("test_rig")
        geo = GeoModel(json.loads(files[build.out_paths("test_rig")["geo"]]))
        def key(bone, origin, size):
            return (bone,) + tuple(round(float(a), 3) for a in tuple(origin) + tuple(size))
        by_key = {key(c.bone, c.origin, c.size): c for c in b.model.cubes}
        checked = 0
        for gc in geo.cubes:
            mc = by_key[key(gc.bone, gc.origin, gc.size)]
            quads = gecko_quads(gc.origin, gc.size, gc.inflate)
            for face, (uv, size) in gc.raw_uv.items():
                if mc.inflate:
                    continue
                rect = from_file_uv(face, uv, size)
                got = point_at_uv([unbake(p) for p in quads[face]], gecko_uvs(uv, size), rect[0] + 0.5, rect[1] + 0.5)
                np.testing.assert_allclose(got, texel_point(mc, face, 0, 0, rect), atol=2e-3)
                checked += 1
        self.assertGreater(checked, 200)


if __name__ == "__main__":
    unittest.main()
