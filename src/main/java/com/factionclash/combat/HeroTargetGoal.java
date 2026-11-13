package com.factionclash.combat;

import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroForms;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.TargetRules;
import com.factionclash.npc.FactionNpc;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * A hero's target (HEROES_V2 3.5 "target"; HERO_FORMAT.md 8; task A4): every 2 s (every half second while it has none)
 * it looks for enemies within its aggression's radius (cautious 12, steady 20, bold 28 blocks; 5 while blinded, 6.15)
 * that it can see, and takes the one its {@code focus} and {@code protects} prefer ({@link TargetRules#pick}): first
 * one attacking whom it protects, then the nearest, weakest, strongest, a ranged one or a hero, as its file says. An
 * enemy is anyone its side fights, a player, an NPC, a hero, a monster or an enemy summon, read the same way. There
 * are <b>no attack tokens</b> (owner, 2026-11-13): it never asks how many heroes already press a target, so several
 * heroes may take the same one. A hero with honour 60 or more lets an escaping hero go (5.4). Replaces the soldiers'
 * nearest-target goals for heroes; retaliation ({@link FactionHurtByTargetGoal}) stays above it.
 */
public class HeroTargetGoal extends Goal {
    private final HeroEntity hero;
    private int next;
    private final List<LivingEntity> found = new ArrayList<>();
    private final List<TargetRules.Candidate> candidates = new ArrayList<>();

    public HeroTargetGoal(HeroEntity hero) {
        this.hero = hero;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (hero.level().isClientSide || hero.isPreview() || --next > 0) {
            return false;
        }
        LivingEntity cur = hero.getTarget();
        boolean has = cur != null && cur.isAlive();
        next = has ? TargetRules.RECHECK_TICKS : 10;
        HeroDef d = hero.dormant() ? null : hero.definition();
        int honour = d == null || d.v2() == null || d.v2().personality() == null ? 50
                : d.v2().personality().trait(HeroDef.Trait.HONOUR);
        if (has && escaping(cur) && honour >= TargetRules.SPARES_THE_FLEEING_HONOUR) {
            hero.setTarget(null); // an honourable hero lets a fleeing one go (5.4)
            cur = null;
            has = false;
        }
        double radius = TargetRules.searchRadius(d == null ? null : d.behaviour().aggression());
        if (com.factionclash.hero.HeroControlEvents.blinded(hero)) {
            radius = Math.min(radius, com.factionclash.hero.HeroControlEvents.BLIND_RANGE);
        }
        pick(d, honour, radius, has ? cur : null);
        return false;
    }

    private void pick(HeroDef d, int honour, double radius, LivingEntity cur) {
        found.clear();
        candidates.clear();
        AABB box = hero.getBoundingBox().inflate(radius, 8, radius);
        for (LivingEntity e : hero.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != hero && e.isAlive())) {
            if (!HeroForms.enemy(hero, e) || e instanceof Player p && (p.isCreative() || p.isSpectator())
                    || hero.distanceTo(e) > radius || hero.pursuit().ignores(e)
                    || e != cur && !hero.getSensing().hasLineOfSight(e)) {
                continue;
            }
            found.add(e);
            candidates.add(new TargetRules.Candidate(hero.distanceTo(e), e.getHealth(), e.getMaxHealth(), ranged(e),
                    e instanceof HeroEntity, attackingProtected(d, e), escaping(e), e == cur));
        }
        if (found.isEmpty()) {
            return;
        }
        HeroDef.Focus focus = d == null ? HeroDef.Focus.NEAREST : d.behaviour().focus();
        HeroDef.Protects protects = d == null ? HeroDef.Protects.NONE : d.behaviour().protects();
        int i = TargetRules.pick(focus, protects, honour, candidates);
        if (i >= 0 && found.get(i) != cur) {
            hero.setTarget(found.get(i));
        }
        found.clear();
    }

    private static boolean escaping(LivingEntity e) {
        return e instanceof HeroEntity h && h.stateFlag(HeroActions.STATE_ESCAPING);
    }

    /** A bow, crossbow or trident in hand, an archer NPC or a shooting monster: the same test for players and NPCs. */
    static boolean ranged(LivingEntity e) {
        var item = e.getMainHandItem().getItem();
        return e instanceof RangedAttackMob || item instanceof ProjectileWeaponItem || item instanceof TridentItem
                || e instanceof FactionNpc n && n.rangedFighter();
    }

    /**
     * Whether {@code e} attacks someone the hero protects (v1 8): its target or whom it hurt in the last 5 s is of the
     * hero's side ({@code allies}; the hero itself counts) or a worker of its side ({@code workers}). The same reading
     * for a player and an NPC: vanilla keeps whom any living entity last hurt.
     */
    private boolean attackingProtected(HeroDef d, LivingEntity e) {
        HeroDef.Protects p = d == null ? HeroDef.Protects.NONE : d.behaviour().protects();
        boolean guards = p != HeroDef.Protects.NONE || d != null && d.behaviour().focus() == HeroDef.Focus.THREAT_TO_ALLIES;
        if (!guards) {
            return false;
        }
        LivingEntity victim = e instanceof Mob m ? m.getTarget() : null;
        if (!protectedOne(p, victim)) {
            victim = e.tickCount - e.getLastHurtMobTimestamp() <= 100 ? e.getLastHurtMob() : null;
            return protectedOne(p, victim);
        }
        return true;
    }

    private boolean protectedOne(HeroDef.Protects p, LivingEntity v) {
        if (v == null || !v.isAlive() || !HeroForms.ally(hero, v)) {
            return false;
        }
        return p != HeroDef.Protects.WORKERS || v instanceof FactionNpc n && !n.fightsLikeSoldier();
    }
}
