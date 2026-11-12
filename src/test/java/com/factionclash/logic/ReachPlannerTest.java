package com.factionclash.logic;

import com.factionclash.logic.ReachPlanner.Cell;
import com.factionclash.logic.ReachPlanner.Plan;
import com.factionclash.logic.ReachPlanner.Step;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReachPlannerTest {
    /** Flat ground (y <= 0, never broken); blocks above it with a break cost or FIXED; fences have no top to stand on. */
    static final class Grid implements ReachPlanner.World {
        final Map<Cell, Integer> blocks = new HashMap<>();
        final Set<Cell> fences = new HashSet<>();

        Grid set(int x, int y, int z, int cost) {
            blocks.put(new Cell(x, y, z), cost);
            return this;
        }

        Grid fence(int x, int y, int z) {
            fences.add(new Cell(x, y, z));
            return set(x, y, z, ReachPlanner.FIXED);
        }

        @Override
        public int cell(int x, int y, int z) {
            return y <= 0 ? ReachPlanner.FIXED : blocks.getOrDefault(new Cell(x, y, z), ReachPlanner.OPEN);
        }

        @Override
        public boolean floor(int x, int y, int z) {
            return cell(x, y, z) != ReachPlanner.OPEN && !fences.contains(new Cell(x, y, z));
        }

        @Override
        public boolean placeable(int x, int y, int z) {
            return cell(x, y, z) == ReachPlanner.OPEN;
        }
    }

    static final int CAP = 600;

    /** A wall along z = 3 from x = -12 to 12, y 1 to 3, all FIXED. */
    static Grid wall() {
        Grid g = new Grid();
        for (int x = -12; x <= 12; x++) {
            for (int y = 1; y <= 3; y++) {
                g.set(x, y, 3, ReachPlanner.FIXED);
            }
        }
        return g;
    }

    static void neverBreaksFixed(Grid g, Plan p) {
        for (Step s : p.steps()) {
            for (Cell c : s.breaks()) {
                assertNotEquals(ReachPlanner.FIXED, g.cell(c.x(), c.y(), c.z()), "broke a fixed block at " + c);
            }
        }
    }

    @Test
    void walksWhenNothingIsInTheWay() {
        Plan p = ReachPlanner.plan(new Grid(), new Cell(0, 1, 0), new Cell(5, 1, 0), 0, true, CAP);
        assertNotNull(p);
        assertEquals(-1, p.firstWork());
        assertEquals(4, p.steps().size(), "four steps to the cell beside the target");
    }

    @Test
    void strikesFromWhereItStands() {
        Plan p = ReachPlanner.plan(new Grid(), new Cell(0, 1, 0), new Cell(1, 1, 1), 0, true, CAP);
        assertNotNull(p);
        assertTrue(p.steps().isEmpty());
    }

    @Test
    void breaksTheBlockPutInADoorwayAndNothingElse() {
        // a broken door: the player's dirt (15 ticks) at the bottom, the gap of the door's top half, the lintel above
        Grid g = wall().set(0, 1, 3, 15).set(0, 2, 3, ReachPlanner.OPEN);
        Plan p = ReachPlanner.plan(g, new Cell(0, 1, 0), new Cell(0, 1, 5), 0, true, CAP);
        assertNotNull(p);
        neverBreaksFixed(g, p);
        Step work = p.steps().get(p.firstWork());
        assertEquals(java.util.List.of(new Cell(0, 1, 3)), work.breaks());
        assertEquals(new Cell(0, 1, 2), work.from(), "breaks it from right in front of the doorway");
        assertFalse(p.undermine());
    }

    @Test
    void noWayThroughAWallItMayNotBreak() {
        Grid g = wall();
        assertNull(ReachPlanner.plan(g, new Cell(0, 1, 0), new Cell(0, 1, 5), 2, true, CAP));
    }

    @Test
    void walksRoundRatherThanBreakSlowly() {
        // a slow block in the way (90 ticks) and an open gap three blocks to the side
        Grid g = wall().set(0, 1, 3, 90).set(0, 2, 3, 90).set(3, 1, 3, ReachPlanner.OPEN).set(3, 2, 3, ReachPlanner.OPEN);
        Plan p = ReachPlanner.plan(g, new Cell(0, 1, 0), new Cell(0, 1, 5), 0, true, CAP);
        assertNotNull(p);
        assertEquals(-1, p.firstWork(), "the way round is cheaper than breaking");
    }

    @Test
    void givesUpWhenBreakingTakesTooLong() {
        Grid g = wall().set(0, 1, 3, 400).set(0, 2, 3, 400);
        assertNull(ReachPlanner.plan(g, new Cell(0, 1, 0), new Cell(0, 1, 5), 0, true, 300));
    }

    @Test
    void aOneHighPerchNeedsNoWork() {
        Grid g = new Grid().set(3, 1, 0, 15);
        Plan p = ReachPlanner.plan(g, new Cell(0, 1, 0), new Cell(3, 2, 0), 0, true, CAP);
        assertNotNull(p);
        assertEquals(-1, p.firstWork(), "a target one block up is struck from the ground beside it");
    }

    @Test
    void breaksTheBlockUnderATargetOnATwoHighPillar() {
        Grid g = new Grid().set(3, 1, 0, 15).set(3, 2, 0, 15);
        Plan p = ReachPlanner.plan(g, new Cell(0, 1, 0), new Cell(3, 3, 0), 0, true, CAP);
        assertNotNull(p);
        assertTrue(p.undermine());
        Step last = p.steps().get(p.steps().size() - 1);
        assertEquals(java.util.List.of(new Cell(3, 2, 0)), last.breaks(), "the block right under the target's feet");
        assertEquals(last.from(), last.to());
        assertNull(ReachPlanner.plan(g, new Cell(0, 1, 0), new Cell(3, 3, 0), 0, false, CAP), "not asked to undermine, no blocks: no way");
    }

    @Test
    void pillarsUpBesideAPerchItMayNotBreak() {
        Grid g = new Grid().set(3, 1, 0, ReachPlanner.FIXED).set(3, 2, 0, ReachPlanner.FIXED);
        Plan p = ReachPlanner.plan(g, new Cell(0, 1, 0), new Cell(3, 3, 0), 3, true, CAP);
        assertNotNull(p);
        assertEquals(1, p.blocksUsed(), "one block up is enough to strike a target two up");
        neverBreaksFixed(g, p);
        assertNull(ReachPlanner.plan(g, new Cell(0, 1, 0), new Cell(3, 3, 0), 0, true, CAP), "without blocks there is no way up");
    }

    @Test
    void neverBuildsHigherThanTheTarget() {
        // a target on a 4-high planned tower: two blocks are not enough, and it never pillars past the target
        Grid g = new Grid();
        for (int y = 1; y <= 4; y++) {
            g.set(3, y, 0, ReachPlanner.FIXED);
        }
        assertNull(ReachPlanner.plan(g, new Cell(0, 1, 0), new Cell(3, 5, 0), 2, true, CAP));
        Plan p = ReachPlanner.plan(g, new Cell(0, 1, 0), new Cell(3, 5, 0), 5, true, CAP);
        assertNotNull(p);
        assertEquals(3, p.blocksUsed());
    }

    @Test
    void goesThroughAGateRatherThanOverTheFence() {
        // a pen of fences (fixed, no top) with an open gate cell; the target inside
        Grid g = new Grid();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) == 3 || Math.abs(z) == 3) {
                    g.fence(x, 1, z);
                }
            }
        }
        g.set(0, 1, -3, ReachPlanner.OPEN);
        Plan p = ReachPlanner.plan(g, new Cell(0, 1, -7), new Cell(0, 1, 1), 2, true, CAP);
        assertNotNull(p);
        assertEquals(-1, p.firstWork());
        assertTrue(p.steps().stream().anyMatch(s -> s.to().equals(new Cell(0, 1, -3))), "through the gate");
        g.fence(0, 1, -3);
        assertNull(ReachPlanner.plan(g, new Cell(0, 1, -7), new Cell(0, 1, 1), 2, true, CAP), "a sealed pen of fixed fences has no way in");
    }

    @Test
    void aCornerHidesTheTarget() {
        Grid g = new Grid().set(1, 2, 0, ReachPlanner.FIXED).set(0, 2, 1, ReachPlanner.FIXED);
        assertFalse(ReachPlanner.canStrike(g, new Cell(0, 1, 0), new Cell(1, 1, 1)));
        g.set(0, 2, 1, ReachPlanner.OPEN);
        assertTrue(ReachPlanner.canStrike(g, new Cell(0, 1, 0), new Cell(1, 1, 1)));
        assertFalse(ReachPlanner.canStrike(g, new Cell(0, 1, 0), new Cell(0, 3, 1)), "two up is out of a melee swing");
    }

    @Test
    void stepUpNeedsHeadRoomAboveTheFighter() {
        // a block in the doorway with the lintel right above the doorway's top: no stepping up onto it
        Grid g = wall().set(0, 1, 3, ReachPlanner.FIXED).set(0, 2, 3, ReachPlanner.OPEN).set(0, 1, 2, 15)
                .set(0, 3, 2, ReachPlanner.FIXED);
        // the only way: break the block in front (15) and ... the doorway block itself is fixed: no way through
        assertNull(ReachPlanner.plan(g, new Cell(0, 1, 0), new Cell(0, 1, 5), 0, true, CAP));
    }
}
