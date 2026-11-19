package com.factionclash.hero;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.Diplomacy;
import com.factionclash.campaign.FactionState;
import com.factionclash.logic.hero.FlightPlanner;
import com.factionclash.logic.hero.HeroLinks;
import com.factionclash.logic.hero.MobilityRules;
import com.factionclash.npc.NpcNavigator;
import com.factionclash.npc.NpcPathNavigation;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * A hero's path search (MOBILITY_AND_WORK 3.2, 3.7 and 3.9; task A11): the faction NPCs' gate-aware walking, plus the
 * hero's links of {@link HeroLinks} where the vanilla neighbour fails (a hole, a wall, a fluid), at most 8 per node:
 * jump-ups onto ledges, gap leaps the {@code LeapSolver} lands, drops to the safe fall distance, climbs up a face and
 * down one, chimneys and glides, all within the hero's numbers at its level now. The walls of any base, a hostile
 * one's included, are terrain like any other; out of a raid or a fight, a route pays three times as much inside a
 * hostile base's walls box and 16 blocks around it (a berth, not a ban). Links never land on leaves or a block that
 * hurts, and a link that failed (the hero did not land where it should) is left out for 10 minutes.
 *
 * <p>The search reads blocks through {@link WorldGrid}: loaded chunks only (an unloaded one counts as solid, so no link
 * reaches into it) and each block once per search. Vanilla's {@code Path} holds plain nodes, so the links of a search
 * are kept by their two nodes and {@link HeroGroundNavigation} reads the ones its path uses. A hero with no mobility
 * (a format-1 hero, a dormant one, {@code mobility.enabled = false}) searches exactly as a worker does.
 */
public final class HeroNodeEvaluator extends NpcPathNavigation.GateEvaluator {
    /** Where a search starts the grid's void: this far under the start (a deeper floor is as good as none). */
    static final int FLOOR_SEARCH = 80;

    private final HeroGroundNavigation nav;
    private final WorldGrid grid = new WorldGrid();
    /** The links offered in the search that runs now (or ran last), by their two nodes. */
    private final Long2ObjectOpenHashMap<HeroLinks.Link> edges = new Long2ObjectOpenHashMap<>();
    /** Hostile bases' walls boxes, grown by the berth margin, when the situation gives them a berth. */
    private final List<FlightPlanner.Box> berth = new ArrayList<>();
    private HeroEntity hero;
    private MobilityRules.Profile profile = MobilityRules.NONE;
    private MobilityRules.Situation situation = MobilityRules.Situation.TRAVEL;
    /**
     * A raid's target whose walls no link may cross into in this search (the review of A10-A12: a hero whose way in is
     * the gate, {@link HeroRaid#wallsClosedTo}), as its centre and half size; {@code closedHalf < 0}: none.
     */
    private int closedX, closedZ, closedHalf = -1;
    /** Links offered in all searches (tests and the sheet), and links left out because they crossed a closed wall. */
    public int offered, closedRefused;

    HeroNodeEvaluator(HeroGroundNavigation nav) {
        this.nav = nav;
    }

    // ------------------------------------------------------------------ one search

    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        edges.clear();
        berth.clear();
        hero = mob instanceof HeroEntity h ? h : null;
        profile = hero == null || hero.level().isClientSide ? MobilityRules.NONE : hero.movement().profile();
        if (!profile.any()) {
            return;
        }
        situation = situation(hero);
        BaseState closed = hero.raid().wallsClosedTo();
        closedHalf = closed == null ? -1 : BaseZones.half(closed);
        if (closed != null) {
            closedX = closed.center.getX();
            closedZ = closed.center.getZ();
        }
        grid.reset(hero.level(), Math.max(hero.level().getMinBuildHeight(), hero.getBlockY() - FLOOR_SEARCH));
        if (HeroLinks.berth(situation, true) > 1) {
            hostileBases(hero, berth);
        }
    }

    /** After the search: the block cache goes (the links stay until the navigation has read its path's). */
    void finish() {
        grid.clear();
    }

    /**
     * The situation the links are judged in (3.3): escaping; in a fight (a target, an engaged enemy, a hostile within
     * 16 blocks, a player or an NPC or a monster alike, or hurt in the last 5 s); on a raid; else a trip.
     */
    static MobilityRules.Situation situation(HeroEntity hero) {
        HeroBoss boss = hero.combat().boss();
        if (boss.escape().running()) {
            return MobilityRules.Situation.ESCAPE;
        }
        LivingEntity t = hero.getTarget();
        boolean goal = t != null && t.isAlive() || boss.fighting();
        double nearest = goal ? 0 : nearestHostile(hero);
        if (HeroLinks.inCombat(goal, nearest, (int) Math.min(Integer.MAX_VALUE, hero.ticksSinceHurt()))) {
            return MobilityRules.Situation.FIGHT;
        }
        return onRaid(hero) ? MobilityRules.Situation.RAID : MobilityRules.Situation.TRAVEL;
    }

    /** The nearest enemy within the combat range (a player, an NPC, a monster: whoever is hostile to it), or infinity. */
    static double nearestHostile(HeroEntity hero) {
        double r = HeroLinks.COMBAT_HOSTILE_RANGE;
        double best = Double.POSITIVE_INFINITY;
        for (LivingEntity e : hero.level().getEntitiesOfClass(LivingEntity.class, hero.getBoundingBox().inflate(r),
                e -> e != hero && e.isAlive() && hero.isEnemy(e)
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            best = Math.min(best, e.distanceTo(hero));
        }
        return best;
    }

    static boolean onRaid(HeroEntity hero) {
        if (hero.getServer() == null || hero.getFaction() == null) {
            return false;
        }
        FactionState f = CampaignData.get(hero.getServer()).factions.get(hero.getFaction());
        return f != null && f.raiders.contains(hero.getUUID());
    }

    /** Every standing base hostile to the hero's faction: its walls box grown by the berth margin. */
    static void hostileBases(HeroEntity hero, List<FlightPlanner.Box> out) {
        if (hero.getServer() == null || hero.getFaction() == null) {
            return;
        }
        CampaignData data = CampaignData.get(hero.getServer());
        for (BaseState b : data.bases.values()) {
            if (b.destroyed || b.faction == null || !Diplomacy.hostile(hero.getServer(), hero.getFaction(), b.faction)) {
                continue;
            }
            int h = BaseZones.half(b);
            out.add(new FlightPlanner.Box(b.center.getX() - h, b.center.getZ() - h, b.center.getX() + h + 1,
                    b.center.getZ() + h + 1).grow(HeroLinks.BERTH_MARGIN));
        }
    }

    // ------------------------------------------------------------------ neighbours

    @Override
    public int getNeighbors(Node[] out, Node node) {
        int n = super.getNeighbors(out, node);
        if (!profile.any() || hero == null) {
            return n;
        }
        HeroLinks.Pos from = new HeroLinks.Pos(node.x, node.y, node.z);
        for (HeroLinks.Link l : HeroLinks.links(grid, from, profile, situation)) {
            if (n >= out.length) {
                break;
            }
            HeroLinks.Pos t = l.to();
            if (nav.dead(from, t) || !landing(t)) {
                continue;
            }
            if (closedHalf >= 0 && insideClosed(t.x(), t.z()) && !insideClosed(from.x(), from.z())) {
                closedRefused++;
                continue; // over a raid target's wall with no way out chosen: the gate with the soldiers (5.2)
            }
            Node to = getNode(t.x(), t.y(), t.z());
            if (to.closed || contains(out, n, to)) {
                continue; // walking gets there already
            }
            PathType type = getCachedPathType(t.x(), t.y(), t.z());
            float malus = mob.getPathfindingMalus(type);
            if (malus < 0) {
                continue;
            }
            to.type = type;
            to.costMalus = Math.max(to.costMalus, malus);
            out[n++] = to;
            edges.put(key(node, to), l);
            offered++;
        }
        return n;
    }

    /** Within the closed base's walls box (the wall ring included), at any height. */
    private boolean insideClosed(int x, int z) {
        return com.factionclash.logic.Zones.insideHalf(x - closedX, z - closedZ, closedHalf, 0);
    }

    private static boolean contains(Node[] out, int n, Node x) {
        for (int i = 0; i < n; i++) {
            if (out[i] == x) {
                return true;
            }
        }
        return false;
    }

    /** A link's landing: never on leaves (a canopy is no ground to walk off) nor on a block that hurts. */
    private boolean landing(HeroLinks.Pos t) {
        BlockState below = grid.state(t.x(), t.y() - 1, t.z());
        return below != null && !below.is(BlockTags.LEAVES) && !NpcNavigator.isDangerous(below);
    }

    static long key(Node a, Node b) {
        return ((long) a.hashCode() << 32) | (b.hashCode() & 0xffffffffL);
    }

    /** The link between two nodes of the last search, or null for a step of plain walking. */
    HeroLinks.Link edge(Node a, Node b) {
        if (edges.isEmpty()) {
            return null;
        }
        HeroLinks.Link l = edges.get(key(a, b));
        return l != null && l.from().x() == a.x && l.from().y() == a.y && l.from().z() == a.z && l.to().x() == b.x
                && l.to().y() == b.y && l.to().z() == b.z ? l : null;
    }

    /**
     * The cost of a step: a link's own cost (3.2) instead of the distance, times three inside a hostile base's berth
     * on a trip or an escape (3.7).
     */
    float stepCost(Node a, Node b) {
        HeroLinks.Link l = edge(a, b);
        float d = l != null ? (float) l.cost() : a.distanceTo(b);
        if (!berth.isEmpty()) {
            for (FlightPlanner.Box box : berth) {
                if (b.x + 0.5 > box.minX() && b.x + 0.5 < box.maxX() && b.z + 0.5 > box.minZ() && b.z + 0.5 < box.maxZ()) {
                    return d * (float) HeroLinks.BERTH_COST;
                }
            }
        }
        return d;
    }

    /** The hero's mobility the last search was made with (tests). */
    public MobilityRules.Profile profile() {
        return profile;
    }

    /** The situation of the last search (tests). */
    public MobilityRules.Situation lastSituation() {
        return situation;
    }

    // ------------------------------------------------------------------ the world as the links see it

    /**
     * The blocks as {@link HeroLinks.Cell}s: loaded chunks only (an unloaded one reads as solid: no link reaches into
     * it, and nothing is loaded or generated), each block read once per search.
     */
    static final class WorldGrid implements HeroLinks.Grid {
        private static final HeroLinks.Cell[] CELLS = HeroLinks.Cell.values();
        private final Long2ByteOpenHashMap cache = new Long2ByteOpenHashMap();
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private Level level;
        private int minY;
        private long chunkKey = Long.MIN_VALUE;
        private LevelChunk chunk;

        WorldGrid() {
            cache.defaultReturnValue((byte) -1);
        }

        void reset(Level level, int minY) {
            this.level = level;
            this.minY = minY;
            cache.clear();
            chunk = null;
            chunkKey = Long.MIN_VALUE;
        }

        void clear() {
            cache.clear();
            chunk = null;
            chunkKey = Long.MIN_VALUE;
        }

        @Override
        public int minY() {
            return minY;
        }

        /** The block at a cell when its chunk is loaded (null: not loaded or outside the world). */
        BlockState state(int x, int y, int z) {
            if (level == null || y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                return null;
            }
            long k = (long) (x >> 4) << 32 | ((z >> 4) & 0xffffffffL);
            if (k != chunkKey) {
                chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                chunkKey = k;
            }
            return chunk == null ? null : chunk.getBlockState(cursor.set(x, y, z));
        }

        @Override
        public HeroLinks.Cell at(int x, int y, int z) {
            long k = BlockPos.asLong(x, y, z);
            byte b = cache.get(k);
            if (b >= 0) {
                return CELLS[b];
            }
            HeroLinks.Cell c = read(x, y, z);
            cache.put(k, (byte) c.ordinal());
            return c;
        }

        private HeroLinks.Cell read(int x, int y, int z) {
            if (level != null && y >= level.getMaxBuildHeight()) {
                return HeroLinks.Cell.AIR;
            }
            BlockState s = state(x, y, z);
            if (s == null) {
                return HeroLinks.Cell.SOLID;
            }
            FluidState fl = s.getFluidState();
            if (fl.is(FluidTags.LAVA)) {
                return HeroLinks.Cell.LAVA;
            }
            if (s.is(BlockTags.FIRE)) {
                return HeroLinks.Cell.FIRE;
            }
            if (s.is(Blocks.POWDER_SNOW)) {
                return HeroLinks.Cell.POWDER_SNOW;
            }
            if (s.is(Blocks.COBWEB)) {
                return HeroLinks.Cell.COBWEB;
            }
            VoxelShape shape = s.getCollisionShape(level, cursor.set(x, y, z));
            if (shape.isEmpty()) {
                return fl.is(FluidTags.WATER) ? HeroLinks.Cell.WATER : HeroLinks.Cell.AIR;
            }
            return shape.max(Direction.Axis.Y) > 1.0 + 1e-6 ? HeroLinks.Cell.FENCE : HeroLinks.Cell.SOLID;
        }
    }
}
