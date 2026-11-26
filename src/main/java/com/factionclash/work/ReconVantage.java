package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.CampaignData;
import com.factionclash.logic.campaign.ReconRules;
import com.factionclash.logic.campaign.ReconRules.Side;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a recon scout watches an enemy base from ({@code docs/design/recon.md} 3.5, task R4a; the scout's trip that
 * uses it is R4b): the sides in the order it tries them, and on a side the columns of the vantage band it can stand on,
 * best first.
 *
 * <p>A column is a vantage when it lies in the band ({@link ReconRules#columns}: 61 to 72 blocks from the centre, up to
 * 8 to either side of the side's axis; a lair's 46 to 64 on the axis, and a GameTest base's scaled band, {@link #band})
 * and it is ground the scout can walk to: its top is solid (not water, leaves, a log, a block entity or a built block:
 * not a tree and not a roof), and it is reached from the land round the band by steps of at most one block up and drops
 * of at most three, with room to walk under leaves (so the top of an old pillar, a tree or a cliff it cannot climb is not
 * ground). Over it the sky is open for the whole pillar ({@link #SKY}: a lair's is watched from the ground and needs room
 * to stand only), it lies in a loaded chunk (nothing is loaded to look), and it is in no base's alarm box (the walls plus
 * {@link #ALARM_MARGIN}, the box in which a base's people hand intruders to the guards) and no block of the column is
 * part of a base's plan. Every base counts, the scout's own and the target included.</p>
 *
 * <p>The columns come ranked by {@link ReconRules#GROUND_ORDER}: the side's axis first, then each line farther off it,
 * and along a line the highest standing ground first, then the nearest the standoff. The walk is the scout's: when it
 * cannot get to the first, it takes the next.</p>
 */
public final class ReconVantage {
    private ReconVantage() {
    }

    /** The land read round a side's band, so the walk onto the band's ground is judged from outside it. */
    static final int MARGIN = 4;
    /**
     * Open sky over a settlement's column: the tallest pillar ({@link ReconRules#PILLAR_MAX}) and the two cells it needs
     * over its feet on the way up (its head and the jump), the 22 blocks of recon.md 3.5.
     */
    public static final int SKY = ReconRules.PILLAR_MAX + 2;
    /** Room to stand at a lair's vantage, watched from the ground: its feet and head. */
    static final int HEADROOM = 2;
    /** The alarm box: the walls' half plus this ({@code FactionDirector.defenceAlarm}, recon.md 1). */
    public static final int ALARM_MARGIN = 4;
    /** A walker steps up one block at most and drops three at most (a longer fall hurts). */
    static final int STEP_UP = 1, DROP = 3;

    /**
     * A vantage: the side, the band's column ({@code dx}, {@code dz} from the target's centre), the cell the climber's feet
     * stand in at its foot (where the pillar's first block goes), and how far that lies above the target's floor.
     */
    public record Vantage(Side side, ReconRules.Column column, BlockPos foot, int rise) {
    }

    /** The band a scout watches {@code target} from: the real one, or a GameTest base's scaled one. */
    public static ReconRules.Band band(BaseState target) {
        ReconRules.Band real = ReconRules.Band.of(!target.isSettlement());
        return new ReconRules.Band(target.testStandoff > 0 ? target.testStandoff : real.standoff(),
                target.testSight > 0 ? target.testSight : real.sight(), real.lateral());
    }

    /** The sides with a gate: a settlement's west and east ({@code BaseLayout}), every side of a lair (its walls have gaps). */
    public static int gates(BaseState target) {
        return target.isSettlement() ? Side.WEST.bit() | Side.EAST.bit() : Side.ALL;
    }

    /**
     * The order in which a scout coming from {@code home} (its own base's centre) tries the target's sides
     * ({@link ReconRules#sideOrder}): the gate side facing home, the other gate side, the side facing home, the last.
     */
    public static List<Side> order(BaseState target, BlockPos home) {
        return ReconRules.sideOrder(home.getX() - target.center.getX(), home.getZ() - target.center.getZ(), gates(target));
    }

    /** The sides one trip tries ({@link ReconRules#tripSides}): those not in {@code tried} first, at most two. */
    public static List<Side> sides(BaseState target, BlockPos home, int tried) {
        return ReconRules.tripSides(order(target, home), tried);
    }

    /** The ground of one column of the land read: where a walker stands on it, or none. */
    private record Spot(int stand, boolean walk) {
    }

    /**
     * The columns of {@code side} a scout can stand on and watch {@code target} from, best first; empty when there are
     * none (the scout tries its next side). Reads only loaded chunks, once.
     */
    public static List<Vantage> columns(ServerLevel level, CampaignData data, BaseState target, Side side) {
        ReconRules.Band band = band(target);
        List<ReconRules.Column> cols = ReconRules.columns(side, band);
        if (cols.isEmpty()) {
            return List.of();
        }
        int cx = target.center.getX(), cz = target.center.getZ();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (ReconRules.Column c : cols) {
            minX = Math.min(minX, cx + c.dx());
            maxX = Math.max(maxX, cx + c.dx());
            minZ = Math.min(minZ, cz + c.dz());
            maxZ = Math.max(maxZ, cz + c.dz());
        }
        minX -= MARGIN;
        minZ -= MARGIN;
        maxX += MARGIN;
        maxZ += MARGIN;
        int w = maxX - minX + 1, d = maxZ - minZ + 1;
        int y0 = target.center.getY();
        Spot[] land = new Spot[w * d];
        for (int i = 0; i < w; i++) {
            for (int k = 0; k < d; k++) {
                land[i * d + k] = spot(level, minX + i, minZ + k, y0);
            }
        }
        boolean[] reached = walkable(land, w, d);
        boolean lair = !target.isSettlement();
        List<BaseState> bases = new ArrayList<>();
        for (BaseState b : data.bases.values()) {
            if (!b.destroyed) {
                bases.add(b);
            }
        }
        Map<ReconRules.Column, Vantage> found = new HashMap<>();
        List<ReconRules.Ground> ground = new ArrayList<>();
        for (ReconRules.Column c : cols) {
            int x = cx + c.dx(), z = cz + c.dz();
            int idx = (x - minX) * d + (z - minZ);
            Spot s = land[idx];
            if (s == null || !s.walk() || !reached[idx]) {
                continue;
            }
            BlockPos foot = new BlockPos(x, s.stand(), z);
            if (!standsOn(level, foot.below()) || !open(level, foot, lair ? HEADROOM : SKY) || inBox(bases, x, z)
                    || planned(bases, foot.below(), lair ? HEADROOM : SKY)) {
                continue;
            }
            Vantage v = new Vantage(side, c, foot, s.stand() - y0);
            found.put(c, v);
            ground.add(new ReconRules.Ground(c, v.rise()));
        }
        List<Vantage> out = new ArrayList<>();
        for (ReconRules.Ground g : ReconRules.rank(ground)) {
            out.add(found.get(g.column()));
        }
        return out;
    }

    /**
     * The ground of column ({@code x}, {@code z}) as a walker meets it: the highest block a walker stands on (leaves and
     * the plants that give way are seen through, so the ground under a tree is ground), where it stands, and whether it
     * can stand there at all (no fluid, something to stand on, room for its feet and head). Under a solid overhang the
     * column's ground is the overhang's top (the heightmap's): a lair's vantage under a rock ledge is not found, one under
     * leaves is. Null where the chunk is not loaded: nothing is loaded to look.
     */
    private static Spot spot(ServerLevel level, int x, int z, int y0) {
        BlockPos probe = new BlockPos(x, y0, z);
        if (!level.isLoaded(probe)) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, y, z);
        for (int i = 0; i < 3 && y > level.getMinBuildHeight(); i++) {
            BlockState s = level.getBlockState(m);
            if (!(s.canBeReplaced() && s.getFluidState().isEmpty())) {
                break; // a snow layer or a plant on the ground is stood in, not on
            }
            m.setY(--y);
        }
        BlockState g = level.getBlockState(m);
        boolean walk = g.getFluidState().isEmpty() && !g.getCollisionShape(level, m).isEmpty() && passable(level, m.above())
                && passable(level, m.above(2));
        return new Spot(y + 1, walk);
    }

    private static boolean passable(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
    }

    /**
     * Which columns of the land read a walker reaches from its edge (the land round the band, where the scout comes from):
     * by steps of at most {@link #STEP_UP} up and drops of at most {@link #DROP}, from one column it can stand on to the
     * next beside it.
     */
    private static boolean[] walkable(Spot[] land, int w, int d) {
        boolean[] seen = new boolean[land.length];
        ArrayDeque<Integer> open = new ArrayDeque<>();
        for (int i = 0; i < w; i++) {
            for (int k = 0; k < d; k++) {
                int idx = i * d + k;
                if ((i == 0 || k == 0 || i == w - 1 || k == d - 1) && land[idx] != null && land[idx].walk()) {
                    seen[idx] = true;
                    open.add(idx);
                }
            }
        }
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty()) {
            int idx = open.poll();
            int i = idx / d, k = idx % d;
            Spot from = land[idx];
            for (int[] st : steps) {
                int ni = i + st[0], nk = k + st[1];
                if (ni < 0 || nk < 0 || ni >= w || nk >= d) {
                    continue;
                }
                int n = ni * d + nk;
                Spot to = land[n];
                if (seen[n] || to == null || !to.walk()) {
                    continue;
                }
                int rise = to.stand() - from.stand();
                if (rise <= STEP_UP && -rise <= DROP) {
                    seen[n] = true;
                    open.add(n);
                }
            }
        }
        return seen;
    }

    /**
     * A solid top to build a pillar on: a full face up, no fluid, and not a tree (leaves, a log), a block entity or a
     * built block (planks, cobblestone, glass and the like: a roof).
     */
    private static boolean standsOn(ServerLevel level, BlockPos ground) {
        BlockState g = level.getBlockState(ground);
        return g.isFaceSturdy(level, ground, Direction.UP) && g.getFluidState().isEmpty() && !g.is(BlockTags.LEAVES)
                && !g.is(BlockTags.LOGS) && !g.hasBlockEntity() && !WorkSafety.builtBlock(g);
    }

    /** {@code cells} cells from {@code foot} up are open: air, or a plant that gives way; no fluid, no block entity. */
    private static boolean open(ServerLevel level, BlockPos foot, int cells) {
        BlockPos.MutableBlockPos m = foot.mutable();
        for (int i = 0; i < cells; i++) {
            BlockState s = level.getBlockState(m);
            if (!(s.isAir() || s.canBeReplaced()) || !s.getFluidState().isEmpty() || s.hasBlockEntity()) {
                return false;
            }
            m.move(Direction.UP);
        }
        return true;
    }

    /** In the alarm box of a base still standing (its walls' half plus {@link #ALARM_MARGIN}, any height). */
    private static boolean inBox(List<BaseState> bases, int x, int z) {
        for (BaseState b : bases) {
            if (Math.max(Math.abs(x - b.center.getX()), Math.abs(z - b.center.getZ())) <= BaseZones.half(b) + ALARM_MARGIN) {
                return true;
            }
        }
        return false;
    }

    /** The ground block, or one of the {@code cells} cells over it, is part of the plan of a base still standing. */
    private static boolean planned(List<BaseState> bases, BlockPos ground, int cells) {
        for (BaseState b : bases) {
            int[] box = b.planBox();
            if (box == null || ground.getX() < box[0] || ground.getZ() < box[1] || ground.getX() > box[2] || ground.getZ() > box[3]) {
                continue;
            }
            Map<Long, BlockState> plan = b.planIndex();
            for (int i = 0; i <= cells; i++) {
                if (plan.containsKey(BlockPos.asLong(ground.getX(), ground.getY() + i, ground.getZ()))) {
                    return true;
                }
            }
        }
        return false;
    }
}
