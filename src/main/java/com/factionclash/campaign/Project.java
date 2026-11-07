package com.factionclash.campaign;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** A queued construction job. Its block plan is regenerated deterministically from type + origin + faction. */
public final class Project {
    public enum Type {
        HOUSE("House"),
        STORAGE_EXPANSION("Storage expansion"),
        WATCHTOWER("Watchtower");

        public final String label;

        Type(String label) {
            this.label = label;
        }
    }

    public enum Status { GATHERING, BUILDING, DONE }

    public int id;
    public Type type;
    public BlockPos origin;
    public Status status = Status.GATHERING;
    /** Index into the plan of the next block to place; saved so work resumes after reloads. */
    public int placed;
    public long createdAt;

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putString("type", type.name());
        t.putLong("origin", origin.asLong());
        t.putString("status", status.name());
        t.putInt("placed", placed);
        t.putLong("created", createdAt);
        return t;
    }

    public static Project load(CompoundTag t) {
        Project p = new Project();
        p.id = t.getInt("id");
        try {
            p.type = Type.valueOf(t.getString("type"));
            p.status = Status.valueOf(t.getString("status"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        p.origin = BlockPos.of(t.getLong("origin"));
        p.placed = t.getInt("placed");
        p.createdAt = t.getLong("created");
        return p;
    }
}
