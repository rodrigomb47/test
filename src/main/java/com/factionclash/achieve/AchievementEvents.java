package com.factionclash.achieve;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.faction.FactionType;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Game events that count toward a faction's advancements (kills, shots, breeding, players' own advancements). */
public final class AchievementEvents {
    /** A faction player's advancement is the faction's too. */
    @SubscribeEvent
    public void onPlayerAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null || event.getAdvancement().value().display().isEmpty()) {
            return;
        }
        FactionType f = CampaignData.get(p.getServer()).players.get(p.getUUID());
        if (f != null) {
            FactionAchievements.complete(p.getServer(), f, event.getAdvancement(), p.getName().getString());
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getServer() != null) {
            FactionType f = CampaignData.get(p.getServer()).players.get(p.getUUID());
            if (f != null) {
                FactionAchievements.grantAll(p, f);
            }
        }
    }

    /** Adventure, Monster Hunter, Monsters Hunted, Sniper Duel, Who's the Pillager Now? - and being killed. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        MinecraftServer server = victim.getServer();
        if (server == null) {
            return;
        }
        if (victim instanceof FactionNpc dead) {
            FactionAchievements.criterion(server, dead.getFaction(), "adventure/root", "killed_by_something", dead.npcName());
        }
        if (!(event.getSource().getEntity() instanceof FactionNpc killer)) {
            return;
        }
        FactionType f = killer.getFaction();
        String by = killer.npcName();
        FactionAchievements.criterion(server, f, "adventure/root", "killed_something", by);
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
        if (victim instanceof Enemy) {
            FactionAchievements.criterion(server, f, "adventure/kill_a_mob", type, by);
            FactionAchievements.criterion(server, f, "adventure/kill_all_mobs", type, by);
        }
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof AbstractArrow && victim instanceof AbstractSkeleton && victim.getType() == net.minecraft.world.entity.EntityType.SKELETON
                && killer.distanceTo(victim) >= 50) {
            FactionAchievements.criterion(server, f, "adventure/sniper_duel", "killed_skeleton", by);
        }
        if (direct instanceof AbstractArrow arrow && victim.getType() == net.minecraft.world.entity.EntityType.PILLAGER
                && arrow.getWeaponItem() != null && arrow.getWeaponItem().getItem() instanceof net.minecraft.world.item.CrossbowItem) {
            FactionAchievements.criterion(server, f, "adventure/whos_the_pillager_now", "kill_pillager", by);
        }
    }

    /** Take Aim: an arrow from a faction NPC hit something. */
    @SubscribeEvent
    public void onDamage(LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() instanceof AbstractArrow && event.getSource().getEntity() instanceof FactionNpc shooter
                && event.getEntity().getServer() != null) {
            FactionAchievements.criterion(event.getEntity().getServer(), shooter.getFaction(), "adventure/shoot_arrow", "shot_arrow", shooter.npcName());
        }
    }

    /** The Parrots and the Bats, Two by Two: animals bred in a faction's pen. */
    @SubscribeEvent
    public void onBreed(BabyEntitySpawnEvent event) {
        if (!(event.getParentA().level() instanceof ServerLevel level) || event.getChild() == null) {
            return;
        }
        for (BaseState b : CampaignData.get(level.getServer()).bases.values()) {
            if (!b.destroyed && b.isSettlement() && b.contains(event.getParentA().blockPosition(), 2)) {
                FactionAchievements.criterion(level.getServer(), b.faction, "husbandry/breed_an_animal", "bred", null);
                FactionAchievements.criterion(level.getServer(), b.faction, "husbandry/bred_all_animals",
                        BuiltInRegistries.ENTITY_TYPE.getKey(event.getChild().getType()).toString(), null);
                return;
            }
        }
    }
}
