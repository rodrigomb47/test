import json
import unittest

import numpy as np

from helpers import HEROGEN  # noqa: F401

import build
from geo import GeoModel
from render import ortho


class RenderTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        files, cls.b = build.build_files("test_rig")
        cls.geo = GeoModel(json.loads(files[build.out_paths("test_rig")["geo"]]))

    def test_front_view(self):
        hidden = ["p2_halo", "weapon_r", "weapon_l"]
        img = ortho.render(self.geo, self.b.image, scale=4, hidden=hidden, pad=0)
        ys = np.argwhere(img[:, :, 3] > 0)[:, 0]
        self.assertAlmostEqual((ys.max() - ys.min() + 1) / 4.0, 35.0, delta=1.0, msg="feet to ear tips")
        self.assertTrue(img[:, :, 3].any())
        colours = {tuple(p[:3]) for p in img.reshape(-1, 4) if p[3]}
        self.assertGreater(len(colours), 20)

    def test_hidden_bones(self):
        full = ortho.render(self.geo, self.b.image, scale=2, hidden=["weapon_r", "weapon_l"], pad=0,
                            bounds=(-12, 12, 0, 40))
        bare = ortho.render(self.geo, self.b.image, scale=2, hidden=["weapon_r", "weapon_l", "p2_halo"], pad=0,
                            bounds=(-12, 12, 0, 40))
        self.assertGreater(int((full[:, :, 3] > 0).sum()), int((bare[:, :, 3] > 0).sum()))

    def test_sheathed_blade_points_back_and_down(self):
        quads = [q for q in ortho.quads(self.geo) if q[4] == "weapon_back"]
        pts = np.concatenate([q[0] for q in quads])
        lowest = pts[pts[:, 1].argmin()]
        highest = pts[pts[:, 1].argmax()]
        self.assertGreater(lowest[2], highest[2], "the scabbard end is behind the hilt (+z is back)")


if __name__ == "__main__":
    unittest.main()
