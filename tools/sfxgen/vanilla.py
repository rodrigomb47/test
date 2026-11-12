"""Vanilla 1.21.1 sound events: the list of valid ids and, for measurement only, the decoded vanilla files.

The ids come from the game's own `minecraft/sounds.json` in the asset index the build uses
(`build/moddev/minecraft_assets.properties` names the index and the assets root; the NeoForm runtime cache is the
fallback). When neither is present (a fresh clone without a Gradle run), `vanilla_sound_ids.txt` beside this file is
used: a list of event NAMES only, written by `sfxgen vanilla-ids` from the same artifacts. No vanilla audio is ever
copied into the repository; `vanilla_samples` decodes files into memory for loudness comparisons.
"""
import json
import os

from paths import ROOT

HERE = os.path.dirname(os.path.abspath(__file__))
ID_LIST = os.path.join(HERE, "vanilla_sound_ids.txt")
GAME_VERSION = "1.21.1"


def _assets_root():
    props = os.path.join(ROOT, "build", "moddev", "minecraft_assets.properties")
    index, root = None, None
    if os.path.exists(props):
        for line in open(props, encoding="utf-8"):
            line = line.strip()
            if line.startswith("asset_index="):
                index = line.split("=", 1)[1]
            elif line.startswith("assets_root="):
                root = line.split("=", 1)[1]
    if root is None:
        cand = os.path.expanduser("~/.gradle/caches/neoformruntime/assets")
        if os.path.isdir(cand):
            root = cand
    if root and index is None:
        idx = os.path.join(root, "indexes")
        if os.path.isdir(idx):
            names = sorted(n[:-5] for n in os.listdir(idx) if n.endswith(".json"))
            index = names[-1] if names else None
    if root and index and os.path.exists(os.path.join(root, "indexes", index + ".json")):
        return root, index
    return None, None


def _object(root, objects, name):
    h = objects[name]["hash"]
    return os.path.join(root, "objects", h[:2], h)


def game_sounds_json():
    """The parsed vanilla sounds.json, or None when the game artifacts are not on this machine."""
    root, index = _assets_root()
    if not root:
        return None
    objects = json.load(open(os.path.join(root, "indexes", index + ".json"), encoding="utf-8"))["objects"]
    if "minecraft/sounds.json" not in objects:
        return None
    path = _object(root, objects, "minecraft/sounds.json")
    if not os.path.exists(path):
        return None
    return json.load(open(path, encoding="utf-8"))


def sound_ids():
    """The set of vanilla event ids ("minecraft:entity.cat.hiss") and where they came from."""
    data = game_sounds_json()
    if data is not None:
        return {"minecraft:" + k for k in data}, "game artifacts"
    if os.path.exists(ID_LIST):
        ids = {l.strip() for l in open(ID_LIST, encoding="utf-8") if l.strip() and not l.startswith("#")}
        return ids, os.path.relpath(ID_LIST, ROOT)
    return None, "none"


def write_id_list():
    data = game_sounds_json()
    if data is None:
        raise RuntimeError("the game artifacts are not on this machine (run a Gradle build once)")
    with open(ID_LIST, "w", encoding="utf-8", newline="\n") as f:
        f.write("# Minecraft %s sound event ids (names only), from the game's sounds.json; sfxgen vanilla-ids\n"
                % GAME_VERSION)
        for k in sorted(data):
            f.write("minecraft:%s\n" % k)
    return len(data)


def event_files(event):
    """The vanilla files of one event, following `type: event` references: [(path, volume, pitch)], or None."""
    root, index = _assets_root()
    data = game_sounds_json()
    if data is None:
        return None
    objects = json.load(open(os.path.join(root, "indexes", index + ".json"), encoding="utf-8"))["objects"]
    out = []

    def walk(name, vol, pitch, depth=0):
        key = name.split(":", 1)[-1]
        ent = data.get(key)
        if ent is None or depth > 4:
            return
        for s in ent.get("sounds", []):
            if isinstance(s, str):
                s = {"name": s}
            v, p = vol * s.get("volume", 1.0), pitch * s.get("pitch", 1.0)
            if s.get("type") == "event":
                walk(s["name"], v, p, depth + 1)
                continue
            nm = s["name"].split(":", 1)[-1]
            obj = "minecraft/sounds/%s.ogg" % nm
            if obj in objects:
                out.append((_object(root, objects, obj), v, p))

    walk(event, 1.0, 1.0)
    return out


def vanilla_samples(event, sr=44100):
    """Decoded vanilla files of an event for MEASUREMENT (in memory): [(samples, volume, pitch)]."""
    import codec
    files = event_files(event) or []
    return [(codec.decode(p, sr)[0], v, pt) for p, v, pt in files]
