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
            return r;
        }
        if (older.fallen) {
            return older.copy();
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
        return r;
    }
}
