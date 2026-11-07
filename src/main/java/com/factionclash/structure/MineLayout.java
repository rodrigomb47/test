package com.factionclash.structure;

import com.factionclash.campaign.WorkplaceState;
import com.factionclash.faction.FactionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Geometry of a constructed mine (spec 6). A surface hut holds the stairwell; each tier adds a staircase going
 * further down and forward plus a lit 5x5 chamber. The miner works at the end of the deepest chamber.
 *
 * <p>Local coordinates: f = forward (away from the base), s = sideways, y = relative to the hut floor level.
 */
public final class MineLayout {
    public static final int HUT_BACK = -2;
    public static final int HUT_FRONT = 5;

    private MineLayout() {
    }

    public record Segment(int startF, int startY, int steps, int chamberStart, int chamberEnd, int chamberY) {
    }

    public static Segment segment(int tier) {
        int startF = 0, startY = 0;
        Segment seg = null;
        for (int t = 1; t <= tier; t++) {
            int n = t == 1 ? 4 : 6;
            int cs = startF + n + 1;
            int ce = startF + n + 5;
            int cy = startY - n;
            seg = new Segment(startF, startY, n, cs, ce, cy);
            startF = ce;
            startY = cy;
        }
        return seg;
    }

    public static BlockPos local(WorkplaceState wp, int f, int s, int y) {
        Direction fw = wp.facing;
        Direction side = fw.getClockWise();
        return wp.origin.relative(fw, f).relative(side, s).above(y);
    }

    /**
     * The miner works the chamber's side wall, so production never blocks (or is blocked by) the next tier's
     * staircase, which continues straight ahead from the chamber end.
     */
    public static BlockPos workPos(WorkplaceState wp) {
        Segment g = segment(wp.tier);
        return local(wp, g.chamberEnd(), -2, g.chamberY());
    }

    public static BlockPos facePos(WorkplaceState wp) {
        Segment g = segment(wp.tier);
        return local(wp, g.chamberEnd(), -3, g.chamberY() + 1);
    }

    /** Centre of the chamber of the given tier: the waypoint miners walk through on the way in and out. */
    public static BlockPos chamberCenter(WorkplaceState wp, int tier) {
        Segment g = segment(tier);
        return local(wp, (g.chamberStart() + g.chamberEnd()) / 2, 0, g.chamberY());
    }

    /** Local forward coordinate of a world position relative to the mine origin. */
    public static int forwardOf(WorkplaceState wp, BlockPos p) {
        BlockPos d = p.subtract(wp.origin);
        return d.getX() * wp.facing.getStepX() + d.getZ() * wp.facing.getStepZ();
    }

    /** Which segment (tier) of the mine a position lies in, or 0 when it is outside / in the surface hut. */
    public static int segmentAt(WorkplaceState wp, BlockPos p, int tier) {
        if (p.getY() >= wp.origin.getY() - 1) {
            return 0;
        }
        int f = forwardOf(wp, p);
        for (int t = 1; t <= tier; t++) {
            Segment g = segment(t);
            if (f <= g.chamberEnd()) {
                return t;
            }
        }
        return tier;
    }

    public static BlockPos entrance(WorkplaceState wp) {
        return local(wp, HUT_BACK - 1, 0, 0);
    }

    /** One excavation step: dig a cell (null place) or place a block. */
    public record Action(BlockPos pos, BlockState place) {
        public boolean isDig() {
            return place == null;
        }
    }

    /** Ordered steps that turn a tier-1 mine into one of {@code tier}: dig corridor/chamber, stairs, torches, face. */
    public static List<Action> segmentActions(WorkplaceState wp, int tier) {
        Segment g = segment(tier);
        Direction back = wp.facing.getOpposite();
        Direction side = wp.facing.getClockWise();
        List<Action> out = new ArrayList<>();
        BlockState stair = Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, back);
        for (int k = 1; k <= g.steps(); k++) {
            int f = g.startF() + k;
            int y = g.startY() - k;
            // dig each column top-down so sand or gravel above is sealed before the cell below opens
            for (int dy = 2; dy >= 0; dy--) {
                out.add(new Action(local(wp, f, 0, y + dy), null));
            }
            out.add(new Action(local(wp, f, 0, y - 1), stair));
            if (k % 3 == 0) {
                out.add(new Action(local(wp, f, 0, y + 1),
                        Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, side)));
            }
        }
        for (int f = g.chamberStart(); f <= g.chamberEnd(); f++) {
            for (int s = -2; s <= 2; s++) {
                for (int dy = 2; dy >= 0; dy--) {
                    out.add(new Action(local(wp, f, s, g.chamberY() + dy), null));
                }
            }
        }
        int tf = g.chamberStart() + 2;
        out.add(new Action(local(wp, tf, -2, g.chamberY() + 1),
                Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, side)));
        out.add(new Action(local(wp, tf, 2, g.chamberY() + 1),
                Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, side.getOpposite())));
        return out;
    }

    /** Every cell that must be air in a mine of {@code tier} (used for sealing checks). */
    public static Set<BlockPos> openCells(WorkplaceState wp, int tier) {
        Set<BlockPos> open = new LinkedHashSet<>();
        for (int t = 1; t <= tier; t++) {
            for (Action a : segmentActions(wp, t)) {
                if (a.isDig() || a.place().is(Blocks.WALL_TORCH)) {
                    open.add(a.pos());
                }
            }
        }
        // hut interior and the stairwell mouth
        for (int f = HUT_BACK + 1; f < HUT_FRONT; f++) {
            for (int s = -1; s <= 1; s++) {
                for (int y = 0; y <= 2; y++) {
                    open.add(local(wp, f, s, y));
                }
            }
        }
        return open;
    }

    /** Builds the surface hut and the complete tier-1 excavation into a blueprint (used at campaign start). */
    public static void buildInitial(Blueprint bp, WorkplaceState wp, FactionType faction) {
        FactionType.Style st = faction.style;
        BlockState wall = Blocks.COBBLESTONE.defaultBlockState();
        BlockState frame = st.frame().defaultBlockState();
        BlockState roof = st.floor().defaultBlockState();
        for (int f = HUT_BACK; f <= HUT_FRONT; f++) {
            for (int s = -2; s <= 2; s++) {
                bp.setAbs(local(wp, f, s, -1), Blocks.COBBLESTONE.defaultBlockState());
                boolean edge = f == HUT_BACK || f == HUT_FRONT || s == -2 || s == 2;
                boolean corner = (f == HUT_BACK || f == HUT_FRONT) && (s == -2 || s == 2);
                for (int y = 0; y <= 2; y++) {
                    bp.setAbs(local(wp, f, s, y), edge ? (corner ? frame : wall) : Blocks.AIR.defaultBlockState());
                }
                bp.setAbs(local(wp, f, s, 3), roof);
            }
        }
        // door opening facing the base
        bp.setAbs(local(wp, HUT_BACK, 0, 0), Blocks.AIR.defaultBlockState());
        bp.setAbs(local(wp, HUT_BACK, 0, 1), Blocks.AIR.defaultBlockState());
        Direction side = wp.facing.getClockWise();
        bp.setAbs(local(wp, 0, -1, 1), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, side));
        bp.setAbs(local(wp, 0, 1, 1), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, side.getOpposite()));
        bp.markers.put("mine_sign_" + wp.id, local(wp, HUT_BACK - 1, 1, 1));
        for (Action a : segmentActions(wp, 1)) {
            bp.setAbs(a.pos(), a.isDig() ? Blocks.AIR.defaultBlockState() : a.place());
        }
    }

    /** Pickaxe tier the upgrade to {@code nextTier} consumes (PROPOSED: stone, iron, diamond). */
    public static int upgradePickTier(int nextTier) {
        return switch (nextTier) {
            case 2 -> 1;
            case 3 -> 2;
            default -> 3;
        };
    }

    public static final int UPGRADE_TORCHES = 6;
    public static final int UPGRADE_STAIRS = 6;
    public static final int UPGRADE_COBBLE = 24;
}
