"""G6a/b: the parts library (head and body parts for the slice), the format additions it needed (strand tips, left-hand
slots, chain copies, part-relative paint globs, the face-cover bone), the layering rules that keep coplanar faces
out (the sash outermost at the waist, leg shells short of the centre line, chain boxes of different widths), the
coplanar-face rule in `herogen check`, `herogen lint` and the sample outfits."""
import json
import unittest

import numpy as np

from helpers import mannequin

import build
import cli
import lint
import validate
from geo import GeoModel, geo_json
from paint import Painter
from parts import apply_part
from rig import BASE_IDS, Model
from rot import rotate_points
from uvpack import pack

HEAD = {"hair": 12, "ears": 4, "horns": 2, "tails": 3, "wings": 1, "headwear": 8}          # HEROES_V2 13.3, slice
BODY = {"outfit_top": 8, "outfit_bottom": 6, "robes": 3, "armour": 5, "legwear": 5, "back": 2}
SLICE = ("natsuho", "yugen", "benika", "hinae", "kagenori", "tobimaru", "ilsabet", "ambrose")


def model_with(base_id, *entries):
    m = Model("t", base_id)
    for i, e in enumerate(entries):
        apply_part(m, e, i)
    return m


def world_corners(c):
    x0, y0, z0 = c.origin
    sx, sy, sz = c.size
    pts = [(x0 + a, y0 + b, z0 + d) for a in (0, sx) for b in (0, sy) for d in (0, sz)]
    return rotate_points(pts, c.rotation, c.pivot) if c.rotation is not None else pts


class LibraryContentTest(unittest.TestCase):
    def test_slice_counts_of_13_3(self):
        """The slice column of HEROES_V2 13.3: 30 head parts and at least 29 body parts."""
        ids = lint.part_ids()
        for group in (HEAD, BODY):
            for cat, n in group.items():
                with self.subTest(category=cat):
                    self.assertGreaterEqual(len([i for i in ids if i.startswith(cat + "/")]), n)
        self.assertGreaterEqual(sum(len([i for i in ids if i.startswith(c + "/")]) for c in HEAD), 30)
        self.assertGreaterEqual(sum(len([i for i in ids if i.startswith(c + "/")]) for c in BODY), 29)

    def test_static_lint_rules(self):
        """Every part names existing parts in `conflicts`, declares `occupies`, and mirrors exactly."""
        all_ids = lint.part_ids()
        for pid in all_ids:
            with self.subTest(part=pid):
                self.assertEqual(lint.static_check(pid, all_ids), [])

    def test_outfits_cover_the_slice_and_build(self):
        """The sample outfits are the eight slice heroes; each builds and passes the asset checks on the smallest and
        the largest base (the lint runs all six with the coplanar rule)."""
        outfits = lint.load_outfits()
        self.assertEqual(tuple(o["id"] for o in outfits), SLICE)
        for o in outfits:
            for b in ("chibi_f", "adult_m"):
                with self.subTest(outfit=o["id"], base=b):
                    self.assertEqual(lint.full_check(lint.outfit_hero(o, b), ties=False), [])


class LayeringTest(unittest.TestCase):
    def test_sash_is_the_outermost_waist_layer(self):
        """The faction sash (tinted) sits outside every other waist layer by at least 0.1 px on every base, so it is
        never hidden in a plane it shares (the G2 review: Maren's sash had exactly her waist plate's extents)."""
        layers = ["armour/breastplate", "outfit_bottom/hakama", "outfit_bottom/pleated_skirt",
                  "outfit_bottom/flared_skirt", "robes/court_robe", "robes/priest_robe"]
        for sash in ("accessories/sash_obi", "accessories/sash_bow"):
            for b in BASE_IDS:
                m = model_with(b, {"part": sash})
                obi = [c for c in m.cubes if c.name.endswith("#obi")][0]
                for pid in layers:
                    other = model_with(b, {"part": pid, "color": "primary", "trim": "trim", "alt": "secondary"})
                    for c in other.cubes:
                        if c.part != pid or c.rotation is not None or c.bone not in ("waist", "skirt", "sash"):
                            continue
                        if c.box[4] <= obi.box[1] or c.box[1] >= obi.box[4]:
                            continue                       # not at the sash's height
                        with self.subTest(sash=sash, base=b, cube=c.name):
                            self.assertLessEqual(obi.box[2], c.box[2] - 0.1)
                            self.assertGreaterEqual(obi.box[5], c.box[5] + 0.1)

    def test_leg_shells_stop_short_of_the_centre_line(self):
        """Shells round the legs (greaves, boots, socks, shoes, shorts, trousers) never reach the hero's centre line,
        so the two legs' shells cannot share a front plane (Maren's greaves overlapped by 0.45 px)."""
        legs = ("leg_r", "shin_r", "foot_r", "leg_l", "shin_l", "foot_l")
        pids = [p for p in lint.part_ids() if p.split("/")[0] in ("legwear", "outfit_bottom")]
        for pid in pids:
            for b in BASE_IDS:
                m = model_with(b, {"part": pid, "color": "primary", "trim": "trim", "alt": "secondary",
                                   "detail": "accent"})
                for c in m.cubes:
                    bone = c.bone.replace("fine_", "")
                    if c.part != pid or bone not in legs or c.rotation is not None:
                        continue
                    with self.subTest(part=pid, base=b, cube=c.name):
                        if bone.endswith("_r"):
                            self.assertLessEqual(c.box[3], -0.025 + 1e-9)
                        else:
                            self.assertGreaterEqual(c.box[0], 0.025 - 1e-9)

    def test_chain_boxes_never_share_a_side_plane(self):
        """Consecutive boxes of a chain differ in width and depth by at least 0.1 px (a tail curling in one plane
        keeps every side face at +-w/2: the test rig's tail_4/tail_5 tie)."""
        m = model_with("teen_f", {"part": "tails/cat_long", "color": "hair", "alt": "hair_accent"})
        main = sorted((c for c in m.cubes if c.part == "tails/cat_long" and c.name.endswith("_0")),
                      key=lambda c: c.bone)
        for a, b in zip(main, main[1:]):
            self.assertGreaterEqual(abs(a.size[0] - b.size[0]), 0.1 - 1e-9, (a.name, b.name))
            self.assertGreaterEqual(abs(a.size[2] - b.size[2]), 0.1 - 1e-9, (a.name, b.name))


class FormatTest(unittest.TestCase):
    def test_strand_tips_take_their_slot(self):
        """`tip` paints the bottom fraction of a strand's side faces and its end cap in another slot (red tips)."""
        m = model_with("teen_f", {"part": "hair/wild_mane", "color": "hair", "alt": "primary"})
        tipped = [c for c in m.cubes if c.tip]
        self.assertTrue(tipped)
        p = Painter(m, {}, {})
        c = tipped[0]
        self.assertEqual(p.resolve(c, "north", (0, 0, 0), 9, 10)[0], "primary")
        self.assertEqual(p.resolve(c, "north", (0, 0, 0), 0, 10)[0], "hair")
        self.assertEqual(p.resolve(c, "down", (0, 0, 0))[0], "primary")

    def test_left_slots_recolour_the_mirrored_copies(self):
        """An entry's `left` gives the left-hand (mirrored) cubes their own slots: odd-coloured ears."""
        m = model_with("teen_f", {"part": "ears/cat_tufted", "color": "leather", "left": {"color": "primary"}})
        slabs = [c for c in m.cubes if c.part == "ears/cat_tufted" and c.name.split("#")[1][:4] == "ear_"
                 and c.name.split("#")[1][4:5].isdigit()]
        self.assertTrue(all(c.slot == ("primary" if c.name.endswith(":l") else "leather") for c in slabs))
        self.assertTrue(any(c.name.endswith(":l") for c in slabs))

    def test_chain_copies_fan_out_inside_the_budgets(self):
        """`copies` builds up to nine tails fanned behind the hero, each with its own spring chain, inside the bone
        budget; the middle copy keeps the rig's tail bones (the core library bakes them)."""
        for n in (1, 3, 9):
            hero = mannequin("adult_m", [{"part": "tails/fox", "color": "hair", "alt": "hair_accent",
                                         "params": {"count": n}}])
            files, b = build.build_files(hero["id"], hero)
            chains = [s["chain"] for s in b.model.secondary if s.get("part") == "tails/fox"]
            self.assertEqual(len(chains), n)
            self.assertEqual(chains[0], ["tail_1", "tail_2", "tail_3", "tail_4", "tail_5"])
            self.assertEqual(len({x for c in chains for x in c}), sum(len(c) for c in chains))
            self.assertLessEqual(len(b.model.bones), b.model.rig["budgets"]["bones"])
        roots = {}
        for bone, info in b.model.bones.items():
            if bone.startswith("tail_3"):
                roots[bone] = info.pivot
        xs = sorted(round(p[0], 3) for p in roots.values())
        self.assertEqual(xs, sorted(-x for x in xs), "the fan is symmetric")

    def test_paint_globs_can_name_the_parts_own_cubes(self):
        """A paint op glob starting with '#' names the part's own cubes, mirrored copies included (a sleeve's
        lining)."""
        m = model_with("teen_f", {"part": "outfit_top/kimono_wide", "color": "primary", "alt": "secondary",
                                  "trim": "trim"})
        op = [o for o in m.paint_ops if any(g.endswith("#bell*") for g in o["cubes"])][0]
        hit = m.cubes_matching(op["cubes"])
        self.assertTrue(any(c.name.endswith(":l") for c in hit))
        self.assertTrue(all("#bell" in c.name for c in hit))

    def test_bell_sleeve_lining_is_dark(self):
        """The inside of a bell sleeve (the strands' inner faces) is painted two tones darker than the outside, so an
        arm raised toward the camera shows a shadowed opening, not a lit ring (G2: the swing at 0.45 s)."""
        hero = mannequin("teen_f", [{"part": "outfit_top/kimono_wide", "color": "primary", "alt": "secondary",
                                     "trim": "trim"}], palette={"primary": "#3A8A6A", "secondary": "#E4DCC8",
                                                                "trim": "#D9A944"})
        files, b = build.build_files(hero["id"], hero)
        img = b.image.astype(float)
        bell = [c for c in b.model.cubes if "#bell" in c.name][0]

        def mean(face):
            u, v, w, h = bell.uv[face]
            return img[v:v + h - 1, u:u + w, :3].mean()          # the hem row (trim) left out
        self.assertLess(mean("south"), mean("north") - 20)


class FaceCoverTest(unittest.TestCase):
    def geo(self, part, base="teen_f"):
        m = model_with(base, part)
        pack(m)
        return GeoModel(json.loads(json.dumps(geo_json(m))))

    def test_a_mask_may_cover_the_mouth_never_the_eyes(self):
        """A cube on a `mask` bone may sit in front of the face below the eyes plane (a cloth half mask); above it,
        or on any other bone, the clear-face rule still fails it."""
        blank = np.zeros((128, 256, 4), dtype=np.uint8)
        ok = self.geo({"part": "headwear/half_mask", "color": "primary"})
        self.assertFalse([p for p in validate.check_geometry(ok, blank) if "in front of the face" in p])
        for bone, top in (("mask", 2.5), ("hat", 1.5)):
            part = {"part": {"id": "inline/cover", "category": "headwear", "occupies": ["x"],
                             "offset": [0, "head_y", 0],
                             "bones": [{"name": "mask", "parent": "head", "pivot": [0, 0.5, 0]}],
                             "cubes": [{"bone": bone, "origin": [-4, 0, -5.8], "size": [8, top, 0.5]}]},
                    "color": "primary"}
            with self.subTest(bone=bone):
                bad = self.geo(part)
                self.assertTrue([p for p in validate.check_geometry(bad, blank) if "in front of the face" in p])


class CoplanarRuleTest(unittest.TestCase):
    def test_check_reports_a_sash_sharing_its_planes(self):
        """`herogen check` fails a hero whose sash shares its planes with a waist layer (the G2 review's case, put
        back on purpose), and passes the fixed library."""
        fixed = lint.lint_hero("armour/breastplate", "adult_f")
        fixed["art"]["parts"].append({"part": "accessories/sash_obi"})
        files, b = build.build_files(fixed["id"], fixed)
        self.assertEqual(cli.tie_problems(fixed["id"], files, b), [])
        old = json.loads(json.dumps(fixed))
        old["art"]["parts"][-1] = {"part": {"id": "inline/old_sash", "category": "accessories",
                                            "slots": {"main": "sash"}, "occupies": ["waist_band"],
                                            "cubes": [{"name": "obi", "bone": "sash",
                                                       "origin": ["-waist_w / 2 - 0.4", "waist_y",
                                                                  "-waist_d / 2 - 0.4"],
                                                       "size": ["waist_w + 0.8", 2.25, "waist_d + 0.8"]}]}}
        files, b = build.build_files(old["id"], old)
        problems = cli.tie_problems(old["id"], files, b)
        self.assertEqual(len(problems), 1)
        self.assertIn("sash/waist", problems[0])

    def test_lint_fails_a_part_below_the_ground(self):
        part = {"id": "inline/sunk", "category": "legwear", "occupies": ["x"],
                "cubes": [{"bone": "foot_r", "origin": ["-hip_x - 1", -0.5, -1], "size": [1, 1, 1]}]}
        m = Model("t", "teen_f")
        apply_part(m, {"part": part, "color": "primary"}, 0)
        self.assertTrue(lint._below_ground(m))


if __name__ == "__main__":
    unittest.main()
