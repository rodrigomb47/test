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
 * (at most one block across and one up or down), and bridges gaps with blocks from its backpack. Jobs keep
 * digging until the NPC is out in the open or a route exists again. Faction plan blocks, containers, unbreakable
 * blocks and anything touching a fluid are never dug.
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
        if (target.getY() > p.getY() + 1 && climbStairs(level, npc, p)) {
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
        boolean below = target.getY() < p.getY() - 1;
        boolean climb = target.getY() > p.getY() + 1;
        if (!below && bridge(level, npc, p, dirs.get(0), climb)) {
            return true;
        }
        // destination above: climb (a step up in any direction beats walking along a ledge back to the same dead
        // end); destination well below (a cliff top, a pocket over the mine tunnels): dig a staircase down;
        // otherwise tunnel level toward it
        for (int dy : climb ? new int[]{1, 0} : below ? new int[]{-1, 0} : new int[]{0, 1}) {
            if (move(level, npc, p, dirs, dy, fx, fz, false)) {
                return true;
            }
        }
        if (climb) {
            // nothing to dig a step into and nowhere new to go (open air, or ground that may not be dug, such as a
            // settlement's path overhead): build up instead - a block to step onto, or one underfoot to pillar up
            for (Direction d : dirs.subList(1, dirs.size())) {
                if (bridge(level, npc, p, d, true)) {
                    return true;
                }
            }
            if (pillar(level, npc, p)) {
                return true;
            }
        }
        // last of all, a step back down: out on a bridge with no blocks left, or on a ledge with no way on, it walks
        // back (the spot it leaves is a dead end it will not return to) toward ground it can dig its way through
        return !below && move(level, npc, p, dirs, -1, fx, fz, true);
    }

    /**
     * One step (dy = 1 up, 0 level, -1 down) in the first direction that works: digs the way clear and moves onto
     * the spot. Never back onto a spot it just left (pacing between two cells gets nowhere) unless retreating, and
     * never onto a dead end.
     */
    private static boolean move(ServerLevel level, FactionNpc npc, BlockPos p, List<Direction> dirs, int dy, double fx, double fz, boolean retreat) {
        for (Direction d : dirs) {
            BlockPos q = p.relative(d).above(dy);
            if (npc.navigator().isDeadEnd(q) || !retreat && npc.navigator().recentlyLeft(q)) {
                continue;
            }
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
                if (retreat || d.getStepX() * fx + d.getStepZ() * fz < 0) {
                    npc.navigator().deadEnd(p); // turning back: no way on from here
                }
                stepTo(npc, p, q);
                return true;
            }
        }
        return false;
    }

    private static void stepTo(FactionNpc npc, BlockPos from, BlockPos to) {
        npc.swing(InteractionHand.MAIN_HAND);
        npc.getNavigation().stop();
        npc.navigator().leftSpot(from);
        npc.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        npc.navigator().recoveries++;
    }

    /** Pillars up one block, as a player would: a block from the backpack placed underfoot with a jump. */
    private static boolean pillar(ServerLevel level, FactionNpc npc, BlockPos p) {
        int slot = blockSlot(level, npc);
        BlockPos head = p.above(2);
        BlockState hs = level.getBlockState(head);
        if (slot < 0 || !hs.isAir() && !allDiggable(level, List.of(head)) || !level.getBlockState(p).canBeReplaced()
                || !level.getBlockState(p).getFluidState().isEmpty() || isProtected(level, p)) {
            return false;
        }
        dig(level, npc, head);
        BlockPos up = p.above();
        npc.teleportTo(up.getX() + 0.5, up.getY(), up.getZ() + 0.5);
        ItemStack block = npc.inventory().getItem(slot);
        BlockState place = ((net.minecraft.world.item.BlockItem) block.getItem()).getBlock().defaultBlockState();
        level.setBlock(p, place, Block.UPDATE_ALL);
        level.playSound(null, p, place.getSoundType().getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        block.shrink(1);
        npc.inventory().setChanged();
        npc.swing(InteractionHand.MAIN_HAND);
        npc.getNavigation().stop();
        npc.navigator().leftSpot(p);
        npc.navigator().recoveries++;
        return true;
    }

    /**
     * In a mine's stairwell with a step missing (its floor mined out or washed away), where the stairs may not be
     * dug: climb the spiral one step, putting the missing floor back with a block from the backpack first, as a
     * player would. Stair cells hold nothing but air and step floors, so this only ever restores the stairs.
     */
    private static boolean climbStairs(ServerLevel level, FactionNpc npc, BlockPos p) {
        com.factionclash.campaign.WorkplaceState wp = null;
        for (com.factionclash.campaign.WorkplaceState w : CampaignData.get(level.getServer()).workplaces.values()) {
            if (w.type == com.factionclash.campaign.WorkplaceState.Type.MINE && com.factionclash.structure.MineLayout.inStairwell(w, p)) {
                wp = w;
                break;
            }
        }
        if (wp == null) {
            return false;
        }
        int k = wp.origin.getY() - p.getY();
        BlockPos here = com.factionclash.structure.MineLayout.stand(wp, k);
        // on the step for this height: one up the spiral; beside it (the other cells of the stairwell): onto it
        BlockPos next = here.equals(p) ? com.factionclash.structure.MineLayout.stand(wp, k - 1) : here;
        if (Math.abs(next.getX() - p.getX()) > 1 || Math.abs(next.getZ() - p.getZ()) > 1 || next.getY() - p.getY() > 1) {
            return false;
        }
        BlockPos floor = next.below();
        BlockState fs = level.getBlockState(floor);
        if (!fs.isFaceSturdy(level, floor, Direction.UP)) {
            int slot = blockSlot(level, npc);
            if (!fs.canBeReplaced() || !fs.getFluidState().isEmpty() || slot < 0) {
                return false;
            }
            ItemStack block = npc.inventory().getItem(slot);
            BlockState place = ((net.minecraft.world.item.BlockItem) block.getItem()).getBlock().defaultBlockState();
            level.setBlock(floor, place, Block.UPDATE_ALL);
            level.playSound(null, floor, place.getSoundType().getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            block.shrink(1);
            npc.inventory().setChanged();
        }
        for (BlockPos c : List.of(next, next.above(), p.above(2))) {
            BlockState s = level.getBlockState(c);
            if (!s.isAir() && (s.getDestroySpeed(level, c) < 0 || s.hasBlockEntity() || !s.getFluidState().isEmpty())) {
                return false;
            }
            if (!s.isAir() && !s.getCollisionShape(level, c).isEmpty()) {
                dig(level, npc, c); // rubble in the stairwell (a cave-in, gravel): the stairs are open space
            }
        }
        if (!NpcNavigator.isSafeStandingSpot(level, npc, next)) {
            return false;
        }
        stepTo(npc, p, next);
        return true;
    }

    /** Backpack slot of a block to build with: stone or earth first, else any full solid block. */
    private static int blockSlot(ServerLevel level, FactionNpc npc) {
        for (int i = 0; i < npc.inventory().getContainerSize(); i++) {
            if (BRIDGE.contains(npc.inventory().getItem(i).getItem())) {
                return i;
            }
        }
        for (int i = 0; i < npc.inventory().getContainerSize(); i++) {
            if (solidBlock(level, npc.inventory().getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    /** Blocks an NPC may build a bridge or a step with: plain stone and earth it dug up itself. */
    private static final java.util.Set<net.minecraft.world.item.Item> BRIDGE = java.util.Set.of(
            net.minecraft.world.item.Items.COBBLESTONE, net.minecraft.world.item.Items.COBBLED_DEEPSLATE,
            net.minecraft.world.item.Items.STONE, net.minecraft.world.item.Items.DIRT, net.minecraft.world.item.Items.ANDESITE,
            net.minecraft.world.item.Items.DIORITE, net.minecraft.world.item.Items.GRANITE, net.minecraft.world.item.Items.TUFF,
            net.minecraft.world.item.Items.DEEPSLATE, net.minecraft.world.item.Items.NETHERRACK, net.minecraft.world.item.Items.COARSE_DIRT);

    /**
     * A gap toward the destination (a ravine between the NPC and home, a drop off a ledge): like a player, it places
     * a block from its backpack where the floor is missing and steps onto it, one block higher each step when the
     * destination is above. Only where digging cannot help, and never inside protected ground.
     */
    private static boolean bridge(ServerLevel level, FactionNpc npc, BlockPos p, Direction d, boolean climb) {
        int slot = blockSlot(level, npc); // stone or dirt, else any solid block it carries (logs, planks...)
        if (slot < 0) {
            return false;
        }
        for (int dy : climb ? new int[]{1, 0} : new int[]{0}) {
            BlockPos q = p.relative(d).above(dy);
            BlockPos floor = q.below();
            BlockState fs = level.getBlockState(floor);
            if (npc.navigator().isDeadEnd(q)) {
                continue;
            }
            if (fs.isFaceSturdy(level, floor, Direction.UP) || !fs.canBeReplaced() || isProtected(level, floor)) {
                continue; // solid ground ahead is the digging loop's business
            }
            List<BlockPos> clear = new ArrayList<>(List.of(q, q.above()));
            if (dy == 1) {
                clear.add(p.above(2));
            }
            if (!allDiggable(level, clear)) {
                continue;
            }
            ItemStack block = npc.inventory().getItem(slot);
            BlockState place = ((net.minecraft.world.item.BlockItem) block.getItem()).getBlock().defaultBlockState();
            level.setBlock(floor, place, Block.UPDATE_ALL);
            level.playSound(null, floor, place.getSoundType().getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            block.shrink(1);
            npc.inventory().setChanged();
            for (BlockPos c : clear) {
                dig(level, npc, c);
            }
            if (NpcNavigator.isSafeStandingSpot(level, npc, q)) {
                stepTo(npc, p, q);
                return true;
            }
        }
        return false;
    }

    /** A full, solid, non-falling block with no block entity (not leaves, saplings, sand or chests). */
    private static boolean solidBlock(ServerLevel level, ItemStack s) {
        if (!(s.getItem() instanceof net.minecraft.world.item.BlockItem bi)) {
            return false;
        }
        BlockState st = bi.getBlock().defaultBlockState();
        return !(bi.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) && !st.hasBlockEntity()
                && !st.is(BlockTags.LEAVES) && st.isCollisionShapeFullBlock(level, BlockPos.ZERO);
    }

    /** The settlement whose protected floor band lies right above this underground spot, if any. */
    private static BaseState settlementOver(ServerLevel level, BlockPos p) {
        for (BaseState b : CampaignData.get(level.getServer()).bases.values()) {
            int half = com.factionclash.structure.BaseLayout.HALF + 2;
            if (!b.destroyed && b.isSettlement() && !b.planIndex().isEmpty() && Math.abs(p.getX() - b.center.getX()) <= half
                    && Math.abs(p.getZ() - b.center.getZ()) <= half && p.getY() < b.center.getY() - 1 && p.getY() >= b.center.getY() - 12) {
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
        ItemStack tool = npc.getMainHandItem();
        if (com.factionclash.config.FCConfig.TOOL_WEAR.get() && !tool.isEmpty()) {
            tool.getItem().mineBlock(tool, level, s, c, npc);
        }
        npc.exhaust(0.005F);
        level.setBlock(c, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.levelEvent(2001, c, Block.getId(s));
    }

    private static boolean allDiggable(ServerLevel level, List<BlockPos> cells) {
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
            if (isProtected(level, c)) {
                return false;
            }
        }
        return true;
    }

    /** Faction structures, a settlement's walls and floor band, and every mine's spiral stairs. */
    private static boolean isProtected(ServerLevel level, BlockPos c) {
        CampaignData data = CampaignData.get(level.getServer());
        for (com.factionclash.campaign.WorkplaceState w : data.workplaces.values()) {
            if (w.type == com.factionclash.campaign.WorkplaceState.Type.MINE && com.factionclash.structure.MineLayout.nearStairs(w, c)) {
                return true; // a mine's spiral stairs and the rock around them: its floors hold the stairs up
            }
        }
        for (BaseState b : data.bases.values()) {
            if (b.destroyed) {
                continue;
            }
            if (b.planIndex().containsKey(c.asLong())) {
                return true; // faction structures are never dug
            }
            int half = (b.isSettlement() ? com.factionclash.structure.BaseLayout.HALF : com.factionclash.structure.LairLayout.HALF) + 1;
            if (!b.planIndex().isEmpty() && Math.abs(c.getX() - b.center.getX()) <= half && Math.abs(c.getZ() - b.center.getZ()) <= half
                    && c.getY() >= b.center.getY() - 1 && c.getY() <= b.center.getY() + 8) {
                // never through a settlement's walls or floor; the rock under the floor is fine (the floor itself and
                // the walls are blueprint blocks and stay), so nobody is trapped just beneath a base
                return true;
            }
        }
        return false;
    }
}
