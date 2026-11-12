package com.factionclash.logic.hero;

/**
 * A hero's hitbox from its model (HEROES_V2 3.6). The model stands {@code height_px x render_scale x scale / 16}
 * blocks tall and its eyes sit at {@code (head_y + 3) x render_scale x scale / 16}, both read from the hero's
 * {@code visual.json} ({@code scale} being the entity's {@code generic.scale}, which carries the art scale and any later
 * size state). The hitbox is that height, but:
 * <ul>
 *   <li>at least {@link #MIN_HEIGHT}, so a small chibi hero is not much harder to hit than a crouching player
 *   (the eyes stay where the model has them);</li>
 *   <li>at most {@link #MAX_HEIGHT}, so every hero still walks through 2-high tunnels and doors (the eyes drop in
 *   proportion). The model keeps drawing at its full scale; horns and ears may stick out of the box.</li>
 * </ul>
 * The width is a player's 0.6 times the scale. Pure, unit-tested ({@code HeroSizeTest}).
 */
public final class HeroSize {
    /** A player's width, at scale 1. */
    public static final double WIDTH = 0.6;
    /** PROPOSED: the smallest hitbox height, in blocks. */
    public static final double MIN_HEIGHT = 1.2;
    /** The largest hitbox height, in blocks: just under two blocks, so a hero fits a 2-high doorway. */
    public static final double MAX_HEIGHT = 1.98;
    /** Model pixels from {@code head_y} (the chin) up to the eye line. */
    public static final double EYE_ABOVE_HEAD_Y = 3.0;
    /** Model pixels per block before the render scale. */
    public static final double PIXELS_PER_BLOCK = 16.0;

    private HeroSize() {
    }

    /**
     * A hitbox in blocks.
     *
     * @param clamped the model was taller than {@link #MAX_HEIGHT}
     * @param floored the model was shorter than {@link #MIN_HEIGHT}
     */
    public record Size(double width, double height, double eyeHeight, boolean clamped, boolean floored) {
    }

    /** The model's height in blocks. */
    public static double modelHeight(double heightPx, double renderScale, double scale) {
        return heightPx * renderScale * scale / PIXELS_PER_BLOCK;
    }

    /** The model's eye height in blocks. */
    public static double modelEyeHeight(double headY, double renderScale, double scale) {
        return (headY + EYE_ABOVE_HEAD_Y) * renderScale * scale / PIXELS_PER_BLOCK;
    }

    /**
     * The hitbox of a model {@code heightPx} model pixels tall with its chin at {@code headY}, drawn at
     * {@code renderScale} and sized by {@code scale}. Numbers that make no model (not positive, not finite, or a chin
     * above the top) give the size of a player-sized model instead, so a broken file never makes a broken hitbox.
     */
    public static Size of(double heightPx, double headY, double renderScale, double scale) {
        if (!(scale > 0) || !Double.isFinite(scale)) {
            scale = 1.0;
        }
        if (!valid(heightPx, headY, renderScale)) {
            heightPx = 52;
            headY = 42;
            renderScale = 0.56;
        }
        double h = modelHeight(heightPx, renderScale, scale);
        double eye = modelEyeHeight(headY, renderScale, scale);
        boolean clamped = false, floored = false;
        if (h > MAX_HEIGHT) {
            eye = eye * MAX_HEIGHT / h;
            h = MAX_HEIGHT;
            clamped = true;
        } else if (h < MIN_HEIGHT) {
            h = MIN_HEIGHT;
            floored = true;
        }
        return new Size(WIDTH * scale, h, Math.min(eye, h), clamped, floored);
    }

    /** Whether these numbers describe a model: positive and finite, the chin below the top. */
    public static boolean valid(double heightPx, double headY, double renderScale) {
        return heightPx > 0 && Double.isFinite(heightPx) && renderScale > 0 && Double.isFinite(renderScale)
                && headY >= 0 && headY < heightPx;
    }
}
