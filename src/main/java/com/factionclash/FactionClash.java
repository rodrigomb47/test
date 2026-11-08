package com.factionclash;

import com.factionclash.config.FCConfig;
import com.factionclash.event.FCEvents;
import com.factionclash.npc.FactionNpc;
import com.factionclash.registry.ModBlocks;
import com.factionclash.registry.ModEntities;
import com.factionclash.registry.ModItems;
import com.factionclash.util.ChunkLoading;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

@Mod(FactionClash.MODID)
public final class FactionClash {
    public static final String MODID = "factionclash";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FactionClash(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, FCConfig.SPEC);
        modBus.addListener(FactionClash::onAttributes);
        modBus.addListener(FactionClash::onTickets);
        modBus.addListener(com.factionclash.net.FCNetwork::register);
        NeoForge.EVENT_BUS.register(new FCEvents());
        NeoForge.EVENT_BUS.register(new com.factionclash.achieve.AchievementEvents());
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.FACTION_NPC.get(), FactionNpc.createAttributes().build());
    }

    private static void onTickets(RegisterTicketControllersEvent event) {
        event.register(ChunkLoading.CONTROLLER);
    }
}
