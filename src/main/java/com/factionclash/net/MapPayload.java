package com.factionclash.net;

import com.factionclash.FactionClash;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: open the faction's world map board. {@code frame} = [map centre x, centre z, radius],
 * {@code explored} = 32x32 sectors covered by delivered reports, {@code lines} = the written report list.
 */
public record MapPayload(String title, int titleColor, List<Integer> frame, List<Marker> markers, List<Long> explored,
                         List<String> lines) implements CustomPacketPayload {
    public static final Type<MapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "map"));

    /** Marker kinds drawn on the map. */
    public static final int OWN_BASE = 0, ENEMY_BASE = 1, LAIR = 2, HOSTILES = 3, CAVE = 4, ORE = 5, SURVEYED_CAVE = 6, VILLAGE = 7, STALE = 16;

    public record Marker(int x, int z, int kind, String label) {
        public static final StreamCodec<ByteBuf, Marker> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Marker::x,
                ByteBufCodecs.VAR_INT, Marker::z,
                ByteBufCodecs.VAR_INT, Marker::kind,
                ByteBufCodecs.STRING_UTF8, Marker::label,
                Marker::new);
    }

    public static final StreamCodec<ByteBuf, MapPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MapPayload::title,
            ByteBufCodecs.INT, MapPayload::titleColor,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), MapPayload::frame,
            Marker.CODEC.apply(ByteBufCodecs.list(1024)), MapPayload::markers,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(16384)), MapPayload::explored,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)), MapPayload::lines,
            MapPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
