package com.factionclash.campaign;

import com.factionclash.faction.FactionType;
import com.factionclash.logic.campaign.ReconRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * A war council's order to watch one enemy base ({@code docs/design/recon.md} 2.1; task R2 keeps it and saves it, R3 places
 * and settles orders ({@link ReconOrders}), R4b's scouts take them). It names the target as the faction knows it, by its report (position,
 * subject, kind), never by the engine's base id; the home base whose station gave it; when; whether it is the urgent order
 * after a strike; once a scout took it at home, which scout and when; and what that scout told at the station. At most
 * {@link ReconRules#ORDERS_MAX} are open per faction ({@link FactionState#reconOrders}).
 */
public final class ReconOrder {
    /** The target's reported position (its report's {@link Report#pos}). */
    public BlockPos target;
    /** The faction whose base it is (a lair's: the Hollow). */
    public FactionType subject;
    /** {@link Report.Kind#ENEMY_BASE} or {@link Report.Kind#LAIR}. */
    public Report.Kind kind;
    /** The base whose station gave the order (-1: none). */
    public int homeBase = -1;
    public long orderedAt;
    /** The urgent order after a strike (recon.md 6), else the ordinary one before the interval ends. */
    public boolean urgent;
    /** The scout that took it at home (null: not taken yet). */
    public UUID scout;
    /** When its scout took it (0: not taken). */
    public long takenAt;
    /**
     * What its scout told at the station about the target since it took the order ({@link ReconOrders#atStation}): nothing
     * yet, a good recon, a blind one, or no recon of it. The next council pass ends the order by it ({@link ReconOrders#settle}).
     */
    public ReconRules.ScoutReport reported = ReconRules.ScoutReport.NONE;
    /** The sides its scout's trip tried, told with an empty-handed report (a mask of {@link ReconRules.Side} bits). */
    public int reportedSides;

    public ReconOrder() {
    }

    public ReconOrder(BlockPos target, FactionType subject, Report.Kind kind, int homeBase, long orderedAt, boolean urgent) {
        this.target = target;
        this.subject = subject;
        this.kind = kind;
        this.homeBase = homeBase;
        this.orderedAt = orderedAt;
        this.urgent = urgent;
    }

    /** Whether a scout has taken it. */
    public boolean taken() {
        return scout != null;
    }

    /** Whether {@code r} is the faction's report of this order's target (the same subject by {@link Report#sameSubject}'s rule). */
    public boolean about(Report r) {
        return r != null && r.kind == kind && r.subject == subject && r.pos != null && target != null && r.pos.distSqr(target) <= 40 * 40;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("target", target.asLong());
        if (subject != null) {
            t.putString("subject", subject.id);
        }
        t.putString("kind", kind.name());
        t.putInt("homeBase", homeBase);
        t.putLong("orderedAt", orderedAt);
        t.putBoolean("urgent", urgent);
        if (scout != null) {
            t.putUUID("scout", scout);
        }
        t.putLong("takenAt", takenAt);
        if (reported != ReconRules.ScoutReport.NONE) {
            t.putString("reported", reported.name()); // a taken order whose outcome a restart lost would never end
            t.putInt("reportedSides", reportedSides);
        }
        return t;
    }

    /** An order as {@link #save} wrote it, or null when it cannot be read (an unknown kind). */
    public static ReconOrder load(CompoundTag t) {
        ReconOrder o = new ReconOrder();
        try {
            o.kind = Report.Kind.valueOf(t.getString("kind"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        o.target = BlockPos.of(t.getLong("target"));
        o.subject = t.contains("subject") ? FactionType.byId(t.getString("subject")) : null;
        o.homeBase = t.contains("homeBase") ? t.getInt("homeBase") : -1;
        o.orderedAt = t.getLong("orderedAt");
        o.urgent = t.getBoolean("urgent");
        o.scout = t.hasUUID("scout") ? t.getUUID("scout") : null;
        o.takenAt = t.getLong("takenAt");
        try {
            o.reported = t.contains("reported") ? ReconRules.ScoutReport.valueOf(t.getString("reported")) : ReconRules.ScoutReport.NONE;
        } catch (IllegalArgumentException e) {
            o.reported = ReconRules.ScoutReport.NONE;
        }
        o.reportedSides = t.getInt("reportedSides");
        return o;
    }

    @Override
    public String toString() {
        return (urgent ? "urgent " : "") + "recon of " + (subject == null ? "?" : subject.id) + " " + kind + " at " + target.toShortString()
                + " (ordered " + orderedAt + (taken() ? ", taken " + takenAt + " by " + scout : ", not taken")
                + (reported != ReconRules.ScoutReport.NONE ? ", reported " + reported : "") + ")";
    }
}
