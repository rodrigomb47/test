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
import java.util.List;

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
    private int broken;
    private int alarmIn;
    private boolean archer;
    /** Last tick (the NPC's tickCount) it was actively going for or watching its target: keeps the target from going stale. */
    public int engagedAt = Integer.MIN_VALUE / 2;
    /** Counters for tests: planner runs, times it started waiting, blocks broken and built. */
    public int plans, holds, breaks, pillars;

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
    }

    /** Ties the state to this target; a different one starts afresh. */
    public void bind(LivingEntity t, boolean ranged) {
        if (t != target) {
            reset();
            target = t;
        }
        archer = ranged;
    }

    private ServerLevel level() {
        return (ServerLevel) npc.level();
    }

    private static double horizontal(LivingEntity a, LivingEntity b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Whether it can hit the target from where it stands right now. */
    public boolean canStrikeNow(LivingEntity t) {
        return npc.isWithinMeleeAttackRange(t) && npc.getSensing().hasLineOfSight(t);
    }

    // ------------------------------------------------------------------ deciding

    /**
     * The path the attack goal just searched toward the target does not reach it: plan a way (break, build, undermine)
     * or wait out of reach. Far targets are walked toward as before (the partial path), until they are near enough to plan.
     */
    public void unreachable(LivingEntity t, Path partial) {
        Node end = partial == null ? null : partial.getEndNode();
        exit = end == null ? npc.blockPosition() : end.asBlockPos();
        if (horizontal(npc, t) > PLAN_RANGE || Math.abs(t.getY() - npc.getY()) > PLAN_HEIGHT) {
            return;
        }
        decide(t);
    }

    /** Close and not landing a blow for a while although the path says it can reach: treat it as unreachable. */
    public void watchStuck(LivingEntity t, boolean struckOrCanStrike) {
        if (mode != Mode.CHASE || struckOrCanStrike || horizontal(npc, t) > 3.0) {
            closeNoHit = 0;
            return;
        }
        if (++closeNoHit >= STUCK) {
            closeNoHit = 0;
            if (exit == null) {
                exit = npc.blockPosition();
            }
            decide(t);
        }
    }

    private void decide(LivingEntity t) {
        if (canStrikeNow(t)) {
            mode = Mode.CHASE;
            return;
        }
        if (!npc.onGround() && !npc.isInWater()) {
            return; // mid-jump: its cell is not where it stands; the next look decides
        }
        plan = null;
        if (broken < MAX_BREAKS && npc.onGround()) {
            int blocks = Math.min(MAX_PILLAR, pillarBlocks());
            plan = ReachPlanner.plan(Reach.world(level(), npc), cell(npc.blockPosition()), cell(t.blockPosition()), blocks, true, MAX_PLAN_COST);
            plans++;
        }
        if (plan != null && !plan.steps().isEmpty()) {
            int w = plan.firstWork();
            workIndex = w >= 0 ? w : plan.steps().size() - 1;
            walkTicks = 0;
            mode = Mode.WORK;
            engagedAt = npc.tickCount;
            return;
        }
        if (plan != null) {
            mode = Mode.CHASE; // it can strike from where it stands
            return;
        }
        hold(t);
    }

    private void hold(LivingEntity t) {
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
        if (npc.getRole().isCivilian()) {
            // a worker does not stand guard: it lets the target be and gets out of its way (its job takes over)
            Vec3 away = DefaultRandomPos.getPosAway(npc, 8, 4, t.position());
            if (away != null && horizontal(npc, t) < HOLD_MIN) {
                npc.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
            }
            npc.setTarget(null);
            reset();
        }
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
            if (!Reach.mayBreak(level(), b, npc) || !BlockWork.inReach(npc, b, 4.5)) {
                decide(t); // the world changed: plan again
                return;
            }
            if (breaker.tick(npc, b)) {
                breaks++;
                broken++;
                Reach.forget(level(), b);
            }
            return; // one block at a time, at a player's speed
        }
        if (step.pillar() && !pillarUp(origin)) {
            decide(t);
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

    /** A target it gave up on a little while ago (it does not pick it again until then). */
    public boolean ignores(net.minecraft.world.entity.Entity e) {
        return ignored != null && e.getUUID().equals(ignored) && npc.tickCount < ignoredUntil;
    }

    private boolean atHome() {
        com.factionclash.campaign.BaseState b = npc.base();
        return b != null && !b.destroyed && com.factionclash.campaign.BaseZones.insideWalls(b, npc.blockPosition(), 16);
    }

    private void holdTick(LivingEntity t) {
        if (++heldFor > GIVE_UP_AWAY && !atHome()) {
            ignored = t.getUUID();
            ignoredUntil = npc.tickCount + IGNORE_TICKS;
            npc.setTarget(null);
            reset();
            return;
        }
        boolean sees = npc.getSensing().hasLineOfSight(t);
        if (sees && npc.distanceTo(t) < 24) {
            engagedAt = npc.tickCount; // keeps watching: the target does not go stale while it waits in sight of it
        }
        npc.getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (--alarmIn <= 0) {
            alarmIn = ALARM_EVERY;
            Alerts.callArchers(npc, t, 32);
        }
        if (--recheck <= 0) {
            recheck = RECHECK;
            Path p = npc.getNavigation().createPath(t, 0);
            if (p != null && p.canReach()) {
                mode = Mode.CHASE; // a way in: go
                holdSpot = null;
                return;
            }
            Node end = p == null ? null : p.getEndNode();
            if (end != null) {
                exit = end.asBlockPos();
            }
            decide(t);
            if (mode != Mode.HOLD) {
                return;
            }
        }
        goToHoldSpot(t, 1.1);
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
        double ring = archer ? ARCHER_RING : HOLD_RING;
        if (hd >= HOLD_MIN && hd <= (archer ? 14 : HOLD_MAX) && npc.onGround() && !crowded(npc.blockPosition())
                && (!archer || npc.getSensing().hasLineOfSight(t))) {
            holdSpot = npc.blockPosition();
            return;
        }
        BlockPos anchor = exit != null ? exit : npc.blockPosition();
        List<BlockPos> spots = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            int x = (int) Math.floor(t.getX() + Math.cos(a) * ring), z = (int) Math.floor(t.getZ() + Math.sin(a) * ring);
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                BlockPos f = new BlockPos(x, anchor.getY() + dy, z);
                if (NpcNavigator.isSafeStandingSpot(level(), npc, f)) {
                    double dx = f.getX() + 0.5 - t.getX(), dz = f.getZ() + 0.5 - t.getZ();
                    if (dx * dx + dz * dz >= HOLD_MIN * HOLD_MIN) {
                        spots.add(f);
                    }
                    break;
                }
            }
        }
        spots.sort(Comparator.comparingDouble(f -> f.distSqr(anchor) + (crowded(f) ? 400 : 0) + (archer && !sight(f, t) ? 900 : 0)));
        for (int i = 0; i < Math.min(3, spots.size()); i++) {
            Path p = npc.getNavigation().createPath(spots.get(i), 0);
            if (p != null && p.canReach()) {
                holdSpot = spots.get(i);
                npc.getNavigation().moveTo(p, 1.1);
                return;
            }
        }
        holdSpot = null;
        if (hd < HOLD_MIN) {
            Vec3 away = DefaultRandomPos.getPosAway(npc, 8, 4, t.position());
            if (away != null) {
                holdSpot = BlockPos.containing(away);
                npc.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
            }
        }
    }

    /** Another fighter of its faction already waits within two blocks of {@code p}. */
    private boolean crowded(BlockPos p) {
        for (FactionNpc o : npc.level().getEntitiesOfClass(FactionNpc.class, npc.getBoundingBox().inflate(16),
                o -> o != npc && o.isAlive() && o.getFaction() == npc.getFaction())) {
            BlockPos s = o.pursuit().holdSpot();
            if (s != null && s.distSqr(p) <= 4) {
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
            Path p = npc.getNavigation().createPath(t, 0);
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
