import unittest

import numpy as np

from helpers import HEROGEN  # noqa: F401

from rig import BASE_IDS, Cube, Model
from uvpack import Packer, from_file_uv, net, pack, texel_point, texels, to_file_uv


def cube(size, **kw):
    return Cube(name="c", bone="body", origin=(0.0, 0.0, 0.0), size=size, slot="primary", material="cloth", **kw)


class UvTest(unittest.TestCase):
    def test_whole_texels(self):
        self.assertEqual(texels(2.5), 3)
        self.assertEqual(texels(6.5), 7)
        self.assertEqual(texels(2.4), 2)
        self.assertEqual(texels(0.75), 1)
        self.assertEqual(texels(0.25), 1)
        self.assertEqual(texels(8, 2), 16)

    def test_net_layout(self):
        w, h, rects = net(cube((3.0, 5.0, 2.0)))
        self.assertEqual((w, h), (10, 7))
        self.assertEqual(rects["up"], (2, 0, 3, 2))
        self.assertEqual(rects["down"], (5, 0, 3, 2))
        self.assertEqual(rects["east"], (0, 2, 2, 5))
        self.assertEqual(rects["north"], (2, 2, 3, 5))
        self.assertEqual(rects["west"], (5, 2, 2, 5))
        self.assertEqual(rects["south"], (7, 2, 3, 5))

    def test_flat_cube_has_one_face(self):
        w, h, rects = net(cube((4.0, 2.0, 0.0)))
        self.assertEqual(list(rects), ["north"])
        self.assertEqual((w, h), (4, 2))

    def test_file_convention(self):
        self.assertEqual(to_file_uv("north", (4, 6, 3, 5)), ([4, 6], [3, 5]))
        self.assertEqual(to_file_uv("down", (4, 6, 3, 2)), ([4, 8], [3, -2]))
        for f in ("north", "down", "up"):
            uv, size = to_file_uv(f, (4, 6, 3, 2))
            self.assertEqual(from_file_uv(f, uv, size), (4, 6, 3, 2))

    def test_texel_orientation(self):
        c = cube((4.0, 4.0, 4.0))
        c.uv = {f: (0, 0, 4, 4) for f in ("north", "south", "east", "west", "up", "down")}
        x, y, z = texel_point(c, "north", 0, 0)
        self.assertLess(x, 1)              # left column = hero's right (-x), top row = top
        self.assertGreater(y, 3)
        self.assertGreater(texel_point(c, "south", 0, 0)[0], 3)     # back: left column = hero's left
        self.assertGreater(texel_point(c, "east", 0, 0)[2], 3)      # right side: left column = back
        self.assertLess(texel_point(c, "west", 0, 0)[2], 1)         # left side: left column = front
        self.assertLess(texel_point(c, "up", 0, 3)[2], 1)           # top: bottom row = front
        self.assertLess(texel_point(c, "down", 0, 3)[2], 1)         # bottom: bottom row = front

    def test_packer_gutter_and_order(self):
        p = Packer(16, 16, reserved=[(0, 0, 4, 4)])
        a = p.place(4, 4)
        self.assertEqual(a, (5, 0), "first free spot right of the reserved block, one-texel gutter")
        b = p.place(4, 4)
        self.assertEqual(b, (10, 0))
        c = p.place(16, 2)
        self.assertEqual(c, (0, 5))
        self.assertIsNone(p.place(17, 1))
        self.assertIsNone(Packer(4, 4).place(5, 5))

    def test_packer_nests_islands_in_net_corners(self):
        p = Packer(10, 7)
        self.assertEqual(p.place(10, 7, list(net(cube((3.0, 5.0, 2.0)))[2].values())), (0, 0))
        self.assertIsNotNone(p.place(1, 1), "the empty corner of the net still takes a 1x1 island")

    def test_every_base_packs_inside_its_region(self):
        for bid in BASE_IDS:
            m = Model("t", bid)
            pack(m)
            occ = np.zeros((m.tex_h, m.tex_w), dtype=int)
            for c in m.cubes:
                for f, (u, v, w, h) in c.uv.items():
                    self.assertTrue(all(isinstance(a, int) for a in (u, v, w, h)))
                    if f in c.fixed:
                        continue
                    self.assertTrue(u + w <= 64 and v + h <= 64, "%s %s %s outside the base region" % (bid, c.name, f))
                    self.assertFalse(occ[v:v + h, u:u + w].any(), "%s %s %s overlaps" % (bid, c.name, f))
                    occ[v:v + h, u:u + w] = 1
            head = m.cube("head")
            self.assertEqual(head.uv["north"], (64, 32, 16, 16))
            self.assertEqual(m.cube("eyes").uv["north"], (64, 0, 16, 8))
            self.assertEqual(m.cube("mouth").uv["north"], (64, 24, 8, 4))
            self.assertEqual(m.cube("cheeks").uv["north"], (64, 28, 12, 2))

    def test_packing_is_deterministic(self):
        a, b = Model("t", "m_large"), Model("t", "m_large")
        pack(a)
        pack(b)
        self.assertEqual([c.uv for c in a.cubes], [c.uv for c in b.cubes])


if __name__ == "__main__":
    unittest.main()
