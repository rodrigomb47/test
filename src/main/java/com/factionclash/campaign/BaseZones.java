package com.factionclash.campaign;

import com.factionclash.logic.Zones;
import com.factionclash.structure.BaseLayout;
import com.factionclash.structure.LairLayout;
import net.minecraft.core.BlockPos;

/**
 * The zones around bases that miners, woodcutters and fishermen keep to. They are measured on the walls box (the
 * walls' half-size around the centre, at any height), never on a base's MIN/MAX box: that box is the forced, ticking
 * work belt, and the base's own workers are meant to work inside it. Other factions' land is the MIN/MAX box of
 * their bases ({@link BaseState#contains}).
 */
public final class BaseZones {
    private BaseZones() {
    }

    /** The walls' half-size: a GameTest base's own, else the settlement or lair layout's. */
    public static int half(BaseState b) {
        return Zones.half(b.testHalf, b.isSettlement() ? BaseLayout.HALF : LairLayout.HALF);
    }

    /** Horizontal Chebyshev distance from the base centre. */
    public static int chebyshev(BaseState b, BlockPos p) {
        return Zones.chebyshev(p.getX(), p.getZ(), b.center.getX(), b.center.getZ());
    }

    /** Within the walls box plus {@code margin}, at any height. */
    public static boolean insideWalls(BaseState b, BlockPos p, int margin) {
        return Zones.insideHalf(p.getX() - b.center.getX(), p.getZ() - b.center.getZ(), half(b), margin);
    }

    /** Outside the walls box plus {@code margin} of every base still standing, the worker's own included. */
    public static boolean outsideAllWalls(CampaignData d, BlockPos p, int margin) {
        for (BaseState b : d.bases.values()) {
            if (!b.destroyed && insideWalls(b, p, margin)) {
                return false;
            }
        }
        return true;
    }

    /**
     * In the land of another base still standing: within {@code margin} of its MIN/MAX box, or {@code margin + 8} of
     * a lair's. The base with id {@code ownBaseId} does not count.
     */
    public static boolean inForeignArea(CampaignData d, int ownBaseId, BlockPos p, int margin) {
        for (BaseState b : d.bases.values()) {
            if (b.destroyed || b.id == ownBaseId) {
                continue;
            }
            if (b.contains(p, b.isSettlement() ? margin : margin + 8)) {
                return true;
            }
        }
        return false;
    }

    /** Within {@code r} blocks (Chebyshev) of the centre of a lair still standing. */
    public static boolean nearLair(CampaignData d, BlockPos p, int r) {
        for (BaseState b : d.bases.values()) {
            if (!b.destroyed && b.kind == BaseState.Kind.LAIR && chebyshev(b, p) <= r) {
                return true;
            }
        }
        return false;
    }

    /** Within {@code r} blocks (Chebyshev) of a village the faction's scouts have reported. */
    public static boolean nearKnownVillage(FactionState f, BlockPos p, int r) {
        if (f == null) {
            return false;
        }
        for (Report rep : f.knowledge) {
            if (rep.kind == Report.Kind.VILLAGE && rep.pos != null
                    && Zones.chebyshev(p.getX(), p.getZ(), rep.pos.getX(), rep.pos.getZ()) <= r) {
                return true;
            }
        }
        return false;
    }

    /**
     * PROPOSED: how far (Chebyshev, from the reported position) the faction's people keep from a pillager outpost or
     * woodland mansion its scouts have reported: no exploration target, mine site, tree, fishing spot or travel waypoint
     * lies closer.
     */
    public static final int DANGER_RADIUS = 48;

    /** Within {@code r} blocks (Chebyshev) of a pillager outpost or woodland mansion the faction's scouts have reported. */
    public static boolean nearKnownDanger(FactionState f, BlockPos p, int r) {
        if (f == null) {
            return false;
        }
        for (Report rep : f.knowledge) {
            if (rep.kind.dangerousGround() && rep.pos != null
                    && Zones.chebyshev(p.getX(), p.getZ(), rep.pos.getX(), rep.pos.getZ()) <= r) {
                return true;
            }
        }
        return false;
    }

    /** How far from the centre the base's workers look for work: a GameTest base's own radius, else the configured one. */
    public static int workRadius(BaseState b, int configured) {
        return Zones.workRadius(b.testRadius, configured);
    }

    /** Whether workers may forage beyond the work radius (never for GameTest bases, which must stay on their platform). */
    public static boolean forageAllowed(BaseState b) {
        return b.testRadius <= 0;
    }
}
