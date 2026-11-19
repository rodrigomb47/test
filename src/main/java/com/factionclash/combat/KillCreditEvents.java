package com.factionclash.combat;

import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroKillFeed;
import com.factionclash.logic.hero.KillCredit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The game's half of {@link KillCredit} (HEROES_V2 6.17, {@code docs/design/breaching.md} 8.5): every blow is recorded
 * for its receiver ({@link KillCredits#blow}), the refresh runs once a second, and at a death with no attacker of its
 * own the credited enemy becomes the receiver's last attacker (vanilla's message and kill score then name it), a hero
 * credited with a death by terrain gets its kill ({@link HeroEntity#creditKill}), and a hero's death is told in the kill
 * feed ({@link HeroKillFeed}). The same for every receiver: a player, an NPC, a hero or a monster.
 */
public final class KillCreditEvents {
    /** Every blow with a living attacker, whether or not armour, a shield or a hurt cooldown takes it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onIncoming(LivingIncomingDamageEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            KillCredits.blow(event.getSource().getEntity(), event.getEntity());
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        KillCredits.tick(event.getServer());
    }

    /**
     * Last of the death's handlers (a cancelled death is no death), and before a player's own death message, which
     * vanilla builds after the event (so it names the credited enemy).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        DamageSource source = event.getSource();
        if (KillCredits.attacker(source) == null) {
            LivingEntity credited = KillCredits.credited(victim, source.getMsgId());
            if (credited != null) {
                victim.setLastHurtByMob(credited);
                if (KillCredit.terrain(source.getMsgId()) && credited instanceof HeroEntity hero && hero.isAlive()) {
                    hero.creditKill(level, victim); // its blow's kill is vanilla's killedEntity; terrain's is this
                }
            }
        }
        HeroKillFeed.onDeath(victim, source);
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        KillCredits.clear();
    }
}
