package com.factionclash.combat;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.hero.HeroEntity;
import com.factionclash.logic.hero.FightMobility;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.npc.FactionNpc;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;

import java.util.EnumSet;

/**
 * Archers first at an airborne hero (MOBILITY_AND_WORK 5.2; task A12): a ranged soldier (an archer or a crossbowman)
 * takes an enemy hero in the air (its {@code DATA_LOCO} mode airborne, gliding or flying) within its bow's range and in
 * sight over any other target, then an enemy hero on its base's walls or inside them, then the rest (its other target
 * goals). It keeps such a hero at least 3 s, so it does not switch as a flyer lands and takes off. Each arrow adds air
 * poise (5 arrows down a flyer). Ordinary NPCs get no new movement from this: only a target. Heroes choose their own
 * targets and never use it.
 */
public class AirborneHeroTargetGoal extends TargetGoal {
    /** A bow's reach for this goal (blocks). */
    static final double RANGE = 24;
    /** How often it looks (ticks). */
    static final int LOOK_TICKS = 10;
    private final FactionNpc npc;
    private HeroEntity found;
    private int nextLook;
    private int keepUntil;
    /** How many times it took an airborne hero (tests). */
    public int taken;

    public AirborneHeroTargetGoal(FactionNpc npc) {
        super(npc, false);
        this.npc = npc;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    private int priority(LivingEntity e) {
        if (!(e instanceof HeroEntity h) || !h.isAlive()) {
            return 0;
        }
        boolean airborne = FightMobility.holdUnder(true, HeroActions.locoMode(h.loco()));
        BaseState b = npc.base();
        boolean intruding = b != null && !b.destroyed && BaseZones.insideWalls(b, h.blockPosition(), 1);
        return FightMobility.archerPriority(true, airborne, intruding);
    }

    @Override
    public boolean canUse() {
        if (npc.tickCount < nextLook || npc instanceof HeroEntity || !npc.rangedFighter() || npc.jobIgnoresCombat()) {
            return false;
        }
        nextLook = npc.tickCount + LOOK_TICKS;
        HeroEntity best = null;
        int bestP = 0;
        double bestD = Double.MAX_VALUE;
        for (HeroEntity h : npc.level().getEntitiesOfClass(HeroEntity.class, npc.getBoundingBox().inflate(RANGE),
                h -> h.isAlive() && !h.isPreview() && npc.isEnemy(h) && !npc.pursuit().ignores(h))) {
            int p = priority(h);
            double d = npc.distanceToSqr(h);
            if (p == 0 || d > RANGE * RANGE || !npc.getSensing().hasLineOfSight(h)) {
                continue;
            }
            if (p > bestP || p == bestP && d < bestD) {
                best = h;
                bestP = p;
                bestD = d;
            }
        }
        LivingEntity cur = npc.getTarget();
        if (best == null || best == cur || cur != null && cur.isAlive() && priority(cur) >= bestP) {
            return false;
        }
        found = best;
        return true;
    }

    @Override
    public void start() {
        npc.setTarget(found);
        targetMob = found;
        keepUntil = npc.tickCount + FightMobility.AIRBORNE_TARGET_HOLD;
        taken++;
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = npc.getTarget();
        if (t == null || !t.isAlive() || t != found) {
            return false;
        }
        return npc.tickCount < keepUntil || priority(t) > 0 && npc.distanceToSqr(t) <= RANGE * RANGE;
    }

    /** It keeps its target when it stops (a landed hero may still be the one to shoot); the other goals choose next. */
    @Override
    public void stop() {
        found = null;
        targetMob = null;
    }
}
