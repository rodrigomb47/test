package com.factionclash.work;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.function.Predicate;

/** Operates real furnace/smoker block entities: slot 0 input, 1 fuel, 2 result. */
public final class FurnaceWork {
    private FurnaceWork() {
    }

    public static boolean cookable(ServerLevel level, RecipeType<?> type, ItemStack s) {
        if (s.isEmpty()) {
            return false;
        }
        if (type == RecipeType.SMOKING) {
            return level.getRecipeManager().getRecipeFor(RecipeType.SMOKING, new SingleRecipeInput(s), level).isPresent();
        }
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(s), level).isPresent();
    }

    public static boolean isFuel(ItemStack s, RecipeType<?> type) {
        return !s.isEmpty() && s.getBurnTime(type) > 0 && !s.is(Items.LAVA_BUCKET);
    }

    public static AbstractFurnaceBlockEntity furnace(ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (pos == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity f ? f : null;
    }

    public static boolean lit(AbstractFurnaceBlockEntity f) {
        return f.getBlockState().hasProperty(AbstractFurnaceBlock.LIT) && f.getBlockState().getValue(AbstractFurnaceBlock.LIT);
    }

    /** Moves cooked output into the backpack. Returns the number of items taken. */
    public static int collect(Container furnace, Container inv) {
        ItemStack out = furnace.getItem(2);
        if (out.isEmpty()) {
            return 0;
        }
        int room = Inv.room(inv, out);
        int take = Math.min(room, out.getCount());
        if (take <= 0) {
            return 0;
        }
        Crafting.addAll(inv, furnace.removeItem(2, take));
        furnace.setChanged();
        return take;
    }

    public static int loadInput(Container furnace, Container inv, Predicate<ItemStack> filter) {
        ItemStack in = furnace.getItem(0);
        int moved = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !filter.test(s)) {
                continue;
            }
            if (in.isEmpty()) {
                furnace.setItem(0, s.copy());
                moved += s.getCount();
                inv.setItem(i, ItemStack.EMPTY);
                in = furnace.getItem(0);
            } else if (ItemStack.isSameItemSameComponents(in, s) && in.getCount() < in.getMaxStackSize()) {
                int m = Math.min(s.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(m);
                s.shrink(m);
                moved += m;
            }
        }
        furnace.setChanged();
        inv.setChanged();
        return moved;
    }

    public static int loadFuel(Container furnace, Container inv, Predicate<ItemStack> fuel, int max) {
        ItemStack slot = furnace.getItem(1);
        int moved = 0;
        for (int i = 0; i < inv.getContainerSize() && moved < max; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !fuel.test(s)) {
                continue;
            }
            if (slot.isEmpty()) {
                int m = Math.min(max - moved, s.getCount());
                furnace.setItem(1, s.split(m));
                moved += m;
                slot = furnace.getItem(1);
            } else if (ItemStack.isSameItemSameComponents(slot, s) && slot.getCount() < slot.getMaxStackSize()) {
                int m = Math.min(Math.min(max - moved, s.getCount()), slot.getMaxStackSize() - slot.getCount());
                slot.grow(m);
                s.shrink(m);
                moved += m;
            }
        }
        furnace.setChanged();
        inv.setChanged();
        return moved;
    }
}
