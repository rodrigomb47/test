package com.factionclash.combat;

import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Out of lava at once ({@code docs/design/breaching.md} 8.2, HEROES_V2 6.17 "How everyone defends"; task T3), for every
 * faction NPC and hero: one in lava stops everything else and heads for the nearest cell it can stand on that is not lava,
 * swimming and jumping as vanilla's movement allows (a player jumps out at once; a vanilla mob flounders). Out of the lava
 * and still burning, it steps into water within 6 blocks if there is any (2 s at most), else goes back to its fight. A
 * Fire Resistance potion it carries is drunk on the way ({@link TerrainSurvival}). A fire-proof one ignores lava. Priority
 * 0, beside {@code FloatGoal} (which keeps it swimming up); it walks, never teleports.
 */
public class LavaEscapeGoal extends Goal {
    /** How far round it looks for a way out of lava, and for water once out and burning. */
    public static final int EXIT_RADIUS = 8, WATER_RADIUS = 6;
    /** Once out, it looks for water this long (ticks) while it burns. */
    public static final int WATER_TICKS = 40;
    /**
     * Bobbing at the surface or in the jump onto the bank it keeps heading for the way out this long after it last
     * touched lava (ticks), until it stands, so a fight's goal does not take over half-way out.
     */
    public static final int BANK_TICKS = 10;

    private final FactionNpc npc;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final int[] drop = new int[1];
    private Vec3 exit;
    private BlockPos water;
    private int repick, outFor = -1, escapes, lavaAt;

    public LavaEscapeGoal(FactionNpc npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean fireproof() {
        return npc.fireImmune();
    }

    @Override
    public boolean canUse() {
        return !npc.level().isClientSide && !fireproof() && npc.isInLava();
    }

    @Override
    public boolean canContinueToUse() {
        if (fireproof()) {
            return false;
        }
        if (npc.isInLava() || onTheWayOut()) {
            return true;
        }
        // out of the lava: into water within 6 blocks while it burns, for at most 2 s
        return water != null && npc.isOnFire() && !npc.isInWater() && outFor >= 0 && outFor < WATER_TICKS;
    }

    @Override
    public void start() {
        exit = null;
        water = null;
        repick = 0;
        outFor = -1;
        escapes++;
        npc.getNavigation().stop();
    }

    @Override
    public void stop() {
        exit = null;
        water = null;
        outFor = -1;
        npc.getNavigation().stop();
    }

    /** Just out of the lava and not standing yet (at the surface, or jumping onto the bank). */
    private boolean onTheWayOut() {
        return exit != null && !npc.onGround() && npc.tickCount - lavaAt <= BANK_TICKS;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (npc.isInLava()) {
            outFor = -1;
            lavaAt = npc.tickCount;
            if (--repick <= 0 || exit == null) {
                repick = 10;
                exit = nearestExit();
            }
            if (exit != null) {
                steer();
            } else {
                npc.getJumpControl().jump(); // nowhere to stand in sight: swim up
            }
            return;
        }
        if (onTheWayOut()) {
            steer(); // at the surface or jumping onto the bank: on toward the way out
            return;
        }
        if (outFor < 0) {
            outFor = 0;
            water = npc.isOnFire() ? nearestWater() : null;
            if (water != null) {
                npc.getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 1.3);
            }
        }
        outFor++;
    }

    /** Heads for the way out, jumping while it lies higher or a bank is in the way. */
    private void steer() {
        npc.getMoveControl().setWantedPosition(exit.x, exit.y, exit.z, 1.4); // a player's input in lava (FactionNpc.setSpeed)
        npc.getLookControl().setLookAt(exit.x, exit.y + 1, exit.z);
        if (npc.horizontalCollision || exit.y > npc.getY() + 0.4) {
            npc.getJumpControl().jump();
        }
    }

    /** Lava escapes begun (tests). */
    public int escapes() {
        return escapes;
    }

    /**
     * The nearest cell within {@link #EXIT_RADIUS} where it stands out of the lava (a floor, room, no lava or fire): the
     * rows from one under its feet to two over, nearest first.
     */
    private Vec3 nearestExit() {
        Level level = npc.level();
        int fx = (int) Math.floor(npc.getX()), fy = (int) Math.floor(npc.getY()), fz = (int) Math.floor(npc.getZ());
        Vec3 best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (int r = 1; r <= EXIT_RADIUS && best == null; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = -1; dy <= 2; dy++) {
                        int x = fx + dx, y = fy + dy, z = fz + dz;
                        if (TerrainEdges.safeStand(level, x, y, z, cursor, drop)) {
                            double d = dx * dx + dz * dz + dy * dy * 0.5;
                            if (d < bestD) {
                                bestD = d;
                                best = new Vec3(x + 0.5, y, z + 0.5);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** The nearest water within {@link #WATER_RADIUS} blocks at or about its feet (null: none). */
    private BlockPos nearestWater() {
        Level level = npc.level();
        BlockPos at = npc.blockPosition();
        BlockPos best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (int dx = -WATER_RADIUS; dx <= WATER_RADIUS; dx++) {
            for (int dz = -WATER_RADIUS; dz <= WATER_RADIUS; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    cursor.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    if (level.getFluidState(cursor).is(FluidTags.WATER)) {
                        double d = dx * dx + dz * dz + dy * dy;
                        if (d < bestD) {
                            bestD = d;
                            best = cursor.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }
}
