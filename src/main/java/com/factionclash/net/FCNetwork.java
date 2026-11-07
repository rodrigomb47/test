package com.factionclash.net;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/**
 * Network messages. The server only ever sends data; the screens that show it live in the client package and are
 * referenced from the handler lambdas only, so a dedicated server never loads them.
 */
public final class FCNetwork {
    private FCNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(InfoPayload.TYPE, InfoPayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.factionclash.client.ClientScreens.openInfo(p)));
        r.playToClient(MapPayload.TYPE, MapPayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.factionclash.client.ClientScreens.openMap(p)));
    }

    public static void sendInfo(ServerPlayer player, String title, int titleColor, List<String> lines, List<Integer> colors) {
        PacketDistributor.sendToPlayer(player, new InfoPayload(title, titleColor, lines, colors));
    }

    public static void sendMap(ServerPlayer player, MapPayload map) {
        PacketDistributor.sendToPlayer(player, map);
    }
}
