package com.factionclash.hero;

import com.factionclash.combat.HeroEscapeGoal;
import com.factionclash.npc.NpcNavigator;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * An escaping hero's movement (MOBILITY_AND_WORK 7.7; task A12): its whole mobility, even where no pursuer can follow.
 * A flyer takes off toward its refuge (or the wild) as soon as it may and flies there on the travel layer with only its
 * stamina to stop it ({@link HeroTravel#escapeTakeOff}); one already in the air when the escape starts flies on from
 * there (7.6). On the ground it runs with its own navigation, whose path search offers its links for an escape (climbs,
 * jump-ups, gap leaps over risky gaps with a wide landing, drops to its safe fall, chimneys, glides: A11), a waypoint
 * within 16 blocks at a time, straight toward the destination first: so it goes over a wall or up a cliff that a
 * soldier must go round. The travel bonuses are on (an escape is travel). Nothing teleports: the stuck recovery is
 * never used to get away. It reports each second whether it has a way and moves, so a hero with no way for 3 s is
 * cornered; air poise, the downing and the boss rules still hold.
 */
public final class HeroEscapeMovement implements HeroEscapeGoal.Movement {
    /** Turns tried for a waypoint when the straight one has no path (radians). */
    private static final double[] TURNS = {0, 0.5, -0.5, 1.0, -1.0, 1.5, -1.5};
    private int repathAt, progressAt, flightTryAt;
    private Vec3 last;
    private boolean hasWay = true, moving = true;
    /** Escape flights started (tests). */
    public int flights;

    @Override
    public boolean step(HeroEntity hero, Vec3 destination) {
        HeroMovement mv = hero.movement();
        HeroTravel tr = hero.travel();
        BlockPos dest = BlockPos.containing(destination);
        // an escape that starts in the air flies on from there (7.6)
        if (!tr.flying() && mv.flying()) {
            tr.adoptFlight(dest);
        }
        if (tr.flying()) {
            NpcNavigator.Result r = tr.travel(hero.navigator(), dest, 2, HeroEscapeGoal.RUN_SPEED);
            if (r == NpcNavigator.Result.MOVING) {
                return progress(hero);
            }
        }
        if (mv.controls()) {
            return progress(hero); // a link, a glide or a jump under way: it is getting along
        }
        if (!hero.onGround() && !hero.isInLiquid()) {
            // a hop or a blow's knock-back: vanilla makes no path from the air, so it searches again once it lands
            return progress(hero) && hasWay;
        }
        if (hero.tickCount >= flightTryAt && hero.onGround() && mv.profile().flies()) {
            flightTryAt = hero.tickCount + 20;
            if (tr.escapeTakeOff(dest)) {
                flights++;
                return true;
            }
        }
        // a fresh path every second, never while its navigation walks to a link's start (that would begin it again)
        boolean repath = hero.getNavigation().isDone() || hero.tickCount >= repathAt
                && !(hero.getNavigation() instanceof HeroGroundNavigation g && g.atLink());
        if (repath) {
            repathAt = hero.tickCount + 20;
            hasWay = runToward(hero, destination);
        }
        return progress(hero) && hasWay;
    }

    /** Moving away: under half a block in a second is not (a hero in a link or the air is). */
    private boolean progress(HeroEntity hero) {
        if (hero.tickCount >= progressAt) {
            progressAt = hero.tickCount + 20;
            double moved = last == null ? 1 : hero.position().distanceTo(last);
            last = hero.position();
            moving = moved >= 0.5;
        }
        return moving;
    }

    /**
     * A waypoint within 16 blocks toward the destination, straight first and then turned, on the surface where it is
     * loaded; the first whose path (links included) gets there or at least closer is run; else a random reachable spot
     * toward it as on foot.
     */
    private boolean runToward(HeroEntity hero, Vec3 destination) {
        double dx = destination.x - hero.getX(), dz = destination.z - hero.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d <= HeroEscapeGoal.WAYPOINT_RANGE) {
            Path p = hero.getNavigation().createPath(BlockPos.containing(destination), 1);
            if (p != null && hero.getNavigation().moveTo(p, HeroEscapeGoal.RUN_SPEED)) {
                return true;
            }
        }
        double ux = d < 1e-3 ? 1 : dx / d, uz = d < 1e-3 ? 0 : dz / d;
        double here = d;
        for (double turn : TURNS) {
            double c = Math.cos(turn), s = Math.sin(turn);
            double step = Math.min(HeroEscapeGoal.WAYPOINT_RANGE, d);
            int x = Mth.floor(hero.getX() + (ux * c - uz * s) * step), z = Mth.floor(hero.getZ() + (ux * s + uz * c) * step);
            BlockPos wp = surface(hero, x, z);
            if (wp == null) {
                continue;
            }
            Path p = hero.getNavigation().createPath(wp, 1);
            if (p == null || p.getNodeCount() <= 1) {
                continue;
            }
            BlockPos end = p.getEndNode() == null ? null : p.getEndNode().asBlockPos();
            double after = end == null ? here : Math.hypot(destination.x - end.getX() - 0.5, destination.z - end.getZ() - 0.5);
            if ((p.canReach() || after < here - 2) && hero.getNavigation().moveTo(p, HeroEscapeGoal.RUN_SPEED)) {
                return true;
            }
        }
        Vec3 r = LandRandomPos.getPosTowards(hero, HeroEscapeGoal.WAYPOINT_RANGE, 7, destination);
        return r != null && hero.getNavigation().moveTo(r.x, r.y, r.z, HeroEscapeGoal.RUN_SPEED);
    }

    /** The standing cell on the surface of a loaded column (null: not loaded). */
    private static BlockPos surface(HeroEntity hero, int x, int z) {
        LevelChunk c = hero.level().getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (c == null) {
            return null;
        }
        int y = c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
        // under a roof (a test field's, a cave's) the heightmap's top is no place to run to: its own level then
        if (y > hero.getBlockY() + 12) {
            y = hero.getBlockY();
        }
        return new BlockPos(x, y, z);
    }

    @Override
    public void stop(HeroEntity hero) {
        hero.getNavigation().stop();
        repathAt = progressAt = 0;
        last = null;
        hasWay = moving = true;
    }
}
