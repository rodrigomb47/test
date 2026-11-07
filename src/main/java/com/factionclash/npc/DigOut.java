package com.factionclash.npc;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.work.Crafting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
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
 * Last-resort recovery for an NPC with no route at all (sealed cave pocket, ravine floor, cliff, tree canopy): it
 * digs a staircase toward its destination like a player would, one step at a time, moving onto each validated spot
 * (at most one block across and one up or down). Jobs keep digging until the NPC is out in the open or a route
 * exists again. Faction plan blocks, containers, unbreakable blocks and anything touching a fluid are
 * never dug.
 */
public final class DigOut {
    private DigOut() {
    }

    /** True once the NPC is out in the open (not under cover, not up a tree): normal walking takes over. */
    public static boolean free(FactionNpc npc) {
        BlockPos p = npc.blockPosition();
        int surface = npc.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
        return p.getY() >= surface - 2 && !perched(npc.level(), p);
    }

    /**
     * Up a tree: on its canopy, or on top of a trunk (3 or more blocks of wood and leaves below, often with a
     * canopy all around). Walking cannot get it down; chopping what is underfoot can.
     */
    public static boolean perched(net.minecraft.world.level.Level level, BlockPos p) {
        BlockState below = level.getBlockState(p.below());
        if (below.is(BlockTags.LEAVES)) {
            return true;
        }
        int wood = 0;
        while (wood < 8 && (level.getBlockState(p.below(wood + 1)).is(BlockTags.LOGS) || level.getBlockState(p.below(wood + 1)).is(BlockTags.LEAVES))) {
            wood++;
        }
        return wood >= 3;
    }

    /**
     * Shut in with no route: under cover, on a canopy, or at the bottom of a pit or ravine far below the
     * destination. Such an NPC keeps digging its staircase instead of walking back to the same dead end.
     */
    public static boolean shutIn(FactionNpc npc, BlockPos target) {
        return !free(npc) || target.getY() - npc.getBlockY() > CLIMB;
    }

    private static final int CLIMB = 4;

    public static boolean step(FactionNpc npc, BlockPos target) {
        if (!(npc.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos p = npc.blockPosition();
        if (perched(level, p) && allDiggable(level, List.of(p.below()))) {
            // stranded up a tree: break the leaves or log underfoot and drop down, as a player would
            dig(level, npc, p.below());
            npc.swing(InteractionHand.MAIN_HAND);
            npc.getNavigation().stop();
            npc.navigator().recoveries++;
            return true;
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
        boolean underground = p.getY() < surface - 2;
        List<Direction> dirs = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        double tx = target.getX() - p.getX(), tz = target.getZ() - p.getZ();
        BaseState over = underground ? settlementOver(level, p) : null;
        if (over != null) {
            // under a settlement's edge, where its floor and walls may not be dug: head out from under it first
            tx = p.getX() - over.center.getX();
            tz = p.getZ() - over.center.getZ();
        }
        final double fx = tx, fz = tz;
        dirs.sort(Comparator.comparingDouble(d -> -(d.getStepX() * fx + d.getStepZ() * fz)));
        // destination above: climb (a step up in any direction beats walking along a ledge back to the same dead
        // end); destination well below (a cliff top, a pocket over the mine tunnels): dig a staircase down;
        // otherwise tunnel level toward it
        boolean below = target.getY() < p.getY() - 1;
        boolean climb = target.getY() > p.getY() + 1;
        int[] order = climb ? new int[]{1, 0} : below ? new int[]{-1, 0} : new int[]{0, 1};
        for (int dy : order) {
            for (Direction d : dirs) {
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
                    dig(level, npc, c);
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

    /** The settlement whose protected floor band lies right above this underground spot, if any. */
    private static BaseState settlementOver(ServerLevel level, BlockPos p) {
        for (BaseState b : CampaignData.get(level.getServer()).bases.values()) {
            int half = com.factionclash.structure.BaseLayout.HALF + 2;
            if (!b.destroyed && b.isSettlement() && Math.abs(p.getX() - b.center.getX()) <= half
                    && Math.abs(p.getZ() - b.center.getZ()) <= half && p.getY() < b.center.getY() - 4 && p.getY() >= b.center.getY() - 12) {
                return b;
            }
        }
        return null;
    }

    private static void dig(ServerLevel level, FactionNpc npc, BlockPos c) {
        BlockState s = level.getBlockState(c);
        if (s.isAir()) {
            return;
        }
        for (ItemStack drop : Block.getDrops(s, level, c, null, npc, npc.getMainHandItem())) {
            ItemStack rest = Crafting.addAll(npc.inventory(), drop);
            if (!rest.isEmpty()) {
                npc.spawnAtLocation(rest);
            }
        }
        level.setBlock(c, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.levelEvent(2001, c, Block.getId(s));
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
            for (com.factionclash.campaign.WorkplaceState w : data.workplaces.values()) {
                if (w.type == com.factionclash.campaign.WorkplaceState.Type.MINE && c.getY() < w.origin.getY()) {
                    int f = com.factionclash.structure.MineLayout.forwardOf(w, c), sd = com.factionclash.structure.MineLayout.sideOf(w, c);
                    if (f >= 0 && f <= 3 && sd >= -1 && sd <= 2) {
                        return false; // a mine's spiral stairs and the rock around them: its floors hold the stairs up
                    }
                }
            }
            for (BaseState b : data.bases.values()) {
                if (b.destroyed) {
                    continue;
                }
                if (b.planIndex().containsKey(c.asLong())) {
                    return false; // faction structures are never dug
                }
                int half = (b.isSettlement() ? com.factionclash.structure.BaseLayout.HALF : com.factionclash.structure.LairLayout.HALF) + 1;
                if (Math.abs(c.getX() - b.center.getX()) <= half && Math.abs(c.getZ() - b.center.getZ()) <= half
                        && c.getY() >= b.center.getY() - 4 && c.getY() <= b.center.getY() + 8) {
                    return false; // never through a settlement's walls or floor (deep caves below it are fine)
                }
            }
        }
        return true;
    }
}
