package com.factionclash.combat;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.Diplomacy;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.logic.ReachPlanner;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcNavigator;
import com.factionclash.structure.MineLayout;
import com.factionclash.work.BlockWork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What a fighter may do to the world to reach its target, for {@link ReachPlanner}: which blocks it may break and
 * which cells it may build in. Never a block of any faction's plan (its walls, doors, fences, houses: a broken door
 * is put back by the builders), never a container or anything else with a block entity, never a block holding or
 * touching water or lava, never a mine's stairs, never the ground of a base (its yard and the rock under it); in a
 * base of its own side only what a hostile player put there; and only what it can break with what it holds within
 * {@link #MAX_BREAK_TICKS}.
 */
public final class Reach {
    /** PROPOSED: a block that takes longer than this (5 s) to break with the fighter's weapon is left alone. */
    public static final int MAX_BREAK_TICKS = 100;
    /** Below this height a block's top is stepped over like a carpet or a pressure plate. */
    private static final double LOW = 0.1875;

    private Reach() {
    }

    /**
     * True if {@code npc} may break the block at {@code pos} in a fight. The planned block of a base never; another
     * block standing in a planned cell (a raider's dirt in a doorway) yes, unless a player of the base's own faction
     * put it there (a deliberate edit) and the fighter is of that faction. Unplanned blocks in the walls box of a base
     * of its own side (its own or an ally's) only when a hostile player placed them; the ground of any base (from its
     * floor down) never.
     */
    public static boolean mayBreak(ServerLevel level, BlockPos pos, FactionNpc npc) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || s.getDestroySpeed(level, pos) < 0 || s.hasBlockEntity() || !s.getFluidState().isEmpty()) {
            return false;
        }
        for (Direction d : Direction.values()) {
            if (!level.getFluidState(pos.relative(d)).isEmpty()) {
                return false; // never let water or lava in
            }
        }
        CampaignData data = CampaignData.get(level.getServer());
        long key = pos.asLong();
        for (BaseState b : data.bases.values()) {
            if (b.destroyed) {
                continue;
            }
            BlockState planned = b.planIndex().get(key);
            if (planned != null) {
                if (planned.getBlock() == s.getBlock()) {
                    return false; // a faction's own building
                }
                if (b.faction == npc.getFaction() && b.playerEdits.contains(key)) {
                    return false; // what its own faction's player chose to put there
                }
                continue;
            }
            if (b.planIndex().isEmpty() || !BaseZones.insideWalls(b, pos, 1)) {
                continue;
            }
            if (pos.getY() < b.center.getY()) {
                return false; // the base's ground: its yard floor and the rock under it
            }
            boolean ownSide = !Diplomacy.hostile(level.getServer(), npc.getFaction(), b.faction);
            if (ownSide && !b.intruderBlocks.contains(key)) {
                return false; // its own side's base: only what an enemy built there
            }
        }
        for (WorkplaceState w : data.workplaces.values()) {
            if (w.type == WorkplaceState.Type.MINE && w.origin != null && Math.abs(pos.getX() - w.origin.getX()) <= 6
                    && Math.abs(pos.getZ() - w.origin.getZ()) <= 6 && MineLayout.nearStairs(w, pos)) {
                return false;
            }
        }
        return true;
    }

    /** A cell that can be walked through: nothing solid in it (or only something as low as a carpet), a wooden door or a gate. */
    static boolean passable(ServerLevel level, BlockPos p, BlockState s) {
        if (s.getBlock() instanceof DoorBlock d) {
            return d.type().canOpenByHand();
        }
        if (s.getBlock() instanceof FenceGateBlock) {
            return true;
        }
        VoxelShape shape = s.getCollisionShape(level, p);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= LOW;
    }

    /** The planner's view of the world for this fighter. */
    public static ReachPlanner.World world(ServerLevel level, FactionNpc npc) {
        return new ReachPlanner.World() {
            private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

            @Override
            public int cell(int x, int y, int z) {
                BlockPos p = m.set(x, y, z).immutable();
                if (!level.isLoaded(p)) {
                    return ReachPlanner.FIXED;
                }
                BlockState s = level.getBlockState(p);
                if (!s.getFluidState().isEmpty() || NpcNavigator.isDangerous(s)) {
                    return ReachPlanner.FIXED;
                }
                if (passable(level, p, s)) {
                    return s.getBlock() instanceof DoorBlock d && !d.type().canOpenByHand() ? ReachPlanner.FIXED : ReachPlanner.OPEN;
                }
                if (!mayBreak(level, p, npc)) {
                    return ReachPlanner.FIXED;
                }
                int t = BlockWork.ticksToBreak(npc, s, p, npc.getMainHandItem());
                return t > MAX_BREAK_TICKS ? ReachPlanner.FIXED : Math.max(1, t);
            }

            @Override
            public boolean floor(int x, int y, int z) {
                BlockPos p = m.set(x, y, z).immutable();
                if (!level.isLoaded(p)) {
                    return false;
                }
                BlockState s = level.getBlockState(p);
                if (!s.getFluidState().isEmpty() || NpcNavigator.isDangerous(s) || s.getBlock() instanceof DoorBlock
                        || s.getBlock() instanceof FenceGateBlock) {
                    return false;
                }
                VoxelShape shape = s.getCollisionShape(level, p);
                if (shape.isEmpty()) {
                    return false;
                }
                double top = shape.max(Direction.Axis.Y);
                return top >= 0.5 && top <= 1.0; // no fence or wall tops: nobody steps up onto those
            }

            @Override
            public boolean placeable(int x, int y, int z) {
                return mayPlace(level, m.set(x, y, z).immutable(), npc);
            }
        };
    }

    /**
     * Whether the fighter may build itself up with a block at {@code pos}: an empty cell (air, grass) outside every
     * base's plan, and not in a base of its own side (it would leave a pillar in its own yard).
     */
    public static boolean mayPlace(ServerLevel level, BlockPos pos, FactionNpc npc) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState s = level.getBlockState(pos);
        if (!s.canBeReplaced() || !s.getFluidState().isEmpty() || s.getBlock() instanceof DoorBlock) {
            return false;
        }
        CampaignData data = CampaignData.get(level.getServer());
        for (BaseState b : data.bases.values()) {
            if (b.destroyed) {
                continue;
            }
            if (b.planIndex().containsKey(pos.asLong())) {
                return false;
            }
            if (BaseZones.insideWalls(b, pos, 1) && !Diplomacy.hostile(level.getServer(), npc.getFaction(), b.faction)) {
                return false;
            }
        }
        return true;
    }

    /** A block broken in a fight is no longer an intruder's block anywhere. */
    static void forget(ServerLevel level, BlockPos pos) {
        CampaignData data = CampaignData.get(level.getServer());
        for (BaseState b : data.bases.values()) {
            if (b.intruderBlocks.remove(pos.asLong())) {
                data.setDirty();
            }
        }
    }
}
