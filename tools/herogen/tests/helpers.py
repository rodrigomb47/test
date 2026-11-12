"""Shared test setup: puts tools/herogen on sys.path and builds small fixtures."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
HEROGEN = os.path.dirname(HERE)
if HEROGEN not in sys.path:
    sys.path.insert(0, HEROGEN)


def mannequin(base_id, parts=None, build="standard", **art):
    """A bare hero on a rig v2 base id (form_f / form_m)."""
    form, fam = base_id.split("_")
    family = "female" if fam == "f" else "male"
    a = {"base": family, "form": form, "build": build, "parts": parts or []}
    a.update(art)
    return {"format": 2, "id": "t_" + base_id, "gender": family, "art": a}
