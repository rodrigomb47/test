package com.factionclash.npc;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.work.Crafting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Last-resort recovery for an NPC with no route at all (sealed cave pocket, ravine floor, cliff): it digs one
 * staircase step toward its destination like a player would, then moves onto that validated spot (at most one
 * block across and one up). Faction plan blocks, containers, unbreakable blocks and anything touching a fluid are
 * never dug.
 */
public final class DigOut {
    private DigOut() {
    }

    public static boolean step(FactionNpc npc, BlockPos target) {
        if (!(npc.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos p = npc.blockPosition();
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
        boolean underground = p.getY() < surface - 2;
        List<Direction> dirs = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        double tx = target.getX() - p.getX(), tz = target.getZ() - p.getZ();
        dirs.sort(Comparator.comparingDouble(d -> -(d.getStepX() * tx + d.getStepZ() * tz)));
        for (Direction d : dirs) {
            // destination well below (e.g. stuck on a cliff top): dig a staircase down; underground: climb (step
            // up); on the surface: tunnel level first
            boolean below = target.getY() < p.getY() - 1;
            int[] order = below ? new int[]{-1, 0} : underground ? new int[]{1, 0} : new int[]{0, 1};
            for (int dy : order) {
                BlockPos q = p.relative(d).above(dy);
                List<BlockPos> clear = new ArrayList<>(List.of(q, q.above()));
                if (dy == 1) {
                    clear.add(p.above(2));
                } else if (dy == -1) {
                    clear.add(q.above(2));
                }
                if (!level.getBlockState(q.below()).isFaceSturdy(level, q.below(), Direction.UP) || !allDiggable(level, clear)) {
                    continue;
                }
                for (BlockPos c : clear) {
                    BlockState s = level.getBlockState(c);
                    if (!s.isAir()) {
                        for (ItemStack drop : Block.getDrops(s, level, c, null, npc, npc.getMainHandItem())) {
                            ItemStack rest = Crafting.addAll(npc.inventory(), drop);
                            if (!rest.isEmpty()) {
                                npc.spawnAtLocation(rest);
                            }
                        }
                        level.setBlock(c, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        level.levelEvent(2001, c, Block.getId(s));
                    }
                }
                if (NpcNavigator.isSafeStandingSpot(level, npc, q)) {
                    npc.swing(InteractionHand.MAIN_HAND);
                    npc.getNavigation().stop();
                    npc.teleportTo(q.getX() + 0.5, q.getY(), q.getZ() + 0.5);
                    npc.navigator().recoveries++;
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean allDiggable(ServerLevel level, List<BlockPos> cells) {
        CampaignData data = CampaignData.get(level.getServer());
        for (BlockPos c : cells) {
            BlockState s = level.getBlockState(c);
            if (s.isAir()) {
                continue;
            }
            if (s.getDestroySpeed(level, c) < 0 || s.hasBlockEntity() || !s.getFluidState().isEmpty()) {
                return false;
            }
            for (Direction n : Direction.values()) {
                if (!level.getFluidState(c.relative(n)).isEmpty()) {
                    return false; // never open a path for water or lava
                }
            }
            for (BaseState b : data.bases.values()) {
                if (b.destroyed) {
                    continue;
                }
                if (b.planIndex().containsKey(c.asLong())) {
                    return false; // faction structures are never dug
                }
                int half = (b.isSettlement() ? com.factionclash.structure.BaseLayout.HALF : com.factionclash.structure.LairLayout.HALF) + 3;
                if (Math.abs(c.getX() - b.center.getX()) <= half && Math.abs(c.getZ() - b.center.getZ()) <= half
                        && c.getY() <= b.center.getY() + 8) {
                    return false; // never tunnel under or through a settlement's walls
                }
            }
        }
        return true;
    }
}
