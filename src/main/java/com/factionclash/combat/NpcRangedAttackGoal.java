package com.factionclash.combat;

import com.factionclash.npc.FactionNpc;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BowItem;

import java.util.EnumSet;

/**
 * Archer attack using one equipped arrow that is never consumed (spec 9). Draw time and shot are tied to the
 * bow's real use animation so the visible shot matches the damage.
 */
public class NpcRangedAttackGoal extends Goal {
    private static final double RANGE = 16.0;
    /** PROPOSED: crossbows outrange bows a little (straighter, faster bolts). */
    private static final double CROSSBOW_RANGE = 20.0;
    private final FactionNpc npc;
    private int seeTime;
    private int cooldown;
    private int retarget;

    /** An enemy that has come within {@code dist} (a melee attacker closing in). */
    private LivingEntity nearestThreat(double dist) {
        LivingEntity best = null;
        double bestD = dist * dist;
        for (LivingEntity e : npc.level().getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(dist))) {
            if (e != npc && e.isAlive() && npc.isEnemy(e) && !(e instanceof FactionNpc o && o.getRole().ranged)
                    && !(e instanceof net.minecraft.world.entity.monster.RangedAttackMob) && npc.distanceToSqr(e) < bestD) {
                bestD = npc.distanceToSqr(e);
                best = e;
            }
        }
        return best;
    }

    /** Focus fire: the visible enemy in range closest to falling (fewest health points) gets the next arrows. */
    private LivingEntity focus(LivingEntity current, double range) {
        LivingEntity best = current;
        float bestHp = current.getHealth();
        for (LivingEntity e : npc.level().getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(range))) {
            if (e != npc && e.isAlive() && npc.isEnemy(e) && e.getHealth() < bestHp - 2.0F && npc.distanceToSqr(e) <= range * range
                    && npc.getSensing().hasLineOfSight(e)) {
                bestHp = e.getHealth();
                best = e;
            }
        }
        if (best != current) {
            npc.setTarget(best);
        }
        return best;
    }

    public NpcRangedAttackGoal(FactionNpc npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean armed() {
        return npc.getRole().ranged && (npc.getMainHandItem().getItem() instanceof BowItem || crossbow()) && !npc.ammo().isEmpty();
    }

    private boolean crossbow() {
        return npc.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem;
    }

    @Override
    public boolean canUse() {
        LivingEntity t = npc.getTarget();
        return armed() && t != null && t.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        npc.setAiming(false);
        npc.stopUsingItem();
        seeTime = 0;
        npc.pursuit().reset();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity t = npc.getTarget();
        if (t == null) {
            return;
        }
        double range = crossbow() ? CROSSBOW_RANGE : RANGE;
        if (++retarget >= 40) {
            retarget = 0;
            t = focus(t, range);
        }
        npc.pursuit().bind(t, true);
        double d2 = npc.distanceToSqr(t);
        boolean sees = npc.getSensing().hasLineOfSight(t);
        seeTime = sees ? seeTime + 1 : 0;
        LivingEntity close = nearestThreat(6.0);
        if (close != null) {
            // kiting: someone got close - back away to shooting distance, like an archer player
            if (npc.getNavigation().isDone() || npc.tickCount % 20 == 0) {
                net.minecraft.world.phys.Vec3 away = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(npc, 10, 4, close.position());
                if (away != null) {
                    npc.getNavigation().moveTo(away.x, away.y, away.z, 1.25);
                } else {
                    npc.getMoveControl().strafe(-0.5F, 0.0F);
                }
            }
        } else if (d2 > range * range || seeTime < 5) {
            // toward it if there is a way; if not, a spot out of its reach (in sight of it), never up to the fence
            npc.pursuit().approachRanged(t);
        } else {
            npc.getNavigation().stop();
        }
        npc.getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (!sees || d2 > range * range) {
            if (npc.isUsingItem()) {
                npc.stopUsingItem();
                npc.setAiming(false);
            }
            return;
        }
        if (!npc.isUsingItem()) {
            npc.startUsingItem(InteractionHand.MAIN_HAND);
            npc.setAiming(true);
        } else if (crossbow() && npc.getTicksUsingItem() >= net.minecraft.world.item.CrossbowItem.getChargeDuration(npc.getMainHandItem(), npc)) {
            // a crossbow is charged (Quick Charge shortens it), then fires at full power
            npc.stopUsingItem();
            npc.setAiming(false);
            npc.performRangedAttack(t, 1.0F);
            cooldown = 20;
        } else if (!crossbow() && npc.getTicksUsingItem() >= 20) {
            int used = npc.getTicksUsingItem();
            npc.stopUsingItem();
            npc.setAiming(false);
            npc.performRangedAttack(t, BowItem.getPowerForTime(used));
            cooldown = 20;
        }
    }
}
