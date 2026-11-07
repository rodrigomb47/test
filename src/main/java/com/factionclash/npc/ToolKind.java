package com.factionclash.npc;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;

import java.util.List;

/** Kinds of equipment a role needs, with tier ranking and the crafting-material fallback used at recruitment. */
public enum ToolKind {
    NONE,
    PICKAXE,
    AXE,
    HOE,
    SHOVEL,
    SWORD,
    BOW;

    /** One ingredient line of a material payment. */
    public record Cost(Item item, int count) {
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (this) {
            case NONE -> false;
            case PICKAXE -> stack.is(ItemTags.PICKAXES);
            case AXE -> stack.is(ItemTags.AXES);
            case HOE -> stack.is(ItemTags.HOES);
            case SHOVEL -> stack.is(ItemTags.SHOVELS);
            case SWORD -> stack.is(ItemTags.SWORDS);
            case BOW -> stack.getItem() instanceof BowItem;
        };
    }

    /** 0 wood/gold, 1 stone, 2 iron, 3 diamond, 4 netherite; bows are tier 1. -1 for non-matching items. */
    public static int tierOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }
        if (stack.getItem() instanceof TieredItem tiered) {
            return tierRank(tiered.getTier());
        }
        if (stack.getItem() instanceof BowItem) {
            return 1;
        }
        return -1;
    }

    public static int tierRank(Tier tier) {
        if (tier == Tiers.STONE) {
            return 1;
        }
        if (tier == Tiers.IRON) {
            return 2;
        }
        if (tier == Tiers.DIAMOND) {
            return 3;
        }
        if (tier == Tiers.NETHERITE) {
            return 4;
        }
        return 0;
    }

    /** The cheapest item satisfying this kind at recruitment, produced from {@link #materialCost()}. */
    public ItemStack basicItem() {
        return switch (this) {
            case NONE -> ItemStack.EMPTY;
            case PICKAXE -> new ItemStack(Items.STONE_PICKAXE);
            case AXE -> new ItemStack(Items.STONE_AXE);
            case HOE -> new ItemStack(Items.STONE_HOE);
            case SHOVEL -> new ItemStack(Items.STONE_SHOVEL);
            case SWORD -> new ItemStack(Items.STONE_SWORD);
            case BOW -> new ItemStack(Items.BOW);
        };
    }

    /** Vanilla crafting ingredients of {@link #basicItem()}; charged instead of the finished item when none is stored. */
    public List<Cost> materialCost() {
        return switch (this) {
            case NONE -> List.of();
            case PICKAXE, AXE -> List.of(new Cost(Items.COBBLESTONE, 3), new Cost(Items.STICK, 2));
            case HOE -> List.of(new Cost(Items.COBBLESTONE, 2), new Cost(Items.STICK, 2));
            case SHOVEL -> List.of(new Cost(Items.COBBLESTONE, 1), new Cost(Items.STICK, 2));
            case SWORD -> List.of(new Cost(Items.COBBLESTONE, 2), new Cost(Items.STICK, 1));
            case BOW -> List.of(new Cost(Items.STICK, 3), new Cost(Items.STRING, 3));
        };
    }

    public static boolean isArrow(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ArrowItem;
    }
}
