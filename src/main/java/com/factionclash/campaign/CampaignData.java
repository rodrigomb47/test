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
     * format 1 campaigns load and keep playing. 3: heroes ({@link #heroes}, an empty map when missing). 4: the roll call
     * (roster entries' whereabouts and truth, factions' carried news; {@code docs/design/missing_npcs.md} 10: an older
     * entry reads as here, away or known dead). It is read back only to be logged, never used as a gate.
     */
    public static final int FORMAT = 4;

    public boolean started;
    public BlockPos center = BlockPos.ZERO;
    public int radius;
    public long startedAt;
    /**
     * This campaign's own id, a random number other than 0 given when a campaign is prepared ({@link #reset}), or when a
     * started campaign saved before 2026-11-25 loads. Each member carries the id of the campaign it was recruited in
     * ({@code FactionNpc.campaignId}), so a member of an ended campaign that loads later is told apart. 0: none yet.
     */
    public long campaignId;
    public FactionType winner;
    public long finishedAt;
    public final EnumMap<FactionType, FactionState> factions = new EnumMap<>(FactionType.class);
    public final Map<Integer, BaseState> bases = new LinkedHashMap<>();
    public final Map<Integer, WorkplaceState> workplaces = new LinkedHashMap<>();
    public final Map<UUID, FactionType> players = new HashMap<>();
    /** Every hero that has ever been given to a faction, by hero id (HeroState: owner, base, entity, alive, revival). */
    public final Map<String, HeroState> heroes = new LinkedHashMap<>();
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

    /** A random campaign id, never 0 (0 means none). */
    private static long newCampaignId() {
        long id = 0;
        while (id == 0) {
            id = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        }
        return id;
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
        campaignId = newCampaignId(); // a new campaign: members of the old one are told apart when they load
        started = false;
        winner = null;
        finishedAt = 0;
        factions.clear();
        bases.clear();
        workplaces.clear();
        players.clear();
        // a new campaign frees every hero (they keep their level for the next owner, and their last UUID, so a stale
        // copy of an old entity that loads later is discarded); HeroManager.releaseAll removes the loaded entities
        for (HeroState h : heroes.values()) {
            h.owner = null;
            h.baseId = -1;
            h.alive = false;
            h.reviveAt = 0;
        }
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
        t.putLong("campaignId", campaignId);
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
        ListTag hl = new ListTag();
        for (HeroState h : heroes.values()) {
            hl.add(h.save());
        }
        t.put("heroes", hl);
        t.putInt("nextBase", nextBaseId);
        t.putInt("nextWorkplace", nextWorkplaceId);
        t.putInt("nextProject", nextProjectId);
        return t;
    }

    public static CampaignData load(CompoundTag t, HolderLookup.Provider provider) {
        CampaignData d = new CampaignData();
        d.loadedFormat = t.getInt("format");
        if (d.loadedFormat < 2) {
            // older records convert themselves as they load (WorkplaceState.load): hut mine kept, old fences taken down,
            // fishing spots moved off the farm to real water
            com.factionclash.FactionClash.LOGGER.info("Loading campaign format {} - migrating workplaces to format {}", d.loadedFormat, FORMAT);
        } else if (d.loadedFormat < 3) {
            com.factionclash.FactionClash.LOGGER.info("Loading campaign format {} (no heroes yet) as format {}", d.loadedFormat, FORMAT);
        } else if (d.loadedFormat < FORMAT) {
            com.factionclash.FactionClash.LOGGER.info("Loading campaign format {} (no roll call yet: members read as here, away or known dead) as format {}",
                    d.loadedFormat, FORMAT);
        }
        d.started = t.getBoolean("started");
        d.center = BlockPos.of(t.getLong("center"));
        d.radius = t.getInt("radius");
        d.startedAt = t.getLong("startedAt");
        d.campaignId = t.getLong("campaignId");
        if (d.campaignId == 0 && d.started) {
            d.campaignId = newCampaignId(); // (a campaign saved before ids: its members recruited from now on carry it)
            d.setDirty();
        }
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
        // (a save from before 2026-11-25 may hold a pact against a conqueror, "pactAgainst", "pactMembers" and
        // "pactSince": the owner removed pacts, so those keys are left unread and are gone at the next save)
        ListTag hl = t.getList("heroes", Tag.TAG_COMPOUND);
        for (int i = 0; i < hl.size(); i++) {
            HeroState h = HeroState.load(hl.getCompound(i));
            if (h != null) {
                d.heroes.put(h.id, h);
            }
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
