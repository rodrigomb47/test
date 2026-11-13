package com.factionclash.logic.leader;

import com.factionclash.logic.hero.HeroDef.Dye;
import com.factionclash.logic.leader.Palette.BarColor;
import com.factionclash.logic.leader.Palette.ChatColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link Palette}: Lab and delta-E, the nearest dye, chat and bar colours, the identity dyes, hex text. */
class PaletteTest {
    @Test
    void labAndDeltaE() {
        double[] white = Palette.lab(0xFFFFFF), black = Palette.lab(0x000000);
        assertEquals(100, white[0], 0.01);
        assertEquals(0, white[1], 0.01);
        assertEquals(0, white[2], 0.01);
        assertEquals(0, black[0], 0.01);
        double[] red = Palette.lab(0xFF0000);
        assertEquals(53.24, red[0], 0.05, "sRGB red, D65");
        assertEquals(80.09, red[1], 0.05);
        assertEquals(67.20, red[2], 0.05);
        assertEquals(100, Palette.deltaE(0x000000, 0xFFFFFF), 0.01);
        assertEquals(Palette.deltaE(0x123456, 0xABCDEF), Palette.deltaE(0xABCDEF, 0x123456), 1e-12, "symmetric");
        assertEquals(0, Palette.deltaE(0x2E3A6B, 0x2E3A6B), 1e-12);
    }

    @Test
    void theNearestDyeAndWhyBannersAreAuthored() {
        for (Dye d : Dye.values()) {
            assertEquals(d, Palette.nearestDye(Palette.DYE_RGB.get(d)), "a dye is its own nearest");
        }
        // HEROES_V2 5.1: the nearest dye is often wrong for dark colours, so dye_primary comes from the banner
        assertEquals(Dye.GRAY, Palette.nearestDye(0x2E3A6B), "indigo lands on gray");
        assertEquals(Dye.GRAY, Palette.nearestDye(0x4A4E57), "charcoal lands on gray");
        assertEquals(Dye.BLACK, Palette.nearestDye(0x6B2148), "plum lands on black");
        assertEquals(Dye.RED, Palette.nearestDye(0xA3202A), "Benika's crimson");
        assertEquals(16, Palette.dyes().size());
    }

    @Test
    void chatAndBarColours() {
        for (ChatColor c : ChatColor.values()) {
            assertEquals(c, Palette.nearestChat(c.rgb));
        }
        assertEquals(ChatColor.DARK_RED, Palette.nearestChat(0xA3202A));
        assertEquals(ChatColor.DARK_GRAY, Palette.nearestChat(0x2E3A6B), "indigo is nearer dark gray than dark blue"
                + " (the scoreboard glow; chat itself can show the exact colour)");
        for (BarColor b : BarColor.values()) {
            assertEquals(b, Palette.nearestBar(b.rgb));
        }
        assertEquals(BarColor.RED, Palette.nearestBar(0xA3202A));
        assertEquals(BarColor.BLUE, Palette.nearestBar(0x3AB3DA));
    }

    @Test
    void identityDyes() {
        // a banner decides: base and first pattern dye
        assertArrayEquals(new Dye[]{Dye.BLUE, Dye.WHITE}, Palette.identityDyes(0x2E3A6B, 0xE8E4D8, Dye.BLUE,
                Dye.WHITE));
        // a banner without a pattern dye: the secondary colour's nearest dye other than the base
        Dye[] d = Palette.identityDyes(0x2E3A6B, 0x474F52, Dye.GRAY, null);
        assertEquals(Dye.GRAY, d[0]);
        assertNotEquals(Dye.GRAY, d[1]);
        // no banner: the nearest dyes, never the same twice
        Dye[] n = Palette.identityDyes(0xB02E26, 0xB02E26, null, null);
        assertEquals(Dye.RED, n[0]);
        assertNotEquals(Dye.RED, n[1]);
    }

    @Test
    void hexText() {
        assertEquals("#A3202A", Palette.hex(0xA3202A));
        assertEquals("#000000", Palette.hex(0));
        assertEquals(0xA3202A, Palette.parse("#a3202a"));
        assertEquals(0xA3202A, Palette.parse(" A3202A "));
        assertEquals(-1, Palette.parse("#A3202"));
        assertEquals(-1, Palette.parse("red"));
        assertEquals(-1, Palette.parse(null));
        assertTrue(Palette.parse("#FFFFFF") == 0xFFFFFF);
    }
}
