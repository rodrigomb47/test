package com.factionclash.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.Set;

/**
 * The faction NPC's ground navigation. While it is fighting (it has a living target) a closed fence gate counts as a
 * wooden door it can open, as it is for a player chasing someone into an animal pen; {@link
 * com.factionclash.combat.OpenGateGoal} opens the gate when the path reaches it and closes it behind. At work, a
 * closed gate stays a fence, so workers never walk through a pen (only the animal carer and butcher go in, through
 * their own gate handling). It also counts its path searches, so tests can check that a fight keeps them few.
 */
public class NpcPathNavigation extends GroundPathNavigation {
    /** Path searches run so far (not the cached path handed back again). */
    public int searches;

    public NpcPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        GateEvaluator e = new GateEvaluator();
        e.setCanPassDoors(true);
        this.nodeEvaluator = e;
        return new PathFinder(e, maxVisitedNodes) {
            @Override
            public Path findPath(PathNavigationRegion region, Mob mob, Set<BlockPos> targets, float maxRange, int accuracy, float searchDepthMultiplier) {
                LivingEntity t = mob.getTarget();
                e.gates = t != null && t.isAlive();
                searches++;
                return super.findPath(region, mob, targets, maxRange, accuracy, searchDepthMultiplier);
            }
        };
    }

    /** Whether paths searched now may lead through closed fence gates. */
    public boolean gatesOpen() {
        return nodeEvaluator instanceof GateEvaluator g && g.gates;
    }

    /** Vanilla walking, with closed fence gates as wooden doors while {@link #gates} is set. */
    static final class GateEvaluator extends WalkNodeEvaluator {
        boolean gates;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        @Override
        public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            if (gates) {
                BlockState s = context.level().getBlockState(cursor.set(x, y, z));
                if (s.getBlock() instanceof FenceGateBlock && !s.getValue(FenceGateBlock.OPEN)) {
                    return PathType.DOOR_WOOD_CLOSED; // passable as WALKABLE_DOOR for a mob that opens doors
                }
            }
            return super.getPathType(context, x, y, z);
        }
    }
}
