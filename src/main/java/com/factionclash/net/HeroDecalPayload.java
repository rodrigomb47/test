package com.factionclash.net;

import com.factionclash.FactionClash;
import com.factionclash.logic.hero.Decals;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: a hero's telegraph ground decal (HEROES_V2 6.4; task A6a). A decal is shown with its pieces (the
 * shapes of {@link Decals}, packed {@link Decals#PIECE_FLOATS} floats a piece, relative to the origin), its colour
 * (RGB), and the ticks it takes to grow to full size (the move's wind-up); it is cleared by the same message with no
 * pieces ({@code id} 0: every decal of that hero). Presentation only: sent to the players within 32 blocks of the hero,
 * never deciding anything.
 */
public record HeroDecalPayload(int hero, int id, int growTicks, int color, double x, double y, double z, float[] pieces)
        implements CustomPacketPayload {
    public static final Type<HeroDecalPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "hero_decal"));

    /** At most this many floats a message (12 pieces, the per-hero budget, with room to spare). */
    public static final int MAX_FLOATS = 32 * Decals.PIECE_FLOATS;

    public static final StreamCodec<FriendlyByteBuf, HeroDecalPayload> CODEC =
            CustomPacketPayload.codec(HeroDecalPayload::write, HeroDecalPayload::read);

    /** A clear: the decal {@code id} of {@code hero} goes (0: all of them). */
    public static HeroDecalPayload clear(int hero, int id) {
        return new HeroDecalPayload(hero, id, 0, 0, 0, 0, 0, new float[0]);
    }

    public boolean isClear() {
        return pieces.length == 0;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(hero);
        buf.writeVarInt(id);
        buf.writeVarInt(growTicks);
        buf.writeInt(color);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        int n = Math.min(pieces.length, MAX_FLOATS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeFloat(pieces[i]);
        }
    }

    private static HeroDecalPayload read(FriendlyByteBuf buf) {
        int hero = buf.readVarInt(), id = buf.readVarInt(), grow = buf.readVarInt(), color = buf.readInt();
        double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_FLOATS) {
            throw new IllegalArgumentException("hero decal with " + n + " floats");
        }
        float[] p = new float[n];
        for (int i = 0; i < n; i++) {
            p[i] = buf.readFloat();
        }
        return new HeroDecalPayload(hero, id, grow, color, x, y, z, p);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
