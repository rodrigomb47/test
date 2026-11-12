"""herogen unit tests. Run from the repository root:  python3 -m unittest discover tools/herogen/tests
or from tools/herogen:  python3 -m unittest"""
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
for _p in (_HERE, os.path.dirname(_HERE)):
    if _p not in sys.path:
        sys.path.insert(0, _p)
