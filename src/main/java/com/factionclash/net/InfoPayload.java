package com.factionclash.net;

import com.factionclash.FactionClash;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server to client: open an information screen (reporting station, NPC inspection) with coloured lines. */
public record InfoPayload(String title, int titleColor, List<String> lines, List<Integer> colors) implements CustomPacketPayload {
    public static final Type<InfoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "info"));

    public static final StreamCodec<ByteBuf, InfoPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, InfoPayload::title,
            ByteBufCodecs.INT, InfoPayload::titleColor,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)), InfoPayload::lines,
            ByteBufCodecs.INT.apply(ByteBufCodecs.list(512)), InfoPayload::colors,
            InfoPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
