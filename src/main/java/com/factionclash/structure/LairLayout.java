package com.factionclash.structure;

import com.factionclash.campaign.BaseState;
import com.factionclash.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** A ruined Hollow lair: broken mossy walls, a dark roofed hall and the Hollow heart at its centre. */
public final class LairLayout {
    public static final int HALF = 7;

    private LairLayout() {
    }

    public static Blueprint generate(BlockPos center, RandomSource random) {
        Blueprint bp = new Blueprint(center);
        bp.mark(BaseState.MIN, -HALF - 1, -1, -HALF - 1);
        bp.mark(BaseState.MAX, HALF + 1, 6, HALF + 1);
        BlockState mossy = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                bp.set(x, -1, z, random.nextInt(3) == 0 ? mossy : cobble);
                boolean edge = Math.abs(x) == HALF || Math.abs(z) == HALF;
                for (int y = 0; y <= 3; y++) {
                    if (edge) {
                        boolean gap = (Math.abs(x) <= 1 || Math.abs(z) <= 1) && y <= 2;
                        boolean broken = y == 3 && random.nextInt(3) == 0;
                        bp.set(x, y, z, gap || broken ? Blocks.AIR.defaultBlockState() : (random.nextBoolean() ? mossy : cobble));
                    } else {
                        bp.set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                }
                if (!edge && random.nextInt(9) != 0) {
                    bp.set(x, 4, z, random.nextBoolean() ? mossy : cobble);
                }
            }
        }
        bp.set(0, 0, 0, ModBlocks.FACTION_HEART.get().defaultBlockState());
        bp.mark(BaseState.HEART, 0, 0, 0);
        bp.mark(BaseState.SPAWN, 3, 0, 3);
        bp.mark(BaseState.STATION_STAND, 0, 0, 2);
        bp.set(-4, 0, -4, Blocks.COBWEB);
        bp.set(4, 0, -4, Blocks.COBWEB);
        bp.set(-4, 0, 4, Blocks.SKELETON_SKULL);
        return bp;
    }
}
