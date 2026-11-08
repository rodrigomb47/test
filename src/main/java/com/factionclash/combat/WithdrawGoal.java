package com.factionclash.combat;

import com.factionclash.campaign.BaseState;
import com.factionclash.npc.FactionNpc;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A badly wounded soldier with no golden apple to turn the fight falls back, as a player would: away from its
 * attacker toward home, eating to heal (healing needs a fed stomach), and back into the fight once it is over 60%.
 */
public class WithdrawGoal extends Goal {
    /** PROPOSED: fall back under 30% health, return over 60%, give up withdrawing after a minute. */
    static final float LEAVE = 0.3F, RETURN = 0.6F;
    static final int MAX_TICKS = 1200;
    private final FactionNpc npc;
    private int ticks;

    public WithdrawGoal(FactionNpc npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = npc.getTarget();
        return npc.getRole().combatant && t != null && t.isAlive() && npc.getHealth() < npc.getMaxHealth() * LEAVE
                && !npc.eatingGoldenApple() && com.factionclash.work.Inv.count(npc.inventory(), s -> s.is(Items.GOLDEN_APPLE)) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return ticks < MAX_TICKS && npc.getHealth() < npc.getMaxHealth() * RETURN && npc.isAlive();
    }

    @Override
    public void start() {
        ticks = 0;
        npc.setStatus("Wounded - falling back to recover", "Withdrawing");
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks % 20 != 1) {
            return;
        }
        LivingEntity t = npc.getTarget();
        BaseState home = npc.base();
        Vec3 dest = null;
        if (home != null && npc.blockPosition().distSqr(home.center) > 12 * 12) {
            dest = Vec3.atBottomCenterOf(home.point(BaseState.STORAGE_STAND) != null ? home.point(BaseState.STORAGE_STAND) : home.center);
        } else if (t != null) {
            // straight away from the attacker, 10 blocks (a random spot away from it if that way is blocked)
            Vec3 away = npc.position().subtract(t.position()).multiply(1, 0, 1);
            if (away.lengthSqr() > 1.0E-4) {
                dest = npc.position().add(away.normalize().scale(10));
            }
            if (dest == null || npc.getNavigation().createPath(net.minecraft.core.BlockPos.containing(dest), 1) == null) {
                dest = DefaultRandomPos.getPosAway(npc, 12, 5, t.position());
            }
        }
        if (dest != null) {
            npc.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.25);
        }
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        if (npc.getHealth() >= npc.getMaxHealth() * RETURN) {
            npc.setStatus("Recovered - back to the fight", "Fighting");
        }
    }
}
