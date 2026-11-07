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
 * repath and then the small stuck-recovery teleport (max 2 blocks across, 1 up/down, validated safe spot),
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
    private Vec3 lastCheck = Vec3.ZERO;
    public int recoveries;
    private String lastProblem = "";
    private int nextExitSearch;
    /** Closest distance to the target reached so far, and ticks since it last improved (progress, not motion). */
    private double bestDist;
    private int sinceBest;
    /** PROPOSED: ticks without getting at least a block closer before the trip counts as failing (pacing back and forth). */
    private static final int NO_PROGRESS_TICKS = 300;

    public NpcNavigator(FactionNpc npc) {
        this.npc = npc;
    }

    public BlockPos target() {
        return target;
    }

    public String lastProblem() {
        return lastProblem;
    }

    public void reset() {
        target = null;
        failures = 0;
        stuckChecks = 0;
        repathTimer = 0;
        npc.getNavigation().stop();
    }

    public static boolean within(FactionNpc npc, BlockPos pos, double reach) {
        double dx = npc.getX() - (pos.getX() + 0.5);
        double dy = npc.getY() - pos.getY();
        double dz = npc.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dz * dz <= reach * reach && Math.abs(dy) <= Math.max(2.5, reach);
    }

    public Result travel(BlockPos pos, double reach, double speed) {
        if (target == null || !target.equals(pos)) {
            target = pos.immutable();
            this.reach = reach;
            failures = 0;
            stuckChecks = 0;
            repathTimer = 0;
            lastCheck = npc.position();
            progressTimer = 0;
            bestDist = Double.MAX_VALUE;
            sinceBest = 0;
        }
        if (within(npc, target, this.reach)) {
            npc.getNavigation().stop();
            return Result.ARRIVED;
        }
        if (failures >= MAX_FAILURES) {
            return Result.FAILED;
        }
        if (npc.getNavigation().isDone() || --repathTimer <= 0) {
            repath(speed);
        }
        double dist = npc.position().distanceTo(Vec3.atBottomCenterOf(target));
        if (dist < bestDist - 1.0) {
            bestDist = dist;
            sinceBest = 0;
        } else if (++sinceBest >= NO_PROGRESS_TICKS) {
            // moving, but not getting any closer (partial path to a cliff edge, pacing between two dead ends)
            sinceBest = 0;
            bestDist = dist;
            failures += 2;
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
                        failures++;
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

    private void repath(double speed) {
        BlockPos goal = target;
        double hd = Math.sqrt(Mth.square(target.getX() + 0.5 - npc.getX()) + Mth.square(target.getZ() + 0.5 - npc.getZ()));
        int surface = npc.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, npc.getBlockX(), npc.getBlockZ());
        boolean underground = npc.getY() < surface - 3;
        if (hd > LONG_TRIP && !underground) {
            double f = WAYPOINT_STEP / hd;
            int wx = Mth.floor(npc.getX() + (target.getX() + 0.5 - npc.getX()) * f);
            int wz = Mth.floor(npc.getZ() + (target.getZ() + 0.5 - npc.getZ()) * f);
            // Heightmaps of unloaded chunks read as the world floor, so load (or generate) the waypoint's chunk first.
            npc.level().getChunk(wx >> 4, wz >> 4);
            goal = npc.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(wx, 0, wz));
        }
        Path path = npc.getNavigation().createPath(goal, 1);
        if ((path == null || !path.canReach()) && underground && hd > 16 && npc.tickCount >= nextExitSearch) {
            nextExitSearch = npc.tickCount + 200; // path searches are expensive: bounded per NPC
            // No route from down here (lost in a cave or ravine): climb out first, toward the nearest reachable
            // surface point. A miner in its own tunnels has a full route and never takes this detour.
            BlockPos exit = surfaceExit(hd);
            if (exit != null) {
                Path out = npc.getNavigation().createPath(exit, 1);
                if (out != null) {
                    path = out;
                }
            }
        }
        if (path == null) {
            failures++;
            lastProblem = "no path";
            repathTimer = 20;
            return;
        }
        npc.getNavigation().moveTo(path, speed);
        repathTimer = 100;
    }

    /** Finds a surface position near an underground NPC (toward its target first) that a path can reach. */
    private BlockPos surfaceExit(double hd) {
        double tx = (target.getX() + 0.5 - npc.getX()) / hd, tz = (target.getZ() + 0.5 - npc.getZ()) / hd;
        double[][] dirs = {{tx, tz}, {-tz, tx}, {tz, -tx}, {-tx, -tz}};
        for (int dist : new int[]{8, 16, 28}) {
            for (double[] d : dirs) {
                int x = Mth.floor(npc.getX() + d[0] * dist), z = Mth.floor(npc.getZ() + d[1] * dist);
                npc.level().getChunk(x >> 4, z >> 4);
                BlockPos top = npc.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                Path p = npc.getNavigation().createPath(top, 1);
                if (p != null && p.canReach()) {
                    return top;
                }
            }
        }
        return null;
    }

    /** Local unstick: move to a validated safe spot within 2 blocks horizontally and 1 vertically. */
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
                (x, y, z) -> isSafeStandingSpot(level, npc, new BlockPos(x, y, z)));
        if (spot == null) {
            return false;
        }
        npc.getNavigation().stop();
        npc.teleportTo(spot.x() + 0.5, spot.y(), spot.z() + 0.5);
        recoveries++;
        return true;
    }

    public static boolean isSafeStandingSpot(ServerLevel level, FactionNpc npc, BlockPos feet) {
        if (!level.isLoaded(feet)) {
            return false;
        }
        BlockState below = level.getBlockState(feet.below());
        if (below.getCollisionShape(level, feet.below()).isEmpty() || isDangerous(below) || !below.getFluidState().isEmpty()) {
            return false;
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
