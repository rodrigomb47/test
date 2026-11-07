package com.factionclash.work;

import com.factionclash.logic.MineResource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Maps abstract mine resources to real items, and to the block shown cracking on the mine face. */
public final class MineItems {
    private MineItems() {
    }

    public static Item item(MineResource r) {
        return switch (r) {
            case COBBLESTONE -> Items.COBBLESTONE;
            case COAL -> Items.COAL;
            case RAW_COPPER -> Items.RAW_COPPER;
            case RAW_IRON -> Items.RAW_IRON;
            case LAPIS -> Items.LAPIS_LAZULI;
            case REDSTONE -> Items.REDSTONE;
            case RAW_GOLD -> Items.RAW_GOLD;
            case DIAMOND -> Items.DIAMOND;
            case EMERALD -> Items.EMERALD;
        };
    }

    public static Block faceBlock(MineResource r) {
        return switch (r) {
            case COBBLESTONE -> Blocks.STONE;
            case COAL -> Blocks.COAL_ORE;
            case RAW_COPPER -> Blocks.COPPER_ORE;
            case RAW_IRON -> Blocks.IRON_ORE;
            case LAPIS -> Blocks.LAPIS_ORE;
            case REDSTONE -> Blocks.REDSTONE_ORE;
            case RAW_GOLD -> Blocks.GOLD_ORE;
            case DIAMOND -> Blocks.DIAMOND_ORE;
            case EMERALD -> Blocks.EMERALD_ORE;
        };
    }

    public static boolean isMineOutput(net.minecraft.world.item.ItemStack s) {
        for (MineResource r : MineResource.values()) {
            if (s.is(item(r))) {
                return true;
            }
        }
        return false;
    }
}
