package com.factionclash.campaign;

import com.factionclash.logic.Zones;
import net.minecraft.core.BlockPos;

/**
 * The campaign map: a square of {@link CampaignData#radius} blocks around {@link CampaignData#center} (radius 0 means
 * no edge). Far targets such as survey points, forage points and lakes are clamped onto it, because walking to a
 * point generates the chunks along the way.
 */
public final class MapBounds {
    private MapBounds() {
    }

    /** True when (x, z) lies at least {@code margin} blocks inside the map edge. */
    public static boolean inside(CampaignData d, int x, int z, int margin) {
        return Zones.mapInside(d.center.getX(), d.center.getZ(), d.radius, x, z, margin);
    }

    public static boolean inside(CampaignData d, BlockPos p, int margin) {
        return inside(d, p.getX(), p.getZ(), margin);
    }

    /** {@code p} moved onto the map, {@code margin} blocks inside its edge (its height is kept). */
    public static BlockPos clamp(CampaignData d, BlockPos p, int margin) {
        int[] c = Zones.mapClamp(d.center.getX(), d.center.getZ(), d.radius, p.getX(), p.getZ(), margin);
        return c[0] == p.getX() && c[1] == p.getZ() ? p : new BlockPos(c[0], p.getY(), c[1]);
    }
}
