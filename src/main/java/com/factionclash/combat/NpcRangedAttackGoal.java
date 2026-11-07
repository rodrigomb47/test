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
        double d2 = npc.distanceToSqr(t);
        double range = crossbow() ? CROSSBOW_RANGE : RANGE;
        boolean sees = npc.getSensing().hasLineOfSight(t);
        seeTime = sees ? seeTime + 1 : 0;
        if (d2 > range * range || seeTime < 5) {
            npc.getNavigation().moveTo(t, 1.0);
        } else if (d2 < 16) {
            // too close: step back while keeping aim
            npc.getMoveControl().strafe(-0.5F, 0.0F);
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
