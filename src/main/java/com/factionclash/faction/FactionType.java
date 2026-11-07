package com.factionclash.faction;

import net.minecraft.ChatFormatting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** The established faction identities. Styles drive base appearance only; any faction may use any material. */
public enum FactionType {
    EMBER_LEGION("ember_legion", "Ember Legion", ChatFormatting.RED, 0xC0392B, true,
            new Style(Blocks.BRICKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DEEPSLATE_TILES,
                    Blocks.DEEPSLATE_TILE_STAIRS, Blocks.RED_WOOL, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.RED_CARPET)),
    GROVE_COVENANT("grove_covenant", "Grove Covenant", ChatFormatting.GREEN, 0x27AE60, true,
            new Style(Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS,
                    Blocks.SPRUCE_STAIRS, Blocks.GREEN_WOOL, Blocks.MOSSY_STONE_BRICKS, Blocks.GREEN_CARPET)),
    TIDE_ALLIANCE("tide_alliance", "Tide Alliance", ChatFormatting.AQUA, 0x2E86C1, true,
            new Style(Blocks.PRISMARINE_BRICKS, Blocks.BIRCH_LOG, Blocks.BIRCH_PLANKS, Blocks.DARK_PRISMARINE,
                    Blocks.DARK_PRISMARINE_STAIRS, Blocks.LIGHT_BLUE_WOOL, Blocks.SMOOTH_SANDSTONE, Blocks.LIGHT_BLUE_CARPET)),
    STONEGUARD("stoneguard", "Stoneguard", ChatFormatting.GRAY, 0x7F8C8D, true,
            new Style(Blocks.STONE_BRICKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.COBBLESTONE,
                    Blocks.STONE_BRICK_STAIRS, Blocks.GRAY_WOOL, Blocks.STONE_BRICKS, Blocks.GRAY_CARPET)),
    HOLLOW("hollow", "The Hollow", ChatFormatting.DARK_PURPLE, 0x5B2C6F, false,
            new Style(Blocks.MOSSY_COBBLESTONE, Blocks.DARK_OAK_LOG, Blocks.COBBLESTONE, Blocks.COBBLESTONE,
                    Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.PURPLE_WOOL, Blocks.MOSSY_COBBLESTONE, Blocks.PURPLE_CARPET));

    /** Preferred building blocks for a faction's generated structures. */
    public record Style(Block wall, Block frame, Block floor, Block roof, Block roofStairs, Block banner,
                        Block perimeter, Block carpet) {
    }

    public final String id;
    public final String displayName;
    public final ChatFormatting color;
    public final int rgb;
    public final boolean human;
    public final Style style;

    FactionType(String id, String displayName, ChatFormatting color, int rgb, boolean human, Style style) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.rgb = rgb;
        this.human = human;
        this.style = style;
    }

    public static FactionType byId(String id) {
        for (FactionType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    public static FactionType[] humans() {
        return new FactionType[]{EMBER_LEGION, GROVE_COVENANT, TIDE_ALLIANCE, STONEGUARD};
    }
}
