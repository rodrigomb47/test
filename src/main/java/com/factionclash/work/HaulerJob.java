package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.npc.FactionNpc;
import com.factionclash.storage.FactionStorage;
import com.factionclash.storage.StorageCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Hauler / quartermaster: keeps the base tidy the way a player tidies a storehouse. It picks up items lying
 * around inside the walls (loot from fights, things dropped and forgotten) and re-sorts the storehouse, carrying
 * stacks that ended up in the overflow chest or the wrong chest to the chests of their own category.
 */
public class HaulerJob extends Job {
    enum State { CHECK, PICKUP, SORT, STORE, FUEL }

    private State state = State.CHECK;
    private BlockPos chest;
    private int slot = -1;
    private int pickupTicks;

    public HaulerJob(FactionNpc npc) {
        super(npc);
    }

    @Override
    public String workplaceInfo() {
        return switch (state) {
            case CHECK -> "Looking for work around the storehouse";
            case FUEL -> "Carrying fuel to the workshop furnaces";
            case PICKUP -> "Picking up items lying around the base";
            case SORT -> "Re-sorting the storehouse" + (chest == null ? "" : " (chest at " + chest.toShortString() + ")");
            case STORE -> "Carrying things to the storehouse";
        };
    }

    @Override
    protected void run() {
        BaseState b = base();
        if (b == null) {
            status("No home base", "Idle");
            waitFor(400);
            return;
        }
        switch (state) {
            case CHECK -> check(b);
            case PICKUP -> pickup(b);
            case SORT -> sort(b);
            case FUEL -> fuelRound(b);
            case STORE -> {
                if (!atStorage()) {
                    return;
                }
                if (unloadNow() == Unload.PARTIAL_FULL) {
                    status("The storehouse is full", "Waiting");
                    waitFor(400);
                }
                state = State.CHECK;
            }
        }
    }

    private void check(BaseState b) {
        if (cargoCount() > 0) {
            state = State.STORE;
            return;
        }
        if (looseItem(b) != null) {
            pickupTicks = 0;
            state = State.PICKUP;
            return;
        }
        if (findMisplaced(b)) {
            state = State.SORT;
            return;
        }
        if (stationsNeedFuel(b) && FactionStorage.count(level(), b, HaulerJob::coalLike) > 0) {
            state = State.FUEL;
            return;
        }
        status("The storehouse is in order", "Idle");
        waitFor(400);
    }

    // ------------------------------------------------------------------ loose items

    /** An item lying inside the walls, outside the farm and pen (their workers collect those themselves). */
    private ItemEntity looseItem(BaseState b) {
        BlockPos min = b.point(BaseState.MIN), max = b.point(BaseState.MAX);
        if (min == null || max == null) {
            return null;
        }
        AABB walls = new AABB(min.getX(), min.getY() - 4, min.getZ(), max.getX() + 1, max.getY() + 12, max.getZ() + 1);
        return BlockWork.nearestItem(npc, walls, s -> true, e -> e.getAge() > 200 && !unreachable.contains(e.getUUID())
                && !inWorkArea(b, e.blockPosition()));
    }

    /** Items it could not get to (on a roof, a wall top, behind a fence): left alone, like a player would. */
    private final java.util.Set<java.util.UUID> unreachable = new java.util.HashSet<>();
    private java.util.UUID chasing;
    private int chaseTicks;

    private boolean inWorkArea(BaseState b, BlockPos p) {
        for (int id : b.workplaces) {
            WorkplaceState w = data().workplace(id);
            if (w == null) {
                continue;
            }
            if (w.type == WorkplaceState.Type.FARM
                    && new AABB(w.origin).minmax(new AABB(w.extent)).inflate(2, 2, 2).contains(p.getCenter())) {
                return true;
            }
            if (w.type == WorkplaceState.Type.PEN && p.closerThan(w.origin, 7)) {
                return true;
            }
        }
        return false;
    }

    private void pickup(BaseState b) {
        ItemEntity item = looseItem(b);
        if (item == null || Inv.freeSlots(npc.inventory()) <= 2 || ++pickupTicks > 1200) {
            state = cargoCount() > 0 ? State.STORE : State.CHECK;
            return;
        }
        if (!item.getUUID().equals(chasing)) {
            chasing = item.getUUID();
            chaseTicks = 0;
        } else if (++chaseTicks > 200) {
            unreachable.add(chasing);
            if (unreachable.size() > 128) {
                unreachable.clear();
            }
            npc.navigator().reset();
            return;
        }
        status("Picking up " + item.getItem().getHoverName().getString() + " lying around the base", "Hauling");
        if (goTo(item.blockPosition(), 0.8, "an item lying around")) {
            BlockWork.pickUp(npc, s -> true);
        }
    }

    // ------------------------------------------------------------------ supply rounds

    private static boolean coalLike(ItemStack s) {
        return s.is(net.minecraft.world.item.Items.COAL) || s.is(net.minecraft.world.item.Items.CHARCOAL);
    }

    private List<net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity> stations(BaseState b) {
        List<net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity> out = new java.util.ArrayList<>();
        for (String k : new String[]{BaseState.FURNACE, BaseState.FURNACE_2, BaseState.SMOKER}) {
            var f = FurnaceWork.furnace(level(), b.point(k));
            if (f != null) {
                out.add(f);
            }
        }
        return out;
    }

    /** A workshop furnace or the smoker with fewer than 4 coal in its fuel slot (or none). */
    private boolean stationsNeedFuel(BaseState b) {
        return stations(b).stream().anyMatch(f -> f.getItem(1).isEmpty() || coalLike(f.getItem(1)) && f.getItem(1).getCount() < 4);
    }

    /**
     * Logistics: like a quartermaster, the hauler keeps the workshop's furnaces and smoker stocked with coal or
     * charcoal from the storehouse, so the smelter and the chef never stand at a cold station while fuel is stored.
     */
    private void fuelRound(BaseState b) {
        if (Inv.count(npc.inventory(), HaulerJob::coalLike) == 0) {
            if (!atStorage()) {
                return;
            }
            if (takeFromStorage(HaulerJob::coalLike, 16) == 0) {
                state = State.CHECK;
                return;
            }
        }
        if (!goTo(b.point(BaseState.WORKSHOP_STAND), 2.0, "the workshop")) {
            return;
        }
        status("Stocking the furnaces and the smoker with fuel", "Hauling");
        for (var f : stations(b)) {
            lookAt(f.getBlockPos());
            int want = 8 - (coalLike(f.getItem(1)) ? f.getItem(1).getCount() : f.getItem(1).isEmpty() ? 0 : 8);
            if (want > 0 && FurnaceWork.loadFuel(f, npc.inventory(), HaulerJob::coalLike, want) > 0) {
                npc.swing(InteractionHand.MAIN_HAND);
            }
        }
        state = State.STORE; // leftover fuel goes back
    }

    // ------------------------------------------------------------------ sorting

    /** Finds a stack sitting in the overflow chest or a chest of another category whose own chests have room. */
    private boolean findMisplaced(BaseState b) {
        for (StorageCategory cat : StorageCategory.values()) {
            for (BlockPos pos : b.storage(cat)) {
                Container c = FactionStorage.container(level(), pos);
                if (c == null) {
                    continue;
                }
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack s = c.getItem(i);
                    if (s.isEmpty()) {
                        continue;
                    }
                    StorageCategory own = StorageCategory.classify(s);
                    if (own != cat && own != StorageCategory.GENERAL && roomIn(b, own, s)) {
                        chest = pos;
                        slot = i;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean roomIn(BaseState b, StorageCategory cat, ItemStack stack) {
        for (BlockPos pos : b.storage(cat)) {
            Container c = FactionStorage.container(level(), pos);
            if (c == null) {
                continue;
            }
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty() || (ItemStack.isSameItemSameComponents(s, stack) && s.getCount() + stack.getCount() <= s.getMaxStackSize())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void sort(BaseState b) {
        if (chest == null || slot < 0) {
            state = State.CHECK;
            return;
        }
        if (!goTo(chest, 2.5, "a storehouse chest")) {
            return;
        }
        Container c = FactionStorage.container(level(), chest);
        if (c != null && slot < c.getContainerSize() && !c.getItem(slot).isEmpty()) {
            ItemStack taken = c.removeItemNoUpdate(slot);
            c.setChanged();
            ItemStack rest = Crafting.addAll(npc.inventory(), taken);
            if (!rest.isEmpty()) {
                c.setItem(slot, rest);
            }
            lookAt(chest);
            npc.swing(InteractionHand.MAIN_HAND);
            status("Re-sorting " + taken.getHoverName().getString() + " into its own chest", "Hauling");
        }
        chest = null;
        slot = -1;
        state = State.STORE; // unloading puts each stack into the chests of its category
    }
}
