package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.logic.SpiralMine;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcNavigator;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Miner that mines like a player (spec 6, reworked): it walks down a spiral shaft to its mine level and digs a
 * branch mine block by block, with real break times for its pickaxe and the block's hardness. Everything it gets is
 * what is really in the ground and drops by the vanilla rules (an iron ore broken with a wooden pickaxe drops
 * nothing). Exposed ore veins are followed, torches are placed, fluids and loose blocks are plugged before a cell
 * is opened, and it digs the shaft deeper to reach the next tier.
 */
public class MinerJob extends Job {
    enum State { CHECK, STORAGE, CRAFT, TRAVEL, MINE, EXTEND, CLEAR }

    static final Predicate<ItemStack> TORCH = s -> s.is(Items.TORCH);
    /** Solid, non-falling blocks a miner may use to plug water, lava or sand. */
    static final Predicate<ItemStack> SEAL = s -> s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE) || s.is(Items.STONE)
            || s.is(Items.DIRT) || s.is(Items.ANDESITE) || s.is(Items.DIORITE) || s.is(Items.GRANITE) || s.is(Items.TUFF)
            || s.is(Items.DEEPSLATE) || s.is(Items.COARSE_DIRT) || s.is(Items.NETHERRACK);
    /** Common blocks a player stops collecting once the warehouse has plenty. */
    private static final Set<Item> JUNK = Set.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.GRAVEL,
            Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF, Items.CALCITE, Items.SAND, Items.DEEPSLATE);
    private static final int JUNK_STOCK_LIMIT = 512;
    private static final int MAX_VEIN = 24;

    private State state = State.CHECK;
    private final BlockWork.Breaker breaker = new BlockWork.Breaker();
    private final Deque<BlockPos> vein = new ArrayDeque<>();
    private final Deque<BlockPos> blocked = new ArrayDeque<>();
    private final Set<Item> junkFull = new HashSet<>();
    private long nextIntegrityCheck;
    private int craftTimer;

    /** Miners without the constructed mine work a cave a cave explorer surveyed. */
    private final CaveMinerJob caveJob;

    public MinerJob(FactionNpc npc) {
        super(npc);
        caveJob = new CaveMinerJob(npc);
    }

    private WorkplaceState wp() {
        return Workplaces.claim(npc, WorkplaceState.Type.MINE);
    }

    /**
     * The surveyed cave this miner works, if any: its own one, or - when it has no constructed mine and none is
     * free - a cave with marked ore that nobody works.
     */
    private WorkplaceState caveWork() {
        WorkplaceState cave = Workplaces.held(npc, WorkplaceState.Type.CAVE);
        WorkplaceState mine = Workplaces.held(npc, WorkplaceState.Type.MINE);
        if (cave == null && mine != null && exhausted(mine)) {
            // nothing left to dig in the constructed mine: a surveyed cave with ore is the next best work
            return Workplaces.claim(npc, WorkplaceState.Type.CAVE, w -> !w.workedOut && !w.marks.isEmpty());
        }
        if (cave != null || mine != null) {
            return cave;
        }
        if (Workplaces.claim(npc, WorkplaceState.Type.MINE) != null) {
            return null;
        }
        return Workplaces.claim(npc, WorkplaceState.Type.CAVE, w -> !w.workedOut && !w.marks.isEmpty());
    }

    @Override
    public String workplaceInfo() {
        if (Workplaces.held(npc, WorkplaceState.Type.CAVE) != null) {
            return caveJob.workplaceInfo();
        }
        WorkplaceState wp = wp();
        if (wp == null) {
            return "no mine";
        }
        int lv = MineLayout.level(wp);
        return "Mine #" + wp.id + " tier " + wp.tier + (wp.subLevel > 0 ? " (+" + wp.subLevel + " floors)" : "")
                + (wp.revisit > 0 ? ", lengthening round " + wp.reach : wp.reach > 0 ? ", lengthened " + wp.reach + "x" : "")
                + ", level y=" + SpiralMine.levelY(wp.origin.getY(), lv) + ", branch mine " + wp.progress + "/"
                + MineLayout.tunnelPlan(wp).size() + (wp.upgrading() ? ", digging deeper (step " + wp.upgradeStep + ")" : "");
    }

    /** Every level the mine can have (down to the mining floor, plus the infill levels) is dug out. */
    static boolean exhausted(WorkplaceState wp) {
        int lv = MineLayout.deepest(wp);
        return !wp.upgrading() && wp.revisit == 0 && wp.progress >= MineLayout.tunnelPlan(wp).size() && MineLayout.landingSteps(wp, lv + 1) < 0;
    }

    /** Watchdog for the current plan cell: which one, and how long the miner has been at it. */
    private int faceProgress = -1, faceTicks;
    /** PROPOSED: ticks spent on one plan cell (vein mining excluded) before it is given up as unreachable (or
     *  twice as long while the miner is still more than 10 blocks from it). */
    static final int FACE_GIVE_UP_TICKS = 1200;

    /** PROPOSED: how many times a worked-out mine's branch mines are lengthened (another 40 blocks out each time). */
    static final int MAX_REACH = 4;

    /**
     * Every level is dug: like a player lengthening a branch mine, the miner goes back to the regular levels (tiers
     * 1-4 and the floors below) from the deepest up and continues each main tunnel further out, with its branches.
     */
    private void startLengthening(WorkplaceState wp) {
        wp.reach++;
        wp.revisit = Math.min(SpiralMine.regularLevels(wp.origin.getY()), MineLayout.deepest(wp)); // only levels already dug
        wp.progress = SpiralMine.planSize(wp.reach - 1);
        data().setDirty();
        state = State.STORAGE;
        Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Mine #" + wp.id + ": every level is dug - "
                + npc.npcName() + " lengthens the branch mines, starting at y=" + SpiralMine.levelY(wp.origin.getY(), wp.revisit)));
    }

    /** The level being lengthened is done: on to the next regular level up, or back to normal once all are. */
    private void nextLengthening(WorkplaceState wp) {
        wp.revisit--;
        if (wp.revisit <= 0) {
            wp.revisit = 0;
            wp.progress = SpiralMine.planSize(0); // the deepest level stays as it was: worked out
        } else {
            wp.progress = SpiralMine.planSize(wp.reach - 1);
        }
        data().setDirty();
        npc.awardXp(20);
    }

    /**
     * The current bottom is dug out but the next level needs an iron pickaxe in hand and the faction has none: like a
     * player without iron gear, branch-mine further out on the levels already reached (where the iron is) meanwhile.
     */
    private boolean blockedDeeper(WorkplaceState wp) {
        int lv = MineLayout.deepest(wp);
        return !wp.upgrading() && wp.revisit == 0 && wp.progress >= MineLayout.tunnelPlan(wp).size()
                && MineLayout.landingSteps(wp, lv + 1) >= 0 && wp.tier + 1 >= 3 && wp.tier < WorkplaceState.Type.MINE.maxTier
                && pickTier() < 2 && base() != null
                && FactionStorage.count(level(), base(), s -> ToolKind.PICKAXE.matches(s) && ToolKind.tierOf(s) >= 2) == 0;
    }

    private int pickTier() {
        return ToolKind.PICKAXE.matches(npc.tool()) ? ToolKind.tierOf(npc.tool()) : -1;
    }

    @Override
    protected int keep(ItemStack s) {
        if (TORCH.test(s)) {
            return 32;
        }
        if (SEAL.test(s)) {
            return 64;
        }
        if (s.is(Items.STICK)) {
            return 8;
        }
        return 0;
    }

    /** Breaks a block at player speed; its drops go straight into the backpack (junk it does not want is tossed). */
    private boolean dig(WorkplaceState wp, BlockPos pos) {
        if (!breaker.tick(npc, pos, this::wanted)) {
            return false;
        }
        if (wp != null && breaker.lastGained() > 0) {
            wp.totalOutput += breaker.lastGained();
            data().setDirty();
        }
        return true;
    }

    /** Pick-up filter: everything except surplus junk the warehouse is already full of. */
    private boolean wanted(ItemStack s) {
        if (JUNK.contains(s.getItem()) && junkFull.contains(s.getItem())) {
            return Inv.count(npc.inventory(), x -> x.is(s.getItem())) < 64;
        }
        return true;
    }

    private int collect() {
        int n = BlockWork.pickUp(npc, this::wanted);
        if (n > 0) {
            WorkplaceState wp = wp();
            if (wp != null) {
                wp.totalOutput += n;
                data().setDirty();
            }
        }
        return n;
    }

    /** Since when this miner has had nothing to dig (no free mine, no cave, its mine done for good); -1 = busy. */
    private long idleSince = -1;

    /** True when the miner has had no mine or cave to work for at least {@code ticks}. */
    public boolean idleFor(long ticks) {
        return idleSince >= 0 && now() - idleSince >= ticks;
    }

    @Override
    protected void run() {
        if (caveWork() != null) {
            idleSince = -1;
            caveJob.tick();
            return;
        }
        WorkplaceState wp = wp();
        if (wp != null && (exhausted(wp) || blockedDeeper(wp)) && wp.reach < MAX_REACH) {
            startLengthening(wp);
        }
        if (wp != null && exhausted(wp) && state != State.STORAGE) {
            if (hasCargo()) {
                state = State.STORAGE; // bring the last load home first
                return;
            }
            if (!leaveMine(wp)) {
                return;
            }
            status("Mine #" + wp.id + " is fully worked out: every level is dug", "Idle");
            if (idleSince < 0) {
                idleSince = now();
            }
            waitFor(1200);
            return;
        }
        if (wp == null) {
            if (idleSince < 0) {
                idleSince = now();
            }
            status("No free mine to work", "Idle");
            waitFor(200);
            return;
        }
        idleSince = -1;
        collect();
        switch (state) {
            case CHECK -> state = pickTier() < 0 || Inv.freeSlots(npc.inventory()) <= 1 ? State.STORAGE : State.TRAVEL;
            case STORAGE -> storage(wp);
            case CRAFT -> craftTorches(wp);
            case TRAVEL -> travel(wp);
            case MINE -> mine(wp);
            case EXTEND -> extend(wp);
            case CLEAR -> clear(wp);
        }
    }

    // ------------------------------------------------------------------ moving through the mine

    /**
     * Walks to {@code dest} through the mine in short legs: along a tunnel to its landing, up or down the spiral six
     * steps at a time, then to the destination. {@code destStep} is the spiral step of the destination's level
     * (0 = surface). Returns true once at {@code dest}.
     */
    private boolean walkInMine(WorkplaceState wp, BlockPos dest, int destStep, double reach, String label) {
        BlockPos p = npc.blockPosition();
        // swimming (a flooded cell or a lake above the tunnels): only a real tunnel position counts, otherwise the
        // way back is through the mine entrance
        int lv = npc.isInWater() ? MineLayout.tunnelLevelAt(wp, p) : MineLayout.nearLevelAt(wp, p);
        int cur;
        if (lv > 0) {
            int lk = MineLayout.landingSteps(wp, lv);
            if (lk == destStep) {
                return goTo(dest, reach, label);
            }
            BlockPos landing = MineLayout.stand(wp, lk);
            if (!NpcNavigator.within(npc, landing, 1.2)) {
                goTo(landing, 1.2, "the mine shaft");
                return false;
            }
            cur = lk;
        } else {
            cur = MineLayout.stepAt(wp, p);
            int depth = wp.origin.getY() - p.getY();
            double hx = p.getX() - wp.origin.getX(), hz = p.getZ() - wp.origin.getZ();
            if (cur == 0 && depth > 3 && hx * hx + hz * hz < 14 * 14) {
                // underground beside the shaft (a side pocket, a cave): first get onto the stair at this height
                int deepestDug = MineLayout.landingSteps(wp, MineLayout.deepest(wp) + (wp.upgrading() ? 1 : 0));
                if (deepestDug < 0) {
                    deepestDug = MineLayout.landingSteps(wp, MineLayout.deepest(wp));
                }
                int k = Math.min(depth, Math.max(1, deepestDug));
                if (!goTo(MineLayout.stand(wp, k), 1.2, "the mine stairs")) {
                    return false;
                }
                cur = k;
            }
        }
        if (cur == destStep || (destStep == 0 && cur == 0)) {
            return goTo(dest, reach, label);
        }
        int next = cur < destStep ? Math.min(cur + 6, destStep) : Math.max(cur - 6, destStep);
        // the next stair must be open: a cave-in, falling sand or a block plugged against water fills it, and a
        // player clears it before walking on (digging from where it stands)
        int dir = destStep > cur ? 1 : -1;
        for (int k = cur + dir; k > 0 && (dir > 0 ? k <= next : k >= next); k += dir) {
            BlockPos step = MineLayout.stand(wp, k);
            if (floorMissing(step) && !noSealBlocks && Inv.count(npc.inventory(), SEAL) > 0) {
                // a stair whose floor is gone (dug away, washed out): put a block back, as a player would
                if (!BlockWork.inReach(npc, step.below(), 4.5)) {
                    goTo(MineLayout.stand(wp, k - dir), 0.45, label);
                    return false;
                }
                status("Repairing the mine stairs (mine #" + wp.id + ")", "Building");
                if (placeSeal(step.below())) {
                    lookAt(step.below());
                    npc.swing(InteractionHand.MAIN_HAND);
                    return false;
                }
            }
            BlockPos[] clearance = dir > 0 ? new BlockPos[]{step.above(), step}
                    : new BlockPos[]{MineLayout.stand(wp, k - dir).above(2), step.above(), step};
            for (BlockPos q : clearance) {
                if (passable(level().getBlockState(q))) {
                    continue;
                }
                if (!BlockWork.inReach(npc, q, 4.5)) {
                    // walk down (or up) to the stair just before the blocked one, then clear it from there
                    goTo(MineLayout.stand(wp, k - dir), 0.45, label);
                    return false;
                }
                status("Clearing the mine stairs (mine #" + wp.id + ")", "Digging");
                if (!preSeal(q, MineLayout.landingSteps(wp, MineLayout.deepest(wp)))) {
                    state = State.STORAGE;
                    return false;
                }
                if (!level().getBlockState(q).getFluidState().isEmpty()) {
                    level().setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                } else {
                    dig(wp, q);
                }
                return false;
            }
        }
        // spiral steps are diagonal neighbours: arrive precisely, or the next leg would be "reached" without moving
        goTo(MineLayout.stand(wp, next), 0.45, label);
        return false;
    }

    private boolean leaveMine(WorkplaceState wp) {
        boolean underground = npc.getBlockY() < wp.origin.getY() - 1 && npc.blockPosition().closerThan(wp.origin, 64);
        boolean inHut = MineLayout.forwardOf(wp, npc.blockPosition()) > MineLayout.HUT_BACK - 1
                && MineLayout.forwardOf(wp, npc.blockPosition()) <= MineLayout.HUT_FRONT
                && Math.abs(MineLayout.sideOf(wp, npc.blockPosition())) <= 2 && npc.blockPosition().closerThan(wp.origin, 7);
        if (!underground && !inHut) {
            return true;
        }
        return walkInMine(wp, MineLayout.entrance(wp), 0, 1.5, "the mine exit");
    }

    // ------------------------------------------------------------------ storehouse

    private void storage(WorkplaceState wp) {
        breaker.reset(npc);
        if (!leaveMine(wp) || !atStorage()) {
            return;
        }
        if (unloadNow() == Unload.PARTIAL_FULL && Inv.freeSlots(npc.inventory()) < 3) {
            status("Storehouse full - holding the mine's output", "Waiting");
            waitFor(200);
            return;
        }
        upgradeEquipment();
        if (pickTier() < 0) {
            status("No pickaxe in storage - waiting for the blacksmith", "Waiting");
            waitFor(300);
            return;
        }
        junkFull.clear();
        for (Item junk : JUNK) {
            if (FactionStorage.count(level(), base(), s -> s.is(junk)) >= JUNK_STOCK_LIMIT) {
                junkFull.add(junk);
            }
        }
        int torches = Inv.count(npc.inventory(), TORCH);
        if (torches < 16) {
            takeFromStorage(TORCH, 24 - torches);
        }
        if (Inv.count(npc.inventory(), SEAL) < 24) {
            takeFromStorage(SEAL, 32);
        }
        noSealBlocks = Inv.count(npc.inventory(), SEAL) == 0;
        if (!wp.upgrading()) {
            considerGoingDeeper(wp);
        }
        if (Inv.count(npc.inventory(), TORCH) < 8) {
            takeFromStorage(Crafting.FUEL_COAL, 4);
            takeFromStorage(s -> s.is(Items.STICK), 4);
            if (Inv.count(npc.inventory(), s -> s.is(Items.STICK)) < 2) {
                takeFromStorage(s -> s.is(ItemTags.PLANKS), 2);
            }
            if (Inv.count(npc.inventory(), Crafting.FUEL_COAL) > 0) {
                state = State.CRAFT;
                return;
            }
        }
        state = State.TRAVEL;
    }

    /**
     * Goes deeper when the current level is mostly worked out and the next level is worth it: a tier upgrade costs a
     * real pickaxe (PROPOSED: stone for tier 2, iron for 3 and 4) plus torches; floors below tier 4 cost torches only.
     * Tiers 3+ need an iron pickaxe, otherwise their ores would not drop.
     */
    private void considerGoingDeeper(WorkplaceState wp) {
        if (wp.revisit > 0) {
            return; // lengthening the upper levels: the shaft is already at the bottom
        }
        int lv = MineLayout.deepest(wp);
        if (MineLayout.landingSteps(wp, lv + 1) < 0) {
            return;
        }
        double done = wp.progress / (double) Math.max(1, MineLayout.tunnelPlan(wp).size());
        if (done < 0.6) {
            return;
        }
        int nextTier = wp.tier + 1;
        if (wp.tier < WorkplaceState.Type.MINE.maxTier) {
            int needPick = MineLayout.upgradePickTier(nextTier);
            if (nextTier >= 3 && pickTier() < 2) {
                return;
            }
            Predicate<ItemStack> pay = s -> ToolKind.PICKAXE.matches(s) && ToolKind.tierOf(s) >= needPick;
            ItemStack pick = FactionStorage.findBest(level(), base(), pay, s -> -ToolKind.tierOf(s));
            ItemStack paid = pick.isEmpty() ? ItemStack.EMPTY : FactionStorage.extractExact(level(), base(), pick);
            if (paid.isEmpty()) {
                if (done < 1.0) {
                    return; // going down early needs the upgrade payment
                }
                // the level is worked out: rather than stand idle, the miner digs on with its own pickaxe
                Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Mine #" + wp.id + ": level worked out, "
                        + npc.npcName() + " digs down to tier " + nextTier));
            } else {
                Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Mine #" + wp.id + ": " + npc.npcName()
                        + " starts digging down to tier " + nextTier + " (paid " + paid.getHoverName().getString() + ")"));
            }
        }
        wp.upgradeStep = 0;
        data().setDirty();
    }

    private void craftTorches(WorkplaceState wp) {
        BaseState b = base();
        if (!goTo(b.point(BaseState.WORKSHOP_STAND), 2.0, "the crafting table")) {
            return;
        }
        lookAt(b.point(BaseState.CRAFTING));
        status("Crafting torches", "Crafting");
        if (++craftTimer < 20) {
            return;
        }
        craftTimer = 0;
        Crafting.ensureSticks(npc.inventory(), 1);
        if (Inv.count(npc.inventory(), TORCH) < 32 && Crafting.craft(npc.inventory(), Crafting.TORCHES, 1) > 0) {
            npc.swing(InteractionHand.MAIN_HAND);
            return;
        }
        state = State.TRAVEL;
    }

    // ------------------------------------------------------------------ travel and integrity

    private void travel(WorkplaceState wp) {
        if (Inv.freeSlots(npc.inventory()) <= 1) {
            state = State.STORAGE;
            return;
        }
        if (now() >= nextIntegrityCheck) {
            nextIntegrityCheck = now() + 1200;
            if (findBlocked(wp)) {
                state = State.CLEAR;
                return;
            }
        }
        int lv = MineLayout.level(wp);
        if (wp.upgrading()) {
            if (walkInMine(wp, MineLayout.landing(wp, lv), MineLayout.landingSteps(wp, lv), 1.2, "mine #" + wp.id)) {
                state = State.EXTEND;
            }
            return;
        }
        List<SpiralMine.Cell> plan = MineLayout.tunnelPlan(wp);
        BlockPos dest = wp.progress >= plan.size() ? MineLayout.landing(wp, lv) : approach(wp, plan, wp.progress);
        // already down in this level's tunnels: start working from here (mining handles walking, reach and bridging)
        boolean inLevel = !npc.isInWater() && MineLayout.nearLevelAt(wp, npc.blockPosition()) == lv && wp.progress < plan.size();
        if (inLevel || walkInMine(wp, dest, MineLayout.landingSteps(wp, lv), 1.2, "mine #" + wp.id)) {
            state = State.MINE;
        }
    }

    /**
     * Where to stand to dig plan cell i: the already-dug, walkable floor cell nearest to it (e.g. the junction when a
     * new branch starts), else the level's landing.
     */
    private BlockPos approach(WorkplaceState wp, List<SpiralMine.Cell> plan, int i) {
        BlockPos target = MineLayout.tunnelPos(wp, plan.get(Math.min(i, plan.size() - 1)));
        BlockPos best = MineLayout.landing(wp, MineLayout.level(wp));
        double bestD = best.distSqr(target);
        for (int j = Math.min(i, plan.size()) - 1; j >= 0; j--) {
            SpiralMine.Cell c = plan.get(j);
            if (c.torch() || c.dy() != 0) {
                continue;
            }
            BlockPos p = MineLayout.tunnelPos(wp, c);
            double d = p.distSqr(target);
            if (d >= bestD) {
                continue;
            }
            BlockState s = level().getBlockState(p);
            if ((s.isAir() || s.is(Blocks.TORCH)) && level().getBlockState(p.above()).isAir() && !level().canSeeSky(p)
                    && !level().getBlockState(p.below()).getCollisionShape(level(), p.below()).isEmpty()) {
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    /** Dug cells that have been refilled (falling gravel, cave-ins, flooding). */
    private boolean findBlocked(WorkplaceState wp) {
        blocked.clear();
        int steps = MineLayout.landingSteps(wp, MineLayout.level(wp));
        for (SpiralMine.Cell c : SpiralMine.spiralPlan(0, steps)) {
            BlockPos p = MineLayout.spiralPos(wp, c);
            if (!c.torch() && !passable(level().getBlockState(p))) {
                blocked.add(p);
            }
        }
        List<SpiralMine.Cell> plan = MineLayout.tunnelPlan(wp);
        for (int i = 0; i < Math.min(wp.progress, plan.size()); i++) {
            SpiralMine.Cell c = plan.get(i);
            BlockPos p = MineLayout.tunnelPos(wp, c);
            if (!c.torch() && !passable(level().getBlockState(p))) {
                blocked.add(p);
            }
        }
        return !blocked.isEmpty();
    }

    private static boolean passable(BlockState s) {
        return s.isAir() || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH);
    }

    private void clear(WorkplaceState wp) {
        BlockPos cell = blocked.peek();
        if (cell == null) {
            state = State.TRAVEL;
            return;
        }
        BlockState s = level().getBlockState(cell);
        if (passable(s)) {
            blocked.poll();
            return;
        }
        if (!BlockWork.inReach(npc, cell, 4.5)) {
            int step = MineLayout.stepAt(wp, cell.above(0));
            BlockPos stand;
            int destStep;
            if (step > 0 || MineLayout.forwardOf(wp, cell) < SpiralMine.MAIN_START) {
                int k = Math.max(0, Math.min(MineLayout.landingSteps(wp, MineLayout.level(wp)),
                        wp.origin.getY() - cell.getY() - 1));
                stand = MineLayout.stand(wp, k);
                destStep = k;
            } else {
                stand = MineLayout.landing(wp, MineLayout.level(wp));
                destStep = MineLayout.landingSteps(wp, MineLayout.level(wp));
            }
            if (!walkInMine(wp, stand, destStep, 1.2, "a blocked passage in mine #" + wp.id)) {
                return;
            }
            if (!BlockWork.inReach(npc, cell, 5.0)) {
                blocked.poll(); // cannot reach from the route; try again on the next inspection
                return;
            }
        }
        status("Clearing a cave-in in mine #" + wp.id + " (" + blocked.size() + " cells)", "Digging");
        if (!preSeal(cell, MineLayout.landingSteps(wp, MineLayout.deepest(wp)))) {
            state = State.STORAGE;
            return;
        }
        if (s.getFluidState().isEmpty()) {
            if (dig(wp, cell)) {
                blocked.poll();
            }
        } else {
            level().setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            blocked.poll();
        }
    }

    // ------------------------------------------------------------------ branch mining

    static boolean isOre(BlockState s) {
        return s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES) || s.is(BlockTags.GOLD_ORES)
                || s.is(BlockTags.REDSTONE_ORES) || s.is(BlockTags.LAPIS_ORES) || s.is(BlockTags.DIAMOND_ORES)
                || s.is(BlockTags.EMERALD_ORES);
    }

    /** Experience for the miner per ore block it broke and could harvest (cobblestone and stone give none). */
    public static int oreXp(BlockState s) {
        if (s.is(BlockTags.EMERALD_ORES)) {
            return 12;
        }
        if (s.is(BlockTags.DIAMOND_ORES)) {
            return 10;
        }
        if (s.is(BlockTags.GOLD_ORES)) {
            return 6;
        }
        if (s.is(BlockTags.IRON_ORES) || s.is(BlockTags.LAPIS_ORES)) {
            return 4;
        }
        if (s.is(BlockTags.REDSTONE_ORES)) {
            return 3;
        }
        if (s.is(BlockTags.COAL_ORES) || s.is(BlockTags.COPPER_ORES)) {
            return 2;
        }
        return 0;
    }

    private void noticeOres(BlockPos opened) {
        var mines = com.factionclash.campaign.CampaignData.get(npc.getServer()).workplaces.values();
        for (Direction d : Direction.values()) {
            BlockPos n = opened.relative(d);
            if (mines.stream().anyMatch(w -> w.type == WorkplaceState.Type.MINE && MineLayout.nearStairs(w, n))) {
                continue; // ore in a stair floor or the rock holding the stairs stays: mining it breaks the way down
            }
            if (vein.size() < MAX_VEIN && !vein.contains(n) && isOre(level().getBlockState(n))) {
                vein.add(n);
            }
        }
    }

    private int unreachable;
    /** Set at the storehouse when neither the miner nor storage has any block to plug water with. */
    private boolean noSealBlocks;

    private void mine(WorkplaceState wp) {
        if (Inv.freeSlots(npc.inventory()) <= 1) {
            breaker.reset(npc);
            state = State.STORAGE;
            return;
        }
        int landingStep = MineLayout.landingSteps(wp, MineLayout.level(wp));
        // a player mines the ore it just uncovered before moving on
        if (!vein.isEmpty()) {
            BlockPos ore = vein.peek();
            BlockState os = level().getBlockState(ore);
            if (!isOre(os) || !BlockWork.inReach(npc, ore, 5.0)) {
                vein.poll();
                return;
            }
            if (!preSeal(ore, landingStep)) {
                vein.poll();
                return;
            }
            status("Mining a " + os.getBlock().getName().getString() + " vein (mine #" + wp.id + ")", "Mining");
            if (dig(wp, ore)) {
                if (BlockWork.canHarvest(os, npc.getMainHandItem())) {
                    npc.awardXp(oreXp(os));
                    prospect(os, ore);
                }
                vein.poll();
                noticeOres(ore);
            }
            return;
        }
        List<SpiralMine.Cell> plan = MineLayout.tunnelPlan(wp);
        if (wp.progress != faceProgress) {
            faceProgress = wp.progress;
            faceTicks = 0;
        } else if (wp.progress < plan.size() && ++faceTicks > (npc.blockPosition()
                .closerThan(MineLayout.tunnelPos(wp, plan.get(wp.progress)), 10) ? FACE_GIVE_UP_TICKS : 2 * FACE_GIVE_UP_TICKS)) {
            // a full minute on one cell of the plan without getting it done (it cannot be reached from any floor the
            // miner can walk to, e.g. across a cavern): leave it, as a player would, and what lies beyond it
            faceTicks = 0;
            SpiralMine.Cell lost = plan.get(wp.progress);
            int side = MineLayout.landingSide(wp, MineLayout.level(wp));
            if (lost.s() == side) {
                // the main tunnel is cut (a cavern too wide to bridge): everything further out lies beyond the gap
                wp.progress = plan.size();
            } else {
                // a side branch cut off: on to the next branch
                int dir = Integer.signum(lost.s() - side);
                while (wp.progress < plan.size() && plan.get(wp.progress).f() == lost.f()
                        && Integer.signum(plan.get(wp.progress).s() - side) == dir) {
                    wp.progress++;
                }
            }
            data().setDirty();
            npc.navigator().reset();
            return;
        }
        if (wp.progress >= plan.size() && wp.revisit > 0) {
            nextLengthening(wp);
            state = State.STORAGE;
            return;
        }
        if (wp.progress >= plan.size()) {
            status("Mine level worked out - going to prepare the next level", "Waiting");
            state = State.STORAGE;
            waitFor(100);
            return;
        }
        SpiralMine.Cell c = plan.get(wp.progress);
        BlockPos pos = MineLayout.tunnelPos(wp, c);
        BlockState s = level().getBlockState(pos);
        if (c.torch()) {
            if (s.isAir() && Inv.consume(npc.inventory(), TORCH, 1)) {
                level().setBlock(pos, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
                level().playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
                npc.swing(InteractionHand.MAIN_HAND);
            }
            wp.progress++;
            data().setDirty();
            return;
        }
        if (level().canSeeSky(pos) || level().canSeeSky(pos.above())) {
            // the tunnel came out of a hillside: a player stops the branch there instead of opening the mine to the
            // surface (and to mobs)
            wp.progress++;
            data().setDirty();
            return;
        }
        if (passable(s)) {
            if (c.dy() == 0 && floorMissing(pos) && !noSealBlocks) {
                // an open cave cell: walk up and bridge its floor before going on, so the tunnel stays walkable
                if (!BlockWork.inReach(npc, pos.below(), 4.5)) {
                    if (!bridgeNearbyHoles(wp, plan)
                            && walkInMine(wp, approach(wp, plan, wp.progress), landingStep, 1.0, "the tunnel face") && ++unreachable > 60) {
                        // at the last cell with a floor and the gap is still out of reach (a wide cavern): leave it open
                        unreachable = 0;
                        wp.progress++;
                        data().setDirty();
                    }
                    return;
                }
                bridgeFloor(pos);
            }
            wp.progress++;
            data().setDirty();
            noticeOres(pos);
            return;
        }
        if (!BlockWork.inReach(npc, pos, 4.5)) {
            if (bridgeNearbyHoles(wp, plan)) {
                return;
            }
            BlockPos stand = approach(wp, plan, wp.progress);
            if (walkInMine(wp, stand, landingStep, 1.0, "the tunnel face") && ++unreachable > 60) {
                // standing at the closest dug cell and still out of reach (e.g. a cave gap without floor): skip it
                unreachable = 0;
                wp.progress++;
                data().setDirty();
            }
            return;
        }
        unreachable = 0;
        if (!preSeal(pos, landingStep)) {
            if (noSealBlocks) {
                // nothing to plug the water with and the storehouse had nothing either: leave this cell as a wall
                wp.progress++;
                data().setDirty();
                return;
            }
            status("Need blocks to plug water in mine #" + wp.id, "Waiting");
            breaker.reset(npc);
            state = State.STORAGE;
            return;
        }
        status("Branch mining at y=" + pos.getY() + " (mine #" + wp.id + ", " + wp.progress + "/" + plan.size() + ")", "Mining");
        if (dig(wp, pos)) {
            if (isOre(s) && BlockWork.canHarvest(s, npc.getMainHandItem())) {
                npc.awardXp(oreXp(s));
                prospect(s, pos);
            }
            if (c.dy() == 0) {
                bridgeFloor(pos);
            }
            wp.progress++;
            data().setDirty();
            noticeOres(pos);
        }
    }

    /** Prospector affix: a chance that the ore just mined drops its loot a second time. */
    private void prospect(BlockState ore, BlockPos pos) {
        if (level().random.nextDouble() < npc.affix(com.factionclash.logic.Affix.PROSPECTOR)) {
            wp().totalOutput += BlockWork.dropExtra(npc, ore, pos);
        }
    }

    /** A player branch-mining over a cave places a block so the tunnel floor stays walkable. */
    private boolean bridgeFloor(BlockPos foot) {
        return floorMissing(foot) && placeSeal(foot.below());
    }

    private boolean floorMissing(BlockPos foot) {
        BlockPos floor = foot.below();
        BlockState fs = level().getBlockState(floor);
        return fs.getCollisionShape(level(), floor).isEmpty() && !fs.is(Blocks.TORCH) && !fs.is(Blocks.WALL_TORCH);
    }

    /** Bridges one hole in the floor of the already-dug tunnel cells within reach (cave gaps found earlier). */
    private boolean bridgeNearbyHoles(WorkplaceState wp, List<SpiralMine.Cell> plan) {
        if (noSealBlocks) {
            return false;
        }
        for (int j = Math.max(0, wp.progress - 48); j < Math.min(wp.progress, plan.size()); j++) {
            SpiralMine.Cell c = plan.get(j);
            if (c.dy() != 0 || c.torch()) {
                continue;
            }
            BlockPos foot = MineLayout.tunnelPos(wp, c);
            if (passable(level().getBlockState(foot)) && floorMissing(foot) && !level().canSeeSky(foot)
                    && BlockWork.inReach(npc, foot.below(), 4.5) && bridgeFloor(foot)) {
                status("Bridging a cave hole in the tunnel floor (mine #" + wp.id + ")", "Mining");
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ digging deeper

    private void extend(WorkplaceState wp) {
        int lv = MineLayout.deepest(wp);
        int from = MineLayout.landingSteps(wp, lv);
        int to = MineLayout.landingSteps(wp, lv + 1);
        if (to < 0) {
            wp.upgradeStep = -1;
            data().setDirty();
            state = State.TRAVEL;
            return;
        }
        List<SpiralMine.Cell> plan = SpiralMine.spiralPlan(from, to);
        if (wp.upgradeStep >= plan.size()) {
            if (wp.tier < WorkplaceState.Type.MINE.maxTier) {
                wp.tier++;
            } else {
                wp.subLevel++;
            }
            wp.upgradeStep = -1;
            wp.progress = 0;
            data().setDirty();
            npc.awardXp(20);
            boolean infill = !SpiralMine.isRegular(wp.origin.getY(), MineLayout.deepest(wp));
            Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Mine #" + wp.id
                    + (infill ? " starts another branch mine between its levels" : " reached tier " + wp.tier + (wp.subLevel > 0 ? " +" + wp.subLevel : ""))
                    + " at y=" + SpiralMine.levelY(wp.origin.getY(), MineLayout.deepest(wp)) + " (" + npc.npcName() + ")"));
            state = State.TRAVEL;
            return;
        }
        SpiralMine.Cell c = plan.get(wp.upgradeStep);
        BlockPos pos = MineLayout.spiralPos(wp, c);
        int step = stepOfCell(c);
        if (c.torch()) {
            if (level().getBlockState(pos).isAir() && Inv.consume(npc.inventory(), TORCH, 1)) {
                level().setBlock(pos, MineLayout.wallTorchFor(wp, c.s()), Block.UPDATE_ALL);
                npc.swing(InteractionHand.MAIN_HAND);
            }
            wp.upgradeStep++;
            data().setDirty();
            return;
        }
        BlockState s = level().getBlockState(pos);
        if (passable(s)) {
            sealShaft(wp, pos, to);
            wp.upgradeStep++;
            data().setDirty();
            return;
        }
        if (!BlockWork.inReach(npc, pos, 4.5)) {
            walkInMine(wp, MineLayout.stand(wp, step - 1), step - 1, 1.0, "the bottom of the shaft");
            return;
        }
        if (!preSeal(pos, to)) {
            status("Need blocks to plug water in the shaft", "Waiting");
            breaker.reset(npc);
            state = State.STORAGE;
            return;
        }
        status("Digging the shaft down to tier " + (wp.tier < 4 ? wp.tier + 1 : wp.tier) + " (y=" + pos.getY() + ")", "Digging");
        if (dig(wp, pos)) {
            if (isOre(s) && BlockWork.canHarvest(s, npc.getMainHandItem())) {
                npc.awardXp(oreXp(s));
                prospect(s, pos);
            }
            noticeOres(pos);
            if (!sealShaft(wp, pos, to)) {
                state = State.STORAGE;
                return;
            }
            wp.upgradeStep++;
            data().setDirty();
        }
    }

    /** The spiral step a planned shaft cell belongs to. */
    private static int stepOfCell(SpiralMine.Cell c) {
        for (int k = -c.dy(); k <= -c.dy() + 2; k++) {
            int[] cell = SpiralMine.cell(k);
            if (k > 0 && cell[0] == c.f() && cell[1] == c.s()) {
                return k;
            }
        }
        return Math.max(1, -c.dy());
    }

    // ------------------------------------------------------------------ sealing

    private boolean placeSeal(BlockPos n) {
        ItemStack mat = Inv.first(npc.inventory(), SEAL);
        if (mat.isEmpty() && !level().getBlockState(n.below()).getCollisionShape(level(), n.below()).isEmpty()) {
            // a resting block of sand or gravel plugs a hole just as well
            mat = Inv.first(npc.inventory(), x -> x.is(Items.SAND) || x.is(Items.RED_SAND) || x.is(Items.GRAVEL)
                    || x.is(Items.SANDSTONE) || x.is(Items.RED_SANDSTONE));
        }
        if (mat.isEmpty() || !(mat.getItem() instanceof BlockItem bi)) {
            return false;
        }
        if (BlockWork.obstructed(level(), n, bi.getBlock().defaultBlockState(), npc)) {
            return false;
        }
        mat.shrink(1);
        npc.inventory().setChanged();
        level().setBlock(n, bi.getBlock().defaultBlockState(), Block.UPDATE_ALL);
        npc.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /**
     * Before opening a cell: plug neighbouring water, lava and loose blocks (sand, gravel) so nothing pours in, as a
     * careful player would. Returns false when the miner has nothing to plug with.
     */
    private boolean preSeal(BlockPos cell, int shaftSteps) {
        WorkplaceState wp = wp();
        for (Direction d : Direction.values()) {
            BlockPos n = cell.relative(d);
            BlockState ns = level().getBlockState(n);
            boolean loose = ns.getBlock() instanceof FallingBlock && d == Direction.UP;
            // sand or gravel holding back water (a lake bottom) is as dangerous as the water itself
            boolean fluid = !ns.getFluidState().isEmpty() || (loose && touchesFluid(n));
            boolean inShaft = wp != null && MineLayout.inShaft(wp, n, shaftSteps);
            if (fluid && inShaft && !level().getFluidState(n).isEmpty()) {
                // water or lava that ran into the stairs themselves: clear it out (a player places a block there
                // and breaks it again) rather than plugging the stairs shut
                level().setBlock(n, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                continue;
            }
            if ((fluid || loose) && !(inShaft && !fluid)) {
                // no block to prop sand/gravel with: dig through it as it falls, like a player would
                if (!placeSeal(n) && fluid) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean touchesFluid(BlockPos p) {
        for (Direction d : Direction.values()) {
            if (d != Direction.DOWN && !level().getFluidState(p.relative(d)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** After digging a shaft cell: close cave holes around the spiral so it stays a safe staircase. */
    private boolean sealShaft(WorkplaceState wp, BlockPos dug, int steps) {
        for (Direction d : Direction.values()) {
            BlockPos n = dug.relative(d);
            if (MineLayout.inShaft(wp, n, steps) || n.getY() >= wp.origin.getY() - 1
                    || MineLayout.forwardOf(wp, n) >= SpiralMine.MAIN_START) {
                continue;
            }
            BlockState ns = level().getBlockState(n);
            if ((ns.isAir() || !ns.getFluidState().isEmpty() || ns.getBlock() instanceof FallingBlock) && !placeSeal(n)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        caveJob.onPause();
        breaker.reset(npc);
        if (state == State.MINE || state == State.EXTEND || state == State.CLEAR || state == State.CRAFT) {
            state = State.TRAVEL;
        }
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state.name());
        CompoundTag cave = new CompoundTag();
        caveJob.save(cave, provider);
        tag.put("cave", cave);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            state = State.CHECK;
        }
        if (state == State.MINE || state == State.EXTEND || state == State.CLEAR || state == State.CRAFT) {
            state = State.TRAVEL;
        }
        caveJob.load(tag.getCompound("cave"), provider);
    }
}
