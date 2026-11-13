package com.factionclash.combat;

import com.factionclash.hero.HeroEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * A hero held still by its own boss rules (HEROES_V2 6.7 and 6.9; task A6a): the 2.5 s kneel of a stagger and a phase
 * transition. While {@link com.factionclash.hero.HeroBoss#held} says so this goal holds MOVE, LOOK and JUMP at
 * priority 0, so its walking, its basic attacks and its moves wait (a move is never started inside either; the
 * stagger has already interrupted the one it met). Knock-back still moves it, as any hit moves anyone. The same against
 * every enemy: nothing reads who it fights.
 */
public class HeroHoldGoal extends Goal {
    private final HeroEntity hero;

    public HeroHoldGoal(HeroEntity hero) {
        this.hero = hero;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return !hero.level().isClientSide && hero.combat().boss().held();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public void start() {
        hero.getNavigation().stop();
        hero.setAggressive(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        hero.getNavigation().stop();
    }
}
