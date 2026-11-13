package com.factionclash.campaign;

import com.factionclash.faction.FactionType;
import com.factionclash.logic.Affix;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The campaign's record of one hero (HEROES_V2 3.3, v1 25.1): who it serves, its home base, the UUID of its one
 * living entity, whether it is alive and when a fallen hero returns, and the progress that outlives a death (level,
 * experience, traits). A hero id has at most one record and one living entity. A1b adds the counters
 * {@code sim.py}'s hero lines print (SLICE_PLAN 6.4: fights by who the enemy was, kills, deaths, revivals; casts,
 * staggers, escapes, last stands and phases stay 0 until the boss systems count them), where it last fell (its own
 * revival may raise it there) and its health when last seen (rival war councils weigh it, HEROES_V2 8.1).
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
    /** The role id it last had (a dormant hero returns as a plain soldier of it). */
    public String role;
    public final Map<Affix, Integer> affixes = new EnumMap<>(Affix.class);
    public int deaths;
    /** Times it came back after a fall. */
    public int revivals;
    /** Enemies it killed. */
    public int kills;
    /** Fights it began, by who the enemy was ({@code player}, {@code soldiers}, {@code hero}, {@code monsters}; SLICE_PLAN 6.4). */
    public final Map<String, Integer> fights = new TreeMap<>();
    /** Moves cast by id; staggers, escapes, last stands and the highest phase reached (the boss systems count them). */
    public final Map<String, Integer> casts = new TreeMap<>();
    public int staggers;
    public int escapes;
    public int lastStands;
    public int phaseReached;
    /** Where it last fell (a {@code revival.where: where_it_fell} hero rises there); null before a fall. */
    public BlockPos fellAt;
    /** Its health and maximum when last seen alive (the war councils of other factions read them, 8.1). */
    public float health;
    public float maxHealth;
    /**
     * Task A6b (HEROES_V2 6.13, MOBILITY_AND_WORK 7.7): the game time its last escape began (0: never), for the
     * 10-minute window and the stay away; it outlives a death and a restart.
     */
    public long lastEscapeAt;
    /**
     * The in-game day each winning side last took this hero's crest ({@code HeroRules.Side} key to day): one crest per
     * hero per winning side per day (6.13).
     */
    public final Map<String, Long> crests = new TreeMap<>();

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
        if (role != null) {
            t.putString("role", role);
        }
        t.putInt("revivals", revivals);
        t.putInt("kills", kills);
        t.put("fights", counts(fights));
        t.put("casts", counts(casts));
        t.putInt("staggers", staggers);
        t.putInt("escapes", escapes);
        t.putInt("lastStands", lastStands);
        t.putInt("phaseReached", phaseReached);
        if (fellAt != null) {
            t.putLong("fellAt", fellAt.asLong());
        }
        t.putFloat("health", health);
        t.putFloat("maxHealth", maxHealth);
        if (lastEscapeAt > 0) {
            t.putLong("lastEscapeAt", lastEscapeAt);
        }
        if (!crests.isEmpty()) {
            CompoundTag c = new CompoundTag();
            crests.forEach(c::putLong);
            t.put("crests", c);
        }
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
        s.role = t.contains("role") ? t.getString("role") : null;
        s.revivals = t.getInt("revivals");
        s.kills = t.getInt("kills");
        readCounts(t.getCompound("fights"), s.fights);
        readCounts(t.getCompound("casts"), s.casts);
        s.staggers = t.getInt("staggers");
        s.escapes = t.getInt("escapes");
        s.lastStands = t.getInt("lastStands");
        s.phaseReached = t.getInt("phaseReached");
        s.fellAt = t.contains("fellAt") ? BlockPos.of(t.getLong("fellAt")) : null;
        s.health = t.getFloat("health");
        s.maxHealth = t.getFloat("maxHealth");
        s.lastEscapeAt = t.getLong("lastEscapeAt");
        CompoundTag c = t.getCompound("crests");
        for (String k : c.getAllKeys()) {
            s.crests.put(k, c.getLong(k));
        }
        return s;
    }

    /** Adds one to a counter of {@link #fights} or {@link #casts}. */
    public static void count(Map<String, Integer> counter, String key) {
        counter.merge(key, 1, Integer::sum);
    }

    private static CompoundTag counts(Map<String, Integer> m) {
        CompoundTag c = new CompoundTag();
        m.forEach(c::putInt);
        return c;
    }

    private static void readCounts(CompoundTag c, Map<String, Integer> into) {
        into.clear();
        for (String k : c.getAllKeys()) {
            into.put(k, c.getInt(k));
        }
    }
}
