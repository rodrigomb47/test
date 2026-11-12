package com.factionclash.hero;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus handlers for heroes: the registry at server start, one entity per hero id, the revival timer. */
public final class HeroEvents {
    /** The config overrides are read again whenever a server starts (a singleplayer world, a dedicated server). */
    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        HeroRegistry.reload();
    }

    /** A stale copy of a hero (another UUID than the campaign records) never joins the level. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof HeroEntity h && !event.getLevel().isClientSide() && event.getLevel().getServer() != null
                && !HeroManager.mayJoin(event.getLevel().getServer(), h)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % HeroManager.TICK_INTERVAL == 0) {
            HeroManager.tick(event.getServer());
        }
    }
}
