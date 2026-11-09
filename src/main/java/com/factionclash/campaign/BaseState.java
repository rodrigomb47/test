package com.factionclash.campaign;

import com.factionclash.faction.FactionType;
import com.factionclash.npc.NpcRole;
import com.factionclash.storage.StorageCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A faction settlement or a Hollow lair. Base ownership and destruction follow the faction heart. */
public final class BaseState {
    public enum Kind { SETTLEMENT, LAIR }

    // Named functional points
    public static final String STATION = "station";
    public static final String STATION_STAND = "station_stand";
    public static final String MAP_BOARD = "map_board";
    public static final String HEART = "heart";
    public static final String SPAWN = "spawn";
    public static final String STORAGE_STAND = "storage_stand";
    public static final String WORKSHOP_STAND = "workshop_stand";
    public static final String CRAFTING = "crafting";
    public static final String FURNACE = "furnace";
    public static final String FURNACE_2 = "furnace2";
    public static final String SMOKER = "smoker";
    public static final String ANVIL = "anvil";
    public static final String ENCHANTING = "enchanting";
    public static final String STAGING = "staging";
    public static final String STAGING_SIGN = "staging_sign";
    public static final String GATE_WEST = "gate_w";
    public static final String GATE_EAST = "gate_e";
    public static final String GATE_SOUTH = "gate_s";
    public static final String MIN = "min";
    public static final String MAX = "max";

    public int id;
    public FactionType faction;
    public Kind kind = Kind.SETTLEMENT;
    public String name = "";
    public BlockPos center = BlockPos.ZERO;
    public boolean destroyed;
    public long destroyedAt;
    public final Map<String, BlockPos> points = new HashMap<>();
    public final EnumMap<StorageCategory, List<BlockPos>> storage = new EnumMap<>(StorageCategory.class);
    public final List<BlockPos> beds = new ArrayList<>();
    public final List<Integer> workplaces = new ArrayList<>();
    public boolean starterGranted;
    public final List<StructureRecord> structures = new ArrayList<>();
    /** Positions players deliberately changed; repairs leave them alone (spec 11). */
    public final Set<Long> playerEdits = new HashSet<>();
    /** Known damaged plan positions waiting for a builder. */
    public final LinkedHashSet<Long> pendingRepairs = new LinkedHashSet<>();
    /** Logistics: stock of each tracked resource over time, and the resources currently short. */
    public final java.util.Map<String, com.factionclash.logic.Ledger> ledgers = new java.util.LinkedHashMap<>();
    public final java.util.Set<String> shortages = new java.util.LinkedHashSet<>();
    public final List<Project> projects = new ArrayList<>();
    public int repairStructure;
    public int repairIndex;
    public long lastRecruitAt;
    public long lastProjectAt;
    /** Free expansion lots (house-sized) inside the walls, consumed by HOUSE projects. */
    public final List<BlockPos> expansionLots = new ArrayList<>();
    /** Reserved chest slots inside the storage house for STORAGE_EXPANSION projects. */
    public final List<BlockPos> storageExpansionSlots = new ArrayList<>();

    /**
     * Roles (by id) that found nothing to do here and were retrained: until the tick stored, the director does not
     * hire that role again, so a base without a place to mine, trees or fishing water does not keep paying for
     * recruits who stand idle. Missing in old saves, which means none.
     */
    public final Map<String, Long> barrenUntil = new HashMap<>();

    /**
     * GameTests only, never saved: the walls' half-size (-1 = the layout's) and the work radius around the centre
     * (-1 = the configured one), so test bases fit on a small platform.
     */
    public transient int testHalf = -1;
    public transient int testRadius = -1;

    // Lair state
    public final Set<UUID> lairMobs = new HashSet<>();
    public int lairCap;
    public long lastLairRaid;
    public long lastLairGrowth;
    public int lairRaidTarget = -1;
    public int lairRaidProgress;

    /** Transient index of planned blocks (rebuilt on demand, never saved). */
    private transient Map<Long, net.minecraft.world.level.block.state.BlockState> planIndex;

    public Map<Long, net.minecraft.world.level.block.state.BlockState> planIndex() {
        if (planIndex == null) {
            planIndex = new HashMap<>();
            for (StructureRecord r : structures) {
                for (int i = 0; i < r.size(); i++) {
                    planIndex.put(r.positions[i], r.state(i));
                }
            }
        }
        return planIndex;
    }

    public void invalidatePlanIndex() {
        planIndex = null;
    }

    public BlockPos point(String key) {
        return points.get(key);
    }

    public List<BlockPos> storage(StorageCategory cat) {
        return storage.computeIfAbsent(cat, c -> new ArrayList<>());
    }

    public List<BlockPos> allStoragePositions() {
        List<BlockPos> out = new ArrayList<>();
        for (StorageCategory c : StorageCategory.values()) {
            out.addAll(storage(c));
        }
        return out;
    }

    public boolean isSettlement() {
        return kind == Kind.SETTLEMENT;
    }

    /** Until when the director hires no new worker of this role (0 when it may). */
    public long barrenUntil(NpcRole role) {
        return barrenUntil.getOrDefault(role.id, 0L);
    }

    /** True while the role is barren here at tick {@code now}: its last worker had nothing to do. */
    public boolean barren(NpcRole role, long now) {
        return now < barrenUntil(role);
    }

    /** Marks the role barren until the given tick (never shortening an earlier mark). */
    public void markBarren(NpcRole role, long until) {
        barrenUntil.merge(role.id, until, Math::max);
    }

    public boolean contains(BlockPos pos, int margin) {
        BlockPos min = point(MIN), max = point(MAX);
        if (min == null || max == null) {
            return pos.closerThan(center, 24 + margin);
        }
        return pos.getX() >= min.getX() - margin && pos.getX() <= max.getX() + margin
                && pos.getZ() >= min.getZ() - margin && pos.getZ() <= max.getZ() + margin
                && pos.getY() >= min.getY() - margin - 32 && pos.getY() <= max.getY() + margin + 32;
    }

    /** True when pos is at least one chunk inside the base's force-loaded chunk rectangle. */
    public boolean deepInside(BlockPos pos) {
        BlockPos min = point(MIN), max = point(MAX);
        if (min == null || max == null) {
            return pos.closerThan(center, 16);
        }
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        return cx > (min.getX() >> 4) && cx < (max.getX() >> 4) && cz > (min.getZ() >> 4) && cz < (max.getZ() >> 4);
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putString("faction", faction.id);
        t.putString("kind", kind.name());
        t.putString("name", name);
        t.putLong("center", center.asLong());
        t.putBoolean("destroyed", destroyed);
        t.putLong("destroyedAt", destroyedAt);
        CompoundTag pts = new CompoundTag();
        points.forEach((k, v) -> pts.putLong(k, v.asLong()));
        t.put("points", pts);
        CompoundTag st = new CompoundTag();
        storage.forEach((k, v) -> st.put(k.name(), longs(v)));
        t.put("storage", st);
        t.put("beds", longs(beds));
        t.putIntArray("workplaces", workplaces.stream().mapToInt(Integer::intValue).toArray());
        t.putBoolean("starterGranted", starterGranted);
        ListTag structs = new ListTag();
        for (StructureRecord r : structures) {
            structs.add(r.save());
        }
        t.put("structures", structs);
        t.putLongArray("playerEdits", playerEdits.stream().mapToLong(Long::longValue).toArray());
        t.putLongArray("pendingRepairs", pendingRepairs.stream().mapToLong(Long::longValue).toArray());
        ListTag projs = new ListTag();
        for (Project p : projects) {
            projs.add(p.save());
        }
        t.put("projects", projs);
        t.putInt("repairStructure", repairStructure);
        t.putInt("repairIndex", repairIndex);
        t.putLong("lastRecruit", lastRecruitAt);
        t.putLong("lastProject", lastProjectAt);
        t.put("lots", longs(expansionLots));
        t.put("storageSlots", longs(storageExpansionSlots));
        ListTag mobs = new ListTag();
        for (UUID u : lairMobs) {
            mobs.add(NbtUtils.createUUID(u));
        }
        t.put("lairMobs", mobs);
        t.putInt("lairCap", lairCap);
        t.putLong("lastLairRaid", lastLairRaid);
        t.putLong("lastLairGrowth", lastLairGrowth);
        t.putInt("lairRaidTarget", lairRaidTarget);
        t.putInt("lairRaidProgress", lairRaidProgress);
        CompoundTag led = new CompoundTag();
        ledgers.forEach((k, l) -> {
            CompoundTag e = new CompoundTag();
            e.putLongArray("t", l.times());
            e.putIntArray("v", l.values());
            led.put(k, e);
        });
        t.put("ledgers", led);
        t.putString("shortages", String.join(",", shortages));
        CompoundTag barren = new CompoundTag();
        barrenUntil.forEach(barren::putLong);
        t.put("barrenUntil", barren);
        return t;
    }

    public static BaseState load(CompoundTag t, HolderLookup.Provider provider) {
        BaseState b = new BaseState();
        b.id = t.getInt("id");
        b.faction = FactionType.byId(t.getString("faction"));
        if (b.faction == null) {
            return null;
        }
        try {
            b.kind = Kind.valueOf(t.getString("kind"));
        } catch (IllegalArgumentException e) {
            b.kind = Kind.SETTLEMENT;
        }
        b.name = t.getString("name");
        b.center = BlockPos.of(t.getLong("center"));
        b.destroyed = t.getBoolean("destroyed");
        b.destroyedAt = t.getLong("destroyedAt");
        CompoundTag pts = t.getCompound("points");
        for (String k : pts.getAllKeys()) {
            b.points.put(k, BlockPos.of(pts.getLong(k)));
        }
        CompoundTag st = t.getCompound("storage");
        for (String k : st.getAllKeys()) {
            try {
                b.storage.put(StorageCategory.valueOf(k), positions(st.get(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        b.beds.addAll(positions(t.get("beds")));
        for (int i : t.getIntArray("workplaces")) {
            b.workplaces.add(i);
        }
        b.starterGranted = t.getBoolean("starterGranted");
        ListTag structs = t.getList("structures", Tag.TAG_COMPOUND);
        for (int i = 0; i < structs.size(); i++) {
            StructureRecord r = StructureRecord.load(structs.getCompound(i), provider);
            if (r != null) {
                b.structures.add(r);
            }
        }
        for (long l : t.getLongArray("playerEdits")) {
            b.playerEdits.add(l);
        }
        for (long l : t.getLongArray("pendingRepairs")) {
            b.pendingRepairs.add(l);
        }
        ListTag projs = t.getList("projects", Tag.TAG_COMPOUND);
        for (int i = 0; i < projs.size(); i++) {
            Project p = Project.load(projs.getCompound(i));
            if (p != null) {
                b.projects.add(p);
            }
        }
        b.repairStructure = t.getInt("repairStructure");
        b.repairIndex = t.getInt("repairIndex");
        b.lastRecruitAt = t.getLong("lastRecruit");
        b.lastProjectAt = t.getLong("lastProject");
        b.expansionLots.addAll(positions(t.get("lots")));
        b.storageExpansionSlots.addAll(positions(t.get("storageSlots")));
        ListTag mobs = t.getList("lairMobs", Tag.TAG_INT_ARRAY);
        for (Tag m : mobs) {
            b.lairMobs.add(NbtUtils.loadUUID(m));
        }
        b.lairCap = t.getInt("lairCap");
        b.lastLairRaid = t.getLong("lastLairRaid");
        b.lastLairGrowth = t.getLong("lastLairGrowth");
        b.lairRaidTarget = t.contains("lairRaidTarget") ? t.getInt("lairRaidTarget") : -1;
        b.lairRaidProgress = t.getInt("lairRaidProgress");
        CompoundTag led = t.getCompound("ledgers");
        for (String k : led.getAllKeys()) {
            b.ledgers.put(k, com.factionclash.logic.Ledger.of(led.getCompound(k).getLongArray("t"), led.getCompound(k).getIntArray("v")));
        }
        for (String s : t.getString("shortages").split(",")) {
            if (!s.isEmpty()) {
                b.shortages.add(s);
            }
        }
        CompoundTag barren = t.getCompound("barrenUntil");
        for (String k : barren.getAllKeys()) {
            b.barrenUntil.put(k, barren.getLong(k));
        }
        return b;
    }

    private static LongArrayTag longs(List<BlockPos> list) {
        return new LongArrayTag(list.stream().mapToLong(BlockPos::asLong).toArray());
    }

    private static List<BlockPos> positions(Tag tag) {
        List<BlockPos> out = new ArrayList<>();
        if (tag instanceof LongArrayTag lat) {
            for (long l : lat.getAsLongArray()) {
                out.add(BlockPos.of(l));
            }
        }
        return out;
    }
}
