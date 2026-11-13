package com.factionclash.combat;

import com.factionclash.hero.HeroDamage;
import com.factionclash.hero.HeroEffects;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.entity.HeroProjectile;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroRules;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The {@code thrown} basic attack (HERO_FORMAT.md 9; HEROES_V2 3.5 priority 3; task A3): a caster-like hero throws its
 * projectile look at its target every {@code interval} seconds (the reader already held {@code amount / interval} to a
 * bow's 5 a second) for its {@code amount} of its element plus the hero's attack bonuses, within 14 blocks and in sight.
 * It keeps its {@code keep_distance}, backing away like an archer when an enemy comes closer than half of it, and
 * closes in, or finds a spot in sight, when the target is out of range ({@link Pursuit#approachRanged}). A landed throw
 * is a basic hit: 5 energy and the basic attack's {@code on_hit}, as a melee blow or an arrow. The same for every target.
 */
public class HeroThrowGoal extends Goal {
    private final HeroEntity hero;
    private int cooldown;
    private int seeTime;

    public HeroThrowGoal(HeroEntity hero) {
        this.hero = hero;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private HeroDef def() {
        return hero.throwsBasic() ? hero.definition() : null;
    }

    @Override
    public boolean canUse() {
        LivingEntity t = hero.getTarget();
        return t != null && t.isAlive() && def() != null && !hero.combat().running();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        seeTime = 0;
        hero.pursuit().reset();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity t = hero.getTarget();
        HeroDef d = def();
        if (t == null || d == null) {
            return;
        }
        HeroDef.BasicAttack b = d.basicAttack();
        double range = HeroProjectile.THROWN_RANGE;
        hero.pursuit().bind(t, true);
        double dist = hero.distanceTo(t);
        boolean sees = hero.getSensing().hasLineOfSight(t);
        seeTime = sees ? seeTime + 1 : 0;
        double keep = Math.max(0, d.behaviour().keepDistance());
        if (keep > 0 && dist < keep / 2) {
            // an enemy came close: back away to throwing distance, as an archer does
            if (hero.getNavigation().isDone() || hero.tickCount % 20 == 0) {
                Vec3 away = DefaultRandomPos.getPosAway(hero, (int) Math.max(6, keep), 4, t.position());
                if (away != null) {
                    hero.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
                }
            }
        } else if (dist > range || seeTime < 5) {
            hero.pursuit().approachRanged(t);
        } else {
            hero.getNavigation().stop();
        }
        hero.getLookControl().setLookAt(t, 30F, 30F);
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (!sees || dist > range) {
            return;
        }
        double raw = HeroEffects.attackPower(hero, d);
        HeroDef.Element el = b.element() != null ? b.element() : d.element();
        HeroProjectile.launchBasic(hero, b.projectile(), el, raw, t.position().add(0, t.getBbHeight() * 0.6, 0));
        hero.swing(InteractionHand.MAIN_HAND);
        hero.markAttacked();
        cooldown = (int) Math.max(1, HeroRules.ticks(b.interval()));
    }

    /** The damage one throw of this hero deals {@code target} now (for the sheet and the tests). */
    public static double throwDamage(HeroEntity hero, LivingEntity target) {
        HeroDef d = hero.definition();
        if (d == null) {
            return 0;
        }
        HeroDef.Element el = d.basicAttack().element() != null ? d.basicAttack().element() : d.element();
        return HeroRules.dealt(HeroEffects.attackPower(hero, d), HeroDamage.factor(hero, target, el), HeroDamage.maxHit());
    }
}
