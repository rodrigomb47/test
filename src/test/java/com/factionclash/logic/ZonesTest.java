package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZonesTest {
    @Test
    void chebyshev() {
        assertEquals(0, Zones.chebyshev(5, 5, 5, 5));
        assertEquals(7, Zones.chebyshev(0, 0, 7, -3));
        assertEquals(7, Zones.chebyshev(0, 0, -3, 7));
        assertEquals(30, Zones.chebyshev(-10, 20, 20, 0), "the larger axis wins");
        assertEquals(Zones.chebyshev(1, 2, 9, -4), Zones.chebyshev(9, -4, 1, 2), "symmetric");
    }

    @Test
    void insideHalfMargins() {
        // the walls box of a settlement (half 24) plus 4 ends at 28, on both axes and both sides
        assertTrue(Zones.insideHalf(28, 0, 24, 4));
        assertTrue(Zones.insideHalf(-28, 28, 24, 4));
        assertFalse(Zones.insideHalf(29, 0, 24, 4));
        assertFalse(Zones.insideHalf(0, -29, 24, 4));
        assertTrue(Zones.insideHalf(24, 24, 24, 0), "the wall itself is inside");
        assertFalse(Zones.insideHalf(25, 0, 24, 0));
        assertEquals(2, Zones.half(2, 24), "a GameTest half-size overrides the layout");
        assertEquals(0, Zones.half(0, 24));
        assertEquals(24, Zones.half(-1, 24));
        assertEquals(11, Zones.workRadius(11, 64));
        assertEquals(64, Zones.workRadius(-1, 64));
        assertEquals(64, Zones.workRadius(0, 64));
    }

    @Test
    void mapInsideAndClamp() {
        // radius 0: no map edge, always inside and never moved
        assertTrue(Zones.mapInside(0, 0, 0, 1_000_000, -1_000_000, 16));
        assertArrayEquals(new int[]{5000, -7000}, Zones.mapClamp(0, 0, 0, 5000, -7000, 16));
        // a 500-block map around (100, -50) with a margin of 16
        assertTrue(Zones.mapInside(100, -50, 500, 100 + 484, -50 - 484, 16));
        assertFalse(Zones.mapInside(100, -50, 500, 100 + 485, -50, 16));
        assertFalse(Zones.mapInside(100, -50, 500, 100, -50 - 485, 16));
        assertArrayEquals(new int[]{100 + 484, -50 - 484}, Zones.mapClamp(100, -50, 500, 2000, -2000, 16));
        assertArrayEquals(new int[]{150, -60}, Zones.mapClamp(100, -50, 500, 150, -60, 16), "inside: unchanged");
        assertArrayEquals(new int[]{100 - 484, -50}, Zones.mapClamp(100, -50, 500, -9999, -50, 16), "each axis on its own");
        assertArrayEquals(new int[]{100, -50}, Zones.mapClamp(100, -50, 10, 300, 300, 16), "a margin wider than the map clamps to the centre");
        for (int x = -700; x <= 900; x += 37) {
            int[] c = Zones.mapClamp(100, -50, 500, x, x - 300, 16);
            assertTrue(Zones.mapInside(100, -50, 500, c[0], c[1], 16), "a clamped point is always inside");
        }
    }

    @Test
    void rallyStaysOutsideTheTargetsWallsOnEveryHeading() {
        // the live check's case: home 128 west and 128 north of the target (a diagonal)
        int[] r = Zones.rally(528, 144, 400, 16, 28);
        assertEquals(500, r[0]);
        assertEquals(116, r[1]);
        assertFalse(Zones.insideHalf(r[0] - 528, r[1] - 144, 24, 0), "inside the walls: " + r[0] + ", " + r[1]);
        // straight on an axis it is 28 out, as before
        assertArrayEquals(new int[]{128, 0}, Zones.rally(100, 0, 400, 0, 28));
        assertArrayEquals(new int[]{0, -28}, Zones.rally(0, 0, 3, -300, 28));
        for (int a = 0; a < 360; a += 5) {
            double t = Math.toRadians(a);
            int hx = (int) Math.round(Math.cos(t) * 200), hz = (int) Math.round(Math.sin(t) * 200);
            int[] p = Zones.rally(0, 0, hx, hz, 28);
            assertEquals(28, Zones.chebyshev(0, 0, p[0], p[1]), 1, "heading " + a);
            assertFalse(Zones.insideHalf(p[0], p[1], 24, 2), "heading " + a + ": " + p[0] + ", " + p[1]);
            assertTrue(p[0] * hx + p[1] * hz > 0, "on the home side at heading " + a);
        }
        assertArrayEquals(new int[]{28, 0}, Zones.rally(0, 0, 0, 0, 28), "home on the target: any side");
    }
}
