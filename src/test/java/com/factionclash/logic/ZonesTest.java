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
}
