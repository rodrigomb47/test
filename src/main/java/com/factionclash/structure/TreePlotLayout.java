package com.factionclash.structure;

import com.factionclash.campaign.WorkplaceState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * The fenced tree plot of older saves, kept only to read them: tier t held a (t+1)x(t+1) grid of trees spaced 6
 * apart inside a fence whose entrance gap faced the base, and enlargements moved the fence away from the base. An
 * old plot's grid saplings count as planted when it loads, and its woodcutter takes the fence of every tier down.
 * New bases have no plot: their woodland has no fence and no grid (the tier-1 numbers still size the levelled grove
 * pad west of the walls).
 */
public final class TreePlotLayout {
    private TreePlotLayout() {
    }

    public static int grid(int tier) {
        return tier + 1;
    }

    /** Half-width of the inner area across (s) — 5, 8, 11 for tiers 1..3. */
    public static int half(int tier) {
        return 3 * grid(tier) - 1;
    }

    /** Inner depth along f (1..depth). */
    public static int depth(int tier) {
        return 2 * half(tier) + 1;
    }

    public static BlockPos local(WorkplaceState wp, int f, int s) {
        Direction side = wp.facing.getClockWise();
        return wp.origin.relative(wp.facing, f).relative(side, s);
    }

    public static List<BlockPos> spots(WorkplaceState wp) {
        return spots(wp, wp.tier);
    }

    public static List<BlockPos> spots(WorkplaceState wp, int tier) {
        List<BlockPos> out = new ArrayList<>();
        int g = grid(tier);
        for (int i = 0; i < g; i++) {
            for (int j = 0; j < g; j++) {
                out.add(local(wp, 3 + 6 * i, -3 * (g - 1) + 6 * j));
            }
        }
        return out;
    }

    /** Fence ring for a tier (excluding the entrance gap at f=0, s=0). */
    public static List<BlockPos> fence(WorkplaceState wp, int tier) {
        List<BlockPos> out = new ArrayList<>();
        int h = half(tier) + 1;
        int d = depth(tier) + 1;
        for (int f = 0; f <= d; f++) {
            for (int s = -h; s <= h; s++) {
                boolean edge = f == 0 || f == d || s == -h || s == h;
                if (edge && !(f == 0 && s == 0)) {
                    out.add(local(wp, f, s));
                }
            }
        }
        return out;
    }

}
