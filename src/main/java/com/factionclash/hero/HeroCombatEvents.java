package com.factionclash.hero;

import com.factionclash.logic.hero.HeroDef.Element;
import com.factionclash.logic.hero.HeroDef.Stat;
import com.factionclash.logic.hero.HeroRules;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
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
 * <li>no fall damage from a hero's own leap or hop (v1 13.4).</li>
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
    }

    @SubscribeEvent
    public void onHeal(LivingHealEvent event) {
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
