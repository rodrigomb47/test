package com.factionclash.work;

import com.factionclash.campaign.WorkplaceState;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.Affix;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.ToolKind;
import com.factionclash.storage.FactionStorage;
import com.factionclash.structure.Blueprint;
import com.factionclash.structure.Terrain;
import com.factionclash.structure.TreePlotLayout;
import com.factionclash.util.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tree-plot woodcutter (spec 8): fells grown trees in its fenced plot (real logs into the backpack), clears the
 * canopy (saplings and, for oak, apples), replants, and tends saplings with the vanilla growth step. Upgrades
 * enlarge the fence so the plot holds more trees and yields more apples.
 */
public class WoodcutterJob extends Job {
    enum State { CHECK, STORAGE, CRAFT, CHOP, PLANT, TEND, UPGRADE }

    private static final int CARGO_LIMIT = 48;
    private State state = State.CHECK;
    private BlockPos spot;
    private int progress;
    private int tendCursor;
    private long nextUpgradeCheck;
    private final List<BlockPos> logs = new ArrayList<>();

    public WoodcutterJob(FactionNpc npc) {
        super(npc);
    }

    private WorkplaceState wp() {
        return Workplaces.claim(npc, WorkplaceState.Type.TREE_PLOT);
    }

    @Override
    public String workplaceInfo() {
        WorkplaceState wp = wp();
        return wp == null ? "no tree plot" : "Tree plot #" + wp.id + " tier " + wp.tier + " (" + TreePlotLayout.spots(wp).size()
                + " trees)" + (wp.upgrading() ? " upgrading" : "");
    }

    @Override
    protected int keep(ItemStack s) {
        if (s.is(ItemTags.SAPLINGS)) {
            return 8;
        }
        WorkplaceState wp = wp();
        if (wp != null && wp.upgrading()) {
            if (s.is(Items.OAK_FENCE) || s.is(ItemTags.FENCES)) {
                return 256;
            }
            if (s.is(ItemTags.PLANKS) || s.is(ItemTags.LOGS)) {
                return 64;
            }
            if (s.is(Items.STICK)) {
                return 32;
            }
        }
        return 0;
    }

    private int cargo() {
        return Inv.count(npc.inventory(), s -> s.is(ItemTags.LOGS) || s.is(Items.APPLE) || s.is(Items.STICK));
    }

    private boolean hasAxe() {
        return ToolKind.AXE.matches(npc.tool());
    }

    @Override
    protected void run() {
        WorkplaceState wp = wp();
        if (wp == null) {
            status("No free tree plot", "Idle");
            waitFor(200);
            return;
        }
        switch (state) {
            case CHECK -> decide(wp);
            case STORAGE -> storage(wp);
            case CRAFT -> craftFences(wp);
            case CHOP -> chop(wp);
            case PLANT -> plant(wp);
            case TEND -> tend(wp);
            case UPGRADE -> upgrade(wp);
        }
    }

    private boolean isTree(BlockPos p) {
        return level().getBlockState(p).is(BlockTags.LOGS);
    }

    private void decide(WorkplaceState wp) {
        if (!hasAxe() || cargo() >= CARGO_LIMIT || Inv.freeSlots(npc.inventory()) == 0) {
            state = State.STORAGE;
            return;
        }
        if (wp.upgrading()) {
            state = State.UPGRADE;
            return;
        }
        List<BlockPos> spots = TreePlotLayout.spots(wp);
        for (BlockPos s : spots) {
            if (isTree(s)) {
                spot = s;
                logs.clear();
                state = State.CHOP;
                return;
            }
        }
        BlockPos orphan = orphanTree(wp, spots);
        if (orphan != null) {
            spot = orphan;
            logs.clear();
            state = State.CHOP;
            return;
        }
        for (BlockPos s : spots) {
            BlockState st = level().getBlockState(s);
            if (!(st.getBlock() instanceof SaplingBlock) && !isTree(s)) {
                if (Inv.count(npc.inventory(), x -> x.is(ItemTags.SAPLINGS)) == 0) {
                    state = State.STORAGE;
                    return;
                }
                spot = s;
                state = State.PLANT;
                return;
            }
        }
        for (int i = 0; i < spots.size(); i++) {
            BlockPos s = spots.get((tendCursor + i) % spots.size());
            if (level().getBlockState(s).getBlock() instanceof SaplingBlock) {
                tendCursor = (tendCursor + i + 1) % spots.size();
                spot = s;
                progress = 0;
                state = State.TEND;
                return;
            }
        }
        status("Plot is fully planted; waiting", "Waiting");
        waitFor(100);
    }

    /** Trees standing inside the plot but off the current planting grid (e.g. after an enlargement). */
    private BlockPos orphanTree(WorkplaceState wp, List<BlockPos> spots) {
        if (orphanCache != null && level().getBlockState(orphanCache).is(BlockTags.LOGS)) {
            return orphanCache;
        }
        orphanCache = null;
        if (now() < nextOrphanScan) {
            return null;
        }
        nextOrphanScan = now() + 400;
        java.util.Set<BlockPos> grid = new HashSet<>(spots);
        int h = TreePlotLayout.half(wp.tier), d = TreePlotLayout.depth(wp.tier);
        for (int f = 1; f <= d; f++) {
            for (int s = -h; s <= h; s++) {
                BlockPos p = TreePlotLayout.local(wp, f, s);
                if (!grid.contains(p) && level().getBlockState(p).is(BlockTags.LOGS)) {
                    orphanCache = p;
                    return p;
                }
            }
        }
        return null;
    }

    private BlockPos orphanCache;
    private long nextOrphanScan;

    private void storage(WorkplaceState wp) {
        if (!atStorage()) {
            return;
        }
        if (unloadNow() == Unload.PARTIAL_FULL && Inv.freeSlots(npc.inventory()) < 3) {
            status("Storehouse full - holding logs", "Waiting");
            waitFor(200);
            return;
        }
        upgradeEquipment();
        if (!hasAxe()) {
            status("No axe in storage - waiting", "Waiting");
            waitFor(300);
            return;
        }
        int saplings = Inv.count(npc.inventory(), s -> s.is(ItemTags.SAPLINGS));
        if (saplings < 4) {
            takeFromStorage(s -> s.is(Items.OAK_SAPLING), 6 - saplings);
            saplings = Inv.count(npc.inventory(), s -> s.is(ItemTags.SAPLINGS));
            if (saplings < 2) {
                takeFromStorage(s -> s.is(ItemTags.SAPLINGS), 6 - saplings);
            }
        }
        if (!wp.upgrading() && now() >= nextUpgradeCheck && wp.tier < WorkplaceState.Type.TREE_PLOT.maxTier) {
            nextUpgradeCheck = now() + 3600;
            int fencesNeeded = fenceNet(wp);
            int fences = FactionStorage.count(level(), base(), s -> s.is(Items.OAK_FENCE));
            int planks = FactionStorage.count(level(), base(), s -> s.is(ItemTags.PLANKS));
            int logsStored = FactionStorage.count(level(), base(), s -> s.is(ItemTags.LOGS));
            // Upgrade only with a healthy wood surplus, after real plot work, and never while construction waits.
            int woodEquivalent = fences + (planks + logsStored * 4) * 3 / 6;
            boolean projectActive = base().projects.stream().anyMatch(p -> p.status != com.factionclash.campaign.Project.Status.DONE);
            if (!projectActive && wp.totalOutput >= 64 && woodEquivalent >= fencesNeeded + 96) {
                wp.upgradeStep = 0;
                data().setDirty();
                Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Tree plot #" + wp.id
                        + " enlargement to tier " + (wp.tier + 1) + " started by " + npc.npcName()));
            }
        }
        if (wp.upgrading()) {
            int have = Inv.count(npc.inventory(), s -> s.is(Items.OAK_FENCE));
            int need = fenceNet(wp) - have;
            if (need > 0) {
                need -= takeFromStorage(s -> s.is(Items.OAK_FENCE), need);
            }
            if (need > 0) {
                int crafts = (need + 2) / 3;                 // 4 planks + 2 sticks -> 3 fences
                int sticksNeed = crafts * 2 - Inv.count(npc.inventory(), x -> x.is(Items.STICK));
                if (sticksNeed > 0) {
                    sticksNeed -= takeFromStorage(x -> x.is(Items.STICK), sticksNeed);
                }
                int planksNeed = crafts * 4 + Math.max(0, (sticksNeed + 3) / 4) * 2
                        - Inv.count(npc.inventory(), x -> x.is(ItemTags.PLANKS));
                if (planksNeed > 0) {
                    planksNeed -= takeFromStorage(x -> x.is(ItemTags.PLANKS), planksNeed);
                }
                int logsNeed = (planksNeed + 3) / 4 - Inv.count(npc.inventory(), x -> x.is(ItemTags.LOGS));
                if (logsNeed > 0) {
                    takeFromStorage(x -> x.is(ItemTags.LOGS), logsNeed);
                }
                state = State.CRAFT;
                return;
            }
        }
        state = State.CHECK;
    }

    private int fenceNet(WorkplaceState wp) {
        Set<BlockPos> oldRing = new HashSet<>(TreePlotLayout.fence(wp, wp.tier));
        int place = 0, remove = 0;
        for (BlockPos p : TreePlotLayout.fence(wp, wp.tier + 1)) {
            if (!oldRing.contains(p)) {
                place++;
            }
        }
        Set<BlockPos> newRing = new HashSet<>(TreePlotLayout.fence(wp, wp.tier + 1));
        for (BlockPos p : oldRing) {
            if (!newRing.contains(p)) {
                remove++;
            }
        }
        return Math.max(0, place - remove);
    }

    private void craftFences(WorkplaceState wp) {
        if (!goTo(base().point(com.factionclash.campaign.BaseState.WORKSHOP_STAND), 2.0, "the crafting table")) {
            return;
        }
        if (++progress < 25) {
            status("Crafting fences for the plot upgrade", "Crafting");
            return;
        }
        progress = 0;
        int need = fenceNet(wp) - Inv.count(npc.inventory(), s -> s.is(Items.OAK_FENCE));
        if (need > 0 && Crafting.craftWithIntermediates(npc.inventory(), Crafting.FENCES, 1) > 0) {
            npc.swing(InteractionHand.MAIN_HAND);
            return;
        }
        state = need > 0 ? State.STORAGE : State.UPGRADE;
        if (need > 0) {
            status("Not enough wood for fences (" + need + " missing)", "Waiting");
            waitFor(600);
        }
    }

    private void collectLogs() {
        logs.clear();
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(spot);
        while (!queue.isEmpty() && logs.size() < 96) {
            BlockPos p = queue.poll();
            if (!seen.add(p) || Math.abs(p.getX() - spot.getX()) > 7 || Math.abs(p.getZ() - spot.getZ()) > 7
                    || p.getY() < spot.getY() || p.getY() > spot.getY() + 24) {
                continue;
            }
            if (!level().getBlockState(p).is(BlockTags.LOGS)) {
                continue;
            }
            logs.add(p);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx != 0 || dy != 0 || dz != 0) {
                            queue.add(p.offset(dx, dy, dz));
                        }
                    }
                }
            }
        }
        logs.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).reversed());
    }

    private void chop(WorkplaceState wp) {
        if (!goTo(spot, 4.0, "a tree in plot #" + wp.id)) {
            return;
        }
        if (logs.isEmpty()) {
            collectLogs();
            if (logs.isEmpty()) {
                clearCanopy(wp);
                state = State.PLANT;
                return;
            }
        }
        BlockPos log = logs.get(0);
        if (!level().getBlockState(log).is(BlockTags.LOGS)) {
            logs.remove(0);
            return;
        }
        lookAt(log);
        int axeTier = Math.max(0, ToolKind.tierOf(npc.tool()));
        int ticks = (int) Math.max(6, FCConfig.CHOP_TICKS_PER_LOG.get() / ((1.0 + 0.2 * axeTier) * (1.0 + npc.affix(Affix.DILIGENCE))));
        progress++;
        level().destroyBlockProgress(npc.getId(), log, Math.min(9, progress * 10 / ticks));
        status("Felling a tree in plot #" + wp.id + " (" + logs.size() + " logs left)", "Chopping");
        if (progress < ticks) {
            return;
        }
        BlockState s = level().getBlockState(log);
        ItemStack item = new ItemStack(s.getBlock().asItem());
        if (!Inv.fits(npc.inventory(), item)) {
            state = State.STORAGE;
            return;
        }
        progress = 0;
        level().destroyBlockProgress(npc.getId(), log, -1);
        level().setBlock(log, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level().levelEvent(2001, log, Block.getId(s));
        Crafting.addAll(npc.inventory(), item);
        npc.swing(InteractionHand.MAIN_HAND);
        npc.awardXp(1);
        wp.totalOutput++;
        data().setDirty();
        logs.remove(0);
        if (logs.isEmpty()) {
            clearCanopy(wp);
            state = State.PLANT;
        }
    }

    /** Clears the felled tree's leaves, rolling vanilla-like sapling and (oak) apple chances into the backpack. */
    private void clearCanopy(WorkplaceState wp) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 0; dy <= 14; dy++) {
                    m.set(spot.getX() + dx, spot.getY() + dy, spot.getZ() + dz);
                    BlockState s = level().getBlockState(m);
                    if (!s.is(BlockTags.LEAVES) || (s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT))) {
                        continue;
                    }
                    level().setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    if (level().random.nextDouble() < TreePlotLayout.SAPLING_CHANCE) {
                        net.minecraft.world.item.Item sap = saplingFor(s);
                        if (sap != null) {
                            Crafting.addAll(npc.inventory(), new ItemStack(sap));
                        }
                    }
                    if ((s.is(Blocks.OAK_LEAVES) || s.is(Blocks.DARK_OAK_LEAVES)) && level().random.nextDouble() < TreePlotLayout.appleChance(wp.tier)) {
                        ItemStack apple = new ItemStack(Items.APPLE);
                        if (Inv.fits(npc.inventory(), apple)) {
                            Crafting.addAll(npc.inventory(), apple);
                        }
                    }
                }
            }
        }
        level().playSound(null, spot, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
    }

    private static net.minecraft.world.item.Item saplingFor(BlockState leaves) {
        if (leaves.is(Blocks.OAK_LEAVES)) {
            return Items.OAK_SAPLING;
        }
        if (leaves.is(Blocks.BIRCH_LEAVES)) {
            return Items.BIRCH_SAPLING;
        }
        if (leaves.is(Blocks.SPRUCE_LEAVES)) {
            return Items.SPRUCE_SAPLING;
        }
        if (leaves.is(Blocks.DARK_OAK_LEAVES)) {
            return Items.DARK_OAK_SAPLING;
        }
        return Items.OAK_SAPLING;
    }

    private void plant(WorkplaceState wp) {
        if (!goTo(spot, 3.0, "a planting spot")) {
            return;
        }
        ItemStack sap = Inv.first(npc.inventory(), s -> s.is(Items.OAK_SAPLING));
        if (sap.isEmpty()) {
            sap = Inv.first(npc.inventory(), s -> s.is(ItemTags.SAPLINGS));
        }
        if (sap.isEmpty() || !(sap.getItem() instanceof BlockItem bi)) {
            state = State.STORAGE;
            return;
        }
        BlockState below = level().getBlockState(spot.below());
        if (!below.is(BlockTags.DIRT)) {
            level().setBlock(spot.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        }
        BlockState at = level().getBlockState(spot);
        if (at.is(BlockTags.LEAVES)) {
            level().setBlock(spot, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            at = Blocks.AIR.defaultBlockState();
        } else if (at.is(BlockTags.LOGS)) {
            logs.clear();
            state = State.CHOP;
            return;
        }
        if (!at.isAir() && !at.canBeReplaced()) {
            status("Planting spot is blocked", "Blocked");
            state = State.CHECK;
            waitFor(100);
            return;
        }
        Block saplingBlock = bi.getBlock();
        sap.shrink(1);
        npc.inventory().setChanged();
        level().setBlock(spot, saplingBlock.defaultBlockState(), Block.UPDATE_ALL);
        level().playSound(null, spot, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        npc.swing(InteractionHand.MAIN_HAND);
        if (level().random.nextDouble() < npc.affix(Affix.REGROWTH)) {
            growNow(spot, 2);
            status("Instant regrowth!", "Planting");
        }
        state = State.CHECK;
    }

    private void growNow(BlockPos p, int steps) {
        for (int i = 0; i < steps; i++) {
            BlockState s = level().getBlockState(p);
            if (s.getBlock() instanceof SaplingBlock sb) {
                sb.advanceTree(level(), p, s, level().random);
            }
        }
    }

    private void tend(WorkplaceState wp) {
        BlockState s = level().getBlockState(spot);
        if (!(s.getBlock() instanceof SaplingBlock)) {
            state = State.CHECK;
            return;
        }
        if (!goTo(spot, 3.0, "a sapling")) {
            return;
        }
        lookAt(spot);
        status("Tending saplings in plot #" + wp.id, "Tending");
        int ticks = (int) Math.max(20, FCConfig.TEND_TICKS.get() / (1.0 + npc.affix(Affix.DILIGENCE)));
        if (++progress < ticks) {
            return;
        }
        progress = 0;
        growNow(spot, 1);
        level().levelEvent(1505, spot, 0);
        npc.swing(InteractionHand.MAIN_HAND);
        state = State.CHECK;
    }

    /** Upgrade steps: pick up old fence outside the new ring, clear/level the new ground, place the new ring. */
    private List<BlockPos[]> upgradeSteps(WorkplaceState wp) {
        List<BlockPos[]> steps = new ArrayList<>();
        Set<BlockPos> newRing = new HashSet<>(TreePlotLayout.fence(wp, wp.tier + 1));
        for (BlockPos p : TreePlotLayout.fence(wp, wp.tier)) {
            if (!newRing.contains(p)) {
                steps.add(new BlockPos[]{p, null});
            }
        }
        for (BlockPos p : TreePlotLayout.fence(wp, wp.tier + 1)) {
            steps.add(new BlockPos[]{null, p});
        }
        return steps;
    }

    private void upgrade(WorkplaceState wp) {
        List<BlockPos[]> steps = upgradeSteps(wp);
        if (wp.upgradeStep >= steps.size()) {
            levelNewGround(wp);
            wp.tier++;
            wp.upgradeStep = -1;
            data().setDirty();
            npc.awardXp(15);
            Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Tree plot #" + wp.id + " enlarged to tier "
                    + wp.tier + " (" + TreePlotLayout.spots(wp).size() + " trees)"));
            state = State.CHECK;
            return;
        }
        BlockPos[] step = steps.get(wp.upgradeStep);
        BlockPos target = step[0] != null ? step[0] : step[1];
        if (!goTo(target, 3.5, "the plot fence")) {
            return;
        }
        status("Enlarging tree plot #" + wp.id + " (" + (wp.upgradeStep + 1) + "/" + steps.size() + ")", "Building");
        if (++progress < 8) {
            return;
        }
        progress = 0;
        if (step[0] != null) {
            if (level().getBlockState(step[0]).is(BlockTags.FENCES)) {
                level().setBlock(step[0], Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                Crafting.addAll(npc.inventory(), new ItemStack(Items.OAK_FENCE));
            }
        } else {
            BlockPos p = step[1];
            prepareColumn(p);
            if (!level().getBlockState(p).is(BlockTags.FENCES)) {
                if (!Inv.consume(npc.inventory(), s -> s.is(Items.OAK_FENCE), 1)) {
                    state = State.STORAGE;
                    return;
                }
                level().setBlock(p, Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
                Blueprint.fixShapes(level(), List.of(p, p.north(), p.south(), p.east(), p.west()));
            }
        }
        npc.swing(InteractionHand.MAIN_HAND);
        wp.upgradeStep++;
        data().setDirty();
    }

    /** Clears obstacles in a column of the new plot area (logs are kept, other blocks removed) and fixes ground. */
    private void prepareColumn(BlockPos p) {
        ServerLevel l = level();
        for (int dy = 0; dy <= 10; dy++) {
            BlockPos q = p.above(dy);
            BlockState s = l.getBlockState(q);
            if (s.isAir() || s.is(BlockTags.FENCES)) {
                continue;
            }
            if (s.is(BlockTags.LOGS)) {
                ItemStack log = new ItemStack(s.getBlock().asItem());
                if (Inv.fits(npc.inventory(), log)) {
                    Crafting.addAll(npc.inventory(), log);
                }
            }
            l.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        if (!Terrain.isSolidGround(l.getBlockState(p.below()))) {
            l.setBlock(p.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private void levelNewGround(WorkplaceState wp) {
        int h = TreePlotLayout.half(wp.tier + 1);
        int d = TreePlotLayout.depth(wp.tier + 1);
        for (int f = 1; f <= d; f++) {
            for (int s = -h; s <= h; s++) {
                BlockPos p = TreePlotLayout.local(wp, f, s);
                if (!TreePlotLayout.insideInner(wp, wp.tier, p)) {
                    prepareColumn(p);
                    BlockState below = level().getBlockState(p.below());
                    if (!below.is(BlockTags.DIRT)) {
                        level().setBlock(p.below(), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (spot != null && npc.level() instanceof ServerLevel sl && !logs.isEmpty()) {
            sl.destroyBlockProgress(npc.getId(), logs.get(0), -1);
        }
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state.name());
        if (spot != null) {
            tag.putLong("spot", spot.asLong());
        }
        tag.putInt("progress", progress);
        tag.putInt("tendCursor", tendCursor);
        tag.putLong("nextUpgrade", nextUpgradeCheck);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            state = State.CHECK;
        }
        spot = readPos(tag, "spot");
        if (spot == null && state != State.STORAGE && state != State.UPGRADE) {
            state = State.CHECK;
        }
        progress = tag.getInt("progress");
        tendCursor = tag.getInt("tendCursor");
        nextUpgradeCheck = tag.getLong("nextUpgrade");
    }
}
