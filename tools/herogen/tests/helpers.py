"""Shared test setup: puts tools/herogen on sys.path and builds small fixtures."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
HEROGEN = os.path.dirname(HERE)
if HEROGEN not in sys.path:
    sys.path.insert(0, HEROGEN)


def mannequin(base_id, parts=None, **art):
    family = "female" if base_id.startswith("f_") else "male"
    a = {"base": family, "build": base_id.split("_", 1)[1], "parts": parts or []}
    a.update(art)
    return {"format": 2, "id": "t_" + base_id, "gender": family, "art": a}
