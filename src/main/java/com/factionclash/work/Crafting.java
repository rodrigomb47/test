package com.factionclash.work;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Predicate;

/**
 * NPC crafting at a crafting table, using real inputs from the NPC's backpack and the vanilla recipe ratios.
 * Intermediate items (planks, sticks) are crafted when the NPC carries the raw material (spec 11: a builder
 * carrying wood is never idle for lack of planks).
 */
public final class Crafting {
    public record Input(Predicate<ItemStack> filter, int count, String label) {
    }

    public record Recipe(String name, List<Input> inputs, Item output, int outputCount) {
    }

    private Crafting() {
    }

    public static Input of(Item item, int count) {
        return new Input(s -> s.is(item), count, item.getDescription().getString());
    }

    public static Input of(TagKey<Item> tag, int count, String label) {
        return new Input(s -> s.is(tag), count, label);
    }

    public static final Predicate<ItemStack> FUEL_COAL = s -> s.is(Items.COAL) || s.is(Items.CHARCOAL);

    public static final Recipe PLANKS = new Recipe("planks", List.of(of(ItemTags.LOGS, 1, "Log")), Items.OAK_PLANKS, 4);
    public static final Recipe STICKS = new Recipe("sticks", List.of(of(ItemTags.PLANKS, 2, "Planks")), Items.STICK, 4);
    public static final Recipe TORCHES = new Recipe("torches", List.of(new Input(FUEL_COAL, 1, "Coal"), of(Items.STICK, 1)), Items.TORCH, 4);
    public static final Recipe COBBLE_STAIRS = new Recipe("cobblestone stairs", List.of(of(Items.COBBLESTONE, 6)), Items.COBBLESTONE_STAIRS, 4);
    public static final Recipe FENCES = new Recipe("fences", List.of(of(ItemTags.PLANKS, 4, "Planks"), of(Items.STICK, 2)), Items.OAK_FENCE, 3);
    public static final Recipe BREAD = new Recipe("bread", List.of(of(Items.WHEAT, 3)), Items.BREAD, 1);
    public static final Recipe CHEST = new Recipe("chest", List.of(of(ItemTags.PLANKS, 8, "Planks")), Items.CHEST, 1);
    public static final Recipe DOOR = new Recipe("doors", List.of(of(ItemTags.PLANKS, 6, "Planks")), Items.OAK_DOOR, 3);
    public static final Recipe ARROWS = new Recipe("arrows", List.of(of(Items.FLINT, 1), of(Items.STICK, 1), of(Items.FEATHER, 1)), Items.ARROW, 4);
    public static final Recipe BOW = new Recipe("bow", List.of(of(Items.STICK, 3), of(Items.STRING, 3)), Items.BOW, 1);

    public static Recipe bedroll(Item bedroll) {
        return new Recipe("bedroll", List.of(of(Items.WHEAT, 3)), bedroll, 1);
    }

    public static Recipe tool(String name, Item material, int materialCount, int sticks, Item output) {
        return new Recipe(name, sticks > 0 ? List.of(of(material, materialCount), of(Items.STICK, sticks))
                : List.of(of(material, materialCount)), output, 1);
    }

    public static boolean canCraft(Container inv, Recipe r, int times) {
        for (Input in : r.inputs()) {
            if (Inv.count(inv, in.filter()) < in.count() * times) {
                return false;
            }
        }
        return true;
    }

    /** Crafts up to {@code times} batches; returns how many were crafted. Output that does not fit stays uncrafted. */
    public static int craft(Container inv, Recipe r, int times) {
        int done = 0;
        for (int i = 0; i < times; i++) {
            if (!canCraft(inv, r, 1)) {
                break;
            }
            ItemStack out = new ItemStack(r.output(), r.outputCount());
            // inputs free up space, so check room after a simulated removal: require room for the output
            if (Inv.room(inv, out) < out.getCount() && Inv.freeSlots(inv) == 0) {
                break;
            }
            for (Input in : r.inputs()) {
                Inv.remove(inv, in.filter(), in.count());
            }
            ItemStack rest = addAll(inv, out);
            if (!rest.isEmpty()) {
                // should not happen; never delete: put inputs back is impossible, so keep output in any slot
                for (int s = 0; s < inv.getContainerSize() && !rest.isEmpty(); s++) {
                    if (inv.getItem(s).isEmpty()) {
                        inv.setItem(s, rest);
                        rest = ItemStack.EMPTY;
                    }
                }
            }
            done++;
        }
        return done;
    }

    public static ItemStack addAll(Container inv, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int i = 0; i < inv.getContainerSize() && !rest.isEmpty(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, rest)) {
                int move = Math.min(rest.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(move);
                rest.shrink(move);
            }
        }
        for (int i = 0; i < inv.getContainerSize() && !rest.isEmpty(); i++) {
            if (inv.getItem(i).isEmpty()) {
                inv.setItem(i, rest.copy());
                rest = ItemStack.EMPTY;
            }
        }
        inv.setChanged();
        return rest;
    }

    /** Ensures at least {@code count} sticks, crafting planks from logs and sticks from planks as needed. */
    public static boolean ensureSticks(Container inv, int count) {
        while (Inv.count(inv, s -> s.is(Items.STICK)) < count) {
            if (!canCraft(inv, STICKS, 1) && !ensurePlanks(inv, 2)) {
                return false;
            }
            if (craft(inv, STICKS, 1) == 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean ensurePlanks(Container inv, int count) {
        while (Inv.count(inv, s -> s.is(ItemTags.PLANKS)) < count) {
            if (craft(inv, PLANKS, 1) == 0) {
                return false;
            }
        }
        return true;
    }

    /** Crafts {@code times} batches, first making any planks/sticks the recipe needs from carried logs. */
    public static int craftWithIntermediates(Container inv, Recipe r, int times) {
        int done = 0;
        for (int i = 0; i < times; i++) {
            for (Input in : r.inputs()) {
                if (in.label().equals("Planks")) {
                    ensurePlanks(inv, in.count());
                }
            }
            for (Input in : r.inputs()) {
                if (in.label().equals(Items.STICK.getDescription().getString())) {
                    ensureSticks(inv, in.count());
                }
            }
            if (craft(inv, r, 1) == 0) {
                break;
            }
            done++;
        }
        return done;
    }
}
