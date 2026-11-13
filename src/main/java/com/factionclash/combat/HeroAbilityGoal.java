package com.factionclash.combat;

import com.factionclash.hero.HeroCombat;
import com.factionclash.hero.HeroEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * A hero's moves (HEROES_V2 3.5 priority 2, 6.3; HERO_FORMAT.md 10.4; task A3): while the hero fights and runs no
 * move, every 5 ticks it asks its {@link com.factionclash.logic.hero.MoveSelector} for a move against its current
 * target, whoever that is (a player, an NPC, a hero or a monster), and starts it; the hero's own tick advances it
 * through the wind-up, the hits and the recovery while the goal holds MOVE and LOOK. Between moves it holds nothing,
 * so the basic attack goals (priority 3) space and strike.
 * A stopped goal interrupts a move still in its wind-up (half the cooldown back, v1 10.3).
 */
public class HeroAbilityGoal extends Goal {
    private final HeroEntity hero;

    public HeroAbilityGoal(HeroEntity hero) {
        this.hero = hero;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private HeroCombat combat() {
        return hero.combat();
    }

    @Override
    public boolean canUse() {
        return !hero.level().isClientSide && (combat().tryPick() || combat().running());
    }

    @Override
    public boolean canContinueToUse() {
        return combat().running();
    }

    @Override
    public boolean isInterruptable() {
        return true;
    }

    @Override
    public void start() {
        combat().startPicked();
    }

    @Override
    public void stop() {
        combat().stopRun();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** The move itself advances in the hero's own tick ({@link HeroCombat}); the goal keeps the hero in place. */
    @Override
    public void tick() {
        hero.getNavigation().stop();
    }
}
