package com.factionclash.combat;

import com.factionclash.logic.ReachPlanner;
import com.factionclash.npc.DigOut;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcNavigator;
import com.factionclash.work.BlockWork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a fighter does about a target it cannot walk up to and hit (the owner's report: a player in an animal pen, or
 * behind a broken door with a block in the doorway, killed soldiers that stood within reach unable to hit back). As
 * a player would: it opens gates and doors on the way (the path does), breaks a block in the way that it may break
 * (never a planned one, see {@link Reach}), builds itself up a block or two with blocks it carries, or breaks the block
 * under a target standing too high to hit ({@link ReachPlanner}). When there is no way it stays out of the target's
 * reach and watches it from near the way out, calls the archers, and goes in as soon as a way opens.
 * <p>
 * Modes: CHASE (the attack goal walks and swings as usual), WORK (walking to where a plan's step is done and doing it),
 * HOLD (waiting out of reach). Path searches are few: one check every {@link #RECHECK} ticks while holding, the planner
 * at most that often, and none at all while working.
 */
public final class Pursuit {
    public enum Mode { CHASE, WORK, HOLD }

    /** PROPOSED: a fighter that cannot reach its target waits this far from it (horizontal): out of a player's 3-block reach (4 allowed by the server) with a margin, close enough to watch. */
    public static final double HOLD_MIN = 5.0, HOLD_MAX = 9.0;
    /** PROPOSED: the ring the waiting spots are chosen on (melee; archers stand farther, within their range). */
    static final double HOLD_RING = 6.5, ARCHER_RING = 9.0;
    /** PROPOSED: ticks between checks for a way while waiting (one path search), and the most often the planner runs. */
    public static final int RECHECK = 40;
    /** PROPOSED: this long close to the target without landing a blow although the path says it can: it cannot. */
    static final int STUCK = 60;
    /** PROPOSED: the planner's budget (ticks of work), the blocks broken per target, the blocks built up per climb. */
    static final int MAX_PLAN_COST = 600, MAX_BREAKS = 16, MAX_PILLAR = 3;
    /** The planner is used for targets this close (horizontal) and this far up or down; farther away it walks on. */
    static final int PLAN_RANGE = 12, PLAN_HEIGHT = 5;
    /** PROPOSED: ticks to get to where the work is done before the plan counts as failed. */
    static final int WALK_LIMIT = 120;
    /** PROPOSED: ticks between calls for the archers while waiting. */
    static final int ALARM_EVERY = 100;
    /**
     * PROPOSED: away from home (on a raid, out with a patrol) a fighter waits this long for a way to a target it cannot
     * reach, then lets it be for {@link #IGNORE_TICKS}: a player hiding in a box must not hold up a raid. At home
     * (within 16 blocks of its walls) it keeps watch for as long as the target stays in sight.
     */
    static final int GIVE_UP_AWAY = 1200, IGNORE_TICKS = 1200;
    /**
     * PROPOSED: blows it takes from its target without being able to hit back (while working its way in, or with no
     * path to it) before it counts that spot of the target's as one it cannot get at from here: it backs off out of
     * reach and does not walk up to it again (a player hitting soldiers from just behind a fence, through the gap
     * above a block in a doorway, or from a spot the planner thought it could strike from).
     */
    static final int UNANSWERED = 3;
    /**
     * PROPOSED: unanswered blows from one target in all (a player stepping from spot to spot behind a fence it is
     * breaking) after which it plans no more breaking or building toward that target: it waits out of reach until a
     * blow lands or a path opens.
     */
    static final int UNANSWERED_TOTAL = 6;
    /** PROPOSED: close and not landing a blow this long, with the target well above or below it: it cannot. */
    static final int STUCK_VERTICAL = 20;
    /** PROPOSED: how long it remembers a target's spots it cannot get at, and the blocks it broke for that target. */
    static final int MEMORY = 6000;
    /** PROPOSED: the farther ring a waiting fighter backs off to when its target shoots at it and there is no cover nearer. */
    static final double COVER_RING = 10.0;
    /** PROPOSED: a worker steps out of reach of a target it cannot get at for at most this long, then lets it be. */
    static final int CIVILIAN_STEP_AWAY = 100;
    /**
     * PROPOSED: waiting (HOLD) this long within its target's reach, not landing a blow and unable to move a block away
     * (boxed in: a 1x1 pit it jumped or was knocked into, with the player hitting it from the edge), it uses the stuck
     * recovery to get out toward the waiting ring. At most {@link #MAX_UNBOX} times per target.
     */
    static final int BOXED_IN = 30, MAX_UNBOX = 3;
    /**
     * PROPOSED: how far (blocks walked) a path search toward the target may wander: the way round an animal pen to its
     * gate on the far side was cut off at vanilla's 48 (see {@link #pathTo}); the search's node budget is unchanged.
     */
    static final float PATH_RANGE = 64;

    private final FactionNpc npc;
    private Mode mode = Mode.CHASE;
    private LivingEntity target;
    private int recheck;
    private ReachPlanner.Plan plan;
    private int workIndex;
    private int walkTicks;
    private final BlockWork.Breaker breaker = new BlockWork.Breaker();
    private BlockPos holdSpot;
    private BlockPos exit;
    private Vec3 holdFrom;
    private int holdAge;
    private int closeNoHit;
    /** The fewest path steps it had left since it came close without landing a blow (it is stuck unless that drops). */
    private int fewestLeft;
    private int broken;
    private int alarmIn;
    private boolean archer;
    /** Last tick (the NPC's tickCount) it was actively going for or watching its target: keeps the target from going stale. */
    public int engagedAt = Integer.MIN_VALUE / 2;
    /** Counters for tests: planner runs, times it started waiting, blocks broken and built. */
    public int plans, holds, breaks, pillars;
    /**
     * What it learnt about one target (kept while the target leaves and comes back, until {@link #MEMORY} ticks after
     * it last learnt something, or a blow lands): the target's cells it cannot get at (true when a path reached the
     * cell then, so only a new block in the way changes that; false when there was no path, so a path opening clears
     * it), and the blocks it broke on the way (one put back is not broken again).
     */
    private final Map<Long, Boolean> futile = new HashMap<>();
    private final Set<Long> brokenCells = new HashSet<>();
    private UUID memoryOf;
    private int memoryAt;
    /** Whether the last path searched toward the target reached it. */
    private boolean pathReach = true;
    /** Blows taken from the target since it last landed one, while it could not hit back: at the current spot, and in all. */
    private int unanswered, unansweredTotal;
    /** Shot at by its target while waiting: it waits out of the target's sight (or farther) instead. */
    private boolean cover;
    /** Where it stood when it came to be boxed in within the target's reach, the ticks since, and its hops out (per target). */
    private Vec3 boxedAt;
    private int boxedTicks, unboxes;
    /** For tests: stuck recoveries out of a box (a pit) within a target's reach. */
    public int unboxed;

    public Pursuit(FactionNpc npc) {
        this.npc = npc;
    }

    public Mode mode() {
        return mode;
    }

    /** The target this state belongs to. */
    public LivingEntity target() {
        return target;
    }

    public BlockPos holdSpot() {
        return mode == Mode.HOLD ? holdSpot : null;
    }

    /** Forgets everything (the fight is over, or a new target). */
    public void reset() {
        if (mode == Mode.WORK) {
            breaker.reset(npc);
        }
        mode = Mode.CHASE;
        target = null;
        plan = null;
        holdSpot = null;
        exit = null;
        recheck = 0;
        closeNoHit = 0;
        broken = 0;
        alarmIn = 0;
        heldFor = 0;
        unanswered = 0;
        cover = false;
        pathReach = true;
        boxedAt = null;
        boxedTicks = 0;
        decideOnLanding = false;
        futileOnLanding = false;
    }

    /**
     * Ties the state to this target; a different one starts afresh. What it learnt about a target (spots it cannot get
     * at, blocks put back) is kept when the same target comes back, so a player hiding in a hole is not walked up to
     * again each time a guard is sent after it.
     */
    public void bind(LivingEntity t, boolean ranged) {
        if (t != target) {
            reset();
            target = t;
            if (t != null && (!t.getUUID().equals(memoryOf) || npc.tickCount - memoryAt > MEMORY)) {
                futile.clear();
                brokenCells.clear();
                unansweredTotal = 0;
                unboxes = 0;
                memoryOf = t.getUUID();
                memoryAt = npc.tickCount;
            }
        }
        archer = ranged;
    }

    /** It landed a blow on {@code victim}: the target can be got at after all. */
    public void landed(Entity victim) {
        if (victim == target) {
            unanswered = 0;
            unansweredTotal = 0;
            futile.clear();
        }
    }

    /**
     * Whether the target stands in a cell it found it cannot get at. {@code reachNow}: a path searched just now
     * reaches the target; that clears a cell marked when there was no path (a way in opened).
     */
    public boolean futile(LivingEntity t, boolean reachNow) {
        long key = t.blockPosition().asLong();
        Boolean then = futile.get(key);
        if (then == null) {
            return false;
        }
        if (reachNow && !then) {
            futile.remove(key);
            return false;
        }
        return true;
    }

    private void markFutile(LivingEntity t) {
        if (futile.size() < 64) {
            futile.put(t.blockPosition().asLong(), pathReach);
        }
        memoryAt = npc.tickCount;
    }

    /** The path searched toward the target reached it (and the target is not in a cell it found it cannot get at). */
    public void reachable() {
        pathReach = true;
    }

    /**
     * It was hurt. Blows from its target that it cannot answer while it works its way in, has no path to it, or
     * stands close below or above it are counted, and after {@link #UNANSWERED} it backs off out of reach and keeps
     * away from that spot of the target's; an arrow from its target while it waits sends it into cover.
     */
    public void onHurt(DamageSource source) {
        Entity e = source.getEntity();
        if (archer || target == null || e != target || !target.isAlive() || canStrikeNow(target)) {
            return;
        }
        boolean projectile = source.getDirectEntity() != null && source.getDirectEntity() != e;
        if (mode == Mode.HOLD) {
            if (projectile && !cover) {
                cover = true;
                holdAge = 1000; // a new spot at once
            }
            return;
        }
        // working its way in, no path, or close below or above the target (the edge of a hole, the foot of a ledge)
        boolean blocked = mode == Mode.WORK || !pathReach
                || Math.abs(target.getY() - npc.getY()) >= 1.5 && horizontal(npc, target) <= 3.0;
        if (blocked) {
            unansweredTotal++;
            memoryAt = npc.tickCount;
        }
        if (blocked && ++unanswered >= UNANSWERED) {
            unanswered = 0;
            markFutile(target);
            cover |= projectile;
            hold(target);
        }
    }

    private ServerLevel level() {
        return (ServerLevel) npc.level();
    }

    private static double horizontal(LivingEntity a, LivingEntity b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Whether it can hit the target from where it stands right now. Standing: in the air its hit box is higher than
     * where it stands (live check: in a 1x1 pit, knocked up by the player's blows at the edge, it counted as able to
     * strike at the top of each bounce, so nothing ever saw it as stuck).
     */
    public boolean canStrikeNow(LivingEntity t) {
        return (npc.onGround() || npc.isInWater()) && npc.isWithinMeleeAttackRange(t) && npc.getSensing().hasLineOfSight(t);
    }

    // ------------------------------------------------------------------ deciding

    /**
     * The path the attack goal just searched toward the target does not reach it: plan a way (break, build, undermine)
     * or wait out of reach. Far targets are walked toward as before (the partial path), until they are near enough to plan.
     */
    public void unreachable(LivingEntity t, Path partial) {
        Node end = partial == null ? null : partial.getEndNode();
        exit = end == null ? npc.blockPosition() : end.asBlockPos();
        pathReach = partial != null && partial.canReach();
        if (horizontal(npc, t) > PLAN_RANGE || Math.abs(t.getY() - npc.getY()) > PLAN_HEIGHT) {
            return;
        }
        decide(t);
    }

    /**
     * Close and not landing a blow for a while although the path says it can reach (a player in a 1x1 hole two deep,
     * just behind a fence): treat it as unreachable; when nothing can be done about it from here, that spot of the
     * target's is one it does not walk up to again.
     */
    public void watchStuck(LivingEntity t, boolean struckOrCanStrike) {
        if (mode != Mode.CHASE || struckOrCanStrike || horizontal(npc, t) > 3.0) {
            closeNoHit = 0;
            return;
        }
        int remaining = pathLeft();
        if (closeNoHit == 0) {
            fewestLeft = remaining;
        } else if (remaining < fewestLeft - 1) {
            // still getting along its path (round to the stairs of a wall the target stands on): not stuck. Being
            // knocked back from the edge of a hole is no progress: the path's last steps are still to go.
            closeNoHit = 0;
            return;
        }
        int limit = Math.abs(t.getY() - npc.getY()) >= 1.5 ? STUCK_VERTICAL : STUCK;
        if (++closeNoHit >= limit) {
            closeNoHit = 0;
            if (exit == null) {
                exit = npc.blockPosition();
            }
            decide(t);
            if (mode == Mode.HOLD) {
                markFutile(t);
            } else if (decideOnLanding) {
                futileOnLanding = true; // knocked into the air: the same, once it lands
            }
        }
    }

    /**
     * Live check (a 1x1 pit two deep in its own yard): soldiers that jumped in after a player, or were knocked in,
     * could neither hit the player at the edge (two blocks up) nor walk, dig (the base's ground) or build (its own
     * yard) their way out, and stood there within its reach until killed. Waiting (HOLD) boxed in like that for
     * {@link #BOXED_IN} ticks - within the target's reach, unable to strike from where it stands (a lucky blow at the
     * top of a bounce does not count), not getting a block away - it uses the stuck recovery (the one teleport
     * player-like NPCs allow themselves) toward the waiting ring. A fighter with blocks builds itself out first:
     * filling a hole in its own base's ground is allowed ({@link Reach#mayPlace}), so the planner does that.
     */
    public void watchBoxedIn(LivingEntity t, boolean canStrike) {
        if (mode != Mode.HOLD || canStrike || horizontal(npc, t) >= HOLD_MIN - 0.3 || unboxes >= MAX_UNBOX) {
            boxedAt = null;
            boxedTicks = 0;
            return;
        }
        double mx = boxedAt == null ? 0 : npc.getX() - boxedAt.x, mz = boxedAt == null ? 0 : npc.getZ() - boxedAt.z;
        if (boxedAt == null || mx * mx + mz * mz > 1.0) { // (jumping against the side of a pit is no way out)
            boxedAt = npc.position();
            boxedTicks = 0;
            return;
        }
        if (++boxedTicks < BOXED_IN) {
            return;
        }
        boxedAt = null;
        boxedTicks = 0;
        double dx = npc.getX() - t.getX(), dz = npc.getZ() - t.getZ(), len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.3) {
            BlockPos from = exit != null ? exit : npc.blockPosition().relative(npc.getDirection().getOpposite(), 4);
            dx = from.getX() + 0.5 - t.getX();
            dz = from.getZ() + 0.5 - t.getZ();
            len = Math.max(1.0e-3, Math.sqrt(dx * dx + dz * dz));
        }
        Vec3 goal = new Vec3(t.getX() + dx / len * HOLD_RING, t.getY(), t.getZ() + dz / len * HOLD_RING);
        if (npc.navigator().recoverToward(goal)) {
            unboxes++;
            unboxed++;
            closeNoHit = 0;
            npc.getNavigation().stop();
            // that spot of the target's cannot be got at from where it was: it waits out of reach (and goes in when a
            // path leads to the target, as one does once it is out of the pit beside a player at the edge)
            markFutile(t);
            hold(t);
            holdSpot = null;
            holdAge = 1000; // a spot from where it landed
        }
    }

    private void decide(LivingEntity t) {
        if (canStrikeNow(t)) {
            mode = Mode.CHASE;
            return;
        }
        if (!npc.onGround() && !npc.isInWater()) {
            // mid-jump: its cell is not where it stands. It decides as soon as it lands (in a pit it would otherwise
            // keep jumping at the side, and be in the air at every look)
            decideOnLanding = true;
            return;
        }
        decideOnLanding = false;
        if (!supported(t)) {
            mode = Mode.CHASE; // a target in the air (a flying monster, a fall): chased as always, nothing built toward it
            return;
        }
        boolean known = futile(t, pathReach);
        plan = null;
        // a worker defends itself but does not break or build its way to anyone
        if (!known && npc.fightsLikeSoldier() && broken < MAX_BREAKS && unansweredTotal < UNANSWERED_TOTAL && npc.onGround()) {
            int blocks = Math.min(MAX_PILLAR, pillarBlocks());
            plan = ReachPlanner.plan(Reach.world(level(), npc, k -> brokenCells.contains(k)), cell(standCell()), cell(targetCell(t)),
                    blocks, true, MAX_PLAN_COST);
            plans++;
        }
        if (plan != null && !plan.steps().isEmpty()) {
            int w = plan.firstWork();
            workIndex = w >= 0 ? w : plan.steps().size() - 1;
            walkTicks = 0;
            mode = Mode.WORK;
            engagedAt = npc.tickCount;
            npc.getNavigation().stop(); // off the chase's path: the first work tick walks to where the work is
            return;
        }
        if (plan != null) {
            // the plan says it can strike from where it stands, but the swing does not land (its hit box a little
            // short of a target at the far side of its cell): one short go, then this spot counts as out of reach
            markFutile(t);
            mode = Mode.CHASE;
            closeNoHit = STUCK - STUCK_VERTICAL;
            fewestLeft = pathLeft();
            return;
        }
        hold(t);
    }

    /** Path steps it has left to walk (0 with no path). */
    private int pathLeft() {
        Path path = npc.getNavigation().getPath();
        return path == null || path.isDone() ? 0 : path.getNodeCount() - path.getNextNodeIndex();
    }

    /** Something under the target within two blocks (it stands, or jumps): not a flying or falling one. */
    private boolean supported(LivingEntity t) {
        if (t.onGround() || t.isInWater()) {
            return true;
        }
        BlockPos f = t.blockPosition();
        for (int k = 0; k <= 2; k++) {
            BlockPos b = f.below(k);
            if (!level().getBlockState(b).getCollisionShape(level(), b).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The cell it stands in for planning. Its feet's block position, unless that holds something it is only pressed
     * against (a fence post its middle has slid over): then the open cell beside it that its body is mostly in; or,
     * standing on top of a low block (a slab, a path), the cell above it.
     */
    private BlockPos standCell() {
        return bodyCell(npc);
    }

    /** {@link #standCell()} for any entity. */
    private BlockPos bodyCell(Entity e) {
        ServerLevel level = level();
        BlockPos b = e.blockPosition();
        if (open(b) && open(b.above())) {
            return b;
        }
        BlockState s = level.getBlockState(b);
        var shape = s.getCollisionShape(level, b);
        if (!shape.isEmpty() && shape.max(net.minecraft.core.Direction.Axis.Y) <= e.getY() - b.getY() + 1.0E-3
                && open(b.above()) && open(b.above(2))) {
            return b.above();
        }
        BlockPos best = b;
        double bestD = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = b.offset(dx, 0, dz);
                if ((dx != 0 || dz != 0) && open(n) && open(n.above())) {
                    double d = e.position().distanceToSqr(n.getX() + 0.5, e.getY(), n.getZ() + 0.5);
                    if (d < bestD) {
                        bestD = d;
                        best = n;
                    }
                }
            }
        }
        return best;
    }

    /**
     * The cell paths and plans aim at for the target: its block position, unless that holds a fence, wall or pane it
     * is only pressed against: then the open cell beside it that its body is mostly in (a low block it stands on keeps
     * its block position, where the path search puts it too). Live check: a player pressed against a pen's far fence
     * stood "in" the fence's cell, and the cell just outside the fence was as near to it as the one inside; a soldier
     * coming from outside walked round to there, could not hit across the fence, took the player's blows and then
     * waited out of reach for good.
     */
    public BlockPos targetCell(LivingEntity t) {
        BlockPos b = t.blockPosition();
        BlockPos c = bodyCell(t);
        return c.getY() == b.getY() ? c : b;
    }

    /**
     * A path search toward the target ({@link #targetCell}), as the attack goal's own search, but one that may wander
     * up to {@link #PATH_RANGE} blocks walked ({@link com.factionclash.npc.NpcPathNavigation#createLongPath}).
     */
    public Path pathTo(LivingEntity t) {
        BlockPos c = targetCell(t);
        return npc.getNavigation() instanceof com.factionclash.npc.NpcPathNavigation nav ? nav.createLongPath(c, 0, PATH_RANGE)
                : npc.getNavigation().createPath(c, 0);
    }

    private boolean open(BlockPos p) {
        return Reach.passable(level(), p, level().getBlockState(p));
    }

    private void hold(LivingEntity t) {
        if (mode == Mode.WORK) {
            breaker.reset(npc);
        }
        if (mode != Mode.HOLD) {
            holds++;
            mode = Mode.HOLD;
            holdSpot = null;
            holdAge = 1000; // pick a spot at once
            alarmIn = 0;
        }
        if (heldFor == 0) {
            heldFor = 1;
        }
        recheck = RECHECK;
    }

    private static ReachPlanner.Cell cell(BlockPos p) {
        return new ReachPlanner.Cell(p.getX(), p.getY(), p.getZ());
    }

    private static BlockPos pos(ReachPlanner.Cell c) {
        return new BlockPos(c.x(), c.y(), c.z());
    }

    private int pillarBlocks() {
        int slot = DigOut.blockSlot(level(), npc);
        return slot < 0 ? 0 : npc.inventory().getItem(slot).getCount();
    }

    // ------------------------------------------------------------------ ticking

    /** A look found it in the air (a jump, a blow's knock-back): it decides when it lands, and (stuck) marks that spot. */
    private boolean decideOnLanding, futileOnLanding;

    /** Every tick, whatever the mode: a decision put off by a jump is taken on landing. */
    public void afterJump(LivingEntity t) {
        if (decideOnLanding && (npc.onGround() || npc.isInWater())) {
            boolean mark = futileOnLanding;
            futileOnLanding = false;
            decide(t);
            if (mark && mode == Mode.HOLD) {
                markFutile(t);
            }
        }
    }

    /** Runs WORK or HOLD for this tick (the attack goal chases in CHASE). */
    public void tick(LivingEntity t) {
        if (mode == Mode.WORK) {
            work(t);
        } else if (mode == Mode.HOLD) {
            holdTick(t);
        }
    }

    private void work(LivingEntity t) {
        engagedAt = npc.tickCount;
        if (plan == null || workIndex >= plan.steps().size()) {
            mode = Mode.CHASE;
            return;
        }
        ReachPlanner.Step step = plan.steps().get(workIndex);
        boolean walkOnly = !step.work();
        BlockPos origin = pos(walkOnly ? step.to() : step.from());
        if (!at(origin)) {
            breaker.reset(npc);
            if (++walkTicks > WALK_LIMIT) {
                hold(t); // cannot get to where the work is: wait instead, and look again later
                return;
            }
            stepTo(origin, 1.2);
            if (walkTicks % 20 == 1 && npc.getNavigation().isDone() && !near(origin)) {
                Path p = npc.getNavigation().createPath(origin, 0);
                if (p == null || !p.canReach()) {
                    hold(t);
                    return;
                }
                npc.getNavigation().moveTo(p, 1.2);
            }
            return;
        }
        npc.getNavigation().stop();
        if (walkOnly) {
            mode = Mode.CHASE; // there: from here it can strike (or the attack goal looks again)
            return;
        }
        for (ReachPlanner.Cell c : step.breaks()) {
            BlockPos b = pos(c);
            BlockState s = level().getBlockState(b);
            if (Reach.passable(level(), b, s)) {
                continue; // already gone (or it was only a door after all)
            }
            if (brokenCells.contains(b.asLong()) || !Reach.mayBreak(level(), b, npc) || !BlockWork.inReach(npc, b, 4.5)) {
                // the world changed (a block put back where it broke one, something it may not break): wait out of
                // reach and plan again at the next look, never again on the spot every tick
                hold(t);
                return;
            }
            if (breaker.tick(npc, b)) {
                breaks++;
                broken++;
                if (brokenCells.size() < 256) {
                    brokenCells.add(b.asLong());
                }
                memoryAt = npc.tickCount;
                Reach.forget(level(), b);
            }
            return; // one block at a time, at a player's speed
        }
        if (step.pillar() && !pillarUp(origin)) {
            hold(t); // someone stands where the block goes, or no room above: look again later
            return;
        }
        mode = Mode.CHASE; // this step is done: the attack goal searches again, and plans the next one if needed
    }

    /** Standing in that cell, near its middle or with nowhere further to walk. */
    private boolean at(BlockPos origin) {
        boolean centred = Math.abs(npc.getX() - (origin.getX() + 0.5)) < 0.35 && Math.abs(npc.getZ() - (origin.getZ() + 0.5)) < 0.35;
        return npc.onGround() && npc.blockPosition().equals(origin) && (centred || npc.getNavigation().isDone());
    }

    /** Within a step of that cell at its level: walked into straight, as a player steps over, no path search needed. */
    private boolean near(BlockPos c) {
        return Math.abs(npc.getY() - c.getY()) < 0.6 && npc.position().distanceToSqr(Vec3.atBottomCenterOf(c)) < 1.6 * 1.6;
    }

    /** The last step into a cell a path stopped short of (its search ends a block early now and then). */
    private void stepTo(BlockPos c, double speed) {
        if (npc.getNavigation().isDone() && near(c)) {
            npc.getMoveControl().setWantedPosition(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, speed * 0.8);
        }
    }

    /** Builds itself up one block, as a player does: a jump and a block from the backpack placed underfoot. */
    private boolean pillarUp(BlockPos p) {
        int slot = DigOut.blockSlot(level(), npc);
        BlockPos head = p.above(2);
        if (slot < 0 || !Reach.mayPlace(level(), p, npc) || !level().getBlockState(head).getCollisionShape(level(), head).isEmpty()) {
            return false;
        }
        ItemStack stack = npc.inventory().getItem(slot);
        if (!(stack.getItem() instanceof BlockItem bi)) {
            return false;
        }
        BlockState place = bi.getBlock().defaultBlockState();
        if (BlockWork.obstructed(level(), p, place, npc)) {
            return false; // someone else stands there
        }
        npc.teleportTo(p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5);
        level().setBlock(p, place, Block.UPDATE_ALL);
        level().playSound(null, p, place.getSoundType(level(), p, npc).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        stack.shrink(1);
        npc.inventory().setChanged();
        npc.swing(InteractionHand.MAIN_HAND);
        npc.exhaust(0.05F); // the jump
        pillars++;
        return true;
    }

    private int heldFor;
    private java.util.UUID ignored;
    private int ignoredUntil;

    /**
     * The target went stale (unseen, no blows) while it waited out of reach: a monster is given up for
     * {@link #IGNORE_TICKS} too, so the base's defence alarm, which hands intruders to free guards without line of
     * sight, does not hand it straight back (live check: a creeper shut in a pocket of the ground under the yard kept
     * the home guards waiting on it, round after round). An intruder (a player, an enemy soldier) is not.
     */
    public void staleTarget(LivingEntity t) {
        if (mode == Mode.HOLD && !(t instanceof net.minecraft.world.entity.player.Player) && !(t instanceof FactionNpc)) {
            ignored = t.getUUID();
            ignoredUntil = npc.tickCount + IGNORE_TICKS;
        }
    }

    /** A target it gave up on a little while ago (it does not pick it again until then). */
    public boolean ignores(net.minecraft.world.entity.Entity e) {
        return ignored != null && e.getUUID().equals(ignored) && npc.tickCount < ignoredUntil;
    }

    private boolean atHome() {
        com.factionclash.campaign.BaseState b = npc.base();
        return b != null && !b.destroyed && com.factionclash.campaign.BaseZones.insideWalls(b, npc.blockPosition(), 16);
    }

    private void holdTick(LivingEntity t) {
        if (!npc.fightsLikeSoldier()) {
            // a worker does not stand guard: it steps out of the target's reach, then lets it be (its job takes over)
            if (horizontal(npc, t) >= HOLD_MIN || ++heldFor > CIVILIAN_STEP_AWAY) {
                npc.setTarget(null);
                reset();
                return;
            }
            goToHoldSpot(t, 1.2);
            return;
        }
        // at home it keeps watch over an intruder (a player or an enemy soldier) for as long as it is there; a monster it
        // cannot get at (live check: a creeper shut in a pocket of the ground under the yard held two guards for good,
        // while an enemy player was in the base) is given up after a minute, as away from home
        boolean intruder = t instanceof net.minecraft.world.entity.player.Player || t instanceof FactionNpc;
        if (++heldFor > GIVE_UP_AWAY && (!atHome() || !intruder)) {
            ignored = t.getUUID();
            ignoredUntil = npc.tickCount + IGNORE_TICKS;
            npc.setTarget(null);
            reset();
            return;
        }
        boolean sees = npc.getSensing().hasLineOfSight(t);
        // keeps watching: the target does not go stale while it waits in sight of it, in cover from it, or, an
        // intruder, at home (an intruder hiding in a hole in its base is still there)
        if ((sees || cover || intruder && atHome()) && npc.distanceTo(t) < 24) {
            engagedAt = npc.tickCount;
        }
        npc.getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (--alarmIn <= 0) {
            alarmIn = ALARM_EVERY;
            Alerts.callArchers(npc, t, 32);
        }
        if (--recheck <= 0) {
            recheck = RECHECK;
            LivingEntity other = intruder ? null : intruderInSight();
            if (other != null) {
                // waiting on a monster it cannot get at, it turns to an enemy player or soldier it sees close by
                npc.setTarget(other);
                reset();
                return;
            }
            Path p = pathTo(t);
            boolean reach = p != null && p.canReach();
            pathReach = reach;
            if (reach && !futile(t, true)) {
                mode = Mode.CHASE; // a way in: go
                holdSpot = null;
                cover = false;
                return;
            }
            Node end = p == null ? null : p.getEndNode();
            if (end != null && !reach) {
                exit = end.asBlockPos();
            }
            decide(t);
            if (mode != Mode.HOLD) {
                cover = false;
                return;
            }
        }
        goToHoldSpot(t, 1.1);
    }

    /** PROPOSED: how far a fighter waiting on a monster it cannot get at looks for an intruder to turn to instead. */
    static final double INTRUDER_RANGE = 16;

    /** The nearest enemy player or enemy soldier in sight within {@link #INTRUDER_RANGE} (one it has not given up on). */
    private LivingEntity intruderInSight() {
        LivingEntity best = null;
        double bestD = INTRUDER_RANGE * INTRUDER_RANGE;
        for (LivingEntity e : npc.level().getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(INTRUDER_RANGE),
                e -> e.isAlive() && !ignores(e) && (e instanceof net.minecraft.server.level.ServerPlayer p && CombatRules.isEnemyPlayer(npc, p)
                        || e instanceof FactionNpc o && com.factionclash.campaign.Diplomacy.hostile(npc.getServer(), npc.getFaction(), o.getFaction())))) {
            double d = npc.distanceToSqr(e);
            if (d < bestD && npc.getSensing().hasLineOfSight(e)) {
                best = e;
                bestD = d;
            }
        }
        return best;
    }

    /**
     * Keeps to the waiting spot: picks a new one when the target moved or came too close (at most once a second, at
     * once when within its reach), walks there, stops there.
     */
    private void goToHoldSpot(LivingEntity t, double speed) {
        double hd = horizontal(npc, t);
        boolean moved = holdFrom == null || t.position().distanceToSqr(holdFrom) > 2.0 * 2.0;
        ++holdAge;
        if (holdSpot == null && holdAge > 20 || moved && holdAge > 20 || hd < HOLD_MIN - 0.3 && holdAge > 5 || holdAge > 200) {
            pickHoldSpot(t);
        }
        if (holdSpot == null) {
            return;
        }
        boolean there = npc.blockPosition().equals(holdSpot) || npc.position().distanceToSqr(Vec3.atBottomCenterOf(holdSpot)) < 0.8 * 0.8;
        if (there) {
            npc.getNavigation().stop();
        } else if (npc.getNavigation().isDone() && near(holdSpot)) {
            stepTo(holdSpot, speed);
        } else if (npc.getNavigation().isDone() && ++holdWalks % 10 == 1) {
            Path p = npc.getNavigation().createPath(holdSpot, 0);
            if (p != null && p.canReach()) {
                npc.getNavigation().moveTo(p, speed);
            } else {
                holdSpot = null;
            }
        }
    }

    private int holdWalks;

    /**
     * A spot out of the target's reach to wait at: where it stands if that is already fine, else on a ring around the
     * target, nearest the way out (the end of the path toward it), apart from the spots other fighters already
     * hold; an archer's within its sight. Checked with at most three path searches.
     */
    private void pickHoldSpot(LivingEntity t) {
        holdAge = 0;
        holdFrom = t.position();
        double hd = horizontal(npc, t);
        List<BlockPos> others = othersHoldSpots();
        if (hd >= HOLD_MIN && hd <= (archer ? 14 : HOLD_MAX) && npc.onGround() && !crowded(others, npc.blockPosition())
                && (!archer || npc.getSensing().hasLineOfSight(t)) && (!cover || !npc.getSensing().hasLineOfSight(t))) {
            holdSpot = npc.blockPosition();
            return;
        }
        BlockPos anchor = exit != null ? exit : npc.blockPosition();
        List<BlockPos> spots = new ArrayList<>();
        Map<BlockPos, Double> score = new HashMap<>();
        for (double ring : archer ? new double[]{ARCHER_RING} : cover ? new double[]{HOLD_RING, COVER_RING} : new double[]{HOLD_RING}) {
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                int x = (int) Math.floor(t.getX() + Math.cos(a) * ring), z = (int) Math.floor(t.getZ() + Math.sin(a) * ring);
                for (int dy : new int[]{0, 1, -1, 2, -2}) {
                    BlockPos f = new BlockPos(x, anchor.getY() + dy, z);
                    if (NpcNavigator.isSafeStandingSpot(level(), npc, f)) {
                        double dx = f.getX() + 0.5 - t.getX(), dz = f.getZ() + 0.5 - t.getZ();
                        if (dx * dx + dz * dz >= HOLD_MIN * HOLD_MIN && !score.containsKey(f)) {
                            // nearest the way out, apart from the others; an archer in sight of the target, a fighter
                            // being shot at out of it (or at least farther off)
                            double v = f.distSqr(anchor) + (crowded(others, f) ? 400 : 0);
                            if (archer && !sight(f, t)) {
                                v += 900;
                            } else if (cover) {
                                v += (sight(f, t) ? 900 : 0) - (dx * dx + dz * dz);
                            }
                            spots.add(f);
                            score.put(f, v);
                        }
                        break;
                    }
                }
            }
        }
        spots.sort(Comparator.comparingDouble(score::get));
        for (int i = 0; i < Math.min(3, spots.size()); i++) {
            Path p = npc.getNavigation().createPath(spots.get(i), 0);
            if (p != null && p.canReach()) {
                holdSpot = spots.get(i);
                npc.getNavigation().moveTo(p, 1.1);
                return;
            }
        }
        holdSpot = null;
        if (hd < HOLD_MIN || cover) {
            Vec3 away = DefaultRandomPos.getPosAway(npc, cover ? 12 : 8, 4, t.position());
            if (away != null) {
                holdSpot = BlockPos.containing(away);
                npc.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
            }
        }
    }

    /** The spots other fighters of its faction nearby wait at. */
    private List<BlockPos> othersHoldSpots() {
        List<BlockPos> out = new ArrayList<>();
        for (FactionNpc o : npc.level().getEntitiesOfClass(FactionNpc.class, npc.getBoundingBox().inflate(16),
                o -> o != npc && o.isAlive() && o.getFaction() == npc.getFaction())) {
            BlockPos s = o.pursuit().holdSpot();
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    /** Another fighter of its faction already waits within two blocks of {@code p}. */
    private static boolean crowded(List<BlockPos> others, BlockPos p) {
        for (BlockPos s : others) {
            if (s.distSqr(p) <= 4) {
                return true;
            }
        }
        return false;
    }

    private boolean sight(BlockPos feet, LivingEntity t) {
        Vec3 eye = new Vec3(feet.getX() + 0.5, feet.getY() + npc.getEyeHeight(), feet.getZ() + 0.5);
        return npc.level().clip(new ClipContext(eye, t.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc))
                .getType() == HitResult.Type.MISS;
    }

    // ------------------------------------------------------------------ archers

    private boolean rangedReachable = true;

    /**
     * An archer that would walk toward a target it cannot see or is out of range of: toward it if there is a way,
     * otherwise to a spot out of the target's reach (in sight of it if it can), never up to the fence. A path check
     * every {@link #RECHECK} ticks.
     */
    public void approachRanged(LivingEntity t) {
        if (--recheck <= 0) {
            recheck = RECHECK;
            Path p = pathTo(t);
            rangedReachable = p != null && p.canReach();
            if (!rangedReachable) {
                Node end = p == null ? null : p.getEndNode();
                exit = end == null ? npc.blockPosition() : end.asBlockPos();
                if (mode != Mode.HOLD) {
                    holds++;
                    holdSpot = null;
                    holdAge = 1000;
                }
                mode = Mode.HOLD;
            } else {
                mode = Mode.CHASE;
                holdSpot = null;
            }
        }
        if (rangedReachable) {
            npc.getNavigation().moveTo(t, 1.0);
            return;
        }
        engagedAt = npc.tickCount;
        goToHoldSpot(t, 1.0);
    }

    /** Whether an archer's target is out of reach for walking (as last checked). */
    public boolean rangedHolding() {
        return !rangedReachable;
    }
}
