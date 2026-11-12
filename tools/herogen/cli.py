"""Command line (design 13.7). G1 provides build, build --check, check (geometry and face part of 14.2), previews
and stats; G2 and G3 add preview/faces/sheet/gif, the animation commands and the kit checks."""
import argparse
import io
import json
import sys

import numpy as np
from PIL import Image

import build
import palette
import validate
from geo import GeoModel
from util import HerogenError


def _ids(args):
    ids = args.ids or []
    if getattr(args, "all", False) or not ids:
        ids = build.hero_ids()
    return ids


def cmd_build(args):
    bad = 0
    for hid in _ids(args):
        files, b = build.build_files(hid)
        if args.check:
            problems = build.check_files(hid, files)
            for p in problems:
                print("DIFF " + p)
            bad += len(problems)
            if not problems:
                print("OK   %s: committed files match a fresh build" % hid)
        else:
            build.write_files(files)
            for p in build.stale_files(hid, files):
                print("NOTE %s: stale output (delete it)" % p)
            print("built %s: %d bones, %d cubes, %dx%d texture, %d files" % (
                hid, len(b.model.bones), len(b.model.cubes), b.model.tex_w, b.model.tex_h, len(files)))
        for note in _palette_notes(b):
            print("NOTE %s: %s" % (hid, note))
    return 1 if bad else 0


def _palette_notes(b):
    return palette.check_palette(b.ramps["_hex"]) if not b.hero["id"].startswith("_") else []


def cmd_check(args):
    bad = 0
    for hid in _ids(args):
        files, _ = build.build_files(hid)
        paths = build.out_paths(hid)
        geo = GeoModel(json.loads(files[paths["geo"]]))
        tex = np.array(Image.open(io.BytesIO(files[paths["texture"]])).convert("RGBA"))
        problems = validate.check_built(geo, tex) + build.check_files(hid, files)
        for p in problems:
            print("ERROR %s: %s" % (hid, p))
        if not problems:
            print("OK    %s" % hid)
        bad += len(problems)
    return 1 if bad else 0


def cmd_previews(args):
    import previews
    paths = previews.write_all(args.out) if args.out else previews.write_all()
    for k, p in paths.items():
        print("%s: %s" % (k, p))
    return 0


def cmd_stats(args):
    for hid in _ids(args):
        files, b = build.build_files(hid)
        m = b.model
        used = int((b.image[:, :, 3] > 0).sum())
        print("%-20s %-11s bones %2d/%d  cubes %3d/%d  texture %dx%d (%d%% painted)  parts %d" % (
            hid, m.base_id, len(m.bones), m.rig["budgets"]["bones"], len(m.cubes), m.rig["budgets"]["cubes"],
            m.tex_w, m.tex_h, 100 * used // (m.tex_w * m.tex_h), len(m.parts_used)))
    return 0


def main(argv=None):
    p = argparse.ArgumentParser(prog="herogen", description="Faction Clash hero art generator")
    sub = p.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("build", help="build heroes from herosrc/heroes (all when no id is given)")
    b.add_argument("ids", nargs="*")
    b.add_argument("--all", action="store_true")
    b.add_argument("--check", action="store_true", help="rebuild in memory and fail if committed files differ")
    b.set_defaults(fn=cmd_build)
    c = sub.add_parser("check", help="asset checks (geometry, UVs, face frames, reproducibility)")
    c.add_argument("ids", nargs="*")
    c.add_argument("--all", action="store_true")
    c.set_defaults(fn=cmd_check)
    v = sub.add_parser("previews", help="write the G1 preview sheets (bases, test rig, faces, texture)")
    v.add_argument("--out", default=None)
    v.set_defaults(fn=cmd_previews)
    s = sub.add_parser("stats", help="budgets per hero")
    s.add_argument("ids", nargs="*")
    s.set_defaults(fn=cmd_stats)
    args = p.parse_args(argv)
    try:
        return args.fn(args)
    except HerogenError as e:
        print("ERROR " + str(e), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
