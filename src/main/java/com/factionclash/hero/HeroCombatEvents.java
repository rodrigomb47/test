package com.factionclash.hero;

import com.factionclash.logic.hero.HeroDef.Element;
import com.factionclash.logic.hero.HeroDef.Stat;
import com.factionclash.logic.hero.HeroRules;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import com.factionclash.logic.hero.HeroDef.Trigger;
import com.factionclash.logic.hero.PassiveBook;
import java.util.List;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Game-bus handlers of the ability core (task A3), each the same for players and NPCs:
 * <ul>
 * <li>every server tick, the statuses of every entity that carries some ({@link HeroStatus#tickAll});</li>
 * <li>an entity loaded with statuses is ticked again (its stat modifiers come back);</li>
 * <li>damage that is not a hero's (a sword, an arrow, lava, a monster): the attacker's {@code damage_dealt}, the
 * target's {@code damage_taken} and, on a hero, its resistance to the element vanilla's damage maps to (v1 6.1, 13.3);
 * a hero's own hits already went through these in {@link HeroDamage};</li>
 * <li>a hero's arrow that lands is its basic hit (energy and {@code on_hit}, v1 9 and 14);</li>
 * <li>{@code healing_received} on every heal (v1 13.3);</li>
 * <li>no fall damage from a hero's own leap or hop (v1 13.4);</li>
 * <li>the passives' world triggers (task A4, v1 17.1): {@code on_ally_hurt} and {@code on_ally_low_health} when an ally
 * within 12 blocks is hurt, {@code on_ally_attack} when an ally within 12 blocks strikes an enemy with a weapon or an
 * arrow (never a hero's move, a passive or a summon: the loop guard of v1 17.3), {@code on_target_heal} when its
 * target heals and {@code on_guard_broken} when its own blow is blocked. A player and an NPC set them off alike.</li>
 * </ul>
 */
public final class HeroCombatEvents {
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        HeroStatus.tickAll(event.getServer());
    }

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity l) {
            HeroStatus s = HeroStatus.peek(l);
            if (s != null && !s.book().isEmpty()) {
                HeroStatus.track(l);
            }
        }
    }

    /** The element vanilla damage maps to on a hero target (v1 6.1), or null (falls, drowning, the void...). */
    static Element elementOf(DamageSource src) {
        if (src.is(DamageTypeTags.IS_FIRE)) {
            return Element.FIRE;
        }
        if (src.is(DamageTypeTags.IS_FREEZING)) {
            return Element.FROST;
        }
        if (src.is(DamageTypeTags.IS_LIGHTNING)) {
            return Element.SHOCK;
        }
        if (src.is(DamageTypes.WITHER) || src.is(DamageTypes.WITHER_SKULL)) {
            return Element.BLIGHT;
        }
        if (src.is(DamageTypes.MAGIC) || src.is(DamageTypes.INDIRECT_MAGIC)) {
            return Element.ARCANE;
        }
        return src.getEntity() != null ? Element.PHYSICAL : null;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource src = event.getSource();
        if (target.level().isClientSide() || src.is(HeroDamage.HERO) || event.getAmount() <= 0) {
            return;
        }
        double dealt = src.getEntity() instanceof LivingEntity a ? HeroStatus.percent(a, Stat.DAMAGE_DEALT) : 0;
        double taken = HeroStatus.percent(target, Stat.DAMAGE_TAKEN);
        Element e = target instanceof HeroEntity ? elementOf(src) : null;
        double resist = e == null ? 0 : HeroDamage.resistance(target, e);
        if (dealt == 0 && taken == 0 && resist == 0) {
            return;
        }
        event.setAmount((float) (event.getAmount() * HeroRules.damageFactor(dealt, 1, resist, taken)));
    }

    @SubscribeEvent
    public void onDamaged(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow
                && arrow.getPersistentData().getBoolean(HeroEntity.BASIC_ARROW_TAG)
                && event.getSource().getEntity() instanceof HeroEntity hero && hero.isAlive()) {
            hero.combat().onBasicHit(event.getEntity(), arrow);
        }
        allyTriggers(event.getEntity(), event.getSource());
    }

    /** {@code on_ally_hurt}, {@code on_ally_low_health} and {@code on_ally_attack} for the heroes near (v1 17.1). */
    static void allyTriggers(LivingEntity victim, DamageSource src) {
        List<HeroEntity> heroes = HeroEntity.live();
        if (heroes.isEmpty()) {
            return;
        }
        LivingEntity attacker = src.getEntity() instanceof LivingEntity a && a != victim ? a : null;
        boolean heroMove = src.is(HeroDamage.HERO);
        boolean summon = HeroSummons.isSummon(src.getEntity());
        boolean weapon = attacker != null && !heroMove && (src.getDirectEntity() == attacker
                || src.getDirectEntity() instanceof Projectile);
        double r2 = PassiveBook.ALLY_RADIUS * PassiveBook.ALLY_RADIUS;
        for (HeroEntity h : heroes) {
            if (h == victim || !h.isAlive() || h.level() != victim.level()) {
                continue;
            }
            HeroCombat c = h.combat();
            if (h.distanceToSqr(victim) <= r2 && HeroForms.ally(h, victim)) {
                if (attacker != null && c.hasPassive(Trigger.ON_ALLY_HURT) && !HeroForms.ally(h, attacker)) {
                    c.triggerLater(new PassiveBook.Event(Trigger.ON_ALLY_HURT, null, 1, null, 0, summon || heroMove), attacker, victim);
                }
                if (victim.isAlive() && c.hasPassive(Trigger.ON_ALLY_LOW_HEALTH)) {
                    double share = victim.getHealth() / Math.max(1F, victim.getMaxHealth());
                    c.triggerLater(new PassiveBook.Event(Trigger.ON_ALLY_LOW_HEALTH, null, share, victim.getStringUUID(), 0,
                            summon || heroMove), attacker, victim);
                }
            }
            if (weapon && attacker != h && c.hasPassive(Trigger.ON_ALLY_ATTACK) && h.distanceToSqr(attacker) <= r2
                    && HeroForms.ally(h, attacker) && HeroForms.enemy(h, victim)) {
                // the hero joins the attack on that enemy; a summon's bite is guarded (v1 17.3)
                c.triggerLater(new PassiveBook.Event(Trigger.ON_ALLY_ATTACK, null, 1, null, 0, summon), victim, attacker);
            }
        }
    }

    /** {@code on_guard_broken}: a hero's own blow was blocked by a shield (HEROES_V2 6.6), whoever held it. */
    @SubscribeEvent
    public void onBlocked(LivingShieldBlockEvent event) {
        if (event.getBlocked() && event.getDamageSource().getEntity() instanceof HeroEntity h && h.isAlive()
                && !h.level().isClientSide()) {
            h.combat().triggerLater(PassiveBook.Event.of(Trigger.ON_GUARD_BROKEN), event.getEntity(), null);
        }
    }

    @SubscribeEvent
    public void onHeal(LivingHealEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getAmount() > 0) {
            for (HeroEntity h : HeroEntity.live()) {
                if (h.getTarget() == event.getEntity() && h.isAlive() && h.combat().hasPassive(Trigger.ON_TARGET_HEAL)) {
                    h.combat().triggerLater(PassiveBook.Event.of(Trigger.ON_TARGET_HEAL), event.getEntity(), null);
                }
            }
        }
        double p = HeroStatus.percent(event.getEntity(), Stat.HEALING_RECEIVED);
        if (p != 0) {
            event.setAmount((float) Math.max(0, event.getAmount() * (1 + HeroStatus.clampStat(Stat.HEALING_RECEIVED, p) / 100.0)));
        }
    }

    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof HeroEntity h && h.combat().ownHop()) {
            event.setDamageMultiplier(0);
        }
    }
}
