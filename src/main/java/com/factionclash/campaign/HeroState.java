package com.factionclash.campaign;

import com.factionclash.faction.FactionType;
import com.factionclash.logic.Affix;
import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * The campaign's record of one hero (HEROES_V2 3.3, v1 25.1): who it serves, its home base, the UUID of its one
 * living entity, whether it is alive and when a fallen hero returns, and the progress that outlives a death (level,
 * experience, traits). A hero id has at most one record and one living entity. A1a keeps what spawning, saving,
 * reloading, death and revival need; kills, casts and the rest of 3.3 come with the systems that count them.
 */
public final class HeroState {
    public String id;
    /** The faction it serves; null while it is free (released, or never given). */
    public FactionType owner;
    public int baseId = -1;
    /**
     * Its entity's UUID. Kept after a release, so a stale copy of the released entity that loads later (its chunk was
     * not loaded at the release) is recognised and discarded.
     */
    public UUID uuid;
    public boolean alive;
    /** Game time a fallen hero returns at its home base; 0 when none is due. */
    public long reviveAt;
    public int level;
    public int xp;
    public final Map<Affix, Integer> affixes = new EnumMap<>(Affix.class);
    public int deaths;

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        if (owner != null) {
            t.putString("owner", owner.id);
        }
        t.putInt("base", baseId);
        if (uuid != null) {
            t.putUUID("uuid", uuid);
        }
        t.putBoolean("alive", alive);
        t.putLong("reviveAt", reviveAt);
        t.putInt("level", level);
        t.putInt("xp", xp);
        CompoundTag af = new CompoundTag();
        affixes.forEach((k, v) -> af.putInt(k.name(), v));
        t.put("affixes", af);
        t.putInt("deaths", deaths);
        return t;
    }

    public static HeroState load(CompoundTag t) {
        HeroState s = new HeroState();
        s.id = t.getString("id");
        if (s.id.isEmpty()) {
            return null;
        }
        s.owner = t.contains("owner") ? FactionType.byId(t.getString("owner")) : null;
        s.baseId = t.contains("base") ? t.getInt("base") : -1;
        s.uuid = t.hasUUID("uuid") ? t.getUUID("uuid") : null;
        s.alive = t.getBoolean("alive") && s.owner != null;
        s.reviveAt = t.getLong("reviveAt");
        s.level = t.getInt("level");
        s.xp = t.getInt("xp");
        CompoundTag af = t.getCompound("affixes");
        for (String k : af.getAllKeys()) {
            try {
                Affix a = Affix.valueOf(k);
                s.affixes.put(a, Math.min(a.maxStacks, af.getInt(k)));
            } catch (IllegalArgumentException ignored) {
                // a trait a later version removed
            }
        }
        s.deaths = t.getInt("deaths");
        return s;
    }
}
