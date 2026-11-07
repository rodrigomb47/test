package com.factionclash.work;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Small helpers for NPC backpacks. */
public final class Inv {
    private Inv() {
    }

    public static int count(Container c, Predicate<ItemStack> f) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && f.test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    public static List<ItemStack> remove(Container c, Predicate<ItemStack> f, int amount) {
        List<ItemStack> out = new ArrayList<>();
        int rem = amount;
        for (int i = 0; i < c.getContainerSize() && rem > 0; i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && f.test(s)) {
                int take = Math.min(rem, s.getCount());
                out.add(c.removeItem(i, take));
                rem -= take;
            }
        }
        c.setChanged();
        return out;
    }

    /** Removes exactly {@code amount} or nothing. */
    public static boolean consume(Container c, Predicate<ItemStack> f, int amount) {
        if (count(c, f) < amount) {
            return false;
        }
        remove(c, f, amount);
        return true;
    }

    public static ItemStack first(Container c, Predicate<ItemStack> f) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && f.test(s)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Space available for this exact item. */
    public static int room(Container c, ItemStack stack) {
        int room = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) {
                room += stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(s, stack)) {
                room += s.getMaxStackSize() - s.getCount();
            }
        }
        return room;
    }

    public static boolean fits(Container c, ItemStack stack) {
        return room(c, stack) >= stack.getCount();
    }

    public static int freeSlots(Container c) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public static int totalItems(Container c) {
        return count(c, s -> true);
    }
}
