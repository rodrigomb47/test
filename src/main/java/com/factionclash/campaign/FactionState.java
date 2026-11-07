package com.factionclash.campaign;

import com.factionclash.faction.FactionType;
import com.factionclash.npc.NpcRole;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Faction-wide state: survival, delivered knowledge, roster and war plans. */
public final class FactionState {
    public static final int MAX_REPORTS = 160;

    public FactionType type;
    public boolean alive = true;
    public long eliminatedAt;
    public final List<Report> knowledge = new ArrayList<>();
    public final Map<UUID, NpcRecord> roster = new LinkedHashMap<>();
    public long lastRaidAt;
    /** Active raid: target base id (-1 none), members and launch time. */
    public int raidTarget = -1;
    public final List<UUID> raiders = new ArrayList<>();
    public long raidStartedAt;
    public int raidsLaunched;
    public int basesDestroyed;
    /** 32x32 map sectors covered by delivered scout reports. */
    public final java.util.Set<Long> explored = new java.util.HashSet<>();
    public int reportsDelivered;

    public FactionState() {
    }

    public static long sector(int x, int z) {
        return ((long) (x >> 5) << 32) | ((z >> 5) & 0xffffffffL);
    }

    public static int sectorCenterX(long key) {
        return (int) (key >> 32) * 32 + 16;
    }

    public static int sectorCenterZ(long key) {
        return (int) key * 32 + 16;
    }

    public FactionState(FactionType type) {
        this.type = type;
    }

    public long living(NpcRole role) {
        return roster.values().stream().filter(r -> r.alive && r.role == role).count();
    }

    public long living() {
        return roster.values().stream().filter(r -> r.alive).count();
    }

    /** Adds or refreshes a delivered report, replacing an older observation of the same subject. */
    public boolean addReport(Report r) {
        for (int i = 0; i < knowledge.size(); i++) {
            Report old = knowledge.get(i);
            if (old.sameSubject(r)) {
                if (r.observedAt >= old.observedAt) {
                    knowledge.set(i, r);
                }
                return false;
            }
        }
        knowledge.add(r);
        while (knowledge.size() > MAX_REPORTS) {
            knowledge.remove(0);
        }
        return true;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("type", type.id);
        t.putBoolean("alive", alive);
        t.putLong("eliminatedAt", eliminatedAt);
        ListTag k = new ListTag();
        for (Report r : knowledge) {
            k.add(r.save());
        }
        t.put("knowledge", k);
        ListTag ros = new ListTag();
        for (NpcRecord r : roster.values()) {
            ros.add(r.save());
        }
        t.put("roster", ros);
        t.putLong("lastRaid", lastRaidAt);
        t.putInt("raidTarget", raidTarget);
        ListTag rd = new ListTag();
        for (UUID u : raiders) {
            rd.add(NbtUtils.createUUID(u));
        }
        t.put("raiders", rd);
        t.putLong("raidStarted", raidStartedAt);
        t.putInt("raidsLaunched", raidsLaunched);
        t.putInt("basesDestroyed", basesDestroyed);
        t.putLongArray("explored", explored.stream().mapToLong(Long::longValue).toArray());
        t.putInt("reportsDelivered", reportsDelivered);
        return t;
    }

    public static FactionState load(CompoundTag t) {
        FactionState f = new FactionState();
        f.type = FactionType.byId(t.getString("type"));
        if (f.type == null) {
            return null;
        }
        f.alive = t.getBoolean("alive");
        f.eliminatedAt = t.getLong("eliminatedAt");
        ListTag k = t.getList("knowledge", Tag.TAG_COMPOUND);
        for (int i = 0; i < k.size(); i++) {
            Report r = Report.load(k.getCompound(i));
            if (r != null) {
                f.knowledge.add(r);
            }
        }
        ListTag ros = t.getList("roster", Tag.TAG_COMPOUND);
        for (int i = 0; i < ros.size(); i++) {
            NpcRecord r = NpcRecord.load(ros.getCompound(i));
            if (r != null) {
                f.roster.put(r.uuid, r);
            }
        }
        f.lastRaidAt = t.getLong("lastRaid");
        f.raidTarget = t.contains("raidTarget") ? t.getInt("raidTarget") : -1;
        for (Tag u : t.getList("raiders", Tag.TAG_INT_ARRAY)) {
            f.raiders.add(NbtUtils.loadUUID(u));
        }
        f.raidStartedAt = t.getLong("raidStarted");
        f.raidsLaunched = t.getInt("raidsLaunched");
        f.basesDestroyed = t.getInt("basesDestroyed");
        for (long l : t.getLongArray("explored")) {
            f.explored.add(l);
        }
        f.reportsDelivered = t.getInt("reportsDelivered");
        return f;
    }
}
