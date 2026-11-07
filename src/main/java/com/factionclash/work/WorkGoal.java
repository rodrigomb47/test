package com.factionclash.work;

import com.factionclash.npc.FactionNpc;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Runs the NPC's job whenever it is not fighting or fleeing. */
public class WorkGoal extends Goal {
    private final FactionNpc npc;

    public WorkGoal(FactionNpc npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    private boolean free() {
        Job job = npc.job();
        if (job == null) {
            return false;
        }
        if (npc.getTarget() != null && npc.getTarget().isAlive()) {
            if (job.ignoresCombat()) {
                npc.setTarget(null);
                return true;
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean canUse() {
        return free();
    }

    @Override
    public boolean canContinueToUse() {
        return free();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Job job = npc.job();
        if (job != null) {
            job.tick();
        }
    }

    @Override
    public void stop() {
        Job job = npc.job();
        if (job != null) {
            job.onPause();
        }
    }
}
