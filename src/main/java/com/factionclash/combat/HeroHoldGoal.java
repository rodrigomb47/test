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
        // also while its mobility owns its movement (a leap, a climb, a glide, flight, a downing: task A10), unless its
        // own navigation started the action (a link it follows, a travel flight: task A11), which then waits for it
        // and not while its fight mobility owns it (an aerial window, a reach flight, a dive: task A12): its moves start
        // in the air then, and its basic-attack goals wait on their own hook; nor while it escapes in the air (7.6): the
        // escape goal takes the flight over from there (A12)
        return !hero.level().isClientSide && (hero.combat().boss().held()
                || hero.movement().controls() && !hero.movement().navOwned() && !hero.combat().air().ownsMovement()
                && !(hero.movement().flying() && hero.combat().boss().escape().running()));
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
