package com.factionclash.logic.hero;

/**
 * Form-scaled animation keys (HEROES_V2 4.4, G4): the core library writes the root heights of kneels, sits, sleep and
 * death as the Molang expression {@code "v + k * v.fc_dhip"}, exact on every form because the forms' leg segments
 * scale together. The variable is the model's hip height (its {@code leg_r} pivot, in model pixels) minus the middle of
 * the forms' hip heights (chibi 12, adult 24.5): about -6.25 on a chibi, 0 on a teen, +6.25 on an adult. A model that
 * does not set it reads 0, the middle-form compromise. Pure.
 */
public final class FormScale {
    /** The Molang variable the generator writes (tools/herogen anim/clip.py FORM_VAR). */
    public static final String VARIABLE = "v.fc_dhip";
    /** The middle of the three forms' hip heights, model pixels (anim/clip.py HIP_MID). */
    public static final double HIP_MID = 18.25;

    private FormScale() {
    }

    /** The variable's value for a model whose {@code leg_r} pivot is at {@code hipY} model pixels. */
    public static double of(double hipY) {
        return Double.isFinite(hipY) ? hipY - HIP_MID : 0.0;
    }
}
