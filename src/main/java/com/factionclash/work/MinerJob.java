package com.factionclash.work;

import com.factionclash.campaign.WorkplaceState;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.Affix;
import com.factionclash.logic.MineLootTable;
import com.factionclash.logic.MineResource;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.ToolKind;
import com.factionclash.storage.FactionStorage;
import com.factionclash.structure.MineLayout;
import com.factionclash.util.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Constructed-mine miner (spec 6). Each completed cycle generates one real item into the miner's backpack and
 * triggers exactly one swing; the face block shows crack progress during the cycle. Output depends on mine
 * tier AND pickaxe tier. Upgrades consume a real pickaxe plus torches, stairs and cobblestone and are dug out
 * step by step, so the mine visibly extends downward.
 */
public class MinerJob extends Job {
    enum State { CHECK, STORAGE, CRAFT, TRAVEL, WORK, EXCAVATE, CLEAR }

    private static final int CARGO_LIMIT = 64;
    private static final Predicate<ItemStack> STAIRS = s -> s.is(Items.COBBLESTONE_STAIRS) || s.is(Items.STONE_STAIRS)
            || s.is(Items.STONE_BRICK_STAIRS) || s.is(Items.COBBLED_DEEPSLATE_STAIRS);
    private static final Predicate<ItemStack> COBBLE = s -> s.is(Items.COBBLESTONE);
    private static final Predicate<ItemStack> TORCH = s -> s.is(Items.TORCH);
    /** Solid, non-falling blocks a miner may use to plug water, lava, sand or cave openings. */
    private static final Predicate<ItemStack> SEAL = s -> s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE) || s.is(Items.STONE)
            || s.is(Items.DIRT) || s.is(Items.ANDESITE) || s.is(Items.DIORITE) || s.is(Items.GRANITE) || s.is(Items.TUFF)
            || s.is(Items.DEEPSLATE) || s.is(Items.COARSE_DIRT) || s.is(Items.NETHERRACK);

    private static boolean unsafeNeighbour(BlockState ns) {
        return ns.isAir() || !ns.getFluidState().isEmpty() || ns.getBlock() instanceof FallingBlock
                || ns.canBeReplaced();
    }

    /** Places one seal block from the backpack at {@code n}; false when the miner carries nothing suitable. */
    private boolean placeSeal(BlockPos n) {
        ItemStack mat = Inv.first(npc.inventory(), SEAL);
        if (mat.isEmpty() || !(mat.getItem() instanceof net.minecraft.world.item.BlockItem bi)) {
            return false;
        }
        mat.shrink(1);
        npc.inventory().setChanged();
        level().setBlock(n, bi.getBlock().defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    private State state = State.CHECK;
    private int progress;
    private int actionTimer;
    private long nextUpgradeCheck;
    private long nextIntegrityCheck;
    private long nextSupplyCheck;
    private final java.util.ArrayDeque<BlockPos> blocked = new java.util.ArrayDeque<>();
    private Set<BlockPos> openCache;
    private int openCacheTier = -1;

    public MinerJob(FactionNpc npc) {
        super(npc);
    }

    private WorkplaceState wp() {
        return Workplaces.claim(npc, WorkplaceState.Type.MINE);
    }

    @Override
    public String workplaceInfo() {
        WorkplaceState wp = wp();
        if (wp == null) {
            return "no mine";
        }
        return "Mine #" + wp.id + " tier " + wp.tier + (wp.upgrading() ? " (upgrading to " + (wp.tier + 1) + ")" : "")
                + " at " + wp.origin.toShortString() + ", cycle " + progress + "/" + cycleTicks();
    }

    public int progress() {
        return progress;
    }

    private int pickTier() {
        return ToolKind.PICKAXE.matches(npc.tool()) ? ToolKind.tierOf(npc.tool()) : -1;
    }

    private int cycleTicks() {
        return MineLootTable.cycleTicks(FCConfig.MINE_CYCLE_TICKS.get(), Math.max(0, pickTier()), npc.affix(Affix.DILIGENCE));
    }

    @Override
    protected int keep(ItemStack s) {
        WorkplaceState wp = wp();
        if (wp != null && wp.upgrading()) {
            if (TORCH.test(s)) {
                return 16;
            }
            if (STAIRS.test(s)) {
                return 16;
            }
            if (SEAL.test(s)) {
                return MineLayout.UPGRADE_COBBLE + 16;
            }
            if (s.is(Items.COAL) || s.is(Items.STICK)) {
                return 8;
            }
        }
        return 0;
    }

    private int cargo() {
        return Inv.count(npc.inventory(), MineItems::isMineOutput);
    }

    @Override
    protected void run() {
        WorkplaceState wp = wp();
        if (wp == null) {
            status("No free mine to work (a builder must provide one)", "Idle");
            waitFor(200);
            return;
        }
        switch (state) {
            case CHECK -> {
                if (pickTier() < 0) {
                    state = State.STORAGE;
                } else if (Inv.freeSlots(npc.inventory()) == 0 || cargo() >= CARGO_LIMIT) {
                    state = State.STORAGE;
                } else if (wp.upgrading() && !hasExcavationSupplies(wp) && now() >= nextSupplyCheck) {
                    nextSupplyCheck = now() + 1200;
                    state = State.STORAGE;
                } else {
                    state = State.TRAVEL;
                }
            }
            case STORAGE -> storage(wp);
            case CRAFT -> craft(wp);
            case TRAVEL -> {
                if (now() >= nextIntegrityCheck) {
                    nextIntegrityCheck = now() + 1200;
                    if (findBlockedCells(wp)) {
                        state = State.CLEAR;
                        return;
                    }
                }
                BlockPos target = MineLayout.workPos(wp);
                if (enterMine(wp)) {
                    state = wp.upgrading() && hasExcavationSupplies(wp) ? State.EXCAVATE : State.WORK;
                } else if (npc.navigator().target() == null) {
                    nextIntegrityCheck = 0; // route failed: check the mine for cave-ins next time
                }
            }
            case CLEAR -> clear(wp);
            case WORK -> work(wp);
            case EXCAVATE -> excavate(wp);
        }
    }

    /**
     * Walks into the mine through its entrance and each tier's chamber in turn (short, reliable legs), finishing at
     * the work position. Returns true once there.
     */
    private boolean enterMine(WorkplaceState wp) {
        BlockPos work = MineLayout.workPos(wp);
        int seg = MineLayout.segmentAt(wp, npc.blockPosition(), wp.tier);
        if (seg == 0) {
            if (npc.blockPosition().closerThan(MineLayout.local(wp, 0, 0, 0), 3.5)) {
                return goTo(MineLayout.chamberCenter(wp, 1), 2.0, "mine #" + wp.id) && wp.tier == 1 && goTo(work, 1.3, "mine #" + wp.id);
            }
            BlockPos inside = MineLayout.local(wp, 0, 0, 0);
            goTo(com.factionclash.npc.NpcNavigator.within(npc, MineLayout.entrance(wp), 2.0) ? inside : MineLayout.entrance(wp),
                    1.5, "mine #" + wp.id);
            return false;
        }
        if (seg >= wp.tier) {
            return goTo(work, 1.3, "mine #" + wp.id);
        }
        BlockPos c = MineLayout.chamberCenter(wp, seg);
        if (com.factionclash.npc.NpcNavigator.within(npc, c, 2.5) || MineLayout.forwardOf(wp, npc.blockPosition()) > MineLayout.forwardOf(wp, c)) {
            goTo(MineLayout.chamberCenter(wp, seg + 1), 2.0, "mine #" + wp.id);
        } else {
            goTo(c, 2.0, "mine #" + wp.id);
        }
        return false;
    }

    /** Walks out of the mine the same way (chamber by chamber, then the hut door). True once outside. */
    private boolean leaveMine(WorkplaceState wp) {
        int seg = MineLayout.segmentAt(wp, npc.blockPosition(), wp.tier + (wp.upgrading() ? 1 : 0));
        if (seg == 0) {
            if (MineLayout.forwardOf(wp, npc.blockPosition()) > MineLayout.HUT_BACK - 1
                    && MineLayout.forwardOf(wp, npc.blockPosition()) <= MineLayout.HUT_FRONT
                    && npc.blockPosition().closerThan(wp.origin, 6)) {
                return goTo(MineLayout.entrance(wp), 1.5, "the mine exit");
            }
            return true;
        }
        // from the first segment walk straight out through the hut door; deeper, step back one chamber at a time
        BlockPos target = seg <= 1 ? MineLayout.entrance(wp) : MineLayout.chamberCenter(wp, seg - 1);
        if (seg >= 2 && com.factionclash.npc.NpcNavigator.within(npc, target, 2.5)) {
            target = seg - 2 >= 1 ? MineLayout.chamberCenter(wp, seg - 2) : MineLayout.entrance(wp);
        }
        goTo(target, 2.0, "the mine exit");
        return false;
    }

    private void storage(WorkplaceState wp) {
        if (!leaveMine(wp)) {
            return;
        }
        if (!atStorage()) {
            return;
        }
        if (unloadNow() == Unload.PARTIAL_FULL) {
            status("Storehouse full - holding cargo", "Waiting");
            if (Inv.freeSlots(npc.inventory()) >= 3) {
                state = State.TRAVEL; // keep producing into the backpack until it is full
            } else {
                waitFor(200);
            }
            return;
        }
        upgradeEquipment();
        if (pickTier() < 0) {
            status("No pickaxe in storage - waiting for the blacksmith", "Waiting");
            waitFor(300);
            return;
        }
        if (!wp.upgrading() && now() >= nextUpgradeCheck) {
            nextUpgradeCheck = now() + 2400;
            tryStartUpgrade(wp);
        }
        if (wp.upgrading()) {
            gatherExcavationSupplies(wp);
            if (needsCrafting(wp)) {
                state = State.CRAFT;
                return;
            }
            if (!hasExcavationSupplies(wp)) {
                // never stall: keep producing (cobblestone, coal) until the upgrade can be supplied
                status("Mining to supply the upgrade (missing " + missingSupplies(wp) + ")", "Mining");
                state = State.TRAVEL;
                return;
            }
        }
        state = State.TRAVEL;
    }

    /** Upgrade when it helps (pickaxe can use deeper odds), it is affordable, and a qualifying pickaxe exists. */
    private void tryStartUpgrade(WorkplaceState wp) {
        if (wp.tier >= MineLootTable.MAX_MINE_TIER || pickTier() < 1) {
            return;
        }
        int next = wp.tier + 1;
        int needTier = MineLayout.upgradePickTier(next);
        Predicate<ItemStack> payPick = s -> ToolKind.PICKAXE.matches(s) && ToolKind.tierOf(s) >= needTier;
        int torches = FactionStorage.count(level(), base(), TORCH);
        int coal = FactionStorage.count(level(), base(), Crafting.FUEL_COAL);
        int stairs = FactionStorage.count(level(), base(), STAIRS);
        int cobble = FactionStorage.count(level(), base(), COBBLE);
        boolean torchesOk = torches >= MineLayout.UPGRADE_TORCHES || (torches + coal * 4 >= MineLayout.UPGRADE_TORCHES
                && FactionStorage.count(level(), base(), s -> s.is(Items.STICK) || s.is(ItemTags.PLANKS) || s.is(ItemTags.LOGS)) >= 2);
        int cobbleNeed = MineLayout.UPGRADE_COBBLE + (stairs >= MineLayout.UPGRADE_STAIRS ? 0 : 12);
        if (!torchesOk || cobble < cobbleNeed) {
            return;
        }
        ItemStack pick = FactionStorage.findBest(level(), base(), payPick, s -> -ToolKind.tierOf(s));
        if (pick.isEmpty()) {
            return;
        }
        ItemStack paid = FactionStorage.extractExact(level(), base(), pick);
        if (paid.isEmpty()) {
            return;
        }
        // The pickaxe is the excavation payment: it is consumed now (equipped tools never wear out).
        wp.upgradeStep = 0;
        data().setDirty();
        Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Mine #" + wp.id + " upgrade to tier " + next
                + " started by " + npc.npcName() + " (paid " + paid.getHoverName().getString() + ")"));
    }

    private List<MineLayout.Action> remainingActions(WorkplaceState wp) {
        List<MineLayout.Action> all = MineLayout.segmentActions(wp, wp.tier + 1);
        return all.subList(Math.min(all.size(), Math.max(0, wp.upgradeStep)), all.size());
    }

    private int needTorches(WorkplaceState wp) {
        return (int) remainingActions(wp).stream().filter(a -> !a.isDig() && a.place().is(Blocks.WALL_TORCH)).count();
    }

    private int needStairs(WorkplaceState wp) {
        return (int) remainingActions(wp).stream().filter(a -> !a.isDig() && a.place().is(BlockTags.STAIRS)).count();
    }

    private boolean hasExcavationSupplies(WorkplaceState wp) {
        return Inv.count(npc.inventory(), TORCH) >= needTorches(wp) && Inv.count(npc.inventory(), STAIRS) >= needStairs(wp)
                && Inv.count(npc.inventory(), SEAL) >= Math.min(8, MineLayout.UPGRADE_COBBLE);
    }

    private String missingSupplies(WorkplaceState wp) {
        StringBuilder sb = new StringBuilder();
        int t = needTorches(wp) - Inv.count(npc.inventory(), TORCH);
        int s = needStairs(wp) - Inv.count(npc.inventory(), STAIRS);
        int c = 8 - Inv.count(npc.inventory(), SEAL);
        if (t > 0) {
            sb.append(t).append(" torches ");
        }
        if (s > 0) {
            sb.append(s).append(" stairs ");
        }
        if (c > 0) {
            sb.append(c).append(" sealing blocks");
        }
        return sb.toString().trim();
    }

    private void gatherExcavationSupplies(WorkplaceState wp) {
        int t = needTorches(wp) - Inv.count(npc.inventory(), TORCH);
        if (t > 0) {
            t -= takeFromStorage(TORCH, t);
            if (t > 0) {
                takeFromStorage(Crafting.FUEL_COAL, (t + 3) / 4);
                if (Inv.count(npc.inventory(), s -> s.is(Items.STICK)) < (t + 3) / 4) {
                    takeFromStorage(s -> s.is(Items.STICK), (t + 3) / 4);
                    if (Inv.count(npc.inventory(), s -> s.is(Items.STICK)) < (t + 3) / 4) {
                        takeFromStorage(s -> s.is(ItemTags.PLANKS), 2);
                        takeFromStorage(s -> s.is(ItemTags.LOGS), 1);
                    }
                }
            }
        }
        int st = needStairs(wp) - Inv.count(npc.inventory(), STAIRS);
        if (st > 0) {
            st -= takeFromStorage(STAIRS, st);
        }
        int cobbleWanted = MineLayout.UPGRADE_COBBLE + Math.max(0, (st + 3) / 4) * 6 - Inv.count(npc.inventory(), COBBLE);
        if (cobbleWanted > 0) {
            int got = takeFromStorage(COBBLE, cobbleWanted);
            if (got < cobbleWanted) {
                takeFromStorage(SEAL, cobbleWanted - got);
            }
        }
    }

    private boolean needsCrafting(WorkplaceState wp) {
        boolean torches = Inv.count(npc.inventory(), TORCH) < needTorches(wp) && Inv.count(npc.inventory(), Crafting.FUEL_COAL) > 0;
        boolean stairs = Inv.count(npc.inventory(), STAIRS) < needStairs(wp) && Inv.count(npc.inventory(), COBBLE) >= 6;
        return torches || stairs;
    }

    /** NPCs craft the stairs and torches the excavation needs at the workshop crafting table (spec 6). */
    private void craft(WorkplaceState wp) {
        if (!leaveMine(wp)) {
            return;
        }
        if (!goTo(base().point(com.factionclash.campaign.BaseState.WORKSHOP_STAND), 2.0, "the crafting table")) {
            return;
        }
        lookAt(base().point(com.factionclash.campaign.BaseState.CRAFTING));
        if (++actionTimer < 30) {
            status("Crafting excavation supplies", "Crafting");
            return;
        }
        actionTimer = 0;
        int st = needStairs(wp) - Inv.count(npc.inventory(), STAIRS);
        if (st > 0 && Crafting.craft(npc.inventory(), Crafting.COBBLE_STAIRS, 1) > 0) {
            npc.swing(InteractionHand.MAIN_HAND);
            return;
        }
        int t = needTorches(wp) - Inv.count(npc.inventory(), TORCH);
        if (t > 0) {
            Crafting.ensureSticks(npc.inventory(), 1);
            if (Crafting.craft(npc.inventory(), Crafting.TORCHES, 1) > 0) {
                npc.swing(InteractionHand.MAIN_HAND);
                return;
            }
        }
        state = hasExcavationSupplies(wp) ? State.TRAVEL : State.STORAGE;
        if (state == State.STORAGE) {
            waitFor(200);
        }
    }

    private void work(WorkplaceState wp) {
        BlockPos stand = MineLayout.workPos(wp);
        BlockPos face = MineLayout.facePos(wp);
        if (!com.factionclash.npc.NpcNavigator.within(npc, stand, 1.8)) {
            state = State.TRAVEL;
            return;
        }
        if (wp.upgrading() && hasExcavationSupplies(wp)) {
            state = State.EXCAVATE;
            return;
        }
        BlockState faceState = level().getBlockState(face);
        if (faceState.isAir() || !faceState.getFluidState().isEmpty()) {
            level().setBlock(face, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        lookAt(face);
        int cycle = cycleTicks();
        progress = Math.min(cycle, progress + 1);
        int stage = Math.min(9, progress * 10 / cycle);
        level().destroyBlockProgress(npc.getId(), face, stage);
        status("Mining at mine #" + wp.id + " (tier " + wp.tier + (wp.upgrading() ? ", gathering upgrade supplies" : "") + "), "
                + (progress * 100 / cycle) + "%", "Mining");
        if (progress < cycle) {
            return;
        }
        MineResource res = MineLootTable.roll(wp.tier, Math.max(0, pickTier()), new java.util.SplittableRandom(level().random.nextLong()));
        ItemStack out = new ItemStack(MineItems.item(res));
        if (!Inv.fits(npc.inventory(), out)) {
            // keep the completed cycle; it is delivered once the backpack has room
            state = State.STORAGE;
            return;
        }
        Crafting.addAll(npc.inventory(), out);
        int produced = 1;
        if (res.isOre() && level().random.nextDouble() < npc.affix(Affix.PROSPECTOR)) {
            ItemStack extra = new ItemStack(MineItems.item(res));
            if (Inv.fits(npc.inventory(), extra)) {
                Crafting.addAll(npc.inventory(), extra);
                produced++;
            }
        }
        npc.swing(InteractionHand.MAIN_HAND);
        level().levelEvent(2001, face, Block.getId(MineItems.faceBlock(res).defaultBlockState()));
        level().destroyBlockProgress(npc.getId(), face, -1);
        npc.awardXp(res.xp * produced);
        wp.totalOutput += produced;
        data().setDirty();
        progress = 0;
        if (cargo() >= CARGO_LIMIT || Inv.freeSlots(npc.inventory()) == 0) {
            state = State.STORAGE;
        }
    }

    private Set<BlockPos> openCells(WorkplaceState wp) {
        if (openCache == null || openCacheTier != wp.tier + 1) {
            openCache = MineLayout.openCells(wp, wp.tier + 1);
            openCacheTier = wp.tier + 1;
        }
        return openCache;
    }

    private void excavate(WorkplaceState wp) {
        List<MineLayout.Action> all = MineLayout.segmentActions(wp, wp.tier + 1);
        if (wp.upgradeStep >= all.size()) {
            finishUpgrade(wp);
            return;
        }
        MineLayout.Action a = all.get(wp.upgradeStep);
        double dist = npc.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(a.pos()));
        if (dist > 4.5) {
            BlockPos approach = lastOpenBefore(wp, all, wp.upgradeStep);
            if (!goTo(approach, 1.5, "the excavation face")) {
                return;
            }
        }
        lookAt(a.pos());
        status("Extending mine #" + wp.id + " to tier " + (wp.tier + 1) + " (step " + (wp.upgradeStep + 1) + "/" + all.size() + ")", "Digging");
        int speed = (int) Math.max(4, 12 / (1 + npc.affix(Affix.DILIGENCE)));
        if (++actionTimer < speed) {
            return;
        }
        actionTimer = 0;
        if (a.isDig()) {
            // seal water, lava and loose blocks around the cell BEFORE opening it, so the tunnel cannot flood
            if (!preSeal(wp, a.pos())) {
                status("Need blocks to seal the excavation (water or loose ground ahead)", "Waiting");
                state = State.STORAGE;
                return;
            }
            BlockState s = level().getBlockState(a.pos());
            if (!s.isAir() && !s.is(Blocks.WALL_TORCH)) {
                BlockEntity be = level().getBlockEntity(a.pos());
                List<ItemStack> drops = s.getFluidState().isEmpty() ? Block.getDrops(s, level(), a.pos(), be, npc, npc.tool()) : List.of();
                level().setBlock(a.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level().levelEvent(2001, a.pos(), Block.getId(s));
                for (ItemStack d : drops) {
                    ItemStack rest = Crafting.addAll(npc.inventory(), d);
                    if (!rest.isEmpty()) {
                        npc.spawnAtLocation(rest);
                    }
                }
                npc.swing(InteractionHand.MAIN_HAND);
            }
            if (!seal(wp, a.pos())) {
                state = State.STORAGE;
                return;
            }
        } else {
            boolean torch = a.place().is(Blocks.WALL_TORCH);
            Predicate<ItemStack> need = torch ? TORCH : STAIRS;
            if (!Inv.consume(npc.inventory(), need, 1)) {
                state = State.STORAGE;
                return;
            }
            level().setBlock(a.pos(), a.place(), Block.UPDATE_ALL);
            level().playSound(null, a.pos(), torch ? SoundEvents.WOOD_PLACE : SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
            npc.swing(InteractionHand.MAIN_HAND);
        }
        wp.upgradeStep++;
        data().setDirty();
    }

    /** All excavation steps already completed for this mine, in dig order. */
    private List<MineLayout.Action> completedActions(WorkplaceState wp) {
        List<MineLayout.Action> out = new java.util.ArrayList<>();
        for (int t = 1; t <= wp.tier; t++) {
            out.addAll(MineLayout.segmentActions(wp, t));
        }
        if (wp.upgrading()) {
            List<MineLayout.Action> next = MineLayout.segmentActions(wp, wp.tier + 1);
            out.addAll(next.subList(0, Math.min(next.size(), wp.upgradeStep)));
        }
        return out;
    }

    /** Finds dug cells that have been refilled (falling sand, cave-ins, mobs, players). */
    private boolean findBlockedCells(WorkplaceState wp) {
        blocked.clear();
        for (MineLayout.Action a : completedActions(wp)) {
            if (a.isDig()) {
                BlockState s = level().getBlockState(a.pos());
                if (!s.isAir() && !s.is(Blocks.WALL_TORCH) && !s.is(Blocks.TORCH)) {
                    blocked.add(a.pos());
                }
            }
        }
        return !blocked.isEmpty();
    }

    /** Clears blocked cells from the entrance inward so the staircase and return route stay navigable. */
    private void clear(WorkplaceState wp) {
        if (blocked.isEmpty()) {
            state = State.TRAVEL;
            return;
        }
        BlockPos cell = blocked.peek();
        BlockState s = level().getBlockState(cell);
        if (s.isAir() || s.is(Blocks.WALL_TORCH)) {
            blocked.poll();
            return;
        }
        List<MineLayout.Action> done = completedActions(wp);
        int idx = 0;
        for (int i = 0; i < done.size(); i++) {
            if (done.get(i).pos().equals(cell)) {
                idx = i;
                break;
            }
        }
        double dist = npc.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(cell));
        if (dist > 4.5) {
            BlockPos approach = idx == 0 ? MineLayout.entrance(wp) : lastOpenBefore(wp, done, idx);
            if (!goTo(approach, 1.5, "a blocked passage in mine #" + wp.id)) {
                return;
            }
        }
        lookAt(cell);
        status("Clearing a cave-in or flood in mine #" + wp.id + " (" + blocked.size() + " cells)", "Digging");
        if (++actionTimer < 10) {
            return;
        }
        actionTimer = 0;
        if (!preSeal(wp, cell)) {
            status("Mine #" + wp.id + " needs blocks to seal a flood", "Waiting");
            state = State.STORAGE;
            return;
        }
        List<ItemStack> drops = s.getFluidState().isEmpty() ? Block.getDrops(s, level(), cell, level().getBlockEntity(cell), npc, npc.tool()) : List.of();
        level().setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level().levelEvent(2001, cell, Block.getId(s));
        for (ItemStack d : drops) {
            ItemStack rest = Crafting.addAll(npc.inventory(), d);
            if (!rest.isEmpty()) {
                npc.spawnAtLocation(rest);
            }
        }
        npc.swing(InteractionHand.MAIN_HAND);
        openCache = null;
        sealAround(wp, cell, MineLayout.openCells(wp, wp.tier + (wp.upgrading() ? 1 : 0)));
        blocked.poll();
    }

    /** Seals unsafe neighbours with carried blocks (best effort while clearing). */
    private void sealAround(WorkplaceState wp, BlockPos dug, Set<BlockPos> open) {
        for (Direction d : Direction.values()) {
            BlockPos n = dug.relative(d);
            BlockState ns = level().getBlockState(n);
            if (!ns.getFluidState().isEmpty() || (!open.contains(n) && unsafeNeighbour(ns))) {
                placeSeal(n);
            }
        }
    }

    /** The newest already-open cell before step i, to stand next to the face being dug. */
    private BlockPos lastOpenBefore(WorkplaceState wp, List<MineLayout.Action> all, int i) {
        for (int j = i - 1; j >= 0; j--) {
            MineLayout.Action a = all.get(j);
            if (a.isDig() && level().getBlockState(a.pos()).isAir() && level().getBlockState(a.pos().above()).isAir()
                    && !level().getBlockState(a.pos().below()).isAir()) {
                return a.pos();
            }
        }
        return MineLayout.workPos(wp);
    }

    /**
     * Seals the non-tunnel neighbours of a cell that hold fluid or loose blocks. Requires enough sealing blocks
     * for all of them first, so a cell is never opened half-sealed.
     */
    private boolean preSeal(WorkplaceState wp, BlockPos cell) {
        Set<BlockPos> open = openCells(wp);
        List<BlockPos> todo = new java.util.ArrayList<>();
        for (Direction d : Direction.values()) {
            BlockPos n = cell.relative(d);
            BlockState ns = level().getBlockState(n);
            // Fluids are plugged even inside the planned tunnel: an undug future cell full of aquifer water would
            // otherwise drain into the finished part. The plug is dug out later, once its own neighbours are sealed.
            if (!ns.getFluidState().isEmpty() || (!open.contains(n) && ns.getBlock() instanceof FallingBlock)) {
                todo.add(n);
            }
        }
        if (Inv.count(npc.inventory(), SEAL) < todo.size()) {
            return false;
        }
        for (BlockPos n : todo) {
            placeSeal(n);
        }
        return true;
    }

    /** After digging: cave openings and gaps next to the new cell are closed so mobs and fluids stay out. */
    private boolean seal(WorkplaceState wp, BlockPos dug) {
        Set<BlockPos> open = openCells(wp);
        for (Direction d : Direction.values()) {
            BlockPos n = dug.relative(d);
            BlockState ns = level().getBlockState(n);
            boolean fluid = !ns.getFluidState().isEmpty();
            if ((fluid || (!open.contains(n) && unsafeNeighbour(ns))) && !placeSeal(n)) {
                return false;
            }
        }
        return true;
    }

    private void finishUpgrade(WorkplaceState wp) {
        wp.tier++;
        wp.upgradeStep = -1;
        progress = 0;
        openCache = null;
        data().setDirty();
        npc.awardXp(20);
        Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Mine #" + wp.id + " reached tier " + wp.tier
                + " (" + npc.npcName() + ")"));
        state = State.TRAVEL;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (npc.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            WorkplaceState wp = wp();
            if (wp != null) {
                sl.destroyBlockProgress(npc.getId(), MineLayout.facePos(wp), -1);
            }
        }
        if (state == State.WORK || state == State.EXCAVATE || state == State.CLEAR) {
            state = State.TRAVEL;
        }
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state.name());
        tag.putInt("progress", progress);
        tag.putLong("nextUpgrade", nextUpgradeCheck);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            state = State.CHECK;
        }
        if (state == State.WORK || state == State.EXCAVATE || state == State.CRAFT || state == State.CLEAR) {
            state = State.TRAVEL;
        }
        progress = tag.getInt("progress");
        nextUpgradeCheck = tag.getLong("nextUpgrade");
    }
}
