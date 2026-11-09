package com.factionclash.campaign;

import com.factionclash.faction.FactionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** World-saved campaign state, stored with the overworld. Single authoritative record of every faction. */
public final class CampaignData extends SavedData {
    public static final String NAME = "factionclash_campaign";
    /**
     * The save layout's version, written with every save. 2: mines, woodlands and fishing spots carry their own
     * per-record versions (mineV, woodV, fishV) and convert older records themselves on load (WorkplaceState.load), so
     * format 1 campaigns load and keep playing. It is read back only to be logged, never used as a gate.
     */
    public static final int FORMAT = 2;

    public boolean started;
    public BlockPos center = BlockPos.ZERO;
    public int radius;
    public long startedAt;
    public FactionType winner;
    public long finishedAt;
    public final EnumMap<FactionType, FactionState> factions = new EnumMap<>(FactionType.class);
    public final Map<Integer, BaseState> bases = new LinkedHashMap<>();
    public final Map<Integer, WorkplaceState> workplaces = new LinkedHashMap<>();
    public final Map<UUID, FactionType> players = new HashMap<>();
    /** The pact (Diplomacy): members sworn against one conqueror; null when there is none. */
    public FactionType pactAgainst;
    public final java.util.Set<FactionType> pactMembers = java.util.EnumSet.noneOf(FactionType.class);
    public long pactSince;
    public int nextBaseId = 1;
    public int nextWorkplaceId = 1;
    public int nextProjectId = 1;
    /** The format the campaign was saved with (0 before the format was written); only logged, never a gate. */
    public int loadedFormat = FORMAT;

    public static CampaignData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CampaignData::new, CampaignData::load), NAME);
    }

    public FactionState faction(FactionType type) {
        return factions.computeIfAbsent(type, FactionState::new);
    }

    public BaseState base(int id) {
        return bases.get(id);
    }

    public WorkplaceState workplace(int id) {
        return workplaces.get(id);
    }

    public List<BaseState> basesOf(FactionType type) {
        List<BaseState> out = new ArrayList<>();
        for (BaseState b : bases.values()) {
            if (b.faction == type) {
                out.add(b);
            }
        }
        return out;
    }

    public List<BaseState> livingBasesOf(FactionType type) {
        List<BaseState> out = new ArrayList<>();
        for (BaseState b : bases.values()) {
            if (b.faction == type && !b.destroyed) {
                out.add(b);
            }
        }
        return out;
    }

    public BaseState addBase(BaseState b) {
        b.id = nextBaseId++;
        bases.put(b.id, b);
        setDirty();
        return b;
    }

    public WorkplaceState addWorkplace(BaseState base, WorkplaceState w) {
        w.id = nextWorkplaceId++;
        w.baseId = base.id;
        workplaces.put(w.id, w);
        base.workplaces.add(w.id);
        setDirty();
        return w;
    }

    public NpcRecord record(UUID uuid) {
        for (FactionState f : factions.values()) {
            NpcRecord r = f.roster.get(uuid);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    public void reset() {
        started = false;
        winner = null;
        finishedAt = 0;
        factions.clear();
        bases.clear();
        workplaces.clear();
        players.clear();
        pactAgainst = null;
        pactMembers.clear();
        nextBaseId = 1;
        nextWorkplaceId = 1;
        nextProjectId = 1;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider provider) {
        t.putInt("format", FORMAT);
        t.putBoolean("started", started);
        t.putLong("center", center.asLong());
        t.putInt("radius", radius);
        t.putLong("startedAt", startedAt);
        if (winner != null) {
            t.putString("winner", winner.id);
        }
        t.putLong("finishedAt", finishedAt);
        ListTag fl = new ListTag();
        for (FactionState f : factions.values()) {
            fl.add(f.save());
        }
        t.put("factions", fl);
        ListTag bl = new ListTag();
        for (BaseState b : bases.values()) {
            bl.add(b.save(provider));
        }
        t.put("bases", bl);
        ListTag wl = new ListTag();
        for (WorkplaceState w : workplaces.values()) {
            wl.add(w.save());
        }
        t.put("workplaces", wl);
        CompoundTag pl = new CompoundTag();
        players.forEach((u, f) -> pl.putString(u.toString(), f.id));
        t.put("players", pl);
        if (pactAgainst != null) {
            t.putString("pactAgainst", pactAgainst.id);
            ListTag pm = new ListTag();
            pactMembers.forEach(m -> pm.add(net.minecraft.nbt.StringTag.valueOf(m.id)));
            t.put("pactMembers", pm);
            t.putLong("pactSince", pactSince);
        }
        t.putInt("nextBase", nextBaseId);
        t.putInt("nextWorkplace", nextWorkplaceId);
        t.putInt("nextProject", nextProjectId);
        return t;
    }

    public static CampaignData load(CompoundTag t, HolderLookup.Provider provider) {
        CampaignData d = new CampaignData();
        d.loadedFormat = t.getInt("format");
        if (d.loadedFormat < FORMAT) {
            // older records convert themselves as they load (WorkplaceState.load): hut mine kept, old fences taken down,
            // fishing spots moved off the farm to real water
            com.factionclash.FactionClash.LOGGER.info("Loading campaign format {} - migrating workplaces to format {}", d.loadedFormat, FORMAT);
        }
        d.started = t.getBoolean("started");
        d.center = BlockPos.of(t.getLong("center"));
        d.radius = t.getInt("radius");
        d.startedAt = t.getLong("startedAt");
        d.winner = t.contains("winner") ? FactionType.byId(t.getString("winner")) : null;
        d.finishedAt = t.getLong("finishedAt");
        ListTag fl = t.getList("factions", Tag.TAG_COMPOUND);
        for (int i = 0; i < fl.size(); i++) {
            FactionState f = FactionState.load(fl.getCompound(i));
            if (f != null) {
                d.factions.put(f.type, f);
            }
        }
        ListTag bl = t.getList("bases", Tag.TAG_COMPOUND);
        for (int i = 0; i < bl.size(); i++) {
            BaseState b = BaseState.load(bl.getCompound(i), provider);
            if (b != null) {
                d.bases.put(b.id, b);
            }
        }
        ListTag wl = t.getList("workplaces", Tag.TAG_COMPOUND);
        for (int i = 0; i < wl.size(); i++) {
            WorkplaceState w = WorkplaceState.load(wl.getCompound(i));
            if (w != null) {
                d.workplaces.put(w.id, w);
            }
        }
        if (t.contains("pactAgainst")) {
            d.pactAgainst = FactionType.byId(t.getString("pactAgainst"));
            ListTag pm = t.getList("pactMembers", Tag.TAG_STRING);
            for (int i = 0; i < pm.size(); i++) {
                FactionType m = FactionType.byId(pm.getString(i));
                if (m != null) {
                    d.pactMembers.add(m);
                }
            }
            d.pactSince = t.getLong("pactSince");
        }
        CompoundTag pl = t.getCompound("players");
        for (String k : pl.getAllKeys()) {
            FactionType f = FactionType.byId(pl.getString(k));
            if (f != null) {
                d.players.put(UUID.fromString(k), f);
            }
        }
        d.nextBaseId = Math.max(1, t.getInt("nextBase"));
        d.nextWorkplaceId = Math.max(1, t.getInt("nextWorkplace"));
        d.nextProjectId = Math.max(1, t.getInt("nextProject"));
        return d;
    }
}
