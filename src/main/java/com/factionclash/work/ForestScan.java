package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.MapBounds;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.ForestRules;
import com.factionclash.logic.TreeShape;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Where a woodcutter finds its work, reading loaded ground only: the natural trees around its base (see
 * {@link #findTree}), the shape of one tree as {@link TreeShape} sees it in the world ({@link #survey}), the spots of
 * the grove it keeps growing in the base's work belt ({@link GroveSearch}), and the claims that keep two woodcutters
 * (of any faction) off the same trunk ({@link TreeClaims}).
 */
public final class ForestScan {
    private ForestScan() {
    }

    /** PROPOSED: the tree search reads every second column, in a rotating pattern that covers each column every 4 scans. */
    static final int STEP = 2;
    /** The number of column patterns: scans {@code scan}, {@code scan+1}, ... {@code scan+PATTERNS-1} read every column once. */
    public static final int PATTERNS = STEP * STEP;
    /** A trunk is followed down at most this far (past the tallest tree a scaffold reaches). */
    static final int MAX_TRUNK_WALK = TreeShape.MAX_TALL_DY + 2;

    /**
     * A tree found: its stump (the north-west corner of a 2x2 trunk), whether the trunk is 2x2, its species, and how
     * far above the stump the top of the column it was found by lies.
     */
    public record Found(BlockPos stump, boolean big, ForestRules.Species species, int topDy) {
    }

    /** The species of the log block in {@code s}, or null when it is not a log a woodcutter fells. */
    public static ForestRules.Species speciesOf(BlockState s) {
        if (!s.is(BlockTags.LOGS)) {
            return null;
        }
        return ForestRules.fromLog(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
    }

    /** The sapling item of a species. */
    public static Item saplingItem(ForestRules.Species s) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(s.sapling()));
    }

    /** The sapling block of a species. */
    public static Block saplingBlock(ForestRules.Species s) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(s.sapling()));
    }

    /** The species of a sapling item, or null for any other item (azaleas and mangrove propagules included). */
    public static ForestRules.Species saplingSpecies(Item item) {
        return ForestRules.fromSapling(BuiltInRegistries.ITEM.getKey(item).toString());
    }

    /**
     * The best natural tree to fell within {@code radius} of {@code around} (a circle), or null. Columns are read every
     * second block, the pattern shifting with {@code scan} so that every column is read once in four scans; only
     * loaded chunks inside the campaign map are read. A column holds a tree when its top block (leaves not counted) is
     * a log of a species the woodcutter fells, the same log runs down at least 3 blocks and at most {@code maxDy} below
     * the top (8 for a woodcutter felling from the ground, up to 32 with blocks for a scaffold), the trunk stands on
     * soil, and natural leaves grow around the top. Trees the woodcutter gave up on
     * ({@code badWild}), trees another woodcutter is felling, wood that is not the faction's to fell
     * ({@link WorkSafety#protectedWood}) and spots {@code skip} rejects (too far out at night, or not to be got to for
     * now) are passed over. A trunk that is one corner of a 2x2 square is found as the square's north-west corner. The
     * lowest score wins: the distance from the base centre plus half the distance from the woodcutter, so the trees next
     * to the walls go first.
     */
    public static Found findTree(ServerLevel level, CampaignData data, BaseState base, FactionNpc npc, BlockPos around, int radius,
                                 Set<Long> badWild, int scan, Predicate<BlockPos> skip, int maxDy) {
        Loaded w = new Loaded(level);
        int phase = Math.floorMod(scan, PATTERNS);
        int px = phase == 1 || phase == 2 ? 1 : 0, pz = phase == 1 || phase == 3 ? 1 : 0;
        int ax = around.getX(), az = around.getZ();
        int x0 = ax - radius + Math.floorMod(px - (ax - radius), STEP), z0 = az - radius + Math.floorMod(pz - (az - radius), STEP);
        UUID me = npc.getUUID();
        long now = level.getGameTime();
        Set<Long> seen = new HashSet<>();
        Found best = null;
        double bestScore = Double.MAX_VALUE;
        for (int x = x0; x <= ax + radius; x += STEP) {
            for (int z = z0; z <= az + radius; z += STEP) {
                long dx = x - ax, dz = z - az;
                if (dx * dx + dz * dz > (long) radius * radius || !MapBounds.inside(data, x, z, 0)) {
                    continue;
                }
                Found f = column(w, x, z, maxDy);
                if (f == null || !seen.add(f.stump().asLong())) {
                    continue;
                }
                BlockPos s = f.stump();
                double score = horizontal(base.center, s) + 0.5 * Math.sqrt(npc.blockPosition().distSqr(new BlockPos(s.getX(), npc.getBlockY(), s.getZ())));
                if (score >= bestScore || badWild.contains(s.asLong()) || TreeClaims.claimedByOther(s, me, now) || skip.test(s)
                        || WorkSafety.protectedWood(level, data, base, s)) {
                    continue;
                }
                best = f;
                bestScore = score;
            }
        }
        return best;
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The tree whose trunk reaches the top of column (x, z), by the cheap column rule, or null. */
    private static Found column(Loaded w, int x, int z, int maxDy) {
        int top = w.top(x, z);
        if (top == Integer.MIN_VALUE) {
            return null;
        }
        BlockState log = w.state(x, top, z);
        ForestRules.Species sp = log == null ? null : speciesOf(log);
        if (sp == null) {
            return null;
        }
        int base = top;
        while (top - base < MAX_TRUNK_WALK && sameBlock(w.state(x, base - 1, z), log)) {
            base--;
        }
        int dy = top - base;
        if (dy < TreeShape.MIN_LOGS - 1 || dy > maxDy) {
            return null; // a stump or post, or too tall to fell whole (from the ground, or from a scaffold)
        }
        BlockState ground = w.state(x, base - 1, z);
        if (ground == null || !ground.is(BlockTags.DIRT) || !naturalLeavesNear(w, x, top, z)) {
            return null;
        }
        // a corner of a 2x2 trunk: the whole square is one tree, found at its north-west corner
        for (int ox = -1; ox <= 0; ox++) {
            for (int oz = -1; oz <= 0; oz++) {
                boolean all = true;
                for (int i = 0; i <= 1 && all; i++) {
                    for (int j = 0; j <= 1 && all; j++) {
                        BlockState g = w.state(x + ox + i, base - 1, z + oz + j);
                        all = sameBlock(w.state(x + ox + i, base, z + oz + j), log) && g != null && g.is(BlockTags.DIRT);
                    }
                }
                if (all) {
                    return new Found(new BlockPos(x + ox, base, z + oz), true, sp, dy);
                }
            }
        }
        return new Found(new BlockPos(x, base, z), false, sp, dy);
    }

    private static boolean sameBlock(@Nullable BlockState s, BlockState log) {
        return s != null && s.getBlock() == log.getBlock();
    }

    /** Natural (not persistent) leaves within 2 blocks sideways of the top log, from 1 below to 2 above it. */
    private static boolean naturalLeavesNear(Loaded w, int x, int y, int z) {
        int r = TreeShape.LEAF_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    if (naturalLeaves(w.state(x + dx, y + dy, z + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean naturalLeaves(@Nullable BlockState s) {
        return s != null && s.is(BlockTags.LEAVES) && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT);
    }

    /**
     * The tree whose trunk starts at {@code start}, as {@link TreeShape} sees it in the world. With {@code zones}, wood
     * that is not the woodcutter's to fell ({@link WorkSafety#protectedWood}, at the start or any log) counts as a
     * structure the tree touches; without, only the tree's shape counts. {@code resume} is for a tree the woodcutter
     * has started on: its top is gone, so the size, leaf and soil rules are skipped.
     */
    public static TreeShape.Tree survey(ServerLevel level, CampaignData data, @Nullable BaseState own, BlockPos start, boolean zones,
                                        boolean resume) {
        return survey(level, data, own, start, zones, resume, TreeShape.MAX_FELL_DY);
    }

    /** As {@link #survey(ServerLevel, CampaignData, BaseState, BlockPos, boolean, boolean)}, reaching logs up to {@code maxDy}. */
    public static TreeShape.Tree survey(ServerLevel level, CampaignData data, @Nullable BaseState own, BlockPos start, boolean zones,
                                        boolean resume, int maxDy) {
        if (WorkSafety.planned(data, start) || (zones && WorkSafety.protectedWood(level, data, own, start))) {
            return new TreeShape.Tree(TreeShape.Verdict.TOUCHES_STRUCTURE, List.of(), false, 0);
        }
        TreeShape.Cells cells = cells(level, data, own, level.getBlockState(start).getBlock(), zones);
        return TreeShape.survey(cells, start.getX(), start.getY(), start.getZ(), resume, maxDy);
    }

    /**
     * Where the scaffold pillar for a tall tree stands (see {@link TreeShape#pillarSpot}): the stump {@code stump}
     * (the north-west corner of a 2x2 trunk), its logs {@code logs}. With {@code groundToo} false every block of
     * {@code logs} must come within reach from the pillar (scaffold blocks left standing, taken down from beside).
     */
    public static TreeShape.Pillar pillarSpot(ServerLevel level, CampaignData data, @Nullable BaseState own, BlockPos stump, boolean big,
                                              List<BlockPos> logs, boolean groundToo) {
        Set<Long> wood = new HashSet<>();
        List<int[]> cells = new ArrayList<>();
        for (BlockPos p : logs) {
            wood.add(p.asLong());
            cells.add(new int[]{p.getX(), p.getY(), p.getZ()});
        }
        int y = stump.getY();
        return TreeShape.pillarSpot(cells, stump.getX(), y, stump.getZ(), big,
                (x, z) -> pillarRoom(level, data, own, new BlockPos(x, y, z), wood), groundToo);
    }

    /**
     * How high a scaffold pillar may be built in the column whose lowest cell is {@code base} (see
     * {@link TreeShape.Room}): firm ground under it (no leaves, no log, no fluid), wood that is the woodcutter's to
     * fell there ({@link WorkSafety#protectedWood}: no pillar by the walls, in other factions' land or by a known
     * village), and every cell from {@code base} up clear: air or a plant that gives way, natural leaves, or one of
     * {@code wood} (the tree's own logs, cut on the way up). No cell may be part of a base's plan or unloaded. Negative
     * when no pillar can stand there.
     */
    static int pillarRoom(ServerLevel level, CampaignData data, @Nullable BaseState own, BlockPos base, Set<Long> wood) {
        BlockPos ground = base.below();
        if (!level.isLoaded(ground) || WorkSafety.protectedWood(level, data, own, base)) {
            return -1;
        }
        BlockState g = level.getBlockState(ground);
        if (!g.isFaceSturdy(level, ground, net.minecraft.core.Direction.UP) || g.is(BlockTags.LEAVES) || g.is(BlockTags.LOGS)
                || !g.getFluidState().isEmpty() || wood.contains(ground.asLong())) {
            return -1;
        }
        int clear = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < TreeShape.MAX_PILLAR + 2; dy++) {
            m.set(base.getX(), base.getY() + dy, base.getZ());
            if (!level.isLoaded(m) || WorkSafety.planned(data, m)) {
                break;
            }
            BlockState s = level.getBlockState(m);
            boolean open = (s.isAir() || s.canBeReplaced()) && s.getFluidState().isEmpty() && !s.hasBlockEntity();
            if (!open && !naturalLeaves(s) && !wood.contains(m.asLong())) {
                break;
            }
            clear++;
        }
        return clear - 2;
    }

    /**
     * The world as {@link TreeShape} sees it, for a tree of block {@code log}: anything in a base's plan, any block
     * entity (chests, signs, beds, bee nests: no angry bees), planks, wooden slabs, stairs, fences, gates, doors and
     * trapdoors, walls, glass, wool, carpets, beds, cobblestone, bricks, bookshelves, hay, torches, lanterns, signs and
     * rails are STRUCTURE, and so is anything not loaded; logs of another kind are OTHER_LOG; leaves are split by
     * PERSISTENT; soil (#dirt, mud and muddy mangrove roots included) is GROUND; stone, vines, cocoa and the rest are
     * OTHER. This keeps faction frame logs, old hut corners and village houses standing.
     */
    public static TreeShape.Cells cells(ServerLevel level, CampaignData data, @Nullable BaseState own, Block log, boolean zones) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            m.set(x, y, z);
            if (!level.isLoaded(m) || WorkSafety.planned(data, m)) {
                return TreeShape.Kind.STRUCTURE; // unseen, or part of a base: never felled into
            }
            BlockState s = level.getBlockState(m);
            if (s.isAir()) {
                return TreeShape.Kind.AIR;
            }
            if (s.hasBlockEntity()) {
                return TreeShape.Kind.STRUCTURE;
            }
            if (s.is(BlockTags.LOGS)) {
                if (zones && WorkSafety.protectedWood(level, data, own, m.immutable())) {
                    return TreeShape.Kind.STRUCTURE;
                }
                return s.is(log) ? TreeShape.Kind.SAME_LOG : TreeShape.Kind.OTHER_LOG;
            }
            if (s.is(BlockTags.LEAVES)) {
                return s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT)
                        ? TreeShape.Kind.PERSISTENT_LEAVES : TreeShape.Kind.NATURAL_LEAVES;
            }
            if (WorkSafety.builtBlock(s)) {
                return TreeShape.Kind.STRUCTURE;
            }
            return s.is(BlockTags.DIRT) ? TreeShape.Kind.GROUND : TreeShape.Kind.OTHER;
        };
    }

    /** True when {@code p} lies in the base's work belt (its MIN/MAX box, any height), which is kept loaded and ticking. */
    public static boolean inBelt(BaseState b, BlockPos p) {
        BlockPos min = b.point(BaseState.MIN), max = b.point(BaseState.MAX);
        return min != null && max != null && p.getX() >= min.getX() && p.getX() <= max.getX() && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
    }

    /**
     * Looks for grove spots of one base: lattice points ({@link ForestRules#GROVE_SPACING} apart) inside the base's
     * work belt and within the forest radius (so the grown trees are felled), the walls' side first, where a sapling
     * may stand by {@link ForestRules#groveSpotAllowed} and the
     * ground allows it: soil under open sky, the spot free, nothing growing within 2 blocks, no part of a base's plan,
     * nothing else reserved there. At most {@link #POINTS_PER_CALL} points are looked at per call; a found spot is
     * kept until it is planted or no longer fits; after a full round with nothing found it rests a while. GameTest
     * bases have no such spots, since their belt lies within the walls rule.
     */
    public static final class GroveSearch {
        /** PROPOSED: lattice points looked at per call, and how long a fruitless round rests. */
        static final int POINTS_PER_CALL = 200, REST_TICKS = 600;
        /** Nothing may grow this close (sideways) to a grove spot, from its level to 4 above. */
        static final int CLEARANCE = 2, CLEAR_HEIGHT = 4;
        private int baseId = Integer.MIN_VALUE;
        private List<int[]> points = List.of();
        private int cursor;
        private BlockPos found;
        private long restUntil;
        private final Set<Long> rejected = new HashSet<>();

        /** A grove spot (the cell the sapling goes into) for base {@code b}, or null. */
        public BlockPos next(ServerLevel level, CampaignData data, BaseState b) {
            return next(level, data, b, p -> false);
        }

        /**
         * A grove spot for base {@code b} that {@code skip} does not pass over (one the woodcutter could not get to
         * for now, one too far out tonight), or null.
         */
        public BlockPos next(ServerLevel level, CampaignData data, BaseState b, Predicate<BlockPos> skip) {
            if (b.id != baseId) {
                baseId = b.id;
                points = lattice(b);
                cursor = 0;
                found = null;
                restUntil = 0;
                rejected.clear();
            }
            if (found != null) {
                if (!skip.test(found) && fits(level, data, b, found.getX(), found.getZ()) != null) {
                    return found;
                }
                found = null;
            }
            long now = level.getGameTime();
            if (now < restUntil && restUntil - now <= REST_TICKS || points.isEmpty()) {
                return null;
            }
            for (int i = 0; i < POINTS_PER_CALL && i < points.size(); i++) {
                int[] p = points.get(cursor);
                cursor = (cursor + 1) % points.size();
                BlockPos at = fits(level, data, b, p[0], p[1]);
                if (at != null && !skip.test(at)) {
                    found = at;
                    return at;
                }
                if (cursor == 0) {
                    restUntil = now + REST_TICKS; // a full round found nothing: look again in a while
                    return null;
                }
            }
            return null;
        }

        /**
         * The spot did not take a sapling after all (the ground would not hold it): never offered again. A spot that
         * was only too far to walk to is passed over through {@code skip} instead, for a while.
         */
        public void reject(BlockPos p) {
            rejected.add(p.asLong());
            if (p.equals(found)) {
                found = null;
            }
        }

        /**
         * Lattice points of the base's belt that pass the pure rules except ground and reservations, walls' side
         * first. Only points within the woodcutters' forest radius count: a grove tree is felled and replanted like
         * any other, and the tree search reaches no further (the belt's far east end lies beyond it).
         */
        private static List<int[]> lattice(BaseState b) {
            List<int[]> out = new ArrayList<>();
            BlockPos min = b.point(BaseState.MIN), max = b.point(BaseState.MAX);
            if (min == null || max == null) {
                return out;
            }
            int cx = b.center.getX(), cz = b.center.getZ(), half = BaseZones.half(b);
            long r = BaseZones.workRadius(b, FCConfig.FOREST_RADIUS.get());
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    long dx = x - cx, dz = z - cz;
                    if (dx * dx + dz * dz <= r * r
                            && ForestRules.groveSpotAllowed(x - cx, z - cz, half, true, ForestRules.Ground.DIRT, false)) {
                        out.add(new int[]{x, z});
                    }
                }
            }
            out.sort(Comparator.comparingInt(p -> Math.max(Math.abs(p[0] - cx), Math.abs(p[1] - cz))));
            return out;
        }

        /** The cell a sapling would go into at column (x, z), when every grove rule holds there; else null. */
        private BlockPos fits(ServerLevel level, CampaignData data, BaseState b, int x, int z) {
            if (!MapBounds.inside(data, x, z, 0)) {
                return null;
            }
            LevelChunk c = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (c == null) {
                return null;
            }
            int gy = c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
            BlockPos g = new BlockPos(x, gy, z);
            BlockState ground = level.getBlockState(g);
            if (ground.canBeReplaced() && ground.getFluidState().isEmpty() && !ground.isAir()) {
                g = g.below(); // a snow layer or tall grass on the ground
                ground = level.getBlockState(g);
            }
            BlockPos spot = g.above();
            if (rejected.contains(spot.asLong())) {
                return null;
            }
            ForestRules.Ground kind = ground.is(Blocks.FARMLAND) ? ForestRules.Ground.FARMLAND
                    : ground.is(Blocks.DIRT_PATH) ? ForestRules.Ground.PATH
                    : ground.is(BlockTags.DIRT) ? ForestRules.Ground.DIRT : ForestRules.Ground.OTHER;
            if (!ForestRules.groveSpotAllowed(x - b.center.getX(), z - b.center.getZ(), BaseZones.half(b), inBelt(b, spot), kind, false)) {
                return null;
            }
            BlockState at = level.getBlockState(spot);
            if (!(at.isAir() || at.canBeReplaced()) || !at.getFluidState().isEmpty() || !level.canSeeSky(spot)) {
                return null;
            }
            for (int dx = -CLEARANCE; dx <= CLEARANCE; dx++) {
                for (int dz = -CLEARANCE; dz <= CLEARANCE; dz++) {
                    for (int dy = 0; dy <= CLEAR_HEIGHT; dy++) {
                        BlockState s = level.getBlockState(spot.offset(dx, dy, dz));
                        if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.getBlock() instanceof SaplingBlock) {
                            return null;
                        }
                    }
                }
            }
            if (WorkSafety.planned(data, spot) || WorkSafety.planned(data, g) || WorkSafety.reserved(data, b, spot, WorkSafety.Use.GROVE)) {
                return null;
            }
            return spot;
        }
    }

    /**
     * Which woodcutter is felling which trunk, across every faction, so that two never fell one tree: a claim lasts
     * {@link #CLAIM_TICKS}, is renewed while the woodcutter chops, and is let go when the tree is done or given up. It is
     * not saved: the woodcutter's own saved tree is claimed again when it goes back to it.
     */
    public static final class TreeClaims {
        /** PROPOSED: how long a claim on a trunk lasts without being renewed. */
        public static final int CLAIM_TICKS = 1200;
        private static final Map<Long, Claim> CLAIMS = new ConcurrentHashMap<>();

        private record Claim(UUID who, long until) {
        }

        private TreeClaims() {
        }

        private static Claim live(BlockPos stump, long now) {
            Claim c = CLAIMS.get(stump.asLong());
            // a claim that ran out, or one from a world with a later clock (a reopened single-player world), is void
            if (c != null && (now >= c.until() || c.until() - now > CLAIM_TICKS)) {
                CLAIMS.remove(stump.asLong(), c);
                return null;
            }
            return c;
        }

        /** True if another woodcutter holds a live claim on this trunk. */
        public static boolean claimedByOther(BlockPos stump, UUID me, long now) {
            Claim c = live(stump, now);
            return c != null && !c.who().equals(me);
        }

        /** Claims (or renews the claim on) this trunk; false if another woodcutter holds it. */
        public static boolean claim(BlockPos stump, UUID me, long now) {
            if (claimedByOther(stump, me, now)) {
                return false;
            }
            CLAIMS.put(stump.asLong(), new Claim(me, now + CLAIM_TICKS));
            return true;
        }

        /** Lets go of this trunk, if it is this woodcutter's. */
        public static void release(BlockPos stump, UUID me) {
            if (stump == null) {
                return;
            }
            Claim c = CLAIMS.get(stump.asLong());
            if (c != null && c.who().equals(me)) {
                CLAIMS.remove(stump.asLong(), c);
            }
        }
    }

    /** Block reads of loaded chunks only (unloaded: null), with a per-search chunk cache. */
    private static final class Loaded {
        private final ServerLevel level;
        private final Map<Long, LevelChunk> chunks = new HashMap<>();
        private final Set<Long> unloaded = new HashSet<>();
        private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        Loaded(ServerLevel level) {
            this.level = level;
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

        /** The top block of the column, leaves not counted, or MIN_VALUE when its chunk is not loaded. */
        int top(int x, int z) {
            LevelChunk c = chunk(x, z);
            return c == null ? Integer.MIN_VALUE : c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
        }

        BlockState state(int x, int y, int z) {
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                return null;
            }
            LevelChunk c = chunk(x, z);
            return c == null ? null : c.getBlockState(m.set(x, y, z));
        }
    }
}
