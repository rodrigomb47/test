package com.factionclash.storage;

import com.factionclash.campaign.BaseState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Physical faction storage: only the chests registered on a base count (spec 10), so unrelated player chests
 * are never treated as faction stock. Each chest half is handled as its own 27-slot container.
 */
public final class FactionStorage {
    private FactionStorage() {
    }

    public static Container container(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container c ? c : null;
    }

    private static List<Container> containers(ServerLevel level, List<BlockPos> positions) {
        List<Container> out = new ArrayList<>();
        for (BlockPos p : positions) {
            Container c = container(level, p);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    public static List<Container> all(ServerLevel level, BaseState base) {
        return containers(level, base.allStoragePositions());
    }

    public static int count(ServerLevel level, BaseState base, Predicate<ItemStack> filter) {
        int n = 0;
        for (Container c : all(level, base)) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && filter.test(s)) {
                    n += s.getCount();
                }
            }
        }
        return n;
    }

    /** Finds (without removing) the best matching stack by score; returns empty if none. */
    public static ItemStack findBest(ServerLevel level, BaseState base, Predicate<ItemStack> filter,
                                     java.util.function.ToIntFunction<ItemStack> score) {
        ItemStack best = ItemStack.EMPTY;
        int bestScore = Integer.MIN_VALUE;
        for (Container c : all(level, base)) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && filter.test(s)) {
                    int sc = score.applyAsInt(s);
                    if (sc > bestScore) {
                        bestScore = sc;
                        best = s;
                    }
                }
            }
        }
        return best;
    }

    /** Removes up to {@code amount} matching items and returns them as real stacks. */
    public static List<ItemStack> extract(ServerLevel level, BaseState base, Predicate<ItemStack> filter, int amount) {
        List<ItemStack> out = new ArrayList<>();
        int remaining = amount;
        for (Container c : all(level, base)) {
            for (int i = 0; i < c.getContainerSize() && remaining > 0; i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && filter.test(s)) {
                    int take = Math.min(remaining, s.getCount());
                    out.add(c.removeItem(i, take));
                    remaining -= take;
                }
            }
            c.setChanged();
            if (remaining <= 0) {
                break;
            }
        }
        return out;
    }

    /** Removes exactly one item equal to {@code exact} (same item and components). */
    public static ItemStack extractExact(ServerLevel level, BaseState base, ItemStack exact) {
        List<ItemStack> got = extract(level, base, s -> ItemStack.isSameItemSameComponents(s, exact), 1);
        return got.isEmpty() ? ItemStack.EMPTY : got.get(0);
    }

    /**
     * Inserts a stack, preferring its category chests, then GENERAL, then any faction chest.
     * @return what did not fit (empty when fully stored). Nothing is ever deleted.
     */
    public static ItemStack insert(ServerLevel level, BaseState base, ItemStack stack) {
        return insert(level, base, stack, null);
    }

    /** As {@link #insert(ServerLevel, BaseState, ItemStack)}, crediting {@code by} with what reaches the faction. */
    public static ItemStack insert(ServerLevel level, BaseState base, ItemStack stack, String by) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        com.factionclash.achieve.FactionAchievements.obtained(level.getServer(), base.faction, stack, by);
        ItemStack rest = stack.copy();
        StorageCategory cat = StorageCategory.classify(rest);
        rest = insertInto(containers(level, base.storage(cat)), rest);
        if (!rest.isEmpty() && cat != StorageCategory.GENERAL) {
            rest = insertInto(containers(level, base.storage(StorageCategory.GENERAL)), rest);
        }
        if (!rest.isEmpty()) {
            rest = insertInto(all(level, base), rest);
        }
        return rest;
    }

    public static ItemStack insertInto(List<Container> targets, ItemStack stack) {
        ItemStack rest = stack;
        for (Container c : targets) {
            rest = insertInto(c, rest);
            if (rest.isEmpty()) {
                break;
            }
        }
        return rest;
    }

    public static ItemStack insertInto(Container c, ItemStack stack) {
        ItemStack rest = stack;
        // merge first
        for (int i = 0; i < c.getContainerSize() && !rest.isEmpty(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, rest) && s.getCount() < s.getMaxStackSize()) {
                int move = Math.min(rest.getCount(), Math.min(s.getMaxStackSize(), c.getMaxStackSize()) - s.getCount());
                if (move > 0) {
                    s.grow(move);
                    rest.shrink(move);
                }
            }
        }
        for (int i = 0; i < c.getContainerSize() && !rest.isEmpty(); i++) {
            if (c.getItem(i).isEmpty() && c.canPlaceItem(i, rest)) {
                c.setItem(i, rest.copy());
                rest = ItemStack.EMPTY;
            }
        }
        c.setChanged();
        return rest;
    }

    public static boolean hasSpaceFor(ServerLevel level, BaseState base, ItemStack stack) {
        int canTake = 0;
        for (Container c : all(level, base)) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty()) {
                    return true;
                }
                if (ItemStack.isSameItemSameComponents(s, stack)) {
                    canTake += s.getMaxStackSize() - s.getCount();
                    if (canTake >= stack.getCount()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Fraction of slots in use across all faction chests. */
    public static double fillRatio(ServerLevel level, BaseState base) {
        int used = 0, total = 0;
        for (Container c : all(level, base)) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                total++;
                if (!c.getItem(i).isEmpty()) {
                    used++;
                }
            }
        }
        return total == 0 ? 1.0 : (double) used / total;
    }
}
