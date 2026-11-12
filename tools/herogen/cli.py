"""Command line (design 13.7). G1 provides build, build --check, check (geometry and face part of 14.2), previews
(rig v3: the style set) and stats; G3 adds the animation libraries to build and check, the animation rules and the
timing agreement to check, `anim` (list the libraries and their timings) and `anim-previews` (filmstrips and GIFs);
G2 adds preview/faces/sheet/gif. `check --style` also checks the style fixtures in herosrc/style (built in memory,
never written)."""
import argparse
import io
import json
import os
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
    if getattr(args, "style", False):
        ids = list(ids) + build.style_ids()
    return ids


def _library(check):
    """Build (or check) the shared animation libraries; returns the number of problems."""
    files = build.library_files()
    if check:
        problems = build.check_library_files(files)
        for p in problems:
            print("DIFF " + p)
        if not problems:
            print("OK   animation libraries: committed files match a fresh build")
        return len(problems)
    build.write_files(files)
    for p in build.stale_library_files(files):
        print("NOTE %s: stale output (delete it)" % p)
    print("built animation libraries: %s" % ", ".join(sorted(os.path.basename(p) for p in files
                                                             if p.endswith(".animation.json"))))
    return 0


def cmd_build(args):
    bad = _library(args.check)
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


def _anim_problems(hid, b):
    """The animation rules (13.6) on the hero's own file and the timing agreement of its file."""
    from anim import library as animlib
    from anim import quality
    hero = b.hero
    chain = [animlib.style(animlib.style_of(hero)), animlib.core()]

    def lookup(name):
        for built in [b.animations] + chain:
            if name in built.by_name:
                return built.by_name[name]
        return None

    declared = [n for n in b.model.bones]
    out = quality.check_built(b.animations, lookup, declared, own_file=True)
    out += quality.check_timing(json.loads(json.dumps(b.animations.animation_json())),
                                json.loads(json.dumps(b.animations.timing_json())))
    return out


def _library_problems():
    from anim import library as animlib
    from anim import quality
    from util import dumps_json
    out = []
    core = animlib.core()
    built = [core] + [animlib.style(s) for s in build.library_styles()]
    for lib in built:
        def lookup(name, lib=lib):
            return lib.by_name.get(name) or core.by_name.get(name)
        out += ["%s: %s" % (lib.file, p) for p in quality.check_built(lib, lookup)]
        out += ["%s: %s" % (lib.file, p) for p in quality.check_timing(json.loads(dumps_json(lib.animation_json())),
                                                                      json.loads(dumps_json(lib.timing_json())))]
    return out


def cmd_check(args):
    bad = 0
    lib = _library_problems() + build.check_library_files(build.library_files())
    for p in lib:
        print("ERROR animation libraries: %s" % p)
    if not lib:
        print("OK    animation libraries")
    bad += len(lib)
    for hid in _ids(args):
        files, b = build.build_files(hid)
        paths = build.out_paths(hid)
        geo = GeoModel(json.loads(files[paths["geo"]]))
        tex = np.array(Image.open(io.BytesIO(files[paths["texture"]])).convert("RGBA"))
        problems = validate.check_built(geo, tex, asymmetric=b.spec["asymmetric"])
        problems += _anim_problems(hid, b)
        if hid in build.hero_ids():
            problems += build.check_files(hid, files)
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


def cmd_anim(args):
    """The animation libraries: each animation's length, loop, keyed bones and markers (from the timing data)."""
    from anim import library as animlib
    libs = [animlib.core()] + [animlib.style(s) for s in build.library_styles()]
    for lib in libs:
        print("%s (%d animations)" % (lib.file, len(lib.clips)))
        timing = lib.timing_json()["animations"]
        for clip in lib.clips:
            t = timing[clip.name]
            keys = sum(len(tr.keys) for tr in clip.tracks.values())
            marks = ", ".join("%s@%.2f" % (e["type"] + (":" + e["value"] if e.get("value") else ""), e["t"])
                              for e in t["events"])
            fr = t.get("frames")
            print("  %-14s %5.2f s %-5s %-6s %2d bones %4d keys%s%s" % (
                clip.name, clip.length, t["loop"], t["kind"], len(clip.bones()), keys,
                "  frames %.2f/%.2f/%.2f" % (fr["windup"], fr["active"], fr["recovery"]) if fr else "",
                "  " + marks if marks else ""))
    return 0


def cmd_anim_previews(args):
    from anim import preview
    out = args.out or preview.ANIM_PREVIEW_DIR
    for k, p in sorted(preview.write_all(out, gifs=not args.no_gif).items()):
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
    c.add_argument("--style", action="store_true", help="also check the style fixtures (herosrc/style)")
    c.set_defaults(fn=cmd_check)
    v = sub.add_parser("previews", help="write the style preview set (bases, lineup, face close-ups, character "
                                        "sheets, test rig, fallback) to docs/heroes-v2/previews/style")
    v.add_argument("--out", default=None)
    v.set_defaults(fn=cmd_previews)
    a = sub.add_parser("anim", help="list the animation libraries (lengths, keys, markers, frames)")
    a.set_defaults(fn=cmd_anim)
    ap = sub.add_parser("anim-previews", help="write animation filmstrips and GIFs to docs/heroes-v2/previews/anim")
    ap.add_argument("--out", default=None)
    ap.add_argument("--no-gif", action="store_true")
    ap.set_defaults(fn=cmd_anim_previews)
    s = sub.add_parser("stats", help="budgets per hero")
    s.add_argument("ids", nargs="*")
    s.add_argument("--style", action="store_true", help="include the style fixtures")
    s.set_defaults(fn=cmd_stats)
    args = p.parse_args(argv)
    try:
        return args.fn(args)
    except HerogenError as e:
        print("ERROR " + str(e), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
