package com.factionclash.hero;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * The boss controller's hooks on the game bus (task A6a; HEROES_V2 6.2, 6.9), each the same for a player and an NPC:
 * <ul>
 * <li>a hit on a hero, after armour, resistances and a counter's absorb (the lowest priority of the damage event):
 * the damage-taken window, the per-hit cap for a hit with any attacking entity and its poise
 * ({@link HeroBoss#incoming});</li>
 * <li>a shield that blocks a hero's heavy move 5-11 ticks after it went up parries it: 25 poise on the hero
 * ({@link HeroBoss#blocked}), whoever held the shield.</li>
 * </ul>
 */
public final class HeroBossEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof HeroEntity h && !h.level().isClientSide() && !h.isPreview() && h.isAlive()
                && event.getNewDamage() > 0) {
            float d = h.combat().boss().incoming(event.getSource(), event.getNewDamage());
            if (d != event.getNewDamage()) {
                event.setNewDamage(d);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onShieldBlock(LivingShieldBlockEvent event) {
        if (event.getBlocked() && !event.getEntity().level().isClientSide()
                && event.getDamageSource().is(HeroDamage.HERO)
                && event.getDamageSource().getEntity() instanceof HeroEntity h && h.isAlive()) {
            h.combat().boss().blocked(event.getEntity());
        }
    }
}
