"""`<id>.visual.json`: the client facts `HeroModel` and `HeroFace` read (design 12.1).

Fields: style library, blink profile, face frame layout and the resolved mood table (defaults with the hero's
overrides applied, so the Java side needs no copy of the defaults), move list (index -> animation name; filled by the
animation generator), states, phase looks, bone groups the renderer treats specially (tint, fine, phase, weapon,
items), texture facts and voice.
"""
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
    for ph in art.get("phases", []) or []:
        phases.append({"phase": ph["phase"], "show": list(ph.get("show", [])), "hide": list(ph.get("hide", [])),
                       "texture": "p2" if ph.get("palette") else "base", "eyes_glow": bool(ph.get("eyes_glow"))})
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
        "scale": art.get("scale", 100) / 100.0,
        "style": anim.get("style", "fists"),
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
            "weapon_drawn": [b for b in ("weapon_r", "weapon_l") if any(c.bone == b for c in model.cubes)],
            "weapon_sheathed": ["weapon_back"] if any(c.bone == "weapon_back" for c in model.cubes) else [],
            "items": ["item_r", "item_l"],
            "locators": sorted(n for n, b in model.bones.items() if b.kind == "locator"),
        },
        "weapon": {"hand": weapon.get("hand", "right"), "sheathed": weapon.get("sheathed", "none")}
        if weapon.get("prop") else None,
        "voice": hero.get("voice"),
    }
