package com.factionclash.hero;

import com.factionclash.logic.hero.MoveTiming;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * The move runner's hooks on the game bus (task A5; HEROES_V2 4.8, 6.5-6.6, 6.9), each the same for a player and an
 * NPC:
 * <ul>
 * <li>a hero's {@code counter} stance catches the first blow in its wind-up (after shields and armour): most of it is
 * absorbed and the riposte answers on the hero's next tick ({@link HeroCombat#counterBlow});</li>
 * <li>a knock-back stronger than 0.5 (after the hero's knock-back resistance) interrupts a hero's wind-up, unless the
 * move has super armour or is an ultimate;</li>
 * <li>an unblockable move (every grab among them) goes through a raised shield, whoever holds it;</li>
 * <li>a broken guard ({@code guard_break}) keeps any shield from going up, a player's and an NPC's alike, for its
 * seconds.</li>
 * </ul>
 */
public final class HeroMoveEvents {
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof HeroEntity h && !h.level().isClientSide() && h.isAlive() && event.getNewDamage() > 0
                && h.combat().countering()) {
            double share = h.combat().counterBlow(event.getSource());
            if (share < 1) {
                event.setNewDamage((float) (event.getNewDamage() * share));
            }
        }
    }

    @SubscribeEvent
    public void onKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof HeroEntity h && !h.level().isClientSide() && h.combat().windingUp()
                && MoveTiming.knockbackInterrupts(event.getStrength(), h.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE))) {
            h.combat().interrupt(MoveTiming.Interrupt.KNOCKBACK);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onShieldBlock(LivingShieldBlockEvent event) {
        if (event.getBlocked() && HeroDamage.dealingUnblockable() && event.getDamageSource().is(HeroDamage.HERO)) {
            event.setBlocked(false);
        }
    }

    @SubscribeEvent
    public void onStartUsing(LivingEntityUseItemEvent.Start event) {
        LivingEntity e = event.getEntity();
        if (!e.level().isClientSide() && event.getItem().canPerformAction(ItemAbilities.SHIELD_BLOCK) && HeroStatus.guardBroken(e)) {
            event.setCanceled(true);
        }
    }
}
