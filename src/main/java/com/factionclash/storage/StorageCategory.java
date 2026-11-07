package com.factionclash.storage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;

/** Storage categories of a faction warehouse (spec 10). GENERAL is overflow for full category chests. */
public enum StorageCategory {
    FOOD("Food"),
    RAW("Raw Resources"),
    BUILDING("Building Supplies"),
    TOOLS("Tools"),
    WEAPONS("Weapons"),
    ARMOR("Armor"),
    SPECIAL("Special Items"),
    GENERAL("General");

    public final String label;

    StorageCategory(String label) {
        this.label = label;
    }

    public static StorageCategory classify(ItemStack stack) {
        if (stack.isEmpty()) {
            return GENERAL;
        }
        if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.ENCHANTED_BOOK)
                || stack.is(Items.EMERALD) || stack.is(Items.DIAMOND) || stack.is(Items.NETHERITE_INGOT)) {
            return SPECIAL;
        }
        if (stack.get(DataComponents.FOOD) != null) {
            return FOOD;
        }
        if (stack.getItem() instanceof ArmorItem || stack.getItem() instanceof ShieldItem) {
            return ARMOR;
        }
        if (stack.is(ItemTags.SWORDS) || stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof TridentItem || stack.getItem() instanceof ArrowItem) {
            return WEAPONS;
        }
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)
                || stack.is(Items.SHEARS) || stack.is(Items.FISHING_ROD) || stack.is(Items.FLINT_AND_STEEL)) {
            return TOOLS;
        }
        if (stack.is(ItemTags.SAPLINGS) || stack.is(ItemTags.LOGS) || stack.is(Items.WHEAT_SEEDS) || stack.is(Items.BEETROOT_SEEDS)
                || stack.is(Items.COAL_ORE) || stack.is(Items.IRON_ORE)) {
            return RAW;
        }
        if (stack.getItem() instanceof BlockItem) {
            return BUILDING;
        }
        return RAW;
    }
}
