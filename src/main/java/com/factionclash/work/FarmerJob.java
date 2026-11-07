package com.factionclash.work;

import com.factionclash.campaign.WorkplaceState;
import com.factionclash.logic.Affix;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.ToolKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Farmer that farms like a player (spec 8): breaks ripe crops so their real loot drops into the world, picks the
 * drops up, replants from the harvest, re-tills trampled soil with its hoe and carries the produce to storage.
 */
public class FarmerJob extends Job {
    enum State { CHECK, STORAGE, WORK, COLLECT }

    private State state = State.CHECK;
    private int cursor;
    private BlockPos cell;
    private int timer;
    private int collectTicks;

    public FarmerJob(FactionNpc npc) {
        super(npc);
    }

    private WorkplaceState wp() {
        return Workplaces.claim(npc, WorkplaceState.Type.FARM);
    }

    @Override
    public String workplaceInfo() {
        WorkplaceState wp = wp();
        return wp == null ? "no farm" : "Farm #" + wp.id + " (" + cellCount(wp) + " plots)";
    }

    private static int cellCount(WorkplaceState wp) {
        return (wp.extent.getX() - wp.origin.getX() + 1) * (wp.extent.getZ() - wp.origin.getZ() + 1);
    }

    private static BlockPos cellAt(WorkplaceState wp, int i) {
        int w = wp.extent.getX() - wp.origin.getX() + 1;
        return wp.origin.offset(i % w, 0, i / w);
    }

    @Override
    protected int keep(ItemStack s) {
        if (s.is(Items.WHEAT_SEEDS)) {
            return 24;
        }
        if (s.is(Items.CARROT) || s.is(Items.POTATO) || s.is(Items.BEETROOT_SEEDS)) {
            return 6;
        }
        return 0;
    }

    private int produce() {
        return Inv.count(npc.inventory(), s -> s.is(Items.WHEAT) || s.is(Items.CARROT) || s.is(Items.POTATO)
                || s.is(Items.BEETROOT) || s.is(Items.WHEAT_SEEDS) || s.is(Items.POISONOUS_POTATO));
    }

    @Override
    protected void run() {
        WorkplaceState wp = wp();
        if (wp == null) {
            status("No free farm", "Idle");
            waitFor(200);
            return;
        }
        if (state != State.STORAGE) {
            BlockWork.pickUp(npc, x -> true);
        }
        switch (state) {
            case CHECK -> {
                if (Inv.freeSlots(npc.inventory()) <= 1 || produce() >= 96) {
                    state = State.STORAGE;
                    return;
                }
                if (BlockWork.nearestItem(npc, new net.minecraft.world.phys.AABB(wp.origin).minmax(new net.minecraft.world.phys.AABB(wp.extent))
                        .inflate(2, 2, 2), x -> true) != null && Inv.freeSlots(npc.inventory()) > 0) {
                    collectTicks = 0;
                    state = State.COLLECT;
                    return;
                }
                cell = findWork(wp);
                if (cell == null) {
                    if (hasCargo() && produce() >= 16) {
                        state = State.STORAGE;
                        return;
                    }
                    status("Crops are growing", "Waiting");
                    waitFor(200);
                    return;
                }
                timer = 0;
                state = State.WORK;
            }
            case STORAGE -> {
                if (!atStorage()) {
                    return;
                }
                if (unloadNow() == Unload.PARTIAL_FULL && Inv.freeSlots(npc.inventory()) < 2) {
                    status("Storehouse full - holding produce", "Waiting");
                    waitFor(200);
                    return;
                }
                upgradeEquipment();
                if (Inv.count(npc.inventory(), s -> s.is(Items.WHEAT_SEEDS)) < 8) {
                    takeFromStorage(s -> s.is(Items.WHEAT_SEEDS), 16);
                }
                // a few carrots are planted too, so the pigs have something to be bred with
                if (Inv.count(npc.inventory(), s -> s.is(Items.CARROT)) < 2) {
                    takeFromStorage(s -> s.is(Items.CARROT), 4);
                }
                state = State.CHECK;
            }
            case WORK -> work(wp);
            case COLLECT -> {
                net.minecraft.world.entity.item.ItemEntity item = BlockWork.nearestItem(npc,
                        new net.minecraft.world.phys.AABB(wp.origin).minmax(new net.minecraft.world.phys.AABB(wp.extent)).inflate(2, 2, 2), x -> true);
                if (item == null || ++collectTicks > 200 || Inv.freeSlots(npc.inventory()) == 0) {
                    state = State.CHECK;
                    return;
                }
                status("Picking up the harvest", "Collecting");
                goTo(item.blockPosition(), 0.8, "the harvest");
            }
        }
    }

    /** Next cell needing attention: ripe crop, empty farmland, or trampled soil. */
    private BlockPos findWork(WorkplaceState wp) {
        int n = cellCount(wp);
        for (int k = 0; k < n; k++) {
            int i = (cursor + k) % n;
            BlockPos soil = cellAt(wp, i);
            BlockState s = level().getBlockState(soil);
            BlockState c = level().getBlockState(soil.above());
            boolean ripe = c.getBlock() instanceof CropBlock crop && crop.isMaxAge(c);
            boolean empty = s.is(Blocks.FARMLAND) && c.isAir() && seedFor(wp, soil) != null;
            boolean trampled = s.is(Blocks.DIRT) && c.isAir() && ToolKind.HOE.matches(npc.tool());
            if (ripe || empty || trampled) {
                cursor = (i + 1) % n;
                return soil;
            }
        }
        return null;
    }

    /** Replant the crop grown in neighbouring cells, falling back to whatever seed the farmer carries. */
    private Item seedFor(WorkplaceState wp, BlockPos soil) {
        // the first row is the carrot row (feed for the pigs) whenever the farmer has carrots to plant
        if (soil.getZ() == wp.origin.getZ() && Inv.count(npc.inventory(), s -> s.is(Items.CARROT)) > 0) {
            return Items.CARROT;
        }
        for (BlockPos n : List.of(soil.north().above(), soil.south().above(), soil.east().above(), soil.west().above())) {
            Item seed = seedOf(level().getBlockState(n));
            if (seed != null && Inv.count(npc.inventory(), s -> s.is(seed)) > 0) {
                return seed;
            }
        }
        for (Item seed : List.of(Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT_SEEDS)) {
            if (Inv.count(npc.inventory(), s -> s.is(seed)) > 0) {
                return seed;
            }
        }
        return null;
    }

    private static Item seedOf(BlockState crop) {
        if (crop.is(Blocks.WHEAT)) {
            return Items.WHEAT_SEEDS;
        }
        if (crop.is(Blocks.CARROTS)) {
            return Items.CARROT;
        }
        if (crop.is(Blocks.POTATOES)) {
            return Items.POTATO;
        }
        if (crop.is(Blocks.BEETROOTS)) {
            return Items.BEETROOT_SEEDS;
        }
        return null;
    }

    private static Block cropOf(Item seed) {
        if (seed == Items.CARROT) {
            return Blocks.CARROTS;
        }
        if (seed == Items.POTATO) {
            return Blocks.POTATOES;
        }
        if (seed == Items.BEETROOT_SEEDS) {
            return Blocks.BEETROOTS;
        }
        return Blocks.WHEAT;
    }

    private void work(WorkplaceState wp) {
        if (!goTo(cell, 3.0, "the farm")) {
            return;
        }
        BlockPos crop = cell.above();
        lookAt(crop);
        if (++timer < 15) {
            status("Tending farm #" + wp.id, "Farming");
            return;
        }
        timer = 0;
        BlockState soil = level().getBlockState(cell);
        BlockState c = level().getBlockState(crop);
        Item replant = seedOf(c);
        if (c.getBlock() instanceof CropBlock cb && cb.isMaxAge(c)) {
            if (Inv.freeSlots(npc.inventory()) == 0) {
                state = State.STORAGE;
                return;
            }
            // broken like a player breaks it: the crop's own loot table drops into the world
            BlockWork.breakNow(npc, crop);
            if (level().random.nextDouble() < npc.affix(com.factionclash.logic.Affix.GREEN_THUMB)) {
                BlockWork.dropExtra(npc, c, crop);
            }
            npc.awardXp(1);
            wp.totalOutput++;
            data().setDirty();
            c = Blocks.AIR.defaultBlockState();
        }
        if (soil.is(Blocks.DIRT) || soil.is(Blocks.GRASS_BLOCK)) {
            if (ToolKind.HOE.matches(npc.tool()) && level().getBlockState(crop).isAir()) {
                level().setBlock(cell, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
                level().playSound(null, cell, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                npc.swing(InteractionHand.MAIN_HAND);
                soil = level().getBlockState(cell);
            }
        }
        if (soil.is(Blocks.FARMLAND) && level().getBlockState(crop).isAir()) {
            Item seed = replant != null && Inv.count(npc.inventory(), s -> s.is(replant)) > 0 ? replant : seedFor(wp, cell);
            if (seed != null && Inv.consume(npc.inventory(), s -> s.is(seed), 1)) {
                level().setBlock(crop, cropOf(seed).defaultBlockState(), Block.UPDATE_ALL);
                level().playSound(null, crop, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
                npc.swing(InteractionHand.MAIN_HAND);
            }
        }
        state = State.CHECK;
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state.name());
        tag.putInt("cursor", cursor);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            state = State.CHECK;
        }
        if (state == State.WORK) {
            state = State.CHECK;
        }
        cursor = tag.getInt("cursor");
    }
}
