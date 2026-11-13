package com.factionclash.client;

import com.factionclash.FactionClash;
import com.factionclash.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = FactionClash.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FACTION_NPC.get(), FactionNpcRenderer::new);
        event.registerEntityRenderer(ModEntities.HERO.get(), com.factionclash.client.hero.HeroRenderer::new);
        event.registerEntityRenderer(ModEntities.HERO_PROJECTILE.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.HERO_ZONE.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.HERO_DECOY.get(), com.factionclash.client.hero.HeroDecoyRenderer::new);
    }

    /** Heroes' looping sounds (a channel's loop, the glide's wind) need the client's sound manager (S2). */
    @SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        com.factionclash.hero.HeroLocalSounds.setLoops(com.factionclash.client.hero.HeroLoopSounds::tick);
    }

    /** Hero visual files a resource pack adds (preview fixtures for screenshots, HEROES_V2 15.2 A2). */
    @SubscribeEvent
    public static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new com.factionclash.client.hero.ClientHeroVisuals());
    }
}
