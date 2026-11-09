package com.factionclash.combat;

import com.factionclash.campaign.BaseState;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Noncombatants below 50% health run to safety and alert guards (spec 14). Combatants never use this, nor does a
 * worker whose job looks after its own safety (a scout away from home steps aside or retreats by itself).
 */
public class CivilianFleeGoal extends Goal {
    private final FactionNpc npc;
    private LivingEntity threat;
    private int alertCooldown;

    public CivilianFleeGoal(FactionNpc npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    private LivingEntity currentThreat() {
        LivingEntity t = npc.getLastHurtByMob();
        if (t != null && t.isAlive() && npc.tickCount - npc.getLastHurtByMobTimestamp() < 200 && !CombatRules.sameSide(npc, t)) {
            return t;
        }
        return null;
    }

    /** A job that looks after its own safety (a scout away from home) is left to it. */
    private boolean jobDecides() {
        return npc.job() != null && npc.job().handlesOwnSafety();
    }

    @Override
    public boolean canUse() {
        if (!npc.getRole().isCivilian() || npc.getHealth() >= npc.getMaxHealth() * 0.5F || jobDecides()) {
            return false;
        }
        threat = currentThreat();
        return threat != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (npc.getHealth() >= npc.getMaxHealth() * 0.5F || jobDecides()) {
            return false;
        }
        LivingEntity t = currentThreat();
        if (t == null) {
            return !npc.getNavigation().isDone();
        }
        threat = t;
        return true;
    }

    @Override
    public void start() {
        npc.setTarget(null);
        npc.setStatus("Fleeing to safety (wounded)", "Fleeing");
        flee();
    }

    @Override
    public void tick() {
        if (--alertCooldown <= 0) {
            alertCooldown = 40;
            Alerts.callGuards(npc, threat, 32);
            if (npc.getNavigation().isDone()) {
                flee();
            }
        }
    }

    private void flee() {
        BaseState b = npc.base();
        BlockPos safe = b == null ? null : b.point(BaseState.STATION_STAND);
        if (safe != null && safe.distSqr(npc.blockPosition()) > 9) {
            npc.getNavigation().moveTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, 1.3);
            return;
        }
        if (threat != null) {
            Vec3 away = DefaultRandomPos.getPosAway(npc, 12, 6, threat.position());
            if (away != null) {
                npc.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
            }
        }
    }

    @Override
    public void stop() {
        threat = null;
    }
}
