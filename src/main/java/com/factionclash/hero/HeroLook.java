package com.factionclash.hero;

import com.factionclash.logic.hero.BlinkSchedule;
import com.factionclash.logic.hero.FaceRules;
import com.factionclash.logic.hero.HeroActions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The part of a hero's {@code visual.json} the client model reads (HEROES_V2 4.3-4.9, generator decision 10 of
 * {@code notes/artgen.md}): the bone groups the renderer treats specially (tint, {@code fine_}, phase, weapon drawn and
 * sheathed, item locators, effect locators), the face (frame regions, the resolved mood table and its overrides, the
 * blink profile and the eyes a blink may close), the phase looks, the move and state names behind the {@code move.N}
 * and {@code state.N} action codes, the chains the parts declare for the procedural sway, whether the glow mask
 * glows anywhere but the glowing-eye frame, and the carrier's {@code mount} bones (G10, MOBILITY_AND_WORK 4.3: shown only
 * while the hero flies, glides or rides; {@code carrierWeapon} when its weapon is the mount, so the weapon's hand and
 * back copies hide meanwhile). Missing parts fall back to the rig's defaults.
 */
public record HeroLook(List<String> fine, List<String> tint, List<String> weaponDrawn, List<String> weaponSheathed,
                       List<String> items, List<String> locators, Map<String, Integer> phaseBones, List<Phase> phases,
                       Face face, List<String> moves, List<String> states, List<Chain> chains, boolean glowOutsideFace,
                       List<String> mount, boolean carrierWeapon) {

    /** A phase look: from {@code phase} on, these bones show or hide, the texture may switch and the eyes may glow. */
    public record Phase(int phase, List<String> show, List<String> hide, String texture, boolean eyesGlow) {
    }

    /** The face: frame regions, the 12 moods' frames (in {@link HeroActions#MOODS} order), the blink. */
    public record Face(FaceRules.Region eyes, FaceRules.Region mouth, FaceRules.Region cheeks, List<FaceRules.Frames> moods,
                       BlinkSchedule.Profile blink, List<Integer> blinkOver) {
        public static final Face DEFAULT = new Face(FaceRules.Region.EYES, FaceRules.Region.MOUTH, FaceRules.Region.CHEEKS,
                FaceRules.DEFAULT_MOODS, BlinkSchedule.Profile.NORMAL, FaceRules.DEFAULT_BLINK_OVER);
    }

    /** A chain (or group) of bones a part declares, with its spring numbers. */
    public record Chain(List<String> bones, double stiffness, double damping) {
    }

    /** What a hero without a readable look has: the rig's sash bones, the default face. */
    public static final HeroLook DEFAULT = new HeroLook(List.of(), List.of("sash", "fine_sash"), List.of(), List.of(),
            List.of("item_r", "item_l"), List.of("vfx_chest", "vfx_feet", "vfx_head", "vfx_tip_l", "vfx_tip_r"), Map.of(),
            List.of(), Face.DEFAULT, List.of(), List.of(), List.of(), true, List.of(), false);

    /** Reads the look from a visual file; what is missing or broken comes from {@link #DEFAULT} and is noted. */
    public static HeroLook parse(JsonObject o, List<String> problems) {
        JsonObject bones = obj(o, "bones");
        Map<String, Integer> phaseBones = new LinkedHashMap<>();
        if (bones.get("phase") instanceof JsonObject p) {
            for (Map.Entry<String, JsonElement> e : p.entrySet()) {
                if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) {
                    phaseBones.put(e.getKey(), e.getValue().getAsInt());
                }
            }
        }
        List<Phase> phases = new ArrayList<>();
        if (o.get("phases") instanceof JsonArray arr) {
            for (JsonElement e : arr) {
                if (e instanceof JsonObject p && p.get("phase") != null && p.get("phase").isJsonPrimitive()) {
                    phases.add(new Phase(p.get("phase").getAsInt(), strings(p, "show"), strings(p, "hide"),
                            p.get("texture") != null && p.get("texture").isJsonPrimitive() ? p.get("texture").getAsString() : "",
                            bool(p, "eyes_glow")));
                }
            }
        }
        List<Chain> chains = new ArrayList<>();
        if (o.get("secondary") instanceof JsonArray arr) {
            for (JsonElement e : arr) {
                if (e instanceof JsonObject c) {
                    List<String> b = strings(c, "chain");
                    if (!b.isEmpty()) {
                        chains.add(new Chain(b, num(c, "stiffness", 0.5), num(c, "damping", 0.7)));
                    }
                }
            }
        }
        JsonObject tex = obj(o, "texture");
        return new HeroLook(strings(bones, "fine"), bones.has("tint") ? strings(bones, "tint") : DEFAULT.tint,
                strings(bones, "weapon_drawn"), strings(bones, "weapon_sheathed"),
                bones.has("items") ? strings(bones, "items") : DEFAULT.items, strings(bones, "locators"), Map.copyOf(phaseBones),
                List.copyOf(phases), face(obj(o, "face"), problems), names(o, "moves"), names(o, "states"), List.copyOf(chains),
                !tex.has("glow_outside_face") || bool(tex, "glow_outside_face"), strings(bones, "mount"),
                "weapon".equals(obj(o, "carrier").get("part") instanceof JsonElement e && e.isJsonPrimitive() ? e.getAsString() : ""));
    }

    private static Face face(JsonObject f, List<String> problems) {
        if (f.size() == 0) {
            return Face.DEFAULT;
        }
        JsonObject regions = obj(f, "regions");
        FaceRules.Region eyes = region(regions, "eyes", FaceRules.Region.EYES, FaceRules.EYE_FRAMES, problems);
        FaceRules.Region mouth = region(regions, "mouth", FaceRules.Region.MOUTH, FaceRules.MOUTH_FRAMES, problems);
        FaceRules.Region cheeks = region(regions, "cheeks", FaceRules.Region.CHEEKS, FaceRules.CHEEK_FRAMES, problems);
        List<FaceRules.Frames> own = new ArrayList<>();
        JsonObject moods = obj(f, "moods");
        for (String m : HeroActions.MOODS) {
            own.add(moods.get(m) instanceof JsonObject mo ? frames(mo) : null);
        }
        Map<Integer, FaceRules.Frames> overrides = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : obj(f, "overrides").entrySet()) {
            int code = HeroActions.moodCode(e.getKey());
            if (code >= 0 && e.getValue() instanceof JsonObject mo) {
                overrides.put(code, frames(mo));
            } else {
                problems.add("face.overrides: \"" + e.getKey() + "\" is not a mood; ignored");
            }
        }
        JsonObject b = obj(f, "blink");
        BlinkSchedule.Profile base = BlinkSchedule.Profile.named(b.get("profile") != null && b.get("profile").isJsonPrimitive()
                ? b.get("profile").getAsString() : "normal");
        BlinkSchedule.Profile blink = base;
        if (b.get("interval") instanceof JsonArray iv && iv.size() == 2 && b.get("ticks") instanceof JsonArray tk && tk.size() == 3) {
            blink = BlinkSchedule.Profile.of(base, iv.get(0).getAsDouble(), iv.get(1).getAsDouble(), tk.get(0).getAsInt(),
                    tk.get(1).getAsInt(), tk.get(2).getAsInt(), num(b, "double_chance", base.doubleChance()));
        }
        List<Integer> over = new ArrayList<>();
        if (b.get("over_eyes") instanceof JsonArray oe) {
            for (JsonElement e : oe) {
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                    over.add(e.getAsInt());
                }
            }
        }
        return new Face(eyes, mouth, cheeks, FaceRules.moodTable(own, overrides), blink,
                over.isEmpty() ? FaceRules.DEFAULT_BLINK_OVER : List.copyOf(over));
    }

    private static FaceRules.Frames frames(JsonObject m) {
        return new FaceRules.Frames((int) num(m, "eyes", 0), (int) num(m, "mouth", 0), (int) num(m, "cheeks", 0));
    }

    private static FaceRules.Region region(JsonObject regions, String key, FaceRules.Region fallback, int frames, List<String> problems) {
        if (!(regions.get(key) instanceof JsonObject r)) {
            return fallback;
        }
        FaceRules.Region out = new FaceRules.Region((int) num(r, "u", fallback.u()), (int) num(r, "v", fallback.v()),
                (int) num(r, "w", fallback.w()), (int) num(r, "h", fallback.h()), (int) num(r, "columns", fallback.columns()),
                (int) num(r, "frames", fallback.frames()));
        if (out.w() <= 0 || out.h() <= 0 || out.columns() <= 0 || out.frames() < frames) {
            problems.add("face.regions." + key + " is not a strip of " + frames + " frames; the rig's is used");
            return fallback;
        }
        return out;
    }

    /** Names from a list of strings or of objects with an {@code id} (or {@code name}). */
    private static List<String> names(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o.get(key) instanceof JsonArray arr) {
            for (JsonElement e : arr) {
                if (e.isJsonPrimitive()) {
                    out.add(e.getAsString());
                } else if (e instanceof JsonObject m) {
                    JsonElement id = m.has("id") ? m.get("id") : m.get("name");
                    out.add(id != null && id.isJsonPrimitive() ? id.getAsString() : null);
                }
            }
        }
        return java.util.Collections.unmodifiableList(out);
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o.get(key) instanceof JsonArray arr) {
            for (JsonElement e : arr) {
                if (e.isJsonPrimitive()) {
                    out.add(e.getAsString());
                }
            }
        }
        return List.copyOf(out);
    }

    private static JsonObject obj(JsonObject o, String key) {
        return o.get(key) instanceof JsonObject j ? j : new JsonObject();
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    private static double num(JsonObject o, String key, double fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : fallback;
    }

    /** The phase a {@code pN_} bone shows from (a bone the file lists wins), or 0 for any other bone. */
    public int phaseOf(String bone) {
        Integer p = phaseBones.get(bone);
        if (p != null) {
            return p;
        }
        if (bone.length() > 3 && bone.charAt(0) == 'p' && bone.charAt(2) == '_' && Character.isDigit(bone.charAt(1))) {
            return bone.charAt(1) - '0';
        }
        return 0;
    }
}
