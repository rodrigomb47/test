package com.factionclash.combat;

import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroEscape;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A hero running from a fight it lost (HEROES_V2 3.5 priority 1, 6.13; MOBILITY_AND_WORK 7.7; task A6b): while its
 * {@link HeroEscape} says it flees (an escape that is not cornered), it holds MOVE and LOOK, keeps no target and runs
 * toward its refuge (its home base, another base of its faction) or, with none, into the wild, at least 64 blocks from
 * the fight; after an escape into the wild it keeps that far off for its stay-away time. It moves through a
 * {@link Movement}: today on foot with ordinary navigation, waypoint by waypoint (no teleporting: the stuck recovery is
 * never used to get away); A12 gives a hero its superhuman mobility here (links, climbs, glides, flight). It reports
 * whether it could get away each second, so a hero with no way for 3 s is cornered (it fights back) and runs again once
 * a way opens. A stagger's hold (priority 0) stops it where it is. Nothing here reads who the pursuers are.
 */
public class HeroEscapeGoal extends Goal {
    /** How a hero moves while it escapes; A12 replaces {@link #ON_FOOT} with a hero's own mobility. */
    public interface Movement {
        /** One tick toward {@code destination}; returns whether it has a way to go (a path, progress). */
        boolean step(HeroEntity hero, Vec3 destination);

        void stop(HeroEntity hero);
    }

    /** The sprint of an escape on foot (a player's sprint is 1.3 times its walk). */
    public static final double RUN_SPEED = 1.3;
    /** A waypoint is picked within this many blocks, toward the destination. */
    public static final int WAYPOINT_RANGE = 16;

    /** On foot, by the hero's own navigation, a waypoint at a time. */
    public static final Movement ON_FOOT = new Movement() {
        @Override
        public boolean step(HeroEntity hero, Vec3 destination) {
            FootState st = FootState.of(hero);
            boolean repath = hero.getNavigation().isDone() || hero.tickCount >= st.repathAt;
            if (repath) {
                st.repathAt = hero.tickCount + 20;
                Vec3 wp = waypoint(hero, destination);
                st.hasWay = wp != null && hero.getNavigation().moveTo(wp.x, wp.y, wp.z, RUN_SPEED);
            }
            if (hero.tickCount >= st.progressAt) {
                st.progressAt = hero.tickCount + 20;
                double moved = st.last == null ? 1 : hero.position().distanceTo(st.last);
                st.last = hero.position();
                st.moving = moved >= 0.5;
            }
            return st.hasWay && st.moving;
        }

        @Override
        public void stop(HeroEntity hero) {
            hero.getNavigation().stop();
            FootState.of(hero).reset();
        }
    };

    /** The foot movement's per-hero bookkeeping (kept on the goal: one per hero). */
    private static final class FootState {
        int repathAt, progressAt;
        Vec3 last;
        boolean hasWay = true, moving = true;

        void reset() {
            repathAt = progressAt = 0;
            last = null;
            hasWay = moving = true;
        }

        static FootState of(HeroEntity hero) {
            HeroEscapeGoal g = hero.escapeGoal();
            return g == null ? new FootState() : g.foot;
        }
    }

    /** The next waypoint toward {@code destination}: the destination itself once near, else a reachable spot toward it. */
    static Vec3 waypoint(HeroEntity hero, Vec3 destination) {
        if (hero.position().distanceToSqr(destination) <= WAYPOINT_RANGE * WAYPOINT_RANGE) {
            return destination;
        }
        Vec3 p = LandRandomPos.getPosTowards(hero, WAYPOINT_RANGE, 7, destination);
        if (p == null) {
            p = DefaultRandomPos.getPosTowards(hero, WAYPOINT_RANGE, 7, destination, Math.PI / 2);
        }
        return p;
    }

    /**
     * Whether a way toward {@code destination} is open now (a cornered hero asks every second): a waypoint toward it
     * that a path reaches.
     */
    public static boolean wayOpen(HeroEntity hero, Vec3 destination) {
        Vec3 wp = waypoint(hero, destination);
        if (wp == null) {
            return false;
        }
        Path p = hero.getNavigation().createPath(wp.x, wp.y, wp.z, 1);
        return p != null && p.canReach() && p.getNodeCount() > 1;
    }

    private final HeroEntity hero;
    private final FootState foot = new FootState();
    private Movement movement = ON_FOOT;

    public HeroEscapeGoal(HeroEntity hero) {
        this.hero = hero;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** A12: the hero's own mobility while it escapes. */
    public void movement(Movement m) {
        movement = m == null ? ON_FOOT : m;
    }

    private HeroEscape escape() {
        return hero.combat().boss().escape();
    }

    @Override
    public boolean canUse() {
        return !hero.level().isClientSide && hero.isAlive() && (escape().fleeing() || escape().keepFarther());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean isInterruptable() {
        return true;
    }

    @Override
    public void start() {
        foot.reset();
        hero.setAggressive(false);
    }

    @Override
    public void stop() {
        movement.stop(hero);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        HeroEscape e = escape();
        if (e.fleeing()) {
            if (hero.getTarget() != null) {
                hero.setTarget(null); // it runs: no target, no blow (an alarm or a hurt cannot hand it one)
            }
            e.reportWay(movement.step(hero, e.destination()));
        } else {
            Vec3 p = e.keepAwayPoint();
            if (p != null) {
                movement.step(hero, p);
            }
        }
    }
}
