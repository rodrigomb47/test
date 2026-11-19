package com.factionclash.hero;

import com.factionclash.combat.TerrainEdges;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.hero.EdgePlay;
import com.factionclash.logic.hero.FormGeometry;
import com.factionclash.logic.hero.MoveSelector;
import com.factionclash.logic.hero.Sense;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Terrain is a weapon, in the game (HEROES_V2 6.17; task T1): one per hero, owned by its {@link HeroCombat}. At each of
 * the selector's checks it samples the edges round the hero's target every 2 s ({@link TerrainEdges#sampleRing}, as the
 * target can be hurt by them) and puts them in the {@link Sense} for the pick's factor; with a push ready within 2 s it
 * walks the hero round to the target's far side ({@link #spot}, which the fight's spacing follows), holding the pick
 * while it walks, for at most 3 s ({@link EdgePlay.Walk}). A grab's throw asks {@link #throwDirection}. With
 * {@code heroes.edgePlay} off the pushes stay full strength but nothing is aimed. The same against a player, an NPC, a
 * hero and a monster: the ring reads only what the receiver can be hurt by.
 */
public final class HeroEdgePlay {
    private final HeroEntity hero;
    private final EdgePlay.Ring ring = new EdgePlay.Ring();
    private final EdgePlay.Ring held = new EdgePlay.Ring();
    private final EdgePlay.Walk walk = new EdgePlay.Walk();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final int[] drop = new int[1];
    /** Where the walk round heads (null: none), and the tick it was last set. */
    private Vec3 spot;
    private long spotAt = Long.MIN_VALUE / 4;
    /** Walks round started and given up, and throws aimed at an edge (tests, the combat log). */
    private int walks, givenUp, aimedThrows;

    HeroEdgePlay(HeroEntity hero) {
        this.hero = hero;
    }

    /** Whether 6.17's aiming is on ({@code heroes.edgePlay}); the pushes are full strength either way. */
    public static boolean enabled() {
        return FCConfig.HERO_EDGE_PLAY.get();
    }

    /**
     * At a selector check, after the sense is filled, with the hero free to pick: the edges round {@code t} into
     * {@code s}, and the walk round. True while the hero walks round (the pick waits).
     */
    boolean update(LivingEntity t, Sense s, MoveSelector sel, long now) {
        if (!enabled() || t == null || !t.isAlive() || s.airborne || s.climbing || s.inWater) {
            walk.reset();
            spot = null;
            return false;
        }
        if (ring.stale(now, t.getId(), t.getX(), t.getZ())) {
            TerrainEdges.sampleRing(t, TerrainEdges.receiver(t), ring, now, cursor, drop);
        }
        double dx = t.getX() - hero.getX(), dz = t.getZ() - hero.getZ();
        double l = Math.sqrt(dx * dx + dz * dz);
        double kbr = Math.max(0, Math.min(1, t.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)));
        s.edges = ring;
        s.toTargetX = l < 1e-6 ? 0 : dx / l;
        s.toTargetZ = l < 1e-6 ? 0 : dz / l;
        s.targetKnockbackResistance = kbr;
        EdgePlay.Push p = ring.any() ? sel.readyPush(now, s, EdgePlay.READY_TICKS) : null;
        EdgePlay.Spot sp = null;
        Vec3 at = null;
        if (p != null) {
            double blocks = p.blocks(kbr, l);
            int skipped = 0;
            for (int tries = 0; tries < 4; tries++) {
                int mask = skipped;
                sp = EdgePlay.approach(ring, blocks, p.reach(), -dx, -dz, d -> (mask & (1 << d)) != 0);
                if (sp == null || EdgePlay.standsRight(ring, dx, dz, blocks, sp)) {
                    break;
                }
                at = standable(t, sp, p.reach());
                if (at != null) {
                    break;
                }
                skipped |= 1 << sp.dir();
                sp = null;
            }
        }
        boolean aligned = sp != null && at == null;
        boolean wasWalking = walk.walking();
        EdgePlay.Step step = walk.step(now, t.getId(), sp, aligned);
        if (step == EdgePlay.Step.WALK) {
            if (!wasWalking) {
                walks++;
            }
            spot = at;
            spotAt = hero.tickCount;
            return true;
        }
        if (step == EdgePlay.Step.GAVE_UP) {
            givenUp++;
        }
        spot = null;
        return false;
    }

    /** Where the walk round heads now (null: none, or its last check is older than half a second). */
    public Vec3 spot() {
        return spot != null && hero.tickCount - spotAt <= 10 ? spot : null;
    }

    /** Scratch for {@link #clearLine} (server thread only). */
    private static final BlockPos.MutableBlockPos LINE_CURSOR = new BlockPos.MutableBlockPos();
    private static final int[] LINE_DROP = new int[1];

    /** Within this many blocks of its spot the walk round steers straight at it instead of following a path. */
    public static final double STEER_BLOCKS = 1.5;

    /**
     * Walks {@code mob} to the walk round's {@code spot} (the fight's spacing calls it each tick while a spot is set): its
     * path while the spot is farther than {@link #STEER_BLOCKS}, then straight at the spot's centre (review of T1,
     * 2026-11-19: a path counts a neighbouring block as arrived, and a hero left a block short stood outside the edge's
     * sector for good). The straight steer only over cells it stands on unhurt; else the path, to the spot's own block.
     */
    public static void walkTo(net.minecraft.world.entity.Mob mob, Vec3 spot, double speed) {
        double dx = spot.x - mob.getX(), dz = spot.z - mob.getZ();
        if (dx * dx + dz * dz <= STEER_BLOCKS * STEER_BLOCKS && Math.abs(spot.y - mob.getY()) < 0.6
                && clearLine(mob.level(), mob.getX(), mob.getZ(), spot, TerrainEdges.feetRow(mob))) {
            mob.getNavigation().stop();
            mob.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, speed);
            return;
        }
        if (mob.getNavigation().isDone() || mob.tickCount % 10 == 0) {
            var path = mob.getNavigation().createPath(BlockPos.containing(spot), 0);
            if (path != null) {
                mob.getNavigation().moveTo(path, speed);
            } else {
                mob.getNavigation().moveTo(spot.x, spot.y, spot.z, speed);
            }
        }
    }

    /** Every cell on the straight line from (x, z) to {@code spot}, a third of a block apart, a floor it stands on unhurt. */
    private static boolean clearLine(Level level, double x, double z, Vec3 spot, int row) {
        BlockPos.MutableBlockPos c = LINE_CURSOR;
        int[] drop = LINE_DROP;
        for (int i = 1; i <= 3; i++) {
            double f = i / 3.0;
            int cx = (int) Math.floor(x + (spot.x - x) * f), cz = (int) Math.floor(z + (spot.z - z) * f);
            if (!TerrainEdges.safeStand(level, cx, row, cz, c, drop)) {
                return false;
            }
        }
        return true;
    }

    /** A move began, or the fight ended: no walk round is under way. */
    void stop() {
        walk.reset();
        spot = null;
    }

    /**
     * The spot of a walk round: of the cells round the ideal spot (the target's feet row, one up or one down), the one
     * nearest it where the hero stands unhurt (a floor, room for it, nothing that burns, no drop) and whose centre lines
     * up with the edge (its line to the target in the edge's sector, so the pick doubles the push from there) within the
     * move's reach; null with none.
     */
    private static final int[] ROWS = {0, 1, -1};

    private Vec3 standable(LivingEntity t, EdgePlay.Spot sp, double reach) {
        Level level = hero.level();
        double ix = t.getX() + sp.dx(), iz = t.getZ() + sp.dz();
        int cx = (int) Math.floor(ix), cz = (int) Math.floor(iz);
        int y = TerrainEdges.feetRow(t);
        Vec3 best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                double mx = cx + dx + 0.5, mz = cz + dz + 0.5;
                double tx = t.getX() - mx, tz = t.getZ() - mz, l = Math.hypot(tx, tz);
                double d = Math.hypot(mx - ix, mz - iz);
                if (d >= bestD || l < EdgePlay.STAND_MIN - 0.5 || l > Math.max(EdgePlay.STAND_MIN, reach)
                        || !EdgePlay.aligned(tx, tz, sp.dir())) {
                    continue;
                }
                for (int dy : ROWS) {
                    if (TerrainEdges.safeStand(level, cx + dx, y + dy, cz + dz, cursor, drop)) {
                        best = new Vec3(mx, y + dy, mz);
                        bestD = d;
                        break;
                    }
                }
            }
        }
        return best;
    }

    /**
     * A grab's throw (6.17 3): the unit direction to throw {@code g} in, from the hero's facing {@code (fx, fz)}, for a
     * throw of {@code blocks}: the nearest lethal edge round where it is held (then a harmful one) in the order of
     * {@link FormGeometry#THROW_TRIES}, else straight ahead; straight ahead with the aiming off.
     */
    public double[] throwDirection(LivingEntity g, double fx, double fz, double blocks) {
        int i = 0;
        if (enabled()) {
            TerrainEdges.sampleRing(g, TerrainEdges.receiver(g), held, hero.level().getGameTime(), cursor, drop);
            i = EdgePlay.throwTry(held, fx, fz, blocks);
            if (i > 0 || held.valueWithin(EdgePlay.sector(fx, fz), blocks) > 0) {
                aimedThrows++;
            }
        }
        return FormGeometry.turn(fx, fz, FormGeometry.THROW_TRIES[i]);
    }

    /** Walks round started so far. */
    public int walks() {
        return walks;
    }

    /** Walks round given up after their 3 s. */
    public int givenUp() {
        return givenUp;
    }

    /** Throws aimed at an edge. */
    public int aimedThrows() {
        return aimedThrows;
    }

    /** The last ring sampled round its target (tests). */
    public EdgePlay.Ring ring() {
        return ring;
    }
}
