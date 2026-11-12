package com.factionclash.combat;

import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

/**
 * A fighting NPC whose path leads through a closed fence gate opens it, steps through and closes it behind itself,
 * as a player chasing someone into an animal pen does (and as the animal carer does), so the animals stay in. It
 * never shuts a gate on anyone standing in it, and after closing one it does not open a gate again for a second, so
 * a target moving along the fence cannot make it flap the gate open and shut. Runs beside the other goals (no
 * flags), like vanilla's door opening.
 */
public class OpenGateGoal extends Goal {
    /** PROPOSED: ticks after closing a gate before this NPC opens one again. */
    static final int REOPEN_COOLDOWN = 20;
    /** PROPOSED: a gate is shut again at the latest this long after it was opened (if nobody stands in it). */
    static final int MAX_OPEN = 80;
    private final FactionNpc npc;
    private BlockPos gate;
    private int ticks;
    private boolean entered;
    private float dirX, dirZ;
    private int cooldown;
    /** Gates this NPC has opened and closed, for tests. */
    public int cycles;

    public OpenGateGoal(FactionNpc npc) {
        this.npc = npc;
    }

    private boolean fighting() {
        LivingEntity t = npc.getTarget();
        return t != null && t.isAlive();
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!fighting()) {
            return false;
        }
        Path path = npc.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        int end = Math.min(path.getNextNodeIndex() + 2, path.getNodeCount());
        for (int i = Math.max(0, path.getNextNodeIndex() - 1); i < end; i++) {
            Node n = path.getNode(i);
            BlockPos p = new BlockPos(n.x, n.y, n.z);
            if (npc.distanceToSqr(p.getX() + 0.5, npc.getY(), p.getZ() + 0.5) <= 2.25 * 2.25 && closedGate(p)) {
                gate = p;
                return true;
            }
        }
        return false;
    }

    private boolean closedGate(BlockPos p) {
        BlockState s = npc.level().getBlockState(p);
        return s.getBlock() instanceof FenceGateBlock && !s.getValue(FenceGateBlock.OPEN);
    }

    @Override
    public void start() {
        ticks = 0;
        entered = false;
        dirX = (float) (gate.getX() + 0.5 - npc.getX());
        dirZ = (float) (gate.getZ() + 0.5 - npc.getZ());
        set(true);
    }

    @Override
    public boolean canContinueToUse() {
        return gate != null && npc.level().getBlockState(gate).getBlock() instanceof FenceGateBlock
                && npc.level().getBlockState(gate).getValue(FenceGateBlock.OPEN);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ticks++;
        AABB cell = new AABB(gate);
        if (npc.getBoundingBox().intersects(cell)) {
            entered = true;
            return;
        }
        // past it: on the far side of the gate from where it opened it (or it turned away, or too long)
        float fx = (float) (gate.getX() + 0.5 - npc.getX()), fz = (float) (gate.getZ() + 0.5 - npc.getZ());
        boolean passed = entered && dirX * fx + dirZ * fz < 0;
        boolean away = npc.distanceToSqr(gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5) > 3.0 * 3.0;
        if ((passed || away || ticks > MAX_OPEN) && !occupied(cell)) {
            set(false);
            gate = null;
            cooldown = REOPEN_COOLDOWN;
            cycles++;
        }
    }

    /** Someone (an ally following through, an animal) stands in the gateway: never shut it on them. */
    private boolean occupied(AABB cell) {
        return !npc.level().getEntitiesOfClass(LivingEntity.class, cell, LivingEntity::isAlive).isEmpty();
    }

    @Override
    public void stop() {
        // the fight ended or the goal was cut short with the gate still open: shut it if nobody is in the way
        if (gate != null && canContinueToUse() && !occupied(new AABB(gate))) {
            set(false);
            cycles++;
        }
        gate = null;
    }

    private void set(boolean open) {
        BlockState s = npc.level().getBlockState(gate);
        if (!(s.getBlock() instanceof FenceGateBlock) || s.getValue(FenceGateBlock.OPEN) == open) {
            return;
        }
        if (open) {
            // a gate opens away from the one opening it, as when a player uses it
            s = s.setValue(FenceGateBlock.FACING, facingAway(s));
        }
        npc.level().setBlock(gate, s.setValue(FenceGateBlock.OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
        npc.level().playSound(null, gate, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F,
                npc.level().random.nextFloat() * 0.1F + 0.9F);
        npc.level().gameEvent(npc, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, gate);
        npc.swing(InteractionHand.MAIN_HAND);
    }

    private net.minecraft.core.Direction facingAway(BlockState s) {
        net.minecraft.core.Direction f = s.getValue(FenceGateBlock.FACING);
        net.minecraft.core.Direction look = net.minecraft.core.Direction.fromYRot(npc.getYRot());
        return f == look.getOpposite() ? look : f;
    }
}
