"""`<id>.visual.json`: the client facts `HeroModel` and `HeroFace` read (design 12.1).

Fields: form, build, height_px and head_y (model pixels), render_scale (rig v3: model pixels to world pixels for the
whole model; the hitbox is height_px x render_scale x scale / 16 blocks), style library, blink profile, face frame layout and the resolved mood table (defaults with the hero's
overrides applied, so the Java side needs no copy of the defaults), move list (index -> animation name; filled by the
animation generator), states, phase looks, bone groups the renderer treats specially (tint, fine, phase, weapon,
items), texture facts and voice.
G3 adds `animation_chain` (the animation files GeckoLib looks a name up in, in order: the hero's own, its style
library, the core; HeroModel.getAnimationResource and its fallbacks) and `secondary` (the chains the hero's parts
declare, with their stiffness and damping, for the procedural lag of HeroModel.setCustomAnimations; the shared
libraries bake follow-through only for the rig's own chain bones).
"""
from anim.library import chain_of
from face import BLINK_OVER, BLINK_PROFILES, CHEEK_FRAMES, EYE_FRAMES, MOODS, MOUTH_FRAMES, mood_table
from util import GENERATOR, HerogenError

VISUAL_FORMAT = 1


def visual_json(hero, model, spec, textures, source_hash, moves=None, states=None):
    anim = hero.get("animations", {}) or {}
    blink = anim.get("blink", "normal")
    if blink not in BLINK_PROFILES:
        raise HerogenError("animations.blink %r is unknown (%s)" % (blink, ", ".join(BLINK_PROFILES)))
    rig = model.rig
    tpl = rig["uv_template"]
    planes = rig["face"]["planes"]
    regions = {}
    for plane in ("eyes", "mouth", "cheeks"):
        p = planes[plane]
        regions[plane] = {"u": tpl[plane][0], "v": tpl[plane][1], "w": p["frame"][0], "h": p["frame"][1],
                          "columns": p["grid"][0], "frames": p["frames"]}
    profile = dict(BLINK_PROFILES[blink])
    art = hero.get("art", {})
    phases = []
    from paint import has_phase_glow
    phase_glow = has_phase_glow(model)
    auto_hide = getattr(model, "phase_hide", {})
    for ph in art.get("phases", []) or []:
        p2 = bool(ph.get("palette")) or (phase_glow and ph["phase"] >= 2)
        hide = list(ph.get("hide", [])) + [b for b in auto_hide.get(ph["phase"], []) if b not in ph.get("hide", [])]
        phases.append({"phase": ph["phase"], "show": list(ph.get("show", [])), "hide": hide,
                       "texture": "p2" if p2 else "base", "eyes_glow": bool(ph.get("eyes_glow"))})
    for n in sorted(set(auto_hide) - {ph["phase"] for ph in art.get("phases", []) or []}):
        # a phase the weapon needs (an off-hand drawn from phase 3) but the hero's phases do not list
        phases.append({"phase": n, "show": [], "hide": list(auto_hide[n]), "texture": "base", "eyes_glow": False})
    phases.sort(key=lambda p: p["phase"])
    weapon = art.get("weapon", {}) or {}
    return {
        "format": VISUAL_FORMAT,
        "id": hero["id"],
        "generator": GENERATOR,
        "source_sha256": source_hash,
        "rig": rig["id"],
        "base": model.base_id,
        "form": model.form,
        "build": model.build,
        "height_px": model.env["head_top"],
        "head_y": model.env["head_y"],
        "render_scale": model.env["render_scale"],
        "scale": art.get("scale", 100) / 100.0,
        "style": anim.get("style", "fists"),
        "animation_chain": chain_of(hero),
        "secondary": [{"chain": list(sec["chain"]), "stiffness": sec.get("stiffness", 0.5),
                       "damping": sec.get("damping", 0.6), "part": sec["part"]} for sec in model.secondary],
        "texture": textures,
        "face": {
            "regions": regions,
            "eye_frames": EYE_FRAMES,
            "mouth_frames": MOUTH_FRAMES,
            "cheek_frames": CHEEK_FRAMES,
            "mood_order": MOODS,
            "moods": mood_table(spec),
            "overrides": spec.get("overrides", {}),
            "blink": {"profile": blink, "interval": profile["interval"], "ticks": profile["ticks"],
                      "double_chance": 0.15, "over_eyes": BLINK_OVER},
        },
        "moves": list(moves or []),
        "states": list(states or [s.get("name") for s in hero.get("states", []) or []]),
        "phases": phases,
        "bones": {
            "tint": model.tint_bones(),
            "fine": model.fine_bones(),
            "phase": model.phase_bones(),
            # a bone counts when a cube sits on it or below it (an off-hand drawn from phase 3 is on p3_weapon_l)
            "weapon_drawn": [b for b in ("weapon_r", "weapon_l") if b in model.bones and model.subtree_has_cubes(b)],
            "weapon_sheathed": [b for b in ("weapon_back", "weapon_back_2")
                                if b in model.bones and model.subtree_has_cubes(b)],
            "items": ["item_r", "item_l"],
            # G6c: the mounted copy of a carrier weapon, shown only while riding (G10; HeroModel must hide it otherwise)
            **({"mount": [model.mount["bone"]]} if model.mount else {}),
            "locators": sorted(n for n, b in model.bones.items() if b.kind == "locator"),
        },
        "weapon": _weapon_info(weapon, model) if weapon.get("prop") else None,
        "voice": hero.get("voice"),
    }


def _weapon_info(weapon, model):
    """The weapon block: hand and sheath as before; G6c adds the off-hand prop's sheath and phase, and the mounted
    copy (`mount`: the bone G10's riding animations and the renderer show only while riding)."""
    out = {"hand": weapon.get("hand", "right"), "sheathed": weapon.get("sheathed", "none")}
    off = weapon.get("offhand")
    if off:
        out["offhand"] = {"sheathed": off.get("sheathed", "none"), "phase": off.get("phase", 1)}
    if model.mount:
        out["mount"] = dict(model.mount)
    return out
