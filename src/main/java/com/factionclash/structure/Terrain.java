package com.factionclash.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Ground preparation: levels an area to a walking height, fills gaps with dirt and keeps water out. */
public final class Terrain {
    private Terrain() {
    }

    /**
     * Levels the rectangle so that y0 is the walking level: everything from y0 up to y0+clearHeight is cleared,
     * the surface at y0-1 becomes {@code surface}, and holes below are filled with dirt.
     */
    public static void level(ServerLevel level, int x1, int z1, int x2, int z2, int y0, int clearHeight, BlockState surface) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int y = y0; y <= y0 + clearHeight; y++) {
                    m.set(x, y, z);
                    if (!level.getBlockState(m).isAir()) {
                        level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                m.set(x, y0 - 1, z);
                level.setBlock(m, surface, Block.UPDATE_CLIENTS);
                for (int y = y0 - 2; y >= y0 - 14; y--) {
                    m.set(x, y, z);
                    BlockState s = level.getBlockState(m);
                    if (isSolidGround(s)) {
                        break;
                    }
                    level.setBlock(m, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        sealRing(level, x1 - 1, z1 - 1, x2 + 1, z2 + 1, y0);
    }

    /**
     * Blends the ground around levelled pads into the landscape: within {@code width} blocks of the nearest pad,
     * a column may lie at most d blocks above or below the pads' surface at distance d, so the edge becomes a
     * walkable slope of one block per block instead of a cliff or a trench (NPCs could get trapped in those). A
     * column with no ground at all within reach (a ravine beside the pads) is filled the same way. Columns inside a
     * pad, and columns holding water, are left alone.
     */
    public static void skirt(ServerLevel level, List<int[]> pads, int surfaceY, int width, BlockState surface) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] r : pads) {
            minX = Math.min(minX, Math.min(r[0], r[2]));
            maxX = Math.max(maxX, Math.max(r[0], r[2]));
            minZ = Math.min(minZ, Math.min(r[1], r[3]));
            maxZ = Math.max(maxZ, Math.max(r[1], r[3]));
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = minX - width; x <= maxX + width; x++) {
            for (int z = minZ - width; z <= maxZ + width; z++) {
                int d = com.factionclash.logic.Pads.distance(pads, x, z);
                if (d <= 0 || d > width) {
                    continue;
                }
                int top = Integer.MIN_VALUE;
                boolean wet = false;
                int bottom = surfaceY - width - 14;
                for (int y = surfaceY + 48; y >= bottom; y--) {
                    m.set(x, y, z);
                    BlockState s = level.getBlockState(m);
                    if (!s.getFluidState().isEmpty()) {
                        wet = true;
                        break;
                    }
                    if (isSolidGround(s)) {
                        top = y;
                        break;
                    }
                }
                if (wet) {
                    continue;
                }
                if (top == Integer.MIN_VALUE) {
                    // no ground within reach: a ravine or a deep cave mouth right beside the pads. Left as it is, it is a
                    // sheer drop beside the walls that NPCs fell into and could not climb out of; it gets the same slope,
                    // on dirt filled in from as deep as the search went
                    top = bottom - 1;
                }
                int lo = surfaceY - d, hi = surfaceY + d;
                if (top < lo) {
                    for (int y = top + 1; y < lo; y++) {
                        level.setBlock(m.set(x, y, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                    level.setBlock(m.set(x, lo, z), surface, Block.UPDATE_CLIENTS);
                } else if (top > hi) {
                    for (int y = hi + 1; y <= top + 2; y++) {
                        m.set(x, y, z);
                        if (!level.getBlockState(m).isAir()) {
                            level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                    level.setBlock(m.set(x, hi, z), surface, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Replaces fluids in the ring around a levelled area so water or lava cannot flow in. */
    private static void sealRing(ServerLevel level, int x1, int z1, int x2, int z2, int y0) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (x != x1 && x != x2 && z != z1 && z != z2) {
                    continue;
                }
                for (int y = y0 - 3; y <= y0 + 2; y++) {
                    m.set(x, y, z);
                    if (!level.getFluidState(m).isEmpty()) {
                        level.setBlock(m, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    public static boolean isSolidGround(BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty()) {
            return false;
        }
        if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.canBeReplaced()) {
            return false;
        }
        return s.isSolid();
    }
}
