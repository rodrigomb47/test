package com.factionclash.campaign;

import com.factionclash.faction.FactionType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** One delivered observation. Faction knowledge only contains reports a scout physically brought home. */
public final class Report {
    public enum Kind {
        ENEMY_BASE("Enemy settlement", 12, true),
        LAIR("Monster lair", 12, true),
        HOSTILES("Hostile group", 4, true),
        CAVE("Cave entrance", 8, false),
        ORE("Exposed ore", 6, false),
        VILLAGE("Village", 8, false),
        // appended: kinds are saved by name, and the map's marker kinds follow this order
        PILLAGER_OUTPOST("Pillager outpost", 10, false),
        WOODLAND_MANSION("Woodland mansion", 12, false);

        public final String label;
        /** Base experience for delivering this kind of report. */
        public final int xpValue;
        /** Observations of moving things become stale. */
        public final boolean canGoStale;

        Kind(String label, int xpValue, boolean canGoStale) {
            this.label = label;
            this.xpValue = xpValue;
            this.canGoStale = canGoStale;
        }

        /** Structures of the world, which stay where they are: villages, pillager outposts and woodland mansions. */
        public boolean structure() {
            return this == VILLAGE || this == PILLAGER_OUTPOST || this == WOODLAND_MANSION;
        }

        /** Ground the faction's people keep away from: pillager outposts and woodland mansions. */
        public boolean dangerousGround() {
            return this == PILLAGER_OUTPOST || this == WOODLAND_MANSION;
        }
    }

    public Kind kind;
    public BlockPos pos;
    public long observedAt;
    public long deliveredAt;
    public FactionType subject;
    public int count;
    public String by = "";
    public String note = "";

    public Report() {
    }

    public Report(Kind kind, BlockPos pos, long observedAt, FactionType subject, int count, String note) {
        this.kind = kind;
        this.pos = pos;
        this.observedAt = observedAt;
        this.subject = subject;
        this.count = count;
        this.note = note == null ? "" : note;
    }

    /**
     * Two reports describe the same thing when kind and subject match and they are close together: 24 blocks for a
     * hostile group, 64 for a structure (a village seen from its edge, from its centre or where its traders gather;
     * two villages, outposts or mansions are never that close), 40 otherwise.
     */
    public boolean sameSubject(Report o) {
        if (kind != o.kind || subject != o.subject) {
            return false;
        }
        int r = kind == Kind.HOSTILES ? 24 : kind.structure() ? com.factionclash.logic.StructureRules.SAME_STRUCTURE : 40;
        return pos.distSqr(o.pos) <= (double) r * r;
    }

    /** A village whose traders a scout has seen: worth a trader's trip. Count is the trading villagers seen there. */
    public boolean tradingVillage() {
        return kind == Kind.VILLAGE && count > 0;
    }

    /**
     * The report to keep of two observations of the same subject: the newer one, except that a village seen with no
     * trading villagers (from afar, or with its people indoors) keeps the traders an older observation saw.
     */
    public static Report merge(Report older, Report newer) {
        if (newer.kind != Kind.VILLAGE || newer.count > 0 || older.count <= 0) {
            return newer;
        }
        Report r = newer.copy();
        r.count = com.factionclash.logic.StructureRules.mergedTraders(older.count, newer.count);
        r.note = older.note;
        return r;
    }

    public Report copy() {
        Report r = new Report(kind, pos, observedAt, subject, count, note);
        r.deliveredAt = deliveredAt;
        r.by = by;
        return r;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("kind", kind.name());
        t.putLong("pos", pos.asLong());
        t.putLong("observed", observedAt);
        t.putLong("delivered", deliveredAt);
        if (subject != null) {
            t.putString("subject", subject.id);
        }
        t.putInt("count", count);
        t.putString("by", by);
        t.putString("note", note);
        return t;
    }

    public static Report load(CompoundTag t) {
        Report r = new Report();
        try {
            r.kind = Kind.valueOf(t.getString("kind"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        r.pos = BlockPos.of(t.getLong("pos"));
        r.observedAt = t.getLong("observed");
        r.deliveredAt = t.getLong("delivered");
        r.subject = t.contains("subject") ? FactionType.byId(t.getString("subject")) : null;
        r.count = t.getInt("count");
        r.by = t.getString("by");
        r.note = t.getString("note");
        return r;
    }
}
