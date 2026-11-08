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
 * Tree-plot woodcutter that works like a player (spec 8, reworked): it fells grown trees in its fenced plot with
 * its axe at real break speed, breaks the canopy so the leaves' own loot tables drop saplings, sticks and apples,
 * walks around collecting everything that dropped, replants, and speeds saplings up only with real bone meal
 * (otherwise they grow naturally). Upgrades enlarge the fence so the plot holds more trees.
 */
public class WoodcutterJob extends Job {
    enum State { CHECK, STORAGE, CRAFT, CHOP, LEAVES, COLLECT, PLANT, BONEMEAL, UPGRADE }

    private static final int CARGO_LIMIT = 48;
    private State state = State.CHECK;
    private BlockPos spot;
    private int progress;
    private int tendCursor;
    private long nextUpgradeCheck;
    private final List<BlockPos> logs = new ArrayList<>();
    private final List<BlockPos> leaves = new ArrayList<>();
    private final BlockWork.Breaker breaker = new BlockWork.Breaker();
    private int collectTicks;
    /** Reach used for tall trunks: a player would climb or pillar; NPCs reach a little higher instead (PROPOSED). */
    private static final double TRUNK_REACH = 7.5;

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
        if (s.is(Items.BONE_MEAL)) {
            return 16;
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

    private void collect(WorkplaceState wp) {
        int n = BlockWork.pickUp(npc, x -> true);
        if (n > 0) {
            wp.totalOutput += n;
            data().setDirty();
        }
    }

    private net.minecraft.world.phys.AABB plotArea(WorkplaceState wp) {
        BlockPos a = TreePlotLayout.local(wp, 0, -TreePlotLayout.half(wp.tier) - 1);
        BlockPos b = TreePlotLayout.local(wp, TreePlotLayout.depth(wp.tier) + 1, TreePlotLayout.half(wp.tier) + 1);
        // the fenced area only: items that rolled out over the fence (onto a slope or hill) are left where they are
        return new net.minecraft.world.phys.AABB(Vec3Min(a, b), Vec3Max(a, b)).expandTowards(0, 12, 0).move(0, -1, 0);
    }

    private static net.minecraft.world.phys.Vec3 Vec3Min(BlockPos a, BlockPos b) {
        return new net.minecraft.world.phys.Vec3(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    private static net.minecraft.world.phys.Vec3 Vec3Max(BlockPos a, BlockPos b) {
        return new net.minecraft.world.phys.Vec3(Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    private long nextAxeCheck;

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
        if (state != State.STORAGE && state != State.CRAFT) {
            collect(wp);
        }
        BlockPos under = npc.blockPosition().below();
        if (npc.onGround() && level().getBlockState(under).is(BlockTags.LEAVES)) {
            // standing on a tree canopy: a player breaks the leaves underfoot and drops down
            status("Breaking the leaves underfoot to get down", "Chopping");
            chopBlock(wp, under);
            return;
        }
        switch (state) {
            case CHECK -> decide(wp);
            case STORAGE -> storage(wp);
            case CRAFT -> craftFences(wp);
            case CHOP -> chop(wp);
            case LEAVES -> breakLeaves(wp);
            case COLLECT -> collectDrops(wp);
            case PLANT -> plant(wp);
            case BONEMEAL -> boneMeal(wp);
            case UPGRADE -> upgrade(wp);
        }
    }

    /** Breaks a block at player speed; logs, saplings, sticks and apples go straight into the backpack. */
    private boolean chopBlock(WorkplaceState wp, BlockPos pos) {
        if (!breaker.tick(npc, pos)) {
            return false;
        }
        if (breaker.lastGained() > 0) {
            wp.totalOutput += breaker.lastGained();
            data().setDirty();
        }
        return true;
    }

    private boolean isTree(BlockPos p) {
        return level().getBlockState(p).is(BlockTags.LOGS);
    }

    private void decide(WorkplaceState wp) {
        wild = false;
        if (!hasAxe() && now() >= nextAxeCheck || cargo() >= CARGO_LIMIT || Inv.freeSlots(npc.inventory()) == 0) {
            state = State.STORAGE;
            return;
        }
        if (wp.upgrading() && now() >= upgradeBlockedUntil) {
            state = State.UPGRADE;
            return;
        }
        List<BlockPos> spots = TreePlotLayout.spots(wp);
        for (BlockPos s : spots) {
            if (isTree(s) && skipUntil.getOrDefault(s.asLong(), 0L) <= now()) {
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
        if (collectable(wp) != null) {
            collectTicks = 0;
            state = State.COLLECT;
            return;
        }
        if (Inv.count(npc.inventory(), x -> x.is(Items.BONE_MEAL)) > 0) {
            for (int i = 0; i < spots.size(); i++) {
                BlockPos s = spots.get((tendCursor + i) % spots.size());
                if (level().getBlockState(s).getBlock() instanceof SaplingBlock) {
                    tendCursor = (tendCursor + i + 1) % spots.size();
                    spot = s;
                    progress = 0;
                    state = State.BONEMEAL;
                    return;
                }
            }
        }
        // nothing to do in the plot while its saplings grow: fell a wild tree nearby, as a player would
        BlockPos tree = wildTree(wp);
        if (tree != null) {
            spot = tree;
            wild = true;
            wildWalk = 0;
            logs.clear();
            state = State.CHOP;
            return;
        }
        if (forage(wp)) {
            return;
        }
        status("Saplings are growing" + (Inv.count(npc.inventory(), x -> x.is(Items.BONE_MEAL)) == 0 ? " (no bone meal)" : ""), "Waiting");
        waitFor(100);
    }

    private BlockPos forageTarget;
    private int forageDir = -1, forageFails;
    private long forageStart, nextForage;

    /**
     * No wild tree near the plot either: like a player looking for a forest, walk out about 70 blocks in one
     * direction (a different one each time) and look around there. After a full round with nothing found it rests.
     */
    private boolean forage(WorkplaceState wp) {
        if (forageTarget == null) {
            if (now() < nextForage) {
                return false;
            }
            forageDir = (forageDir + 1) % 8;
            double a = forageDir * Math.PI / 4;
            forageTarget = new BlockPos(wp.origin.getX() + (int) Math.round(Math.cos(a) * 72), wp.origin.getY(),
                    wp.origin.getZ() + (int) Math.round(Math.sin(a) * 72));
            forageStart = now();
        }
        BlockPos t = level().isLoaded(forageTarget)
                ? level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, forageTarget) : forageTarget;
        status("Looking for trees further out (" + t.toShortString() + ")", "Travelling");
        if (goTo(t, 8.0, "trees further out") || now() - forageStart > 2400) {
            forageTarget = null;
            BlockPos tree = wildTreeAround(npc.blockPosition(), 40);
            if (tree != null) {
                forageFails = 0;
                spot = tree;
                wild = true;
                wildWalk = 0;
                logs.clear();
                state = State.CHOP;
            } else if (++forageFails >= 8) {
                forageFails = 0;
                nextForage = now() + 6000;
            }
        }
        return true;
    }

    /** PROPOSED: wild trees are felled within this many blocks of the plot entrance. */
    public static final int WILD_RANGE = 64;
    private boolean wild;
    private long nextWildScan;
    private BlockPos wildCache;
    private int wildWalk;
    private final Set<Long> badWild = new HashSet<>();

    /**
     * The nearest natural tree around the plot (a trunk standing on dirt with natural leaves), outside every base
     * and tree plot, or null. Scanned at most every 30 seconds.
     */
    private BlockPos wildTree(WorkplaceState wp) {
        if (wildCache != null && level().getBlockState(wildCache).is(BlockTags.LOGS)) {
            return wildCache;
        }
        wildCache = null;
        if (now() < nextWildScan) {
            return null;
        }
        nextWildScan = now() + 600;
        wildCache = wildTreeAround(wp.origin, WILD_RANGE);
        return wildCache;
    }

    /** The natural tree nearest the woodcutter within {@code range} of {@code center} (loaded ground only). */
    private BlockPos wildTreeAround(BlockPos center, int range) {
        BlockPos found = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -range; dx <= range; dx += 2) {
            for (int dz = -range; dz <= range; dz += 2) {
                if (dx * dx + dz * dz > range * range) {
                    continue;
                }
                int x = center.getX() + dx, z = center.getZ() + dz;
                if (!level().isLoaded(new BlockPos(x, 0, z))) {
                    continue;
                }
                BlockPos top = new BlockPos(x, level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                if (!level().getBlockState(top).is(BlockTags.LOGS)) {
                    continue;
                }
                BlockPos base = top;
                while (level().getBlockState(base.below()).is(BlockTags.LOGS) && top.getY() - base.getY() < 30) {
                    base = base.below();
                }
                if (top.getY() - base.getY() < 2 || !level().getBlockState(base.below()).is(BlockTags.DIRT) || badWild.contains(base.asLong())
                        || !naturalLeavesNear(top) || protectedSpot(base)) {
                    continue;
                }
                double d = base.distSqr(npc.blockPosition());
                if (d < bestD) {
                    bestD = d;
                    found = base.immutable();
                }
            }
        }
        return found;
    }

    private boolean naturalLeavesNear(BlockPos top) {
        for (BlockPos p : BlockPos.betweenClosed(top.offset(-2, -1, -2), top.offset(2, 2, 2))) {
            BlockState s = level().getBlockState(p);
            if (s.is(BlockTags.LEAVES) && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT)) {
                return true;
            }
        }
        return false;
    }

    /** Inside a base or any faction's tree plot: not a wild tree. */
    private boolean protectedSpot(BlockPos p) {
        for (com.factionclash.campaign.BaseState b : data().bases.values()) {
            if (!b.destroyed && b.contains(p, 3)) {
                return true;
            }
        }
        for (WorkplaceState w : data().workplaces.values()) {
            if (w.type == WorkplaceState.Type.TREE_PLOT && TreePlotLayout.insideInner(w, w.tier + 1, p)) {
                return true;
            }
        }
        return false;
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
    private int stepFailures;
    private boolean groundPrepared;
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
            // like a player at the very start: no axe, so punch the trees (slower, the logs drop all the same) -
            // those logs are what the blacksmith needs to make one
            status("No axe in the storehouse - felling trees by hand", "Chopping");
            nextAxeCheck = now() + 2400;
        }
        int saplings = Inv.count(npc.inventory(), s -> s.is(ItemTags.SAPLINGS));
        if (saplings < 4) {
            takeFromStorage(s -> s.is(Items.OAK_SAPLING), 6 - saplings);
            saplings = Inv.count(npc.inventory(), s -> s.is(ItemTags.SAPLINGS));
            if (saplings < 2) {
                takeFromStorage(s -> s.is(ItemTags.SAPLINGS), 6 - saplings);
            }
        }
        int boneMeal = Inv.count(npc.inventory(), x -> x.is(Items.BONE_MEAL));
        if (boneMeal < 8) {
            takeFromStorage(x -> x.is(Items.BONE_MEAL), 16 - boneMeal);
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
        if (wp.upgrading() && now() >= upgradeBlockedUntil) {
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

    /** Until when the plot enlargement waits because the fences could not be made (game time). */
    private long upgradeBlockedUntil;

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
        state = need > 0 ? State.CHECK : State.UPGRADE;
        if (need > 0) {
            // the wood went elsewhere since the enlargement started: go and cut some, and finish the fence later
            // (waiting at the storehouse for wood that only this woodcutter brings in would never end)
            status("Not enough wood for fences (" + need + " missing) - cutting more first", "Chopping");
            upgradeBlockedUntil = now() + 2400;
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
        if (Inv.freeSlots(npc.inventory()) == 0) {
            breaker.reset(npc);
            state = State.STORAGE;
            return;
        }
        if (!BlockWork.inReach(npc, spot, 4.0)) {
            // low leaves at head height block the way to the trunk: a player cuts through them
            BlockPos leaf = leafInTheWay();
            if (leaf != null) {
                status("Cutting through leaves to reach a tree in plot #" + wp.id, "Chopping");
                chopBlock(wp, leaf);
                return;
            }
            if (wild && ++wildWalk > 400) {
                // cannot get to this wild tree (cliff, water, fenced in): leave it and look for another
                badWild.add(spot.asLong());
                if (badWild.size() > 128) {
                    badWild.clear();
                }
                wildCache = null;
                wild = false;
                npc.navigator().reset();
                state = State.CHECK;
                return;
            }
            if (!wild && ++plotWalk > 600) {
                // this tree cannot be reached (walled in by low leaves, fence, slope): leave it for a while and
                // fell another one, as a player would
                skipUntil.put(spot.asLong(), now() + 6000);
                plotWalk = 0;
                viaOpening = false;
                npc.navigator().reset();
                state = State.CHECK;
                return;
            }
            if (!wild && !inPlot(wp) && plotWalk > 300) {
                viaOpening = true; // stuck outside its own fence (a hill beside it): round to the opening, like a player
            }
            if (viaOpening) {
                if (wild || TreePlotLayout.insideInner(wp, wp.tier, npc.blockPosition())) {
                    viaOpening = false;
                } else {
                    // to the opening, then one step through it into the plot
                    BlockPos gap = TreePlotLayout.local(wp, 0, 0);
                    BlockPos target = com.factionclash.npc.NpcNavigator.within(npc, gap, 1.5) ? TreePlotLayout.local(wp, 1, 0) : gap;
                    goTo(target, 0.9, "the way into plot #" + wp.id);
                    return;
                }
            }
            if (!goTo(spot, 2.5, wild ? "a wild tree" : "a tree in plot #" + wp.id)) {
                return;
            }
        }
        wildWalk = 0;
        plotWalk = 0;
        if (logs.isEmpty()) {
            collectLogs();
            if (logs.isEmpty()) {
                findLeaves();
                state = State.LEAVES;
                return;
            }
        }
        BlockPos log = logs.get(0);
        if (!level().getBlockState(log).is(BlockTags.LOGS) || !BlockWork.inReach(npc, log, TRUNK_REACH)) {
            logs.remove(0);
            return;
        }
        status("Felling " + (wild ? "a wild tree" : "a tree in plot #" + wp.id) + " (" + logs.size() + " logs left)", "Chopping");
        if (chopBlock(wp, log)) {
            npc.awardXp(1);
            logs.remove(0);
            if (logs.isEmpty()) {
                findLeaves();
                state = State.LEAVES;
            }
        }
    }

    /** The felled tree's canopy within a player's reach of the stump. */
    private void findLeaves() {
        leaves.clear();
        for (BlockPos p : BlockPos.betweenClosed(spot.offset(-3, 0, -3), spot.offset(3, 12, 3))) {
            BlockState s = level().getBlockState(p);
            if (s.is(BlockTags.LEAVES) && !(s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT))) {
                leaves.add(p.immutable());
            }
        }
        leaves.sort(Comparator.comparingDouble(p -> p.distSqr(npc.blockPosition())));
    }

    /** Breaks the reachable leaves by hand; their loot tables drop saplings, sticks and apples. */
    private void breakLeaves(WorkplaceState wp) {
        while (!leaves.isEmpty() && (!level().getBlockState(leaves.get(0)).is(BlockTags.LEAVES)
                || !BlockWork.inReach(npc, leaves.get(0), 5.5))) {
            leaves.remove(0);
        }
        if (leaves.isEmpty()) {
            collectTicks = 0;
            state = State.COLLECT;
            return;
        }
        status("Clearing the canopy (" + leaves.size() + " leaves)", "Chopping");
        if (chopBlock(wp, leaves.get(0))) {
            leaves.remove(0);
        }
    }

    /** A leaf block within reach, at foot or head height, between the woodcutter and the trunk. */
    private boolean viaOpening;
    /** Plot trees given up on for now (unreachable), until a game time. */
    private final java.util.Map<Long, Long> skipUntil = new java.util.HashMap<>();
    /** Ticks spent trying to get within reach of the current plot tree (failed trips included). */
    private int plotWalk;

    /** Within the plot's fence, its opening included. */
    private boolean inPlot(WorkplaceState wp) {
        BlockPos d = npc.blockPosition().subtract(wp.origin);
        net.minecraft.core.Direction side = wp.facing.getClockWise();
        int f = d.getX() * wp.facing.getStepX() + d.getZ() * wp.facing.getStepZ();
        int sd = d.getX() * side.getStepX() + d.getZ() * side.getStepZ();
        return f >= 0 && f <= TreePlotLayout.depth(wp.tier) && Math.abs(sd) <= TreePlotLayout.half(wp.tier);
    }

    private BlockPos leafInTheWay() {
        if (spot == null) {
            return null;
        }
        // near the tree, or deeper in a plot so overgrown that walking gets no closer: cut through
        WorkplaceState wp = wp();
        boolean overgrown = !wild && wp != null && inPlot(wp)
                && (npc.navigator().ticksWithoutProgress() > 100 || plotWalk > 200);
        if (npc.blockPosition().distSqr(spot) > 8 * 8 && !overgrown) {
            return null;
        }
        double own = npc.blockPosition().distSqr(spot);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(npc.blockPosition().offset(-3, 0, -3), npc.blockPosition().offset(3, 1, 3))) {
            if (level().getBlockState(p).is(BlockTags.LEAVES) && p.distSqr(spot) < own && BlockWork.inReach(npc, p, 4.5)) {
                double d = p.distSqr(npc.blockPosition());
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    /** Items the woodcutter could not get to (on a fence post, on top of leaves); left for later. */
    private final Set<java.util.UUID> unreachableItems = new HashSet<>();
    private java.util.UUID chasing;
    private int chaseTicks;

    /** The nearest dropped item in the plot that can be walked to (not resting on leaves, not given up on). */
    private net.minecraft.world.entity.item.ItemEntity collectable(WorkplaceState wp) {
        net.minecraft.world.phys.AABB area = wild && spot != null
                ? new net.minecraft.world.phys.AABB(spot).inflate(6, 2, 6).expandTowards(0, 10, 0) : plotArea(wp);
        return BlockWork.nearestItem(npc, area, x -> true, e ->
                !unreachableItems.contains(e.getUUID()) && !level().getBlockState(e.blockPosition().below()).is(BlockTags.LEAVES)
                        && !level().getBlockState(e.blockPosition()).is(BlockTags.LEAVES));
    }

    /** Walks over to the logs, saplings and apples lying in the plot and picks them up. */
    private void collectDrops(WorkplaceState wp) {
        net.minecraft.world.entity.item.ItemEntity item = collectable(wp);
        if (item == null || ++collectTicks > 400 || Inv.freeSlots(npc.inventory()) == 0) {
            state = Inv.freeSlots(npc.inventory()) == 0 ? State.STORAGE : (spot != null && !isTree(spot) ? State.PLANT : State.CHECK);
            if (unreachableItems.size() > 64) {
                unreachableItems.clear();
            }
            return;
        }
        if (!item.getUUID().equals(chasing)) {
            chasing = item.getUUID();
            chaseTicks = 0;
        } else if (++chaseTicks > 160) {
            unreachableItems.add(chasing); // could not get to it: a player would leave it for later too
            return;
        }
        status("Collecting dropped wood and saplings", "Collecting");
        goTo(item.blockPosition(), 0.8, "dropped items");
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
        if (wild) {
            // replant where the wild tree stood, if a sapling will grow there; the ground is left as it is
            ItemStack s = Inv.first(npc.inventory(), x -> x.is(ItemTags.SAPLINGS));
            if (!s.isEmpty() && s.getItem() instanceof BlockItem sb && level().getBlockState(spot).isAir()
                    && sb.getBlock().defaultBlockState().canSurvive(level(), spot)) {
                level().setBlock(spot, sb.getBlock().defaultBlockState(), Block.UPDATE_ALL);
                s.shrink(1);
                npc.inventory().setChanged();
                npc.swing(InteractionHand.MAIN_HAND);
            }
            wild = false;
            state = State.CHECK;
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
        if (!at.isAir() && !at.canBeReplaced() && !at.hasBlockEntity() && TreePlotLayout.insideInner(wp, wp.tier, spot)) {
            ItemStack debris = new ItemStack(at.getBlock().asItem());
            if (!debris.isEmpty()) {
                Crafting.addAll(npc.inventory(), debris);
            }
            level().setBlock(spot, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            npc.swing(InteractionHand.MAIN_HAND);
            return;
        }
        if (!at.isAir() && !at.canBeReplaced()) {
            status("Planting spot " + spot.toShortString() + " is blocked by " + at.getBlock().getName().getString(), "Blocked");
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

    /** Uses real bone meal on a sapling (vanilla 45% chance to advance each use). */
    private void boneMeal(WorkplaceState wp) {
        BlockState s = level().getBlockState(spot);
        ItemStack meal = Inv.first(npc.inventory(), x -> x.is(Items.BONE_MEAL));
        if (!(s.getBlock() instanceof SaplingBlock) || meal.isEmpty()) {
            state = State.CHECK;
            return;
        }
        if (!goTo(spot, 3.0, "a sapling")) {
            return;
        }
        lookAt(spot);
        status("Using bone meal on saplings in plot #" + wp.id, "Tending");
        if (++progress < 10) {
            return;
        }
        progress = 0;
        if (net.minecraft.world.item.BoneMealItem.growCrop(meal, level(), spot)) {
            level().levelEvent(1505, spot, 15);
        }
        npc.inventory().setChanged();
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
        if (wp.upgradeStep == 0 && !groundPrepared) {
            // prepare the enlarged area first, so every new fence post stands on reachable, levelled ground
            levelNewGround(wp);
            groundPrepared = true;
            npc.swing(InteractionHand.MAIN_HAND);
        }
        if (wp.upgradeStep >= steps.size()) {
            levelNewGround(wp);
            groundPrepared = false;
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
        boolean close = npc.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(target)) <= 6.0;
        if (!close && !goTo(target, 3.5, "the plot fence")) {
            if (npc.navigator().target() == null && ++stepFailures >= 3) {
                // unreachable fence post (cliff, water): leave the gap rather than wander indefinitely
                com.factionclash.FactionClash.LOGGER.info("Plot #{} skips unreachable fence step {} at {}", wp.id, wp.upgradeStep, target.toShortString());
                stepFailures = 0;
                wp.upgradeStep++;
                data().setDirty();
            }
            return;
        }
        stepFailures = 0;
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
                if (BlockWork.obstructed(level(), p, Blocks.OAK_FENCE.defaultBlockState(), npc)) {
                    status("Waiting for the way to clear to place a fence", "Building");
                    return;
                }
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
                if (level().getBlockState(p).is(BlockTags.FENCES)) {
                    level().setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); // inner area holds no fences
                }
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
        breaker.reset(npc);
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
