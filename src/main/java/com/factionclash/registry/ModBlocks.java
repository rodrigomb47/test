package com.factionclash.registry;

import com.factionclash.FactionClash;
import com.factionclash.block.BedrollBlock;
import com.factionclash.block.FactionHeartBlock;
import com.factionclash.block.ReportingStationBlock;
import com.factionclash.block.WorldMapBoardBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactionClash.MODID);

    public static final DeferredBlock<Block> REPORTING_STATION = BLOCKS.register("reporting_station",
            () -> new ReportingStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(4.0F, 8.0F).sound(SoundType.WOOD)));
    public static final DeferredBlock<Block> WORLD_MAP_BOARD = BLOCKS.register("world_map_board",
            () -> new WorldMapBoardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F, 6.0F).sound(SoundType.WOOD)));
    public static final DeferredBlock<Block> FACTION_HEART = BLOCKS.register("faction_heart",
            () -> new FactionHeartBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(40.0F, 1200.0F)
                    .sound(SoundType.LODESTONE).lightLevel(s -> 10).pushReaction(PushReaction.BLOCK).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> BEDROLL = BLOCKS.register("bedroll",
            () -> new BedrollBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.2F).sound(SoundType.GRASS)));

    private ModBlocks() {
    }
}
