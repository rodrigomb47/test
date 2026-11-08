package com.factionclash.campaign;

import com.factionclash.FactionClash;
import com.factionclash.faction.FactionType;
import com.factionclash.npc.FactionNpc;
import com.factionclash.util.ChunkLoading;
import com.factionclash.util.Messages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Campaign lifecycle and the PROPOSED victory rules: a base falls when its faction heart is destroyed; a faction
 * (human or The Hollow) is eliminated when it has no bases left; the last surviving faction wins.
 */
public final class CampaignManager {
    private static CampaignStarter starter;
    private static boolean destroying;

    private CampaignManager() {
    }

    public static boolean starting() {
        return starter != null && !starter.done();
    }

    public static void start(MinecraftServer server, int radius) {
        starter = new CampaignStarter(server, radius);
    }

    public static void tick(MinecraftServer server) {
        if (starter != null) {
            if (starter.step()) {
                starter = null;
            }
            return;
        }
        CampaignData data = CampaignData.get(server);
        if (!data.started) {
            return;
        }
        long t = server.overworld().getGameTime();
        FactionDirector.tick(server, data, t);
        if (t % 100 == 0 && data.winner == null) {
            checkHearts(server.overworld(), data);
            checkVictory(server, data);
        }
    }

    private static void checkHearts(ServerLevel level, CampaignData data) {
        for (BaseState b : List.copyOf(data.bases.values())) {
            BlockPos heart = b.point(BaseState.HEART);
            if (!b.destroyed && heart != null && level.isLoaded(heart)
                    && !level.getBlockState(heart).is(com.factionclash.registry.ModBlocks.FACTION_HEART.get())) {
                destroyBase(level, b, null, "unknown");
            }
        }
    }

    public static void onHeartRemoved(ServerLevel level, BlockPos pos) {
        if (destroying || level.getServer() == null) {
            return;
        }
        CampaignData data = CampaignData.get(level.getServer());
        for (BaseState b : List.copyOf(data.bases.values())) {
            if (!b.destroyed && pos.equals(b.point(BaseState.HEART))) {
                destroyBase(level, b, null, "unknown");
            }
        }
    }

    public static void destroyBase(ServerLevel level, BaseState base, FactionType by, String byName) {
        if (base.destroyed) {
            return;
        }
        CampaignData data = CampaignData.get(level.getServer());
        base.destroyed = true;
        base.destroyedAt = level.getGameTime();
        BlockPos heart = base.point(BaseState.HEART);
        destroying = true;
        try {
            if (heart != null && level.getBlockState(heart).is(com.factionclash.registry.ModBlocks.FACTION_HEART.get())) {
                level.destroyBlock(heart, false);
                level.setBlock(heart, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        } finally {
            destroying = false;
        }
        ChunkLoading.forceBase(level, base, false);
        if (by != null) {
            data.faction(by).basesDestroyed++;
        }
        data.setDirty();
        Messages.toAll(level.getServer(), Component.literal(base.faction.displayName + (base.isSettlement() ? " settlement" : " lair")
                + " at " + base.center.toShortString() + " has fallen" + (by == null ? "" : " to " + by.displayName + " (" + byName + ")"))
                .withStyle(ChatFormatting.GOLD));
        if (data.livingBasesOf(base.faction).isEmpty()) {
            eliminate(level, data, base.faction);
            Diplomacy.onElimination(level, data, base.faction, by);
        }
    }

    public static void eliminate(ServerLevel level, CampaignData data, FactionType faction) {
        FactionState f = data.faction(faction);
        if (!f.alive) {
            return;
        }
        f.alive = false;
        f.eliminatedAt = level.getGameTime();
        f.raidTarget = -1;
        f.raiders.clear();
        List<Entity> remove = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof FactionNpc n && n.getFaction() == faction) {
                remove.add(e);
            }
        }
        for (BaseState b : data.basesOf(faction)) {
            for (UUID u : b.lairMobs) {
                Entity e = level.getEntity(u);
                if (e != null) {
                    remove.add(e);
                }
            }
            b.lairMobs.clear();
        }
        for (Entity e : remove) {
            e.kill();
        }
        for (NpcRecord r : f.roster.values()) {
            r.alive = false;
        }
        data.setDirty();
        Messages.toAll(level.getServer(), Component.literal(faction.displayName + " has been eliminated!").withStyle(ChatFormatting.RED));
        checkVictory(level.getServer(), data);
    }

    public static List<FactionType> survivors(CampaignData data) {
        List<FactionType> out = new ArrayList<>();
        for (FactionType t : FactionType.values()) {
            if (!data.basesOf(t).isEmpty() && !data.livingBasesOf(t).isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    public static void checkVictory(MinecraftServer server, CampaignData data) {
        if (!data.started || data.winner != null) {
            return;
        }
        List<FactionType> alive = survivors(data);
        if (alive.size() == 1) {
            data.winner = alive.get(0);
            data.finishedAt = server.overworld().getGameTime();
            data.setDirty();
            Messages.toAll(server, Component.literal("*** " + data.winner.displayName + " is the last faction standing and wins the campaign! ***")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            FactionClash.LOGGER.info("Campaign winner: {}", data.winner.displayName);
        }
    }
}
