package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PadsTest {
    @Test
    void distanceIsChebyshevToTheNearestRectangle() {
        List<int[]> pads = List.of(new int[]{0, 0, 10, 10}, new int[]{20, 2, 14, 8}); // corners in any order
        assertEquals(0, Pads.distance(pads, 5, 5), "inside the first pad");
        assertEquals(0, Pads.distance(pads, 17, 5), "inside the second pad");
        assertEquals(1, Pads.distance(pads, 11, 5));
        assertEquals(3, Pads.distance(pads, -3, -2), "diagonal: the larger axis counts");
        assertEquals(2, Pads.distance(pads, 12, 5), "between the pads: the nearer one counts");
        assertEquals(4, Pads.distance(pads, 17, 12));
    }
}
