package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingWaterTest {
    enum B { AIR, WATER, FLOWING, STONE, FARMLAND, LILY_PAD, KELP, LEAVES, ICE }

    /**
     * A synthetic world: ground (stone) up to {@code groundTop} everywhere unless set otherwise, air above. Water,
     * farmland and other blocks are set cell by cell.
     */
    private static class Grid implements FishingWater.Cells {
        final Map<Long, B> cells = new HashMap<>();
        int groundTop = 0;

        static long k(int x, int y, int z) {
            return CaveSurvey.pack(x, y, z);
        }

        B at(int x, int y, int z) {
            B b = cells.get(k(x, y, z));
            return b != null ? b : y <= groundTop ? B.STONE : B.AIR;
        }

        Grid set(int x, int y, int z, B b) {
            cells.put(k(x, y, z), b);
            return this;
        }

        Grid box(int x0, int y0, int z0, int x1, int y1, int z1, B b) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        set(x, y, z, b);
                    }
                }
            }
            return this;
        }

        /** A pond dug into the ground: water from the surface (groundTop) down {@code depth} blocks. */
        Grid pond(int x0, int z0, int x1, int z1, int depth) {
            return box(x0, groundTop - depth + 1, z0, x1, groundTop, z1, B.WATER);
        }

        boolean solid(int x, int y, int z) {
            B b = at(x, y, z);
            return b == B.STONE || b == B.FARMLAND || b == B.LEAVES || b == B.ICE;
        }

        @Override
        public boolean waterSource(int x, int y, int z) {
            B b = at(x, y, z);
            return b == B.WATER || b == B.KELP;
        }

        @Override
        public boolean aboveWater(int x, int y, int z) {
            B b = at(x, y, z);
            return b == B.AIR || b == B.LILY_PAD;
        }

        @Override
        public boolean insideWater(int x, int y, int z) {
            B b = at(x, y, z);
            return b == B.WATER || b == B.KELP;
        }

        @Override
        public boolean farmland(int x, int y, int z) {
            return at(x, y, z) == B.FARMLAND;
        }

        @Override
        public boolean sky(int x, int y, int z) {
            for (int yy = y; yy < y + 64; yy++) {
                if (solid(x, yy, z)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean sturdyGround(int x, int y, int z) {
            return at(x, y, z) == B.STONE;
        }

        @Override
        public boolean clear(int x, int y, int z) {
            B b = at(x, y, z);
            return b == B.AIR;
        }

        @Override
        public boolean sight(int sx, int sy, int sz, int bx, int by, int bz) {
            double ax = sx + 0.5, ay = sy + 1.62, az = sz + 0.5, ex = bx + 0.5, ey = by + 0.5, ez = bz + 0.5;
            for (int i = 0; i <= 200; i++) {
                double t = i / 200.0;
                int x = (int) Math.floor(ax + (ex - ax) * t), y = (int) Math.floor(ay + (ey - ay) * t), z = (int) Math.floor(az + (ez - az) * t);
                if ((x != bx || y != by || z != bz) && solid(x, y, z)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static void assertStandRules(FishingWater.Spot s) {
        int cheb = Math.max(Math.abs(s.sx() - s.bx()), Math.abs(s.sz() - s.bz()));
        assertTrue(cheb >= 2 && cheb <= 5, "stand 2..5 from the bobber, was " + cheb);
        assertTrue(s.sy() - s.by() >= 1 && s.sy() - s.by() <= 2, "stand 1..2 above the water, was " + (s.sy() - s.by()));
    }

    @Test
    void farmChannelReplicaRejected() {
        // the base's irrigation channel: one block deep, five long, farmland on both sides
        Grid g = new Grid();
        g.box(0, 0, 0, 0, 0, 4, B.WATER);
        g.box(-1, 0, 0, -1, 0, 4, B.FARMLAND);
        g.box(1, 0, 0, 1, 0, 4, B.FARMLAND);
        assertNull(FishingWater.evaluate(g, 0, 0, 2));
        // each rule on its own rejects it
        assertFalse(g.waterSource(0, -1, 2), "one block deep");
        assertEquals(5, FishingWater.bodySize(g, 0, 0, 2, FishingWater.BODY_CAP));
        assertTrue(FishingWater.farmlandNear(g, 0, 0, 2, FishingWater.FARMLAND_CLEARANCE));
    }

    @Test
    void pond7x7x3Accepted() {
        Grid g = new Grid().pond(0, 0, 6, 6, 3);
        FishingWater.Spot s = FishingWater.evaluate(g, 3, 0, 3);
        assertNotNull(s);
        assertTrue(s.open(), "the middle of a 7x7 pond 3 deep is open water");
        assertEquals(49, s.body());
        assertStandRules(s);
        assertTrue(g.sturdyGround(s.sx(), s.sy() - 1, s.sz()) && g.clear(s.sx(), s.sy(), s.sz()) && g.clear(s.sx(), s.sy() + 1, s.sz()));
        // near the edge it is still a spot, but not open water (the 5x5 around it reaches the bank)
        FishingWater.Spot edge = FishingWater.evaluate(g, 1, 0, 3);
        assertNotNull(edge);
        assertFalse(edge.open());
    }

    @Test
    void puddle3x3AndPond4x4Rejected() {
        Grid puddle = new Grid().pond(0, 0, 2, 2, 2);
        assertEquals(9, FishingWater.bodySize(puddle, 1, 0, 1, FishingWater.BODY_CAP));
        assertNull(FishingWater.evaluate(puddle, 1, 0, 1));
        Grid pond = new Grid().pond(0, 0, 3, 3, 3);
        assertEquals(16, FishingWater.bodySize(pond, 1, 0, 1, FishingWater.BODY_CAP));
        assertNull(FishingWater.evaluate(pond, 1, 0, 1));
        Grid five = new Grid().pond(0, 0, 4, 4, 2);
        assertNotNull(FishingWater.evaluate(five, 2, 0, 2), "5x5 is just big enough");
    }

    @Test
    void bodyCountStopsAtTheCap() {
        Grid lake = new Grid().pond(0, 0, 39, 39, 2);
        assertEquals(FishingWater.BODY_CAP, FishingWater.bodySize(lake, 20, 0, 20, FishingWater.BODY_CAP));
    }

    @Test
    void farmlandWithinFiveRejected() {
        Grid g = new Grid().pond(0, 0, 9, 9, 3);
        assertNotNull(FishingWater.evaluate(g, 2, 0, 5));
        g.set(-2, 0, 5, B.FARMLAND); // a field on the bank, 4 from the bobber: this water would hydrate it
        assertNull(FishingWater.evaluate(g, 2, 0, 5));
        assertNotNull(FishingWater.evaluate(g, 8, 0, 5), "the far side of the lake is more than 5 from the field");
    }

    @Test
    void roofedPondRejected() {
        Grid g = new Grid().pond(0, 0, 6, 6, 3);
        g.box(-6, 4, -6, 12, 4, 12, B.STONE);
        assertNull(FishingWater.evaluate(g, 3, 0, 3));
    }

    @Test
    void standRules() {
        // solid floor and head room: a stand needs ground below and two clear blocks
        Grid g = new Grid().pond(0, 0, 6, 6, 3);
        FishingWater.Spot s = FishingWater.evaluate(g, 3, 0, 3);
        assertNotNull(s);
        assertStandRules(s);
        for (int bx = 0; bx <= 6; bx++) {
            for (int bz = 0; bz <= 6; bz++) {
                FishingWater.Spot e = FishingWater.evaluate(g, bx, 0, bz);
                assertNotNull(e, "every water block of a 7x7 pond has a stand");
                assertStandRules(e);
            }
        }
        // the bank is a cliff 3 above the water: too high to stand on
        Grid cliff = new Grid().pond(0, 0, 6, 6, 3);
        cliff.box(-6, 1, -6, 12, 3, 12, B.STONE);
        cliff.box(0, 1, 0, 6, 3, 6, B.AIR);
        assertNull(FishingWater.stand(cliff, 3, 0, 3));
        // a bank 1 higher (stand 2 above the water) is fine
        Grid step = new Grid().pond(0, 0, 6, 6, 3);
        step.box(-6, 1, -6, 12, 1, 12, B.STONE);
        step.box(0, 1, 0, 6, 1, 6, B.AIR);
        int[] st = FishingWater.stand(step, 3, 0, 3);
        assertNotNull(st);
        assertEquals(2, st[1]);
        // a low roof over the whole bank leaves no head room
        Grid low = new Grid().pond(0, 0, 6, 6, 3);
        low.box(-6, 2, -6, 12, 2, 12, B.STONE);
        low.box(0, 2, 0, 6, 2, 6, B.AIR);
        assertNull(FishingWater.stand(low, 3, 0, 3));
        // a wall around the pond hides the bobber from every stand behind it
        Grid walled = new Grid().pond(0, 0, 6, 6, 3);
        walled.box(-1, 1, -1, 7, 3, 7, B.STONE);
        walled.box(0, 1, 0, 6, 3, 6, B.AIR);
        assertNull(FishingWater.stand(walled, 3, 0, 3));
        // a stand the zone rules forbid is skipped for the next one
        Grid zoned = new Grid() {
            @Override
            public boolean standAllowed(int x, int y, int z) {
                return x > 6;
            }
        }.pond(0, 0, 6, 6, 3);
        int[] east = FishingWater.stand(zoned, 5, 0, 3);
        assertNotNull(east);
        assertTrue(east[0] > 6);
        // in open water, a rock right next to the bobber (1 away) is too close to stand on; one 2 away will do
        Grid lake = new Grid();
        lake.box(-8, -5, -8, 8, 0, 8, B.WATER);
        lake.set(1, 0, 0, B.STONE);
        assertNull(FishingWater.stand(lake, 0, 0, 0), "a rock one block from the bobber is too close");
        lake.set(2, 0, 1, B.STONE);
        assertArrayEquals(new int[]{2, 1, 1}, FishingWater.stand(lake, 0, 0, 0));
    }

    @Test
    void openWaterReplica() {
        Grid g = new Grid().pond(0, 0, 6, 6, 3);
        assertTrue(FishingWater.openWater(g, 3, 0, 3));
        g.set(3, 1, 3, B.LILY_PAD).set(1, 1, 5, B.LILY_PAD);
        assertTrue(FishingWater.openWater(g, 3, 0, 3), "lily pads count as air");
        g.set(2, -1, 2, B.KELP).set(4, 0, 4, B.KELP);
        assertTrue(FishingWater.openWater(g, 3, 0, 3), "kelp counts as water");
        g.set(5, -1, 3, B.STONE);
        assertFalse(FishingWater.openWater(g, 3, 0, 3), "a layer mixing water and stone is not open water");
        // water hanging over air: the lowest layer is above water
        Grid hang = new Grid();
        hang.groundTop = -10;
        hang.box(-3, 0, -3, 3, 0, 3, B.WATER);
        assertFalse(FishingWater.openWater(hang, 0, 0, 0), "air below the water is not open water");
        // one deep: the layer below the bobber is the stone floor
        Grid shallow = new Grid().pond(0, 0, 6, 6, 1);
        assertFalse(FishingWater.openWater(shallow, 3, 0, 3));
        // water inside water above air (a waterfall cave) is invalid too
        Grid inv = new Grid().pond(0, 0, 6, 6, 3);
        inv.box(1, 2, 1, 5, 2, 5, B.WATER);
        assertFalse(FishingWater.openWater(inv, 3, 0, 3), "water above an air layer is not open water");
    }

    @Test
    void flowingWaterIsNotABobber() {
        Grid g = new Grid().pond(0, 0, 6, 6, 3);
        g.set(3, 0, 3, B.FLOWING);
        assertNull(FishingWater.evaluate(g, 3, 0, 3));
        assertEquals(48, FishingWater.bodySize(g, 2, 0, 3, FishingWater.BODY_CAP), "flowing water is not part of the body");
        Grid river = new Grid();
        river.box(0, -2, 0, 3, 0, 20, B.FLOWING);
        assertNull(FishingWater.evaluate(river, 1, 0, 10));
    }

    @Test
    void stillFishableNoticesWhatChanged() {
        Grid g = new Grid().pond(0, 0, 6, 6, 3);
        FishingWater.Spot s = FishingWater.evaluate(g, 3, 0, 3);
        assertNotNull(s);
        assertTrue(FishingWater.stillFishable(g, s.bx(), s.by(), s.bz(), s.sx(), s.sy(), s.sz()));
        g.set(s.bx(), s.by(), s.bz(), B.STONE);
        assertFalse(FishingWater.stillFishable(g, s.bx(), s.by(), s.bz(), s.sx(), s.sy(), s.sz()), "the water is gone");
        g.set(s.bx(), s.by(), s.bz(), B.WATER);
        g.set(s.sx(), s.sy() + 1, s.sz(), B.STONE);
        assertFalse(FishingWater.stillFishable(g, s.bx(), s.by(), s.bz(), s.sx(), s.sy(), s.sz()), "the stand is blocked");
        g.set(s.sx(), s.sy() + 1, s.sz(), B.AIR);
        g.set(-2, 0, 3, B.FARMLAND);
        assertFalse(FishingWater.stillFishable(g, s.bx(), s.by(), s.bz(), s.sx(), s.sy(), s.sz()), "a field was dug next to it");
    }

    @Test
    void waterKnownGate() {
        long now = 1_000_000;
        assertTrue(FishingWater.waterKnown(FishingWater.VALID, 0, now));
        assertTrue(FishingWater.waterKnown(FishingWater.UNSEARCHED, now, now));
        assertFalse(FishingWater.waterKnown(FishingWater.NONE, now, now), "a fresh failed search");
        assertFalse(FishingWater.waterKnown(FishingWater.NONE, now - 47999, now));
        assertTrue(FishingWater.waterKnown(FishingWater.NONE, now - 48000, now), "two days later the base may look again");
    }
}
