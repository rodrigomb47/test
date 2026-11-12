package com.factionclash.logic.hero;

/**
 * Level of detail by distance from the camera (HEROES_V2 4.9): {@code fine_} bones are drawn within 20 blocks, the
 * face lives (blinks, talks, authored frames) within 16, the {@code secondary} controller and the procedural sway run
 * within 32, locator bones track their world position within 24. Each switch has a one-block band, so a hero standing
 * at a boundary does not flicker: a part switches off beyond its distance plus the band and on again within it. Pure,
 * unit-tested ({@code HeroLodTest}).
 */
public final class HeroLod {
    public static final double FINE_DISTANCE = 20, FACE_DISTANCE = FaceRules.FACE_DISTANCE, SECONDARY_DISTANCE = 32,
            LOCATOR_DISTANCE = 24;
    /** The hysteresis band (blocks). */
    public static final double BAND = 1;

    private HeroLod() {
    }

    /** What is drawn and animated at one distance. */
    public record Level(boolean fine, boolean face, boolean secondary, boolean locators) {
        public static final Level FULL = new Level(true, true, true, true);
    }

    /** The level at {@code distance} with no history (a hero just seen). */
    public static Level of(double distance) {
        return new Level(distance <= FINE_DISTANCE, distance <= FACE_DISTANCE, distance <= SECONDARY_DISTANCE,
                distance <= LOCATOR_DISTANCE);
    }

    /** The level at {@code distance} after {@code previous}: each part keeps its state inside its band. */
    public static Level of(double distance, Level previous) {
        if (previous == null || !Double.isFinite(distance)) {
            return of(distance);
        }
        return new Level(keep(distance, FINE_DISTANCE, previous.fine()), keep(distance, FACE_DISTANCE, previous.face()),
                keep(distance, SECONDARY_DISTANCE, previous.secondary()), keep(distance, LOCATOR_DISTANCE, previous.locators()));
    }

    private static boolean keep(double d, double limit, boolean wasOn) {
        if (d <= limit - BAND) {
            return true;
        }
        if (d > limit + BAND) {
            return false;
        }
        return wasOn;
    }
}
