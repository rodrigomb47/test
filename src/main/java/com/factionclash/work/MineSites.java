package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.MapBounds;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.MinePlanner;
import com.factionclash.logic.MineSiteRules;
import com.factionclash.logic.MineSiteRules.Rect;
import com.factionclash.npc.FactionNpc;
import com.factionclash.structure.MineLayout;
import com.factionclash.structure.Terrain;
import com.factionclash.util.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mine sites of the campaign, as the world sees them: where a miner without a mine may dig its own
 * ({@link Search}, {@link #evaluate}, {@link #create}), which mines a base has open, which mine an NPC is standing
 * in, whether a mine's tunnels may be lengthened, and the only ways a mine is set aside (worked out or given up for
 * good, or resting until a better pickaxe). Records of set-aside mines are kept: their stairs stay protected, a
 * resting mine keeps its whole corridor, and a mine set aside for good keeps the ground it really dug.
 */
public final class MineSites {
    /** PROPOSED: how far a mine's corridor keeps from other things (blocks, Chebyshev, on the ground). */
    static final int CORRIDOR_GAP = 2, WALLS_GAP = 4, BASE_GAP = 8, LAIR_GAP = 16, MAP_MARGIN = 8;
    /**
     * PROPOSED: the site search looks at points every {@link #GRID} blocks on rings from the walls plus
     * {@link #RING_MARGIN} out to the mine site radius, {@link #PER_TICK} of them a tick.
     */
    public static final int GRID = 4, RING_MARGIN = 8, PER_TICK = 64;
    /**
     * PROPOSED: a mine's mouth keeps this far (Chebyshev) beyond every base's walls, out of other bases' land, from
     * villages the faction knows of, from every other mine's mouth and from the miner's own bad sites, and inside
     * the map edge (and {@link BaseZones#DANGER_RADIUS} from known pillager outposts and woodland mansions).
     */
    static final int SITE_WALLS_MARGIN = 8, SITE_FOREIGN_MARGIN = 16, VILLAGE_RADIUS = 48, MINE_SPACING = 24,
            BAD_SITE_RADIUS = 8, SITE_MAP_MARGIN = 16;
    /** PROPOSED: a tunnel direction is given up when its corridor scores this much water and dipping ground. */
    static final int MAX_PENALTY = 30;
    /** PROPOSED: points of corridor penalty for water over a sampled spot, and for ground dipping toward the tunnels. */
    static final int WATER_PENALTY = 6, DIP_PENALTY = 3, PENALTY_STEP = 6;
    /** Evaluated points that must all have failed only the depth rule before the ground is called too shallow. */
    static final int SHALLOW_PROOF = 8;

    private MineSites() {
    }

    /** The note of a mine whose every reachable layer is dug. */
    public static final String WORKED_OUT = "every layer it can reach is dug";

    /**
     * The mine is worked out for good: every layer it can reach is dug. Nobody works it any more and it is not taken
     * up again.
     */
    public static void retire(ServerLevel level, CampaignData data, WorkplaceState wp, String why) {
        setAside(level, data, wp, why, false);
    }

    /**
     * The mine is given up before it was worked out (its mouth cannot be reached, the ground below is unsafe, its
     * stairs are blocked): set aside for good like a worked-out one, but announced and listed as given up.
     */
    public static void giveUp(ServerLevel level, CampaignData data, WorkplaceState wp, String why) {
        setAside(level, data, wp, why, true);
    }

    /** The only place that marks a mine worked out (or given up). */
    private static void setAside(ServerLevel level, CampaignData data, WorkplaceState wp, String why, boolean givenUp) {
        if (wp.workedOut) {
            return;
        }
        wp.workedOut = true;
        wp.givenUp = givenUp;
        wp.upgradeStep = -1;
        wp.note = why == null ? "" : why;
        Workplaces.release(data, wp);
        Workplaces.releaseHelper(data, wp); // a helper climbs out and looks for work again
        data.setDirty();
        BaseState b = data.base(wp.baseId);
        if (b != null && level != null) {
            Messages.toFaction(level.getServer(), b.faction, Component.literal("Mine #" + wp.id + (givenUp ? " is given up (" : " is worked out (")
                    + wp.note + ")"));
        }
    }

    /**
     * Nothing in the mine can be dug with the pickaxes at hand: it rests until a miner with a pickaxe of {@code tier}
     * or better takes it up again.
     */
    public static void rest(ServerLevel level, CampaignData data, WorkplaceState wp, int tier) {
        wp.restTier = Math.max(1, tier);
        wp.upgradeStep = -1;
        Workplaces.release(data, wp);
        Workplaces.releaseHelper(data, wp);
        data.setDirty();
        BaseState b = data.base(wp.baseId);
        if (b != null && level != null) {
            Messages.toFaction(level.getServer(), b.faction, Component.literal("Mine #" + wp.id + " rests until " + pickName(wp.restTier)));
        }
    }

    /**
     * The band a level was opened for; for a level of an old mine (no band saved), the band whose height it is nearest
     * to. {@code minY} is the world's bottom.
     */
    public static MinePlanner.Band bandOf(WorkplaceState wp, WorkplaceState.MineLevel lv, int minY) {
        MinePlanner.Band b = MinePlanner.Band.byOrdinal(lv.band);
        return b != null ? b : MinePlanner.bandAt(wp.origin.getY() - lv.step, wp.origin.getY(), minY);
    }

    /**
     * One line per mine for the station screen: an open mine with its miner and helper (if any), its levels (height,
     * ore, progress, lengthening rounds; the holder's level first, then the helper's), how deep its shaft goes and its
     * output; worked-out, given-up and resting mines together on one line.
     */
    public static List<String> describe(CampaignData data, BaseState b, int minY, java.util.function.Function<WorkplaceState, String> who) {
        List<String> out = new ArrayList<>();
        List<String> done = new ArrayList<>(), abandoned = new ArrayList<>();
        long doneOutput = 0;
        java.util.Map<Integer, List<String>> resting = new java.util.TreeMap<>();
        for (int id : b.workplaces) {
            WorkplaceState w = data.workplace(id);
            if (w == null || w.type != WorkplaceState.Type.MINE) {
                continue;
            }
            if (w.workedOut) {
                (w.givenUp ? abandoned : done).add("#" + w.id);
                doneOutput += w.totalOutput;
                continue;
            }
            if (w.restTier > 0) {
                resting.computeIfAbsent(w.restTier, k -> new ArrayList<>()).add("#" + w.id);
                continue;
            }
            int y0 = w.origin.getY();
            // a second miner helping in the mine, and the level it works
            WorkplaceState.MineLevel helped = Workplaces.helperValid(data, w) ? w.helperLevel() : null;
            String helper = helped == null ? "" : ", helped by " + data.record(w.helper).name + " on y=" + (y0 - helped.step);
            StringBuilder sb = new StringBuilder("Mine #" + w.id + " at " + w.origin.toShortString() + " facing "
                    + w.facing.getSerializedName() + " (" + who.apply(w) + helper + "): ");
            List<WorkplaceState.MineLevel> order = new ArrayList<>(w.levels);
            WorkplaceState.MineLevel cur = MineLayout.current(w);
            if (helped != null && helped != cur) {
                order.remove(helped);
                order.add(0, helped);
            }
            if (cur != null) {
                order.remove(cur);
                order.add(0, cur); // the level being worked first, then the helper's
            }
            List<String> parts = new ArrayList<>();
            for (WorkplaceState.MineLevel lv : order) {
                parts.add("y=" + (y0 - lv.step) + " " + bandOf(w, lv, minY).label + " " + lv.progress + "/"
                        + MineLayout.tunnelPlan(lv).size() + " r" + lv.rounds + "/" + w.maxRounds + (lv.cut ? " cut" : ""));
            }
            sb.append(parts.isEmpty() ? "no levels yet" : String.join(", ", parts));
            sb.append("; shaft to y=").append(y0 - w.bottom);
            if (w.upgrading() && cur != null) {
                sb.append(", digging down to y=").append(y0 - cur.step).append(" step ").append(w.upgradeStep);
            }
            sb.append("; output ").append(w.totalOutput);
            out.add(sb.toString());
        }
        List<String> tail = new ArrayList<>();
        if (!done.isEmpty()) {
            tail.add("Worked-out mines: " + String.join(", ", done) + (abandoned.isEmpty() ? " (output " + doneOutput + ")" : ""));
        }
        if (!abandoned.isEmpty()) {
            // a mine given up before it was worked out (often before anything was dug) is not called worked out
            tail.add((done.isEmpty() ? "Given-up mines: " : "given up: ") + String.join(", ", abandoned) + " (output " + doneOutput + ")");
        }
        resting.forEach((tier, ids) -> tail.add("resting until " + pickName(tier) + ": " + String.join(", ", ids)));
        if (!tail.isEmpty()) {
            String line = String.join("; ", tail);
            out.add(Character.toUpperCase(line.charAt(0)) + line.substring(1));
        }
        return out;
    }

    /** "a stone pickaxe", "an iron pickaxe" or "a diamond pickaxe" for a pickaxe tier. */
    public static String pickName(int tier) {
        return switch (tier) {
            case 0 -> "a wooden pickaxe";
            case 1 -> "a stone pickaxe";
            case 2 -> "an iron pickaxe";
            default -> "a diamond pickaxe";
        };
    }

    /**
     * True if {@code p} is inside the mine: in its old surface hut, or underground in it ({@link MineLayout#inMine}),
     * that is in the shaft, or in the corridor with ground above it. Ground that dips below the mine's mouth
     * somewhere above its tunnels has nothing over it (at most a tree's leaves), so a miner walking there is not
     * taken for one in the tunnels.
     */
    public static boolean inside(ServerLevel level, WorkplaceState wp, BlockPos p) {
        if (MineLayout.inHut(wp, p)) {
            return true;
        }
        return MineLayout.inMine(wp, p) && (MineLayout.nearStairs(wp, p) || underground(level, p));
    }

    /**
     * True if something solid (or water) lies above {@code p}: it is under the ground, not on it. Leaves do not
     * count, so the floor of a wood is still the surface; the sky light would say otherwise under a canopy.
     */
    public static boolean underground(ServerLevel level, BlockPos p) {
        return p.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
    }

    /** The base's mine (worked out or not) that {@code pos} is {@link #inside}, or null. */
    public static WorkplaceState mineAt(ServerLevel level, CampaignData data, BaseState base, BlockPos pos) {
        if (base == null || pos == null) {
            return null;
        }
        for (int id : base.workplaces) {
            WorkplaceState w = data.workplace(id);
            if (w != null && w.type == WorkplaceState.Type.MINE && w.origin != null && inside(level, w, pos)) {
                return w;
            }
        }
        return null;
    }

    /** Mines of the base that are being worked: neither worked out nor resting. */
    public static int openMines(CampaignData data, BaseState base) {
        int n = 0;
        for (int id : base.workplaces) {
            WorkplaceState w = data.workplace(id);
            if (w != null && w.type == WorkplaceState.Type.MINE && w.active()) {
                n++;
            }
        }
        return n;
    }

    /** The ground a mine reserves for its tunnels (see {@link MineSiteRules#corridor}). */
    public static Rect corridor(WorkplaceState w) {
        return MineSiteRules.corridor(w.origin.getX(), w.origin.getZ(), w.facing.getStepX(), w.facing.getStepZ(), MineLayout.reachRounds(w));
    }

    /**
     * The ground a mine keeps from other mines' corridors ({@link MineSiteRules#keptRounds}): an open or resting
     * mine its whole corridor; one set aside for good only what it really dug, so the ring of sites around a base does
     * not fill up with ground reserved for tunnels nobody will dig. Null when it keeps nothing.
     */
    static Rect kept(WorkplaceState w) {
        if (!w.workedOut) {
            return corridor(w);
        }
        int dug = -1;
        for (WorkplaceState.MineLevel lv : w.levels) {
            if (lv.progress > 0 && lv.step <= w.bottom) {
                dug = Math.max(dug, lv.rounds);
            }
        }
        int r = MineSiteRules.keptRounds(true, w.bottom, w.maxRounds, dug);
        int ox = w.origin.getX(), oz = w.origin.getZ(), fx = w.facing.getStepX(), fz = w.facing.getStepZ();
        return switch (r) {
            case MineSiteRules.KEEPS_NOTHING -> null;
            case MineSiteRules.KEEPS_STAIRS -> MineSiteRules.stairsArea(ox, oz, fx, fz);
            default -> MineSiteRules.corridor(ox, oz, fx, fz, r);
        };
    }

    /**
     * True if the stretch that lengthening round {@code rounds} adds to the mine's tunnels keeps clear of everything
     * a corridor must avoid: the edge of the map, every settlement's walls (its own too), other bases' land and
     * lairs, and other mines' corridors. A mine's corridor is checked when its site is chosen; this catches what
     * came later (a new base, a neighbour's mine).
     */
    public static boolean corridorClear(ServerLevel level, CampaignData data, WorkplaceState wp, int rounds) {
        Rect seg = MineSiteRules.segment(wp.origin.getX(), wp.origin.getZ(), wp.facing.getStepX(), wp.facing.getStepZ(), rounds);
        if (!MapBounds.inside(data, seg.minX(), seg.minZ(), MAP_MARGIN) || !MapBounds.inside(data, seg.maxX(), seg.maxZ(), MAP_MARGIN)) {
            return false;
        }
        for (Rect o : obstacles(data, data.base(wp.baseId), wp)) {
            if (o.overlaps(seg)) {
                return false;
            }
        }
        return true;
    }

    /**
     * What a mine's corridor must keep clear of, already widened by the gap to keep: every settlement's walls (+4,
     * the own base's included, so tunnels never run under the walls), other bases' land (+8), lairs (+16) and the
     * ground every other mine keeps (+2, see {@link #kept}): the whole corridor of an open or resting mine, what a
     * worked-out or given-up one really dug.
     */
    static List<Rect> obstacles(CampaignData data, BaseState own, WorkplaceState self) {
        List<Rect> out = new ArrayList<>();
        for (BaseState b : data.bases.values()) {
            if (b.destroyed || b.center == null) {
                continue;
            }
            int h = BaseZones.half(b);
            int cx = b.center.getX(), cz = b.center.getZ();
            Rect walls = new Rect(cx - h, cz - h, cx + h, cz + h);
            out.add(walls.inflate(b.kind == BaseState.Kind.LAIR ? LAIR_GAP : WALLS_GAP));
            BlockPos min = b.point(BaseState.MIN), max = b.point(BaseState.MAX);
            if ((own == null || b.id != own.id) && min != null && max != null) {
                out.add(new Rect(Math.min(min.getX(), max.getX()), Math.min(min.getZ(), max.getZ()),
                        Math.max(min.getX(), max.getX()), Math.max(min.getZ(), max.getZ())).inflate(BASE_GAP));
            }
        }
        for (WorkplaceState w : data.workplaces.values()) {
            if (w != self && w.type == WorkplaceState.Type.MINE && w.origin != null) {
                Rect k = kept(w);
                if (k != null) {
                    out.add(k.inflate(CORRIDOR_GAP));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ choosing a site

    /** A place for a new mine: the mouth (feet height), the way its tunnels run, its reserved rounds and its score. */
    public record Site(BlockPos origin, Direction facing, int maxRounds, double score) {
    }

    /**
     * Why a point was turned down; only "too shallow" matters to the miner (a world with no room for a mine). WORSE:
     * deep enough, but even at its best it could not beat the site already found (its walk from the gate is longer
     * than that site's whole score), so it was not looked at further.
     */
    enum Verdict { OK, NOT_LOADED, SHALLOW, UNFIT, WORSE }

    /**
     * A search for a mine site around a base, spread over several ticks: points every {@link #GRID} blocks on the
     * rings from the walls plus {@link #RING_MARGIN} out to the mine site radius, read only where the chunk is
     * loaded and inside the map. The best site is the one with the lowest {@link MineSiteRules#score}.
     */
    public static final class Search {
        private final List<int[]> points;
        private final List<Long> bad;
        private int next, evaluated, shallow;
        private Site best;

        public Search(BaseState base, Collection<Long> badSites) {
            int half = BaseZones.half(base);
            int radius = BaseZones.workRadius(base, FCConfig.MINE_SITE_RADIUS.get());
            points = MineSiteRules.candidates(base.center.getX(), base.center.getZ(), half + RING_MARGIN, radius, GRID);
            bad = new ArrayList<>(badSites);
        }

        /** Looks at up to {@code budget} more points; true once every point has been looked at. */
        public boolean step(ServerLevel level, CampaignData data, BaseState base, int budget) {
            if (done()) {
                return true;
            }
            List<Rect> obstacles = obstacles(data, base, null);
            Rect map = mapRect(data);
            Site[] out = new Site[1];
            for (int i = 0; i < budget && next < points.size(); i++, next++) {
                int[] p = points.get(next);
                out[0] = null;
                Verdict v = judge(level, data, base, p[0], p[1], bad, obstacles, map, best == null ? Double.MAX_VALUE : best.score(), out);
                if (v == Verdict.NOT_LOADED) {
                    continue;
                }
                evaluated++;
                if (v == Verdict.SHALLOW) {
                    shallow++;
                } else if (v == Verdict.OK && (best == null || out[0].score() < best.score())) {
                    best = out[0];
                }
            }
            return done();
        }

        public boolean done() {
            return next >= points.size();
        }

        /** The best site found so far, or null. */
        public Site best() {
            return best;
        }

        /** Points that could be read (loaded and on the map). */
        public int evaluated() {
            return evaluated;
        }

        /** True when enough points were read and every one failed only because the ground lies too low for a mine. */
        public boolean allTooShallow() {
            return evaluated >= SHALLOW_PROOF && shallow == evaluated;
        }
    }

    /** The site a mine with its mouth at column (x, z) would have, or null when it is unfit (or not loaded). */
    public static Site evaluate(ServerLevel level, CampaignData data, BaseState base, int x, int z, Collection<Long> badSites) {
        Site[] out = new Site[1];
        return judge(level, data, base, x, z, badSites, obstacles(data, base, null), mapRect(data), Double.MAX_VALUE, out) == Verdict.OK ? out[0] : null;
    }

    /**
     * The rules for a mine's mouth at column (x, z), on the ground there (feet height {@code y}):
     * <ul>
     * <li>deep enough for at least one layer above the bottom of the world ({@link MinePlanner#deepEnough});</li>
     * <li>beyond every base's walls +8, out of other bases' land +16, 48 from villages the faction knows of, not on a
     * place kept for other work ({@link WorkSafety#reserved}), 24 from every other mine's mouth (worked-out ones
     * too; 8 from a mine given up before its shaft was begun) and 8 from the miner's bad sites;</li>
     * <li>a mouth the miner can stand in, and for some tunnel direction pointing away from the base: flat, dry,
     * solid ground around the shaft (no tree over it, no farmland, path or ice, nothing built), the shaft's cells
     * open, no surface water or lava and nothing built within 3, a corridor that fits at least one branch mine, and
     * not too much water over the tunnels or ground dipping toward them.</li>
     * </ul>
     * Of the directions, the one with the best {@link MineSiteRules#score} wins (water and dipping ground over the
     * tunnels, uneven or loose ground at the mouth, and too little room to lengthen all count against it), then the
     * one with more room to lengthen. Only loaded chunks are read. A point whose walk from the gate alone scores {@code bound} or more (the
     * score of a site already found) cannot be better and is not looked at further: a site's score is never less
     * than that walk.
     */
    static Verdict judge(ServerLevel level, CampaignData data, BaseState base, int x, int z, Collection<Long> bad,
                         List<Rect> obstacles, Rect map, double bound, Site[] out) {
        if (!loaded(level, x, z) || !MapBounds.inside(data, x, z, SITE_MAP_MARGIN)) {
            return Verdict.NOT_LOADED;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (!MinePlanner.deepEnough(y, level.getMinBuildHeight())) {
            return Verdict.SHALLOW;
        }
        double walk = gateDistance(base, x, z);
        if (walk >= bound) {
            return Verdict.WORSE;
        }
        BlockPos o = new BlockPos(x, y, z);
        if (!BaseZones.outsideAllWalls(data, o, SITE_WALLS_MARGIN) || BaseZones.inForeignArea(data, base.id, o, SITE_FOREIGN_MARGIN)
                || BaseZones.nearKnownVillage(data.factions.get(base.faction), o, VILLAGE_RADIUS)
                || BaseZones.nearKnownDanger(data.factions.get(base.faction), o, BaseZones.DANGER_RADIUS)
                || WorkSafety.reserved(data, base, o, WorkSafety.Use.MINE_SITE) || nearMine(data, x, z) || nearBad(bad, x, z)) {
            return Verdict.UNFIT;
        }
        if (!CaveWork.standable(level, o)) {
            return Verdict.UNFIT;
        }
        int bestRounds = -1;
        double bestScore = Double.MAX_VALUE;
        Direction bestDir = null;
        Map<Long, Boolean> built = new HashMap<>();
        for (int[] f : MineSiteRules.facingsAwayFrom(x - base.center.getX(), z - base.center.getZ())) {
            Direction dir = Direction.fromDelta(f[0], 0, f[1]);
            int r = MineSiteRules.maxRoundsThatFit(x, z, f[0], f[1], MinePlanner.MAX_REACH, obstacles, map);
            if (dir == null || r < 0) {
                continue;
            }
            int[] collar = collar(level, data, o, dir);
            if (collar == null || !surroundingsClear(level, o, dir, built)) {
                continue;
            }
            int penalty = corridorPenalty(level, o, dir);
            if (penalty >= MAX_PENALTY) {
                continue;
            }
            double score = MineSiteRules.score(walk, penalty, collar[0], collar[1] != 0, r);
            if (score < bestScore || (score == bestScore && r > bestRounds)) {
                bestScore = score;
                bestRounds = r;
                bestDir = dir;
            }
        }
        if (bestDir == null) {
            return Verdict.UNFIT;
        }
        out[0] = new Site(o, bestDir, bestRounds, bestScore);
        return Verdict.OK;
    }

    private static boolean loaded(ServerLevel level, int x, int z) {
        return level.hasChunk(x >> 4, z >> 4);
    }

    /** The campaign map less the corridor's margin, or null when the map has no edge. */
    static Rect mapRect(CampaignData data) {
        if (data.radius <= 0 || data.center == null) {
            return null;
        }
        int r = data.radius - MAP_MARGIN, cx = data.center.getX(), cz = data.center.getZ();
        return new Rect(cx - r, cz - r, cx + r, cz + r);
    }

    /**
     * True within {@link #MINE_SPACING} of another mine's mouth; a mine set aside before its shaft was begun keeps
     * only {@link #BAD_SITE_RADIUS} (nothing is dug there, but its mouth could not be reached or its ground was unsafe,
     * so no miner of the base tries the same spot again).
     */
    private static boolean nearMine(CampaignData data, int x, int z) {
        for (WorkplaceState w : data.workplaces.values()) {
            if (w.type != WorkplaceState.Type.MINE || w.origin == null) {
                continue;
            }
            int d = Math.max(Math.abs(w.origin.getX() - x), Math.abs(w.origin.getZ() - z));
            if (w.workedOut && w.bottom <= 0 ? d <= BAD_SITE_RADIUS : d < MINE_SPACING) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearBad(Collection<Long> bad, int x, int z) {
        for (long k : bad) {
            BlockPos p = BlockPos.of(k);
            if (Math.max(Math.abs(p.getX() - x), Math.abs(p.getZ() - z)) <= BAD_SITE_RADIUS) {
                return true;
            }
        }
        return false;
    }

    /** A cell around a mouth at {@code o}: f forward along the tunnels, s to the side (clockwise), dy up. */
    private static BlockPos at(BlockPos o, Direction dir, int f, int s, int dy) {
        return o.relative(dir, f).relative(dir.getClockWise(), s).above(dy);
    }

    /**
     * The ground around the shaft for tunnels running {@code dir} (forward 0..3, side -1..2, the cells
     * {@link MineLayout#nearStairs} keeps): every column within one block of the mouth's height, solid, dry
     * ground with no tree over it, not farmland, a path, ice, a barrier, a huge mushroom's cap or stem, a block entity
     * or part of a base, with the shaft's four columns open at the mouth's height and above. Returns {unevenness, 1
     * if some of the ground is sand or gravel}, or null when unfit.
     */
    private static int[] collar(ServerLevel level, CampaignData data, BlockPos o, Direction dir) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        boolean loose = false;
        for (int f = 0; f <= 3; f++) {
            for (int s = -1; s <= 2; s++) {
                BlockPos c = at(o, dir, f, s, 0);
                if (!loaded(level, c.getX(), c.getZ())) {
                    return null;
                }
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
                if (Math.abs(h - o.getY()) > 1 || level.getHeight(Heightmap.Types.MOTION_BLOCKING, c.getX(), c.getZ()) != h) {
                    return null;
                }
                BlockPos g = new BlockPos(c.getX(), h - 1, c.getZ());
                BlockState gs = level.getBlockState(g);
                if (!Terrain.isSolidGround(gs) || !gs.getFluidState().isEmpty() || gs.is(BlockTags.LEAVES) || gs.is(BlockTags.LOGS)
                        || gs.is(BlockTags.SAPLINGS) || gs.is(Blocks.FARMLAND) || gs.is(Blocks.DIRT_PATH) || gs.is(BlockTags.ICE)
                        || gs.is(Blocks.BARRIER) || gs.is(Blocks.BROWN_MUSHROOM_BLOCK) || gs.is(Blocks.RED_MUSHROOM_BLOCK)
                        || gs.is(Blocks.MUSHROOM_STEM) || gs.hasBlockEntity() || WorkSafety.planned(data, g)) {
                    return null;
                }
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
                loose |= gs.getBlock() instanceof FallingBlock;
            }
        }
        for (int f = 1; f <= 2; f++) {
            for (int s = 0; s <= 1; s++) {
                if (!CaveWork.open(level, at(o, dir, f, s, 0)) || !CaveWork.open(level, at(o, dir, f, s, 1))) {
                    return null;
                }
            }
        }
        return new int[]{hi - lo, loose ? 1 : 0};
    }

    /**
     * No surface water or lava (every second column) and nothing built (every column, from just under the ground
     * to two above it) within 3 blocks of the shaft: forward -3..6, side -4..5. Built blocks are the woodland's
     * list ({@link WorkSafety#builtBlock}) and block entities, so unreported villages, ruins and players' builds
     * keep their ground. Unloaded columns count as not clear. {@code built} caches columns across directions.
     */
    private static boolean surroundingsClear(ServerLevel level, BlockPos o, Direction dir, Map<Long, Boolean> built) {
        for (int f = -3; f <= 6; f++) {
            for (int s = -4; s <= 5; s++) {
                BlockPos c = at(o, dir, f, s, 0);
                int x = c.getX(), z = c.getZ();
                if (!loaded(level, x, z)) {
                    return false;
                }
                if (((f + s) & 1) == 0 && level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
                        > level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z)) {
                    return false; // water or lava on the surface
                }
                Boolean b = built.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> builtColumn(level, x, z));
                if (b) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean builtColumn(ServerLevel level, int x, int z) {
        int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = g - 1; y <= g + 2; y++) {
            BlockState s = level.getBlockState(m.set(x, y, z));
            if (s.hasBlockEntity() || WorkSafety.builtBlock(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * How poor the ground over the first stretch of tunnels is, sampled every 6 blocks (forward 3..42, side
     * -10..11): {@link #WATER_PENALTY} per sample with water or lava on top, {@link #DIP_PENALTY} per sample whose
     * ground lies more than 3 below the mouth (a hillside the tunnels would break out of). Unloaded samples count 0.
     */
    static int corridorPenalty(ServerLevel level, BlockPos o, Direction dir) {
        int penalty = 0;
        for (int f = 3; f <= 42; f += PENALTY_STEP) {
            for (int s = -10; s <= 11; s += PENALTY_STEP) {
                BlockPos c = at(o, dir, f, s, 0);
                if (!loaded(level, c.getX(), c.getZ())) {
                    continue;
                }
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, c.getX(), c.getZ())
                        > level.getHeight(Heightmap.Types.OCEAN_FLOOR, c.getX(), c.getZ())) {
                    penalty += WATER_PENALTY;
                }
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ()) < o.getY() - 3) {
                    penalty += DIP_PENALTY;
                }
            }
        }
        return penalty;
    }

    /** Horizontal distance from the nearer of the base's gates (its centre when it has none). */
    static double gateDistance(BaseState base, int x, int z) {
        double best = Double.MAX_VALUE;
        for (String g : new String[]{BaseState.GATE_WEST, BaseState.GATE_EAST}) {
            BlockPos p = base.point(g);
            if (p != null) {
                best = Math.min(best, Math.hypot(x - p.getX(), z - p.getZ()));
            }
        }
        return best == Double.MAX_VALUE ? Math.hypot(x - base.center.getX(), z - base.center.getZ()) : best;
    }

    /**
     * Opens a new mine at {@code site} for {@code miner}: no hut, no levels yet, nothing dug; its corridor reserves
     * the site's lengthening rounds. The faction hears where it is.
     */
    public static WorkplaceState create(ServerLevel level, CampaignData data, BaseState base, FactionNpc miner, Site site) {
        WorkplaceState w = new WorkplaceState();
        w.type = WorkplaceState.Type.MINE;
        w.origin = site.origin().immutable();
        w.extent = w.origin;
        w.facing = site.facing();
        w.hut = false;
        w.maxRounds = site.maxRounds();
        w.bottom = 0;
        w.cur = -1;
        w.upgradeStep = -1;
        w.opened = level.getGameTime();
        w.assigned = miner.getUUID();
        data.addWorkplace(base, w);
        Messages.toFaction(level.getServer(), base.faction, Component.literal(miner.npcName() + " opens a mine at "
                + w.origin.toShortString() + " (tunnels run " + w.facing.getSerializedName() + ")"));
        return w;
    }
}
