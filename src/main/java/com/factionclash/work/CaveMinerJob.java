package com.factionclash.work;

import com.factionclash.campaign.WorkplaceState;
import com.factionclash.logic.Affix;
import com.factionclash.logic.CaveSurvey;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcNavigator;
import com.factionclash.npc.ToolKind;
import com.factionclash.util.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A miner working a cave a cave explorer surveyed. It walks the explorer's route into the cave, mines the ores the
 * explorer marked (and every ore it uncovers while doing so), digs a short tunnel to an ore it cannot otherwise
 * reach, and when the marked ore is gone digs prospecting tunnels out from the deepest part of the cave. There is
 * no artificial supply limit: the cave holds exactly the ore that is really in it.
 */
public class CaveMinerJob extends Job {
    enum State { STORAGE, TRAVEL, ROUTE, MINE, PROSPECT, RETURN }

    /** PROPOSED: prospecting tunnels from the deepest explored spot: 4 directions, 16 blocks each, 2 high. */
    public static final int PROSPECT_LENGTH = 16;
    private static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    /** PROPOSED: at most this many blocks are tunnelled to reach one marked ore. */
    private static final int MAX_TUNNEL = 12;
    private static final int MAX_VEIN = 24;

    private State state = State.STORAGE;
    private final BlockWork.Breaker breaker = new BlockWork.Breaker();
    private final Deque<BlockPos> vein = new ArrayDeque<>();
    private final Deque<Integer> via = new ArrayDeque<>();
    /** The route waypoint the miner last stood at (-1: outside the cave). */
    private int at = -1;
    private BlockPos target;
    private BlockPos stand;
    private int targetTicks;
    private int tunnelled;
    private boolean noSeal;

    public CaveMinerJob(FactionNpc npc) {
        super(npc);
    }

    WorkplaceState cave() {
        return Workplaces.held(npc, WorkplaceState.Type.CAVE);
    }

    public static int prospectSize() {
        return DIRS.length * PROSPECT_LENGTH * 2;
    }

    @Override
    public String workplaceInfo() {
        WorkplaceState w = cave();
        if (w == null) {
            return "no cave";
        }
        return "Cave #" + w.id + " at " + w.origin.toShortString() + ": " + w.marks.size() + " marked ores left, prospecting "
                + Math.min(w.progress, prospectSize()) + "/" + prospectSize() + (w.workedOut ? " (worked out)" : "");
    }

    @Override
    protected int keep(ItemStack s) {
        if (MinerJob.TORCH.test(s)) {
            return 32;
        }
        if (MinerJob.SEAL.test(s)) {
            return 64;
        }
        if (s.is(Items.STICK)) {
            return 8;
        }
        return 0;
    }

    @Override
    protected void run() {
        WorkplaceState w = cave();
        if (w == null) {
            status("No surveyed cave to mine", "Idle");
            waitFor(200);
            return;
        }
        if (pendingReturn) {
            pendingReturn = false; // loaded while inside the cave: walk out along the route first
            beginReturn();
        }
        int picked = BlockWork.pickUp(npc, s -> true);
        if (picked > 0) {
            w.totalOutput += picked;
            data().setDirty();
        }
        switch (state) {
            case STORAGE -> storage(w);
            case TRAVEL -> {
                if (goTo(w.origin, 1.5, "the cave at " + w.origin.toShortString())) {
                    at = 0;
                    state = State.ROUTE;
                }
            }
            case ROUTE -> route(w);
            case MINE -> mine(w);
            case PROSPECT -> prospect(w);
            case RETURN -> returnOut();
        }
    }

    // ------------------------------------------------------------------ storehouse

    private void storage(WorkplaceState w) {
        breaker.reset(npc);
        if (at > 0) {
            beginReturn();
            return;
        }
        if (!atStorage()) {
            return;
        }
        if (unloadNow() == Unload.PARTIAL_FULL && Inv.freeSlots(npc.inventory()) < 3) {
            status("Storehouse full - holding the cave's output", "Waiting");
            waitFor(200);
            return;
        }
        upgradeEquipment();
        if (Inv.count(npc.inventory(), MinerJob.TORCH) < 8) {
            takeFromStorage(MinerJob.TORCH, 16);
        }
        if (Inv.count(npc.inventory(), MinerJob.SEAL) < 16) {
            takeFromStorage(MinerJob.SEAL, 32);
        }
        noSeal = Inv.count(npc.inventory(), MinerJob.SEAL) == 0;
        if (!ToolKind.PICKAXE.matches(npc.tool())) {
            status("No pickaxe to mine the cave with", "Waiting");
            waitFor(400);
            return;
        }
        if (w.marks.isEmpty() && w.progress >= prospectSize()) {
            // nothing left that a player could see or reach from this cave: hand it back
            w.workedOut = true;
            Workplaces.release(data(), w);
            data().setDirty();
            Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Cave #" + w.id + " at "
                    + w.origin.toShortString() + " is worked out (" + npc.npcName() + ", " + w.totalOutput + " items brought home)"));
            status("Cave worked out", "Idle");
            waitFor(100);
            return;
        }
        state = State.TRAVEL;
    }

    // ------------------------------------------------------------------ choosing what to mine

    private void route(WorkplaceState w) {
        target = nextMark(w);
        stand = null;
        targetTicks = 0;
        tunnelled = 0;
        if (target == null) {
            if (w.progress < prospectSize() && w.route.length > 0) {
                walkTo(w, deepest(w));
                state = State.PROSPECT;
                return;
            }
            beginReturn();
            return;
        }
        if (w.route.length > 0 && !target.closerThan(npc.blockPosition(), 12)) {
            walkTo(w, CaveSurvey.nearestWaypoint(w.route, target.asLong()));
        }
        state = State.MINE;
    }

    /** Sets the walk along the explorer's route from where the miner is to waypoint {@code k}. */
    private void walkTo(WorkplaceState w, int k) {
        via.clear();
        if (at < 0 || at >= w.route.length) {
            at = 0;
            via.add(0);
        } else {
            via.add(at);
        }
        via.addAll(CaveSurvey.treePath(w.parent, at, k));
    }

    /** Breaks a block at player speed; its drops go straight into the backpack and count as the cave's output. */
    private boolean dig(WorkplaceState w, BlockPos pos) {
        if (!breaker.tick(npc, pos)) {
            return false;
        }
        w.totalOutput += breaker.lastGained();
        data().setDirty();
        return true;
    }

    /** The nearest marked ore that is still there; marks whose ore has gone are dropped. */
    private BlockPos nextMark(WorkplaceState w) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (java.util.Iterator<Long> it = w.marks.iterator(); it.hasNext(); ) {
            BlockPos p = BlockPos.of(it.next());
            if (level().isLoaded(p) && !MinerJob.isOre(level().getBlockState(p))) {
                it.remove();
                data().setDirty();
                continue;
            }
            double d = p.distSqr(npc.blockPosition());
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private int deepest(WorkplaceState w) {
        int best = 0;
        for (int i = 1; i < w.route.length; i++) {
            if (CaveSurvey.y(w.route[i]) < CaveSurvey.y(w.route[best])) {
                best = i;
            }
        }
        return best;
    }

    /** Walks one leg of the route; true when the legs are done. */
    private boolean followRoute(WorkplaceState w) {
        if (via.isEmpty()) {
            return true;
        }
        int k = via.peek();
        if (k < 0 || k >= w.route.length) {
            via.clear();
            return true;
        }
        NpcNavigator.Result r = npc.navigator().travel(BlockPos.of(w.route[k]), 1.5, 1.0);
        if (r == NpcNavigator.Result.MOVING) {
            status("Walking through the cave (cave #" + w.id + ")", "Travelling");
            return false;
        }
        if (r == NpcNavigator.Result.FAILED) {
            npc.navigator().reset();
        }
        at = k;
        via.poll();
        return via.isEmpty();
    }

    // ------------------------------------------------------------------ mining marked ore

    private void mine(WorkplaceState w) {
        if (Inv.freeSlots(npc.inventory()) <= 1) {
            beginReturn();
            return;
        }
        if (!followRoute(w)) {
            return;
        }
        BlockPos t = !vein.isEmpty() ? vein.peek() : target;
        if (t == null) {
            state = State.ROUTE;
            return;
        }
        BlockState s = level().getBlockState(t);
        if (!MinerJob.isOre(s)) {
            done(w, t);
            return;
        }
        if (!WorkSafety.mayDig(level(), t, base(), null)) {
            // under a base, in another mine's stairs, in the deep dark...: not worth walking or tunnelling to
            skip(w, t, "it is protected");
            return;
        }
        if (BlockWork.inReach(npc, t, 4.5)) {
            if (!plugFluids(t)) {
                skip(w, t, "water or lava behind it and nothing to plug it with");
                return;
            }
            status("Mining " + CaveWork.oreName(s) + " ore in cave #" + w.id + " (" + w.marks.size() + " marked)", "Mining");
            if (dig(w, t)) {
                if (BlockWork.canHarvest(s, npc.getMainHandItem())) {
                    npc.awardXp(MinerJob.oreXp(s));
                    if (level().random.nextDouble() < npc.affix(Affix.PROSPECTOR)) {
                        w.totalOutput += BlockWork.dropExtra(npc, s, t);
                    }
                }
                noticeOres(w, t);
                done(w, t);
            }
            return;
        }
        if (stand == null || !stand.closerThan(t, 6)) {
            stand = CaveWork.standNear(level(), npc, t, 4.3);
        }
        if (stand != null && targetTicks < 600) {
            targetTicks++;
            NpcNavigator.Result r = npc.navigator().travel(stand, 0.6, 1.0);
            if (r == NpcNavigator.Result.MOVING) {
                status("Walking to marked " + CaveWork.oreName(s) + " ore (cave #" + w.id + ")", "Travelling");
                return;
            }
            if (r == NpcNavigator.Result.ARRIVED) {
                return; // in reach next tick (or not: then tunnel)
            }
            npc.navigator().reset();
            targetTicks = 600;
        }
        tunnelToward(w, t);
    }

    /** No standing spot reaches the ore: dig a short 2-high tunnel toward it like a player would. */
    private void tunnelToward(WorkplaceState w, BlockPos t) {
        BlockPos feet = npc.blockPosition();
        int dx = t.getX() - feet.getX(), dz = t.getZ() - feet.getZ(), dy = t.getY() - feet.getY();
        if (tunnelled >= MAX_TUNNEL || Math.abs(dx) + Math.abs(dz) > 10 || dy > 4 || dy < -4 || (dx == 0 && dz == 0)) {
            skip(w, t, "out of reach");
            return;
        }
        Direction d = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos step = feet.relative(d);
        if (dy > 1) {
            step = step.above();
        } else if (dy < 0) {
            step = step.below();
        }
        BlockPos[] clear = dy > 1 ? new BlockPos[]{feet.above(2), step.above(), step}
                : dy < 0 ? new BlockPos[]{step.above(2), step.above(), step} : new BlockPos[]{step.above(), step};
        for (BlockPos c : clear) {
            BlockState cs = level().getBlockState(c);
            if (CaveWork.open(level(), c)) {
                continue;
            }
            if (!WorkSafety.mayDig(level(), c, base(), null) || !plugFluids(c)) {
                skip(w, t, "blocked"); // unbreakable or protected (WorkSafety), or water behind it with nothing to plug
                return;
            }
            status("Tunnelling toward marked ore (cave #" + w.id + ")", "Mining");
            if (dig(w, c)) {
                tunnelled++;
                if (MinerJob.isOre(cs)) {
                    npc.awardXp(MinerJob.oreXp(cs));
                }
                noticeOres(w, c);
            }
            return;
        }
        if (level().getBlockState(step.below()).getCollisionShape(level(), step.below()).isEmpty()) {
            placeSeal(step.below()); // bridge a gap in the floor
        }
        npc.getMoveControl().setWantedPosition(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, 1.0);
    }

    private void done(WorkplaceState w, BlockPos t) {
        breaker.reset(npc);
        w.marks.remove(t.asLong());
        vein.remove(t);
        if (t.equals(target)) {
            target = null;
            state = State.ROUTE;
        }
        data().setDirty();
    }

    private void skip(WorkplaceState w, BlockPos t, String why) {
        status("Leaving an ore: " + why, "Mining");
        done(w, t);
    }

    /** A player mines the ore it just uncovered: neighbours of an opened block that are ore join the vein. */
    private void noticeOres(WorkplaceState w, BlockPos opened) {
        for (Direction d : Direction.values()) {
            BlockPos n = opened.relative(d);
            if (vein.size() < MAX_VEIN && !vein.contains(n) && MinerJob.isOre(level().getBlockState(n))) {
                vein.add(n);
                w.marks.add(n.asLong());
            }
        }
    }

    // ------------------------------------------------------------------ prospecting

    /**
     * Prospecting like a player: from the deepest explored spot it walks to the cave wall in each of the four
     * directions and tunnels 2 high into the rock, torches every 8 blocks, mining any ore the tunnel reveals.
     */
    private void prospect(WorkplaceState w) {
        if (Inv.freeSlots(npc.inventory()) <= 1) {
            beginReturn();
            return;
        }
        if (!followRoute(w)) {
            return;
        }
        if (!vein.isEmpty()) {
            state = State.MINE; // ore revealed: mine it, then come back to prospecting
            return;
        }
        if (w.progress >= prospectSize()) {
            state = State.ROUTE;
            return;
        }
        if (w.faces.length != DIRS.length) {
            w.faces = faces(BlockPos.of(w.route[deepest(w)]));
            data().setDirty();
        }
        int perDir = PROSPECT_LENGTH * 2;
        int dir = w.progress / perDir, k = (w.progress % perDir) / 2, up = w.progress % 2 == 0 ? 1 : 0;
        if (w.faces[dir] == Long.MIN_VALUE) {
            endDirection(w, dir);
            return;
        }
        Direction d = DIRS[dir];
        BlockPos face = BlockPos.of(w.faces[dir]);
        BlockPos cell = face.relative(d, k).above(up);
        BlockPos standAt = face.relative(d, k - 1);
        if (CaveWork.open(level(), cell)) {
            // already open: dug before, or the tunnel broke into another hollow
            if (up == 0 && level().getBlockState(cell.below()).getCollisionShape(level(), cell.below()).isEmpty()) {
                endDirection(w, dir); // a drop: stop here rather than build out over it
                return;
            }
            w.progress++;
            data().setDirty();
            return;
        }
        if (!BlockWork.inReach(npc, cell, 4.5)) {
            NpcNavigator.Result r = npc.navigator().travel(standAt, 0.6, 1.0);
            if (r == NpcNavigator.Result.FAILED) {
                npc.navigator().reset();
                endDirection(w, dir);
            } else {
                status("Walking to the prospecting tunnel (cave #" + w.id + ")", "Travelling");
            }
            return;
        }
        BlockState s = level().getBlockState(cell);
        if (!WorkSafety.mayDig(level(), cell, base(), null) || !plugFluids(cell)) {
            endDirection(w, dir); // unbreakable or protected (WorkSafety), or water behind it with nothing to plug
            return;
        }
        status("Prospecting from cave #" + w.id + " (" + d.getName() + ", " + (k + 1) + "/" + PROSPECT_LENGTH + ")", "Mining");
        if (dig(w, cell)) {
            if (MinerJob.isOre(s) && BlockWork.canHarvest(s, npc.getMainHandItem())) {
                npc.awardXp(MinerJob.oreXp(s));
            }
            if (up == 0) {
                if (level().getBlockState(cell.below()).getCollisionShape(level(), cell.below()).isEmpty()) {
                    placeSeal(cell.below());
                }
                if ((k + 1) % 8 == 0 && CaveWork.dark(level(), cell)) {
                    CaveWork.placeTorch(npc, cell);
                }
            }
            noticeOres(w, cell);
            w.progress++;
            data().setDirty();
        }
    }

    /**
     * Where each prospecting tunnel starts: walking along the cave floor from {@code start}, the first column
     * (feet or head height) that is rock, within 8 blocks; none if the floor drops away or there is no wall.
     */
    private long[] faces(BlockPos start) {
        long[] out = new long[DIRS.length];
        for (int i = 0; i < DIRS.length; i++) {
            out[i] = Long.MIN_VALUE;
            for (int j = 1; j <= 8; j++) {
                BlockPos c = start.relative(DIRS[i], j);
                if (!CaveWork.open(level(), c) || !CaveWork.open(level(), c.above())) {
                    out[i] = c.asLong();
                    break;
                }
                if (level().getBlockState(c.below()).getCollisionShape(level(), c.below()).isEmpty()) {
                    break; // the floor drops away
                }
            }
        }
        return out;
    }

    private void endDirection(WorkplaceState w, int dir) {
        breaker.reset(npc);
        w.progress = Math.max(w.progress, (dir + 1) * PROSPECT_LENGTH * 2);
        data().setDirty();
    }

    // ------------------------------------------------------------------ leaving

    private void beginReturn() {
        breaker.reset(npc);
        WorkplaceState w = cave();
        via.clear();
        if (w != null && at > 0 && at < w.route.length) {
            via.add(at);
            via.addAll(CaveSurvey.treePath(w.parent, at, 0));
        }
        state = State.RETURN;
    }

    private void returnOut() {
        WorkplaceState w = cave();
        if (w == null || followRoute(w)) {
            at = -1;
            state = State.STORAGE;
        }
    }

    // ------------------------------------------------------------------ water, lava and sand

    /** Plugs fluids (and sand or gravel holding fluid back) around a block before it is opened. */
    private boolean plugFluids(BlockPos cell) {
        for (Direction d : Direction.values()) {
            BlockPos n = cell.relative(d);
            BlockState ns = level().getBlockState(n);
            boolean fluid = !ns.getFluidState().isEmpty()
                    || (d == Direction.UP && ns.getBlock() instanceof FallingBlock && touchesFluid(n));
            if (fluid && !placeSeal(n)) {
                return false;
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

    private boolean placeSeal(BlockPos n) {
        if (noSeal) {
            return false;
        }
        ItemStack mat = Inv.first(npc.inventory(), MinerJob.SEAL);
        if (mat.isEmpty() || !(mat.getItem() instanceof BlockItem bi)) {
            return false;
        }
        BlockState place = bi.getBlock().defaultBlockState();
        if (BlockWork.obstructed(level(), n, place, npc)) {
            return false;
        }
        mat.shrink(1);
        npc.inventory().setChanged();
        level().setBlock(n, place, Block.UPDATE_ALL);
        npc.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        breaker.reset(npc);
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state.name());
        tag.putInt("at", at);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        at = tag.contains("at") ? tag.getInt("at") : -1;
        // in the cave: walk out along the route first; otherwise start from the storehouse
        state = at > 0 ? State.RETURN : State.STORAGE;
        if (at > 0) {
            via.clear(); // the route is re-read from the workplace when walking out
            pendingReturn = true;
        }
    }

    private boolean pendingReturn;
}
