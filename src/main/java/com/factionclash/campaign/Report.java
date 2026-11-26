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
    /**
     * ENEMY_BASE and LAIR (knowledge review of 2026-11-19): what the observer saw of the defence there, with when (null:
     * no look at it, a report saved before 2026-11-19 or one told by others). The war council judges the base by this
     * only ({@link com.factionclash.logic.campaign.Intel}).
     */
    public com.factionclash.logic.campaign.Intel.Look look;
    /** ENEMY_BASE: the heroes seen there, "id=war power" joined by commas (kept from one look to the next). */
    public String heroes = "";
    /** ENEMY_BASE and LAIR: seen fallen, its heart gone (ruins replace the standing report). */
    public boolean fallen;
    /** ENEMY_BASE: soldiers of this settlement the faction saw fall since the look (at its walls), and when the last fell. */
    public int losses;
    public long lossAt;
    /**
     * ENEMY_BASE and LAIR, focused reconnaissance ({@code docs/design/recon.md} 4; task R2, nothing reads it until the
     * council's switch, R5). Two roles, by where the report is:
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
     * Two reports of one settlement or lair (knowledge review of 2026-11-19): the newer is kept, with what the older saw
     * that it did not. Ruins are ruins, and stay ruins: no base is ever rebuilt, so a later look that sees the yard or the
     * walls but not the heart's place does not raise it again (review of the knowledge review, 2026-11-19: the council
     * sent raid after raid to ruins its people had seen). A newer report with no look at the defence (told by others) keeps
     * the older look; the heroes seen are kept from both, each with its newest sighting (the council counts a sighting for
     * less as it ages, {@link com.factionclash.logic.campaign.Intel#heroPower}); the losses seen since the kept look are
     * kept. {@code trip}: two looks on one trip (or a trip's and the raiders'): the one that saw more of the yard is the
     * look kept ({@link com.factionclash.logic.campaign.Intel#better}).
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
        // the focused recon (recon.md 4). On a trip both are a scout's own reports, not yet delivered: the newer watch's
        // recon, good or blind, else the older one, and every side tried. Otherwise the older report's file, with what
        // the newer one brought (a passing look, no recon, keeps it untouched); FactionState.addReport sets the roles
        // itself by delivery (fileRecon), whatever the observedAt order.
        if (trip) {
            r.recon = newer.recon == null || older.recon != null && older.recon.at() > newer.recon.at() ? older.recon : newer.recon;
            r.reconSidesTried = older.reconSidesTried | newer.reconSidesTried;
        } else {
            r.reconFile(Intel.mergeRecon(older.reconFile(), newer.recon, newer.reconSidesTried));
        }
        com.factionclash.logic.campaign.Intel.Look look = trip ? com.factionclash.logic.campaign.Intel.better(older.look, newer.look)
                : newer.look != null ? newer.look : older.look;
        r.heroes = com.factionclash.logic.campaign.Intel.heroes(com.factionclash.logic.campaign.Intel.mergeHeroes(
                com.factionclash.logic.campaign.Intel.heroes(older.heroes, older.seenAt()),
                com.factionclash.logic.campaign.Intel.heroes(newer.heroes, newer.seenAt())));
        if (look != null && (look == older.look || older.look != null && look.at() == older.look.at() && look.soldiers() == older.look.soldiers())) {
            r.count = older.count;
            r.note = older.note;
        }
        r.look = look;
        long since = look == null ? 0 : look.at();
        int losses = (older.lossAt > since ? older.losses : 0) + (newer.lossAt > since ? newer.losses : 0);
        r.losses = losses;
        r.lossAt = Math.max(older.lossAt, newer.lossAt);
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

    /** When what this report saw of a defence was seen: its look's time, else when it was observed. */
    public long seenAt() {
        return look != null ? look.at() : observedAt;
    }

    public Report copy() {
        Report r = new Report(kind, pos, observedAt, subject, count, note);
        r.deliveredAt = deliveredAt;
        r.by = by;
        r.look = look;
        r.heroes = heroes;
        r.fallen = fallen;
        r.losses = losses;
        r.lossAt = lossAt;
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
        if (look != null) {
            CompoundTag l = new CompoundTag();
            l.putInt("soldiers", look.soldiers());
            l.putDouble("visible", look.visible());
            l.putDouble("power", look.power());
            l.putDouble("heroPower", look.heroPower());
            l.putLong("at", look.at());
            t.put("look", l);
        }
        if (!heroes.isEmpty()) {
            t.putString("heroes", heroes);
        }
        if (fallen) {
            t.putBoolean("fallen", true);
        }
        if (losses > 0) {
            t.putInt("losses", losses);
            t.putLong("lossAt", lossAt);
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
     * vantage, by whom, and the faces counted (their UUIDs).
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
        return t;
    }

    /** A focused recon as {@link #saveRecon} wrote it. */
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
                t.getLong("watch"), t.getLong("at"), t.getString("vantage"), t.getString("by"), faces);
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
        // the look, the heroes, ruins and losses: absent before the knowledge review of 2026-11-19 (no look: the prior)
        if (t.contains("look")) {
            CompoundTag l = t.getCompound("look");
            r.look = new com.factionclash.logic.campaign.Intel.Look(l.getInt("soldiers"), l.getDouble("visible"), l.getDouble("power"),
                    l.getDouble("heroPower"), l.getLong("at"));
        }
        r.heroes = t.getString("heroes");
        r.fallen = t.getBoolean("fallen");
        r.losses = t.getInt("losses");
        r.lossAt = t.getLong("lossAt");
        // the focused recon and its back-off: absent before campaign format 5 (2026-11-26; an old report is a position
        // and its old look, which the old council reads until the switch, R5)
        r.recon = t.contains("recon") ? loadRecon(t.getCompound("recon")) : null;
        r.reconFailedAt = t.getLong("reconFailedAt");
        r.reconFailures = t.getInt("reconFailures");
        r.reconSidesTried = t.getInt("reconSidesTried");
        return r;
    }
}
