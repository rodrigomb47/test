#!/usr/bin/env python3
"""Faction Clash hero art generator.  Run from anywhere:  python3 tools/herogen/herogen.py <command>

Commands (G1): build [ids|--all] [--check], check [ids|--all], previews [--out DIR], stats [ids].
See HEROES_V2.md section 13 and docs/heroes-v2/notes/artgen.md.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)

if sys.version_info < (3, 10):
    sys.exit("herogen needs Python 3.10 or newer")
try:
    import numpy  # noqa: F401
    import PIL  # noqa: F401
except ImportError as e:
    sys.exit("herogen needs Pillow and NumPy (pip install pillow numpy): %s" % e)

from cli import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())
