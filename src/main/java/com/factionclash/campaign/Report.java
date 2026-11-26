package com.factionclash.campaign;

import com.factionclash.faction.FactionType;
import com.factionclash.logic.campaign.Intel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    /** ENEMY_BASE and LAIR: seen fallen, its heart gone (ruins replace the standing report). */
    public boolean fallen;
    /**
     * ENEMY_BASE and LAIR, focused reconnaissance ({@code docs/design/recon.md} 4; task R2): the only thing the war council
     * reads of a base's defence (task R5). Two roles, by where the report is:
     * <ul>
     * <li>on a report a scout delivers (incoming): {@code recon} is what its watch of the base gave, good or blind, and
     * {@link #reconSidesTried} the sides its trip tried; the failure fields are not read;</li>
     * <li>on the faction's kept report (in {@link FactionState#knowledge}): {@code recon} is the newest <b>good</b> focused
     * look only (null: none; a blind one is never kept here, so a council never takes it for "about 0" and a target with
     * only blind recons still calls for a new one), {@link #reconFailedAt} and {@link #reconFailures} the attempts that
     * failed since it (the back-off), and {@link #reconSidesTried} the sides tried since it.</li>
     * </ul>
     * {@link FactionState#addReport} turns the first into the second by {@link Intel#mergeRecon}'s table; see
     * {@link #reconFile} and {@link #fileRecon}.
     */
    public Intel.Recon recon;
    /** Kept report: when the last failed recon attempt on this base was (0: none since the kept recon). */
    public long reconFailedAt;
    /** Kept report: the failed recon attempts in a row since the kept recon (the back-off doubles with each). */
    public int reconFailures;
    /** Kept report: the sides tried since the last good recon; delivered report: the sides its trip tried ({@link com.factionclash.logic.campaign.ReconRules.Side} bits). */
    public int reconSidesTried;

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
     * A passing look's note (recon.md 3.6, task R5): "Grove Covenant settlement seen", "The Hollow lair seen". It tells where
     * the base is and that it stands, and no count.
     */
    public static String positionNote(FactionType subject, Kind kind) {
        return (subject == null ? "Enemy" : subject.displayName) + (kind == Kind.LAIR ? " lair" : " settlement") + " seen";
    }

    /** A settlement's or a lair's report: one the war council reads. */
    public boolean base() {
        return kind == Kind.ENEMY_BASE || kind == Kind.LAIR;
    }

    /**
     * The report to keep of two observations of the same subject: the newer one, except that a village seen with no
     * trading villagers (from afar, or with its people indoors) keeps the traders an older observation saw, and a
     * settlement or lair keeps what the older report saw of it that the newer did not ({@link #mergeBase}).
     */
    public static Report merge(Report older, Report newer) {
        if (newer.base() && older.base()) {
            return mergeBase(older, newer, false);
        }
        if (newer.kind != Kind.VILLAGE || newer.count > 0 || older.count <= 0) {
            return newer;
        }
        Report r = newer.copy();
        r.count = com.factionclash.logic.StructureRules.mergedTraders(older.count, newer.count);
        r.note = older.note;
        return r;
    }

    /**
     * Two reports of one settlement or lair (knowledge review of 2026-11-19; recon.md 4): the newer is kept, with the
     * focused recon the older one held. Ruins are ruins, and stay ruins: no base is ever rebuilt, so a later look that sees
     * the yard or the walls but not the heart's place does not raise it again (review of the knowledge review, 2026-11-19:
     * the council sent raid after raid to ruins its people had seen). {@code trip}: two of a scout's own reports on one trip,
     * not yet delivered: the newer watch's recon, good or blind, else the older one, and every side tried. Otherwise the
     * older report's file, with what the newer one brought (a passing look, no recon, keeps it untouched);
     * {@link FactionState#addReport} sets the roles itself by delivery ({@link #fileRecon}), whatever the observedAt order.
     */
    public static Report mergeBase(Report older, Report newer, boolean trip) {
        Report r = newer.copy();
        if (newer.fallen) {
            r.reconFile(Intel.reconRuins()); // ruins: the recon dropped
            return r;
        }
        if (older.fallen) {
            return older.copy();
        }
        if (trip) {
            r.recon = newer.recon == null || older.recon != null && older.recon.at() > newer.recon.at() ? older.recon : newer.recon;
            r.reconSidesTried = older.reconSidesTried | newer.reconSidesTried;
        } else {
            r.reconFile(Intel.mergeRecon(older.reconFile(), newer.recon, newer.reconSidesTried));
        }
        return r;
    }

    /**
     * The focused-recon file of a kept report ({@link Intel.ReconFile}): its good recon (a blind one, which a kept report
     * never holds, is left out), the back-off and the sides tried, and whether it is ruins.
     */
    public Intel.ReconFile reconFile() {
        return new Intel.ReconFile(recon != null && recon.good() ? recon : null, reconFailedAt, reconFailures, reconSidesTried, fallen);
    }

    /** Sets the kept report's recon fields from {@code f} (its ruins flag is the report's own {@link #fallen}). */
    public void reconFile(Intel.ReconFile f) {
        recon = f.recon();
        reconFailedAt = f.failedAt();
        reconFailures = f.failures();
        reconSidesTried = f.sidesTried();
    }

    /**
     * What a faction keeps of its recons of one base after {@code incoming} is delivered (recon.md 4's table,
     * {@link Intel#mergeRecon}): {@code kept}'s file (null: a base never reported), with the recon {@code incoming}
     * brought and the sides its trip tried. The roles are by delivery, not by {@link #observedAt}: a late scout's recon
     * delivered after a newer passing look is still the newer recon when its watch was newer, and is ignored when it was
     * older. A passing look (no recon) leaves the file as it was; ruins on either side make it ruins.
     */
    public static Intel.ReconFile fileRecon(Report kept, Report incoming) {
        if (kept != null && kept.fallen || incoming.fallen) {
            return Intel.reconRuins();
        }
        return Intel.mergeRecon(kept == null ? Intel.ReconFile.NONE : kept.reconFile(), incoming.recon, incoming.reconSidesTried);
    }

    public Report copy() {
        Report r = new Report(kind, pos, observedAt, subject, count, note);
        r.deliveredAt = deliveredAt;
        r.by = by;
        r.fallen = fallen;
        r.recon = recon;
        r.reconFailedAt = reconFailedAt;
        r.reconFailures = reconFailures;
        r.reconSidesTried = reconSidesTried;
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
        if (fallen) {
            t.putBoolean("fallen", true);
        }
        if (recon != null) {
            t.put("recon", saveRecon(recon));
        }
        if (reconFailures > 0 || reconFailedAt != 0) {
            t.putLong("reconFailedAt", reconFailedAt);
            t.putInt("reconFailures", reconFailures);
        }
        if (reconSidesTried != 0) {
            t.putInt("reconSidesTried", reconSidesTried);
        }
        return t;
    }

    /**
     * A focused recon as saved (recon.md 7, campaign format 5): the soldiers counted, their mean power, the heroes seen
     * (hero id to war power, each a double, so a power reads back exactly), the coverage, the middle, the watch, when, the
     * vantage, by whom, the faces counted (their UUIDs) and the instrument it was watched through (task R12: "spyglass";
     * no tag for the eyes, as every recon before R12 was).
     */
    public static CompoundTag saveRecon(Intel.Recon r) {
        CompoundTag t = new CompoundTag();
        t.putInt("soldiers", r.soldiers());
        t.putDouble("power", r.power());
        CompoundTag heroes = new CompoundTag();
        r.heroes().forEach(heroes::putDouble);
        t.put("heroes", heroes);
        t.putDouble("coverage", r.coverage());
        t.putBoolean("middle", r.middle());
        t.putLong("watch", r.watchTicks());
        t.putLong("at", r.at());
        t.putString("vantage", r.vantage());
        t.putString("by", r.by());
        ListTag faces = new ListTag();
        for (UUID u : r.faces()) {
            faces.add(NbtUtils.createUUID(u));
        }
        t.put("faces", faces);
        if (!r.optic().isEmpty()) {
            t.putString("optic", r.optic());
        }
        return t;
    }

    /** A focused recon as {@link #saveRecon} wrote it (one saved before task R12 has no tag: the eyes). */
    public static Intel.Recon loadRecon(CompoundTag t) {
        Map<String, Double> heroes = new LinkedHashMap<>();
        CompoundTag h = t.getCompound("heroes");
        for (String id : h.getAllKeys()) {
            heroes.put(id, h.getDouble(id));
        }
        Set<UUID> faces = new LinkedHashSet<>();
        for (Tag u : t.getList("faces", Tag.TAG_INT_ARRAY)) {
            faces.add(NbtUtils.loadUUID(u));
        }
        return new Intel.Recon(t.getInt("soldiers"), t.getDouble("power"), heroes, t.getDouble("coverage"), t.getBoolean("middle"),
                t.getLong("watch"), t.getLong("at"), t.getString("vantage"), t.getString("by"), faces, t.getString("optic"));
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
        r.fallen = t.getBoolean("fallen");
        // the focused recon and its back-off: absent before campaign format 5 (2026-11-26). The old council's tags (the look
        // at the yard, the heroes seen there, the losses seen at our walls) are ignored since the council's switch (task
        // R5): such a report is a position only, its count and its note's numbers dropped, and its base is judged again by
        // a recon (recon.md 7)
        r.recon = t.contains("recon") ? loadRecon(t.getCompound("recon")) : null;
        if (r.base() && (t.contains("look") || t.contains("losses") || t.contains("heroes"))) {
            r.count = 0;
            r.note = positionNote(r.subject, r.kind);
        }
        r.reconFailedAt = t.getLong("reconFailedAt");
        r.reconFailures = t.getInt("reconFailures");
        r.reconSidesTried = t.getInt("reconSidesTried");
        return r;
    }
}
