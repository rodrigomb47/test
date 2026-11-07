package com.factionclash.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

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
