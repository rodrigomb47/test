"""Command line: build, check, preview, render, measure, vanilla-ids, subtitles, stats (SOUND.md section 10)."""
import argparse
import json
import os
import sys
import time

import analyze
import build
import codec
import preview
import recipe
import vanilla
from paths import PREVIEWS, ROOT, SRC

SCRATCH = os.path.join(ROOT, "build", "sfxgen")


def _cat(args):
    return recipe.load_all(getattr(args, "src", None) or SRC)


def cmd_build(args):
    t0 = time.time()
    cat = _cat(args)
    files, rendered = build.plan(cat, args.src or SRC, args.jobs)
    if args.check:
        missing, different, stale = build.compare(files)
        for p in missing:
            print("MISSING  %s" % build.rel(p))
        for p in different:
            print("DIFFERS  %s" % build.rel(p))
        for p in stale:
            print("STALE    %s" % build.rel(p))
        n = len(missing) + len(different) + len(stale)
        print("build --check: %d files, %d out of date (%.1f s)" % (len(files), n, time.time() - t0))
        return 1 if n else 0
    changed, removed = build.write(files)
    total = sum(len(d) for p, d in files.items() if p.endswith(".ogg"))
    print("built %d sounds (%d files, %.1f KB of Ogg), %d written, %d removed (%.1f s)" % (
        sum(1 for _ in cat.sounds()), len(files), total / 1024.0, changed, removed, time.time() - t0))
    return 0


def cmd_check(args):
    import check
    return check.run(_cat(args), verbose=args.verbose, decode=not args.fast)


def _select(cat, names):
    out = []
    for n in names:
        if n in cat.sets:
            out.extend(cat.sets[n].sounds.values())
            continue
        hit = [s for s in cat.sounds() if s.event == n or s.event.endswith("." + n) or "%s:%s" % (s.set, s.name) == n]
        if not hit:
            raise SystemExit("no sound or set %r" % n)
        out.extend(hit)
    return out


def cmd_render(args):
    """Render sounds to WAV and a sheet in build/sfxgen (for review; nothing in the repository changes)."""
    cat = _cat(args)
    sounds = _select(cat, args.names)
    r = recipe.Renderer(cat)
    out = args.out or SCRATCH
    os.makedirs(out, exist_ok=True)
    items = []
    for s in sounds:
        if not s.generated:
            refs = s.vanilla or [{"event": "%s (alias)" % a["sound"]} for a in s.alias]
            print("%-48s %s: %s" % (s.event, "vanilla" if s.vanilla else "alias", ", ".join(v["event"] for v in refs)))
            continue
        for k in range(s.variants):
            x, rate = r.final(s, k)
            name = "%s_%d" % (s.event, k + 1)
            if args.wav:
                import wave
                with wave.open(os.path.join(out, name + ".wav"), "wb") as w:
                    w.setnchannels(1)
                    w.setsampwidth(2)
                    w.setframerate(rate)
                    w.writeframes(codec.to_pcm16(x))
            m = analyze.measure(x, rate)
            print("%-48s %5.2fs Lm %6.1f Ls %6.1f tp %5.1f dc %+.4f cen %5.0f clicks %s edges %s/%s" % (
                name, m["duration"], m["lufs_m"], m["lufs_s"], m["true_peak_db"], m["dc"], m["bands"]["centroid"],
                m["clicks"] or "-", m["edges"]["first"], m["edges"]["last"]))
            items.append(("%s #%d" % (s.event.replace("hero.", ""), k + 1), x, rate))
    if items:
        p = os.path.join(out, (args.sheet or "render") + ".png")
        preview.sheet(items, "render: " + " ".join(args.names), path=p)
        print("sheet:", p)
        if args.mp3:
            preview.write_mp3([(x, sr) for _l, x, sr in items], os.path.join(out, (args.sheet or "render") + ".mp3"))
    return 0


def cmd_preview(args):
    import previews
    return previews.run(_cat(args), args.sets, args.out or PREVIEWS)


def cmd_measure(args):
    """Measure files or vanilla events (vanilla audio is decoded in memory only)."""
    for a in args.items:
        if a.startswith("minecraft:"):
            files = vanilla.event_files(a) or []
            if not files:
                print("%s: not found" % a)
            for p, v, pt in files:
                x, sr = codec.decode(p, 44100)
                m = analyze.measure(x, sr)
                eff = m["lufs_m"] + analyze.db(min(v, 1.0))
                print("%-44s %5.2fs Lm %6.1f (x vol %.2f: %6.1f) Ls %6.1f pk %5.1f cen %5.0f" % (
                    a, m["duration"], m["lufs_m"], v, eff, m["lufs_s"], m["peak_db"], m["bands"]["centroid"]))
        else:
            x, sr = codec.decode(a)
            print(json.dumps(analyze.measure(x, sr)))
    return 0


def cmd_vanilla_ids(args):
    n = vanilla.write_id_list()
    print("wrote %d ids to %s" % (n, os.path.relpath(vanilla.ID_LIST, ROOT)))
    return 0


def cmd_subtitles(args):
    """The en_us subtitle lines S2 merges into the language file (sorted JSON object)."""
    cat = _cat(args)
    print(json.dumps(build.subtitles(cat), indent=2, sort_keys=True, ensure_ascii=False))
    return 0


def cmd_stats(args):
    cat = _cat(args)
    from paths import SOUNDS_DIR
    rows = []
    for sid, st in sorted(cat.sets.items()):
        gen = [s for s in st.sounds.values() if s.generated]
        van = [s for s in st.sounds.values() if not s.generated]
        size = 0
        files = 0
        for s in gen:
            for stem in s.file_names():
                p = os.path.join(SOUNDS_DIR, stem + ".ogg")
                if os.path.exists(p):
                    size += os.path.getsize(p)
                    files += 1
        rows.append((sid, len(st.sounds), len(gen), len(van), files, size))
    tot = [0, 0, 0, 0, 0]
    print("%-22s %6s %9s %7s %6s %9s" % ("set", "events", "generated", "vanilla", "files", "KB"))
    for sid, a, b, c, f, size in rows:
        print("%-22s %6d %9d %7d %6d %9.1f" % (sid, a, b, c, f, size / 1024.0))
        for i, v in enumerate((a, b, c, f, size)):
            tot[i] += v
    print("%-22s %6d %9d %7d %6d %9.1f" % ("total", tot[0], tot[1], tot[2], tot[3], tot[4] / 1024.0))
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(prog="sfxgen", description="Faction Clash hero sound generator (SOUND.md)")
    ap.add_argument("--src", help="recipe folder (default herosrc/sfx)")
    sub = ap.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("build", help="render, encode and write the sounds, sounds.json and the index")
    b.add_argument("--check", action="store_true", help="compare with the files on disk, write nothing")
    b.add_argument("--jobs", type=int, default=None)
    b.set_defaults(fn=cmd_build)
    c = sub.add_parser("check", help="the quality and consistency rules (SOUND.md section 9)")
    c.add_argument("-v", "--verbose", action="store_true")
    c.add_argument("--fast", action="store_true", help="skip decoding the shipped files")
    c.set_defaults(fn=cmd_check)
    p = sub.add_parser("preview", help="MP3 bundles and spectrogram sheets for the owner")
    p.add_argument("sets", nargs="*")
    p.add_argument("--out")
    p.set_defaults(fn=cmd_preview)
    r = sub.add_parser("render", help="render sounds or sets to build/sfxgen for review")
    r.add_argument("names", nargs="+")
    r.add_argument("--out")
    r.add_argument("--sheet")
    r.add_argument("--wav", action="store_true")
    r.add_argument("--mp3", action="store_true")
    r.set_defaults(fn=cmd_render)
    m = sub.add_parser("measure", help="measure audio files or vanilla events")
    m.add_argument("items", nargs="+")
    m.set_defaults(fn=cmd_measure)
    v = sub.add_parser("vanilla-ids", help="write vanilla_sound_ids.txt from the game artifacts")
    v.set_defaults(fn=cmd_vanilla_ids)
    s = sub.add_parser("subtitles", help="print the en_us subtitle lines for S2")
    s.set_defaults(fn=cmd_subtitles)
    st = sub.add_parser("stats", help="events, files and sizes per set")
    st.set_defaults(fn=cmd_stats)
    args = ap.parse_args(argv)
    try:
        return args.fn(args)
    except (recipe.RecipeError, codec.CodecError) as e:
        print("error: %s" % e, file=sys.stderr)
        return 2
