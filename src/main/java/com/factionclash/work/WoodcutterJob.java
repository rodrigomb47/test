package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.MapBounds;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.Affix;
import com.factionclash.logic.ForestRules;
import com.factionclash.logic.TreeShape;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcNavigator;
import com.factionclash.npc.ToolKind;
import com.factionclash.storage.FactionStorage;
import com.factionclash.structure.TreePlotLayout;
import com.factionclash.util.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Woodcutter of the base's woodland, working like a player: no fence and no plot. It fells natural trees around the
 * base (see {@link ForestScan#findTree}) with its axe at real break speed, top down, breaks the canopy so the leaves'
 * own loot tables drop saplings, sticks and apples, picks up what fell, and replants the stump with a sapling of the
 * same species (dark oak only as a square of four). Before the first cut it checks the tree's shape
 * ({@link TreeShape}): a trunk touching anything built or another kind of log, or reaching higher than it can cut
 * from the ground, is left standing whole, and so is wood near bases, in other factions' land or near known villages
 * ({@link WorkSafety#protectedWood}). It speeds its saplings up with real bone meal, keeps a small grove growing in
 * the base's always-ticking work belt (so a base in treeless land still gets wood), takes down the fence of an old
 * save's tree plot, and walks further out to look for trees when none grow near the base. Between dusk and dawn it
 * does not work far outside the walls.
 */
public class WoodcutterJob extends Job {
    enum State { CHECK, STORAGE, CHOP, LEAVES, COLLECT, PLANT, BONEMEAL, DISMANTLE }

    /** PROPOSED: logs, sticks and apples carried before a trip to the storehouse. */
    static final int CARGO_LIMIT = 48;
    /** Reach used for tall trunks: a player would climb or pillar; NPCs reach a little higher instead (PROPOSED). */
    static final double TRUNK_REACH = 7.5;
    /** PROPOSED: the walk to a tree ends this close (eyes to the stump), then it is cut from where the woodcutter stands. */
    static final double CHOP_REACH = 4.0, APPROACH = 2.5;
    /** PROPOSED: a tree not reached after this many walking ticks is given up. */
    static final int WALK_GIVE_UP_TICKS = 400;
    /** PROPOSED: when the top log is out of reach, it first steps this close to the stump, for at most this long. */
    static final double CLOSER_REACH = 1.5;
    static final int CLOSER_TICKS = 200;
    /** PROPOSED: logs still out of reach are cut at the end standing in the stump's column, for at most this long. */
    static final double STUMP_COLUMN = 0.3;
    static final int FINAL_PASS_TICKS = 200;
    /** PROPOSED: the tree search runs at most this often (sooner when the tree it had in mind is gone). */
    static final int RESCAN_TICKS = 600;
    /** The search reads one of 4 column patterns per scan; after an empty one the next follows this soon. */
    static final int SCAN_PATTERNS = ForestScan.PATTERNS, PATTERN_TICKS = 20;
    /** PROPOSED: trees given up on that it remembers, oldest forgotten first. */
    static final int MAX_BAD_WILD = 128;
    /** PROPOSED: at most one trip to the storehouse for a stump's sapling this often. */
    static final int SAPLING_TRIP_TICKS = 2400;
    /** The storehouse is looked into for a stump's missing sapling at most this often. */
    static final int STORAGE_LOOK_TICKS = 200;
    /** PROPOSED: foraging walks this far out in one of eight directions, looks around this far, then tries the next one. */
    static final int FORAGE_DISTANCE = 72, FORAGE_SCAN = 40, FORAGE_DIRECTIONS = 8, FORAGE_LEG_TICKS = 2400;
    /** PROPOSED: after a full round of foraging finds nothing, it rests this long before going out again. */
    static final int FORAGE_REST_TICKS = 6000;
    /** PROPOSED: an old fence post not reached in this many walking ticks, or after this many failed routes, is left. */
    static final int POST_GIVE_UP_TICKS = 300, POST_ROUTE_FAILURES = 3;
    /** PROPOSED: a planting spot or sapling not reached in this many walking ticks is left for a while. */
    static final int SPOT_GIVE_UP_TICKS = 600, SPOT_RETRY_TICKS = 2400;
    /** PROPOSED: what it keeps when unloading: saplings of each species, and bone meal. */
    static final int KEEP_SAPLINGS = 8, KEEP_BONE_MEAL = 16;

    private State state = State.CHECK;
    /** The tree being felled (its stump, the north-west corner of a 2x2 trunk), the planting spot, the sapling or fence post. */
    private BlockPos spot;
    /** The tree at {@link #spot} has a 2x2 trunk. */
    private boolean big;
    /** The stump of the tree being felled once its first log is cut; null otherwise. Kept until the tree is done. */
    private BlockPos felling;
    private ForestRules.Species species;
    private final LinkedHashSet<Long> badWild = new LinkedHashSet<>();
    private int forageDir, forageFails;
    private BlockPos forageTarget;
    private long forageStart, forageRestUntil;

    private final List<BlockPos> logs = new ArrayList<>();
    private final List<BlockPos> deferred = new ArrayList<>();
    private final List<BlockPos> leaves = new ArrayList<>();
    private final BlockWork.Breaker breaker = new BlockWork.Breaker();
    private boolean surveyed, leavesFound, closerDone;
    private int walkTicks, closerTicks, finalTicks, collectTicks, progress;
    /** The last tree felled (its stump and whether it was 2x2): the drops around it are collected. */
    private BlockPos lastStump;
    private boolean lastBig;

    // what PLANT plants: the species, how many, whether as a 2x2 square, for a replant entry or the grove
    private ForestRules.Species plantSpecies;
    private int plantCount;
    private boolean plantSquare, plantGrove;

    private ForestScan.Found treeCache;
    private long nextScan;
    private int scans, emptyScans;
    private long nextSaplingTrip, nextStorageLook, nextAxeCheck, nextPrune;
    private int tendCursor;
    private final Set<Long> skippedPosts = new HashSet<>();
    private int postFailBase;
    /** Planting spots and saplings it could not get to, until a game time. */
    private final Map<Long, Long> skipUntil = new HashMap<>();
    private final ForestScan.GroveSearch grove = new ForestScan.GroveSearch();

    public WoodcutterJob(FactionNpc npc) {
        super(npc);
    }

    @Override
    public String workplaceInfo() {
        WorkplaceState wp = Workplaces.held(npc, WorkplaceState.Type.TREE_PLOT); // looking never claims
        if (wp == null) {
            return "no woodland";
        }
        return "Woodland #" + wp.id + ": " + wp.felled + " trees felled, " + wp.planted.size() + " saplings growing, "
                + wp.replant.size() + " stumps to replant" + (wp.legacyFence ? ", taking down the old fence" : "");
    }

    /** The current state's name, the tree or spot it works on, and the tree it is felling (for the GameTests). */
    public String stateName() {
        return state.name();
    }

    public BlockPos spot() {
        return spot;
    }

    public BlockPos felling() {
        return felling;
    }

    /** True if this woodcutter has given up on the tree with this stump (for the GameTests). */
    public boolean gaveUp(BlockPos stump) {
        return badWild.contains(stump.asLong());
    }

    @Override
    protected int keep(ItemStack s) {
        if (s.is(ItemTags.SAPLINGS)) {
            return KEEP_SAPLINGS; // per species: the backpack keeps 8 of each
        }
        return s.is(Items.BONE_MEAL) ? KEEP_BONE_MEAL : 0;
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

    private boolean hasAxe() {
        return ToolKind.AXE.matches(npc.tool());
    }

    @Override
    protected void run() {
        BaseState b = base();
        WorkplaceState wp = b == null ? null : Workplaces.claim(npc, WorkplaceState.Type.TREE_PLOT);
        if (b == null || wp == null) {
            status("No woodland", "Idle");
            if (b != null) {
                markIdle("no woodland of its own"); // the base's woodland has its woodcutter: retrainable
            }
            waitFor(200);
            return;
        }
        if (state != State.STORAGE) {
            collect(wp);
        }
        BlockPos under = npc.blockPosition().below();
        if (npc.onGround() && cuttableLeaves(under)) {
            // standing on a tree canopy: a player breaks the leaves underfoot and drops down (a hedge or a leaf roof
            // is left alone; getting off one is the stuck recovery's business)
            status("Breaking the leaves underfoot to get down", "Chopping");
            chopBlock(wp, under);
            return;
        }
        switch (state) {
            case CHECK -> decide(b, wp);
            case STORAGE -> storage(b, wp);
            case CHOP -> chop(b, wp);
            case LEAVES -> breakLeaves(wp);
            case COLLECT -> collectDrops();
            case PLANT -> plant(wp);
            case BONEMEAL -> boneMeal();
            case DISMANTLE -> dismantle(wp);
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

    private boolean isLog(BlockPos p) {
        return level().isLoaded(p) && level().getBlockState(p).is(BlockTags.LOGS);
    }

    /** True between dusk and dawn when remote work waits for daylight. */
    private boolean nightRule() {
        return FCConfig.REMOTE_WORK_IN_DAYLIGHT_ONLY.get() && isNight();
    }

    // ------------------------------------------------------------------ deciding

    private void decide(BaseState b, WorkplaceState wp) {
        // 1. the storehouse: an axe, unloading
        if (!hasAxe() && now() >= nextAxeCheck || cargo() >= CARGO_LIMIT || Inv.freeSlots(npc.inventory()) == 0) {
            state = State.STORAGE;
            return;
        }
        // 2. an old save's tree-plot fence comes down first
        if (wp.legacyFence && startDismantling(wp)) {
            return;
        }
        // 3. a tree it started to fell (before a trip to the storehouse, a fight or a reload): go back and finish it.
        // Its chunk may have unloaded while the woodcutter was away, so whether logs are left is only looked at
        // there; a tree with none left goes straight on to its canopy, drops and replanting.
        if (felling != null) {
            if (remoteAtNight(felling)) {
                nightWait(true);
                return;
            }
            spot = felling;
            startTree();
            return;
        }
        // 4. stumps waiting for their sapling
        if (replantQueue(b, wp)) {
            return;
        }
        // 5. what dropped around the last tree
        if (lastStump != null && collectable() != null) {
            collectTicks = 0;
            markBusy();
            state = State.COLLECT;
            return;
        }
        // 6. the best natural tree around the base
        ForestScan.Found tree = bestTree(b);
        if (tree != null) {
            spot = tree.stump();
            big = tree.big();
            startTree();
            return;
        }
        // 7. bone meal on its saplings
        prunePlanted(wp);
        if (Inv.count(npc.inventory(), x -> x.is(Items.BONE_MEAL)) > 0 && boneMealTarget(b, wp)) {
            return;
        }
        // 8. the grove in the work belt
        if (groveTarget(b, wp)) {
            return;
        }
        // 9. at night, home; by day, further out to look for trees
        if (nightRule()) {
            forageTarget = null;
            nightWait(false);
            return;
        }
        if (!noTreeNearby()) {
            // the search has not read every column yet: finish it before walking out
            status("Looking for trees", "Waiting");
            waitFor(PATTERN_TICKS);
            return;
        }
        if (forage(b)) {
            return;
        }
        // 10. nothing to do: saplings are growing
        boolean forageResting = !BaseZones.forageAllowed(b) || now() < forageRestUntil;
        if (forageResting && wp.planted.isEmpty() && wp.replant.isEmpty()
                && Inv.count(npc.inventory(), x -> x.is(ItemTags.SAPLINGS)) == 0
                && FactionStorage.count(level(), b, x -> x.is(ItemTags.SAPLINGS)) == 0) {
            markIdle("no trees or saplings");
        } else {
            markBusy();
        }
        status("Saplings are growing" + (Inv.count(npc.inventory(), x -> x.is(Items.BONE_MEAL)) == 0 ? " (no bone meal)" : ""), "Waiting");
        waitFor(100);
    }

    /**
     * Between dusk and dawn with nothing near the walls left to do: unload, then wait at the storehouse. Only a
     * started tree waiting for daylight ({@code treeWaiting}) counts as work; otherwise the night leaves the idle
     * stretch as it was, so a woodcutter with no trees and no saplings is still retrained in time.
     */
    private void nightWait(boolean treeWaiting) {
        if (hasCargo()) {
            state = State.STORAGE;
            return;
        }
        if (!atStorage()) {
            return;
        }
        status("Waiting for daylight", "Resting");
        if (treeWaiting) {
            markBusy();
        }
        waitFor(600);
    }

    /** Starts work on the tree at {@link #spot}: walk to it, survey it, fell it. */
    private void startTree() {
        logs.clear();
        deferred.clear();
        surveyed = false;
        closerDone = false;
        walkTicks = 0;
        closerTicks = 0;
        finalTicks = 0;
        breaker.reset(npc);
        markBusy();
        state = State.CHOP;
    }

    /** The cached tree while it still stands and is free, else a new search (at most every {@link #RESCAN_TICKS}). */
    private ForestScan.Found bestTree(BaseState b) {
        if (treeCache != null) {
            BlockPos s = treeCache.stump();
            if (isLog(s) && !badWild.contains(s.asLong()) && !ForestScan.TreeClaims.claimedByOther(s, npc.getUUID(), now())
                    && !remoteAtNight(s)) {
                return treeCache;
            }
            // gone, given up on, taken by another woodcutter or too far out tonight: look again at once rather than
            // wait out the rescan time with nothing in mind
            treeCache = null;
            nextScan = now();
        }
        if (now() < nextScan) {
            return null;
        }
        int radius = BaseZones.workRadius(b, FCConfig.FOREST_RADIUS.get());
        treeCache = ForestScan.findTree(level(), data(), b, npc, b.center, radius, badWild, scans++, this::remoteAtNight);
        // each scan reads every second column in a shifting pattern: an empty one is followed by the other three at
        // once, so every column has been read before the woodcutter decides there is no tree near the base
        emptyScans = treeCache == null ? emptyScans + 1 : 0;
        nextScan = now() + (emptyScans % SCAN_PATTERNS != 0 ? PATTERN_TICKS : RESCAN_TICKS);
        return treeCache;
    }

    /** True once a whole round of scans (every column read) found no tree near the base. */
    private boolean noTreeNearby() {
        return emptyScans >= SCAN_PATTERNS;
    }

    /** The tree at {@link #spot} is not to be felled (or cannot be reached): never tried again, the next one looked for at once. */
    private void leaveTree(String why) {
        status("Leaving a tree standing (" + why + ")", "Chopping");
        badWild.remove(spot.asLong());
        badWild.add(spot.asLong());
        while (badWild.size() > MAX_BAD_WILD) {
            badWild.remove(badWild.iterator().next());
        }
        ForestScan.TreeClaims.release(spot, npc.getUUID());
        if (spot.equals(felling)) {
            felling = null;
        }
        treeCache = null;
        nextScan = now(); // look for the next tree at once, rather than walk out to forage
        logs.clear();
        deferred.clear();
        breaker.reset(npc);
        npc.navigator().reset();
        state = State.CHECK;
    }

    // ------------------------------------------------------------------ felling

    /** The bottom cells of the tree at {@link #spot}: the stump, or the four of a 2x2 trunk. */
    private List<BlockPos> stumpCells() {
        return big ? List.of(spot, spot.east(), spot.south(), spot.south().east()) : List.of(spot);
    }

    /** Where a started tree's survey begins: the lowest log of a felled species left in its stump column(s), up to +8. */
    private BlockPos resumeStart() {
        if (felling == null) {
            return null;
        }
        List<BlockPos> cols = big ? List.of(felling, felling.east(), felling.south(), felling.south().east()) : List.of(felling);
        for (int dy = 0; dy <= TreeShape.MAX_FELL_DY; dy++) {
            for (BlockPos c : cols) {
                BlockPos p = c.above(dy);
                if (level().isLoaded(p) && ForestScan.speciesOf(level().getBlockState(p)) != null) {
                    return p;
                }
            }
        }
        return null;
    }

    private void chop(BaseState b, WorkplaceState wp) {
        if (Inv.freeSlots(npc.inventory()) == 0) {
            breaker.reset(npc);
            state = State.STORAGE; // felling is kept: the tree is finished after unloading
            return;
        }
        if (!surveyed) {
            if (!BlockWork.inReach(npc, spot, CHOP_REACH)) {
                // low leaves at head height block the way to the trunk: a player cuts through them
                BlockPos leaf = leafInTheWay();
                if (leaf != null) {
                    status("Cutting through leaves to reach a tree", "Chopping");
                    chopBlock(wp, leaf);
                    return;
                }
                if (++walkTicks > WALK_GIVE_UP_TICKS) {
                    leaveTree("cannot get to it"); // a cliff, water, fenced in
                    return;
                }
                if (!goTo(spot, APPROACH, "a tree")) {
                    return;
                }
            }
            if (!npc.getNavigation().isDone()) {
                // close enough to chop: stop here, or the rest of the walking path carries it out of reach again
                npc.getNavigation().stop();
            }
            if (!ForestScan.TreeClaims.claim(spot, npc.getUUID(), now())) {
                // another woodcutter is felling it (perhaps the rest of one this woodcutter started): find another
                // tree, this one is not given up on
                if (spot.equals(felling)) {
                    felling = null;
                }
                treeCache = null;
                nextScan = now();
                state = State.CHECK;
                return;
            }
            boolean resume = spot.equals(felling);
            BlockPos start = resume ? resumeStart() : spot;
            if (start == null) {
                finishTree(wp);
                return;
            }
            TreeShape.Tree tree = ForestScan.survey(level(), data(), b, start, true, resume);
            if (!tree.ok()) {
                // any other verdict leaves the tree standing whole, before the first cut: no floating logs
                leaveTree(tree.verdict().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
                return;
            }
            species = ForestScan.speciesOf(level().getBlockState(start));
            big = big || tree.big();
            for (int[] p : tree.logs()) {
                logs.add(new BlockPos(p[0], p[1], p[2]));
            }
            surveyed = true;
        }
        ForestScan.TreeClaims.claim(spot, npc.getUUID(), now()); // renewed while chopping
        while (!logs.isEmpty() && !isLog(logs.get(0))) {
            logs.remove(0);
        }
        if (logs.isEmpty()) {
            finalPass(wp);
            return;
        }
        BlockPos log = logs.get(0);
        if (!BlockWork.inReach(npc, log, TRUNK_REACH)) {
            if (!closerDone) {
                // the top is out of reach from here: step in close to the stump first
                if (closerTicks++ < CLOSER_TICKS && !NpcNavigator.within(npc, spot, CLOSER_REACH)) {
                    goTo(spot, CLOSER_REACH, "the trunk");
                    return;
                }
                closerDone = true;
                npc.getNavigation().stop();
                return;
            }
            deferred.add(logs.remove(0)); // cut at the end, from the stump's column
            return;
        }
        closerDone = true;
        status("Felling a tree (" + (logs.size() + deferred.size()) + " logs left)", "Chopping");
        if (chopBlock(wp, log)) {
            npc.awardXp(1);
            logs.remove(0);
            if (felling == null) {
                felling = spot;
            }
            if (log.getY() == spot.getY() && stumpCells().contains(log) && stumpCells().stream().noneMatch(this::isLog)) {
                stumpCut(wp);
            }
        }
    }

    /** The stump is cut: the tree counts as felled and its place waits for a sapling of the same species. */
    private void stumpCut(WorkplaceState wp) {
        wp.felled++;
        if (species != null) {
            boolean square = big || species == ForestRules.Species.DARK_OAK;
            wp.addReplant(new WorkplaceState.Replant(spot.asLong(), species.name(), square, now()));
        }
        data().setDirty();
    }

    /** Logs that were out of reach: cut standing in the stump's column (from there the eighth log above is in reach). */
    private void finalPass(WorkplaceState wp) {
        while (!deferred.isEmpty() && !isLog(deferred.get(0))) {
            deferred.remove(0);
        }
        if (deferred.isEmpty()) {
            finishTree(wp);
            return;
        }
        BlockPos log = deferred.get(0);
        if (!BlockWork.inReach(npc, log, TRUNK_REACH)) {
            if (finalTicks++ < FINAL_PASS_TICKS && !NpcNavigator.within(npc, spot, STUMP_COLUMN)) {
                goTo(spot, STUMP_COLUMN, "the stump");
                return;
            }
            deferred.remove(0); // out of reach even from the stump's column: left (rare)
            return;
        }
        if (!npc.getNavigation().isDone()) {
            npc.getNavigation().stop();
        }
        status("Felling the last logs from the stump", "Chopping");
        if (chopBlock(wp, log)) {
            npc.awardXp(1);
            deferred.remove(0);
        }
    }

    /** The tree is down: on to its canopy. */
    private void finishTree(WorkplaceState wp) {
        ForestScan.TreeClaims.release(spot, npc.getUUID());
        felling = null;
        lastStump = spot;
        lastBig = big;
        treeCache = null;
        nextScan = now();
        logs.clear();
        deferred.clear();
        findLeaves();
        state = State.LEAVES;
    }

    /** The felled tree's natural canopy within a player's reach of the stump (a wider box for a 2x2 trunk). */
    private void findLeaves() {
        leaves.clear();
        leavesFound = true;
        if (lastStump == null) {
            return;
        }
        int r = lastBig ? 5 : 3;
        for (BlockPos p : BlockPos.betweenClosed(lastStump.offset(-r, 0, -r), lastStump.offset(r, 12, r))) {
            if (cuttableLeaves(p)) {
                leaves.add(p.immutable());
            }
        }
        leaves.sort(Comparator.comparingDouble(p -> p.distSqr(npc.blockPosition())));
    }

    /** Breaks the reachable leaves by hand; their loot tables drop saplings, sticks and apples. */
    private void breakLeaves(WorkplaceState wp) {
        if (!leavesFound) {
            findLeaves(); // after a reload
        }
        while (!leaves.isEmpty() && (!cuttableLeaves(leaves.get(0)) || !BlockWork.inReach(npc, leaves.get(0), 5.5))) {
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

    /**
     * Leaves a woodcutter may cut: natural leaves only, never persistent ones (hedges, leaf roofs, decoration) and
     * never part of a base's plan.
     */
    private boolean cuttableLeaves(BlockPos p) {
        BlockState s = level().getBlockState(p);
        return s.is(BlockTags.LEAVES) && !(s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT))
                && !WorkSafety.planned(data(), p);
    }

    /** A leaf block within reach, at foot or head height, between the woodcutter and the trunk. */
    private BlockPos leafInTheWay() {
        if (spot == null) {
            return null;
        }
        // near the tree, or so overgrown that walking gets no closer: cut through
        boolean overgrown = npc.navigator().ticksWithoutProgress() > 100;
        if (npc.blockPosition().distSqr(spot) > 8 * 8 && !overgrown) {
            return null;
        }
        double own = npc.blockPosition().distSqr(spot);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(npc.blockPosition().offset(-3, 0, -3), npc.blockPosition().offset(3, 1, 3))) {
            if (p.distSqr(spot) < own && cuttableLeaves(p) && BlockWork.inReach(npc, p, 4.5)) {
                double d = p.distSqr(npc.blockPosition());
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ collecting

    /** Items the woodcutter could not get to (on a fence post, on top of leaves); left for later. */
    private final Set<UUID> unreachableItems = new HashSet<>();
    private UUID chasing;
    private int chaseTicks;

    /** The nearest dropped item around the last stump that can be walked to (not resting on leaves, not given up on). */
    private ItemEntity collectable() {
        if (lastStump == null) {
            return null;
        }
        int r = lastBig ? 7 : 6;
        AABB area = new AABB(lastStump).inflate(r, 2, r).expandTowards(0, 10, 0);
        return BlockWork.nearestItem(npc, area, x -> true, e ->
                !unreachableItems.contains(e.getUUID()) && !level().getBlockState(e.blockPosition().below()).is(BlockTags.LEAVES)
                        && !level().getBlockState(e.blockPosition()).is(BlockTags.LEAVES));
    }

    /** Walks over to the logs, saplings and apples lying around the stump and picks them up. */
    private void collectDrops() {
        ItemEntity item = collectable();
        if (item == null || ++collectTicks > 400 || Inv.freeSlots(npc.inventory()) == 0) {
            if (collectTicks > 400) {
                lastStump = null; // enough: what is left lies where it cannot be reached
            }
            state = Inv.freeSlots(npc.inventory()) == 0 ? State.STORAGE : State.CHECK;
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

    // ------------------------------------------------------------------ replanting

    private int carried(ForestRules.Species s) {
        net.minecraft.world.item.Item item = ForestScan.saplingItem(s);
        return Inv.count(npc.inventory(), x -> x.is(item));
    }

    /** Saplings of each species the queued stumps need: one each, four for a dark oak. */
    private static Map<ForestRules.Species, Integer> reserved(WorkplaceState wp) {
        Map<ForestRules.Species, Integer> out = new EnumMap<>(ForestRules.Species.class);
        for (WorkplaceState.Replant e : wp.replant) {
            ForestRules.Species s = ForestRules.Species.byName(e.species());
            if (s != null) {
                out.merge(s, s.saplingsPerTree(), Integer::sum);
            }
        }
        return out;
    }

    /** Saplings of a species it carries beyond what the queued stumps need. */
    private int spare(ForestRules.Species s, Map<ForestRules.Species, Integer> reserved) {
        return carried(s) - reserved.getOrDefault(s, 0);
    }

    /**
     * The nearest stump that can be acted on: plant its own species (or, after a long wait, any other sapling it
     * carries), fetch its sapling from the storehouse, or forget it after waiting too long. True when it set a state.
     */
    private boolean replantQueue(BaseState b, WorkplaceState wp) {
        if (wp.replant.isEmpty()) {
            return false;
        }
        List<WorkplaceState.Replant> entries = new ArrayList<>(wp.replant);
        entries.sort(Comparator.comparingDouble(e -> BlockPos.of(e.pos()).distSqr(npc.blockPosition())));
        Map<ForestRules.Species, Integer> reserved = reserved(wp);
        // the storehouse is counted at most once per species here, and looked into at most every STORAGE_LOOK_TICKS
        // while no trip is made (decide() runs every tick on a forage walk)
        Map<ForestRules.Species, Integer> stored = new EnumMap<>(ForestRules.Species.class);
        boolean looked = false;
        for (WorkplaceState.Replant e : entries) {
            BlockPos p = BlockPos.of(e.pos());
            ForestRules.Species want = ForestRules.Species.byName(e.species());
            if (remoteAtNight(p) || skipUntil.getOrDefault(e.pos(), 0L) > now()) {
                continue;
            }
            int same = want == null ? 0 : carried(want);
            ForestRules.Species sub = ForestRules.substitute(s -> s == want ? 0 : spare(s, reserved));
            ForestRules.Replant r = ForestRules.replant(want, e.big(), same, sub, now() - e.since());
            switch (r.action()) {
                case DROP -> {
                    wp.replant.remove(e);
                    data().setDirty();
                }
                case SAME, SUBSTITUTE -> {
                    spot = p;
                    plantSpecies = r.species();
                    plantCount = r.count();
                    plantSquare = r.twoByTwo();
                    plantGrove = false;
                    walkTicks = 0;
                    markBusy();
                    state = State.PLANT;
                    return true;
                }
                case WAIT -> {
                    if (want != null && now() >= nextSaplingTrip && now() >= nextStorageLook) {
                        looked = true;
                        net.minecraft.world.item.Item item = ForestScan.saplingItem(want);
                        int inStore = stored.computeIfAbsent(want, s -> FactionStorage.count(level(), b, x -> x.is(item)));
                        if (inStore >= want.saplingsPerTree() - same) {
                            nextSaplingTrip = now() + SAPLING_TRIP_TICKS; // its sapling is in the storehouse: fetch it
                            state = State.STORAGE;
                            return true;
                        }
                    }
                }
            }
        }
        if (looked) {
            nextStorageLook = now() + STORAGE_LOOK_TICKS; // none of the missing saplings is stored: look again later
        }
        return false;
    }

    /** Plants the sapling(s) chosen for {@link #spot}: a stump's replanting or a grove spot. No terrain is changed. */
    private void plant(WorkplaceState wp) {
        if (!NpcNavigator.within(npc, spot, 3.0)) {
            if (++walkTicks > SPOT_GIVE_UP_TICKS) {
                if (plantGrove) {
                    grove.reject(spot); // the grove search would offer it again and again: another spot is taken
                } else {
                    skipFor(spot); // cannot get to this stump for now
                }
                npc.navigator().reset();
                state = State.CHECK;
                return;
            }
            if (!goTo(spot, 3.0, "a planting spot")) {
                return;
            }
        }
        List<BlockPos> cells = plantSquare ? List.of(spot, spot.east(), spot.south(), spot.south().east()) : List.of(spot);
        Block sapling = ForestScan.saplingBlock(plantSpecies);
        boolean fits = true;
        for (BlockPos c : cells) {
            BlockState at = level().getBlockState(c);
            BlockState below = level().getBlockState(c.below());
            fits &= (at.isAir() || at.canBeReplaced()) && at.getFluidState().isEmpty() && below.is(BlockTags.DIRT)
                    && !below.is(Blocks.FARMLAND) && sapling.defaultBlockState().canSurvive(level(), c);
        }
        if (!fits) {
            // something else stands there now, or the ground would not hold a sapling: the spot is given up
            if (plantGrove) {
                grove.reject(spot);
            } else {
                wp.replant.removeIf(e -> e.pos() == spot.asLong());
                data().setDirty();
            }
            state = State.CHECK;
            return;
        }
        net.minecraft.world.item.Item item = ForestScan.saplingItem(plantSpecies);
        if (Inv.count(npc.inventory(), x -> x.is(item)) < plantCount) {
            state = State.CHECK; // the saplings went elsewhere meanwhile: decide again
            return;
        }
        Inv.consume(npc.inventory(), x -> x.is(item), plantCount);
        npc.inventory().setChanged();
        for (BlockPos c : cells) {
            level().setBlock(c, sapling.defaultBlockState(), Block.UPDATE_ALL);
            wp.addPlanted(c);
        }
        level().playSound(null, spot, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        npc.swing(InteractionHand.MAIN_HAND);
        lookAt(spot);
        if (!plantGrove) {
            wp.replant.removeIf(e -> e.pos() == spot.asLong());
        }
        data().setDirty();
        status("Planting " + (plantCount > 1 ? plantCount + " " + plantSpecies.label() + " saplings" : "a " + plantSpecies.label() + " sapling")
                + (plantGrove ? " in the grove" : " where a tree stood"), "Planting");
        if (level().random.nextDouble() < npc.affix(Affix.REGROWTH)) {
            growNow(spot, 2);
            status("Instant regrowth!", "Planting");
        }
        state = State.CHECK;
    }

    /** Leaves a stump or sapling it could not get to alone for {@link #SPOT_RETRY_TICKS}; spots whose time is up are forgotten. */
    private void skipFor(BlockPos p) {
        long t = now();
        if (skipUntil.size() >= 64) {
            skipUntil.values().removeIf(until -> until <= t);
        }
        skipUntil.put(p.asLong(), t + SPOT_RETRY_TICKS);
    }

    private void growNow(BlockPos p, int steps) {
        for (int i = 0; i < steps; i++) {
            BlockState s = level().getBlockState(p);
            if (s.getBlock() instanceof SaplingBlock sb) {
                sb.advanceTree(level(), p, s, level().random);
            }
        }
    }

    // ------------------------------------------------------------------ tending and the grove

    /** Forgets planted saplings that are no longer saplings (grown into trees or gone), where loaded; at most every 200 ticks. */
    private void prunePlanted(WorkplaceState wp) {
        if (now() < nextPrune || wp.planted.isEmpty()) {
            return;
        }
        nextPrune = now() + 200;
        boolean changed = wp.planted.removeIf(k -> {
            BlockPos p = BlockPos.of(k);
            return level().isLoaded(p) && !(level().getBlockState(p).getBlock() instanceof SaplingBlock);
        });
        if (changed) {
            data().setDirty();
        }
    }

    /**
     * The next of its saplings to give bone meal, in turn, those outside the work belt first (inside it, random ticks
     * do the work). True when it set a state.
     */
    private boolean boneMealTarget(BaseState b, WorkplaceState wp) {
        int n = wp.planted.size();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                int idx = (tendCursor + i) % n;
                BlockPos p = BlockPos.of(wp.planted.get(idx));
                if (!level().isLoaded(p) || ForestScan.inBelt(b, p) != (pass == 1) || remoteAtNight(p)
                        || skipUntil.getOrDefault(p.asLong(), 0L) > now() || !(level().getBlockState(p).getBlock() instanceof SaplingBlock)) {
                    continue;
                }
                tendCursor = (idx + 1) % n;
                spot = p;
                progress = 0;
                walkTicks = 0;
                markBusy();
                state = State.BONEMEAL;
                return true;
            }
        }
        return false;
    }

    /** Uses real bone meal on a sapling (vanilla 45% chance to advance each use). */
    private void boneMeal() {
        BlockState s = level().getBlockState(spot);
        ItemStack meal = Inv.first(npc.inventory(), x -> x.is(Items.BONE_MEAL));
        if (!(s.getBlock() instanceof SaplingBlock) || meal.isEmpty()) {
            state = State.CHECK;
            return;
        }
        if (!NpcNavigator.within(npc, spot, 3.0)) {
            if (++walkTicks > SPOT_GIVE_UP_TICKS) {
                skipFor(spot);
                npc.navigator().reset();
                state = State.CHECK;
                return;
            }
            if (!goTo(spot, 3.0, "a sapling")) {
                return;
            }
        }
        lookAt(spot);
        status("Using bone meal on a sapling", "Tending");
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

    /** Planted saplings inside the base's work belt. */
    private static int inBelt(BaseState b, WorkplaceState wp) {
        int n = 0;
        for (long k : wp.planted) {
            if (ForestScan.inBelt(b, BlockPos.of(k))) {
                n++;
            }
        }
        return n;
    }

    /** True while the grove is short of saplings: fewer than groveSize planted saplings in the work belt. */
    private static boolean groveWanted(BaseState b, WorkplaceState wp) {
        int size = FCConfig.GROVE_SIZE.get();
        return size > 0 && inBelt(b, wp) < size;
    }

    /** A grove spot to plant one of its spare saplings in. True when it set a state. */
    private boolean groveTarget(BaseState b, WorkplaceState wp) {
        if (!groveWanted(b, wp)) {
            return false;
        }
        Map<ForestRules.Species, Integer> reserved = reserved(wp);
        ForestRules.Species sp = ForestRules.groveSpecies(s -> spare(s, reserved));
        if (sp == null) {
            return false;
        }
        BlockPos p = grove.next(level(), data(), b);
        if (p == null || remoteAtNight(p) || skipUntil.getOrDefault(p.asLong(), 0L) > now()) {
            return false;
        }
        spot = p;
        plantSpecies = sp;
        plantCount = 1;
        plantSquare = false;
        plantGrove = true;
        walkTicks = 0;
        markBusy();
        state = State.PLANT;
        return true;
    }

    // ------------------------------------------------------------------ foraging

    /** Where the current forage leg goes: 72 blocks out from the centre in the current direction, kept on the map. */
    private BlockPos forageTargetFor(BaseState b) {
        double a = forageDir * Math.PI * 2 / FORAGE_DIRECTIONS;
        BlockPos p = b.center.offset((int) Math.round(Math.cos(a) * FORAGE_DISTANCE), 0, (int) Math.round(Math.sin(a) * FORAGE_DISTANCE));
        return MapBounds.clamp(data(), p, 16);
    }

    /**
     * No tree near the base: like a player looking for a forest, walk out about 70 blocks in one direction (a
     * different one each time) and look around there. After eight directions with nothing found it rests. GameTest
     * bases never forage.
     */
    private boolean forage(BaseState b) {
        if (!BaseZones.forageAllowed(b)) {
            return false;
        }
        if (forageTarget == null) {
            if (now() < forageRestUntil && forageRestUntil - now() <= FORAGE_REST_TICKS) {
                return false;
            }
            forageTarget = forageTargetFor(b);
            forageStart = now();
        }
        BlockPos t = level().isLoaded(forageTarget) ? level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, forageTarget) : forageTarget;
        status("Looking for trees further out (" + t.toShortString() + ")", "Travelling");
        // the walk itself is not work found: a woodcutter already idle (a round of foraging found nothing and it has
        // no saplings) stays idle while it looks again, as a fisherman does, so the director can retrain it
        if (goTo(t, 8.0, "trees further out") || now() - forageStart > FORAGE_LEG_TICKS) {
            forageTarget = null;
            // every column around the forage point is read (all four patterns), so a lone tree there is not missed
            ForestScan.Found tree = null;
            for (int i = 0; i < ForestScan.PATTERNS && tree == null; i++) {
                tree = ForestScan.findTree(level(), data(), b, npc, npc.blockPosition(), FORAGE_SCAN, badWild, scans++,
                        this::remoteAtNight);
            }
            if (tree != null) {
                forageFails = 0;
                treeCache = tree;
                spot = tree.stump();
                big = tree.big();
                startTree();
                return true;
            }
            forageDir = (forageDir + 1) % FORAGE_DIRECTIONS;
            if (++forageFails >= FORAGE_DIRECTIONS) {
                forageFails = 0;
                forageRestUntil = now() + FORAGE_REST_TICKS;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ the storehouse

    private void storage(BaseState b, WorkplaceState wp) {
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
        // the saplings the stumps wait for: one each (four for a dark oak)
        for (Map.Entry<ForestRules.Species, Integer> e : reserved(wp).entrySet()) {
            int need = e.getKey().saplingsPerTree() - carried(e.getKey());
            net.minecraft.world.item.Item item = ForestScan.saplingItem(e.getKey());
            if (need > 0) {
                takeFromStorage(x -> x.is(item), need);
            }
        }
        // saplings for the grove
        if (groveWanted(b, wp)) {
            int have = 0;
            for (ForestRules.Species s : ForestRules.GROVE_ORDER) {
                have += carried(s);
            }
            for (int i = 0; have < 4 && i < ForestRules.GROVE_ORDER.size(); i++) {
                net.minecraft.world.item.Item item = ForestScan.saplingItem(ForestRules.GROVE_ORDER.get(i));
                have += takeFromStorage(x -> x.is(item), 6 - have);
            }
        }
        int boneMeal = Inv.count(npc.inventory(), x -> x.is(Items.BONE_MEAL));
        if (boneMeal < 8) {
            takeFromStorage(x -> x.is(Items.BONE_MEAL), 16 - boneMeal);
        }
        state = State.CHECK;
    }

    // ------------------------------------------------------------------ an old fence

    /** The posts of an old plot's fence still standing (every tier's ring), not part of any base's plan; null if any is not loaded. */
    private List<BlockPos> fencePosts(WorkplaceState wp) {
        Set<BlockPos> ring = new LinkedHashSet<>();
        for (int t = 1; t <= 3; t++) {
            ring.addAll(TreePlotLayout.fence(wp, t));
        }
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : ring) {
            if (!level().isLoaded(p)) {
                return null;
            }
            if (level().getBlockState(p).is(Blocks.OAK_FENCE) && !WorkSafety.planned(data(), p)) {
                out.add(p);
            }
        }
        return out;
    }

    /** The nearest post of an old fence to take down; when none is left the fence is done with. True when it set a state. */
    private boolean startDismantling(WorkplaceState wp) {
        List<BlockPos> posts = fencePosts(wp);
        if (posts == null) {
            return false; // part of it is not loaded: later
        }
        BlockPos best = null;
        for (BlockPos p : posts) {
            if (!skippedPosts.contains(p.asLong()) && (best == null || p.distSqr(npc.blockPosition()) < best.distSqr(npc.blockPosition()))) {
                best = p;
            }
        }
        if (best == null) {
            wp.legacyFence = false;
            data().setDirty();
            Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal(npc.npcName() + " takes down the old tree-plot fence"));
            return false;
        }
        spot = best;
        walkTicks = 0;
        postFailBase = routeFailures();
        markBusy();
        state = State.DISMANTLE;
        return true;
    }

    /** Breaks an old fence post with the axe at player speed; the fence goes into the backpack, for the storehouse. */
    private void dismantle(WorkplaceState wp) {
        if (spot == null || !level().getBlockState(spot).is(Blocks.OAK_FENCE)) {
            breaker.reset(npc);
            state = State.CHECK;
            return;
        }
        if (!NpcNavigator.within(npc, spot, 3.0)) {
            if (routeFailures() < postFailBase) {
                postFailBase = routeFailures(); // an arrival elsewhere reset the count
            }
            if (++walkTicks > POST_GIVE_UP_TICKS || routeFailures() - postFailBase >= POST_ROUTE_FAILURES) {
                skippedPosts.add(spot.asLong()); // cannot get to it: left standing
                npc.navigator().reset();
                state = State.CHECK;
                return;
            }
            goTo(spot, 3.0, "the old fence");
            return;
        }
        if (!npc.getNavigation().isDone()) {
            npc.getNavigation().stop();
        }
        status("Taking down the old tree-plot fence", "Building");
        if (breaker.tick(npc, spot)) {
            state = State.CHECK;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        breaker.reset(npc);
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("v", 2);
        tag.putString("state", state.name());
        if (spot != null) {
            tag.putLong("spot", spot.asLong());
        }
        if (felling != null) {
            tag.putLong("felling", felling.asLong());
        }
        tag.putBoolean("big", big);
        tag.putLongArray("badWild", badWild.stream().mapToLong(Long::longValue).toArray());
        tag.putInt("forageDir", forageDir);
        tag.putInt("forageFails", forageFails);
    }

    /**
     * A tag without 'v' comes from the fenced-plot version: it starts over in CHECK without its spot, so a tree it was
     * in the middle of is never taken for a plot tree; the stump of a tree it had started to fell is kept. Old states
     * (CRAFT, UPGRADE) and unknown ones load as CHECK; planting and tending pick their target again.
     */
    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        boolean current = tag.getInt("v") >= 2;
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            state = State.CHECK;
        }
        spot = current ? readPos(tag, "spot") : null;
        felling = readPos(tag, "felling");
        big = current && tag.getBoolean("big");
        if (!current || state == State.PLANT || state == State.BONEMEAL || state == State.DISMANTLE
                || spot == null && state != State.STORAGE && state != State.CHECK) {
            state = State.CHECK;
        }
        if (state == State.LEAVES || state == State.COLLECT) {
            lastStump = spot;
            lastBig = big;
        }
        leavesFound = false;
        badWild.clear();
        for (long k : tag.getLongArray("badWild")) {
            badWild.add(k);
        }
        while (badWild.size() > MAX_BAD_WILD) {
            badWild.remove(badWild.iterator().next());
        }
        forageDir = Math.floorMod(tag.getInt("forageDir"), FORAGE_DIRECTIONS);
        forageFails = Math.max(0, Math.min(FORAGE_DIRECTIONS - 1, tag.getInt("forageFails")));
    }
}
