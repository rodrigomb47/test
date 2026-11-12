package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeroSizeTest {
    private static final double EPS = 1e-9;

    @Test
    void anAdultIsAboutPlayerHeight() {
        // rig v3 adult (the test rig): 52 px to the top of the head, chin at 42, drawn at 0.56
        HeroSize.Size s = HeroSize.of(52, 42, 0.56, 1.0);
        assertEquals(52 * 0.56 / 16, s.height(), EPS);
        assertEquals(1.82, s.height(), 1e-9);
        assertEquals(45 * 0.56 / 16, s.eyeHeight(), EPS, "eye line 3 px above the chin");
        assertEquals(0.6, s.width(), EPS);
        assertFalse(s.clamped());
        assertFalse(s.floored());
    }

    @Test
    void teenAndChibiAreSmallerButAChibiIsNeverBelowTheFloor() {
        assertEquals(1.58, HeroSize.of(45, 35, 0.56, 1.0).height(), 0.005, "teen");
        HeroSize.Size chibi = HeroSize.of(35, 25, 0.56, 1.0);
        assertEquals(1.225, chibi.height(), EPS, "chibi");
        assertFalse(chibi.floored());
        // an 85% chibi would be 1.04 blocks: raised to 1.2, its eyes stay where the model has them
        HeroSize.Size small = HeroSize.of(35, 25, 0.56, 0.85);
        assertTrue(small.floored());
        assertEquals(HeroSize.MIN_HEIGHT, small.height(), EPS);
        assertEquals(28 * 0.56 * 0.85 / 16, small.eyeHeight(), EPS);
        assertEquals(0.6 * 0.85, small.width(), EPS);
    }

    @Test
    void theHitboxIsCappedAt198WithTheEyesInProportion() {
        // a 110% adult: 2.002 blocks of model, so the box stops at 1.98 and the eye drops in proportion
        HeroSize.Size big = HeroSize.of(52, 42, 0.56, 1.1);
        assertTrue(big.clamped());
        assertEquals(HeroSize.MAX_HEIGHT, big.height(), EPS);
        double model = 52 * 0.56 * 1.1 / 16, eye = 45 * 0.56 * 1.1 / 16;
        assertEquals(eye * 1.98 / model, big.eyeHeight(), EPS);
        assertTrue(big.eyeHeight() < big.height());
        // a state's size on top of the art scale (Benika: 108% x 105%) stays capped
        assertEquals(HeroSize.MAX_HEIGHT, HeroSize.of(52, 42, 0.56, 1.08 * 1.05).height(), EPS);
        // just under the cap is left alone
        assertFalse(HeroSize.of(52, 42, 0.56, 1.08).clamped());
    }

    @Test
    void numbersThatMakeNoModelGiveThePlayerSizedRig() {
        HeroSize.Size adult = HeroSize.of(52, 42, 0.56, 1.0);
        assertEquals(adult, HeroSize.of(0, 42, 0.56, 1.0));
        assertEquals(adult, HeroSize.of(52, 60, 0.56, 1.0), "chin above the top");
        assertEquals(adult, HeroSize.of(52, 42, Double.NaN, 1.0));
        assertEquals(adult, HeroSize.of(52, 42, 0.56, 0.0), "a zero scale reads as 1");
        assertFalse(HeroSize.valid(52, -1, 0.56));
        assertTrue(HeroSize.valid(35, 25, 0.56));
    }

    @Test
    void modelHeightIsLinearInEveryFactor() {
        assertEquals(2 * HeroSize.modelHeight(52, 0.56, 1.0), HeroSize.modelHeight(52, 0.56, 2.0), EPS);
        assertEquals(HeroSize.modelHeight(26, 1.12, 1.0), HeroSize.modelHeight(52, 0.56, 1.0), EPS);
        assertEquals(1.0, HeroSize.modelEyeHeight(13, 1.0, 1.0), EPS, "16 px at render scale 1 is one block");
    }
}
