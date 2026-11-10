package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.Logistics;
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
 * ({@link TreeShape}): a trunk touching anything built or another kind of log is left standing whole, and so is wood
 * near bases, in other factions' land or near known villages ({@link WorkSafety#protectedWood}). A tree too tall to
 * fell from the ground (a mega spruce, a giant jungle tree, a fancy oak) is felled from a scaffold pillar of dirt
 * beside the trunk ({@link Scaffold}), when it carries the blocks: it pillars up, fells the tree top down and clears
 * the canopy within reach, then breaks the pillar on the way down, the blocks going back into its backpack; without
 * blocks such a tree is left standing. It speeds its saplings up with real bone meal, keeps a small grove growing in
 * the base's always-ticking work belt (so a base in treeless land still gets wood), takes down the fence of an old
 * save's tree plot, and walks further out to look for trees when none grow near the base. Between dusk and dawn it
 * does not work far outside the walls.
 */
public class WoodcutterJob extends Job {
    enum State { CHECK, STORAGE, CHOP, SCAFFOLD, LEAVES, COLLECT, PLANT, BONEMEAL, DISMANTLE }

    /** PROPOSED: logs, sticks and apples carried before a trip to the storehouse. */
    static final int CARGO_LIMIT = 48;
    /** PROPOSED: how far it reaches for a log (eyes to its centre), from the ground or from its scaffold pillar. */
    static final double TRUNK_REACH = TreeShape.REACH;
    /** PROPOSED: the walk to a tree ends this close (eyes to the stump), then it is cut from where the woodcutter stands. */
    static final double CHOP_REACH = 4.0, APPROACH = 2.5;
    /**
     * A tree not reached within {@link ForestRules#walkBudget} walking ticks (400 plus 8 a block of the way), or after
     * this many failed routes, is left alone for {@link #TREE_RETRY_TICKS} (PROPOSED): only what the tree is (its
     * survey) leaves it standing for good.
     */
    static final int TREE_ROUTE_FAILURES = 3, TREE_RETRY_TICKS = 12000;
    /** PROPOSED: one tree it left standing for good is looked at again this often, from afar, in case it changed. */
    static final int RECHECK_TICKS = 200;
    /** PROPOSED: a log far out on a branch is cut standing within this of the ground right under it. */
    static final double UNDER_BRANCH = 1.0;
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
    /**
     * PROPOSED: a planting spot or sapling not reached in this many walking ticks (plus 8 a block of the way) or after
     * {@link #TREE_ROUTE_FAILURES} failed routes is left for a while.
     */
    static final int SPOT_GIVE_UP_TICKS = 600, SPOT_RETRY_TICKS = 2400;
    /** PROPOSED: grove saplings taken from the storehouse at once, at most. */
    static final int GROVE_TAKE = 8;
    /** PROPOSED: what it keeps when unloading: saplings of each species, and bone meal. */
    static final int KEEP_SAPLINGS = 8, KEEP_BONE_MEAL = 16;
    /** How far it reaches for leaves (eyes to the block's centre), from the ground or from its scaffold. */
    static final double LEAF_REACH = 5.5;
    /** PROPOSED: jumps on the scaffold that land without a block going in under it before the pillar stops there. */
    static final int MAX_MISSES = 6;
    /** PROPOSED: pillars built for one tree before what is left of it is cut from the ground as far as it goes. */
    static final int MAX_PILLARS_PER_TREE = 3;
    /** PROPOSED: tries at taking down scaffold blocks left standing before they are given up. */
    static final int MAX_STRAY_TRIES = 3;
    /**
     * PROPOSED: an instant regrowth earned while standing in the sapling's room waits this long for the woodcutter to
     * step clear; bone meal waits this long for the step back before the sapling is left for a while.
     */
    static final int REGROW_WAIT_TICKS = 200, STEP_CLEAR_TICKS = 40;

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

    /**
     * The logs of the tree being felled still to cut, top first, and those out of reach for now (cut at the end). Both
     * are kept, and saved, until the tree is done: once the trunk below a log is cut, the survey no longer finds it.
     */
    private final List<BlockPos> logs = new ArrayList<>();
    private final List<BlockPos> deferred = new ArrayList<>();
    private final List<BlockPos> leaves = new ArrayList<>();
    private final BlockWork.Breaker breaker = new BlockWork.Breaker();
    private boolean surveyed, leavesFound, closerDone;
    private int walkTicks, closerTicks, finalTicks, collectTicks, progress;
    /** The current walk's allowance (see {@link ForestRules#walkBudget}) and the route failures when it began. */
    private int walkBudget, walkFailBase;
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
    private long nextSaplingTrip, nextStorageLook, nextGroveLook, nextAxeCheck, nextPrune, nextRecheck;
    private int tendCursor;
    private final Set<Long> skippedPosts = new HashSet<>();
    private int postFailBase;
    /** Trees, planting spots and saplings it could not get to, until a game time. */
    private final Map<Long, Long> skipUntil = new HashMap<>();
    private final ForestScan.GroveSearch grove = new ForestScan.GroveSearch();

    /** The scaffold pillar it is walking to, building, working from or taking down (null: none). Saved. */
    private Scaffold.Pillar pillar;
    /** Natural leaves around a tall tree's crown, highest first, cut from the pillar for their saplings. */
    private final List<BlockPos> canopy = new ArrayList<>();
    private boolean canopyFound;
    /** Pillars built for the tree being felled (a climb cut short leaves logs for another one). */
    private int pillars;
    /** Scaffold blocks left standing that it is not on (it was knocked off): taken down from beside. Saved. */
    private final List<BlockPos> strays = new ArrayList<>();
    private int strayTries;
    /**
     * A sapling (or square) whose instant regrowth waits until the woodcutter stands clear of it, and until when; not
     * saved (a reload only loses the bonus).
     */
    private BlockPos regrowAt;
    private boolean regrowSquare;
    private long regrowUntil;
    private int clearTicks;
    /** Scaffold blocks to fetch on the next storehouse visit; the storehouse's count of them, looked at now and then. */
    private boolean wantScaffold;
    private long nextScaffoldTrip, nextScaffoldLook, nextScaffoldSweep;
    private int storedScaffold;

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

    /** True while this woodcutter leaves a tree, planting spot or sapling it could not get to alone for a while. */
    public boolean skipping(BlockPos p) {
        return skipUntil.getOrDefault(p.asLong(), 0L) > now();
    }

    /** The scaffold pillar it is busy with, or null (for the GameTests). */
    public Scaffold.Pillar pillar() {
        return pillar;
    }

    @Override
    protected int keep(ItemStack s) {
        if (s.is(ItemTags.SAPLINGS)) {
            return KEEP_SAPLINGS; // per species: the backpack keeps 8 of each
        }
        if (s.is(Items.DIRT)) {
            return Scaffold.CARRY; // its scaffold blocks stay with it: dirt, topped up with coarse dirt
        }
        if (s.is(Items.COARSE_DIRT)) {
            return Math.max(0, Scaffold.CARRY - Inv.count(npc.inventory(), x -> x.is(Items.DIRT)));
        }
        return s.is(Items.BONE_MEAL) ? KEEP_BONE_MEAL : 0;
    }

    /**
     * True while it stands on its scaffold pillar (or jumps up onto it): fights, flight from them and creepers leave
     * it there, and nothing knocks it off. Hurt up there, it comes down first (see {@link #interruption}).
     */
    @Override
    public boolean onScaffold() {
        return pillar != null && (pillar.height > 0 || pillar.phase == Scaffold.Phase.CLIMB && !npc.onGround());
    }

    @Override
    public boolean ignoresCombat() {
        return onScaffold();
    }

    @Override
    public boolean handlesOwnSafety() {
        return onScaffold();
    }

    /**
     * Before a trip home for food: down its scaffold first, breaking it block by block (the felling goes on
     * afterwards). A pillar it has not climbed yet is simply given up.
     */
    @Override
    protected boolean beforeErrand() {
        if (pillar == null) {
            return true;
        }
        comeDown("hungry");
        scaffold(base(), null);
        return pillar == null;
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
        if ((b == null || wp == null) && pillar != null) {
            comeDown("no woodland"); // its base or woodland is gone: down the scaffold all the same
            scaffold(b, null);
            return;
        }
        if (b == null || wp == null) {
            status("No woodland", "Idle");
            if (b != null) {
                markIdle("no woodland of its own"); // the base's woodland has its woodcutter: retrainable
            }
            waitFor(200);
            return;
        }
        if (regrowAt != null && (now() > regrowUntil || !inTreeRoom(saplingCells(regrowAt, regrowSquare)))) {
            if (now() <= regrowUntil) {
                growNow(regrowAt, 2); // stepped clear of it: the regrowth it earned when planting
            }
            regrowAt = null;
        }
        if (state != State.STORAGE) {
            collect(wp);
        }
        if (state != State.CHECK) {
            // other work took over from a forage leg (a tree found on the way, a trip to unload): the leg is over, and
            // the next one starts afresh in the same direction, with its own time, rather than ending at once
            // wherever the woodcutter then stands
            forageTarget = null;
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
            case SCAFFOLD -> scaffold(b, wp);
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
        // 2. an old save's tree-plot fence comes down first, and scaffold blocks left standing
        if (wp.legacyFence && startDismantling(wp)) {
            return;
        }
        if (strays.isEmpty()) {
            sweepScaffolds(wp); // a pillar the woodland's last woodcutter left standing (it died or was retrained up there)
        }
        if (!strays.isEmpty() && startStrayCleanup(b, wp)) {
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
        // a tree it left standing for good is looked at again now and then: one that has changed (the other kind of
        // tree it touched is gone) or that it only gave up on for the way there, in an older save, is felled after all
        recheckBadWild(b);
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
        // 6. the best natural tree around the base (a tall one only with the blocks for its scaffold, fetched first)
        ForestScan.Found tree = bestTree(b);
        if (tree != null) {
            forageFails = 0; // trees are found after all: a later round of foraging starts over
            if (!scaffoldReady(tree)) {
                return;
            }
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

    /**
     * Starts work on the tree at {@link #spot}: walk to it, survey it, fell it. A tree it has started to fell keeps
     * the logs it still had to cut (those above a cut gap are not found by a new survey).
     */
    private void startTree() {
        if (!spot.equals(felling)) {
            logs.clear();
            deferred.clear();
            pillars = 0;
        }
        surveyed = false;
        closerDone = false;
        startWalk(spot, ForestRules.WALK_BASE_TICKS);
        closerTicks = 0;
        finalTicks = 0;
        breaker.reset(npc);
        markBusy();
        state = State.CHOP;
    }

    /** A new walk to {@code p}: its allowance grows with the distance, so a far one is not given up for the way. */
    private void startWalk(BlockPos p, int baseTicks) {
        double dx = p.getX() + 0.5 - npc.getX(), dz = p.getZ() + 0.5 - npc.getZ();
        walkTicks = 0;
        walkBudget = ForestRules.walkBudget(baseTicks, Math.sqrt(dx * dx + dz * dz));
        walkFailBase = routeFailures();
    }

    /** One more tick of the current walk; true once it has taken too long or failed too often to go on. */
    private boolean walkFailed() {
        if (walkBudget <= 0) {
            startWalk(spot, ForestRules.WALK_BASE_TICKS); // a walk to a tree taken up again after a reload
        }
        if (routeFailures() < walkFailBase) {
            walkFailBase = routeFailures(); // an arrival somewhere else (an errand) reset the count
        }
        return ++walkTicks > walkBudget || routeFailures() - walkFailBase >= TREE_ROUTE_FAILURES;
    }

    /** Trees it leaves alone: given up on for good, or for now (could not get to them), or too far out tonight. */
    private boolean passOver(BlockPos stump) {
        return badWild.contains(stump.asLong()) || skipping(stump) || remoteAtNight(stump);
    }

    /** The cached tree while it still stands and is free, else a new search (at most every {@link #RESCAN_TICKS}). */
    private ForestScan.Found bestTree(BaseState b) {
        if (treeCache != null) {
            BlockPos s = treeCache.stump();
            if (isLog(s) && !passOver(s) && !ForestScan.TreeClaims.claimedByOther(s, npc.getUUID(), now())) {
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
        treeCache = ForestScan.findTree(level(), data(), b, npc, b.center, radius, badWild, scans++, this::passOver, maxTreeDy());
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

    /**
     * The tree at {@link #spot} is not to be felled, by what it is (its survey): left standing for good (only looked at
     * again from afar now and then, see {@link #recheckBadWild}); the next one is looked for at once.
     */
    private void leaveTree(String why) {
        status("Leaving a tree standing (" + why + ")", "Chopping");
        badWild.remove(spot.asLong());
        badWild.add(spot.asLong());
        while (badWild.size() > MAX_BAD_WILD) {
            badWild.remove(badWild.iterator().next());
        }
        dropTree();
    }

    /**
     * The tree at {@link #spot} could not be got to (a cliff, water, fenced in): it is left alone for a while, not for
     * good, since nothing is wrong with the tree itself. A tree it had started to fell is let go.
     */
    private void cannotReachTree() {
        status("Leaving a tree for now (cannot get to it)", "Chopping");
        skipFor(spot, TREE_RETRY_TICKS);
        dropTree();
    }

    private void dropTree() {
        ForestScan.TreeClaims.release(spot, npc.getUUID());
        if (spot.equals(felling)) {
            felling = null;
        }
        treeCache = null;
        nextScan = now(); // look for the next tree at once, rather than walk out to forage
        logs.clear();
        deferred.clear();
        pillars = 0;
        breaker.reset(npc);
        npc.navigator().reset();
        state = State.CHECK;
    }

    /**
     * Every {@link #RECHECK_TICKS}, the longest-standing tree it left for good is surveyed again from where the
     * woodcutter is (its chunk loaded): one that would now be felled whole is taken off the list and looked for at
     * once (the other kind of tree it touched was felled meanwhile, or an older save gave it up only for the way
     * there); one that is gone is forgotten; any other goes to the back of the list.
     */
    private void recheckBadWild(BaseState b) {
        if (now() < nextRecheck || badWild.isEmpty()) {
            return;
        }
        nextRecheck = now() + RECHECK_TICKS;
        long k = badWild.iterator().next();
        badWild.remove(k);
        BlockPos p = BlockPos.of(k);
        if (!level().isLoaded(p)) {
            badWild.add(k);
            return;
        }
        if (!isLog(p)) {
            return; // felled by someone else, or burnt: nothing left to remember
        }
        if (fellable(b, p)) {
            treeCache = null;
            nextScan = now();
            return;
        }
        badWild.add(k);
    }

    /**
     * True when the tree with stump {@code p} would now be felled whole, by its survey: from the ground, or from a
     * scaffold when it has the blocks for one (carried or in the storehouse) and a column beside the trunk reaches
     * every log.
     */
    private boolean fellable(BaseState b, BlockPos p) {
        TreeShape.Tree t = ForestScan.survey(level(), data(), b, p, true, false, maxTreeDy());
        if (!t.ok()) {
            return false;
        }
        List<BlockPos> wood = new ArrayList<>();
        for (int[] q : t.logs()) {
            wood.add(new BlockPos(q[0], q[1], q[2]));
        }
        return !needsScaffold(p, wood) || ForestScan.pillarSpot(level(), data(), b, p, t.big(), wood, true) != null;
    }

    // ------------------------------------------------------------------ scaffold blocks

    /** Scaffold blocks in the storehouse, counted at most every {@link #STORAGE_LOOK_TICKS}. */
    private int scaffoldStored() {
        if (now() >= nextScaffoldLook && base() != null) {
            nextScaffoldLook = now() + STORAGE_LOOK_TICKS;
            storedScaffold = FactionStorage.count(level(), base(), Scaffold::isItem);
        }
        return storedScaffold;
    }

    /**
     * How tall a tree it looks for and fells now: up to {@link TreeShape#MAX_TALL_DY} when it has the blocks for the
     * lowest scaffold (carried, or in the storehouse while a trip for them is allowed), else
     * {@link TreeShape#MAX_FELL_DY}, as without scaffolds: no tall tree is even looked at then.
     */
    private int maxTreeDy() {
        int least = TreeShape.scaffoldNeed(TreeShape.MAX_FELL_DY + 1);
        int carried = Scaffold.carried(npc);
        if (carried >= least || now() >= nextScaffoldTrip && carried + scaffoldStored() >= least) {
            return TreeShape.MAX_TALL_DY;
        }
        return TreeShape.MAX_FELL_DY;
    }

    /**
     * A trip to the storehouse for scaffold blocks, when it holds enough to make {@code need} with those carried; at
     * most one every {@link #SAPLING_TRIP_TICKS} unless {@code now} (a tree it has started on). True when it set off.
     */
    private boolean fetchScaffold(int need, boolean now) {
        if (!now && now() < nextScaffoldTrip || Scaffold.carried(npc) + scaffoldStored() < need) {
            return false;
        }
        nextScaffoldTrip = now() + SAPLING_TRIP_TICKS;
        wantScaffold = true;
        breaker.reset(npc);
        status("Fetching dirt for a scaffold", "Travelling");
        state = State.STORAGE;
        return true;
    }

    /**
     * Whether it can go for the tree found: any tree it fells from the ground, a tall one only with enough scaffold
     * blocks ({@link TreeShape#scaffoldNeed}). Short of them it fetches them (and returns false), or leaves the tree
     * for a while when the storehouse has too few.
     */
    private boolean scaffoldReady(ForestScan.Found tree) {
        int need = TreeShape.scaffoldNeed(tree.topDy());
        if (need <= Scaffold.carried(npc) || fetchScaffold(need, false)) {
            return need <= Scaffold.carried(npc);
        }
        status("Leaving a tall tree for now (no dirt for a scaffold)", "Chopping");
        skipFor(tree.stump(), TREE_RETRY_TICKS);
        treeCache = null;
        nextScan = now();
        return false;
    }

    /** True when some log of the tree with stump {@code stump} cannot be cut from the ground ({@link TreeShape#fromGround}). */
    private static boolean needsScaffold(BlockPos stump, List<BlockPos> wood) {
        for (BlockPos q : wood) {
            if (!TreeShape.fromGround(q.getX() - stump.getX(), q.getY() - stump.getY(), q.getZ() - stump.getZ())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ felling

    /** The bottom cells of the tree at {@link #spot}: the stump, or the four of a 2x2 trunk. */
    private List<BlockPos> stumpCells() {
        return big ? List.of(spot, spot.east(), spot.south(), spot.south().east()) : List.of(spot);
    }

    /**
     * Where a started tree's survey begins: the lowest log of a felled species left in its stump column(s), up to the
     * tallest tree a scaffold reaches.
     */
    private BlockPos resumeStart() {
        if (felling == null) {
            return null;
        }
        List<BlockPos> cols = big ? List.of(felling, felling.east(), felling.south(), felling.south().east()) : List.of(felling);
        for (int dy = 0; dy <= TreeShape.MAX_TALL_DY; dy++) {
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
                BlockPos leaf = leafInTheWay(spot);
                if (leaf != null) {
                    status("Cutting through leaves to reach a tree", "Chopping");
                    chopBlock(wp, leaf);
                    return;
                }
                if (walkFailed()) {
                    cannotReachTree(); // a cliff, water, fenced in: tried again later
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
                    logs.clear();
                    deferred.clear();
                }
                treeCache = null;
                nextScan = now();
                state = State.CHECK;
                return;
            }
            boolean resume = spot.equals(felling);
            BlockPos start = resume ? resumeStart() : spot;
            List<BlockPos> found = new ArrayList<>();
            if (start != null) {
                // a tall tree only with the blocks for a scaffold; a started one is finished whatever is left of it
                TreeShape.Tree tree = ForestScan.survey(level(), data(), b, start, true, resume, resume ? TreeShape.MAX_TALL_DY : maxTreeDy());
                if (!tree.ok()) {
                    // any other verdict leaves the tree standing whole, before the first cut: no floating logs
                    leaveTree(tree.verdict().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
                    return;
                }
                species = ForestScan.speciesOf(level().getBlockState(start));
                big = big || tree.big();
                for (int[] p : tree.logs()) {
                    found.add(new BlockPos(p[0], p[1], p[2]));
                }
            }
            if (resume) {
                // a started tree: what it still had to cut, though no longer joined to its stump column (above a
                // cut gap, a branch whose base is gone), is cut as well; what was out of reach stays for the end
                for (BlockPos p : logs) {
                    if (!found.contains(p) && treeLog(p)) {
                        found.add(p);
                    }
                }
                found.removeAll(deferred);
                found.sort(Comparator.comparingInt((BlockPos q) -> q.getY()).reversed());
            }
            logs.clear();
            logs.addAll(found);
            surveyed = true;
            if (startPillar(b, resume)) {
                return; // too tall to fell from the ground: up a scaffold first (or for its blocks, or on to another tree)
            }
        }
        ForestScan.TreeClaims.claim(spot, npc.getUUID(), now()); // renewed while chopping
        while (!logs.isEmpty() && !treeLog(logs.get(0))) {
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
            BlockPos later = logs.remove(0);
            if (!deferred.contains(later)) {
                deferred.add(later); // cut at the end, from the stump's column or right under it
            }
            return;
        }
        closerDone = true;
        status("Felling a tree (" + (logs.size() + deferred.size()) + " logs left)", "Chopping");
        if (chopBlock(wp, log)) {
            logs.remove(0);
            logCut(wp, log);
        }
    }

    /** A log of the tree is cut: experience, the tree counts as started, and as felled once its stump is gone. */
    private void logCut(WorkplaceState wp, BlockPos log) {
        npc.awardXp(1);
        if (felling == null) {
            felling = spot;
        }
        if (log.getY() == spot.getY() && stumpCells().contains(log) && stumpCells().stream().noneMatch(this::isLog)) {
            stumpCut(wp);
        }
    }

    /**
     * A tree with logs out of reach from the ground ({@link TreeShape#fromGround}): a scaffold pillar beside the trunk
     * ({@link ForestScan#pillarSpot}), built with blocks it carries (fetched first when the storehouse has them). A new
     * tree that no column suits is left standing whole, and one it has no blocks for is left for a while; a started
     * one that no pillar can finish (no column, no blocks, or {@link #MAX_PILLARS_PER_TREE} climbs cut short) is left
     * standing as it is, its top already felled and the rest on its stump. True when it set off for the pillar, the
     * storehouse or another tree.
     */
    private boolean startPillar(BaseState b, boolean resume) {
        List<BlockPos> wood = new ArrayList<>(logs);
        for (BlockPos p : deferred) {
            if (!wood.contains(p)) {
                wood.add(p);
            }
        }
        if (!needsScaffold(spot, wood)) {
            return false;
        }
        // A started tree that still needs a scaffold but gets none is left standing as it is: the pillars cut it
        // strictly from the top (see nextLog), so what is left stands on its stump. Cutting its foot from the ground
        // would leave the logs above hanging.
        if (pillars >= MAX_PILLARS_PER_TREE) {
            leaveTree("cannot get up to its top"); // climbs cut short again and again
            return true;
        }
        TreeShape.Pillar plan = ForestScan.pillarSpot(level(), data(), b, spot, big, wood, true);
        if (plan == null) {
            leaveTree("no room for a scaffold"); // wide branches on every side, or no firm ground beside the trunk
            return true;
        }
        int need = plan.height() + TreeShape.PILLAR_SPARE;
        if (Scaffold.carried(npc) < need) {
            if (fetchScaffold(need, resume)) {
                surveyed = false; // the tree is looked at again on its return
                return true;
            }
            if (resume) {
                leaveTree("no dirt for a scaffold");
                return true;
            }
            status("Leaving a tall tree for now (no dirt for a scaffold)", "Chopping");
            skipFor(spot, TREE_RETRY_TICKS);
            dropTree();
            return true;
        }
        // the logs it had put off for the end are cut from the pillar as well
        wood.sort(Comparator.comparingInt((BlockPos q) -> q.getY()).reversed());
        logs.clear();
        logs.addAll(wood);
        deferred.clear();
        pillar = new Scaffold.Pillar(new BlockPos(plan.x(), spot.getY(), plan.z()), plan.height(), false);
        canopy.clear();
        canopyFound = false;
        startWalk(pillar.base, ForestRules.WALK_BASE_TICKS);
        breaker.reset(npc);
        markBusy();
        state = State.SCAFFOLD;
        return true;
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

    /** A log of the tree being felled: the felled species (when known), and no part of any base's plan. */
    private boolean treeLog(BlockPos p) {
        if (!isLog(p) || WorkSafety.planned(data(), p)) {
            return false;
        }
        return species == null || ForestScan.speciesOf(level().getBlockState(p)) == species;
    }

    /**
     * Logs that were out of reach, cut at the end: standing in the cut stump's column, from where the eighth log
     * straight up is in reach, or, for a log far out on a branch (a cherry's or an acacia's crown), standing on the
     * ground right under it. Each gets {@link #FINAL_PASS_TICKS} to get there; one still out of reach is left (rare:
     * the ground under it lies lower).
     */
    private void finalPass(WorkplaceState wp) {
        while (!deferred.isEmpty() && !treeLog(deferred.get(0))) {
            deferred.remove(0);
            finalTicks = 0;
        }
        if (deferred.isEmpty()) {
            finishTree(wp);
            return;
        }
        BlockPos log = deferred.get(0);
        if (!BlockWork.inReach(npc, log, TRUNK_REACH)) {
            int dy = log.getY() - spot.getY();
            boolean column = TreeShape.fromStumpColumn(log.getX() - spot.getX(), dy, log.getZ() - spot.getZ());
            BlockPos stand = column ? spot : new BlockPos(log.getX(), spot.getY(), log.getZ());
            double reach = column ? STUMP_COLUMN : UNDER_BRANCH;
            if ((column || TreeShape.fromBelow(dy)) && finalTicks++ < FINAL_PASS_TICKS && !NpcNavigator.within(npc, stand, reach)) {
                BlockPos leaf = column ? null : leafInTheWay(stand);
                if (leaf != null) {
                    status("Cutting through leaves to reach a branch", "Chopping");
                    chopBlock(wp, leaf);
                    return;
                }
                goTo(stand, reach, column ? "the stump" : "the end of a branch");
                return;
            }
            deferred.remove(0); // out of reach even from there: left (rare)
            finalTicks = 0;
            return;
        }
        if (!npc.getNavigation().isDone()) {
            npc.getNavigation().stop();
        }
        status("Felling the last logs from below", "Chopping");
        if (chopBlock(wp, log)) {
            npc.awardXp(1);
            deferred.remove(0);
            finalTicks = 0;
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
        pillars = 0;
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
        while (!leaves.isEmpty() && (!cuttableLeaves(leaves.get(0)) || !BlockWork.inReach(npc, leaves.get(0), LEAF_REACH))) {
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

    /** A leaf block within reach, at foot or head height, between the woodcutter and {@code toward} (a trunk, a branch's end). */
    private BlockPos leafInTheWay(BlockPos toward) {
        if (toward == null) {
            return null;
        }
        // near the tree, or so overgrown that walking gets no closer: cut through
        boolean overgrown = npc.navigator().ticksWithoutProgress() > 100;
        if (npc.blockPosition().distSqr(toward) > 8 * 8 && !overgrown) {
            return null;
        }
        double own = npc.blockPosition().distSqr(toward);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(npc.blockPosition().offset(-3, 0, -3), npc.blockPosition().offset(3, 1, 3))) {
            if (p.distSqr(toward) < own && cuttableLeaves(p) && BlockWork.inReach(npc, p, 4.5)) {
                double d = p.distSqr(npc.blockPosition());
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ the scaffold pillar

    /** The tree's species, for the statuses ("the spruce"), or "tree". */
    private String treeName() {
        return species == null ? "tree" : species.label();
    }

    /**
     * One tick of work with the scaffold pillar: walking to its foot, building it up, felling from it, taking it down.
     * A woodcutter found off its pillar (knocked or moved off it) leaves the blocks still standing to be taken down
     * from beside ({@link #strayPillar}). {@code wp} is null while it only comes down (no woodland any more, an
     * errand).
     */
    private void scaffold(BaseState b, WorkplaceState wp) {
        Scaffold.Pillar p = pillar;
        if (p == null) {
            state = State.CHECK;
            return;
        }
        if (p.phase != Scaffold.Phase.WALK && !Scaffold.overColumn(npc, p.base, 0.8)) {
            strayPillar();
            return;
        }
        if (p.down == null && wp != null) {
            String why = interruption();
            if (why != null) {
                comeDown(why);
                if (pillar == null) {
                    return; // it had not climbed yet
                }
            }
        }
        switch (p.phase) {
            case WALK -> walkToPillar(wp);
            case CLIMB -> climb(wp);
            case WORK -> workFromPillar(wp);
            case DOWN -> stepDown();
        }
    }

    /**
     * Why it must come down before the work is done, or null: hurt (an arrow, a blow), a backpack with no room left
     * for what the tree and its own blocks give back, night falling out beyond the walls, or the tree taken by
     * another woodcutter.
     */
    private String interruption() {
        if (npc.hurtTime > 0) {
            return "under attack";
        }
        if (Inv.freeSlots(npc.inventory()) <= 1) {
            return "backpack full";
        }
        if (!pillar.strays && remoteAtNight(spot)) {
            return "nightfall";
        }
        if (!pillar.strays && !ForestScan.TreeClaims.claim(spot, npc.getUUID(), now())) {
            return "the tree is taken";
        }
        return null;
    }

    /**
     * It comes down now, saying why, without cutting anything more (the tree is finished later). A pillar it has not
     * climbed yet is simply given up.
     */
    private void comeDown(String why) {
        Scaffold.Pillar p = pillar;
        if (p == null || p.down != null) {
            return;
        }
        p.down = why;
        breaker.reset(npc);
        if (p.phase == Scaffold.Phase.WALK || p.height == 0 && npc.onGround() && p.phase != Scaffold.Phase.DOWN) {
            endPillar();
        } else if (p.phase != Scaffold.Phase.DOWN) {
            p.phase = Scaffold.Phase.WORK; // (lands first, then steps down without cutting)
        }
    }

    /**
     * The pillar is gone (or was never climbed): on with the tree from the ground, or with what made it come down.
     * When a climb was cut short and logs it was for are still standing, another pillar is tried (up to
     * {@link #MAX_PILLARS_PER_TREE}), else they are cut from the ground as far as they go.
     */
    private void endPillar() {
        Scaffold.Pillar p = pillar;
        pillar = null;
        canopy.clear();
        canopyFound = false;
        breaker.reset(npc);
        if (p == null) {
            return;
        }
        forgetScaffold(woodland(), p.base);
        if (p.strays) {
            strays.removeIf(q -> level().isLoaded(q) && !Scaffold.isBlock(level().getBlockState(q)));
            state = State.CHECK;
            return;
        }
        if (p.down != null) {
            if (p.down.equals("the tree is taken")) {
                if (spot.equals(felling)) {
                    felling = null;
                }
                logs.clear();
                deferred.clear();
                treeCache = null;
                nextScan = now();
            }
            surveyed = false; // the tree (now felling, if a log was cut) is surveyed again when it comes back
            state = p.down.equals("backpack full") ? State.STORAGE : State.CHECK;
            return;
        }
        logs.removeIf(q -> !treeLog(q));
        if (needsScaffold(spot, logs)) {
            pillars++; // the climb stopped short (something in the way): try again from a fresh survey
            surveyed = false;
            if (felling == null && !logs.isEmpty()) {
                felling = spot;
            }
        }
        closerDone = false;
        closerTicks = 0;
        finalTicks = 0;
        state = State.CHOP; // the rest (the stump, low branches) from the ground, as for any tree
    }

    /**
     * To the column's foot: natural leaves or the tree's own logs filling its two lowest cells are cut first; there it
     * stands right over the middle of the column and starts building (or, for a pillar of height 0, works from the
     * ground).
     */
    private void walkToPillar(WorkplaceState wp) {
        Scaffold.Pillar p = pillar;
        for (BlockPos c : List.of(p.base, p.base.above())) {
            if ((cuttableLeaves(c) || logs.contains(c) && treeLog(c)) && BlockWork.inReach(npc, c, 4.5)) {
                status("Clearing the foot of its scaffold", "Chopping");
                if (chopBlock(wp, c) && logs.remove(c)) {
                    logCut(wp, c);
                }
                return;
            }
        }
        boolean there = Scaffold.overColumn(npc, p.base, 0.7) && Math.abs(npc.getY() - p.base.getY()) < 0.5 && npc.onGround();
        if (!there) {
            if (walkFailed()) {
                if (p.strays) {
                    pillar = null;
                    giveUpStrays(wp); // cannot get to them: left (rare)
                    state = State.CHECK;
                } else {
                    pillar = null;
                    cannotReachTree();
                }
                return;
            }
            goTo(p.base, 0.7, p.strays ? "scaffold blocks left standing" : "the foot of its scaffold");
            return;
        }
        npc.getNavigation().stop();
        npc.setPos(p.base.getX() + 0.5, npc.getY(), p.base.getZ() + 0.5);
        p.height = 0;
        p.airTicks = 0;
        p.misses = 0;
        p.phase = p.goal > 0 ? Scaffold.Phase.CLIMB : Scaffold.Phase.WORK;
    }

    /**
     * Building up, as a player pillars: on the ground (or the pillar's top) it clears what fills the cell over its
     * head (leaves, a log of the tree) and jumps; once its feet are above the cell it stood in, a block goes in there
     * from the backpack, and it lands on it. At the planned height (or when the way up is blocked, or out of blocks)
     * it starts work.
     */
    private void climb(WorkplaceState wp) {
        Scaffold.Pillar p = pillar;
        Scaffold.holdStill(npc, p.base);
        if (!npc.onGround()) {
            p.airTicks++;
            BlockPos cell = p.feet();
            if (!p.placed && npc.getY() >= cell.getY() + 1.0 && Scaffold.place(npc, cell)) {
                p.placed = true;
                p.height++;
                if (wp != null && wp.addScaffold(p.base)) {
                    data().setDirty(); // the woodland knows it stands, should this woodcutter die up there
                }
            }
            return;
        }
        if (p.airTicks > 0 && !p.placed) {
            p.misses++; // landed back where it jumped from
        }
        p.airTicks = 0;
        p.placed = false;
        p.height = Math.max(0, npc.getBlockY() - p.base.getY());
        if (p.height >= p.goal || p.misses >= MAX_MISSES || Scaffold.carried(npc) == 0) {
            p.goal = p.height;
            p.phase = Scaffold.Phase.WORK;
            return;
        }
        BlockPos feet = p.feet();
        BlockState here = level().getBlockState(feet);
        if (here.is(BlockTags.CLIMBABLE) && here.canBeReplaced()) {
            // vines down the trunk: standing in them, a jump only lifts it half a block (it would hang on them), so it
            // cuts them away first, as a player clears the vines off the spot it pillars up from
            status("Cutting the vines off its scaffold's column", "Chopping");
            breaker.tick(npc, feet);
            return;
        }
        BlockPos over = feet.above(2);
        BlockState s = level().getBlockState(over);
        if (!s.getCollisionShape(level(), over).isEmpty()) {
            if (cuttableLeaves(over) || logs.contains(over) && treeLog(over)) {
                status("Cutting through the canopy above its scaffold", "Chopping");
                if (chopBlock(wp, over) && logs.remove(over)) {
                    logCut(wp, over);
                }
                return;
            }
            p.goal = p.height; // something else overhead (it changed since the survey): the pillar ends here
            p.phase = Scaffold.Phase.WORK;
            return;
        }
        status((p.strays ? "Building a scaffold to take down another (" : "Building a scaffold up the " + treeName() + " (")
                + p.height + " high)", "Building");
        npc.getJumpControl().jump();
    }

    /**
     * On the pillar, at each height on the way down: the highest log of the tree within reach is cut first (so the top
     * comes down first and nothing is left hanging), then the natural leaves of the crown within reach (their
     * saplings go into the backpack); with nothing left in reach it steps down a block. Coming down early, it cuts
     * nothing. On the ground the pillar is done.
     */
    private void workFromPillar(WorkplaceState wp) {
        Scaffold.Pillar p = pillar;
        Scaffold.holdStill(npc, p.base);
        if (!npc.onGround()) {
            return; // still dropping onto the block below
        }
        p.height = Math.max(0, npc.getBlockY() - p.base.getY());
        if (p.down == null && wp != null) {
            if (p.strays) {
                BlockPos block = nextStray();
                if (block != null) {
                    status("Taking down scaffold blocks left standing (" + strays.size() + ")", "Building");
                    if (breaker.tick(npc, block)) {
                        strays.remove(block);
                    }
                    return;
                }
            } else {
                ForestScan.TreeClaims.claim(spot, npc.getUUID(), now());
                BlockPos log = nextLog();
                if (log != null) {
                    status("Felling the " + treeName() + " from its scaffold (" + logs.size() + " logs left)", "Chopping");
                    if (chopBlock(wp, log)) {
                        logs.remove(log);
                        logCut(wp, log);
                    }
                    return;
                }
                BlockPos leaf = nextCanopyLeaf();
                if (leaf != null) {
                    status("Clearing the canopy from its scaffold (" + canopy.size() + " leaves)", "Chopping");
                    if (chopBlock(wp, leaf)) {
                        canopy.remove(leaf);
                    }
                    return;
                }
            }
        }
        if (p.height == 0) {
            endPillar();
            return;
        }
        p.phase = Scaffold.Phase.DOWN;
    }

    /**
     * The step down: the pillar block underfoot is broken at player speed (it goes back into the backpack) and the
     * woodcutter drops one block onto the next. Should the block below have gone (blown up, broken by someone), one
     * from the backpack is put back under it first, so the drop is never more than a block.
     */
    private void stepDown() {
        Scaffold.Pillar p = pillar;
        Scaffold.holdStill(npc, p.base);
        if (!npc.onGround()) {
            return;
        }
        p.height = Math.max(0, npc.getBlockY() - p.base.getY());
        BlockPos under = p.top();
        if (under == null) {
            endPillar();
            return;
        }
        BlockState s = level().getBlockState(under);
        if (s.getCollisionShape(level(), under).isEmpty()) {
            p.phase = Scaffold.Phase.WORK; // nothing under it after all: it drops, then looks again
            return;
        }
        BlockPos below = under.below();
        if (p.height >= 2 && level().getBlockState(below).getCollisionShape(level(), below).isEmpty() && Scaffold.place(npc, below)) {
            return;
        }
        status("Taking the scaffold down (" + p.height + " high" + (p.down != null ? ", " + p.down : "") + ")", "Building");
        if (s.hasBlockEntity() || s.getDestroySpeed(level(), under) < 0) {
            // not a block it can break (someone put it there): left to the stuck recovery, which digs or bridges down;
            // the scaffold blocks still standing are noted, to be taken down from beside
            strayPillar();
            return;
        }
        if (breaker.tick(npc, under)) {
            p.height--;
            p.phase = Scaffold.Phase.WORK;
        }
    }

    /**
     * The highest log of the tree still standing within reach of the pillar ({@link #TRUNK_REACH}), or null. The tree
     * comes down strictly from the top: while a log the ground cannot reach ({@link TreeShape#fromGround}) stands out
     * of reach at this log's height or above, nothing is cut (it comes into reach lower down; after a climb cut short,
     * the next pillar takes it). Cutting the middle of a trunk from a pillar that fell short of its top would leave
     * the top hanging. Logs the ground reaches never hold it up: they are cut from the ground afterwards, as for any
     * tree.
     */
    private BlockPos nextLog() {
        BlockPos best = null;
        for (java.util.Iterator<BlockPos> it = logs.iterator(); it.hasNext(); ) {
            BlockPos q = it.next(); // (the list is highest first)
            if (best != null && q.getY() < best.getY()) {
                break; // every log at its height has been looked at
            }
            boolean reach = BlockWork.inReach(npc, q, TRUNK_REACH);
            if (!reach && TreeShape.fromGround(q.getX() - spot.getX(), q.getY() - spot.getY(), q.getZ() - spot.getZ())) {
                continue;
            }
            if (!treeLog(q)) {
                it.remove(); // cut already (a branch on the way up), or gone
                continue;
            }
            if (!reach) {
                return null; // a log above it (or level with it) is not in reach yet: nothing below it is cut
            }
            if (best == null) {
                best = q;
            }
        }
        return best;
    }

    /**
     * The highest natural leaf of the crown within reach, or null. The crown is read once, from the top of the
     * pillar: natural leaves within 5 (6 for a 2x2 trunk) sideways of the stump, from 2 above it to 2 above the
     * pillar's reach. Leaves out of reach above it are dropped as it goes down (they never come into reach again).
     */
    private BlockPos nextCanopyLeaf() {
        if (!canopyFound) {
            canopyFound = true;
            canopy.clear();
            int r = big ? 6 : 5;
            int top = (int) Math.ceil(npc.getEyeY() + LEAF_REACH);
            for (BlockPos q : BlockPos.betweenClosed(spot.getX() - r, spot.getY() + 2, spot.getZ() - r, spot.getX() + r + (big ? 1 : 0),
                    top, spot.getZ() + r + (big ? 1 : 0))) {
                if (cuttableLeaves(q)) {
                    canopy.add(q.immutable());
                }
            }
            canopy.sort(Comparator.comparingInt((BlockPos q) -> q.getY()).reversed());
        }
        double eye = npc.getEyeY();
        for (java.util.Iterator<BlockPos> it = canopy.iterator(); it.hasNext(); ) {
            BlockPos q = it.next();
            if (q.getY() + 0.5 > eye + LEAF_REACH) {
                it.remove();
                continue;
            }
            if (!BlockWork.inReach(npc, q, LEAF_REACH)) {
                continue;
            }
            if (!cuttableLeaves(q)) {
                it.remove();
                continue;
            }
            return q;
        }
        return null;
    }

    /**
     * The highest scaffold block left standing within reach ({@link #TRUNK_REACH}), or null; also null while someone
     * stands on that block (an NPC retrained up there, a mob): it is not broken from under them.
     */
    private BlockPos nextStray() {
        BlockPos best = null;
        for (java.util.Iterator<BlockPos> it = strays.iterator(); it.hasNext(); ) {
            BlockPos q = it.next();
            if (level().isLoaded(q) && !Scaffold.isBlock(level().getBlockState(q))) {
                it.remove();
                continue;
            }
            if (BlockWork.inReach(npc, q, TRUNK_REACH) && (best == null || q.getY() > best.getY())) {
                best = q;
            }
        }
        if (best != null && !level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(best.above()),
                e -> e != npc && e.isAlive()).isEmpty()) {
            return null;
        }
        return best;
    }

    /**
     * Off its pillar (knocked off, or moved there by something else): the blocks of it still standing are noted, to be
     * taken down from beside ({@link #startStrayCleanup}); the tree is finished afterwards.
     */
    private void strayPillar() {
        Scaffold.Pillar p = pillar;
        for (int k = 0; k <= p.height + 1; k++) {
            BlockPos c = p.base.above(k);
            if (Scaffold.isBlock(level().getBlockState(c)) && !strays.contains(c) && strays.size() < 64) {
                strays.add(c);
            }
        }
        pillar = null;
        canopy.clear();
        canopyFound = false;
        breaker.reset(npc);
        surveyed = false;
        state = State.CHECK;
    }

    /**
     * Scaffold blocks left standing are taken down: from a column beside them, from the ground when the highest is
     * within reach from there, else from a new pillar built beside them (see {@link TreeShape#pillarSpot}). Given up
     * after {@link #MAX_STRAY_TRIES} tries, or when no column beside them will do. True when it set off.
     */
    private boolean startStrayCleanup(BaseState b, WorkplaceState wp) {
        strays.removeIf(q -> level().isLoaded(q) && !Scaffold.isBlock(level().getBlockState(q)));
        if (strays.isEmpty()) {
            strayTries = 0;
            return false;
        }
        for (BlockPos q : strays) {
            if (!level().isLoaded(q)) {
                return false; // later, when the place is loaded
            }
        }
        if (++strayTries > MAX_STRAY_TRIES) {
            giveUpStrays(wp);
            return false;
        }
        BlockPos foot = strays.get(0);
        for (BlockPos q : strays) {
            foot = q.getY() < foot.getY() ? q : foot;
        }
        // the ground the column stands on (its lowest blocks may be gone)
        for (int i = 0; i < TreeShape.MAX_PILLAR && level().getBlockState(foot.below()).getCollisionShape(level(), foot.below()).isEmpty(); i++) {
            foot = foot.below();
        }
        TreeShape.Pillar plan = ForestScan.pillarSpot(level(), data(), b, foot, false, strays, false);
        int need = plan == null || plan.height() == 0 ? 0 : plan.height() + TreeShape.PILLAR_SPARE;
        if (plan == null || Scaffold.carried(npc) < need) {
            if (plan != null && fetchScaffold(need, true)) {
                strayTries--;
                return true;
            }
            giveUpStrays(wp); // nowhere to stand beside them, or no blocks to reach them: left (rare)
            return false;
        }
        pillar = new Scaffold.Pillar(new BlockPos(plan.x(), foot.getY(), plan.z()), plan.height(), true);
        startWalk(pillar.base, ForestRules.WALK_BASE_TICKS);
        breaker.reset(npc);
        markBusy();
        state = State.SCAFFOLD;
        return true;
    }

    /** Scaffold blocks left standing are given up (rare: nowhere to stand beside them); the woodland forgets their columns. */
    private void giveUpStrays(WorkplaceState wp) {
        if (wp != null && wp.scaffolds.removeIf(k -> {
            BlockPos c = BlockPos.of(k);
            return strays.stream().anyMatch(q -> q.getX() == c.getX() && q.getZ() == c.getZ());
        })) {
            data().setDirty();
        }
        strays.clear();
        strayTries = 0;
    }

    /** This woodcutter's woodland, without claiming one. */
    private WorkplaceState woodland() {
        return Workplaces.held(npc, WorkplaceState.Type.TREE_PLOT);
    }

    /**
     * The scaffold blocks standing in the column whose lowest cell is {@code base} (none of them part of a base's
     * plan), from the bottom up to two cells in a row without one: a pillar is one column with at most a block
     * missing here and there, and the two cells over its top were clear (the woodcutter's body), so natural dirt
     * further up (an overhang) is never taken for scaffold.
     */
    private List<BlockPos> scaffoldBlocksIn(BlockPos base) {
        List<BlockPos> out = new ArrayList<>();
        int gap = 0;
        for (int k = 0; k < TreeShape.MAX_PILLAR + 2 && gap < 2; k++) {
            BlockPos c = base.above(k);
            if (Scaffold.isBlock(level().getBlockState(c)) && !WorkSafety.planned(data(), c)) {
                out.add(c);
                gap = 0;
            } else {
                gap++;
            }
        }
        return out;
    }

    /** The woodland forgets the scaffold column at {@code base} once nothing of it is left standing. */
    private void forgetScaffold(WorkplaceState wp, BlockPos base) {
        if (wp != null && wp.scaffolds.contains(base.asLong()) && level().isLoaded(base) && scaffoldBlocksIn(base).isEmpty()) {
            wp.scaffolds.remove(Long.valueOf(base.asLong()));
            data().setDirty();
        }
    }

    /**
     * The scaffold columns the woodland remembers, at most every {@link #STORAGE_LOOK_TICKS} while this woodcutter has
     * no pillar of its own: what still stands of one (its last woodcutter died or was retrained up there, or an NPC
     * stranded on it broke its way down to a safe drop) is taken down from beside ({@link #startStrayCleanup}); a
     * column with nothing left is forgotten. Columns not loaded wait.
     */
    private void sweepScaffolds(WorkplaceState wp) {
        if (wp.scaffolds.isEmpty() || now() < nextScaffoldSweep || pillar != null) {
            return;
        }
        nextScaffoldSweep = now() + STORAGE_LOOK_TICKS;
        for (long k : new ArrayList<>(wp.scaffolds)) {
            BlockPos base = BlockPos.of(k);
            if (!level().isLoaded(base)) {
                continue;
            }
            List<BlockPos> left = scaffoldBlocksIn(base);
            if (left.isEmpty()) {
                wp.scaffolds.remove(Long.valueOf(k));
                data().setDirty();
                continue;
            }
            for (BlockPos c : left) {
                if (!strays.contains(c) && strays.size() < 64) {
                    strays.add(c);
                }
            }
        }
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

    /**
     * Saplings of each species the queued stumps need: one each, four for a dark oak and for a 2x2 spruce or jungle
     * tree ({@link ForestRules#saplingsFor}).
     */
    private static Map<ForestRules.Species, Integer> reserved(WorkplaceState wp) {
        Map<ForestRules.Species, Integer> out = new EnumMap<>(ForestRules.Species.class);
        for (WorkplaceState.Replant e : wp.replant) {
            ForestRules.Species s = ForestRules.Species.byName(e.species());
            if (s != null) {
                out.merge(s, ForestRules.saplingsFor(s, e.big()), Integer::sum);
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
                    startWalk(p, SPOT_GIVE_UP_TICKS);
                    markBusy();
                    state = State.PLANT;
                    return true;
                }
                case WAIT -> {
                    if (want != null && now() >= nextSaplingTrip && now() >= nextStorageLook) {
                        looked = true;
                        net.minecraft.world.item.Item item = ForestScan.saplingItem(want);
                        int inStore = stored.computeIfAbsent(want, s -> FactionStorage.count(level(), b, x -> x.is(item)));
                        if (inStore >= ForestRules.saplingsFor(want, e.big()) - same) {
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

    /**
     * Plants the sapling(s) chosen for {@link #spot}: a stump's replanting or a grove spot. No terrain is changed; the
     * flowers around the spot are picked first for a species that would grow a bee nest beside them.
     */
    private void plant(WorkplaceState wp) {
        if (!NpcNavigator.within(npc, spot, 3.0)) {
            if (walkFailed()) {
                // cannot get to it for now (a grove spot as much as a stump: the spot itself is fine, the search
                // offers another meanwhile)
                skipFor(spot, SPOT_RETRY_TICKS);
                npc.navigator().reset();
                state = State.CHECK;
                return;
            }
            if (!goTo(spot, 3.0, "a planting spot")) {
                return;
            }
        }
        List<BlockPos> cells = plantSquare ? List.of(spot, spot.east(), spot.south(), spot.south().east()) : List.of(spot);
        BlockPos flower = ForestRules.beesNearFlowers(plantSpecies) ? flowerNear(cells) : null;
        if (flower != null) {
            status("Picking the flowers by a planting spot (no bee nest in the tree)", "Planting");
            lookAt(flower);
            level().destroyBlock(flower, true, npc);
            npc.swing(InteractionHand.MAIN_HAND);
            return;
        }
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
            if (inTreeRoom(cells)) {
                // standing where the trunk would grow (a sapling has no body to keep it out): the tree grows once it
                // has stepped clear, never into its head
                regrowAt = spot;
                regrowSquare = plantSquare;
                regrowUntil = now() + REGROW_WAIT_TICKS;
            } else {
                growNow(spot, 2);
            }
            status("Instant regrowth!", "Planting");
        }
        state = State.CHECK;
    }

    /**
     * The sapling at {@code p} and the saplings of its kind beside it (a 2x2 square grows one huge tree whose trunk
     * takes all four): the room bone meal may fill with a trunk.
     */
    private List<BlockPos> saplingsAround(BlockPos p) {
        Block kind = level().getBlockState(p).getBlock();
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, 0, -1), p.offset(1, 0, 1))) {
            if (q.equals(p) || level().getBlockState(q).is(kind)) {
                out.add(q.immutable());
            }
        }
        return out;
    }

    /** One step straight away from the middle of {@code cells}, out of the room their tree would take. */
    private void stepClearOf(List<BlockPos> cells) {
        double x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (BlockPos c : cells) {
            x0 = Math.min(x0, c.getX());
            z0 = Math.min(z0, c.getZ());
            x1 = Math.max(x1, c.getX() + 1);
            z1 = Math.max(z1, c.getZ() + 1);
        }
        double cx = (x0 + x1) / 2, cz = (z0 + z1) / 2, dx = npc.getX() - cx, dz = npc.getZ() - cz, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05) {
            dx = 1;
            dz = 0;
            len = 1;
        }
        double out = Math.max(x1 - x0, z1 - z0) / 2 + 0.9; // the box's half-width, plus the body's and a little
        npc.getNavigation().stop();
        npc.getMoveControl().setWantedPosition(cx + dx / len * out, npc.getY(), cz + dz / len * out, 1.0);
        status("Stepping out of the sapling's way", "Tending");
    }

    /** The cells of a sapling at {@code p}, or of the 2x2 square whose north-west corner it is. */
    private static List<BlockPos> saplingCells(BlockPos p, boolean square) {
        return square ? List.of(p, p.east(), p.south(), p.south().east()) : List.of(p);
    }

    /**
     * True while the woodcutter's body is in the room the trunk of a tree grown from {@code cells} takes (their
     * columns, from the ground to 3 up): vanilla grows a tree whatever stands there, and a log in its head would
     * suffocate it. Saplings have no body, so it can stand half in one after planting or reaching for it.
     */
    private boolean inTreeRoom(List<BlockPos> cells) {
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE, y = cells.get(0).getY();
        for (BlockPos c : cells) {
            x0 = Math.min(x0, c.getX());
            z0 = Math.min(z0, c.getZ());
            x1 = Math.max(x1, c.getX());
            z1 = Math.max(z1, c.getZ());
        }
        return npc.getBoundingBox().intersects(new AABB(x0, y, z0, x1 + 1, y + 3, z1 + 1));
    }

    /**
     * A flower within the box a sapling at one of {@code cells} looks at when it grows (2 sideways, 1 down and up):
     * vanilla then grows the tree with a bee nest, a block entity that would keep the tree standing for good. What
     * counts is vanilla's own #flowers tag, so a flowering azalea bush, a mangrove propagule or low natural cherry or
     * azalea leaves are picked as well as small and tall flowers and pink petals. Flowers that are part of a base's
     * plan, and leaves a player placed, are left.
     */
    private BlockPos flowerNear(List<BlockPos> cells) {
        for (BlockPos c : cells) {
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 1, 2))) {
                BlockState st = level().getBlockState(p);
                if (st.is(BlockTags.FLOWERS) && !(st.hasProperty(LeavesBlock.PERSISTENT) && st.getValue(LeavesBlock.PERSISTENT))
                        && !WorkSafety.planned(data(), p)) {
                    return p.immutable();
                }
            }
        }
        return null;
    }

    /** Leaves a tree, stump, grove spot or sapling it could not get to alone for {@code ticks}; entries whose time is up are forgotten. */
    private void skipFor(BlockPos p, int ticks) {
        long t = now();
        if (skipUntil.size() >= 64) {
            skipUntil.values().removeIf(until -> until <= t);
        }
        skipUntil.put(p.asLong(), t + ticks);
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
                startWalk(p, SPOT_GIVE_UP_TICKS);
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
            if (walkFailed()) {
                skipFor(spot, SPOT_RETRY_TICKS);
                npc.navigator().reset();
                state = State.CHECK;
                return;
            }
            if (!goTo(spot, 3.0, "a sapling")) {
                return;
            }
        }
        List<BlockPos> room = saplingsAround(spot);
        if (inTreeRoom(room)) {
            // half in the sapling's column: a step back before the tree may grow there
            if (clearTicks++ >= STEP_CLEAR_TICKS) {
                clearTicks = 0;
                skipFor(spot, SPOT_RETRY_TICKS); // hemmed in: the sapling is left for a while
                state = State.CHECK;
                return;
            }
            stepClearOf(room);
            return;
        }
        clearTicks = 0;
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

    /**
     * Saplings the grove still wants: groveSize planted saplings in the work belt (twice as many while the base is
     * short of wood, see {@link ForestRules#groveTarget}) less those growing there now. Replanted stumps in the belt
     * count as much as grove spots: they grow in the same always-ticking ground.
     */
    private static int groveShortfall(BaseState b, WorkplaceState wp) {
        int want = ForestRules.groveTarget(FCConfig.GROVE_SIZE.get(), b.shortages.contains(Logistics.Resource.WOOD.name()));
        return Math.max(0, want - inBelt(b, wp));
    }

    /** True while the grove is short of saplings. */
    private static boolean groveWanted(BaseState b, WorkplaceState wp) {
        return groveShortfall(b, wp) > 0;
    }

    /**
     * A grove spot to plant one of its spare saplings in, the best species in {@link ForestRules#GROVE_ORDER} first.
     * Carrying none (or only worse ones than the storehouse holds: oak, which may grow too tall, while birch is
     * stored), it fetches them from the storehouse, as for a stump. True when it set a state.
     */
    private boolean groveTarget(BaseState b, WorkplaceState wp) {
        if (!groveWanted(b, wp)) {
            return false;
        }
        Map<ForestRules.Species, Integer> reserved = reserved(wp);
        ForestRules.Species sp = ForestRules.groveSpecies(s -> spare(s, reserved));
        boolean mayFetch = sp != ForestRules.GROVE_ORDER.get(0) && now() >= nextSaplingTrip && now() >= nextGroveLook;
        if (sp == null && !mayFetch) {
            return false;
        }
        BlockPos p = grove.next(level(), data(), b, q -> remoteAtNight(q) || skipping(q));
        if (p == null) {
            return false; // no spot to plant in: no trip for saplings either
        }
        if (mayFetch && groveSaplingsStored(b, sp)) {
            nextSaplingTrip = now() + SAPLING_TRIP_TICKS;
            state = State.STORAGE;
            return true;
        }
        if (sp == null) {
            return false;
        }
        spot = p;
        plantSpecies = sp;
        plantCount = 1;
        plantSquare = false;
        plantGrove = true;
        startWalk(p, SPOT_GIVE_UP_TICKS);
        markBusy();
        state = State.PLANT;
        return true;
    }

    /**
     * True when the storehouse holds grove saplings better than {@code carried} (any, when it carries none). Called
     * only when a trip for saplings is due; the storehouse is looked into at most every {@link #STORAGE_LOOK_TICKS}.
     */
    private boolean groveSaplingsStored(BaseState b, ForestRules.Species carried) {
        nextGroveLook = now() + STORAGE_LOOK_TICKS;
        for (ForestRules.Species s : ForestRules.GROVE_ORDER) {
            if (s == carried) {
                return false;
            }
            net.minecraft.world.item.Item item = ForestScan.saplingItem(s);
            if (FactionStorage.count(level(), b, x -> x.is(item)) > 0) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ foraging

    /**
     * Where the current forage leg goes: 72 blocks out from the centre in the current direction, kept on the map. A
     * direction whose point lies by a pillager outpost or woodland mansion the faction knows of is passed over.
     */
    private BlockPos forageTargetFor(BaseState b) {
        BlockPos first = null;
        for (int i = 0; i < FORAGE_DIRECTIONS; i++) {
            double a = forageDir * Math.PI * 2 / FORAGE_DIRECTIONS;
            BlockPos p = MapBounds.clamp(data(), b.center.offset((int) Math.round(Math.cos(a) * FORAGE_DISTANCE), 0,
                    (int) Math.round(Math.sin(a) * FORAGE_DISTANCE)), 16);
            if (!BaseZones.nearKnownDanger(data().factions.get(b.faction), p, BaseZones.DANGER_RADIUS)) {
                return p;
            }
            first = first == null ? p : first;
            forageDir = (forageDir + 1) % FORAGE_DIRECTIONS;
        }
        return first; // dangerous every way: the first direction, as before
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
                        this::passOver, maxTreeDy());
            }
            if (tree != null) {
                forageFails = 0;
                if (scaffoldReady(tree)) {
                    treeCache = tree;
                    spot = tree.stump();
                    big = tree.big();
                    startTree();
                }
                return true; // (a tall one without the dirt for its scaffold: fetched first, or left for a while)
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
        // the saplings the stumps wait for: one (four for a dark oak or a 2x2 spruce or jungle tree)
        Map<ForestRules.Species, Integer> most = new EnumMap<>(ForestRules.Species.class);
        for (WorkplaceState.Replant r : wp.replant) {
            ForestRules.Species sp = ForestRules.Species.byName(r.species());
            if (sp != null) {
                most.merge(sp, ForestRules.saplingsFor(sp, r.big()), Math::max);
            }
        }
        for (Map.Entry<ForestRules.Species, Integer> e : most.entrySet()) {
            int need = e.getValue() - carried(e.getKey());
            net.minecraft.world.item.Item item = ForestScan.saplingItem(e.getKey());
            if (need > 0) {
                takeFromStorage(x -> x.is(item), need);
            }
        }
        // saplings for the grove, as many as it is short of (at most GROVE_TAKE), the best species first: birch from
        // the storehouse is taken even while it carries oak
        int groveWant = Math.min(GROVE_TAKE, groveShortfall(b, wp));
        Map<ForestRules.Species, Integer> queued = reserved(wp);
        int have = 0;
        for (ForestRules.Species s : ForestRules.GROVE_ORDER) {
            if (have >= groveWant) {
                break;
            }
            have += Math.max(0, spare(s, queued));
            net.minecraft.world.item.Item item = ForestScan.saplingItem(s);
            if (have < groveWant) {
                have += takeFromStorage(x -> x.is(item), groveWant - have);
            }
        }
        int boneMeal = Inv.count(npc.inventory(), x -> x.is(Items.BONE_MEAL));
        if (boneMeal < 8) {
            takeFromStorage(x -> x.is(Items.BONE_MEAL), 16 - boneMeal);
        }
        // dirt for the scaffold of a tall tree it means to fell (coarse dirt when there is no plain dirt)
        if (wantScaffold) {
            wantScaffold = false;
            int more = Scaffold.CARRY - Scaffold.carried(npc);
            if (more > 0) {
                more -= takeFromStorage(x -> x.is(Items.DIRT), more);
            }
            if (more > 0) {
                takeFromStorage(x -> x.is(Items.COARSE_DIRT), more);
            }
            nextScaffoldLook = now() + STORAGE_LOOK_TICKS;
            storedScaffold = FactionStorage.count(level(), b, Scaffold::isItem);
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
        if (felling != null || pillar != null) {
            // what it still has to cut of that tree: the survey alone would not find logs above a cut gap again
            tag.putLongArray("logs", logs.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putLongArray("deferred", deferred.stream().mapToLong(BlockPos::asLong).toArray());
            if (species != null) {
                tag.putString("species", species.name());
            }
        }
        if (pillar != null) {
            // the scaffold it stands on: column, height, phase (absent in older saves: none)
            tag.put("pillar", pillar.save());
        }
        if (pillar != null || felling != null) {
            tag.putInt("pillars", pillars); // climbs cut short on this tree so far
        }
        if (!strays.isEmpty()) {
            tag.putLongArray("strays", strays.stream().mapToLong(BlockPos::asLong).toArray());
        }
        tag.putBoolean("big", big);
        tag.putLongArray("badWild", badWild.stream().mapToLong(Long::longValue).toArray());
        tag.putInt("forageDir", forageDir);
        tag.putInt("forageFails", forageFails);
    }

    /**
     * A tag without 'v' comes from the fenced-plot version: it starts over in CHECK without its spot, so a tree it was
     * in the middle of is never taken for a plot tree; the stump of a tree it had started to fell is kept. Old states
     * (CRAFT, UPGRADE) and unknown ones load as CHECK; planting and tending pick their target again. A scaffold pillar
     * it was on is resumed exactly where it was (saves from before scaffolds have none).
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
        // a tree it was felling: the logs it still had to cut (absent in saves from before they were kept: empty)
        logs.clear();
        deferred.clear();
        species = null;
        pillar = current && tag.contains("pillar") ? Scaffold.Pillar.load(tag.getCompound("pillar")) : null;
        pillars = tag.getInt("pillars");
        canopy.clear();
        canopyFound = false;
        strays.clear();
        for (long k : tag.getLongArray("strays")) {
            strays.add(BlockPos.of(k));
        }
        strayTries = 0;
        wantScaffold = false;
        if (current && (felling != null || pillar != null)) {
            for (long k : tag.getLongArray("logs")) {
                logs.add(BlockPos.of(k));
            }
            for (long k : tag.getLongArray("deferred")) {
                deferred.add(BlockPos.of(k));
            }
            species = ForestRules.Species.byName(tag.getString("species"));
        }
        surveyed = false;
        walkBudget = 0; // a walk under way starts over, with its allowance from where the woodcutter now stands
        if (!current || state == State.PLANT || state == State.BONEMEAL || state == State.DISMANTLE
                || spot == null && state != State.STORAGE && state != State.CHECK) {
            state = State.CHECK;
        }
        if (pillar != null && pillar.phase == Scaffold.Phase.WALK && (spot == null || pillar.strays && strays.isEmpty())) {
            pillar = null; // not climbed yet: nothing to come down from
        } else if (pillar != null && !pillar.strays && spot == null && pillar.down == null) {
            pillar.down = "lost its tree"; // (never written so) it only comes down
        }
        if (pillar != null) {
            state = State.SCAFFOLD; // on (or by) its pillar: resumed exactly
        } else if (state == State.SCAFFOLD) {
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
