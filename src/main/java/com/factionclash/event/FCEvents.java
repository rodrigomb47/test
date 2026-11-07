package com.factionclash.event;

import com.factionclash.campaign.BaseBuilder;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.CampaignManager;
import com.factionclash.combat.CombatRules;
import com.factionclash.command.FCCommands;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus event handlers. */
public final class FCEvents {
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        CampaignManager.tick(event.getServer());
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        FCCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        CampaignData data = CampaignData.get(event.getServer());
        if (!data.started && FCConfig.AUTO_START.get()) {
            CampaignManager.start(event.getServer(), FCConfig.MAP_RADIUS.get());
        }
        if (data.started) {
            com.factionclash.util.ChunkLoading.restoreTravellers(event.getServer().overworld());
        }
    }

    /** Vanilla hostiles hunt faction NPCs under their normal species behaviour; Hollow mobs keep to their lair. */
    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || mob instanceof FactionNpc) {
            return;
        }
        if (CombatRules.huntsNpcs(mob)) {
            boolean spider = mob instanceof net.minecraft.world.entity.monster.Spider;
            mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, FactionNpc.class, 10, true, false,
                    e -> !spider || mob.getLightLevelDependentMagicValue() < 0.5F));
        }
        if (mob.getPersistentData().contains(BaseBuilder.LAIR_TAG) && mob instanceof PathfinderMob pm) {
            pm.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(pm, 1.0));
            if (event.getLevel().getServer() != null) {
                CampaignData data = CampaignData.get(event.getLevel().getServer());
                BaseState lair = data.base(mob.getPersistentData().getInt(BaseBuilder.LAIR_TAG));
                if (lair != null && !lair.destroyed) {
                    // re-adopt on load so a reload never leaves a live monster untracked
                    if (lair.lairMobs.add(mob.getUUID())) {
                        data.setDirty();
                    }
                    if (!pm.hasRestriction()) {
                        pm.restrictTo(lair.center, 10);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || event.getEntity().level().isClientSide()) {
            return;
        }
        if (attacker != event.getEntity() && CombatRules.sameSide(attacker, event.getEntity())
                && (attacker instanceof FactionNpc || event.getEntity() instanceof FactionNpc)) {
            event.setCanceled(true); // no friendly fire between faction members and their NPCs
            return;
        }
        if (event.getEntity() instanceof FactionNpc && CombatRules.isUndeadHalfDamageSource(attacker)) {
            event.setAmount(event.getAmount() * 0.5F);
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.animal.Animal a && a.level() instanceof ServerLevel lv
                && baseAt(CampaignData.get(lv.getServer()), a.blockPosition()) != null) {
            // livestock losses are worth a line in the log (who or what killed the base's animals)
            net.minecraft.world.entity.Entity killer = event.getSource().getEntity();
            com.factionclash.FactionClash.LOGGER.info("Livestock death: {} at {} by {} ({})", a.getType().getDescription().getString(),
                    a.blockPosition().toShortString(), event.getSource().getMsgId(),
                    killer == null ? "-" : killer.getName().getString());
        }
        if (event.getEntity().level() instanceof ServerLevel level && event.getEntity().getPersistentData().contains(BaseBuilder.LAIR_TAG)) {
            CampaignData data = CampaignData.get(level.getServer());
            BaseState lair = data.base(event.getEntity().getPersistentData().getInt(BaseBuilder.LAIR_TAG));
            if (lair != null && lair.lairMobs.remove(event.getEntity().getUUID())) {
                data.setDirty();
            }
        }
    }

    private static BaseState baseAt(CampaignData data, BlockPos pos) {
        for (BaseState b : data.bases.values()) {
            if (!b.destroyed && b.contains(pos, 0)) {
                return b;
            }
        }
        return null;
    }

    /** Players' deliberate edits inside their own faction's base are preserved by repairs (spec 11). */
    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p) || !(event.getLevel() instanceof ServerLevel)) {
            return;
        }
        CampaignData data = CampaignData.get(p.server);
        BaseState b = baseAt(data, event.getPos());
        if (b != null && data.players.get(p.getUUID()) == b.faction && b.planIndex().containsKey(event.getPos().asLong())) {
            b.playerEdits.add(event.getPos().asLong());
            b.pendingRepairs.remove(event.getPos().asLong());
            data.setDirty();
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        CampaignData data = CampaignData.get(p.server);
        BaseState b = baseAt(data, event.getPos());
        if (b != null && data.players.get(p.getUUID()) == b.faction) {
            b.pendingRepairs.remove(event.getPos().asLong());
            if (b.planIndex().containsKey(event.getPos().asLong())) {
                b.playerEdits.add(event.getPos().asLong());
            }
            data.setDirty();
        }
    }

    /** Explosion damage to planned blocks is queued for repair immediately. */
    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        CampaignData data = CampaignData.get(level.getServer());
        if (!data.started) {
            return;
        }
        for (BlockPos p : event.getAffectedBlocks()) {
            BaseState b = baseAt(data, p);
            if (b != null && b.planIndex().containsKey(p.asLong()) && !b.playerEdits.contains(p.asLong())) {
                b.pendingRepairs.add(p.asLong());
                data.setDirty();
            }
        }
    }

    /** Respawn at the nearest surviving allied base when the player has no bed (spec 1). */
    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        if (p.getRespawnPosition() != null) {
            return;
        }
        CampaignData data = CampaignData.get(p.server);
        FactionType f = data.players.get(p.getUUID());
        if (f == null) {
            return;
        }
        BaseState best = null;
        double bestD = Double.MAX_VALUE;
        for (BaseState b : data.livingBasesOf(f)) {
            double d = b.center.distSqr(p.blockPosition());
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        if (best != null) {
            BlockPos s = best.point(BaseState.SPAWN);
            p.teleportTo(p.server.overworld(), s.getX() + 0.5, s.getY(), s.getZ() + 0.5, p.getYRot(), p.getXRot());
        }
    }
}
