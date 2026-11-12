package com.factionclash.hero;

import com.factionclash.logic.hero.HeroSize;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * What the game reads from a hero's generated {@code assets/factionclash/heroes/<id>.visual.json} (HEROES_V2 12.1):
 * its body (form, build, base), the model numbers the hitbox comes from ({@code height_px}, {@code head_y},
 * {@code render_scale}, {@code scale}), its weapon style, its animation chain (own file, style library, core), its texture
 * facts and its {@link HeroLook} (bone groups, face, phases, moves, chains: what the client model reads). Missing or
 * broken fields fall back to the adult rig's values and are listed in {@link #problems()}.
 */
public record HeroVisual(String id, String base, String form, String build, double heightPx, double headY,
                         double renderScale, double scale, String style, List<String> animationChain,
                         boolean glowmask, boolean phaseTexture, int textureWidth, int textureHeight, HeroLook look,
                         List<String> problems) {

    /** The adult rig v3's numbers: what a hero without a readable file stands as. */
    public static final double DEFAULT_HEIGHT_PX = 52, DEFAULT_HEAD_Y = 42, DEFAULT_RENDER_SCALE = 0.56;

    /** Reads a visual file; {@code id} is the file's id (a different {@code id} inside is a problem, the file name wins). */
    public static HeroVisual parse(String id, JsonObject o) {
        List<String> problems = new ArrayList<>();
        String inner = string(o, "id", id);
        if (!inner.equals(id)) {
            problems.add("its id \"" + inner + "\" differs from its file name; \"" + id + "\" is used");
        }
        String style = string(o, "style", "fists");
        double heightPx = number(o, "height_px", DEFAULT_HEIGHT_PX, problems);
        double headY = number(o, "head_y", DEFAULT_HEAD_Y, problems);
        double renderScale = number(o, "render_scale", DEFAULT_RENDER_SCALE, problems);
        double scale = number(o, "scale", 1.0, problems);
        if (!HeroSize.valid(heightPx, headY, renderScale)) {
            problems.add("height_px " + heightPx + ", head_y " + headY + " and render_scale " + renderScale
                    + " make no model; the adult rig's are used");
            heightPx = DEFAULT_HEIGHT_PX;
            headY = DEFAULT_HEAD_Y;
            renderScale = DEFAULT_RENDER_SCALE;
        }
        if (!(scale >= 0.5 && scale <= 2.0)) {
            problems.add("scale " + scale + " is outside 0.5-2.0; 1 is used");
            scale = 1.0;
        }
        List<String> chain = new ArrayList<>();
        if (o.get("animation_chain") instanceof JsonArray a) {
            for (JsonElement e : a) {
                if (e.isJsonPrimitive()) {
                    chain.add(e.getAsString());
                }
            }
        }
        if (chain.isEmpty()) {
            chain.add(id);
            chain.add("_style_" + style);
            chain.add("_core");
            problems.add("no animation_chain; " + chain + " is used");
        }
        JsonObject tex = o.get("texture") instanceof JsonObject t ? t : new JsonObject();
        return new HeroVisual(id, string(o, "base", "adult_f"), string(o, "form", "adult"), string(o, "build", "standard"),
                heightPx, headY, renderScale, scale, style, List.copyOf(chain),
                bool(tex, "glowmask"), bool(tex, "p2"), (int) number(tex, "width", 256, problems), (int) number(tex, "height", 128, problems),
                HeroLook.parse(o, problems), List.copyOf(problems));
    }

    /** The hitbox at an entity scale of {@code entityScale} (its {@code generic.scale}, which starts at {@link #scale()}). */
    public HeroSize.Size size(double entityScale) {
        return HeroSize.of(heightPx, headY, renderScale, entityScale);
    }

    private static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    private static double number(JsonObject o, String key, double fallback, List<String> problems) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            problems.add("no " + key + "; " + fallback + " is used");
            return fallback;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            problems.add(key + " is not a number; " + fallback + " is used");
            return fallback;
        }
        return e.getAsDouble();
    }
}
