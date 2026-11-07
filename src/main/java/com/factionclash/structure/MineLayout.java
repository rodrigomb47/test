package com.factionclash.structure;

import com.factionclash.campaign.WorkplaceState;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.SpiralMine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * World positions of a player-style mine (geometry in {@link SpiralMine}): a surface hut over a 2x2 spiral shaft,
 * and at each level a branch mine dug block by block by the miner.
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

    /** Current working level (tier, plus free extra floors beyond tier 4). */
    public static int level(WorkplaceState wp) {
        return wp.tier + wp.subLevel;
    }

    public static int landingSteps(WorkplaceState wp, int level) {
        return SpiralMine.landingSteps(wp.origin.getY(), level);
    }

    /** Standing position of spiral step k. */
    public static BlockPos stand(WorkplaceState wp, int k) {
        if (k <= 0) {
            return local(wp, 0, 0, 0);
        }
        int[] c = SpiralMine.cell(k);
        return local(wp, c[0], c[1], SpiralMine.standY(k));
    }

    public static BlockPos landing(WorkplaceState wp, int level) {
        return stand(wp, landingSteps(wp, level));
    }

    public static int landingSide(WorkplaceState wp, int level) {
        return SpiralMine.cell(landingSteps(wp, level))[1];
    }

    public static List<SpiralMine.Cell> tunnelPlan(WorkplaceState wp) {
        return SpiralMine.tunnelPlan(landingSide(wp, level(wp)));
    }

    /** World position of a tunnel-plan cell of the current level. */
    public static BlockPos tunnelPos(WorkplaceState wp, SpiralMine.Cell c) {
        BlockPos landing = landing(wp, level(wp));
        int y = landing.getY() - wp.origin.getY();
        return local(wp, c.f(), c.s(), y + c.dy());
    }

    /** World position of a spiral-plan cell (dy is relative to the hut floor). */
    public static BlockPos spiralPos(WorkplaceState wp, SpiralMine.Cell c) {
        return local(wp, c.f(), c.s(), c.dy());
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

    /** The level whose tunnels contain {@code p}, or 0. */
    public static int tunnelLevelAt(WorkplaceState wp, BlockPos p) {
        int f = forwardOf(wp, p);
        if (f < SpiralMine.MAIN_START) {
            return 0;
        }
        for (int lv = 1; lv <= level(wp); lv++) {
            int y = SpiralMine.levelY(wp.origin.getY(), lv);
            if (y != Integer.MIN_VALUE && Math.abs(p.getY() - y) <= 1) {
                return lv;
            }
        }
        return 0;
    }

    /**
     * Like {@link #tunnelLevelAt} but also for a miner that dropped into a cave hole just under (or climbed just
     * above) its tunnels: the level within 4 blocks of its height, or 0.
     */
    public static int nearLevelAt(WorkplaceState wp, BlockPos p) {
        int lv = tunnelLevelAt(wp, p);
        if (lv > 0 || forwardOf(wp, p) < SpiralMine.MAIN_START || p.getY() >= wp.origin.getY() - 2) {
            return lv;
        }
        int best = 0, bestD = 5;
        for (int l = 1; l <= level(wp); l++) {
            int y = SpiralMine.levelY(wp.origin.getY(), l);
            if (y != Integer.MIN_VALUE && Math.abs(p.getY() - y) < bestD) {
                bestD = Math.abs(p.getY() - y);
                best = l;
            }
        }
        return best;
    }

    public static BlockPos entrance(WorkplaceState wp) {
        return local(wp, HUT_BACK - 1, 0, 0);
    }

    public static BlockState wallTorchFor(WorkplaceState wp, int s) {
        Direction side = wp.facing.getClockWise();
        // torch hangs on the shaft's outer wall: s=0 cells use the wall at s=-1, s=1 cells the wall at s=2
        return Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, s == 0 ? side : side.getOpposite());
    }

    /** Surface hut plus the first spiral steps down to level 1 (the rest is dug by the miner). */
    public static void buildInitial(Blueprint bp, WorkplaceState wp, FactionType faction) {
        BlockState wall = Blocks.COBBLESTONE.defaultBlockState();
        BlockState frame = faction.style.frame().defaultBlockState();
        BlockState roof = faction.style.floor().defaultBlockState();
        for (int f = HUT_BACK; f <= HUT_FRONT; f++) {
            for (int s = -2; s <= 2; s++) {
                bp.setAbs(local(wp, f, s, -1), Blocks.COBBLESTONE.defaultBlockState());
                boolean edge = f == HUT_BACK || f == HUT_FRONT || s == -2 || s == 2;
                boolean corner = (f == HUT_BACK || f == HUT_FRONT) && (s == -2 || s == 2);
                for (int y = 0; y <= 2; y++) {
                    bp.setAbs(local(wp, f, s, y), edge ? (corner ? frame : wall) : Blocks.AIR.defaultBlockState());
                }
                bp.setAbs(local(wp, f, s, 3), roof);
            }
        }
        bp.setAbs(local(wp, HUT_BACK, 0, 0), Blocks.AIR.defaultBlockState());
        bp.setAbs(local(wp, HUT_BACK, 0, 1), Blocks.AIR.defaultBlockState());
        Direction side = wp.facing.getClockWise();
        bp.setAbs(local(wp, -1, -1, 1), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, side));
        bp.setAbs(local(wp, 4, 1, 1), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, side.getOpposite()));
        for (SpiralMine.Cell c : SpiralMine.spiralPlan(0, landingSteps(wp, 1))) {
            bp.setAbs(spiralPos(wp, c), c.torch() ? wallTorchFor(wp, c.s()) : Blocks.AIR.defaultBlockState());
        }
        BlockPos landing = landing(wp, 1);
        bp.setAbs(landing.above(), wallTorchFor(wp, landingSide(wp, 1)));
    }

    /** Pickaxe consumed to open the next tier (PROPOSED: stone for tier 2, iron for tiers 3 and 4). */
    public static int upgradePickTier(int nextTier) {
        return nextTier <= 2 ? 1 : 2;
    }

    public static final int UPGRADE_TORCHES_PER_8_STEPS = 1;
}
