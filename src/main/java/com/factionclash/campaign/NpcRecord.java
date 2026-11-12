package com.factionclash.campaign;

import com.factionclash.npc.NpcRole;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** The faction's authoritative roster entry for one NPC (identity survives unloads and reloads). */
public final class NpcRecord {
    public UUID uuid;
    public String name = "";
    public NpcRole role;
    public int baseId;
    public boolean alive = true;
    public BlockPos lastPos = BlockPos.ZERO;
    public long lastSeen;
    /** Scouts: tick of departure on the current expedition (0 when home). */
    public long departedAt;
    public int level;
    /** The hero this record is (empty for an ordinary NPC). A hero counts as one soldier of its role. */
    public String heroId = "";

    public boolean isHero() {
        return !heroId.isEmpty();
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("uuid", uuid);
        t.putString("name", name);
        t.putString("role", role.id);
        t.putInt("base", baseId);
        t.putBoolean("alive", alive);
        t.putLong("pos", lastPos.asLong());
        t.putLong("seen", lastSeen);
        t.putLong("departed", departedAt);
        t.putInt("level", level);
        if (isHero()) {
            t.putString("hero", heroId);
        }
        return t;
    }

    public static NpcRecord load(CompoundTag t) {
        NpcRecord r = new NpcRecord();
        r.uuid = t.getUUID("uuid");
        r.name = t.getString("name");
        r.role = NpcRole.byId(t.getString("role"));
        if (r.role == null) {
            return null;
        }
        r.baseId = t.getInt("base");
        r.alive = t.getBoolean("alive");
        r.lastPos = BlockPos.of(t.getLong("pos"));
        r.lastSeen = t.getLong("seen");
        r.departedAt = t.getLong("departed");
        r.level = t.getInt("level");
        r.heroId = t.getString("hero");
        return r;
    }
}
