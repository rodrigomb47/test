package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.MapBounds;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.FishingWater;
import com.factionclash.util.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds and checks a base's fishing water in the world (the rules themselves are {@link FishingWater}). The search
 * reads loaded chunks only: it looks at every second column around a point for the surface of real water at least 2
 * deep with a bank nearby, keeps the candidate nearest the base in each 8x8 cell, and fully checks the nearest
 * twelve. Spots inside or near any base's walls, in other factions' land, near a lair, off the map or out of reach
 * are never chosen, and nor is a spot the fisherman already found unfit.
 */
public final class FishingSpots {
    private FishingSpots() {
    }

    /** PROPOSED: search grid step, candidate cell size and how many candidates get the full check. */
    static final int STEP = 2, CELL = 8, EVALUATED = 12;
    /** PROPOSED: spots keep this far beyond every base's walls (28 from a settlement centre), 8 out of other land, 24 from lairs. */
    static final int WALLS_MARGIN = 4, FOREIGN_MARGIN = 8, LAIR_RADIUS = 24;
    /** PROPOSED: a spot within this many blocks of one found unfit is skipped. */
    static final int BAD_RADIUS = 2;
    /** PROPOSED: spots found while foraging may lie this much further from the base than the fishing radius. */
    public static final int FORAGE_EXTRA = 40;
    /** PROPOSED: the faction director looks for a base's fishing water at most this often. */
    public static final long DIRECTOR_SEARCH_TICKS = 6000;

    /** Rotating grid parity per base, so repeated searches look at different columns. */
    private static final Map<Integer, Integer> PARITY = new HashMap<>();
    /** When the director may next look for each base's fishing water (not saved: a restart looks at once). */
    private static final Map<Integer, Long> NEXT_DIRECTOR_SEARCH = new HashMap<>();

    /** The base's fishing spot record, or null. */
    public static WorkplaceState fishery(CampaignData data, BaseState b) {
        for (int id : b.workplaces) {
            WorkplaceState w = data.workplace(id);
            if (w != null && w.type == WorkplaceState.Type.FISHERY) {
                return w;
            }
        }
        return null;
    }

    /** How far from the base centre its fishing water may lie: the configured (or test) radius, more for a forager. */
    public static int reach(BaseState b, boolean foraging) {
        int r = BaseZones.workRadius(b, FCConfig.FISHING_RADIUS.get());
        return foraging ? r + FORAGE_EXTRA : r;
    }

    /**
     * The best fishing spot within {@code radius} of {@code around} and within {@code reach} of the base centre, or
     * null. Open water comes first, then the shorter walk from the nearer gate, then the larger body of water.
     */
    public static FishingWater.Spot search(ServerLevel level, CampaignData data, BaseState base, BlockPos around, int radius, int reach,
                                           Collection<Long> bad) {
        WorldCells cells = new WorldCells(level, data, base, reach);
        int parity = PARITY.getOrDefault(base.id, 0);
        PARITY.put(base.id, (parity + 1) % 4);
        int px = parity & 1, pz = (parity >> 1) & 1;
        int ax = around.getX(), az = around.getZ();
        int x0 = ax - radius + Math.floorMod(px - (ax - radius), STEP), z0 = az - radius + Math.floorMod(pz - (az - radius), STEP);
        Map<Long, int[]> perCell = new HashMap<>();
        for (int x = x0; x <= ax + radius; x += STEP) {
            for (int z = z0; z <= az + radius; z += STEP) {
                long dx = x - ax, dz = z - az;
                if (dx * dx + dz * dz > (long) radius * radius) {
                    continue;
                }
                int top = cells.surfaceWater(x, z);
                if (top == Integer.MIN_VALUE || !cells.shoreNear(x, z) || !cells.zoneOk(x, top, z) || nearBad(bad, x, top, z)) {
                    continue;
                }
                long cell = ChunkPos.asLong(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
                int[] cur = perCell.get(cell);
                if (cur == null || distSq(base.center, x, z) < distSq(base.center, cur[0], cur[2])) {
                    perCell.put(cell, new int[]{x, top, z});
                }
            }
        }
        List<int[]> candidates = new ArrayList<>(perCell.values());
        candidates.sort(Comparator.comparingLong(c -> distSq(base.center, c[0], c[2])));
        FishingWater.Spot best = null;
        for (int i = 0; i < candidates.size() && i < EVALUATED; i++) {
            int[] c = candidates.get(i);
            FishingWater.Spot s = FishingWater.evaluate(cells, c[0], c[1], c[2]);
            if (s != null && (best == null || better(base, s, best))) {
                best = s;
            }
        }
        return best == null || best.open() ? best : castFurther(cells, best, bad);
    }

    /**
     * The candidates lie near the bank, where the water around the bobber is rarely open water. Like a player
     * casting further out, the bobber is moved one or two blocks further from the stand when the water there is open
     * water and a fishing spot by every rule (the stand may change); otherwise the spot stays as found.
     */
    private static FishingWater.Spot castFurther(WorldCells cells, FishingWater.Spot s, Collection<Long> bad) {
        int dx = Integer.signum(s.bx() - s.sx()), dz = Integer.signum(s.bz() - s.sz());
        for (int k = 1; k <= 2; k++) {
            int x = s.bx() + dx * k, z = s.bz() + dz * k;
            if (!cells.zoneOk(x, s.by(), z) || nearBad(bad, x, s.by(), z) || !FishingWater.openWater(cells, x, s.by(), z)) {
                continue;
            }
            FishingWater.Spot out = FishingWater.evaluate(cells, x, s.by(), z);
            if (out != null) {
                return out;
            }
        }
        return s;
    }

    private static boolean better(BaseState base, FishingWater.Spot a, FishingWater.Spot b) {
        if (a.open() != b.open()) {
            return a.open();
        }
        long da = gateDistSq(base, a.sx(), a.sz()), db = gateDistSq(base, b.sx(), b.sz());
        if (da != db) {
            return da < db;
        }
        return a.body() > b.body();
    }

    /** Squared horizontal distance from the nearer gate (the centre for a base without gates). */
    private static long gateDistSq(BaseState base, int x, int z) {
        long best = Long.MAX_VALUE;
        for (String g : new String[]{BaseState.GATE_WEST, BaseState.GATE_EAST}) {
            BlockPos p = base.point(g);
            if (p != null) {
                best = Math.min(best, distSq(p, x, z));
            }
        }
        return best == Long.MAX_VALUE ? distSq(base.center, x, z) : best;
    }

    private static long distSq(BlockPos p, int x, int z) {
        long dx = x - p.getX(), dz = z - p.getZ();
        return dx * dx + dz * dz;
    }

    private static boolean nearBad(Collection<Long> bad, int x, int y, int z) {
        for (long k : bad) {
            BlockPos p = BlockPos.of(k);
            if (Math.abs(p.getX() - x) <= BAD_RADIUS && Math.abs(p.getZ() - z) <= BAD_RADIUS && Math.abs(p.getY() - y) <= BAD_RADIUS) {
                return true;
            }
        }
        return false;
    }

    /** Writes a found spot onto the fishery: bobber, stand, open water, VALID. */
    public static void apply(CampaignData data, WorkplaceState w, FishingWater.Spot s) {
        w.origin = new BlockPos(s.bx(), s.by(), s.bz());
        w.extent = new BlockPos(s.sx(), s.sy(), s.sz());
        w.open = s.open();
        w.spot = FishingWater.VALID;
        data.setDirty();
    }

    /** Tells the faction where its fishing water is. */
    public static void announce(ServerLevel level, BaseState b, WorkplaceState w) {
        Messages.toFaction(level.getServer(), b.faction, Component.literal(b.name + ": fishing water found at "
                + w.origin.toShortString() + (w.open ? " (open water)" : " (still water)")));
    }

    /**
     * The cheap check at every cast: the bobber's block is still a water source with air above, the stand can still
     * be stood on, and no farmland has appeared near the water.
     */
    public static boolean stillFishable(ServerLevel level, CampaignData data, BaseState b, WorkplaceState w) {
        WorldCells c = new WorldCells(level, data, b, reach(b, true));
        BlockPos o = w.origin, s = w.extent;
        return FishingWater.stillFishable(c, o.getX(), o.getY(), o.getZ(), s.getX(), s.getY(), s.getZ());
    }

    /** True while the bobber's block is still a water source with air or a lily pad above it. */
    public static boolean waterAt(ServerLevel level, CampaignData data, BaseState b, WorkplaceState w) {
        WorldCells c = new WorldCells(level, data, b, reach(b, true));
        BlockPos o = w.origin;
        return c.waterSource(o.getX(), o.getY(), o.getZ()) && c.aboveWater(o.getX(), o.getY() + 1, o.getZ());
    }

    /**
     * The full check made every 20th cast: the water is still a fishing spot by every rule, the bobber and the stand
     * still keep to the zones (a base may have been built nearby since), and the stand is still usable, with the
     * bobber in sight from it. Open water is recomputed.
     */
    public static boolean stillValid(ServerLevel level, CampaignData data, BaseState b, WorkplaceState w) {
        WorldCells c = new WorldCells(level, data, b, reach(b, true));
        BlockPos o = w.origin, s = w.extent;
        if (!c.zoneOk(o.getX(), o.getY(), o.getZ()) || !c.standAllowed(s.getX(), s.getY(), s.getZ())
                || !c.sight(s.getX(), s.getY(), s.getZ(), o.getX(), o.getY(), o.getZ())
                || FishingWater.evaluate(c, o.getX(), o.getY(), o.getZ()) == null
                || !FishingWater.stillFishable(c, o.getX(), o.getY(), o.getZ(), s.getX(), s.getY(), s.getZ())) {
            return false;
        }
        boolean open = FishingWater.openWater(c, o.getX(), o.getY(), o.getZ());
        if (open != w.open) {
            w.open = open;
            data.setDirty();
        }
        return true;
    }

    /** Called by the faction director for each settlement; see {@link #maintain(ServerLevel, CampaignData, BaseState, long, boolean)}. */
    public static void maintain(ServerLevel level, CampaignData data, BaseState b, long t) {
        maintain(level, data, b, t, false);
    }

    /**
     * Looks for the base's fishing water when it has none yet, at most every {@link #DIRECTOR_SEARCH_TICKS} per base
     * ({@code force} skips the wait), so an old save's farm fishery moves to real water and a base knows about water
     * before it hires a fisherman. It never gives up on a base's water: only a fisherman who searched and foraged in
     * vain records that there is none.
     */
    public static void maintain(ServerLevel level, CampaignData data, BaseState b, long t, boolean force) {
        WorkplaceState w = fishery(data, b);
        if (w == null || w.spot == FishingWater.VALID) {
            return;
        }
        Long next = NEXT_DIRECTOR_SEARCH.get(b.id);
        // a time further ahead than one wait is left over from another world played in this session: look now
        if (!force && next != null && t < next && next - t <= DIRECTOR_SEARCH_TICKS) {
            return;
        }
        NEXT_DIRECTOR_SEARCH.put(b.id, t + DIRECTOR_SEARCH_TICKS);
        int r = reach(b, false);
        FishingWater.Spot s = search(level, data, b, b.center, r, r, w.badSpots);
        if (s != null) {
            apply(data, w, s);
            announce(level, b, w);
        }
    }

    /**
     * The world as {@link FishingWater} sees it, reading loaded chunks only (an unloaded block is never water, ground
     * or room, and counts as farmland), with the zone rules for the bobber and the stand.
     */
    static final class WorldCells implements FishingWater.Cells {
        private final ServerLevel level;
        private final CampaignData data;
        private final BaseState base;
        private final int reach;
        private final Map<Long, LevelChunk> chunks = new HashMap<>();
        private final Set<Long> unloaded = new HashSet<>();
        private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        WorldCells(ServerLevel level, CampaignData data, BaseState base, int reach) {
            this.level = level;
            this.data = data;
            this.base = base;
            this.reach = reach;
        }

        private LevelChunk chunk(int x, int z) {
            long k = ChunkPos.asLong(x >> 4, z >> 4);
            LevelChunk c = chunks.get(k);
            if (c != null || unloaded.contains(k)) {
                return c;
            }
            c = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (c == null) {
                unloaded.add(k);
            } else {
                chunks.put(k, c);
            }
            return c;
        }

        /** The block state, or null when its chunk is not loaded or it is outside the world's height. */
        private BlockState state(int x, int y, int z) {
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                return null;
            }
            LevelChunk c = chunk(x, z);
            return c == null ? null : c.getBlockState(m.set(x, y, z));
        }

        /** The y of the water surface in this column (the top block, a water source with air or a lily pad above, at least 2 deep), or MIN_VALUE. */
        int surfaceWater(int x, int z) {
            LevelChunk c = chunk(x, z);
            if (c == null || !MapBounds.inside(data, x, z, 0)) {
                return Integer.MIN_VALUE;
            }
            int top = c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
            if (!waterSource(x, top, z) || !aboveWater(x, top + 1, z)) {
                return Integer.MIN_VALUE;
            }
            int floor = c.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15);
            return top - floor >= 2 ? top : Integer.MIN_VALUE;
        }

        /** A column 2 or 4 blocks away in a straight line whose top block is not water: a bank to stand on nearby. */
        boolean shoreNear(int x, int z) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (int dist = 2; dist <= 4; dist += 2) {
                    int sx = x + d.getStepX() * dist, sz = z + d.getStepZ() * dist;
                    LevelChunk c = chunk(sx, sz);
                    if (c == null) {
                        continue;
                    }
                    int top = c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx & 15, sz & 15);
                    BlockState s = state(sx, top, sz);
                    if (s != null && !s.getFluidState().is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * The zone rules for a bobber or a stand: on the map and within reach of the base centre, at least 4 beyond
         * every base's walls (28 from a settlement centre, the own one included), out of other factions' land (8),
         * more than 24 from any lair, more than 48 from a pillager outpost or woodland mansion the faction knows of.
         */
        boolean zoneOk(int x, int y, int z) {
            if (!MapBounds.inside(data, x, z, 0) || distSq(base.center, x, z) > (long) reach * reach) {
                return false;
            }
            BlockPos p = new BlockPos(x, y, z);
            return BaseZones.outsideAllWalls(data, p, WALLS_MARGIN - 1) && !BaseZones.inForeignArea(data, base.id, p, FOREIGN_MARGIN)
                    && !BaseZones.nearLair(data, p, LAIR_RADIUS)
                    && !BaseZones.nearKnownDanger(data.factions.get(base.faction), p, BaseZones.DANGER_RADIUS);
        }

        @Override
        public boolean standAllowed(int x, int y, int z) {
            return zoneOk(x, y, z) && !WorkSafety.reserved(data, base, new BlockPos(x, y, z), WorkSafety.Use.FISH_STAND);
        }

        @Override
        public boolean waterSource(int x, int y, int z) {
            BlockState s = state(x, y, z);
            return s != null && s.getFluidState().is(FluidTags.WATER) && s.getFluidState().isSource()
                    && s.getCollisionShape(level, m.set(x, y, z)).isEmpty();
        }

        @Override
        public boolean aboveWater(int x, int y, int z) {
            BlockState s = state(x, y, z);
            return s != null && (s.isAir() || s.is(Blocks.LILY_PAD));
        }

        @Override
        public boolean insideWater(int x, int y, int z) {
            return waterSource(x, y, z); // vanilla: a water source with nothing to bump into (kelp, seagrass, bubbles)
        }

        @Override
        public boolean farmland(int x, int y, int z) {
            BlockState s = state(x, y, z);
            return s == null || s.is(Blocks.FARMLAND);
        }

        @Override
        public boolean sky(int x, int y, int z) {
            return chunk(x, z) != null && level.canSeeSky(m.set(x, y, z));
        }

        @Override
        public boolean sturdyGround(int x, int y, int z) {
            BlockState s = state(x, y, z);
            if (s == null || s.is(Blocks.FARMLAND) || s.is(BlockTags.LEAVES) || s.is(BlockTags.ICE) || !s.getFluidState().isEmpty()) {
                return false;
            }
            return s.isFaceSturdy(level, m.set(x, y, z), Direction.UP);
        }

        @Override
        public boolean clear(int x, int y, int z) {
            BlockState s = state(x, y, z);
            return s != null && s.getFluidState().isEmpty() && s.getCollisionShape(level, m.set(x, y, z)).isEmpty();
        }

        /** A ray from the eyes of someone standing at s to the middle of b, blocked by anything solid on the way. */
        @Override
        public boolean sight(int sx, int sy, int sz, int bx, int by, int bz) {
            Vec3 from = new Vec3(sx + 0.5, sy + 1.62, sz + 0.5), to = new Vec3(bx + 0.5, by + 0.5, bz + 0.5);
            Vec3 d = to.subtract(from);
            int steps = (int) Math.ceil(d.length() * 8);
            Set<Long> seen = new HashSet<>();
            for (int i = 0; i <= steps; i++) {
                Vec3 p = from.add(d.scale(i / (double) steps));
                int x = net.minecraft.util.Mth.floor(p.x), y = net.minecraft.util.Mth.floor(p.y), z = net.minecraft.util.Mth.floor(p.z);
                if ((x == bx && y == by && z == bz) || !seen.add(BlockPos.asLong(x, y, z))) {
                    continue;
                }
                BlockState s = state(x, y, z);
                if (s == null) {
                    return false;
                }
                BlockPos at = new BlockPos(x, y, z);
                VoxelShape shape = s.getCollisionShape(level, at);
                if (!shape.isEmpty() && shape.clip(from, to, at) != null) {
                    return false;
                }
            }
            return true;
        }
    }
}
