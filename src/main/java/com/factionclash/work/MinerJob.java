package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.Logistics;
import com.factionclash.campaign.MapBounds;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.campaign.WorkplaceState.MineLevel;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.MinePlanner;
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
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
 * is opened. Where it digs is its own choice: a miner without a mine looks for flat, dry ground a little way out
 * from the base ({@link MineSites#evaluate}), walking out to look further when nothing near is fit, and digs its own
 * spiral down from the surface there. Which layer it works is its own choice too ({@link MinePlanner}): the ore the
 * storehouse lacks and its pickaxe can harvest, at the height vanilla generation puts it; it digs the shaft deeper
 * when that layer lies below, lengthens a finished layer, lets the mine rest until a better pickaxe comes, or gives
 * up a worked-out mine.
 */
public class MinerJob extends Job {
    /** SITE: no mine to work, looking for a place to dig one (saved, but loads as CHECK). */
    enum State { CHECK, STORAGE, CRAFT, TRAVEL, MINE, EXTEND, CLEAR, SITE }

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

    /** Miners without a mine of their own work a cave a cave explorer surveyed. */
    private final CaveMinerJob caveJob;

    /**
     * PROPOSED: ticks spent at the same shaft cell, within reach of it (plugging around it and breaking it), before
     * the descent is given up. Walking to it has its own budget ({@link #DESCENT_WALK_TICKS}).
     */
    static final int STUCK_STEP_TICKS = 2400;
    /**
     * PROPOSED: ticks walking toward the shaft cell being dug without getting any closer to it before the descent is
     * given up (the cell cannot be reached). A trip away from the shaft (an errand, a fight) starts the count afresh.
     */
    static final int DESCENT_WALK_TICKS = 1200;
    /** PROPOSED: failed plugs in a row, with nothing to plug with at home either, before the descent is given up. */
    static final int SEAL_FAILURES = 3;
    /** PROPOSED: how often the miner checks that its pickaxe can still harvest the ore of its level. */
    static final int TIER_CHECK_TICKS = 2400;
    /** PROPOSED: ticks the stairs may stay blocked by a block nobody may dig before the mine is given up. */
    static final int STAIRS_BLOCKED_TICKS = 600;
    /**
     * PROPOSED: ticks climbing out of a mine without getting any closer to the way out before plain walking (and
     * digging out) takes over.
     */
    static final int LEAVE_BUDGET_TICKS = 1200;
    /** PROPOSED: experience for the miner per layer reached or opened, and per lengthening. */
    static final int LEVEL_XP = 20;

    /**
     * Shaft watchdog: the mine and shaft cell being dug and for how long, failed plugs in a row, the next pickaxe
     * check.
     */
    private int extendMine = -1, extendStep = -1, extendTicks, sealFailures;
    /** Walking toward the shaft cell: ticks without getting closer, the closest it got, and the last EXTEND tick. */
    private int extendWalkTicks;
    private double extendWalkBest = Double.MAX_VALUE;
    private long extendLast;
    private long nextTierCheck;
    /** Ticks in a row the stairs were blocked by a block nobody may dig. */
    private int stairBlockedTicks;
    /**
     * The mine being climbed out of, for how many ticks without getting any closer to the way out (see
     * {@link #LEAVE_BUDGET_TICKS}), and the closest it got so far.
     */
    private int leaveMineId = -1, leaveTicks;
    private double leaveBest;
    /** The current leg of a long walk along a mine level ({@link #tunnelLeg}), the destination it is for, and until when it holds. */
    private BlockPos leg, legFor;
    private long legUntil;
    /** PROPOSED: a walk along a mine level longer than this goes in legs along its dug tunnels. */
    static final int TUNNEL_LEG = 24;

    /**
     * Why a miner without a mine is idle, in words that follow "has had" (the faction hears "... has had no place to
     * dig a mine for half a day and becomes ..."); the status line says the same as a sentence.
     */
    public static final String NO_SITE = "no place to dig a mine", TOO_SHALLOW = "no ground deep enough for a mine",
            NO_FREE_SITE = "no free mine site (the base works as many mines as it may)";

    /** PROPOSED: how often a miner that found no mine site looks again. */
    static final int SITE_RETRY_TICKS = 6000;
    /** PROPOSED: survey walks out from the base when nothing near is fit, how far out (each way), and how long each may take. */
    static final int SURVEY_POINTS = 4, SURVEY_OFFSET = 44, SURVEY_TICKS = 2400, SURVEY_MAP_MARGIN = 16;
    /** PROPOSED: walking ticks after which a new mine whose mouth the miner cannot reach is given up. */
    public static final int UNREACHABLE_SITE_TICKS = 2400;
    /** PROPOSED: mine sites the miner gave up (unreachable, unsafe) that it remembers and never picks again. */
    static final int MAX_BAD_SITES = 32;
    /** A gap between two walking ticks (an errand, a wait) counts as at most this many ticks. */
    private static final int WALK_GAP = 101;
    /** A miner waiting to look for a site again waits within this many blocks of the walls (at the storehouse otherwise). */
    private static final int HOME_MARGIN = 16;

    /** The site search under way (not saved: it starts again after a reload), and the survey walk (-1: none). */
    private MineSites.Search siteSearch;
    private int surveyIndex = -1;
    private long surveyUntil;
    /** When the miner may look for a site again after finding none (saved), and why it found none. */
    private long nextSiteSearch;
    private String siteFailReason = NO_SITE;
    /** Mouths of mines this miner gave up as unreachable or unsafe, oldest first (saved). */
    private final List<Long> badSites = new ArrayList<>();
    /** Walking ticks toward the mouth of a new mine that was never reached, and the last tick counted. */
    private int siteWalkMine = -1, siteWalkTicks;
    private long siteWalkLast;
    /** With a full storehouse, the next time a miner without a mine tries to unload. */
    private long nextSiteUnload;
    /** After a trip for a resting mine's pickaxe that brought none, when the miner may try again. */
    private long nextRestFetch;

    /**
     * Sharing a mine: the mine this miner helps in as its second miner (-1: none; the relation itself is saved in the
     * mine's record, {@link WorkplaceState#helper}), and a mine it must climb out of before anything else, once its
     * help there is over (-1: none).
     */
    private int helpingId = -1, leaveFirst = -1;
    /** True while this tick's work is a helper's: its level is the mine's helper level, and the shaft is not its own. */
    private boolean helping;
    /** Since when the miner, helping, has had no mine or cave of its own (-1: it has one). Not saved. */
    private long spareSince = -1;
    /** After a help that went wrong (the stairs blocked), when the miner may help in a mine again. */
    private long nextHelp;
    /** The holder waits at the storehouse while only its helper's level has work left ({@link MinePlanner.Kind#WAIT}). */
    private boolean waitingForHelper;
    /** Why a holder waiting for its helper is idle, in words that follow "has had". */
    public static final String ONLY_HELPERS_LEVEL = "no level of its own left in its mine (only its helper's)";
    /** Why a helper with no mine of its own is a spare hand the base may retrain, in words that follow "has had". */
    public static final String NO_MINE_OF_ITS_OWN = "no mine of its own (only another miner's to help in)";

    public MinerJob(FactionNpc npc) {
        super(npc);
        caveJob = new CaveMinerJob(npc);
    }

    /**
     * The mine this miner works: its own one while it is active, else a free one that is active, or resting until a
     * pickaxe no better than the one in hand. Taking up a resting mine ends its rest.
     */
    private WorkplaceState wp() {
        int tier = pickTier();
        WorkplaceState w = Workplaces.claim(npc, WorkplaceState.Type.MINE, m -> !m.workedOut && (m.restTier == 0 || m.restTier <= tier));
        if (w != null && w.restTier != 0) {
            w.restTier = 0;
            data().setDirty();
        }
        return w;
    }

    /**
     * The surveyed cave this miner works, if any: its own one; otherwise, when it has no active mine and none can be
     * claimed, a cave with marked ore that nobody works. A miner whose mine is worked out therefore works a cave
     * before anything else.
     */
    private WorkplaceState caveWork() {
        WorkplaceState cave = Workplaces.held(npc, WorkplaceState.Type.CAVE);
        if (cave != null) {
            // a miner works one place at a time: a mine it still holds (an old save's miner took up a cave without
            // handing its worked-out hut mine back, and the mine came back to life when the save was migrated) goes
            // back to the base, for another miner to take up
            WorkplaceState mine = Workplaces.held(npc, WorkplaceState.Type.MINE);
            if (mine != null) {
                Workplaces.release(data(), mine);
            }
            return cave;
        }
        WorkplaceState mine = Workplaces.held(npc, WorkplaceState.Type.MINE);
        if (mine != null && mine.active()) {
            return null;
        }
        if (wp() != null) {
            return null;
        }
        return Workplaces.claim(npc, WorkplaceState.Type.CAVE, w -> !w.workedOut && !w.marks.isEmpty());
    }

    @Override
    public String workplaceInfo() {
        if (Workplaces.held(npc, WorkplaceState.Type.CAVE) != null) {
            return caveJob.workplaceInfo();
        }
        WorkplaceState wp = Workplaces.held(npc, WorkplaceState.Type.MINE); // looking never claims
        WorkplaceState helped = wp == null || !wp.active() ? Workplaces.helped(npc) : null;
        if (helped != null && helped.helperLevel() != null) {
            MineLevel lv = helped.helperLevel();
            var holder = helped.assigned == null ? null : data().record(helped.assigned);
            return "Helping in mine #" + helped.id + " (its miner: " + (holder == null ? "none" : holder.name) + "), level y="
                    + (helped.origin.getY() - lv.step) + " for " + bandOf(helped, lv).label + ", branch mine " + lv.progress + "/"
                    + MineLayout.tunnelPlan(lv).size();
        }
        if (wp == null) {
            return siteSearch != null || surveyIndex >= 0 ? "no mine yet - looking for a place to dig one" : "no mine";
        }
        int y0 = wp.origin.getY();
        MineLevel lv = MineLayout.current(wp);
        StringBuilder sb = new StringBuilder("Mine #" + wp.id + " (" + wp.levels.size() + " levels, shaft to y=" + (y0 - wp.bottom) + ")");
        if (lv == null) {
            return sb.append(", next level not chosen yet").toString();
        }
        sb.append(", level y=").append(y0 - lv.step).append(" for ").append(bandOf(wp, lv).label);
        if (wp.upgrading()) {
            return sb.append(", digging down to it (step ").append(wp.upgradeStep).append(")").toString();
        }
        sb.append(", branch mine ").append(lv.progress).append("/").append(MineLayout.tunnelPlan(lv).size());
        if (lv.rounds > 0) {
            sb.append(", lengthened ").append(lv.rounds).append("x");
        }
        return sb.toString();
    }

    /** Watchdog for the current plan cell: which one, and how long the miner has been at it. */
    private int faceProgress = -1, faceTicks;
    private MineLevel faceLevel;
    /** PROPOSED: ticks spent on one plan cell (vein mining excluded) before it is given up as unreachable (or
     *  twice as long while the miner is still more than 10 blocks from it). */
    static final int FACE_GIVE_UP_TICKS = 1200;

    private int pickTier() {
        return ToolKind.PICKAXE.matches(npc.tool()) ? ToolKind.tierOf(npc.tool()) : -1;
    }

    /** The band a level was opened for; for a level of an old mine, the band whose height it is nearest to. */
    private MinePlanner.Band bandOf(WorkplaceState wp, MineLevel lv) {
        return MineSites.bandOf(wp, lv, level().getMinBuildHeight());
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

    private int collect(WorkplaceState wp) {
        int n = BlockWork.pickUp(npc, this::wanted);
        if (n > 0) {
            wp.totalOutput += n;
            data().setDirty();
        }
        return n;
    }

    @Override
    protected void run() {
        BaseState b = base();
        if (b == null) {
            status("No home base", "Idle");
            markIdle("no home base");
            waitFor(200);
            return;
        }
        takeOverHelpedMine();
        WorkplaceState cave = caveWork();
        WorkplaceState own = cave == null ? wp() : null;
        WorkplaceState helped = settleHelp(own, cave);
        helping = helped != null;
        if (leaveFirst >= 0 && !climbedOutFirst(own)) {
            return;
        }
        if (cave != null) {
            spareSince = -1;
            markBusy();
            caveJob.tick();
            return;
        }
        WorkplaceState wp = own != null ? own : helped;
        if (wp == null) {
            site(b);
            return;
        }
        if (own != null) {
            spareSince = -1;
        } else if (spareSince < 0) {
            spareSince = now();
        }
        siteSearch = null;
        surveyIndex = -1;
        if (!waitingForHelper || helping) {
            waitingForHelper = false;
            markBusy();
        }
        collect(wp);
        switch (state) {
            case CHECK, SITE -> state = pickTier() < 0 || Inv.freeSlots(npc.inventory()) <= 1 || workLevel(wp) == null
                    ? State.STORAGE : State.TRAVEL;
            case STORAGE -> storage(wp);
            case CRAFT -> craftTorches(wp);
            case TRAVEL -> travel(wp);
            case MINE -> mine(wp);
            case EXTEND -> {
                if (helping) {
                    state = State.TRAVEL; // (the shaft is the holder's to dig)
                } else {
                    extend(wp);
                }
            }
            case CLEAR -> clear(wp);
        }
    }

    /** The block this miner is breaking right now, or null (for GameTests). */
    public BlockPos digTarget() {
        return breaker.target();
    }

    /** The level this miner works in {@code wp}: the helper's level while it helps, else the mine's current level. */
    private MineLevel workLevel(WorkplaceState wp) {
        return helping ? wp.helperLevel() : MineLayout.current(wp);
    }

    // ------------------------------------------------------------------ sharing a mine

    /**
     * True while this miner is a spare hand: it has helped in another miner's mine, with no mine or cave of its own, for
     * at least {@code ticks}. The base may then retrain it for a role it needs, as it would an idle miner (before mines
     * could be shared, such a miner stood idle).
     */
    @Override
    public boolean spareFor(long ticks) {
        return spareSince >= 0 && now() - spareSince >= ticks;
    }

    @Override
    public String spareReason() {
        return NO_MINE_OF_ITS_OWN;
    }

    /**
     * The mine this miner helps in has lost its miner (dead, retrained, or released it): the helper takes it over, as
     * its holder, and goes on with the level it was working (unless the shaft was being dug down, which it then
     * finishes first). It takes it up before any other free mine.
     */
    private void takeOverHelpedMine() {
        WorkplaceState h = Workplaces.helped(npc);
        if (h == null || !h.active() || Workplaces.holderValid(data(), h)) {
            return;
        }
        WorkplaceState own = Workplaces.held(npc, WorkplaceState.Type.MINE);
        if (own != null && own != h && own.active()) {
            return; // (a mine of its own already: the help ends below)
        }
        MineLevel lv = h.helperLevel();
        h.assigned = npc.getUUID();
        Workplaces.releaseHelper(data(), h);
        if (lv != null && !h.upgrading()) {
            h.cur = h.levels.indexOf(lv);
        }
        data().setDirty();
        helpingId = -1;
        state = State.CHECK;
        Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Mine #" + h.id + ": " + npc.npcName()
                + " takes the mine over"));
    }

    /**
     * Whether the help this miner gives in a mine goes on; returns that mine, or null. The help ends when the miner
     * has a mine or cave of its own, or {@link #helpOver} says so; then (and when the mine's record was released
     * elsewhere: the mine set aside or resting) the miner climbs out of that mine first ({@link #leaveFirst}), unless
     * it is now its own.
     */
    private WorkplaceState settleHelp(WorkplaceState own, WorkplaceState cave) {
        WorkplaceState h = Workplaces.helped(npc);
        if (h != null) {
            if (own == null && cave == null && helpOver(h) == null) {
                helpingId = h.id;
                return h;
            }
            Workplaces.releaseHelper(data(), h);
            if (helpingId < 0) {
                helpingId = h.id; // (after a reload)
            }
        }
        if (helpingId >= 0) {
            if (own == null || own.id != helpingId) {
                leaveFirst = helpingId;
            }
            helpingId = -1;
            state = State.CHECK;
            breaker.reset(npc);
            vein.clear();
            blocked.clear();
            npc.navigator().reset();
        }
        return null;
    }

    /**
     * Why the help in mine {@code h} is over, or null while it goes on: the mine is set aside or rests, its miner is
     * gone, the helper's level is gone, done or cut, the holder now works it (or one too close to it), or the
     * pickaxe in hand cannot harvest its ore.
     */
    private String helpOver(WorkplaceState h) {
        if (!h.active()) {
            return "the mine is set aside";
        }
        if (!Workplaces.holderValid(data(), h)) {
            return "the mine has no miner";
        }
        MineLevel lv = h.helperLevel();
        if (lv == null || lv.step > h.bottom) {
            return "its level is gone";
        }
        MineLevel cur = MineLayout.current(h);
        if (cur != null && Math.abs(cur.step - lv.step) < MinePlanner.HELPER_CLEARANCE) {
            return "the mine's miner works there";
        }
        if (lv.cut || lv.progress >= MineLayout.tunnelPlan(lv).size()) {
            return "its level is done";
        }
        int tier = pickTier();
        if (tier >= 0 && bandOf(h, lv).pickTier > tier) {
            return "its pickaxe is too weak for the level"; // (a better one broke and a spare took its place)
        }
        return null;
    }

    /** Climbs out of the mine whose help is over before doing anything else; true once out (or it is the miner's own mine now). */
    private boolean climbedOutFirst(WorkplaceState own) {
        WorkplaceState old = data().workplace(leaveFirst);
        if (old == null || old == own || old.type != WorkplaceState.Type.MINE || leaveMine(old)) {
            leaveFirst = -1;
            return true;
        }
        markBusy();
        return false;
    }

    /**
     * Instead of standing idle (every mine site of the base taken, or no site to be found), the miner helps in an
     * open mine of the base: one with a miner of its own working a level (not between two, nor waiting) and no helper
     * yet, whose stairs are dug, where {@link MinePlanner#forHelper} finds a level for it (never the holder's, never
     * below the stairs). Of several
     * mines, the one with the most wanted ore comes first, then the nearest. The level is taken at once: lengthened,
     * or opened as a new branch mine where the stairs pass. True when the miner now helps.
     */
    private boolean joinAsHelper(BaseState b) {
        int tier = pickTier();
        if (tier < 0 || now() < nextHelp || Workplaces.held(npc, WorkplaceState.Type.CAVE) != null) {
            return false;
        }
        int minY = level().getMinBuildHeight();
        List<MinePlanner.Band> order = null;
        WorkplaceState best = null;
        MinePlanner.Decision bestD = null;
        int bestRank = Integer.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;
        for (int id : b.workplaces) {
            WorkplaceState w = data().workplace(id);
            // (a holder with no level planned is about to choose one, or waits for its helper: it chooses first)
            if (w == null || w.type != WorkplaceState.Type.MINE || !w.active() || w.bottom < 1 || MineLayout.current(w) == null
                    || npc.getUUID().equals(w.assigned) || !Workplaces.holderValid(data(), w) || Workplaces.helperValid(data(), w)
                    || badSites.contains(w.origin.asLong())) {
                continue;
            }
            if (order == null) {
                order = MinePlanner.order(MinePlanner.wanted(stock(b)));
            }
            MinePlanner.Decision d = MinePlanner.forHelper(w.origin.getY(), minY, plannerLevels(w), w.cur, w.bottom, tier, order, w.maxRounds);
            if (d == null) {
                continue;
            }
            int rank = d.band() == null ? order.size() : order.indexOf(d.band());
            double dist = npc.blockPosition().distSqr(w.origin);
            if (rank < bestRank || (rank == bestRank && dist < bestDist)) {
                best = w;
                bestD = d;
                bestRank = rank;
                bestDist = dist;
            }
        }
        if (best == null) {
            return false;
        }
        WorkplaceState w = best;
        int y0 = w.origin.getY();
        MineLevel lv;
        String what;
        switch (bestD.kind()) {
            case WORK -> {
                lv = w.levels.get(bestD.level());
                what = "works its y=" + (y0 - lv.step) + " branch mine";
            }
            case LENGTHEN -> {
                lv = w.levels.get(bestD.level());
                if (!MineSites.corridorClear(level(), data(), w, lv.rounds + 1)) {
                    // something now lies where the next stretch would go: the corridor ends at this length (the next
                    // look finds the mine's other levels)
                    w.maxRounds = lv.rounds;
                    data().setDirty();
                    return false;
                }
                lv.rounds++;
                what = "lengthens its y=" + (y0 - lv.step) + " branch mine (round " + lv.rounds + ")";
            }
            case NEW_LAYER -> {
                lv = new MineLevel(bestD.step(), 0, 0, bestD.band().ordinal());
                w.levels.add(lv);
                what = "opens a branch mine at y=" + (y0 - lv.step);
            }
            default -> {
                return false;
            }
        }
        MinePlanner.Band have = bandOf(w, lv);
        if (bestD.band() != null && bestD.band() != have && have.pickTier > bestD.band().pickTier) {
            lv.band = bestD.band().ordinal(); // (as the holder's choice does: worked for the ore of the weaker pickaxe)
        }
        w.helper = npc.getUUID();
        w.helperStep = lv.step;
        data().setDirty();
        helpingId = w.id;
        helping = true;
        siteSearch = null;
        surveyIndex = -1;
        if (spareSince < 0) {
            spareSince = now();
        }
        markBusy();
        npc.awardXp(bestD.kind() == MinePlanner.Kind.WORK ? 0 : LEVEL_XP);
        var holder = data().record(w.assigned);
        Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Mine #" + w.id + ": " + npc.npcName() + " helps "
                + (holder == null ? "its miner" : holder.name) + " and " + what + " for " + bandOf(w, lv).label));
        boolean stocked = Inv.count(npc.inventory(), TORCH) >= 8 && Inv.count(npc.inventory(), SEAL) >= 16;
        state = stocked ? State.TRAVEL : State.STORAGE;
        return true;
    }

    /** The mine's levels as the planner sees them. */
    private List<MinePlanner.Level> plannerLevels(WorkplaceState wp) {
        List<MinePlanner.Level> levels = new ArrayList<>();
        for (MineLevel lv : wp.levels) {
            levels.add(new MinePlanner.Level(lv.step, lv.progress, lv.rounds, lv.cut, bandOf(wp, lv)));
        }
        return levels;
    }

    /**
     * True if {@code p} lies in the layer of the level the other miner of the mine works (the helper's for the holder,
     * the holder's for the helper): its two tunnel rows and the floor and roof around them, out from the shaft. An ore
     * vein is never followed there, so the two never dig the same cells (nor each other's floor).
     */
    private boolean othersLayer(WorkplaceState wp, BlockPos p) {
        if (wp == null) {
            return false;
        }
        MineLevel other = helping ? MineLayout.current(wp) : Workplaces.helperValid(data(), wp) ? wp.helperLevel() : null;
        if (other == null) {
            return false;
        }
        int y = wp.origin.getY() - other.step;
        return p.getY() >= y - 1 && p.getY() <= y + 2 && MineLayout.forwardOf(wp, p) >= SpiralMine.MAIN_START - 1;
    }

    /**
     * No mine and no cave to work: the miner finds a place to dig its own. First out of the mine it was in (one just
     * given up, say) and the last load home, and a better pickaxe fetched when a mine of the base rests for one.
     * Then, unless the base already works as many mines as it may, it looks for a site around the base
     * ({@link MineSites.Search}); when nothing near is fit it walks out to up to {@link #SURVEY_POINTS} points to look
     * from there (its own chunks load around it). A site found becomes its mine at once; with none, it is idle and
     * looks again after {@link #SITE_RETRY_TICKS}.
     */
    private void site(BaseState b) {
        breaker.reset(npc);
        state = State.SITE;
        WorkplaceState in = MineSites.mineAt(level(), data(), b, npc.blockPosition());
        if (in != null && !leaveMine(in)) {
            return;
        }
        if (hasCargo() && now() >= nextSiteUnload) {
            if (!atStorage()) {
                return;
            }
            if (unloadNow() == Unload.PARTIAL_FULL) {
                // the storehouse is full: what does not fit stays in the backpack, and the miner carries on
                nextSiteUnload = now() + 1200;
            }
        }
        // a mine of the base rests until a better pickaxe and the storehouse has one: fetch it, and the next tick
        // takes the mine up again (before any new site is looked for)
        // (only a pickaxe the miner would really take counts: one it rates no better than its own, such as a Silk
        // Touch one, stays where it is, and the miner would otherwise be held at the storehouse tick after tick)
        int need = restingMineNeed(b);
        int own = pickTier() < 0 ? -1 : gearScore(npc.tool(), pickTier());
        if (need >= 0 && now() >= nextRestFetch && FactionStorage.count(level(), b, s -> ToolKind.PICKAXE.matches(s)
                && ToolKind.tierOf(s) >= need && gearScore(s, ToolKind.tierOf(s)) > own) > 0) {
            status("Fetching " + MineSites.pickName(need) + " to take up a resting mine", "Travelling");
            if (!atStorage()) {
                return;
            }
            upgradeEquipmentNow();
            if (pickTier() >= need) {
                return;
            }
            nextRestFetch = now() + SITE_RETRY_TICKS / 5; // it was not to be had after all: not again for a while
        }
        if (MineSites.openMines(data(), b) >= FCConfig.MAX_OPEN_MINES.get()) {
            siteSearch = null;
            surveyIndex = -1;
            if (joinAsHelper(b)) {
                return; // a level of another miner's mine instead of standing idle
            }
            markIdle(NO_FREE_SITE);
            status("Every mine site of the base is taken", "Idle");
            waitFor(200);
            return;
        }
        if (now() < nextSiteSearch) {
            if (joinAsHelper(b)) {
                return; // no site of its own to be found for now: it helps in a mine of the base meanwhile
            }
            markIdle(siteFailReason);
            // it waits at home, not out where it last looked (a survey point far outside the walls)
            if (!BaseZones.insideWalls(b, npc.blockPosition(), HOME_MARGIN) && !atStorage()) {
                return;
            }
            status(capitalised(siteFailReason) + " - looking again later", "Idle");
            waitFor(200);
            return;
        }
        if (pickTier() < 0) {
            // no pickaxe: the storehouse trip first, as for any mine
            status("No pickaxe - going to the storehouse", "Travelling");
            if (!atStorage()) {
                return;
            }
            upgradeEquipment();
            if (pickTier() < 0) {
                status("No pickaxe in storage - waiting for the blacksmith", "Waiting");
                waitFor(300);
            }
            return;
        }
        if (siteSearch == null && surveyIndex >= 0) {
            // walking out to look from further away; its own chunks load around it as it goes
            BlockPos target = surveyPoint(b, surveyIndex);
            if (!goTo(target, 8.0, "a place to look for a mine site") && now() < surveyUntil) {
                return;
            }
            npc.navigator().reset();
        }
        if (siteSearch == null) {
            siteSearch = new MineSites.Search(b, badSites);
        }
        status("Looking for a place to dig a mine", "Surveying");
        if (!siteSearch.step(level(), data(), b, MineSites.PER_TICK)) {
            return;
        }
        MineSites.Search done = siteSearch;
        siteSearch = null;
        MineSites.Site found = done.best();
        if (found != null && MineSites.evaluate(level(), data(), b, found.origin().getX(), found.origin().getZ(), badSites) == null) {
            // the site was found a few ticks ago; meanwhile another miner opened a mine near it (or the ground there
            // changed): look again from scratch
            return;
        }
        if (found != null && MineSites.openMines(data(), b) >= FCConfig.MAX_OPEN_MINES.get()) {
            // another miner of the base opened a mine during the search and took the last place: the next tick
            // finds every mine site taken
            return;
        }
        if (found != null) {
            surveyIndex = -1;
            WorkplaceState w = MineSites.create(level(), data(), b, npc, found);
            markBusy();
            plan(w);
            // with too few torches or blocks to plug water, the storehouse first (the plan is made: no second one)
            boolean stocked = Inv.count(npc.inventory(), TORCH) >= 8 && Inv.count(npc.inventory(), SEAL) >= 16;
            state = !w.active() || !npc.getUUID().equals(w.assigned) ? State.CHECK : stocked ? State.TRAVEL : State.STORAGE;
            return;
        }
        if (done.allTooShallow()) {
            siteFailed(TOO_SHALLOW);
            return;
        }
        if (BaseZones.forageAllowed(b) && surveyIndex + 1 < SURVEY_POINTS) {
            surveyIndex++;
            surveyUntil = now() + SURVEY_TICKS;
            return;
        }
        siteFailed(NO_SITE);
    }

    /** No site anywhere the miner looked: idle, and it looks again after {@link #SITE_RETRY_TICKS}. */
    private void siteFailed(String reason) {
        surveyIndex = -1;
        siteSearch = null;
        nextSiteSearch = now() + SITE_RETRY_TICKS;
        siteFailReason = reason;
        markIdle(reason);
        status(capitalised(reason), "Idle");
        waitFor(200);
    }

    private static String capitalised(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Survey point {@code i} (0..3): {@link #SURVEY_OFFSET} blocks out from the base centre diagonally (north-east,
     * south-east, south-west, north-west), kept on the map, at ground height when that is loaded.
     */
    private BlockPos surveyPoint(BaseState b, int i) {
        int[][] d = {{1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
        BlockPos p = MapBounds.clamp(data(), b.center.offset(d[i][0] * SURVEY_OFFSET, 0, d[i][1] * SURVEY_OFFSET), SURVEY_MAP_MARGIN);
        if (level().hasChunk(p.getX() >> 4, p.getZ() >> 4)) {
            return new BlockPos(p.getX(), level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), p.getZ());
        }
        return p;
    }

    /** Remembers a mine site this miner gave up, so it never picks it (or ground within 8 of it) again. */
    private void addBadSite(BlockPos p) {
        long k = p.asLong();
        badSites.remove(Long.valueOf(k));
        badSites.add(k);
        while (badSites.size() > MAX_BAD_SITES) {
            badSites.remove(0);
        }
    }

    /**
     * The pickaxe tier the most easily unlocked resting mine of the base waits for, when the pickaxe in hand is too
     * weak for it; -1 when no mine rests beyond this miner's pickaxe.
     */
    private int restingMineNeed(BaseState b) {
        int tier = pickTier(), need = Integer.MAX_VALUE;
        for (int id : b.workplaces) {
            WorkplaceState w = data().workplace(id);
            if (w != null && w.type == WorkplaceState.Type.MINE && !w.workedOut && w.restTier > tier) {
                need = Math.min(need, w.restTier);
            }
        }
        return need == Integer.MAX_VALUE ? -1 : need;
    }

    /** A survival errand (food, a new pickaxe) from inside a mine climbs out the way the miner came in first. */
    @Override
    protected boolean beforeErrand() {
        BaseState b = base();
        if (b == null) {
            return true;
        }
        WorkplaceState w = MineSites.mineAt(level(), data(), b, npc.blockPosition());
        return w == null || leaveMine(w);
    }

    // ------------------------------------------------------------------ moving through the mine

    /**
     * Walks to {@code dest} through the mine in short legs: to the mouth of the shaft first when coming from the
     * surface, along a tunnel to its landing, up or down the spiral six steps at a time, then to the destination.
     * {@code destStep} is the spiral step of the destination's level (0 = surface). Returns true once at {@code dest}.
     */
    private boolean walkInMine(WorkplaceState wp, BlockPos dest, int destStep, double reach, String label) {
        BlockPos p = npc.blockPosition();
        // swimming (a flooded cell or a lake above the tunnels): only a real tunnel position counts, otherwise the
        // way back is through the mine entrance
        MineLevel lv = npc.isInWater() ? MineLayout.tunnelLevelAt(wp, p) : MineLayout.nearLevelAt(wp, p);
        int cur;
        if (lv != null) {
            int lk = lv.step;
            if (lk == destStep) {
                return walkLevel(wp, lv, dest, reach, label);
            }
            BlockPos landing = MineLayout.stand(wp, lk);
            if (!NpcNavigator.within(npc, landing, 1.2)) {
                walkLevel(wp, lv, landing, 1.2, "the mine shaft");
                return false;
            }
            cur = lk;
        } else {
            cur = MineLayout.stepAt(wp, p);
            int depth = wp.origin.getY() - p.getY();
            double hx = p.getX() - wp.origin.getX(), hz = p.getZ() - wp.origin.getZ();
            // on the surface (level with the mouth, or lower ground with nothing above it but sky or leaves), or well
            // away from the mine under some roof (a settlement's gate, a house): to the mouth of the shaft first. (Under
            // the gate it used to aim at a stair deep in the shaft instead, from one step to the next, and back.)
            boolean surface = cur == 0 && (depth <= 1 || !MineSites.underground(level(), p)
                    || hx * hx + hz * hz >= 14 * 14 && !MineLayout.inMine(wp, p));
            if (surface && destStep > 0 && !NpcNavigator.within(npc, MineLayout.stand(wp, 0), 3.0)) {
                // to the mouth of the shaft first, then down the stairs
                goTo(MineLayout.stand(wp, 0), 1.2, "the mine entrance");
                return false;
            }
            if (!surface && cur == 0 && depth > 3 && hx * hx + hz * hz < 14 * 14) {
                // underground beside the shaft (a side pocket, a cave): first get onto the stair at this height
                int k = Math.min(depth, Math.max(1, MineLayout.shaftSteps(wp)));
                if (!goTo(MineLayout.stand(wp, k), 1.2, "the mine stairs")) {
                    return false;
                }
                cur = k;
            }
        }
        if (cur == destStep || (destStep == 0 && cur == 0)) {
            // on the landing of the destination's level (or the surface): along its tunnels from here
            return walkLevel(wp, destStep > 0 ? levelAtStep(wp, destStep) : null, dest, reach, label);
        }
        int next = cur < destStep ? Math.min(cur + 6, destStep) : Math.max(cur - 6, destStep);
        // the next stair must be open: a cave-in, falling sand or a block plugged against water fills it, and a
        // player clears it before walking on (digging from where it stands)
        int dir = destStep > cur ? 1 : -1;
        boolean refused = false;
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
                BlockState qs = level().getBlockState(q);
                if (passable(qs)) {
                    continue;
                }
                if (!mayDig(wp, q)) {
                    // a block nobody may dig is left as it is; if it is in the way, the stairs are blocked
                    refused |= !qs.getCollisionShape(level(), q).isEmpty();
                    continue;
                }
                if (!BlockWork.inReach(npc, q, 4.5)) {
                    // walk down (or up) to the stair just before the blocked one, then clear it from there
                    goTo(MineLayout.stand(wp, k - dir), 0.45, label);
                    return false;
                }
                status("Clearing the mine stairs (mine #" + wp.id + ")", "Digging");
                if (!preSeal(wp, q, MineLayout.shaftSteps(wp))) {
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
        if (!refused) {
            stairBlockedTicks = 0;
        } else if (++stairBlockedTicks > STAIRS_BLOCKED_TICKS) {
            // the stairs stay blocked by something nobody may dig: the mine is given up, and the miner finds its
            // own way out (plain walking, or digging out). Only the mine's own miner gives it up; one just passing
            // through (climbing out of a mine it no longer works) only stops using the stairs.
            stairBlockedTicks = 0;
            leaveMineId = wp.id;
            leaveTicks = LEAVE_BUDGET_TICKS + 1;
            leaveBest = Double.NEGATIVE_INFINITY;
            if (npc.getUUID().equals(wp.assigned)) {
                addBadSite(wp.origin);
                MineSites.giveUp(level(), data(), wp, "its stairs are blocked");
            } else if (npc.getUUID().equals(wp.helper)) {
                // a helper only stops helping there (the next tick climbs out), and helps nowhere for a while
                Workplaces.releaseHelper(data(), wp);
                nextHelp = now() + SITE_RETRY_TICKS;
            }
            state = State.CHECK;
            return false;
        }
        // spiral steps are diagonal neighbours: arrive precisely, or the next leg would be "reached" without moving
        goTo(MineLayout.stand(wp, next), 0.45, label);
        return false;
    }

    /** Walks to {@code dest} on level {@code lv} (if any): in stretches along its dug tunnels while it is far. */
    private boolean walkLevel(WorkplaceState wp, MineLevel lv, BlockPos dest, double reach, String label) {
        BlockPos next = lv == null ? null : tunnelLeg(wp, lv, dest);
        if (next != null) {
            goTo(next, 1.5, label);
            return false;
        }
        return goTo(dest, reach, label);
    }

    private static MineLevel levelAtStep(WorkplaceState wp, int step) {
        for (MineLevel l : wp.levels) {
            if (l.step == step) {
                return l;
            }
        }
        return null;
    }

    /**
     * The next stretch of a long walk along a mine level (out to a tunnel face 70 blocks from the shaft, or back): the
     * dug tunnel cell within {@link #TUNNEL_LEG} blocks of the miner that lies nearest the destination. Vanilla path
     * searches reach about 48 blocks and price every cell beside water (an aquifer the tunnels were sealed against)
     * highly, so a search toward a farther point found a partial path up the stairs and over the surface: the miner
     * stepped onto the stairs, turned back to the landing, and paced there. Null when the destination is near
     * enough to walk to directly, or no dug cell brings the miner closer.
     */
    private BlockPos tunnelLeg(WorkplaceState wp, MineLevel lv, BlockPos dest) {
        BlockPos p = npc.blockPosition();
        double here = p.distSqr(dest);
        if (here <= TUNNEL_LEG * TUNNEL_LEG) {
            return null;
        }
        if (dest.equals(legFor) && now() < legUntil && (leg == null || !NpcNavigator.within(npc, leg, 2.0))) {
            return leg; // (looked for at most every 10 seconds, or when the stretch is walked)
        }
        List<SpiralMine.Cell> plan = MineLayout.tunnelPlan(lv);
        BlockPos best = MineLayout.landing(wp, lv);
        double bestD = best.distSqr(p) <= TUNNEL_LEG * TUNNEL_LEG ? best.distSqr(dest) : Double.MAX_VALUE;
        for (int j = Math.min(lv.progress, plan.size()) - 1; j >= 0; j--) {
            SpiralMine.Cell c = plan.get(j);
            if (c.torch() || c.dy() != 0) {
                continue;
            }
            BlockPos q = MineLayout.tunnelPos(wp, lv, c);
            double d = q.distSqr(dest);
            if (d >= bestD || q.distSqr(p) > TUNNEL_LEG * TUNNEL_LEG) {
                continue;
            }
            BlockState s = level().getBlockState(q);
            if ((s.isAir() || s.is(Blocks.TORCH)) && level().getBlockState(q.above()).isAir()
                    && !level().getBlockState(q.below()).getCollisionShape(level(), q.below()).isEmpty()) {
                best = q;
                bestD = d;
            }
        }
        // a stretch is worth it only when it brings the miner well closer (4 blocks or more)
        leg = bestD < Double.MAX_VALUE && Math.sqrt(bestD) <= Math.sqrt(here) - 4 ? best : null;
        legFor = dest.immutable();
        legUntil = now() + 200;
        return leg;
    }

    /**
     * Climbs out of the mine (through the hut door of an old hut mine). True once out, or when the climb has gone
     * {@link #LEAVE_BUDGET_TICKS} without getting any closer to the way out: then plain walking takes over, and
     * digging out if it must.
     */
    private boolean leaveMine(WorkplaceState wp) {
        BlockPos p = npc.blockPosition();
        if (!MineSites.inside(level(), wp, p)) {
            if (leaveMineId == wp.id) {
                leaveMineId = -1;
                leaveTicks = 0;
            }
            return true;
        }
        // how far from the way out: along the tunnels back to the shaft, and up the stairs
        double left = Math.sqrt(p.distToCenterSqr(wp.origin.getX() + 0.5, p.getY() + 0.5, wp.origin.getZ() + 0.5))
                + 2.0 * Math.max(0, wp.origin.getY() - p.getY());
        if (leaveMineId != wp.id) {
            leaveMineId = wp.id;
            leaveTicks = 0;
            leaveBest = left;
        }
        if (left < leaveBest - 0.5) {
            leaveBest = left;
            leaveTicks = 0;
        }
        if (++leaveTicks > LEAVE_BUDGET_TICKS) {
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
        if (!helping && !wp.upgrading()) {
            // (a helper's level was chosen when it joined; the holder plans the mine)
            plan(wp);
            if (!wp.active() || !npc.getUUID().equals(wp.assigned)) {
                state = State.CHECK; // the mine rests or is worked out: on to another mine, a cave, or nothing
                return;
            }
            if (waitingForHelper) {
                var helper = wp.helper == null ? null : data().record(wp.helper);
                markIdle(ONLY_HELPERS_LEVEL);
                status("Waiting for " + (helper == null ? "its helper" : helper.name) + " to finish the last level of mine #" + wp.id, "Waiting");
                waitFor(600);
                return;
            }
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
     * Chooses the level to work next, like a player deciding where to dig ({@link MinePlanner}): the current level
     * until its branch mine is done; then the ore the storehouse lacks most that the pickaxe in hand can harvest,
     * on a level already near that ore's height (lengthened when it is done), or on a new level - dug down to first
     * when it lies below the shaft. When only a better pickaxe would help the mine rests; when nothing it can reach is
     * left it is worked out.
     */
    private void plan(WorkplaceState wp) {
        BaseState b = base();
        int y0 = wp.origin.getY(), minY = level().getMinBuildHeight();
        List<MinePlanner.Band> wanted = MinePlanner.wanted(stock(b));
        waitingForHelper = false;
        if (wp.helper != null && !Workplaces.helperValid(data(), wp)) {
            Workplaces.releaseHelper(data(), wp); // a helper that died or was retrained (its record stayed behind)
        }
        for (int guard = 0; guard < 16; guard++) {
            List<MinePlanner.Level> levels = plannerLevels(wp);
            // a level the mine's helper works is never the holder's choice
            int taken = Workplaces.helperValid(data(), wp) ? wp.levels.indexOf(wp.helperLevel()) : -1;
            MinePlanner.Decision d = MinePlanner.next(y0, minY, levels, wp.cur, wp.bottom, wp.shaftLimit, pickTier(), wanted, wp.maxRounds, taken);
            switch (d.kind()) {
                case WORK -> choose(wp, d);
                case LENGTHEN -> {
                    MineLevel lv = wp.levels.get(d.level());
                    if (!MineSites.corridorClear(level(), data(), wp, lv.rounds + 1)) {
                        // something now lies where the next stretch would go: the corridor ends at this length
                        wp.maxRounds = lv.rounds;
                        data().setDirty();
                        continue;
                    }
                    lv.rounds++;
                    choose(wp, d);
                    npc.awardXp(LEVEL_XP);
                    Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Mine #" + wp.id + ": " + npc.npcName()
                            + " lengthens the y=" + (y0 - lv.step) + " branch mine (round " + lv.rounds + ")"));
                }
                case NEW_LAYER -> {
                    wp.levels.add(new MineLevel(d.step(), 0, 0, d.band().ordinal()));
                    wp.cur = wp.levels.size() - 1;
                    if (d.step() > wp.bottom) {
                        wp.upgradeStep = 0; // dig the shaft down to it first
                    } else {
                        // a height the stairs already pass: the branch mine starts at once
                        npc.awardXp(LEVEL_XP);
                        Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Mine #" + wp.id + ": " + npc.npcName()
                                + " opens a branch mine at y=" + (y0 - d.step()) + " for " + d.band().label));
                    }
                }
                case REST -> {
                    // before the mine is set aside for a better pickaxe: one may be waiting in the storehouse (the
                    // equipment check runs at most once a minute)
                    int had = pickTier();
                    upgradeEquipmentNow();
                    if (pickTier() > had) {
                        continue;
                    }
                    MineSites.rest(level(), data(), wp, d.needTier());
                }
                case WORKED_OUT -> MineSites.retire(level(), data(), wp, MineSites.WORKED_OUT);
                case WAIT -> {
                    // the only work left is on the helper's level: wait at the storehouse until it is done
                    wp.cur = -1;
                    wp.upgradeStep = -1;
                    waitingForHelper = true;
                }
            }
            data().setDirty();
            return;
        }
    }

    /**
     * Makes the decision's level the current one. A level worked for an ore that needs a weaker pickaxe than the one
     * it was opened for (coal just above an iron level, say) is from now on counted as that ore's, so the pickaxe
     * check does not send the miner home for nothing. A level below the shaft is dug down to first.
     */
    private void choose(WorkplaceState wp, MinePlanner.Decision d) {
        wp.cur = d.level();
        MineLevel lv = wp.levels.get(d.level());
        MinePlanner.Band have = bandOf(wp, lv);
        if (d.band() != null && d.band() != have && have.pickTier > d.band().pickTier) {
            lv.band = d.band().ordinal();
        }
        if (lv.step > wp.bottom && !wp.upgrading()) {
            wp.upgradeStep = 0;
        }
    }

    /** What the storehouse holds that matters for choosing a layer. */
    private MinePlanner.Stock stock(BaseState b) {
        var l = level();
        int iron = Logistics.measure(l, b, Logistics.Resource.IRON);
        int diamonds = Logistics.measure(l, b, Logistics.Resource.DIAMOND);
        int fuel = Logistics.measure(l, b, Logistics.Resource.FUEL);
        int gold = FactionStorage.count(l, b, s -> s.is(Items.RAW_GOLD) || s.is(Items.GOLD_INGOT));
        int lapis = FactionStorage.count(l, b, s -> s.is(Items.LAPIS_LAZULI));
        BlockPos table = b.point(BaseState.ENCHANTING);
        boolean hasTable = table != null && l.isLoaded(table) && l.getBlockState(table).is(Blocks.ENCHANTING_TABLE);
        boolean short_ = b.shortages.contains(Logistics.Resource.FUEL.name()) || b.shortages.contains(Logistics.Resource.WOOD.name());
        return new MinePlanner.Stock(iron, diamonds, gold, lapis, fuel, hasTable, short_);
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
        MineLevel lv = workLevel(wp);
        if (lv == null) {
            state = State.STORAGE; // no level chosen yet: the storehouse trip plans one
            return;
        }
        if (!helping && !wp.upgrading() && lv.step > wp.bottom) {
            // a level below the stairs with no descent under way (a damaged record): dig down to it first, rather
            // than walk down stairs that are not there
            wp.upgradeStep = 0;
            data().setDirty();
        }
        if (now() >= nextIntegrityCheck) {
            nextIntegrityCheck = now() + 1200;
            if (findBlocked(wp)) {
                state = State.CLEAR;
                return;
            }
        }
        if (!helping && wp.upgrading()) {
            if (siteUnreachable(wp)) {
                return;
            }
            // already down in the shaft below its bottom (back from a fight, or after a reload, partway down a
            // descent): the descent walks on from here instead of climbing back up to the bottom (the surface, for
            // a new mine) and down again
            if (MineLayout.stepAt(wp, npc.blockPosition()) > wp.bottom
                    || walkInMine(wp, MineLayout.stand(wp, wp.bottom), wp.bottom, 1.2, "mine #" + wp.id)) {
                state = State.EXTEND;
            }
            return;
        }
        if (pickTooWeak(wp, lv)) {
            state = State.STORAGE;
            return;
        }
        List<SpiralMine.Cell> plan = MineLayout.tunnelPlan(lv);
        if (lv.progress >= plan.size()) {
            // the level is done (after a reload, say): the storehouse trip plans the next one, with no walk down first
            state = State.STORAGE;
            return;
        }
        BlockPos dest = approach(wp, lv, plan, lv.progress);
        // already down in this level's tunnels: start working from here (mining handles walking, reach and bridging)
        boolean inLevel = !npc.isInWater() && MineLayout.nearLevelAt(wp, npc.blockPosition()) == lv;
        if (inLevel || walkInMine(wp, dest, lv.step, 1.2, "mine #" + wp.id)) {
            state = State.MINE;
        }
    }

    /**
     * A new mine (nothing dug yet) whose mouth the miner has walked toward for {@link #UNREACHABLE_SITE_TICKS}
     * without getting there is given up and remembered as a bad site. True when that happened. Once the descent has
     * begun (its first cell is done) the mouth has been reached: the bottom only moves when the whole first descent is
     * dug, so a miner climbing out of a half-dug shaft (after a reload, say) is not walking toward an unreachable mouth.
     */
    private boolean siteUnreachable(WorkplaceState wp) {
        if (wp.bottom != 0 || wp.upgradeStep > 0 || NpcNavigator.within(npc, MineLayout.stand(wp, 0), 3.0)) {
            siteWalkMine = -1;
            return false;
        }
        long t = now();
        if (siteWalkMine != wp.id) {
            siteWalkMine = wp.id;
            siteWalkTicks = 0;
        } else {
            siteWalkTicks += (int) Math.min(WALK_GAP, Math.max(0, t - siteWalkLast));
        }
        siteWalkLast = t;
        if (siteWalkTicks <= UNREACHABLE_SITE_TICKS) {
            return false;
        }
        siteWalkMine = -1;
        addBadSite(wp.origin);
        MineSites.giveUp(level(), data(), wp, "its mouth cannot be reached");
        state = State.CHECK;
        return true;
    }

    /**
     * Every {@link #TIER_CHECK_TICKS}: true if the pickaxe in hand cannot harvest the ore of the level (a better one
     * broke and a spare took its place, say). The storehouse trip then fetches a better one, or the planner moves the
     * miner to a level its pickaxe suits.
     */
    private boolean pickTooWeak(WorkplaceState wp, MineLevel lv) {
        if (now() < nextTierCheck) {
            return false;
        }
        nextTierCheck = now() + TIER_CHECK_TICKS;
        return pickTier() < bandOf(wp, lv).pickTier;
    }

    /**
     * Where to stand to dig plan cell i: the already-dug, walkable floor cell nearest to it (e.g. the junction when a
     * new branch starts), else the level's landing.
     */
    private BlockPos approach(WorkplaceState wp, MineLevel lv, List<SpiralMine.Cell> plan, int i) {
        BlockPos target = MineLayout.tunnelPos(wp, lv, plan.get(Math.min(i, plan.size() - 1)));
        BlockPos best = MineLayout.landing(wp, lv);
        double bestD = best.distSqr(target);
        for (int j = Math.min(i, plan.size()) - 1; j >= 0; j--) {
            SpiralMine.Cell c = plan.get(j);
            if (c.torch() || c.dy() != 0) {
                continue;
            }
            BlockPos p = MineLayout.tunnelPos(wp, lv, c);
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

    /**
     * Dug cells that have been refilled (falling gravel or sand, flooding): the stairs, and the current level. Only a
     * refill counts: a cell of plain rock behind the progress mark was never dug (a branch the face watchdog cut short,
     * a skipped vein, an old save's level), and clearing it is not repairing the passage.
     */
    private boolean findBlocked(WorkplaceState wp) {
        blocked.clear();
        // (the stairs are the holder's to keep: a helper clears a stair only when it is in its way, walking)
        for (SpiralMine.Cell c : helping ? List.<SpiralMine.Cell>of() : SpiralMine.spiralPlan(0, wp.bottom)) {
            BlockPos p = MineLayout.spiralPos(wp, c);
            if (!c.torch() && refilled(level().getBlockState(p)) && mayDig(wp, p)) {
                blocked.add(p);
            }
        }
        MineLevel lv = workLevel(wp);
        if (lv != null && lv.step <= wp.bottom) {
            List<SpiralMine.Cell> plan = MineLayout.tunnelPlan(lv);
            for (int i = 0; i < Math.min(lv.progress, plan.size()); i++) {
                SpiralMine.Cell c = plan.get(i);
                BlockPos p = MineLayout.tunnelPos(wp, lv, c);
                if (!c.torch() && refilled(level().getBlockState(p)) && mayDig(wp, p)) {
                    blocked.add(p);
                }
            }
        }
        return !blocked.isEmpty();
    }

    /** What can fill a dug passage again by itself: falling blocks (gravel, sand) and fluids. */
    static boolean refilled(BlockState s) {
        return !passable(s) && (s.getBlock() instanceof FallingBlock || !s.getFluidState().isEmpty());
    }

    private static boolean passable(BlockState s) {
        return s.isAir() || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH);
    }

    /**
     * A cell that is nothing but fluid: water or lava, or a water plant or bubble column standing in water. Breaking
     * it with a pickaxe would take thousands of ticks (fluids are as hard as obsidian is to a bare hand), so it is
     * cleared once plugged around. A waterlogged block is broken as usual; the water it leaves is cleared next.
     */
    static boolean fluidCell(BlockState s) {
        return !s.getFluidState().isEmpty() && !s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED);
    }

    private void clear(WorkplaceState wp) {
        BlockPos cell = blocked.peek();
        if (cell == null) {
            state = State.TRAVEL;
            nextIntegrityCheck = now() + 1200; // the next inspection counts from the end of this one
            return;
        }
        BlockState s = level().getBlockState(cell);
        if (passable(s) || !mayDig(wp, cell)) {
            blocked.poll(); // open again, or filled with a block nobody may dig
            return;
        }
        if (!BlockWork.inReach(npc, cell, 4.5)) {
            MineLevel lv = workLevel(wp);
            int step = MineLayout.stepAt(wp, cell.above(0));
            BlockPos stand;
            int destStep;
            if (lv == null || step > 0 || MineLayout.forwardOf(wp, cell) < SpiralMine.MAIN_START) {
                int k = Math.max(0, Math.min(wp.bottom, wp.origin.getY() - cell.getY() - 1));
                stand = MineLayout.stand(wp, k);
                destStep = k;
            } else {
                stand = MineLayout.landing(wp, lv);
                destStep = lv.step;
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
        if (!preSeal(wp, cell, MineLayout.shaftSteps(wp))) {
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

    private void noticeOres(WorkplaceState wp, BlockPos opened) {
        var mines = com.factionclash.campaign.CampaignData.get(npc.getServer()).workplaces.values();
        for (Direction d : Direction.values()) {
            BlockPos n = opened.relative(d);
            if (mines.stream().anyMatch(w -> w.type == WorkplaceState.Type.MINE && MineLayout.nearStairs(w, n))) {
                continue; // ore in a stair floor or the rock holding the stairs stays: mining it breaks the way down
            }
            if (othersLayer(wp, n)) {
                continue; // the other miner's level: its cells are its own
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
        MineLevel lv = workLevel(wp);
        if (lv == null || (!helping && wp.upgrading())) {
            breaker.reset(npc);
            state = lv == null ? State.STORAGE : State.TRAVEL;
            return;
        }
        if (pickTooWeak(wp, lv)) {
            breaker.reset(npc);
            state = State.STORAGE;
            return;
        }
        int landingStep = lv.step;
        int shaft = MineLayout.shaftSteps(wp);
        // a player mines the ore it just uncovered before moving on
        if (!vein.isEmpty()) {
            BlockPos ore = vein.peek();
            BlockState os = level().getBlockState(ore);
            if (!isOre(os) || !BlockWork.inReach(npc, ore, 5.0) || !mayDig(wp, ore)) {
                vein.poll();
                return;
            }
            if (!preSeal(wp, ore, shaft)) {
                vein.poll();
                return;
            }
            status("Mining a " + os.getBlock().getName().getString() + " vein (mine #" + wp.id + ")", "Mining");
            if (dig(wp, ore)) {
                if (BlockWork.canHarvest(os, npc.getMainHandItem())) {
                    npc.awardXp(oreXp(os));
                    prospect(wp, os, ore);
                }
                vein.poll();
                noticeOres(wp, ore);
            }
            return;
        }
        List<SpiralMine.Cell> plan = MineLayout.tunnelPlan(lv);
        if (lv.progress != faceProgress || lv != faceLevel) {
            faceProgress = lv.progress;
            faceLevel = lv;
            faceTicks = 0;
        } else if (lv.progress < plan.size() && ++faceTicks > (npc.blockPosition()
                .closerThan(MineLayout.tunnelPos(wp, lv, plan.get(lv.progress)), 10) ? FACE_GIVE_UP_TICKS : 2 * FACE_GIVE_UP_TICKS)) {
            // a full minute on one cell of the plan without getting it done (it cannot be reached from any floor the
            // miner can walk to, e.g. across a cavern): leave it, as a player would, and what lies beyond it
            faceTicks = 0;
            cutPlan(lv, plan);
            npc.navigator().reset();
            return;
        }
        if (lv.progress >= plan.size()) {
            status("Mine level worked out - going to choose the next one", "Waiting");
            state = State.STORAGE;
            waitFor(100);
            return;
        }
        SpiralMine.Cell c = plan.get(lv.progress);
        BlockPos pos = MineLayout.tunnelPos(wp, lv, c);
        BlockState s = level().getBlockState(pos);
        if (c.torch()) {
            if (s.isAir() && Inv.consume(npc.inventory(), TORCH, 1)) {
                level().setBlock(pos, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
                level().playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
                npc.swing(InteractionHand.MAIN_HAND);
            }
            lv.progress++;
            data().setDirty();
            return;
        }
        if (level().canSeeSky(pos) || level().canSeeSky(pos.above())) {
            // the tunnel came out of a hillside: a player stops the branch there instead of opening the mine to the
            // surface (and to mobs)
            lv.progress++;
            data().setDirty();
            return;
        }
        if (passable(s)) {
            if (c.dy() == 0 && floorMissing(pos) && !noSealBlocks) {
                // an open cave cell: walk up and bridge its floor before going on, so the tunnel stays walkable
                if (!BlockWork.inReach(npc, pos.below(), 4.5)) {
                    if (!bridgeNearbyHoles(wp, lv, plan)
                            && walkInMine(wp, approach(wp, lv, plan, lv.progress), landingStep, 1.0, "the tunnel face") && ++unreachable > 60) {
                        // at the last cell with a floor and the gap is still out of reach (a wide cavern): leave it open
                        unreachable = 0;
                        lv.progress++;
                        data().setDirty();
                    }
                    return;
                }
                bridgeFloor(pos);
            }
            lv.progress++;
            data().setDirty();
            noticeOres(wp, pos);
            return;
        }
        if (!mayDig(wp, pos)) {
            // a block nobody may dig (a structure of the world, another mine's stairs, a base or its land, the deep
            // dark): like a player, the miner ends this branch there (or the level, for the main tunnel)
            breaker.reset(npc);
            cutPlan(lv, plan);
            return;
        }
        if (!BlockWork.inReach(npc, pos, 4.5)) {
            if (bridgeNearbyHoles(wp, lv, plan)) {
                return;
            }
            BlockPos stand = approach(wp, lv, plan, lv.progress);
            if (walkInMine(wp, stand, landingStep, 1.0, "the tunnel face") && ++unreachable > 60) {
                // standing at the closest dug cell and still out of reach (e.g. a cave gap without floor): skip it
                unreachable = 0;
                lv.progress++;
                data().setDirty();
            }
            return;
        }
        unreachable = 0;
        if (!preSeal(wp, pos, shaft)) {
            if (noSealBlocks) {
                // nothing to plug the water with and the storehouse had nothing either: leave this cell as a wall
                lv.progress++;
                data().setDirty();
                return;
            }
            status("Need blocks to plug water in mine #" + wp.id, "Waiting");
            breaker.reset(npc);
            state = State.STORAGE;
            return;
        }
        status("Branch mining at y=" + pos.getY() + " (mine #" + wp.id + ", " + lv.progress + "/" + plan.size() + ")", "Mining");
        if (fluidCell(level().getBlockState(pos))) {
            // a flooded tunnel cell (a cave lake, an aquifer): with every way in plugged, it is cleared out, as in the
            // shaft, instead of being "dug" until the face watchdog gives the tunnel up
            level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (c.dy() == 0) {
                bridgeFloor(pos);
            }
            lv.progress++;
            data().setDirty();
            noticeOres(wp, pos);
            return;
        }
        if (dig(wp, pos)) {
            if (isOre(s) && BlockWork.canHarvest(s, npc.getMainHandItem())) {
                npc.awardXp(oreXp(s));
                prospect(wp, s, pos);
            }
            if (c.dy() == 0) {
                bridgeFloor(pos);
            }
            lv.progress++;
            data().setDirty();
            noticeOres(wp, pos);
        }
    }

    /**
     * Gives up plan cell {@code lv.progress} and what lies beyond it: a cut main tunnel ends the level for good
     * (everything further out is past the gap, so it is never lengthened either), a cut side branch goes on to the
     * next branch.
     */
    private void cutPlan(MineLevel lv, List<SpiralMine.Cell> plan) {
        SpiralMine.Cell lost = plan.get(lv.progress);
        int side = MineLayout.landingSide(lv);
        if (lost.s() == side) {
            lv.cut = true;
            lv.progress = plan.size();
        } else {
            int dir = Integer.signum(lost.s() - side);
            while (lv.progress < plan.size() && plan.get(lv.progress).f() == lost.f()
                    && Integer.signum(plan.get(lv.progress).s() - side) == dir) {
                lv.progress++;
            }
        }
        data().setDirty();
    }

    /** The last cell that passed {@link WorkSafety#mayDig}, and its block: breaking one takes many ticks. */
    private BlockPos safeCell;
    private BlockState safeState;

    /** {@link WorkSafety#mayDig} for this miner and its mine, remembered for the cell being broken. */
    private boolean mayDig(WorkplaceState wp, BlockPos pos) {
        BlockState s = level().getBlockState(pos);
        if (pos.equals(safeCell) && s == safeState) {
            return true;
        }
        if (!WorkSafety.mayDig(level(), pos, base(), wp)) {
            return false;
        }
        safeCell = pos.immutable();
        safeState = s;
        return true;
    }

    /** Prospector affix: a chance that the ore just mined drops its loot a second time. */
    private void prospect(WorkplaceState wp, BlockState ore, BlockPos pos) {
        if (level().random.nextDouble() < npc.affix(com.factionclash.logic.Affix.PROSPECTOR)) {
            wp.totalOutput += BlockWork.dropExtra(npc, ore, pos);
            data().setDirty();
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
    private boolean bridgeNearbyHoles(WorkplaceState wp, MineLevel lv, List<SpiralMine.Cell> plan) {
        if (noSealBlocks) {
            return false;
        }
        for (int j = Math.max(0, lv.progress - 48); j < Math.min(lv.progress, plan.size()); j++) {
            SpiralMine.Cell c = plan.get(j);
            if (c.dy() != 0 || c.torch()) {
                continue;
            }
            BlockPos foot = MineLayout.tunnelPos(wp, lv, c);
            if (passable(level().getBlockState(foot)) && floorMissing(foot) && !level().canSeeSky(foot)
                    && BlockWork.inReach(npc, foot.below(), 4.5) && bridgeFloor(foot)) {
                status("Bridging a cave hole in the tunnel floor (mine #" + wp.id + ")", "Mining");
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ digging deeper

    /**
     * Digs the spiral down from the bottom to the current level's landing, step by step. Water or lava in a cell is
     * plugged around and cleared, not broken. The descent is given up (see {@link #abandonDescent}) at a block nobody
     * may dig, after {@link #STUCK_STEP_TICKS} at one cell within reach of it, after {@link #DESCENT_WALK_TICKS}
     * walking toward it without getting closer, or after {@link #SEAL_FAILURES} failed plugs in a row with nothing to
     * plug with.
     */
    private void extend(WorkplaceState wp) {
        MineLevel lv = MineLayout.current(wp);
        int from = wp.bottom, to = lv == null ? -1 : lv.step;
        if (lv == null || to <= from) {
            wp.upgradeStep = -1;
            data().setDirty();
            state = State.TRAVEL;
            return;
        }
        if (from == 0 && wp.upgradeStep == 0 && !wp.hut) {
            mouthTorch(wp);
        }
        List<SpiralMine.Cell> plan = SpiralMine.spiralPlan(from, to);
        if (wp.upgradeStep >= plan.size()) {
            wp.bottom = to;
            wp.upgradeStep = -1;
            extendStep = -1;
            data().setDirty();
            npc.awardXp(LEVEL_XP);
            Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Mine #" + wp.id + " reaches y="
                    + (wp.origin.getY() - to) + " for " + bandOf(wp, lv).label + " (" + npc.npcName() + ")"));
            state = State.TRAVEL;
            return;
        }
        SpiralMine.Cell c = plan.get(wp.upgradeStep);
        BlockPos pos = MineLayout.spiralPos(wp, c);
        int step = stepOfCell(c);
        long t = now();
        if (wp.upgradeStep != extendStep || wp.id != extendMine) {
            extendMine = wp.id;
            extendStep = wp.upgradeStep;
            extendTicks = 0;
            extendWalkTicks = 0;
            extendWalkBest = Double.MAX_VALUE;
        } else if (t - extendLast > WALK_GAP) {
            // back from an errand or a fight: the walk back to the cell is measured from here
            extendWalkTicks = 0;
            extendWalkBest = Double.MAX_VALUE;
        }
        extendLast = t;
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
        if (!mayDig(wp, pos)) {
            // a block nobody may dig (a structure, a block entity, the deep dark): the shaft stops above it
            abandonDescent(wp, c, "it met " + s.getBlock().getName().getString() + " that may not be dug");
            return;
        }
        if (!BlockWork.inReach(npc, pos, 4.5)) {
            // the walk down counts toward its own budget, measured by getting closer, never toward the time at the cell
            double d = Math.sqrt(npc.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            if (d < extendWalkBest - 0.5) {
                extendWalkBest = d;
                extendWalkTicks = 0;
            } else if (++extendWalkTicks > DESCENT_WALK_TICKS) {
                abandonDescent(wp, c, "the bottom of the shaft could not be reached");
                return;
            }
            walkInMine(wp, MineLayout.stand(wp, step - 1), step - 1, 1.0, "the bottom of the shaft");
            return;
        }
        if (++extendTicks > STUCK_STEP_TICKS) {
            abandonDescent(wp, c, "the shaft could not be dug further");
            return;
        }
        if (!preSeal(wp, pos, to)) {
            if (noSealBlocks && ++sealFailures >= SEAL_FAILURES) {
                abandonDescent(wp, c, "water or lava there, and nothing to plug it with");
                return;
            }
            status("Need blocks to plug water in the shaft", "Waiting");
            breaker.reset(npc);
            state = State.STORAGE;
            return;
        }
        sealFailures = 0;
        status("Digging the shaft down to y=" + (wp.origin.getY() - to) + " for " + bandOf(wp, lv).label + " (y=" + pos.getY() + ")", "Digging");
        if (fluidCell(level().getBlockState(pos))) {
            // water or lava standing in the cell itself (an aquifer, an underground lake): with every way in plugged,
            // it is cleared out as a player would (a block placed and broken again), and the descent goes on
            level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (!sealShaft(wp, pos, to)) {
                state = State.STORAGE;
                return;
            }
            wp.upgradeStep++;
            data().setDirty();
            return;
        }
        if (dig(wp, pos)) {
            if (isOre(s) && BlockWork.canHarvest(s, npc.getMainHandItem())) {
                npc.awardXp(oreXp(s));
                prospect(wp, s, pos);
            }
            noticeOres(wp, pos);
            if (!sealShaft(wp, pos, to)) {
                state = State.STORAGE;
                return;
            }
            wp.upgradeStep++;
            data().setDirty();
        }
    }

    /**
     * A new mine has no hut: before the first block of its shaft is dug the miner sets a torch beside the mouth
     * (where it is air on firm ground), so the opening is lit and easy to see.
     */
    private void mouthTorch(WorkplaceState wp) {
        BlockPos t = MineLayout.local(wp, 0, -1, 0);
        BlockState below = level().getBlockState(t.below());
        if (level().getBlockState(t).isAir() && below.isFaceSturdy(level(), t.below(), Direction.UP)
                && BlockWork.inReach(npc, t, 4.5) && Inv.consume(npc.inventory(), TORCH, 1)) {
            level().setBlock(t, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
            level().playSound(null, t, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
            npc.swing(InteractionHand.MAIN_HAND);
        }
    }

    /**
     * The shaft cannot go below the step of {@code cell}: the steps above it, all dug, become the bottom and the
     * deepest the shaft may ever go, the level it was dug for is dropped, and the next storehouse trip plans a level
     * within reach. A mine whose shaft stops before it is {@link MinePlanner#MIN_DEPTH} deep is given up.
     */
    private void abandonDescent(WorkplaceState wp, SpiralMine.Cell cell, String why) {
        int k = stepOfCell(cell);
        MineLevel lv = MineLayout.current(wp);
        wp.bottom = Math.max(wp.bottom, k - 1);
        wp.shaftLimit = wp.shaftLimit > 0 ? Math.min(wp.shaftLimit, k - 1) : k - 1;
        if (lv != null && lv.step > wp.bottom) {
            wp.levels.remove(lv);
        }
        wp.cur = -1;
        wp.upgradeStep = -1;
        data().setDirty();
        extendStep = -1;
        sealFailures = 0;
        breaker.reset(npc);
        state = State.STORAGE;
        Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Mine #" + wp.id + ": the shaft stops at y="
                + (wp.origin.getY() - wp.bottom) + " (" + why + ")"));
        if (wp.bottom < MinePlanner.MIN_DEPTH) {
            addBadSite(wp.origin);
            MineSites.giveUp(level(), data(), wp, "the ground below is unsafe");
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
    private boolean preSeal(WorkplaceState wp, BlockPos cell, int shaftSteps) {
        MineLevel lv = wp == null ? null : workLevel(wp);
        List<SpiralMine.Cell> plan = lv != null && lv.step <= wp.bottom ? MineLayout.tunnelPlan(lv) : null;
        for (Direction d : Direction.values()) {
            BlockPos n = cell.relative(d);
            BlockState ns = level().getBlockState(n);
            boolean loose = ns.getBlock() instanceof FallingBlock && d == Direction.UP;
            // sand or gravel holding back water (a lake bottom) is as dangerous as the water itself
            boolean fluid = !ns.getFluidState().isEmpty() || (loose && touchesFluid(n));
            boolean inShaft = wp != null && MineLayout.inShaft(wp, n, shaftSteps);
            if (fluid && (inShaft || (plan != null && dugTunnelCell(wp, lv, plan, n))) && !level().getFluidState(n).isEmpty()) {
                // water or lava in a cell of the stairs not dug yet (an aquifer below the descent): plugged like the
                // rock around, so it does not pour into the cells just opened; it is dug out as an ordinary block when
                // the descent comes to it (with nothing to plug with, it is only cleared, as below)
                if (inShaft && !MineLayout.shaftCellDug(wp, n) && !npc.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(n))
                        && placeSeal(n)) {
                    continue;
                }
                // water or lava that ran into the stairs or the tunnels already dug: clear it out (a player places a
                // block there and breaks it again) rather than plugging the way in and out shut
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

    /**
     * True if {@code q} is a cell of the level's branch mine that the miner has already passed: the plan runs outward
     * (each main-tunnel cell, then the branches at that distance), so every planned cell nearer the shaft than the
     * cell being worked is behind it, and so are the cells just before it at the same distance.
     */
    static boolean dugTunnelCell(WorkplaceState wp, MineLevel lv, List<SpiralMine.Cell> plan, BlockPos q) {
        int f = MineLayout.forwardOf(wp, q), s = MineLayout.sideOf(wp, q), dy = q.getY() - (wp.origin.getY() - lv.step);
        if (f < SpiralMine.MAIN_START || dy < 0 || dy > 1 || lv.progress <= 0) {
            return false;
        }
        int side = MineLayout.landingSide(lv), off = Math.abs(s - side);
        boolean branch = f > SpiralMine.MAIN_START && (f - SpiralMine.MAIN_START) % SpiralMine.BRANCH_SPACING == 0
                && off >= 1 && off <= SpiralMine.BRANCH_LENGTH;
        if (off != 0 && !branch) {
            return false;
        }
        int cur = Math.min(lv.progress, plan.size());
        int fCur = cur < plan.size() ? plan.get(cur).f() : Integer.MAX_VALUE;
        if (f != fCur) {
            return f < fCur && f < SpiralMine.MAIN_START + SpiralMine.MAIN_LENGTH * (lv.rounds + 1);
        }
        for (int j = cur - 1; j >= 0 && plan.get(j).f() == f; j--) {
            SpiralMine.Cell c = plan.get(j);
            if (!c.torch() && c.s() == s && c.dy() == dy) {
                return true;
            }
        }
        return false;
    }

    private boolean touchesFluid(BlockPos p) {
        for (Direction d : Direction.values()) {
            if (d != Direction.DOWN && !level().getFluidState(p.relative(d)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * After digging a shaft cell: close cave holes around the spiral so it stays a safe staircase. A mine without a
     * hut also plugs the ground ring around its mouth (the layer the miner stands on at the surface) where it is
     * open, wet or loose; open air above the ground is left as it is.
     */
    private boolean sealShaft(WorkplaceState wp, BlockPos dug, int steps) {
        int top = wp.origin.getY() - (wp.hut ? 1 : 0);
        for (Direction d : Direction.values()) {
            BlockPos n = dug.relative(d);
            if (MineLayout.inShaft(wp, n, steps) || n.getY() >= top
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
        if (!badSites.isEmpty()) {
            tag.putLongArray("badSites", badSites.stream().mapToLong(Long::longValue).toArray());
        }
        if (nextSiteSearch > 0) {
            tag.putLong("nextSite", nextSiteSearch);
            tag.putString("siteWhy", siteFailReason);
        }
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
        } else if (state == State.SITE) {
            state = State.CHECK;
        }
        badSites.clear();
        if (tag.contains("badSites", Tag.TAG_LONG_ARRAY)) {
            for (long k : tag.getLongArray("badSites")) {
                addBadSite(BlockPos.of(k));
            }
        }
        nextSiteSearch = tag.getLong("nextSite");
        if (tag.contains("siteWhy", Tag.TAG_STRING)) {
            String why = tag.getString("siteWhy");
            // saved by an earlier version, in words that did not follow "has had"
            siteFailReason = why.equals("the ground here is too shallow for a mine") ? TOO_SHALLOW : why;
        }
        caveJob.load(tag.getCompound("cave"), provider);
    }
}
