package com.factionclash.npc;

import com.factionclash.logic.StuckRecovery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Reliable point-to-point travel: long trips are split into surface waypoints, lack of progress triggers a
 * repath and then the stuck-recovery teleport (by default up to 4 blocks across, 2 up/down, to a validated safe spot),
 * and repeated failure is reported instead of resetting forever (spec 13).
 */
public final class NpcNavigator {
    public enum Result { ARRIVED, MOVING, FAILED }

    private static final int LONG_TRIP = 40;
    private static final int WAYPOINT_STEP = 24;
    private static final int MAX_FAILURES = 6;

    private final FactionNpc npc;
    private BlockPos target;
    private double reach;
    private int repathTimer;
    private int progressTimer;
    private int stuckChecks;
    private int failures;
    /** Where the current run of failures began: switching destinations while stuck there keeps the count. */
    private BlockPos failAnchor;
    /** Watchdog: where the NPC has been travelling without getting 3 blocks away, and for how many ticks. */
    private BlockPos stallAnchor;
    private int stallTicks;
    /** PROPOSED: travelling this long without leaving a 3-block radius, whatever the destinations, is a failure. */
    private static final int STALL_TICKS = 600;
    private Vec3 lastCheck = Vec3.ZERO;
    public int recoveries;
    private String lastProblem = "";
    private int nextExitSearch;
    /** Closest distance to the target reached so far, and ticks since it last improved (progress, not motion). */
    private double bestDist;
    private int sinceBest;
    private int lastTeleport = -1000;
    /**
     * The last path found leads nowhere (a partial path that ends where the NPC stands): the navigation is "done" at
     * once, and without this the next search would run on the very next tick, tick after tick, for as long as the
     * NPC is stuck. It waits for the repath timer instead.
     */
    private boolean deadEndPath;
    /** A point off to one side the NPC is walking to, rounding a pit that the way to its destination leads into. */
    private BlockPos detour;
    /** PROPOSED: a waypoint whose ground lies this far below both ends of the trip is in a pit or ravine. */
    static final int PIT_DEPTH = 8;
    /** PROPOSED: ticks without getting at least a block closer before the trip counts as failing (pacing back and forth). */
    private static final int NO_PROGRESS_TICKS = 300;

    /**
     * Spots a digging-out NPC retreated from (the tip of a bridge it ran out of blocks on, a ledge with no way on):
     * it does not step back onto them, so it walks back to firm ground instead of pacing to the same tip. Forgotten
     * on arrival or a while after the last one.
     */
    private final java.util.Set<BlockPos> deadEnds = new java.util.HashSet<>();
    private int deadEndAt;

    public NpcNavigator(FactionNpc npc) {
        this.npc = npc;
    }

    public void deadEnd(BlockPos p) {
        if (deadEnds.size() >= 64) {
            deadEnds.clear();
        }
        deadEnds.add(p.immutable());
        deadEndAt = npc.tickCount;
    }

    /** The last few spots a digging-out NPC stepped off (it does not pace back onto them). */
    private final java.util.ArrayDeque<BlockPos> recentSpots = new java.util.ArrayDeque<>();

    public void leftSpot(BlockPos p) {
        if (recentSpots.size() >= 6) {
            recentSpots.removeFirst();
        }
        recentSpots.addLast(p.immutable());
        deadEndAt = npc.tickCount;
    }

    public boolean recentlyLeft(BlockPos p) {
        return recentSpots.contains(p);
    }

    public boolean isDeadEnd(BlockPos p) {
        if (npc.tickCount - deadEndAt > 12000) {
            deadEnds.clear();
            recentSpots.clear();
        }
        return deadEnds.contains(p);
    }

    public BlockPos target() {
        return target;
    }

    public String lastProblem() {
        return lastProblem;
    }

    /** Ticks of the current trip without getting at least a block closer to the destination. */
    public int ticksWithoutProgress() {
        return target == null ? 0 : sinceBest;
    }

    public void reset() {
        target = null;
        detour = null;
        failures = 0;
        failAnchor = null;
        stuckChecks = 0;
        repathTimer = 0;
        npc.getNavigation().stop();
    }

    public static boolean within(FactionNpc npc, BlockPos pos, double reach) {
        double dx = npc.getX() - (pos.getX() + 0.5);
        double dy = npc.getY() - pos.getY();
        double dz = npc.getZ() - (pos.getZ() + 0.5);
        // a precise target (a stair, a gate cell) is only reached on its own level, not from the block above it
        double vertical = reach < 0.8 ? 0.6 : Math.max(2.5, reach);
        return dx * dx + dz * dz <= reach * reach && Math.abs(dy) <= vertical;
    }

    public Result travel(BlockPos pos, double reach, double speed) {
        if (target == null || !target.equals(pos)) {
            target = pos.immutable();
            this.reach = reach;
            if (failAnchor == null || !npc.blockPosition().closerThan(failAnchor, 3)) {
                // a fresh start somewhere else; but a job flipping between destinations (stairs, tunnel face) while
                // the NPC stays stuck in the same spot must not wipe the count, or it never gives up and digs out
                failures = 0;
                failAnchor = null;
            }
            stuckChecks = 0;
            repathTimer = 0;
            detour = null;
            lastCheck = npc.position();
            progressTimer = 0;
            bestDist = Double.MAX_VALUE;
            sinceBest = 0;
        }
        if (within(npc, target, this.reach)) {
            npc.getNavigation().stop();
            stallTicks = 0;
            lastProblem = "";
            deadEnds.clear();
            recentSpots.clear();
            return Result.ARRIVED;
        }
        if (failures >= MAX_FAILURES) {
            return Result.FAILED;
        }
        if (stallAnchor == null || !npc.blockPosition().closerThan(stallAnchor, 3)) {
            stallAnchor = npc.blockPosition();
            stallTicks = 0;
        } else if (++stallTicks >= STALL_TICKS) {
            // swinging between destinations at a boundary (stairs / tunnel, gate / yard): every leg looks fine, but
            // the NPC gets nowhere. Treat it as a dead end so the job digs or reroutes.
            stallTicks = 0;
            fail(MAX_FAILURES);
            lastProblem = "going back and forth";
            return Result.FAILED;
        }
        if (npc.getNavigation().isDone() && !deadEndPath || --repathTimer <= 0) {
            repath(speed);
        }
        double dist = npc.position().distanceTo(Vec3.atBottomCenterOf(target));
        if (dist < bestDist - 1.0 && npc.tickCount - lastTeleport > 40) {
            bestDist = dist;
            sinceBest = 0;
        } else if (dist < bestDist - 1.0) {
            bestDist = dist; // a recovery hop is not walking progress: an NPC teleported back and forth stays "stuck"
            sinceBest++;
        } else if (++sinceBest >= NO_PROGRESS_TICKS) {
            // moving, but not getting any closer (partial path to a cliff edge, pacing between two dead ends)
            sinceBest = 0;
            bestDist = dist;
            fail(2);
            lastProblem = "no progress";
            repath(speed);
        }
        if (++progressTimer >= 20) {
            progressTimer = 0;
            double moved = npc.position().distanceTo(lastCheck);
            lastCheck = npc.position();
            if (moved < 0.35) {
                stuckChecks++;
                if (stuckChecks == 2) {
                    repath(speed);
                } else if (stuckChecks >= 3) {
                    stuckChecks = 0;
                    if (!tryRecoveryTeleport()) {
                        fail(1);
                        lastProblem = "no route";
                    }
                    repath(speed);
                }
            } else {
                stuckChecks = 0;
            }
        }
        return Result.MOVING;
    }

    /**
     * The way in and out of a settlement is through a gate. Coming home from outside the walls (or from a cave
     * under the base), head for the nearest gate first, then on to the destination inside; leaving for somewhere
     * outside, head for the gate that fits the trip best rather than into the wall facing the destination.
     */
    private BlockPos viaGate(BlockPos dest) {
        com.factionclash.campaign.BaseState home = npc.base();
        if (home == null || home.destroyed || !home.isSettlement()) {
            return dest;
        }
        boolean destIn = insideWalls(home, dest), npcIn = insideWalls(home, npc.blockPosition());
        // leaving only counts for a destination beyond the walls, not one below the base (the mine, a cave)
        boolean destBeyond = Math.abs(dest.getX() - home.center.getX()) > com.factionclash.structure.BaseLayout.HALF
                || Math.abs(dest.getZ() - home.center.getZ()) > com.factionclash.structure.BaseLayout.HALF;
        if (!(destIn && !npcIn) && !(npcIn && destBeyond)) {
            return dest;
        }
        BlockPos best = null;
        double bestD = Double.MAX_VALUE, bestHere = Double.MAX_VALUE;
        for (String key : new String[]{com.factionclash.campaign.BaseState.GATE_WEST, com.factionclash.campaign.BaseState.GATE_EAST,
                com.factionclash.campaign.BaseState.GATE_SOUTH}) {
            BlockPos g = home.point(key);
            if (g == null) {
                continue;
            }
            BlockPos out = g.offset(Integer.signum(g.getX() - home.center.getX()) * 2, 0, Integer.signum(g.getZ() - home.center.getZ()) * 2);
            double here = out.distSqr(npc.blockPosition());
            // coming in: the nearest gate; going out: the shortest walk through a gate to the destination
            double d = destIn ? here : Math.sqrt(here) + Math.sqrt(out.distSqr(dest));
            if (d < bestD) {
                bestD = d;
                best = out;
                bestHere = here;
            }
        }
        return best == null || bestHere <= 4 ? dest : best;
    }

    /** In or right around its own settlement's walls box, at its floor level (so never down in a cave). */
    private boolean atHome() {
        com.factionclash.campaign.BaseState home = npc.base();
        if (home == null || home.destroyed || !home.isSettlement()) {
            return false;
        }
        int h = com.factionclash.campaign.BaseZones.half(home) + 3;
        BlockPos p = npc.blockPosition();
        return Math.abs(p.getX() - home.center.getX()) <= h && Math.abs(p.getZ() - home.center.getZ()) <= h
                && p.getY() >= home.center.getY() - 2;
    }

    /** Where travelling to {@code dest} really heads first (the nearest gate when coming home from outside). */
    public BlockPos routeTarget(BlockPos dest) {
        return viaGate(dest);
    }

    private static boolean insideWalls(com.factionclash.campaign.BaseState b, BlockPos p) {
        int h = com.factionclash.structure.BaseLayout.HALF - 1;
        return Math.abs(p.getX() - b.center.getX()) <= h && Math.abs(p.getZ() - b.center.getZ()) <= h && p.getY() >= b.center.getY() - 3;
    }

    private void repath(double speed) {
        BlockPos dest = viaGate(target);
        BlockPos goal = dest;
        double hd = Math.sqrt(Mth.square(dest.getX() + 0.5 - npc.getX()) + Mth.square(dest.getZ() + 0.5 - npc.getZ()));
        int surface = npc.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, npc.getBlockX(), npc.getBlockZ());
        // (under a roof at home - a gate's arch, a house - is not underground: leaving through a gate for somewhere
        // far, the direct search found only a partial path back inside, and the NPC paced in the gateway)
        boolean underground = npc.getY() < surface - 3 && !atHome();
        if (hd > LONG_TRIP && !underground) {
            goal = waypoint(dest, hd);
        }
        if (detour != null && (underground || npc.blockPosition().closerThan(detour, 3))) {
            detour = null;
        }
        Path path = search(detour != null ? detour : goal, underground);
        if (detour == null && !underground && hd > 16 && intoPit(path, dest) && npc.tickCount >= nextExitSearch) {
            nextExitSearch = npc.tickCount + 100;
            // the nearest the search got is the floor of a pit or ravine between here and there (a partial path down
            // its slope): nobody climbs back out of there. Round it instead, by a point off to one side or the other.
            Path around = aroundPit(dest, hd);
            if (around != null) {
                path = around;
                detour = around.getTarget();
            }
        }
        if ((path == null || !path.canReach()) && underground && (hd > 16 || dest.getY() - npc.getY() > 8) && npc.tickCount >= nextExitSearch) {
            nextExitSearch = npc.tickCount + 200; // path searches are expensive: bounded per NPC
            // No route from down here (lost in a cave or ravine): climb out first, toward the nearest reachable
            // surface point. A miner in its own tunnels has a full route and never takes this detour.
            BlockPos exit = surfaceExit(dest, hd);
            if (exit != null) {
                Path out = search(exit, true);
                if (out != null) {
                    path = out;
                }
            }
        }
        deadEndPath = false;
        if (path == null && !npc.onGround() && !npc.isInLiquid()) {
            // vanilla plans no path in mid-air (each step down a staircase is a small drop): not a failure, look
            // again as soon as it lands
            repathTimer = 2;
            return;
        }
        if (path == null) {
            fail(1);
            lastProblem = "no path";
            repathTimer = 20;
            return;
        }
        npc.getNavigation().moveTo(path, speed);
        deadEndPath = !path.canReach() && path.getNodeCount() <= 1;
        repathTimer = deadEndPath ? 20 : 100;
    }

    private BlockPos waypoint(BlockPos dest, double hd) {
        double ux = (dest.getX() + 0.5 - npc.getX()) / hd, uz = (dest.getZ() + 0.5 - npc.getZ()) / hd;
        com.factionclash.campaign.BaseState home = npc.base();
        boolean avoid = home != null && !home.destroyed && home.isSettlement() && !insideWalls(home, npc.blockPosition())
                && !insideWalls(home, dest);
        // and a trip that neither starts nor ends at a pillager outpost or woodland mansion the faction knows of
        // goes around it rather than straight through
        com.factionclash.campaign.CampaignData cd = npc.getServer() == null ? null : com.factionclash.campaign.CampaignData.get(npc.getServer());
        com.factionclash.campaign.FactionState known = cd == null ? null : cd.factions.get(npc.getFaction());
        int danger = com.factionclash.campaign.BaseZones.DANGER_RADIUS;
        boolean shun = known != null && !com.factionclash.campaign.BaseZones.nearKnownDanger(known, dest, danger)
                && !com.factionclash.campaign.BaseZones.nearKnownDanger(known, npc.blockPosition(), danger);
        int wx = 0, wz = 0, bx = 0, bz = 0, bestGap = -1;
        BlockPos pit = null;
        // the bottom of a pit or ravine is no waypoint: the partial path toward it leads down a slope into it, and
        // nobody climbs back out of there
        int pitBelow = Math.min(npc.getBlockY(), dest.getY()) - PIT_DEPTH;
        // passing by its own settlement, the waypoint goes around the walls rather than back in through a gate
        for (double turn : new double[]{0, 0.5, -0.5, 1.0, -1.0, 1.5, -1.5}) {
            double c = Math.cos(turn), sn = Math.sin(turn);
            wx = Mth.floor(npc.getX() + (ux * c - uz * sn) * WAYPOINT_STEP);
            wz = Mth.floor(npc.getZ() + (ux * sn + uz * c) * WAYPOINT_STEP);
            boolean walls = !avoid || Math.abs(wx - home.center.getX()) > com.factionclash.structure.BaseLayout.HALF + 2
                    || Math.abs(wz - home.center.getZ()) > com.factionclash.structure.BaseLayout.HALF + 2;
            if (!walls) {
                continue;
            }
            if (shun) {
                // a detour around dangerous ground stays on the campaign map: walking to the waypoint generates its chunk
                boolean onMap = turn == 0 || com.factionclash.campaign.MapBounds.inside(cd, wx, wz, 0);
                int gap = com.factionclash.campaign.BaseZones.dangerGap(known, wx, wz);
                if (!onMap || gap <= danger) {
                    if (onMap && gap > bestGap) {
                        bestGap = gap;
                        bx = wx;
                        bz = wz;
                    }
                    continue;
                }
            }
            BlockPos top = surface(wx, wz);
            if (top.getY() >= pitBelow) {
                return top;
            }
            if (pit == null) {
                pit = top; // every turn may lead down (a wide valley): then the first one after all
            }
        }
        if (pit != null) {
            return pit;
        }
        if (bestGap >= 0) {
            // no turn keeps out of the dangerous ground: the one that stays farthest from it (still clear of the walls)
            return surface(bx, bz);
        }
        return surface(wx, wz);
    }

    /** The surface at a column. Heightmaps of unloaded chunks read as the world floor: its chunk is loaded first. */
    private BlockPos surface(int x, int z) {
        npc.level().getChunk(x >> 4, z >> 4);
        return npc.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
    }

    private void fail(int n) {
        if (failures == 0) {
            failAnchor = npc.blockPosition();
        }
        failures += n;
    }

    /**
     * A vanilla path search. Underground (a mine, a cave) a cell beside water costs no more than any other: vanilla
     * prices it like a river bank (8 extra), and a mine's tunnels sealed against an aquifer then cost more than a
     * long way round over the surface, so the search went up the stairs and across the top, or gave up partway.
     * On the surface the vanilla price stays (a bank one could slip off).
     */
    private Path search(BlockPos goal, boolean underground) {
        npc.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER_BORDER,
                underground ? 0.0F : net.minecraft.world.level.pathfinder.PathType.WATER_BORDER.getMalus());
        return npc.getNavigation().createPath(goal, 1);
    }

    /** Whether a walking route toward {@code dest} exists from here (its first surface waypoint on long trips). */
    public boolean canWalkToward(BlockPos dest) {
        BlockPos d = viaGate(dest);
        double hd = Math.sqrt(Mth.square(d.getX() + 0.5 - npc.getX()) + Mth.square(d.getZ() + 0.5 - npc.getZ()));
        int surface = npc.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, npc.getBlockX(), npc.getBlockZ());
        Path p = search(hd > LONG_TRIP ? waypoint(d, hd) : d, npc.getY() < surface - 3 && !atHome());
        return p != null && p.canReach();
    }

    /** A partial path whose end lies far below both this NPC and the destination: it leads down into a pit. */
    private boolean intoPit(Path path, BlockPos dest) {
        return path != null && !path.canReach() && path.getEndNode() != null
                && path.getEndNode().y < Math.min(npc.getBlockY(), dest.getY()) - PIT_DEPTH;
    }

    /**
     * A path to a surface point off to one side of the way to {@code dest} (turned 30, 60 or 85 degrees, left first),
     * that is not itself in a pit and that a search reaches without leading down into one; null if there is none.
     */
    private Path aroundPit(BlockPos dest, double hd) {
        double ux = (dest.getX() + 0.5 - npc.getX()) / hd, uz = (dest.getZ() + 0.5 - npc.getZ()) / hd;
        double reach = Math.min(hd, WAYPOINT_STEP);
        int pitBelow = Math.min(npc.getBlockY(), dest.getY()) - PIT_DEPTH;
        for (double turn : new double[]{0.5, -0.5, 1.0, -1.0, 1.5, -1.5}) {
            double c = Math.cos(turn), sn = Math.sin(turn);
            BlockPos top = surface(Mth.floor(npc.getX() + (ux * c - uz * sn) * reach), Mth.floor(npc.getZ() + (ux * sn + uz * c) * reach));
            if (top.getY() < pitBelow) {
                continue;
            }
            Path p = search(top, false);
            if (p != null && (p.canReach() || !intoPit(p, dest))) {
                return p;
            }
        }
        return null;
    }

    /** Finds a surface position near an underground NPC (toward its target first) that a path can reach. */
    private BlockPos surfaceExit(BlockPos dest, double hd) {
        double tx = (dest.getX() + 0.5 - npc.getX()) / hd, tz = (dest.getZ() + 0.5 - npc.getZ()) / hd;
        double[][] dirs = {{tx, tz}, {-tz, tx}, {tz, -tx}, {-tx, -tz}};
        for (int dist : new int[]{8, 16, 28}) {
            for (double[] d : dirs) {
                int x = Mth.floor(npc.getX() + d[0] * dist), z = Mth.floor(npc.getZ() + d[1] * dist);
                npc.level().getChunk(x >> 4, z >> 4);
                BlockPos top = npc.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                Path p = search(top, true);
                if (p != null && p.canReach()) {
                    return top;
                }
            }
        }
        return null;
    }

    /** Local unstick: move to a validated safe spot within the configured range (default 4 across, 2 up/down). */
    public boolean tryRecoveryTeleport() {
        if (!(npc.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 goal;
        Path path = npc.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            Node n = path.getNextNode();
            goal = new Vec3(n.x + 0.5, n.y, n.z + 0.5);
        } else if (target != null) {
            goal = Vec3.atBottomCenterOf(target);
        } else {
            return false;
        }
        BlockPos here = npc.blockPosition();
        StuckRecovery.Spot spot = StuckRecovery.choose(here.getX(), here.getY(), here.getZ(), goal.x, goal.y, goal.z,
                com.factionclash.config.FCConfig.UNSTUCK_HORIZONTAL.get(), com.factionclash.config.FCConfig.UNSTUCK_VERTICAL.get(),
                (x, y, z) -> isSafeStandingSpot(level, npc, new BlockPos(x, y, z)));
        if (spot == null) {
            return false;
        }
        npc.getNavigation().stop();
        npc.teleportTo(spot.x() + 0.5, spot.y(), spot.z() + 0.5);
        recoveries++;
        lastTeleport = npc.tickCount;
        return true;
    }

    public static boolean isSafeStandingSpot(ServerLevel level, FactionNpc npc, BlockPos feet) {
        if (!level.isLoaded(feet)) {
            return false;
        }
        BlockState below = level.getBlockState(feet.below());
        if (below.getCollisionShape(level, feet.below()).isEmpty() || isDangerous(below) || !below.getFluidState().isEmpty()
                || below.is(BlockTags.LEAVES) || DigOut.perched(level, feet)) {
            return false; // never onto a tree canopy or a tall trunk: nobody walks down from there
        }
        for (int dy = 0; dy < 2; dy++) {
            BlockPos p = feet.above(dy);
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty() || !s.getFluidState().isEmpty() || isDangerous(s)) {
                return false;
            }
        }
        AABB box = npc.getDimensions(npc.getPose()).makeBoundingBox(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        return level.noCollision(npc, box);
    }

    public static boolean isDangerous(BlockState s) {
        return s.is(BlockTags.FIRE) || s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS)
                || s.is(BlockTags.CAMPFIRES) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.POWDER_SNOW)
                || s.is(Blocks.WITHER_ROSE) || s.is(Blocks.POINTED_DRIPSTONE);
    }
}
