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
        FARM("Farm", 1);

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
        return w;
    }
}
