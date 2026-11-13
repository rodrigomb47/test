"""Command line (design 13.7). G1 provides build, build --check, check (geometry and face part of 14.2), previews
(rig v3: the style set) and stats; G3 adds the animation libraries to build and check, the animation rules and the
timing agreement to check, `anim` (list the libraries and their timings) and `anim-previews` (filmstrips and GIFs);
G2 adds `preview`, `gif`, `faces`, `sheet` (written to build/herogen/ unless --out says otherwise) and
`render-previews` (the owner's set, render/showcase.py); G5 adds `anim-previews --styles` (the style libraries'
films and sheets). `check --style` also checks the style fixtures in
herosrc/style (built in memory, never written). G6a/b add the coplanar-face rule to `check` and `lint` (the parts
library on every base and the sample outfits, lint.py). G10 adds `anim-previews --locomotion` (the mobility set's
films and sheets). G7 makes `check` cover all of 14.2: the file-level rules of assets.py on what the mod ships (the
ones the Java HeroAssetsTest mirrors), `--slice`, and the distinctness NOTEs of distinct.py."""
import argparse
import json
import os
import sys

import numpy as np

import build
import palette
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


def tie_problems(hero_id, files, b, hint=True):
    """The coplanar-face rule (G6b): two coplanar faces of different colour z-fight in game (the preview renderer
    would hide it by drawing the earlier one), so any such pixel in the hero's stances, from six directions, is an
    error. `herogen preview <id> --ties` paints them magenta."""
    from anim import library as animlib
    from render.scene import HeroAssets
    files = dict(files)
    files.update(animlib.core().files())
    files.update(animlib.style(animlib.style_of(b.hero)).files())
    pairs = HeroAssets(hero_id, files, b.hero).tie_report()
    if not pairs:
        return []
    worst = sorted(pairs.items(), key=lambda kv: (-kv[1], kv[0]))
    where = "; herogen preview %s --ties shows them" % hero_id if hint else ""
    return ["coplanar faces of different colour (they z-fight in game): %s (pixels at 6x over six views%s)"
            % (", ".join("%s/%s %d" % (k[0], k[1], n) for k, n in worst[:6]), where)]


def _check_ids(args):
    """The heroes `check` covers: the ids given (or all), `--style` adds the style fixtures, `--slice` the eight slice
    heroes (SLICE_PLAN 6.3) that have a source in herosrc/heroes."""
    import assets
    if getattr(args, "slice", False):
        have = set(build.hero_ids())
        ids = [h for h in assets.slice_ids() if h in have]
        missing = [h for h in assets.slice_ids() if h not in have]
        if missing:
            print("NOTE  slice heroes without a source in herosrc/heroes yet (C1-C4): %s" % ", ".join(missing))
        return ids + list(args.ids or []) + (build.style_ids() if args.style else [])
    return _ids(args)


def cmd_check(args):
    """14.2 (G7): the animation libraries; then per hero the file-level rules on what the mod ships (assets.py: the
    rules the Java HeroAssetsTest mirrors), the animation rules on its built clips, the coplanar rule and the
    reproducible build; then the distinctness NOTEs (distinct.py)."""
    import assets
    import distinct
    from anim import library as animlib
    bad = 0
    lib = _library_problems() + build.check_library_files(build.library_files())
    for p in lib:
        print("ERROR animation libraries: %s" % p)
    if not lib:
        print("OK    animation libraries")
    bad += len(lib)
    table = assets.action_table()
    looks = []
    committed = set(build.hero_ids())
    for hid in _check_ids(args):
        files, b = build.build_files(hid)
        definition, _ = assets.definition_of(hid)
        if hid in committed:
            # what the mod ships (build.check_files below says it is what the sources build)
            problems, notes = assets.check_files(hid, assets.committed_files(hid), definition, table)
        else:
            shipped = dict(files)
            shipped.update(animlib.core().files())
            shipped.update(animlib.style(animlib.style_of(b.hero)).files())
            problems, notes = assets.check_files(hid, shipped, definition, table)
        problems += _anim_problems(hid, b)
        if not args.no_ties:
            problems += tie_problems(hid, files, b)
        if hid in committed:
            problems += build.check_files(hid, files)
        for p in problems:
            print("ERROR %s: %s" % (hid, p))
        for n in notes:
            print("NOTE  %s: %s" % (hid, n))
        if not problems:
            print("OK    %s" % hid)
        bad += len(problems)
        archetype = (definition or {}).get("archetype") or b.hero.get("archetype")
        if archetype and archetype != "fixture" and not hid.startswith("_") and not args.no_distinct:
            from render.scene import HeroAssets
            a = HeroAssets(hid, dict(files, **animlib.core().files(),
                                     **animlib.style(animlib.style_of(b.hero)).files()), b.hero)
            looks.append(distinct.Look(hid, archetype, a, b.ramps))
    for n in distinct.notes(looks):
        print("NOTE  distinct: %s" % n)
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
    if args.core:                       # G4: the core library's previews (anim/core_preview.py)
        from anim import core_preview
        out = args.out or core_preview.OUT_DIR
        if args.check:
            problems = core_preview.check(out, args.groups or None, gifs=not args.no_gif)
            for p in problems:
                print("DIFF " + p)
            if not problems:
                print("OK   %s: the committed previews match a fresh render" % out)
            return 1 if problems else 0
        for k, p in sorted(core_preview.write_all(out, args.groups or None, gifs=not args.no_gif).items()):
            print("%s: %s" % (k, p))
        return 0
    if args.locomotion:                 # G10: the mobility set's previews (anim/loco_preview.py)
        from anim import loco_preview
        out = args.out or loco_preview.OUT_DIR
        if args.check:
            problems = loco_preview.check(out, args.groups or None, gifs=not args.no_gif)
            for p in problems:
                print("DIFF " + p)
            if not problems:
                print("OK   %s: the committed previews match a fresh render" % out)
            return 1 if problems else 0
        for k, p in sorted(loco_preview.write_all(out, args.groups or None, gifs=not args.no_gif).items()):
            print("%s: %s" % (k, p))
        return 0
    if args.styles is not None:         # G5: the style libraries' previews (anim/style_preview.py)
        from anim import style_preview
        out = args.out or style_preview.OUT_DIR
        styles = args.styles or None
        if args.check:
            problems = style_preview.check(out, styles, gifs=not args.no_gif)
            for p in problems:
                print("DIFF " + p)
            if not problems:
                print("OK   %s: the committed previews match a fresh render" % out)
            return 1 if problems else 0
        for k, p in sorted(style_preview.write_all(out, styles, gifs=not args.no_gif).items()):
            print("%s: %s" % (k, p))
        return 0
    out = args.out or preview.ANIM_PREVIEW_DIR
    for k, p in sorted(preview.write_all(out, gifs=not args.no_gif).items()):
        print("%s: %s" % (k, p))
    return 0


# ------------------------------------------------------------------ G2: preview, gif, faces, sheet, render-previews

PREVIEW_OUT = os.path.join("build", "herogen")
VIEWS = {"front": (0.0, None), "34": (-34.0, None), "34back": (145.0, None), "side": (-90.0, None),
         "left": (-90.0, None), "right": (90.0, None), "back": (180.0, None), "top": (0.0, 89.0)}


def _view(args, scale=None):
    from render import raster
    from render.scene import PITCH
    if args.yaw is not None:
        yaw, pitch = args.yaw, None
    else:
        if args.view not in VIEWS:
            raise HerogenError("--view %r: one of %s (or --yaw DEG)" % (args.view, ", ".join(VIEWS)))
        yaw, pitch = VIEWS[args.view]
    pitch = args.pitch if args.pitch is not None else (pitch if pitch is not None else PITCH)
    return raster.View(yaw, pitch, scale or args.scale)


def _assets(hero_id, committed=False):
    from render.scene import HeroAssets
    return HeroAssets.committed(hero_id) if committed else HeroAssets.build(hero_id)


def _out(args, default):
    path = args.out or os.path.join(PREVIEW_OUT, default)
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    return path


def cmd_preview(args):
    """PNG(s) of a hero: a pose (the stance, or an animation at --t, or --frames evenly over it) from one view."""
    from PIL import Image

    from render.scene import to_image
    from render.sheet import BG
    a = _assets(args.id, args.committed)
    view = _view(args)
    drawn = args.drawn or bool(args.action)
    if args.anim or args.action:
        pb = a.playback(args.anim, args.action, 0.0)
        length = pb.length()
        times = [args.t] if args.frames is None else [length * i / args.frames for i in range(args.frames)]
    else:
        pb, times = None, [0.0]
    face_sim = a.face_sim(args.mood)
    frames = []
    for t in times:
        pose = pb.pose(t) if pb else a.stance(drawn)
        face = face_sim.frames(t, pose, blink_ok=False)
        img = to_image(a.render(view, pose, face=face, phase=args.phase, drawn=drawn,
                                brightness=0.25 if args.night else 1.0))
        if args.ties:                   # coplanar faces of different colour (z-fighting in game) in magenta
            mask, pairs, _ = a.ties(view, pose, face=face, phase=args.phase, drawn=drawn)
            arr = np.array(img)
            arr[mask] = (255, 0, 255, 255)
            img = Image.fromarray(arr, "RGBA")
            print("t=%.2f: %d coplanar pixels%s" % (t, int(mask.sum()), "".join(
                "\n  %s/%s %d" % (k[0], k[1], n) for k, n in sorted(pairs.items(), key=lambda kv: -kv[1]))))
        frames.append(img)
    if len(frames) == 1:
        out = frames[0]
    else:
        out = Image.new("RGBA", (sum(f.width + 8 for f in frames) + 8, max(f.height for f in frames) + 16), BG)
        x = 8
        for f in frames:
            out.alpha_composite(f, (x, 8))
            x += f.width + 8
    if not args.transparent:
        bg = Image.new("RGBA", out.size, BG)
        bg.alpha_composite(out)
        out = bg
    path = _out(args, "%s_%s.png" % (args.id, (args.action or args.anim or "stance").replace(".", "_")))
    out.save(path, format="PNG", optimize=True)
    print(path)
    return 0


def cmd_gif(args):
    """An animated preview of one or more heroes."""
    from render import gif
    heroes = [_assets(h, args.committed) for h in args.ids]
    view = _view(args)
    actors = []
    for i, a in enumerate(heroes):
        pb = a.playback(args.anim, args.action, args.lead if args.action else 0.0)
        actors.append(gif.Actor(a, pb, a.face_sim(args.mood, i), view, drawn=bool(args.action) or args.drawn))
    pb = actors[0].pb
    t1 = args.t1 if args.t1 is not None else (pb.length() + (args.lead if args.action else 0.0)
                                              if args.action else pb.base.length)
    frames = gif.film(actors, gif.ticks(args.t0, t1, args.fps), args.action or args.anim or "move.idle")
    path = _out(args, "%s_%s.gif" % ("_".join(args.ids), (args.action or args.anim or "idle").replace(".", "_")))
    gif.write_gif(frames, path, fps=args.fps)
    print(path)
    return 0


def cmd_faces(args):
    """Every eye, mouth and cheek frame enlarged with the 12 moods (from the texture), and the face strip (rendered)."""
    import previews
    from render import sheet
    a = _assets(args.id, args.committed)
    p1 = _out(args, "%s_face_frames.png" % args.id)
    previews.faces_sheet(args.id, p1)
    p2 = p1.replace(".png", "_strip.png")
    sheet.face_strip([a], p2)
    print(p1)
    print(p2)
    return 0


def cmd_sheet(args):
    """Contact sheet(s), 24 heroes each."""
    from render import sheet
    ids = list(args.ids) or build.hero_ids()
    if args.style:
        ids = build.style_ids() + ids
    heroes = [_assets(h, args.committed) for h in ids]
    for path in sheet.contact_sheet(heroes, _out(args, "contact_sheet.png")):
        print(path)
    return 0


def cmd_render_previews(args):
    from render import showcase
    out = args.out or showcase.OUT_DIR
    if args.check:
        problems = showcase.check(out, gifs=not args.no_gif, only=args.only or None)
        for p in problems:
            print("DIFF " + p)
        if not problems:
            print("OK   %s: the committed previews match a fresh render" % out)
        return 1 if problems else 0
    for k, p in sorted(showcase.write_all(out, gifs=not args.no_gif, only=args.only or None).items()):
        print("%s: %s" % (k, p))
    return 0


def cmd_lint(args):
    """The parts library on every base and the sample outfits (lint.py)."""
    import time

    import lint
    t0 = time.time()
    count = [0]

    def progress(what, found):
        count[0] += 1
        if found or args.verbose:
            print("%s %s" % ("FAIL" if found else "ok  ", what))
    problems = lint.run(args.parts or None, ties=not args.no_ties, outfits=not args.no_outfits,
                        workers=args.jobs, progress=progress)
    for p in problems:
        print("ERROR " + p)
    print("%s: %d builds and checks, %d problems (%.0f s)" % ("OK" if not problems else "FAILED", count[0],
                                                               len(problems), time.time() - t0))
    return 1 if problems else 0


def cmd_parts_previews(args):
    """The parts sheets (G6a/b): every head and body part and the sample outfits on the six bases."""
    from render import partsheet
    out = args.out or partsheet.OUT_DIR
    if args.check:
        problems = partsheet.check(out, args.only or None)
        for p in problems:
            print("DIFF " + p)
        if not problems:
            print("OK   %s: the committed parts sheets match a fresh render" % out)
        return 1 if problems else 0
    for k, p in sorted(partsheet.write_all(out, args.only or None).items()):
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
    c = sub.add_parser("check", help="asset checks (HEROES_V2 14.2: files, geometry, UVs, face frames, action codes, "
                                     "move lists and frames, animations, coplanar faces, reproducibility; "
                                     "distinctness NOTEs)")
    c.add_argument("ids", nargs="*")
    c.add_argument("--all", action="store_true")
    c.add_argument("--style", action="store_true", help="also check the style fixtures (herosrc/style)")
    c.add_argument("--slice", action="store_true", help="the eight slice heroes (SLICE_PLAN 6.3) that have a source")
    c.add_argument("--no-ties", action="store_true", help="skip the coplanar-face rule (it renders six views)")
    c.add_argument("--no-distinct", action="store_true", help="skip the distinctness NOTEs (they render two views)")
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
    ap.add_argument("--core", action="store_true", help="the core library's previews (G4) in "
                                                        "docs/heroes-v2/previews/anim/core")
    ap.add_argument("--groups", nargs="*", help="with --core: movement, work, work_home, emotes, combat, "
                                                "combat_katana; with --locomotion: jumps, climb_glide, flight, "
                                                "ground, ride, hero_natsuho, hero_yugen, hero_kagenori, "
                                                "hero_ambrose")
    ap.add_argument("--locomotion", action="store_true", help="the mobility set's previews (G10) in "
                                                              "docs/heroes-v2/previews/anim/locomotion")
    ap.add_argument("--styles", nargs="*", default=None, help="the style libraries' previews (G5) in "
                                                              "docs/heroes-v2/previews/anim/styles (all, or the "
                                                              "styles named)")
    ap.add_argument("--check", action="store_true", help="with --core, --styles or --locomotion: render in a "
                                                         "temporary folder and "
                                                         "fail if a committed preview differs")
    ap.set_defaults(fn=cmd_anim_previews)
    for name, fn, hlp in (("preview", cmd_preview, "render a hero to PNG (a pose, an animation frame or frames)"),
                          ("gif", cmd_gif, "an animated preview of one or more heroes")):
        q = sub.add_parser(name, help=hlp)
        if name == "preview":
            q.add_argument("id")
            q.add_argument("--t", type=float, default=0.0, help="animation time (s)")
            q.add_argument("--frames", type=int, default=None, help="this many frames evenly over the animation")
            q.add_argument("--phase", type=int, default=1)
            q.add_argument("--night", action="store_true", help="light map at night (shows the glow layer)")
            q.add_argument("--transparent", action="store_true")
            q.add_argument("--ties", action="store_true", help="paint coplanar faces of different colour (they "
                                                              "z-fight in game) magenta and list their bones")
        else:
            q.add_argument("ids", nargs="+")
            q.add_argument("--t0", type=float, default=0.0)
            q.add_argument("--t1", type=float, default=None)
            q.add_argument("--fps", type=int, default=20)
            q.add_argument("--lead", type=float, default=0.3, help="stance before the action starts (s)")
        q.add_argument("--anim", default=None, help="base animation (move.idle, move.walk, combat.idle, ...)")
        q.add_argument("--action", default=None, help="action animation over the base (attack.1, ...)")
        q.add_argument("--view", default="34", help="front, 34, 34back, side (left), right, back, top")
        q.add_argument("--yaw", type=float, default=None, help="camera angle round the hero (deg), overrides --view")
        q.add_argument("--pitch", type=float, default=None)
        q.add_argument("--mood", default="calm")
        q.add_argument("--scale", type=int, default=4)
        q.add_argument("--drawn", action="store_true", help="weapon drawn")
        q.add_argument("--committed", action="store_true", help="read the committed files instead of building")
        q.add_argument("--out", default=None)
        q.set_defaults(fn=fn)
    f = sub.add_parser("faces", help="a hero's face frames, moods and face strip")
    f.add_argument("id")
    f.add_argument("--committed", action="store_true")
    f.add_argument("--out", default=None)
    f.set_defaults(fn=cmd_faces)
    sh = sub.add_parser("sheet", help="contact sheet(s): 24 heroes per sheet")
    sh.add_argument("ids", nargs="*")
    sh.add_argument("--style", action="store_true", help="include the style fixtures")
    sh.add_argument("--committed", action="store_true")
    sh.add_argument("--out", default=None)
    sh.set_defaults(fn=cmd_sheet)
    rp = sub.add_parser("render-previews", help="write the G2 preview set to docs/heroes-v2/previews/render")
    rp.add_argument("--out", default=None)
    rp.add_argument("--no-gif", action="store_true")
    rp.add_argument("--only", nargs="*", choices=("lineups", "turnarounds", "faces", "contact", "gifs"))
    rp.add_argument("--check", action="store_true", help="render in a temporary folder and fail if a committed "
                                                         "preview differs (stale)")
    rp.set_defaults(fn=cmd_render_previews)
    li = sub.add_parser("lint", help="every part on every base (builds, checks, coplanar faces) and the sample "
                                     "outfits of herosrc/style/outfits.json")
    li.add_argument("parts", nargs="*", help="part ids or globs (default: the whole library and the outfits)")
    li.add_argument("--no-ties", action="store_true", help="skip the coplanar-face rule")
    li.add_argument("--no-outfits", action="store_true", help="skip the sample outfits")
    li.add_argument("--jobs", type=int, default=None, help="worker processes (default: up to 4)")
    li.add_argument("-v", "--verbose", action="store_true", help="list every build, not only the failing ones")
    li.set_defaults(fn=cmd_lint)
    pp = sub.add_parser("parts-previews", help="write the parts sheets (head parts, body parts, sample outfits on "
                                              "the six bases) to docs/heroes-v2/previews/parts")
    pp.add_argument("--out", default=None)
    pp.add_argument("--only", nargs="*", help="sheet file names (head_hair.png, outfits.png, ...)")
    pp.add_argument("--check", action="store_true", help="render in a temporary folder and fail if a committed "
                                                         "sheet differs (stale)")
    pp.set_defaults(fn=cmd_parts_previews)
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
