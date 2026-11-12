package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeroLodTest {
    @Test
    void eachPartHasItsDistance() {
        assertEquals(HeroLod.Level.FULL, HeroLod.of(5));
        HeroLod.Level at18 = HeroLod.of(18);
        assertTrue(at18.fine());
        assertFalse(at18.face(), "the face freezes beyond 16");
        HeroLod.Level at22 = HeroLod.of(22);
        assertFalse(at22.fine(), "fine bones beyond 20");
        assertTrue(at22.locators());
        assertTrue(at22.secondary());
        HeroLod.Level at30 = HeroLod.of(30);
        assertFalse(at30.locators(), "locators beyond 24");
        assertTrue(at30.secondary());
        assertFalse(HeroLod.of(33).secondary(), "secondary beyond 32");
    }

    @Test
    void aHeroAtABoundaryDoesNotFlicker() {
        HeroLod.Level l = HeroLod.of(19.5);
        assertTrue(l.fine());
        l = HeroLod.of(20.8, l);
        assertTrue(l.fine(), "inside the band: stays on");
        l = HeroLod.of(21.2, l);
        assertFalse(l.fine(), "beyond the band: off");
        l = HeroLod.of(19.5, l);
        assertFalse(l.fine(), "inside the band: stays off");
        l = HeroLod.of(18.9, l);
        assertTrue(l.fine(), "within the band's near edge: on again");
        assertEquals(HeroLod.of(25), HeroLod.of(25, null));
        assertEquals(HeroLod.of(Double.NaN), HeroLod.of(Double.NaN, HeroLod.Level.FULL));
    }
}
