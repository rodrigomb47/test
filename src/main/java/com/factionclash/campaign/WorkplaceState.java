package com.factionclash.campaign;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** A personal, persistent workspace (spec 5): assigned worker, tier and upgrade progress. */
public final class WorkplaceState {
    public enum Type {
        MINE("Constructed mine", 4),
        TREE_PLOT("Tree plot", 3),
        FARM("Farm", 1),
        /** origin = pen centre (inside), extent = the pen's fence gate. */
        PEN("Animal pen", 1),
        /** origin = the water block fished, extent = where the fisherman stands. */
        FISHERY("Fishing spot", 1),
        /**
         * A natural cave surveyed by a cave explorer: origin = the entrance, extent = the cavity the scouts reported,
         * {@link #route}/{@link #parent} = the explorer's walked waypoints, {@link #marks} = ores it saw.
         */
        CAVE("Surveyed cave", 1);

        public final String label;
        public final int maxTier;

        Type(String label, int maxTier) {
            this.label = label;
            this.maxTier = maxTier;
        }
    }

    public int id;
    public int baseId;
    public Type type;
    public int tier = 1;
    /** Mine: surface entrance anchor. Tree plot: entrance (base-side fence centre). Farm: min corner. */
    public BlockPos origin;
    /** Direction pointing away from the base (mine tunnels and plot growth go this way). */
    public Direction facing = Direction.EAST;
    /** Farm: max corner. */
    public BlockPos extent = BlockPos.ZERO;
    public UUID assigned;
    /** Upgrade in progress: next build/dig step index; -1 when idle. */
    public int upgradeStep = -1;
    public long totalOutput;
    /** Mines: extra floors dug below tier 4 (free), and progress through the current level's tunnel plan. */
    public int subLevel;
    public int progress;
    /** Caves: waypoints the explorer walked (index 0 = entrance) and each waypoint's parent (-1 for the root). */
    public long[] route = new long[0];
    public int[] parent = new int[0];
    /** Caves: ore blocks seen and not yet mined (packed positions). */
    public final java.util.LinkedHashSet<Long> marks = new java.util.LinkedHashSet<>();
    /** Caves: when the last survey was delivered, a summary of it, and whether miners have worked it out. */
    public long surveyedAt;
    public String note = "";
    public boolean workedOut;
    /** Caves: where each prospecting tunnel meets rock (packed; Long.MIN_VALUE = no tunnel that way). */
    public long[] faces = new long[0];

    public boolean upgrading() {
        return upgradeStep >= 0;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putInt("base", baseId);
        t.putString("type", type.name());
        t.putInt("tier", tier);
        t.putLong("origin", origin.asLong());
        t.putString("facing", facing.getSerializedName());
        t.putLong("extent", extent.asLong());
        if (assigned != null) {
            t.putUUID("assigned", assigned);
        }
        t.putInt("upgradeStep", upgradeStep);
        t.putLong("output", totalOutput);
        t.putInt("subLevel", subLevel);
        t.putInt("progress", progress);
        if (type == Type.CAVE) {
            t.putLongArray("route", route);
            t.putIntArray("parent", parent);
            t.putLongArray("marks", marks.stream().mapToLong(Long::longValue).toArray());
            t.putLong("surveyedAt", surveyedAt);
            t.putString("note", note);
            t.putBoolean("workedOut", workedOut);
            t.putLongArray("faces", faces);
        }
        return t;
    }

    public static WorkplaceState load(CompoundTag t) {
        WorkplaceState w = new WorkplaceState();
        w.id = t.getInt("id");
        w.baseId = t.getInt("base");
        try {
            w.type = Type.valueOf(t.getString("type"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        w.tier = t.getInt("tier");
        w.origin = BlockPos.of(t.getLong("origin"));
        Direction d = Direction.byName(t.getString("facing"));
        w.facing = d == null ? Direction.EAST : d;
        w.extent = BlockPos.of(t.getLong("extent"));
        w.assigned = t.hasUUID("assigned") ? t.getUUID("assigned") : null;
        w.upgradeStep = t.contains("upgradeStep") ? t.getInt("upgradeStep") : -1;
        w.totalOutput = t.getLong("output");
        w.subLevel = t.getInt("subLevel");
        w.progress = t.getInt("progress");
        if (w.type == Type.CAVE) {
            w.route = t.getLongArray("route");
            w.parent = t.getIntArray("parent");
            if (w.parent.length != w.route.length) {
                w.parent = new int[w.route.length];
                for (int i = 0; i < w.parent.length; i++) {
                    w.parent[i] = i - 1; // a broken tree degrades to a chain
                }
            }
            for (long m : t.getLongArray("marks")) {
                w.marks.add(m);
            }
            w.surveyedAt = t.getLong("surveyedAt");
            w.note = t.getString("note");
            w.workedOut = t.getBoolean("workedOut");
            w.faces = t.getLongArray("faces");
        }
        return w;
    }
}
