package com.factionclash.client;

import com.factionclash.net.InfoPayload;
import com.factionclash.net.MapPayload;
import net.minecraft.client.Minecraft;

/** Client-only entry points the network handlers call to open the mod's screens. */
public final class ClientScreens {
    private ClientScreens() {
    }

    public static void openInfo(InfoPayload p) {
        Minecraft.getInstance().setScreen(new InfoScreen(p));
    }

    public static void openMap(MapPayload p) {
        Minecraft.getInstance().setScreen(new MapScreen(p));
    }
}
