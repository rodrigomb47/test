package com.factionclash.structure;

import com.factionclash.campaign.WorkplaceState;
import com.factionclash.campaign.WorkplaceState.MineLevel;
import com.factionclash.logic.MineSiteRules;
import com.factionclash.logic.SpiralMine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * World positions of a player-style mine (geometry in {@link SpiralMine}): a 2x2 spiral shaft (under a surface hut
 * in mines built by older versions), and on each of the mine's levels a branch mine dug block by block by the miner.
 */
public final class MineLayout {
    public static final int HUT_BACK = -2;
    public static final int HUT_FRONT = 5;

    private MineLayout() {
    }

    public static BlockPos local(WorkplaceState wp, int f, int s, int y) {
        Direction fw = wp.facing;
        Direction side = fw.getClockWise();
        return wp.origin.relative(fw, f).relative(side, s).above(y);
    }

    public static int forwardOf(WorkplaceState wp, BlockPos p) {
        BlockPos d = p.subtract(wp.origin);
        return d.getX() * wp.facing.getStepX() + d.getZ() * wp.facing.getStepZ();
    }

    public static int sideOf(WorkplaceState wp, BlockPos p) {
        Direction side = wp.facing.getClockWise();
        BlockPos d = p.subtract(wp.origin);
        return d.getX() * side.getStepX() + d.getZ() * side.getStepZ();
    }

    /** The level being worked, or null when none is planned. */
    public static MineLevel current(WorkplaceState wp) {
        return wp.cur >= 0 && wp.cur < wp.levels.size() ? wp.levels.get(wp.cur) : null;
    }

    /** Standing position of spiral step k. */
    public static BlockPos stand(WorkplaceState wp, int k) {
        if (k <= 0) {
            return local(wp, 0, 0, 0);
        }
        int[] c = SpiralMine.cell(k);
        return local(wp, c[0], c[1], SpiralMine.standY(k));
    }

    /** Where a miner stands at the landing of a level, the start of its tunnels. */
    public static BlockPos landing(WorkplaceState wp, MineLevel lv) {
        return stand(wp, lv.step);
    }

    /** The side cell (0 or 1) of a level's landing: its main tunnel runs out from there. */
    public static int landingSide(MineLevel lv) {
        return SpiralMine.cell(lv.step)[1];
    }

    /** The level's branch-mine plan at its current length. */
    public static List<SpiralMine.Cell> tunnelPlan(MineLevel lv) {
        return SpiralMine.cachedPlan(landingSide(lv), lv.rounds);
    }

    /** World position of a tunnel-plan cell of a level. */
    public static BlockPos tunnelPos(WorkplaceState wp, MineLevel lv, SpiralMine.Cell c) {
        return local(wp, c.f(), c.s(), -lv.step + c.dy());
    }

    /**
     * How far down the spiral is dug or being dug: the bottom, or the current level's landing while the shaft is dug
     * down to it.
     */
    public static int shaftSteps(WorkplaceState wp) {
        MineLevel lv = current(wp);
        return wp.upgrading() && lv != null ? Math.max(wp.bottom, lv.step) : wp.bottom;
    }

    /** How many lengthening rounds the mine's tunnels may reach: the corridor's, or more if a level was lengthened before. */
    public static int reachRounds(WorkplaceState wp) {
        int r = wp.maxRounds;
        for (MineLevel lv : wp.levels) {
            r = Math.max(r, lv.rounds);
        }
        return r;
    }

    /** World position of a spiral-plan cell (dy is relative to the hut floor). */
    public static BlockPos spiralPos(WorkplaceState wp, SpiralMine.Cell c) {
        return local(wp, c.f(), c.s(), c.dy());
    }

    /** The spiral stairs and the rock around them (below the hut floor): kept as they are, never dug for ore. */
    public static boolean nearStairs(WorkplaceState wp, BlockPos p) {
        if (p.getY() >= wp.origin.getY()) {
            return false;
        }
        int f = forwardOf(wp, p), s = sideOf(wp, p);
        return f >= 0 && f <= 3 && s >= -1 && s <= 2;
    }

    /** Inside the stairwell itself (the 2x2 column the spiral winds down), below the hut floor. */
    public static boolean inStairwell(WorkplaceState wp, BlockPos p) {
        if (p.getY() >= wp.origin.getY()) {
            return false;
        }
        int f = forwardOf(wp, p), s = sideOf(wp, p);
        return f >= 1 && f <= 2 && s >= 0 && s <= 1;
    }

    public static boolean inShaft(WorkplaceState wp, BlockPos p, int steps) {
        return SpiralMine.inShaft(forwardOf(wp, p), sideOf(wp, p), p.getY() - wp.origin.getY(), steps);
    }

    /** Spiral step an NPC standing at {@code p} is on, 0 when at or above the hut floor or outside the shaft. */
    public static int stepAt(WorkplaceState wp, BlockPos p) {
        int f = forwardOf(wp, p), s = sideOf(wp, p);
        int dy = p.getY() - wp.origin.getY();
        if (dy >= 0 || f < 1 || f > 2 || s < 0 || s > 1) {
            return 0;
        }
        return -dy;
    }

    /** The level whose tunnels contain {@code p} (only levels the shaft reaches), or null. */
    public static MineLevel tunnelLevelAt(WorkplaceState wp, BlockPos p) {
        int f = forwardOf(wp, p);
        if (f < SpiralMine.MAIN_START) {
            return null;
        }
        for (MineLevel lv : wp.levels) {
            int y = wp.origin.getY() - lv.step;
            if (lv.step <= wp.bottom && Math.abs(p.getY() - y) <= 1) {
                return lv;
            }
        }
        return null;
    }

    /**
     * Like {@link #tunnelLevelAt} but also for a miner that dropped into a cave hole just under (or climbed just
     * above) its tunnels: the level within 4 blocks of its height, or null.
     */
    public static MineLevel nearLevelAt(WorkplaceState wp, BlockPos p) {
        MineLevel lv = tunnelLevelAt(wp, p);
        if (lv != null || forwardOf(wp, p) < SpiralMine.MAIN_START || p.getY() >= wp.origin.getY() - 2) {
            return lv;
        }
        MineLevel best = null;
        int bestD = 5;
        for (MineLevel l : wp.levels) {
            int y = wp.origin.getY() - l.step;
            if (l.step <= wp.bottom && Math.abs(p.getY() - y) < bestD) {
                bestD = Math.abs(p.getY() - y);
                best = l;
            }
        }
        return best;
    }

    /** Where a miner steps in and out: the hut's door for an old hut mine, else the surface cell by the shaft. */
    public static BlockPos entrance(WorkplaceState wp) {
        return wp.hut ? local(wp, HUT_BACK - 1, 0, 0) : local(wp, 0, 0, 0);
    }

    /**
     * True if {@code p} is underground in the mine: below the surface, no deeper than a few blocks under the shaft's
     * bottom, and within the mine's corridor (from just behind the shaft to the end of its longest tunnels, and as
     * wide as the branches).
     */
    public static boolean inMine(WorkplaceState wp, BlockPos p) {
        int y0 = wp.origin.getY();
        if (p.getY() >= y0 - 1 || p.getY() < y0 - shaftSteps(wp) - 4) {
            return false;
        }
        int f = forwardOf(wp, p), s = sideOf(wp, p);
        return f >= MineSiteRules.CORRIDOR_BACK && f <= MineSiteRules.corridorEnd(reachRounds(wp))
                && s >= MineSiteRules.CORRIDOR_SIDE_MIN && s <= MineSiteRules.CORRIDOR_SIDE_MAX;
    }

    /** True if {@code p} is inside an old mine's surface hut. */
    public static boolean inHut(WorkplaceState wp, BlockPos p) {
        if (!wp.hut) {
            return false;
        }
        int f = forwardOf(wp, p);
        return f > HUT_BACK - 1 && f <= HUT_FRONT && Math.abs(sideOf(wp, p)) <= 2 && p.closerThan(wp.origin, 7);
    }

    public static BlockState wallTorchFor(WorkplaceState wp, int s) {
        Direction side = wp.facing.getClockWise();
        // torch hangs on the shaft's outer wall: s=0 cells use the wall at s=-1, s=1 cells the wall at s=2
        return Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, s == 0 ? side : side.getOpposite());
    }
}
