"""The animation libraries and a hero's own animation file (HEROES_V2.md 4.5, 13.6).

Sources (herosrc/anim/):
  poses/*.json          the pose library
  core/*.json           the core library recipes        -> animations/hero/_core.animation.json
  styles/<style>.json   a style library's recipes        -> animations/hero/_style_<style>.animation.json
  secondary.json        the spring groups of the follow-through bake
A hero's `animations.signatures` and `animations.overrides` (and its own `animations.poses`) become
animations/hero/<id>.animation.json. Each animation file has a server timing file beside the server data:
data/factionclash/hero_timings/<file>.json (writer.py). GeckoLib resolves a name through the hero's file, its style
library, then the core, so every file of that chain is always written (an empty one is valid).

Clips are built on the reference skeleton (skeleton.REFERENCE_BASE) and are the same for every body form.

A style file may carry its own `poses` (G5: the in-between keys of its clips, beside the hand poses of
herosrc/anim/poses/<style>.json); they are added to the pose library for that style's clips and for the own files of
its heroes (style_poses). Its `restyle_arms` {"pose": name, "clips": [...]} gives those restyled reactions the
recipe field `arms_over` (recipes.py): their arm offsets go over that pose (a lowered weapon arm) instead of the
guard, so a death, a kneel or a dizzy stagger does not fold the weapon into the head.

Restyled reactions (G4): a core recipe with `restyle: true` (hurt.heavy, stagger, stun, interrupt, dodge, intro,
victory, ...) starts and ends on "@stance", the first frame of the clip it settles on. The core expands it over
move.idle; every style library with a combat.idle also gets its own copy expanded over that stance (unless the style
defines the name itself), so a reaction played with the weapon drawn ends on the pose the base controller shows
and GeckoLib's snap at the action's end (HEROES_V2 4.8) is invisible.
"""
import functools
import glob
import os

from anim import poses as P
from anim import skeleton as S
from anim import writer
from anim.recipes import Expander
from rig import RIG_FILE
from util import HEROSRC, HerogenError, dumps_json, load_json, rel, sha256_bytes

ANIM_DIR = os.path.join(HEROSRC, "anim")
CORE_DIR = os.path.join(ANIM_DIR, "core")
STYLE_DIR = os.path.join(ANIM_DIR, "styles")
ANIM_OUT = "src/main/resources/assets/factionclash/animations/hero/%s.animation.json"
TIMING_OUT = "src/main/resources/data/factionclash/hero_timings/%s.json"
CORE = "_core"
STYLES = ("katana", "greatblade", "sword_shield", "twin_blades", "rapier", "polearm", "hammer", "axe", "scythe",
          "fists", "bow", "crossbow", "staff", "focus", "fan", "instrument")


def style_file(style):
    return "_style_" + style


def style_of(hero):
    style = (hero.get("animations") or {}).get("style", "fists")
    if style not in STYLES:
        raise HerogenError("%s: animations.style %r is unknown (%s)" % (hero.get("id"), style, ", ".join(STYLES)))
    return style


def chain_of(hero):
    """The animation files GeckoLib looks a name up in, in order."""
    return [hero["id"], style_file(style_of(hero)), CORE]


def out_paths(file_name):
    return {"animation": ANIM_OUT % file_name, "timing": TIMING_OUT % file_name}


def _recipes_in(paths):
    out, where = {}, {}
    for path in paths:
        for name, r in load_json(path).get("animations", {}).items():
            if name in out:
                raise HerogenError("%s: animation %s is also defined in %s" % (rel(path), name, where[name]))
            out[name] = r
            where[name] = rel(path)
    return _resolve_copies(out)


def _resolve_copies(recipes):
    """A recipe {"copy": name} is that recipe of the same file under its own name (an alias)."""
    out = dict(recipes)
    for name, r in recipes.items():
        if "copy" in r:
            src = recipes.get(r["copy"])
            if src is None or "copy" in src:
                raise HerogenError("animation %s: copy %r is not a recipe of the same library" % (name, r["copy"]))
            out[name] = dict(src, **{k: v for k, v in r.items() if k not in ("copy",)})
    return out


def restyled_recipes():
    """The core recipes every style with a combat.idle re-expands over its own stance."""
    return {n: r for n, r in _recipes_in(core_recipe_paths()).items() if r.get("restyle")}


def core_recipe_paths():
    return sorted(glob.glob(os.path.join(CORE_DIR, "*.json")))


def style_recipe_path(style):
    return os.path.join(STYLE_DIR, style + ".json")


def styles_with_recipes():
    return sorted(os.path.basename(p)[:-5] for p in glob.glob(os.path.join(STYLE_DIR, "*.json")))


def _order(recipes):
    """Base loops (the clips actions settle on) first, then action loops (stun.loop: what a one-shot leads into),
    then the rest; by name within each group."""
    def group(r):
        looping = r.get("loop") in (True, "loop")
        if looping and r.get("kind", "base") == "base":
            return 0
        return 1 if looping else 2
    return sorted(recipes, key=lambda n: (group(recipes[n]), n))


def _source_hash(paths):
    data = b""
    for p in sorted(set(paths)):
        if os.path.exists(p):
            with open(p, "rb") as f:
                data += rel(p).encode() + b"\0" + f.read() + b"\0"
    return sha256_bytes(data)


def _shared_sources():
    bases = sorted(set(S.FORM_BASES) | {S.REFERENCE_BASE})
    return ([os.path.join(HEROSRC, "rig", RIG_FILE), os.path.join(ANIM_DIR, "secondary.json"),
             os.path.join(HEROSRC, "bases", "_template.json")]
            + [os.path.join(HEROSRC, "bases", b + ".json") for b in bases]
            + sorted(glob.glob(os.path.join(P.POSE_DIR, "*.json"))))


@functools.lru_cache(maxsize=None)
def pose_library():
    return P.Library.load()


def reference_skeleton():
    return S.for_base(S.REFERENCE_BASE)


def form_skeletons():
    """One skeleton per body form (heights differ by form only; widths do not move the feet's heights)."""
    return [S.for_base(b) for b in S.FORM_BASES]


class Built:
    """One animation file: its clips (in build order), the JSON documents and their source hash."""

    def __init__(self, file_name, clips, source_hash):
        self.file = file_name
        self.clips = clips
        self.by_name = {c.name: c for c in clips}
        self.source_hash = source_hash

    def animation_json(self):
        return writer.animation_file(self.clips)

    def timing_json(self):
        return writer.timing_file(self.file, self.clips, self.source_hash)

    def files(self):
        paths = out_paths(self.file)
        return {paths["animation"]: dumps_json(self.animation_json()).encode("utf-8"),
                paths["timing"]: dumps_json(self.timing_json()).encode("utf-8")}


def _build(file_name, recipes, poses, fallbacks, default_settle, sources):
    built = {}

    def lookup(name):
        if name in built:
            return built[name]
        for fb in fallbacks:
            if name in fb.by_name:
                return fb.by_name[name]
        return None

    ex = Expander(poses, reference_skeleton(), lookup, default_settle, form_skeletons())
    clips = []
    for name in _order(recipes):
        clip = ex.expand(name, recipes[name], "%s: %s" % (file_name, name))
        built[name] = clip
        clips.append(clip)
    return Built(file_name, clips, _source_hash(sources))


@functools.lru_cache(maxsize=None)
def core():
    paths = core_recipe_paths()
    return _build(CORE, _recipes_in(paths), pose_library(), [], "move.idle", _shared_sources() + paths)


@functools.lru_cache(maxsize=None)
def style_poses(style_name):
    """The pose library a style's clips are expanded with (G5): herosrc/anim/poses plus the style file's own `poses`,
    the in-between keys of its clips. Those are not hand poses (grips.py checks the hand poses of
    herosrc/anim/poses/<style>.json); grips.animation_problems checks the props through every frame of the clips
    instead. A style pose may not reuse a pose library name."""
    lib = pose_library()
    path = style_recipe_path(style_name)
    if not os.path.exists(path):
        return lib
    own = load_json(path).get("poses") or {}
    clash = sorted(n for n in own if n in lib.specs)
    if clash:
        raise HerogenError("%s: poses %s are already in herosrc/anim/poses" % (rel(path), ", ".join(clash)))
    return lib.with_specs(own, "%s poses" % rel(path)) if own else lib


@functools.lru_cache(maxsize=None)
def style(style_name):
    if style_name not in STYLES:
        raise HerogenError("style %r is unknown" % style_name)
    path = style_recipe_path(style_name)
    recipes = {}
    if os.path.exists(path):
        data = load_json(path)
        if data.get("style") != style_name:
            raise HerogenError("%s: style field is %r" % (rel(path), data.get("style")))
        recipes = _recipes_in([path])
    c = core()
    settle = "combat.idle" if "combat.idle" in recipes else "move.idle"
    sources = _shared_sources() + [path]
    if "combat.idle" in recipes:
        arms = (data.get("restyle_arms") or {}) if os.path.exists(path) else {}
        unknown = sorted(set(arms.get("clips", ())) - set(restyled_recipes()))
        if unknown:
            raise HerogenError("%s: restyle_arms names %s, which are not restyled reactions" % (rel(path),
                                                                                              ", ".join(unknown)))
        for name, r in restyled_recipes().items():
            if name in arms.get("clips", ()):
                r = dict(r, arms_over=arms["pose"])
            recipes.setdefault(name, r)
        sources += core_recipe_paths()
    return _build(style_file(style_name), recipes, style_poses(style_name), [c], settle, sources)


def hero(hero_obj, hero_source=None):
    """The hero's own animation file from animations.signatures and animations.overrides."""
    anim = hero_obj.get("animations") or {}
    recipes = {}
    for key in ("signatures", "overrides"):
        for name, r in (anim.get(key) or {}).items():
            if name in recipes:
                raise HerogenError("%s: animation %s is both a signature and an override" % (hero_obj["id"], name))
            recipes[name] = r
    lib = style_poses(style_of(hero_obj))
    if anim.get("poses"):
        lib = lib.with_specs(anim["poses"], "%s animations.poses" % hero_obj["id"])
    st = style(style_of(hero_obj))
    settle = "combat.idle" if "combat.idle" in st.by_name else "move.idle"
    sources = _shared_sources() + ([hero_source] if hero_source else [])
    if recipes:
        sources.append(style_recipe_path(style_of(hero_obj)))      # its clips may use the style's own poses
    return _build(hero_obj["id"], recipes, lib, [st, core()], settle, sources)


def library_files(styles):
    """{path: bytes} of the core library and the given style libraries (and their timing files)."""
    files = dict(core().files())
    for s in sorted(set(styles)):
        files.update(style(s).files())
    return files


def resolve(hero_obj, name):
    """The clip GeckoLib plays for `name` on this hero (its file, its style, the core), or None."""
    for built in (hero(hero_obj), style(style_of(hero_obj)), core()):
        if name in built.by_name:
            return built.by_name[name]
    return None


def clear_caches():
    core.cache_clear()
    style.cache_clear()
    style_poses.cache_clear()
    pose_library.cache_clear()
    S.for_base.cache_clear()
