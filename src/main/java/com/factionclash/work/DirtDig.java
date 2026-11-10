package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.MapBounds;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.DirtPatch;
import com.factionclash.logic.ForestRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The world side of {@link DirtPatch}: where a woodcutter may dig its own dirt for a scaffold when the storehouse has
 * too little, reading loaded ground only. A column's top may be dug when it is earth that drops dirt by hand (grass, dirt,
 * coarse dirt, podzol) with a firm floor under it (no fluid, no sand or gravel that would fall), under open sky, with
 * nothing growing near it (no log, leaves or sapling within {@link #TREE_CLEARANCE}), and in no place kept from
 * digging: within the base's forest radius and on the map, outside every walls box plus {@link #WALLS_MARGIN}, in no
 * base's plan, not in wood that is not the faction's ({@link WorkSafety#protectedWood}: other factions' land, by a
 * known village, outpost or mansion), where a worker may dig ({@link WorkSafety#mayDig}), not reserved for other work
 * ({@link WorkSafety#reserved} with {@link WorkSafety.Use#DIRT_PIT}: fishing spots, mine mouths, saplings and stumps,
 * the road strip), not by a grove lattice point of the work belt, and not by a scaffold column a woodland remembers.
 */
public final class DirtDig {
    private DirtDig() {
    }

    /** PROPOSED: no dirt is dug this close (Chebyshev) to any base's walls. */
    static final int WALLS_MARGIN = 4;
    /** PROPOSED: no dirt is dug this close (sideways) to a log, leaves or a sapling (from a block below to 4 above). */
    static final int TREE_CLEARANCE = 2;
    /** PROPOSED: seeds looked at per tick while searching. */
    static final int SEEDS_PER_TICK = 4;

    /** True for a block whose top a woodcutter digs for scaffold dirt: earth that drops dirt (or coarse dirt) by hand. */
    public static boolean earth(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL);
    }

    /** True for a tool that digs earth faster than a hand and keeps its drop dirt: a shovel without Silk Touch. */
    public static boolean digTool(ItemStack s) {
        return s.is(net.minecraft.tags.ItemTags.SHOVELS) && !silky(s);
    }

    /** True for a tool with Silk Touch: earth broken with it drops itself (a grass block), not dirt. */
    public static boolean silky(ItemStack s) {
        for (Holder<Enchantment> h : s.getTagEnchantments().keySet()) {
            if (h.is(Enchantments.SILK_TOUCH)) {
                return true;
            }
        }
        return false;
    }

    /** The ground around base {@code own} as {@link DirtPatch} sees it, cached per column for one search or check. */
    static final class View implements DirtPatch.Ground {
        private final ServerLevel level;
        private final CampaignData data;
        private final BaseState own;
        private final Map<Long, LevelChunk> chunks = new HashMap<>();
        private final Map<Long, DirtPatch.Column> columns = new HashMap<>();
        private final Map<Long, Boolean> woody = new HashMap<>(), allowed = new HashMap<>();
        private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        private static final DirtPatch.Column UNSEEN = new DirtPatch.Column(Integer.MIN_VALUE, false, false);

        View(ServerLevel level, CampaignData data, BaseState own) {
            this.level = level;
            this.data = data;
            this.own = own;
        }

        private LevelChunk chunk(int x, int z) {
            long k = ChunkPos.asLong(x >> 4, z >> 4);
            if (chunks.containsKey(k)) {
                return chunks.get(k);
            }
            LevelChunk c = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            chunks.put(k, c);
            return c;
        }

        private BlockState state(LevelChunk c, int x, int y, int z) {
            return c.getBlockState(m.set(x, y, z));
        }

        @Override
        public DirtPatch.Column at(int x, int z) {
            long k = DirtPatch.key(x, z);
            DirtPatch.Column c = columns.get(k);
            if (c == null) {
                c = read(x, z);
                columns.put(k, c);
            }
            return c == UNSEEN ? null : c;
        }

        private DirtPatch.Column read(int x, int z) {
            LevelChunk c = chunk(x, z);
            if (c == null) {
                return UNSEEN;
            }
            int top = c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
            if (top <= level.getMinBuildHeight() || top + 2 >= level.getMaxBuildHeight()) {
                return new DirtPatch.Column(top, false, false);
            }
            BlockPos at = new BlockPos(x, top, z);
            BlockState s = state(c, x, top, z);
            BlockState a1 = state(c, x, top + 1, z), a2 = state(c, x, top + 2, z);
            boolean standable = s.getFluidState().isEmpty() && !s.is(BlockTags.LEAVES) && s.isFaceSturdy(level, at, Direction.UP)
                    && free(a1, at.above()) && free(a2, at.above(2));
            if (!standable || !earth(s)) {
                return new DirtPatch.Column(top, standable, false);
            }
            return new DirtPatch.Column(top, true, earthMayGo(c, at, a1));
        }

        @Override
        public boolean allowed(int x, int z) {
            long k = DirtPatch.key(x, z);
            Boolean a = allowed.get(k);
            if (a == null) {
                DirtPatch.Column c = at(x, z);
                a = c != null && placeAllows(new BlockPos(x, c.top(), z));
                allowed.put(k, a);
            }
            return a;
        }

        private boolean free(BlockState s, BlockPos p) {
            return s.getFluidState().isEmpty() && s.getCollisionShape(level, p).isEmpty();
        }

        /** The column's own rules for digging the earth at {@code at} (a standable top): what is on it, over it and under it. */
        private boolean earthMayGo(LevelChunk c, BlockPos at, BlockState above) {
            int x = at.getX(), y = at.getY(), z = at.getZ();
            // the plant on it (if any) gives way: no flower, sapling or crop is dug out from under
            if (!above.isAir() && !above.canBeReplaced()) {
                return false;
            }
            // open sky: nothing over it that blocks motion (no canopy, no overhang; a snow layer on it may count)
            if (c.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) > y + 1) {
                return false;
            }
            BlockState floor = state(c, x, y - 1, z);
            return floor.getFluidState().isEmpty() && !(floor.getBlock() instanceof FallingBlock) && !floor.is(BlockTags.LEAVES)
                    && floor.isFaceSturdy(level, at.below(), Direction.UP);
        }

        /** The rules of the place for digging the earth at {@code at}: nothing growing near it, no place kept from digging. */
        private boolean placeAllows(BlockPos at) {
            int x = at.getX(), y = at.getY(), z = at.getZ();
            for (int dx = -TREE_CLEARANCE; dx <= TREE_CLEARANCE; dx++) {
                for (int dz = -TREE_CLEARANCE; dz <= TREE_CLEARANCE; dz++) {
                    if (woodyAt(x + dx, y, z + dz)) {
                        return false;
                    }
                }
            }
            if (own == null || !MapBounds.inside(data, x, z, 2)) {
                return false;
            }
            long dx = x - own.center.getX(), dz = z - own.center.getZ();
            long r = BaseZones.workRadius(own, FCConfig.FOREST_RADIUS.get());
            if (dx * dx + dz * dz > r * r || !BaseZones.outsideAllWalls(data, at, WALLS_MARGIN)) {
                return false;
            }
            if (nearGroveLattice(at) || nearScaffold(x, z)) {
                return false;
            }
            if (WorkSafety.planned(data, at.below()) || WorkSafety.planned(data, at.above())
                    || WorkSafety.protectedWood(level, data, own, at) || !WorkSafety.mayDig(level, at, own, null)) {
                return false;
            }
            return !WorkSafety.reserved(data, own, at, WorkSafety.Use.DIRT_PIT);
        }

        /** True when column (x, z) holds a log, leaves or a sapling from {@code y - 1} to {@code y + 4} (unloaded: true). */
        private boolean woodyAt(int x, int y, int z) {
            long k = BlockPos.asLong(x, y, z);
            Boolean w = woody.get(k);
            if (w == null) {
                LevelChunk c = chunk(x, z);
                w = c == null;
                for (int dy = -1; dy <= 4 && !w; dy++) {
                    BlockState s = state(c, x, y + dy, z);
                    w = s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.getBlock() instanceof SaplingBlock;
                }
                woody.put(k, w);
            }
            return w;
        }

        /** True by a grove lattice point (Chebyshev 1) of the own base's work belt, where the grove's saplings go. */
        private boolean nearGroveLattice(BlockPos at) {
            if (!ForestScan.inBelt(own, at)) {
                return false;
            }
            int dx = at.getX() - own.center.getX(), dz = at.getZ() - own.center.getZ();
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    if (ForestRules.onGroveLattice(dx + ox, dz + oz)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** True by (Chebyshev 1) a column where scaffold may still stand, as any woodland remembers it. */
        private boolean nearScaffold(int x, int z) {
            for (WorkplaceState w : data.workplaces.values()) {
                if (w.type != WorkplaceState.Type.TREE_PLOT) {
                    continue;
                }
                for (long k : w.scaffolds) {
                    BlockPos p = BlockPos.of(k);
                    if (Math.abs(p.getX() - x) <= 1 && Math.abs(p.getZ() - z) <= 1) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * A search for a patch to dig, spread over ticks: seeds on a lattice around the tree waiting for the dirt (within
     * {@link DirtPatch#SEARCH_RADIUS}), nearest the tree first. The first seed whose patch gives all the blocks still
     * needed wins; otherwise the largest patch worth digging ({@link DirtPatch#worthDigging}); otherwise none.
     */
    static final class Search {
        private final List<int[]> seeds;
        private final int need;
        private final View view;
        private int cursor;
        private List<int[]> best = List.of();

        Search(ServerLevel level, CampaignData data, BaseState own, BlockPos tree, int need) {
            this.seeds = DirtPatch.seeds(tree.getX(), tree.getZ(), DirtPatch.SEARCH_RADIUS, DirtPatch.SEED_SPACING);
            this.need = need;
            this.view = new View(level, data, own);
        }

        /** Looks at the next few seeds: null while the search goes on, else the cells to dig (empty: no patch at all). */
        List<BlockPos> step() {
            for (int i = 0; i < SEEDS_PER_TICK && cursor < seeds.size(); i++, cursor++) {
                int[] s = seeds.get(cursor);
                List<int[]> p = DirtPatch.patch(view, s[0], s[1], need);
                if (p.size() >= need) {
                    return cells(p);
                }
                if (p.size() > best.size()) {
                    best = p;
                }
            }
            if (cursor < seeds.size()) {
                return null;
            }
            return DirtPatch.worthDigging(best.size(), need) ? cells(best) : List.of();
        }

        private static List<BlockPos> cells(List<int[]> p) {
            List<BlockPos> out = new ArrayList<>();
            for (int[] c : p) {
                out.add(new BlockPos(c[0], c[1], c[2]));
            }
            return out;
        }
    }

    /**
     * True when the patch cell {@code cell} may be dug now, with the world as it is: {@link DirtPatch#mayDigNow} over
     * a fresh view, the patch being {@code patch} (keys of {@link DirtPatch#key}).
     */
    static boolean mayDigNow(ServerLevel level, CampaignData data, BaseState own, Set<Long> patch, BlockPos cell) {
        return DirtPatch.mayDigNow(new View(level, data, own), patch, cell.getX(), cell.getY(), cell.getZ());
    }
}
