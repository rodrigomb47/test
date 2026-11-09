package com.factionclash.logic;

import com.factionclash.logic.MineSiteRules.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

    private static String names(List<int[]> facings) {
        StringBuilder sb = new StringBuilder();
        for (int[] f : facings) {
            sb.append(f[0] == 1 ? "E" : f[0] == -1 ? "W" : f[1] == 1 ? "S" : "N");
        }
        return sb.toString();
    }

    @Test
    void facingsNeverPointAtTheBase() {
        assertEquals("ESN", names(MineSiteRules.facingsAwayFrom(40, 0)), "due east: east first, then along the ring");
        assertEquals("EN", names(MineSiteRules.facingsAwayFrom(40, -20)), "north-east: east and north only");
        assertEquals("NE", names(MineSiteRules.facingsAwayFrom(20, -40)), "more north than east: north first");
        assertEquals("WS", names(MineSiteRules.facingsAwayFrom(-40, 33)));
        assertTrue(MineSiteRules.facingsAwayFrom(0, 0).isEmpty());
        Random rnd = new Random(7);
        for (int i = 0; i < 2000; i++) {
            int dx = rnd.nextInt(129) - 64, dz = rnd.nextInt(129) - 64;
            List<int[]> fs = MineSiteRules.facingsAwayFrom(dx, dz);
            if (dx == 0 && dz == 0) {
                continue;
            }
            assertFalse(fs.isEmpty());
            int[] first = fs.get(0);
            int best = Integer.MIN_VALUE;
            for (int[] f : fs) {
                int dot = f[0] * dx + f[1] * dz;
                assertTrue(dot >= 0, "a facing points back at the base from " + dx + "," + dz);
                best = Math.max(best, dot);
            }
            assertEquals(best, first[0] * dx + first[1] * dz, "the most outward facing comes first");
        }
    }

    @Test
    void overlappingCorridorsRejected() {
        // another mine 30 blocks north, also running east: its corridor (+2) covers this one's
        List<Rect> obstacles = List.of(MineSiteRules.corridor(100, -10, 1, 0, 4).inflate(2));
        assertEquals(-1, MineSiteRules.maxRoundsThatFit(100, 0, 1, 0, 4, obstacles, null));
        // a mine facing south from 30 blocks further east crosses this one's tunnels in the second round
        List<Rect> crossing = List.of(MineSiteRules.corridor(190, -30, 0, 1, 4).inflate(2));
        int r = MineSiteRules.maxRoundsThatFit(100, 0, 1, 0, 4, crossing, null);
        assertTrue(r >= 0 && r < 4, "the corridor stops short of the other one: " + r);
        assertFalse(MineSiteRules.corridor(100, 0, 1, 0, r).overlaps(crossing.get(0)));
        assertTrue(MineSiteRules.corridor(100, 0, 1, 0, r + 1).overlaps(crossing.get(0)));
    }

    @Test
    void parallelCorridorsFarApartAccepted() {
        // the corridors are 24 wide; 26 apart leaves the 2-block gap, 25 apart does not
        List<Rect> obstacles = List.of(MineSiteRules.corridor(100, 0, 1, 0, 4).inflate(2));
        assertEquals(4, MineSiteRules.maxRoundsThatFit(100, 26, 1, 0, 4, obstacles, null));
        assertEquals(-1, MineSiteRules.maxRoundsThatFit(100, 25, 1, 0, 4, obstacles, null));
        // opposite directions from origins 30 apart
        assertEquals(4, MineSiteRules.maxRoundsThatFit(70, 0, -1, 0, 4, obstacles, null));
    }

    @Test
    void ownWallsBlockTunnelsUnderTheBase() {
        Rect walls = new Rect(-24, -24, 24, 24).inflate(4);
        List<Rect> obstacles = List.of(walls);
        assertEquals(4, MineSiteRules.maxRoundsThatFit(32, 0, 1, 0, 4, obstacles, null), "outward from 32 fits");
        assertEquals(-1, MineSiteRules.maxRoundsThatFit(32, 0, 0, -1, 4, obstacles, null), "along the wall at 32 does not");
        assertEquals(-1, MineSiteRules.maxRoundsThatFit(32, 0, 0, 1, 4, obstacles, null));
        assertEquals(4, MineSiteRules.maxRoundsThatFit(32, -40, 0, -1, 4, obstacles, null), "north from the corner fits");
        assertEquals(-1, MineSiteRules.maxRoundsThatFit(32, 0, -1, 0, 4, obstacles, null), "toward the base never fits");
    }

    @Test
    void maxRoundsShrinksNearTheMapEdge() {
        Rect map = new Rect(-150, -150, 150, 150);
        assertEquals(4, MineSiteRules.maxRoundsThatFit(-60, 0, -1, 0, 4, List.of(), new Rect(-300, -300, 300, 300)));
        int r = MineSiteRules.maxRoundsThatFit(60, 0, 1, 0, 4, List.of(), map);
        assertEquals(1, r, "the corridor ends 142 blocks east after one round, 182 after two");
        assertTrue(r < 4);
        assertEquals(-1, MineSiteRules.maxRoundsThatFit(140, 0, 1, 0, 4, List.of(), map), "not even the first branch mine fits");
        assertEquals(-1, MineSiteRules.maxRoundsThatFit(0, 145, 1, 0, 4, List.of(), map), "too close to the edge sideways");
    }

    @Test
    void candidatesStayInTheRing() {
        List<int[]> cs = MineSiteRules.candidates(1000, -500, 32, 64, 4);
        assertTrue(cs.size() > 700 && cs.size() < 900, "about 800 points, got " + cs.size());
        List<Long> seen = new ArrayList<>();
        for (int[] c : cs) {
            int dx = c[0] - 1000, dz = c[1] + 500;
            int cheb = Math.max(Math.abs(dx), Math.abs(dz));
            assertTrue(cheb >= 32 && cheb <= 64, "Chebyshev " + cheb);
            assertEquals(0, Math.floorMod(dx, 4), "on the 4-grid");
            assertEquals(0, Math.floorMod(dz, 4), "on the 4-grid");
            long key = ((long) c[0] << 32) ^ (c[1] & 0xffffffffL);
            assertFalse(seen.contains(key), "no point twice");
            seen.add(key);
        }
        int[] first = cs.get(0), last = cs.get(cs.size() - 1);
        assertEquals(32, Math.max(Math.abs(first[0] - 1000), Math.abs(first[1] + 500)), "inner ring first");
        assertEquals(64, Math.max(Math.abs(last[0] - 1000), Math.abs(last[1] + 500)));
        // a ring narrower than the grid still gets its points: a test base's ring 10..11
        List<int[]> small = MineSiteRules.candidates(0, 0, 10, 11, 4);
        assertEquals(20, small.size());
        for (int[] c : small) {
            assertEquals(10, Math.max(Math.abs(c[0]), Math.abs(c[1])));
        }
    }

    @Test
    void scoreWeighsTheWalkWaterAndGround() {
        assertEquals(40.0, MineSiteRules.score(40, 0, 0, false, 4));
        assertEquals(40 + 2 * 9 + 4 * 1 + 10, MineSiteRules.score(40, 9, 1, true, 4));
        assertTrue(MineSiteRules.score(50, 0, 0, false, 4) < MineSiteRules.score(40, 6, 0, false, 4), "water over the tunnels costs more than a short walk");
    }

    @Test
    void scorePrefersRoomToLengthen() {
        assertEquals(40 + 4 * MineSiteRules.ROUND_COST, MineSiteRules.score(40, 0, 0, false, 0));
        assertEquals(40 + MineSiteRules.ROUND_COST, MineSiteRules.score(40, 0, 0, false, 3));
        // a site whose corridor allows no lengthening loses to one with full room a good walk further out
        assertTrue(MineSiteRules.score(60, 0, 0, false, 4) < MineSiteRules.score(10, 0, 0, false, 0));
        // but one round more is not worth any walk at all
        assertTrue(MineSiteRules.score(20, 0, 0, false, 3) < MineSiteRules.score(40, 0, 0, false, 4));
        for (int r = 1; r <= 4; r++) {
            assertTrue(MineSiteRules.score(30, 3, 1, false, r) < MineSiteRules.score(30, 3, 1, false, r - 1), "more room scores better");
        }
    }

    @Test
    void setAsideMinesKeepOnlyWhatWasDug() {
        // open or resting: the whole corridor, or more where a level was lengthened further before
        assertEquals(4, MineSiteRules.keptRounds(false, 0, 4, -1), "a new mine keeps its corridor before anything is dug");
        assertEquals(3, MineSiteRules.keptRounds(false, 60, 2, 3));
        // set aside for good: what was really dug
        assertEquals(MineSiteRules.KEEPS_NOTHING, MineSiteRules.keptRounds(true, 0, 4, -1), "never dug: nothing kept");
        assertEquals(MineSiteRules.KEEPS_STAIRS, MineSiteRules.keptRounds(true, 3, 4, -1), "a shaft but no tunnels: the stairs");
        assertEquals(1, MineSiteRules.keptRounds(true, 66, 4, 1), "tunnels lengthened once: one round of corridor");
        assertEquals(0, MineSiteRules.keptRounds(true, 66, 4, 0));
        // the stairs area covers the stairs and their protected ring, and lies within the corridor
        Rect stairs = MineSiteRules.stairsArea(100, 0, 1, 0);
        for (int f = 0; f <= 3; f++) {
            for (int s = -1; s <= 2; s++) {
                assertTrue(stairs.contains(100 + f, s), "stairs cell " + f + "," + s);
            }
        }
        Rect c0 = MineSiteRules.corridor(100, 0, 1, 0, 0);
        assertTrue(c0.contains(stairs.minX(), stairs.minZ()) && c0.contains(stairs.maxX(), stairs.maxZ()));
    }
}
