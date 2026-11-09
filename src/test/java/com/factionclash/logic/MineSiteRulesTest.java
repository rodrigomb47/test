package com.factionclash.logic;

import com.factionclash.logic.MineSiteRules.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MineSiteRulesTest {
    @Test
    void corridorRectForEachFacing() {
        assertEquals(42, MineSiteRules.corridorEnd(0));
        assertEquals(202, MineSiteRules.corridorEnd(4), "the last tunnel cell after four rounds");
        int maxF = SpiralMine.tunnelPlan(0, 4).stream().mapToInt(SpiralMine.Cell::f).max().orElseThrow();
        assertEquals(MineSiteRules.corridorEnd(4), maxF);
        // side = clockwise: east -> south, south -> west, west -> north, north -> east
        assertEquals(new Rect(-2, -11, 42, 12), MineSiteRules.corridor(0, 0, 1, 0, 0));
        assertEquals(new Rect(-12, -2, 11, 42), MineSiteRules.corridor(0, 0, 0, 1, 0));
        assertEquals(new Rect(-42, -12, 2, 11), MineSiteRules.corridor(0, 0, -1, 0, 0));
        assertEquals(new Rect(-11, -42, 12, 2), MineSiteRules.corridor(0, 0, 0, -1, 0));
        assertEquals(new Rect(98, 489, 302, 512), MineSiteRules.corridor(100, 500, 1, 0, 4));
        // every planned tunnel cell of a level lies in the corridor, on either landing side
        Rect c = MineSiteRules.corridor(0, 0, 1, 0, 4);
        for (int side = 0; side <= 1; side++) {
            for (SpiralMine.Cell cell : SpiralMine.tunnelPlan(side, 4)) {
                assertTrue(c.contains(cell.f(), cell.s()), "cell " + cell + " outside the corridor");
            }
        }
        // a lengthening round adds the stretch right after the previous one
        assertEquals(new Rect(43, -11, 82, 12), MineSiteRules.segment(0, 0, 1, 0, 1));
    }

    @Test
    void overlapsHonourTheMargin() {
        Rect a = new Rect(0, 0, 10, 10), b = new Rect(13, 0, 20, 10);
        assertFalse(MineSiteRules.overlaps(a, b, 2));
        assertTrue(MineSiteRules.overlaps(a, b, 3));
        assertTrue(a.overlaps(new Rect(10, 10, 11, 11)), "touching corners overlap");
    }
}
