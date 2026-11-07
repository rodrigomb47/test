package com.factionclash.util;

import com.factionclash.campaign.CampaignData;
import com.factionclash.faction.FactionType;
import com.factionclash.FactionClash;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Faction-scoped messaging: players only hear about their own faction and legitimately known events (spec 19). */
public final class Messages {
    private Messages() {
    }

    public static void toFaction(MinecraftServer server, FactionType faction, Component msg) {
        if (server == null) {
            return;
        }
        CampaignData data = CampaignData.get(server);
        MutableComponent line = Component.literal("[" + faction.displayName + "] ").withStyle(faction.color).append(msg);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (data.players.get(p.getUUID()) == faction) {
                p.sendSystemMessage(line);
            }
        }
        FactionClash.LOGGER.info("[{}] {}", faction.displayName, msg.getString());
    }

    public static void toAll(MinecraftServer server, Component msg) {
        server.getPlayerList().broadcastSystemMessage(msg, false);
        FactionClash.LOGGER.info("[Faction Clash] {}", msg.getString());
    }
}
