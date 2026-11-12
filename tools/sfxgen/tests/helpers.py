"""Shared test setup: puts tools/sfxgen on sys.path."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SFXGEN = os.path.dirname(HERE)
if SFXGEN not in sys.path:
    sys.path.insert(0, SFXGEN)
