package com.factionclash.combat;

import com.factionclash.hero.HeroStatus;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * A stunned mob stands still (HERO_FORMAT.md 13.4 and HEROES_V2 6.15; task A4): while its {@link HeroStatus} says it is
 * stunned, this goal holds MOVE and LOOK at priority 0, so every attack, move and walking goal below it is stopped (a
 * hero's running move included: its wind-up is interrupted). It is added to a goal-based mob the first time anything
 * stuns it, and again when a mob that carries a stun is loaded. A player is stopped by the same status in other ways
 * ({@link HeroStatus#holdStun}): the same seconds for everyone.
 */
public class StunnedGoal extends Goal {
    private final Mob mob;

    public StunnedGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return HeroStatus.stunned(mob);
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
        mob.getNavigation().stop();
        mob.setAggressive(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        mob.getNavigation().stop();
    }

    /** Adds the goal to a goal-based mob that lacks it (brain-based mobs have no goals; the status holds them). */
    public static void ensure(Mob mob) {
        if (mob.level().isClientSide) {
            return;
        }
        for (var g : mob.goalSelector.getAvailableGoals()) {
            if (g.getGoal() instanceof StunnedGoal) {
                return;
            }
        }
        mob.goalSelector.addGoal(0, new StunnedGoal(mob));
    }
}
