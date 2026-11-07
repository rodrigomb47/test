package com.factionclash.work;

import com.factionclash.registry.ModBlocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.Set;
import java.util.function.Predicate;

/**
 * What a planned block costs. Style comes from design, not material locks (spec 11): when the exact block is
 * unavailable, any member of its material group may be used (e.g. cobblestone for styled stone walls).
 */
public final class MaterialRules {
    public enum Group {
        STONE("Stone blocks"), PLANKS("Planks"), LOGS("Logs"), STONE_STAIRS("Stone stairs"), WOOD_STAIRS("Wooden stairs"),
        FENCES("Fences"), GLASS("Glass"), DIRT("Dirt"), TORCH("Torches"), DOOR("Doors"), CHEST("Chests"),
        BEDROLL("Bedrolls"), EXACT("Special");

        public final String label;

        Group(String label) {
            this.label = label;
        }
    }

    /** Requirement for one plan entry; {@code count} 0 means free (e.g. the upper half of a door). */
    public record Need(Group group, Item exact, int count) {
        public String key() {
            return group == Group.EXACT ? "exact:" + exact : group.name();
        }

        public String label() {
            return group == Group.EXACT ? exact.getDescription().getString() : group.label;
        }
    }

    private static final Set<Block> STONE_BLOCKS = Set.of(Blocks.COBBLESTONE, Blocks.STONE, Blocks.STONE_BRICKS,
            Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_STONE_BRICKS, Blocks.BRICKS, Blocks.DEEPSLATE_TILES,
            Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.SMOOTH_SANDSTONE,
            Blocks.SANDSTONE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.COBBLED_DEEPSLATE,
            Blocks.POLISHED_ANDESITE, Blocks.CRACKED_STONE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.BLACKSTONE);

    private MaterialRules() {
    }

    public static Need need(BlockState s) {
        Block b = s.getBlock();
        if (s.isAir() || b == Blocks.WATER || b == Blocks.LAVA) {
            return new Need(Group.EXACT, Items.AIR, 0);
        }
        if (b instanceof DoorBlock) {
            return new Need(Group.DOOR, Items.OAK_DOOR, s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? 0 : 1);
        }
        if (b instanceof BedBlock) {
            return new Need(Group.EXACT, b.asItem(), s.getValue(BedBlock.PART) == BedPart.HEAD ? 0 : 1);
        }
        if (b == Blocks.TORCH || b == Blocks.WALL_TORCH) {
            return new Need(Group.TORCH, Items.TORCH, 1);
        }
        if (b instanceof SignBlock) {
            return new Need(Group.PLANKS, Items.OAK_PLANKS, 2);
        }
        if (b == ModBlocks.REPORTING_STATION.get() || b == ModBlocks.WORLD_MAP_BOARD.get()) {
            return new Need(Group.PLANKS, Items.OAK_PLANKS, 8);
        }
        if (b == ModBlocks.BEDROLL.get()) {
            return new Need(Group.BEDROLL, b.asItem(), 1);
        }
        if (b == Blocks.CHEST) {
            return new Need(Group.CHEST, Items.CHEST, 1);
        }
        if (b == Blocks.GRASS_BLOCK || b == Blocks.DIRT || b == Blocks.DIRT_PATH || b == Blocks.FARMLAND || b == Blocks.COARSE_DIRT) {
            return new Need(Group.DIRT, Items.DIRT, 1);
        }
        if (STONE_BLOCKS.contains(b)) {
            return new Need(Group.STONE, b.asItem(), 1);
        }
        if (s.is(BlockTags.PLANKS)) {
            return new Need(Group.PLANKS, b.asItem(), 1);
        }
        if (s.is(BlockTags.LOGS)) {
            return new Need(Group.LOGS, b.asItem(), 1);
        }
        if (s.is(BlockTags.WOODEN_STAIRS)) {
            return new Need(Group.WOOD_STAIRS, b.asItem(), 1);
        }
        if (s.is(BlockTags.STAIRS)) {
            return new Need(Group.STONE_STAIRS, b.asItem(), 1);
        }
        if (s.is(BlockTags.WOODEN_FENCES)) {
            return new Need(Group.FENCES, b.asItem(), 1);
        }
        if (b == Blocks.GLASS || b == Blocks.GLASS_PANE) {
            return new Need(Group.GLASS, b.asItem(), 1);
        }
        Item item = b.asItem();
        return new Need(Group.EXACT, item, item == Items.AIR ? 0 : 1);
    }

    /** Items that satisfy a need, exact item preferred by callers. */
    public static Predicate<ItemStack> accepts(Need n) {
        return switch (n.group()) {
            case STONE -> s -> s.getItem() instanceof BlockItem bi && STONE_BLOCKS.contains(bi.getBlock());
            case PLANKS -> s -> s.is(ItemTags.PLANKS);
            case LOGS -> s -> s.is(ItemTags.LOGS);
            case WOOD_STAIRS -> s -> s.is(ItemTags.WOODEN_STAIRS);
            case STONE_STAIRS -> s -> s.is(ItemTags.STAIRS) && !s.is(ItemTags.WOODEN_STAIRS);
            case FENCES -> s -> s.is(ItemTags.WOODEN_FENCES);
            case GLASS -> s -> s.is(Items.GLASS) || s.is(Items.GLASS_PANE);
            case DIRT -> s -> s.is(Items.DIRT) || s.is(Items.COARSE_DIRT) || s.is(Items.GRASS_BLOCK);
            case TORCH -> s -> s.is(Items.TORCH);
            case DOOR -> s -> s.is(ItemTags.WOODEN_DOORS);
            case CHEST -> s -> s.is(Items.CHEST);
            case BEDROLL -> s -> s.is(n.exact());
            case EXACT -> s -> s.is(n.exact());
        };
    }

    /** The block state actually placed when {@code used} substitutes for the planned state. */
    public static BlockState placedState(BlockState planned, ItemStack used) {
        Need n = need(planned);
        if (n.group() == Group.DIRT || n.group() == Group.TORCH || n.group() == Group.EXACT || n.group() == Group.BEDROLL
                || n.group() == Group.DOOR || n.group() == Group.CHEST || planned.getBlock() instanceof SignBlock
                || planned.is(ModBlocks.REPORTING_STATION.get()) || planned.is(ModBlocks.WORLD_MAP_BOARD.get())) {
            return planned;
        }
        if (used.getItem() instanceof BlockItem bi && bi.getBlock() != planned.getBlock()) {
            return bi.getBlock().withPropertiesOf(planned);
        }
        return planned;
    }

    /** Raw materials a builder can turn into a group at the crafting table. */
    public static Crafting.Recipe craftFor(Group g) {
        return switch (g) {
            case PLANKS -> Crafting.PLANKS;
            case STONE_STAIRS -> Crafting.COBBLE_STAIRS;
            case FENCES -> Crafting.FENCES;
            case DOOR -> Crafting.DOOR;
            case CHEST -> Crafting.CHEST;
            case TORCH -> Crafting.TORCHES;
            case BEDROLL -> Crafting.bedroll(ModBlocks.BEDROLL.get().asItem());
            default -> null;
        };
    }
}
