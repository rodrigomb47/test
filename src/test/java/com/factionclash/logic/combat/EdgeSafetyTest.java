package com.factionclash.logic.combat;

import com.factionclash.logic.hero.EdgePlay.Ground;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EdgeSafety} ({@code docs/design/breaching.md} 8.1 and 11.1; task T3): lethal edges (lava, fire, magma, the void,
 * a drop of 5) found and a drop of 3 not; with a hero among the pushers an edge 6 blocks behind counts, with only a
 * Knockback sword near one 5 blocks behind does not; the path cost only with a pusher near; a wall between keeps an edge
 * from counting; who is a pusher.
 */
class EdgeSafetyTest {
    /** A field of columns: lethal cells and wall cells by (x, z). */
    private static final class Field implements EdgeSafety.Columns {
        final Set<Long> lethal = new HashSet<>(), wall = new HashSet<>();

        Field lethal(int x, int z) {
            lethal.add(key(x, z));
            return this;
        }

        Field wall(int x, int z) {
            wall.add(key(x, z));
            return this;
        }

        static long key(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }

        @Override
        public int at(int x, int y, int z) {
            return wall.contains(key(x, z)) ? EdgeSafety.WALL : lethal.contains(key(x, z)) ? EdgeSafety.LETHAL : EdgeSafety.CLEAR;
        }
    }

    @Test
    void lethalEdges() {
        assertTrue(EdgeSafety.lethal(Ground.LAVA, 0, false));
        assertTrue(EdgeSafety.lethal(Ground.FIRE, 0, false), "fire, a magma block or a lit campfire");
        assertTrue(EdgeSafety.lethal(Ground.VOID, 0, false));
        assertTrue(EdgeSafety.lethal(Ground.FLOOR, 5, false), "a drop of 5");
        assertFalse(EdgeSafety.lethal(Ground.FLOOR, 4, false), "a drop of 4 keeps ledges usable");
        assertFalse(EdgeSafety.lethal(Ground.FLOOR, 3, false), "a drop of 3");
        assertFalse(EdgeSafety.lethal(Ground.WATER, 10, false), "water takes the fall");
        assertFalse(EdgeSafety.lethal(Ground.LAVA, 0, true), "lava is no edge for the fire-proof");
        assertFalse(EdgeSafety.lethal(Ground.FIRE, 0, true));
        assertTrue(EdgeSafety.lethal(Ground.FLOOR, 8, true), "but a cliff is");
        assertEquals(EdgeSafety.WALL, EdgeSafety.column(Ground.WALL, 0, false));
        assertEquals(EdgeSafety.LETHAL, EdgeSafety.column(Ground.LAVA, 0, false));
        assertEquals(EdgeSafety.CLEAR, EdgeSafety.column(Ground.FLOOR, 1, false));
    }

    @Test
    void aHerosReachIsSixBlocksAKnockbackSwordsFour() {
        // the cell at x 0.5, the pusher west of it at x -2.5: "behind" is east
        Field six = new Field().lethal(6, 0);
        double[] px = {-2.5}, pz = {0.5};
        assertTrue(EdgeSafety.unsafe(0.5, 64, 0.5, px, pz, 1, true, six), "an edge 6 behind with a hero near");
        assertFalse(EdgeSafety.unsafe(0.5, 64, 0.5, px, pz, 1, false, six), "beyond a sword's reach");
        Field five = new Field().lethal(5, 0);
        assertFalse(EdgeSafety.unsafe(0.5, 64, 0.5, px, pz, 1, false, five), "5 behind, only a Knockback sword near");
        Field four = new Field().lethal(4, 0);
        assertTrue(EdgeSafety.unsafe(0.5, 64, 0.5, px, pz, 1, false, four));
        assertEquals(6, EdgeSafety.reach(true), 1e-9);
        assertEquals(4, EdgeSafety.reach(false), 1e-9);
    }

    @Test
    void onlyTheSideAwayFromThePusherAndNotThroughAWall() {
        Field f = new Field().lethal(3, 0);
        // the pusher east of the cell, beyond the edge: the edge is in front, not behind
        assertFalse(EdgeSafety.unsafe(0.5, 64, 0.5, new double[] {10.5}, new double[] {0.5}, 1, true, f));
        // the edge to the flank (north of a pusher-to-cell line running east): not behind
        Field flank = new Field().lethal(0, -3);
        assertFalse(EdgeSafety.unsafe(0.5, 64, 0.5, new double[] {-2.5}, new double[] {0.5}, 1, true, flank));
        // a wall between the cell and the edge stops the push first
        Field walled = new Field().wall(2, 0).lethal(3, 0);
        assertFalse(EdgeSafety.unsafe(0.5, 64, 0.5, new double[] {-2.5}, new double[] {0.5}, 1, true, walled));
        // a pusher on the cell itself: every side is behind
        assertTrue(EdgeSafety.edgeBehind(0.5, 64, 0.5, 0.5, 0.5, 6, flank));
        // a pusher too far from the cell does not count
        assertFalse(EdgeSafety.unsafe(0.5, 64, 0.5, new double[] {-30.5}, new double[] {0.5}, 1, true, f));
    }

    @Test
    void thePathCostsMoreOnlyWithAPusherNear() {
        Field f = new Field().lethal(3, 0);
        assertEquals(EdgeSafety.PATH_MALUS, EdgeSafety.malus(EdgeSafety.unsafe(0.5, 64, 0.5, new double[] {-2.5},
                new double[] {0.5}, 1, true, f)), 1e-9);
        assertEquals(0, EdgeSafety.malus(EdgeSafety.unsafe(0.5, 64, 0.5, new double[0], new double[0], 0, false, f)), 1e-9,
                "no pusher near: plain costs, the short way");
        assertEquals(4, EdgeSafety.PATH_MALUS, 1e-9);
    }

    @Test
    void whoIsAPusher() {
        assertTrue(EdgeSafety.pusher(true, 0, 0, false, false), "any hero");
        assertTrue(EdgeSafety.pusher(false, 1, 0, false, false), "a Knockback weapon, a player's or an NPC's");
        assertTrue(EdgeSafety.pusher(false, 0, 2, false, false), "a Punch bow");
        assertTrue(EdgeSafety.pusher(false, 0, 0, true, false), "a ravager, hoglin, zoglin, iron golem or breeze");
        assertTrue(EdgeSafety.pusher(false, 0, 0, false, true), "wind charges");
        assertFalse(EdgeSafety.pusher(false, 0, 0, false, false), "a plain sword");
    }
}
