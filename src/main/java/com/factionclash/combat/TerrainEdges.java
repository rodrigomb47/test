package com.factionclash.combat;

import com.factionclash.logic.hero.EdgePlay;
import com.factionclash.logic.hero.EdgePlay.Ground;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads lethal terrain from the blocks (HEROES_V2 6.17, {@code docs/design/breaching.md} 8; tasks T1 and T3), the same
 * reading for whoever stands there: what a push would carry a receiver into ({@link #ground}), how it can be hurt
 * ({@link #receiver}), and the ring of edges round it ({@link #sampleRing}). Heroes aim their pushes with it, and every
 * NPC and hero keeps away from it while a pusher is near ({@link EdgeSense}). Nothing here reads whether anyone is a
 * player. A caller passes its own cursor and drop holder, so a sample allocates nothing.
 */
public final class TerrainEdges {
    private TerrainEdges() {
    }

    /** The deepest a column is read down before it counts as the void (below the world's floor it always is). */
    public static final int MAX_SCAN = 384;

    /**
     * What lies in column (x, z) for a receiver whose feet are in block row {@code y}: a wall stops a push there; lava,
     * fire (a lit campfire or a magma block included), powder snow, cactus or a berry bush, water; or the floor it would
     * land on, with the drop to it (blocks of air under its feet) in {@code drop[0]}; or the void, with no floor down to
     * the world's bottom.
     */
    public static Ground ground(Level level, int x, int y, int z, BlockPos.MutableBlockPos c, int[] drop) {
        drop[0] = 0;
        c.set(x, y + 1, z);
        if (!level.getBlockState(c).getCollisionShape(level, c).isEmpty()) {
            return Ground.WALL;
        }
        c.set(x, y, z);
        BlockState feet = level.getBlockState(c);
        Ground h = hazard(level, c, feet, false);
        if (h != null) {
            return h;
        }
        if (!feet.getCollisionShape(level, c).isEmpty()) {
            return Ground.WALL; // a step up stops a push
        }
        int bottom = level.getMinBuildHeight();
        for (int i = 0; i < MAX_SCAN; i++) {
            int qy = y - 1 - i;
            if (qy < bottom) {
                drop[0] = i;
                return Ground.VOID;
            }
            c.set(x, qy, z);
            BlockState s = level.getBlockState(c);
            Ground g = hazard(level, c, s, true);
            if (g != null) {
                drop[0] = i;
                return g;
            }
            if (!s.getCollisionShape(level, c).isEmpty()) {
                drop[0] = i;
                return Ground.FLOOR;
            }
        }
        drop[0] = MAX_SCAN;
        return Ground.VOID;
    }

    /** The hazard a block is ({@code below}: the block landed on, where a magma block burns), or null. */
    private static Ground hazard(Level level, BlockPos p, BlockState s, boolean below) {
        var fluid = level.getFluidState(p);
        if (fluid.is(FluidTags.LAVA)) {
            return Ground.LAVA;
        }
        if (s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES) && s.getValue(CampfireBlock.LIT)
                || below && s.is(Blocks.MAGMA_BLOCK)) {
            return Ground.FIRE;
        }
        if (s.is(Blocks.POWDER_SNOW)) {
            return Ground.POWDER_SNOW;
        }
        if (s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH)) {
            return Ground.THORNS;
        }
        if (fluid.is(FluidTags.WATER)) {
            return Ground.WATER;
        }
        return null;
    }

    /**
     * How {@code r} can be hurt by terrain, read the same way for a player, an NPC, a hero or a monster: its health,
     * vanilla's safe fall and fall damage multiplier, fire-proof (fire-immune or Fire Resistance), a soft landing (Slow
     * Falling, or a hero that glides or flies), whether it freezes, a {@code water} weak point, its knockback resistance.
     */
    public static EdgePlay.Receiver receiver(LivingEntity r) {
        boolean soft = r.hasEffect(MobEffects.SLOW_FALLING) || r instanceof FactionNpc n && n.softLanding();
        return new EdgePlay.Receiver(r.getHealth(), r.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
                r.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), fireproof(r), soft, r.canFreeze(),
                r instanceof FactionNpc n && n.waterWeakPoint(),
                Math.max(0, Math.min(1, r.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE))));
    }

    /** Fire-immune or under Fire Resistance: lava and fire are no edge for it. */
    public static boolean fireproof(LivingEntity r) {
        return r.fireImmune() || r.hasEffect(MobEffects.FIRE_RESISTANCE);
    }

    /** The block row of a receiver's feet (one standing on a slab or in a path counts from that block). */
    public static int feetRow(LivingEntity r) {
        return (int) Math.floor(r.getY() + 0.01);
    }

    /**
     * The edges round {@code r} as {@code rec} can be hurt by them (HEROES_V2 6.17): in each of {@link EdgePlay#DIRECTIONS}
     * directions, cell by cell out to {@link EdgePlay#REACH} blocks, the first edge a push would carry it into, stopping
     * at a wall, at any hazard and at any drop deeper than a step.
     */
    public static void sampleRing(LivingEntity r, EdgePlay.Receiver rec, EdgePlay.Ring ring, long now,
                                  BlockPos.MutableBlockPos c, int[] drop) {
        Level level = r.level();
        ring.clear();
        ring.centreX = r.getX();
        ring.centreY = r.getY();
        ring.centreZ = r.getZ();
        ring.sampledAt = now;
        ring.receiverId = r.getId();
        int y = feetRow(r);
        int ox = (int) Math.floor(r.getX()), oz = (int) Math.floor(r.getZ());
        for (int d = 0; d < EdgePlay.DIRECTIONS; d++) {
            double ux = EdgePlay.dirX(d), uz = EdgePlay.dirZ(d);
            int lastX = ox, lastZ = oz;
            Ground pool = null;
            int poolNear = 0, poolFar = 0;
            double poolValue = 0;
            for (int k = 1; k <= EdgePlay.REACH; k++) {
                int cx = (int) Math.floor(r.getX() + ux * k), cz = (int) Math.floor(r.getZ() + uz * k);
                if (cx == lastX && cz == lastZ) {
                    if (pool != null) {
                        poolFar = k;
                    }
                    continue;
                }
                lastX = cx;
                lastZ = cz;
                Ground g = ground(level, cx, y, cz, c, drop);
                if (pool != null) {
                    if (g == pool && drop[0] <= 1) {
                        poolFar = k; // the same pool goes on
                        continue;
                    }
                    ring.edge(d, poolNear, poolFar, poolValue);
                    pool = null;
                }
                if (g == Ground.WALL) {
                    break;
                }
                double v = EdgePlay.value(g, drop[0], rec);
                if (g != Ground.FLOOR && g != Ground.VOID && drop[0] <= 1) {
                    // a pool at its feet's level (lava, fire, water...): a push lands in it or flies over it
                    if (v > 0) {
                        pool = g;
                        poolNear = poolFar = k;
                        poolValue = v;
                    }
                    continue;
                }
                if (v > 0) {
                    ring.edge(d, k, v); // a drop or the void: whatever passes the edge goes down
                }
                if (g != Ground.FLOOR || drop[0] > 1) {
                    break;
                }
            }
            if (pool != null) {
                // it runs on past the ring: counted as running on
                ring.edge(d, poolNear, poolFar >= EdgePlay.REACH ? Double.POSITIVE_INFINITY : poolFar, poolValue);
            }
        }
    }

    /**
     * Whether a receiver could stand at (x, y, z) unhurt (a floor under it, room for its feet and head, nothing that
     * burns, freezes or pricks, no drop): where a walk round may stop or a revival may rise.
     */
    public static boolean safeStand(Level level, int x, int y, int z, BlockPos.MutableBlockPos c, int[] drop) {
        Ground g = ground(level, x, y, z, c, drop);
        if (g != Ground.FLOOR || drop[0] != 0) {
            return false;
        }
        c.set(x, y - 1, z);
        return !level.getBlockState(c).is(Blocks.MAGMA_BLOCK);
    }
}
