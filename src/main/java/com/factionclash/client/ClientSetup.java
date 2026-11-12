package com.factionclash.client;

import com.factionclash.FactionClash;
import com.factionclash.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = FactionClash.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FACTION_NPC.get(), FactionNpcRenderer::new);
        event.registerEntityRenderer(ModEntities.HERO.get(), com.factionclash.client.hero.HeroRenderer::new);
    }
}
