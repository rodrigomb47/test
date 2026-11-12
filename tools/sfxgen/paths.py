"""Repository paths used by sfxgen."""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, "herosrc", "sfx")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "factionclash")
SOUNDS_DIR = os.path.join(ASSETS, "sounds")
SOUNDS_JSON = os.path.join(ASSETS, "sounds.json")
INDEX_JSON = os.path.join(SOUNDS_DIR, "hero_sfx.json")
PREVIEWS = os.path.join(ROOT, "docs", "heroes-v2", "previews", "sound")
NAMESPACE = "factionclash"
