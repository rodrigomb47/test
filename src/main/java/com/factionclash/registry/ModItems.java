package com.factionclash.registry;

import com.factionclash.FactionClash;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactionClash.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FactionClash.MODID);

    public static final DeferredItem<BlockItem> REPORTING_STATION = ITEMS.registerSimpleBlockItem("reporting_station", ModBlocks.REPORTING_STATION);
    public static final DeferredItem<BlockItem> WORLD_MAP_BOARD = ITEMS.registerSimpleBlockItem("world_map_board", ModBlocks.WORLD_MAP_BOARD);
    public static final DeferredItem<BlockItem> FACTION_HEART = ITEMS.registerSimpleBlockItem("faction_heart", ModBlocks.FACTION_HEART);
    public static final DeferredItem<BlockItem> BEDROLL = ITEMS.registerSimpleBlockItem("bedroll", ModBlocks.BEDROLL);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.factionclash"))
            .icon(() -> new ItemStack(FACTION_HEART.get()))
            .displayItems((params, out) -> {
                out.accept(REPORTING_STATION.get());
                out.accept(WORLD_MAP_BOARD.get());
                out.accept(FACTION_HEART.get());
                out.accept(BEDROLL.get());
            })
            .build());

    private ModItems() {
    }
}
