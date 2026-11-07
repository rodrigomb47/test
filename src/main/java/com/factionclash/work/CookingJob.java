package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.npc.FactionNpc;
import com.factionclash.storage.FactionStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Shared furnace-station work for chefs (smoker) and smelters (furnaces): fetch real inputs and fuel from
 * storage, load the block entity, wait for it to process, collect output and deliver it.
 */
public abstract class CookingJob extends Job {
    enum State { STORAGE, STATION }

    protected State state = State.STORAGE;
    protected int timer;

    protected CookingJob(FactionNpc npc) {
        super(npc);
    }

    protected abstract RecipeType<?> recipeType();

    protected abstract List<String> stationKeys();

    protected abstract boolean wantsInput(ItemStack s);

    protected abstract String verb();

    /** What the station processes, for status text ("cook", "smelt"). */
    protected abstract String noun();

    /** Extra work done while waiting at the station (chef bakes bread). Returns true if it did something. */
    protected boolean sideWork() {
        return false;
    }

    /** Extra items to fetch at storage. */
    protected void fetchExtras() {
    }

    protected boolean hasExtras() {
        return false;
    }

    protected List<AbstractFurnaceBlockEntity> stations() {
        List<AbstractFurnaceBlockEntity> out = new ArrayList<>();
        BaseState b = base();
        if (b == null) {
            return out;
        }
        for (String k : stationKeys()) {
            AbstractFurnaceBlockEntity f = FurnaceWork.furnace(level(), b.point(k));
            if (f != null) {
                out.add(f);
            }
        }
        return out;
    }

    protected Predicate<ItemStack> fuel() {
        return s -> FurnaceWork.isFuel(s, recipeType()) && !s.is(ItemTags.SAPLINGS) && !s.is(Items.STICK);
    }

    @Override
    public String workplaceInfo() {
        return verb() + " station in the workshop (" + stations().size() + " unit(s))";
    }

    @Override
    protected void run() {
        if (base() == null) {
            status("No home base", "Idle");
            waitFor(400);
            return;
        }
        if (state == State.STORAGE) {
            storage();
        } else {
            station();
        }
    }

    private void storage() {
        if (!atStorage()) {
            return;
        }
        if (unloadNow() == Unload.PARTIAL_FULL && Inv.freeSlots(npc.inventory()) < 3) {
            status("Storehouse full", "Waiting");
            waitFor(200);
            return;
        }
        upgradeEquipment();
        int inputs = takeFromStorage(s -> wantsInput(s) && FurnaceWork.cookable(level(), recipeType(), s), 32);
        boolean stationsBusy = stations().stream().anyMatch(f -> !f.getItem(0).isEmpty() || !f.getItem(2).isEmpty());
        if (inputs > 0 || stationsBusy) {
            if (Inv.count(npc.inventory(), fuel()) < 4) {
                int got = takeFromStorage(s -> s.is(Items.COAL) || s.is(Items.CHARCOAL), 6);
                if (got == 0 && FactionStorage.count(level(), base(), s -> s.is(ItemTags.LOGS)) > 24) {
                    takeFromStorage(s -> s.is(ItemTags.LOGS), 4);
                }
            }
        }
        fetchExtras();
        if (inputs == 0 && !stationsBusy && !hasExtras() && Inv.count(npc.inventory(), s -> wantsInput(s)) == 0) {
            status("Nothing to " + noun() + " - waiting for ingredients", "Idle");
            waitFor(400);
            return;
        }
        state = State.STATION;
        timer = 0;
    }

    private void station() {
        BlockPos stand = base().point(BaseState.WORKSHOP_STAND);
        if (!goTo(stand, 2.0, "the workshop")) {
            return;
        }
        List<AbstractFurnaceBlockEntity> list = stations();
        if (list.isEmpty()) {
            status("The " + verb().toLowerCase() + " station is missing - service unavailable", "Blocked");
            waitFor(400);
            state = State.STORAGE;
            return;
        }
        if (++timer % 20 != 0) {
            return;
        }
        boolean pending = false;
        for (AbstractFurnaceBlockEntity f : list) {
            lookAt(f.getBlockPos());
            if (FurnaceWork.collect(f, npc.inventory()) > 0) {
                npc.swing(InteractionHand.MAIN_HAND);
            }
            FurnaceWork.loadInput(f, npc.inventory(), s -> wantsInput(s) && FurnaceWork.cookable(level(), recipeType(), s));
            if (!f.getItem(0).isEmpty() && f.getItem(1).isEmpty() && !FurnaceWork.lit(f)) {
                if (FurnaceWork.loadFuel(f, npc.inventory(), fuel(), 4) == 0) {
                    status(verb() + " station needs fuel", "Waiting");
                }
            }
            if (!f.getItem(0).isEmpty() || !f.getItem(2).isEmpty()) {
                pending = true;
            }
        }
        boolean side = sideWork();
        if (!pending && !side) {
            state = State.STORAGE;
            return;
        }
        int left = list.stream().mapToInt(f -> f.getItem(0).getCount()).sum();
        status(verb() + " (" + left + " in progress)", verb());
        if (Inv.freeSlots(npc.inventory()) == 0) {
            state = State.STORAGE;
        }
        if (pending && list.stream().allMatch(f -> !FurnaceWork.lit(f) && f.getItem(1).isEmpty() && !f.getItem(0).isEmpty())
                && Inv.count(npc.inventory(), fuel()) == 0) {
            state = State.STORAGE;
            waitFor(200);
        }
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state.name());
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            state = State.STORAGE;
        }
    }
}
