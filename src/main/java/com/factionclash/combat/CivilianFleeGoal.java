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
        if (!npc.getRole().isCivilian() || npc.fightsLikeSoldier() || npc.getHealth() >= npc.getMaxHealth() * 0.5F || jobDecides()) {
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
                if (npc.getNavigation().isDone() && threat != null && npc.distanceTo(threat) < UNBOX_RANGE) {
                    // no way away and the threat still beside it (live check: workers that had jumped into a 1x1 pit two
                    // deep after a player who hit them stood there, wounded, until killed): the stuck recovery, the one
                    // hop player-like NPCs allow themselves, toward safety
                    npc.navigator().recoverToward(safeGoal());
                }
            }
        }
    }

    /** PROPOSED: a fleeing worker with no way away from a threat this close uses the stuck recovery. */
    static final double UNBOX_RANGE = 4.0;

    /** Where it flees to: the station stand, or a spot 8 blocks away from the threat. */
    private Vec3 safeGoal() {
        BaseState b = npc.base();
        BlockPos safe = b == null ? null : b.point(BaseState.STATION_STAND);
        if (safe != null) {
            return Vec3.atBottomCenterOf(safe);
        }
        double dx = npc.getX() - threat.getX(), dz = npc.getZ() - threat.getZ(), len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.5) { // (in the same spot as the threat: the way it faces)
            dx = -Math.sin(npc.getYRot() * Math.PI / 180);
            dz = Math.cos(npc.getYRot() * Math.PI / 180);
            len = 1;
        }
        return new Vec3(npc.getX() + dx / len * 8, npc.getY(), npc.getZ() + dz / len * 8);
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
