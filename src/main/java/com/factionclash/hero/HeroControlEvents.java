package com.factionclash.hero;

import com.factionclash.npc.FactionNpc;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Control on the game bus (task A4; HEROES_V2 6.15), the same for every receiver:
 * <ul>
 * <li>a stunned player cannot attack, use or place items, interact or break blocks (its walking and jumping are held
 * by {@link HeroStatus#holdStun}); a stunned NPC's or mob's goals are held by
 * {@link com.factionclash.combat.StunnedGoal}; any stunned living entity starts no item use, and a melee blow it deals
 * (one whose attacker is also what struck) is cancelled, whoever it is;</li>
 * <li>a taunted attacker deals half damage to anyone but the hero that taunts it, players and NPCs alike;</li>
 * <li>a mob loaded while it carries a stun gets its {@code StunnedGoal} back;</li>
 * <li>summons ({@link HeroSummons}): adopted again when loaded (refused when expired), ticked, never dropping loot or
 * experience, never tamed, never changing a block.</li>
 * </ul>
 * The blindness and darkness rule for NPCs (6.15) is in {@code FactionNpc}.
 */
public final class HeroControlEvents {
    private static boolean stunned(Entity e) {
        return e instanceof LivingEntity l && HeroStatus.stunned(l);
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (stunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (stunned(event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (stunned(event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onHitBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (stunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (stunned(event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (stunned(event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (stunned(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (stunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onStartUsing(LivingEntityUseItemEvent.Start event) {
        if (stunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * A stunned attacker's melee blow lands nothing; a taunted attacker deals half to anyone but its taunter. Runs
     * after the friendly-fire rule, before vanilla armour, for every attacker and target.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource src = event.getSource();
        if (event.getEntity().level().isClientSide() || !(src.getEntity() instanceof LivingEntity a) || a == event.getEntity()) {
            return;
        }
        if (src.getDirectEntity() == a && !src.is(HeroDamage.HERO) && HeroStatus.stunned(a)) {
            event.setCanceled(true);
            return;
        }
        double f = HeroStatus.tauntFactor(a, event.getEntity());
        if (f != 1) {
            event.setAmount((float) (event.getAmount() * f));
        }
    }

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob m)) {
            return;
        }
        if (HeroSummons.isSummon(m) && !HeroSummons.onJoin(m)) {
            event.setCanceled(true);
            return;
        }
        if (HeroStatus.stunned(m)) {
            com.factionclash.combat.StunnedGoal.ensure(m);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        HeroSummons.tick(event.getServer());
        HeroTalents.tick(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        HeroSummons.clear();
        HeroTalents.clear();
    }

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (HeroSummons.isSummon(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onExperience(LivingExperienceDropEvent event) {
        if (HeroSummons.isSummon(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onGrief(EntityMobGriefingEvent event) {
        if (HeroSummons.isSummon(event.getEntity())) {
            event.setCanGrief(false);
        }
    }

    @SubscribeEvent
    public void onTame(AnimalTameEvent event) {
        if (HeroSummons.isSummon(event.getAnimal())) {
            event.setCanceled(true);
        }
    }

    /** Blindness or darkness on an NPC or a hero (HEROES_V2 6.15): it sees, follows and shoots only this far. */
    public static final double BLIND_RANGE = 5;

    /** Whether an NPC's senses are cut to {@link #BLIND_RANGE} now (a player sees as vanilla shows it). */
    public static boolean blinded(FactionNpc npc) {
        return npc.hasEffect(MobEffects.BLINDNESS) || npc.hasEffect(MobEffects.DARKNESS);
    }
}
