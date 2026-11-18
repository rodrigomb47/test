package com.factionclash.combat;

import com.factionclash.hero.HeroEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * A fighting hero comes back (MOBILITY_AND_WORK 5.1.15; task A12): after 4 s in a row on ground no engaged enemy can
 * reach (a ledge across a gap, a cliff top, a roof: its links took it there), it walks back toward its nearest engaged
 * enemy with its own navigation (its links included) for up to 5 s, whoever that enemy is. In the air the reach clock
 * lands it instead ({@link com.factionclash.hero.HeroFightMobility}). It holds MOVE at priority 2 beside the move goal,
 * so a move under way plays out first; a stagger, a transition or an escape (priorities 0 and 1) stop it.
 */
public class HeroComeBackGoal extends Goal {
    /** It is back once within this distance of the enemy (a melee reach and a step). */
    static final double BACK = 3.0;
    private final HeroEntity hero;
    private LivingEntity toward;
    private int repath;

    public HeroComeBackGoal(HeroEntity hero) {
        this.hero = hero;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (hero.level().isClientSide || !hero.combat().air().returning() || hero.combat().running()) {
            return false;
        }
        toward = hero.combat().air().nearestEngagedEnemy();
        return toward != null && hero.distanceTo(toward) > BACK;
    }

    @Override
    public boolean canContinueToUse() {
        return hero.combat().air().returning() && toward != null && toward.isAlive() && hero.distanceTo(toward) > BACK;
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        toward = null;
        hero.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (--repath <= 0) {
            repath = 10;
            hero.getNavigation().moveTo(toward, 1.2);
        }
        hero.getLookControl().setLookAt(toward, 30F, 30F);
    }
}
