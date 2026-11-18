package com.factionclash.combat;

import com.factionclash.hero.HeroEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * A hero's raid layer (MOBILITY_AND_WORK 5.2; task A12, {@link com.factionclash.hero.HeroRaid}): while the hero is in
 * its faction's raid party it musters, chooses its way over the target's walls (only with a way out) and its goal
 * inside, and goes after it, whatever its duty. Priority 4, MOVE: its fights (priorities 2-3), an escape and a stagger
 * come first, and its job (5) waits.
 */
public class HeroRaidGoal extends Goal {
    private final HeroEntity hero;
    private int nextCheck;

    public HeroRaidGoal(HeroEntity hero) {
        this.hero = hero;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // (the goal selector asks every other tick, on the hero's parity: a fixed modulo could never meet it)
        if (hero.level().isClientSide || hero.tickCount < nextCheck) {
            return false;
        }
        nextCheck = hero.tickCount + 10;
        return hero.raid().active() && !fighting();
    }

    private boolean fighting() {
        return hero.getTarget() != null && hero.getTarget().isAlive() || hero.combat().running();
    }

    @Override
    public boolean canContinueToUse() {
        return hero.raid().active() && !fighting();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        hero.raidTick();
    }

    @Override
    public void stop() {
        hero.getNavigation().stop();
    }
}
