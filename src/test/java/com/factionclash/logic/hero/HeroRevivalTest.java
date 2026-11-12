package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeroRevivalTest {
    @Test
    void aFallenHeroReturnsAfterTheConfiguredSeconds() {
        assertEquals(1000 + 12000, HeroRevival.reviveAt(1000, 600), "ten minutes");
        assertEquals(1000 + 20, HeroRevival.reviveAt(1000, 0), "never at once");
        assertFalse(HeroRevival.due(12999, 13000));
        assertTrue(HeroRevival.due(13000, 13000));
        assertFalse(HeroRevival.due(50000, 0), "0: nothing is due");
    }

    @Test
    void theCountdownReadsAsMinutesAndSeconds() {
        assertEquals("10:00", HeroRevival.countdown(12000));
        assertEquals("6:12", HeroRevival.countdown(372 * 20));
        assertEquals("0:01", HeroRevival.countdown(1), "rounded up");
        assertEquals("0:00", HeroRevival.countdown(-5));
        assertEquals("1:05", HeroRevival.countdown(65 * 20));
    }
}
