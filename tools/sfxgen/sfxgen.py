#!/usr/bin/env python3
"""Faction Clash hero sound generator.  Run from anywhere:  python3 tools/sfxgen/sfxgen.py <command>

Commands: build [--check], check, preview [sets], render <names>, measure <files|minecraft:ids>, vanilla-ids,
subtitles, stats.  See docs/heroes-v2/SOUND.md.  Needs Python 3.10+, NumPy, Pillow and ffmpeg with libvorbis and
libmp3lame; nothing else.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)

if sys.version_info < (3, 10):
    sys.exit("sfxgen needs Python 3.10 or newer")
try:
    import numpy  # noqa: F401
    import PIL  # noqa: F401
except ImportError as e:
    sys.exit("sfxgen needs NumPy and Pillow: %s" % e)

from cli import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())
