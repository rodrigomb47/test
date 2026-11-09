package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.MapBounds;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.logic.MinePlanner;
import com.factionclash.logic.MineSiteRules;
import com.factionclash.logic.MineSiteRules.Rect;
import com.factionclash.structure.MineLayout;
import com.factionclash.util.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * The mine sites of the campaign, as the world sees them: which mines a base has open, which mine an NPC is
 * standing in, whether a mine's tunnels may be lengthened, and the only two ways a mine is set aside (worked out, or
 * resting until a better pickaxe). Records of set-aside mines are kept: their stairs stay protected and their
 * corridors stay reserved.
 */
public final class MineSites {
    /** PROPOSED: how far a mine's corridor keeps from other things (blocks, Chebyshev, on the ground). */
    static final int CORRIDOR_GAP = 2, WALLS_GAP = 4, BASE_GAP = 8, LAIR_GAP = 16, MAP_MARGIN = 8;

    private MineSites() {
    }

    /**
     * The mine is worked out for good: nobody works it any more and it is not taken up again. The only place that
     * marks a mine worked out.
     */
    public static void retire(ServerLevel level, CampaignData data, WorkplaceState wp, String why) {
        if (wp.workedOut) {
            return;
        }
        wp.workedOut = true;
        wp.upgradeStep = -1;
        wp.note = why == null ? "" : why;
        Workplaces.release(data, wp);
        data.setDirty();
        BaseState b = data.base(wp.baseId);
        if (b != null && level != null) {
            Messages.toFaction(level.getServer(), b.faction, Component.literal("Mine #" + wp.id + " is worked out (" + wp.note + ")"));
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
     * One line per mine for the station screen: an open mine with its levels (height, ore, progress, lengthening
     * rounds), how deep its shaft goes and its output; worked-out and resting mines together on one line.
     */
    public static List<String> describe(CampaignData data, BaseState b, int minY, java.util.function.Function<WorkplaceState, String> who) {
        List<String> out = new ArrayList<>();
        List<String> done = new ArrayList<>();
        long doneOutput = 0;
        java.util.Map<Integer, List<String>> resting = new java.util.TreeMap<>();
        for (int id : b.workplaces) {
            WorkplaceState w = data.workplace(id);
            if (w == null || w.type != WorkplaceState.Type.MINE) {
                continue;
            }
            if (w.workedOut) {
                done.add("#" + w.id);
                doneOutput += w.totalOutput;
                continue;
            }
            if (w.restTier > 0) {
                resting.computeIfAbsent(w.restTier, k -> new ArrayList<>()).add("#" + w.id);
                continue;
            }
            int y0 = w.origin.getY();
            StringBuilder sb = new StringBuilder("Mine #" + w.id + " at " + w.origin.toShortString() + " facing "
                    + w.facing.getSerializedName() + " (" + who.apply(w) + "): ");
            List<WorkplaceState.MineLevel> order = new ArrayList<>(w.levels);
            WorkplaceState.MineLevel cur = MineLayout.current(w);
            if (cur != null) {
                order.remove(cur);
                order.add(0, cur); // the level being worked first
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
            tail.add("Worked-out mines: " + String.join(", ", done) + " (output " + doneOutput + ")");
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
     * that is in the shaft, or in the corridor out of the sky's sight. Ground that dips below the mine's mouth
     * somewhere above its tunnels is open to the sky, so a miner walking there is not taken for one in the tunnels.
     */
    public static boolean inside(ServerLevel level, WorkplaceState wp, BlockPos p) {
        if (MineLayout.inHut(wp, p)) {
            return true;
        }
        return MineLayout.inMine(wp, p) && (MineLayout.nearStairs(wp, p) || !level.canSeeSky(p));
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
     * corridors of every other mine (+2), worked-out ones included.
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
                out.add(corridor(w).inflate(CORRIDOR_GAP));
            }
        }
        return out;
    }
}
