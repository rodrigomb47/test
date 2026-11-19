package com.factionclash.campaign;

import com.factionclash.logic.campaign.Whereabouts;
import com.factionclash.npc.NpcRole;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * The faction's roster entry for one NPC (identity survives unloads and reloads). It holds what the faction
 * <em>knows</em> ({@link #alive}, {@link #where} and its times: read by the faction's AI) and, apart, what
 * <em>happened</em> ({@link #dead}, {@link #vanished}, {@link #diedAt}, {@link #diedPos}, {@link #witnessed}: read only by
 * the engine and operators). {@code docs/design/missing_npcs.md}.
 */
public final class NpcRecord {
    public UUID uuid;
    public String name = "";
    public NpcRole role;
    public int baseId;
    /** Knowledge: the faction counts this member (here, away or missing; not presumed or known dead). */
    public boolean alive = true;
    /** Truth: where its entity last was, and when (written by the entity every 2 s). */
    public BlockPos lastPos = BlockPos.ZERO;
    public long lastSeen;
    /** Scouts: tick of departure on the current expedition (0 when home). */
    public long departedAt;
    public int level;
    /** The hero this record is (empty for an ordinary NPC). A hero counts as one soldier of its role. */
    public String heroId = "";

    /** Knowledge: the faction's view of where it is ({@link Whereabouts.State}). */
    public Whereabouts.State where = Whereabouts.State.HERE;
    /** Knowledge: when it was seen leaving the home area, and when it is expected back. */
    public long leftAt;
    public long expectedBy;
    /** Knowledge: when it went missing, was presumed dead or became known dead. */
    public long since;
    /** Knowledge: when it was last seen inside a home area of its faction. */
    public long seenHomeAt;

    /** Truth: its entity died (whatever the faction knows). */
    public boolean dead;
    /** Truth: its entity went from a loaded place without dying (the engine lost it). */
    public boolean vanished;
    /** Truth: when and where it died, and whether any member of its faction saw it. */
    public long diedAt;
    public BlockPos diedPos;
    public boolean witnessed;

    public boolean isHero() {
        return !heroId.isEmpty();
    }

    /** Truth: no living entity is behind this record any more (it died, or the engine lost it). */
    public boolean gone() {
        return dead || vanished;
    }

    /** Sets the faction's view; whether it counts follows the state. */
    public void where(Whereabouts.State s, long now) {
        if (s != where && s != Whereabouts.State.HERE && s != Whereabouts.State.AWAY) {
            since = now;
        }
        where = s;
        alive = s.counted();
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
        t.putString("where", where.name());
        t.putLong("left", leftAt);
        t.putLong("expected", expectedBy);
        t.putLong("since", since);
        t.putLong("home", seenHomeAt);
        t.putBoolean("dead", dead);
        t.putBoolean("vanished", vanished);
        t.putLong("diedAt", diedAt);
        if (diedPos != null) {
            t.putLong("diedPos", diedPos.asLong());
        }
        t.putBoolean("witnessed", witnessed);
        return t;
    }

    /**
     * An entry as saved. One saved before the whereabouts (2026-11-19) has none of their keys: alive was the truth then,
     * so a dead one is known dead, a scout that had set out is away (expected back by its departure plus
     * {@code scoutOverdueTicks}), and the rest are here.
     */
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
        if (t.contains("where")) {
            r.where = Whereabouts.State.byName(t.getString("where"), Whereabouts.legacy(r.alive, false));
            r.leftAt = t.getLong("left");
            r.expectedBy = t.getLong("expected");
            r.since = t.getLong("since");
            r.seenHomeAt = t.getLong("home");
            r.dead = t.getBoolean("dead");
            r.vanished = t.getBoolean("vanished");
            r.diedAt = t.getLong("diedAt");
            r.diedPos = t.contains("diedPos") ? BlockPos.of(t.getLong("diedPos")) : null;
            r.witnessed = t.getBoolean("witnessed");
        } else {
            boolean scoutAway = r.alive && r.role == NpcRole.SCOUT && r.departedAt > 0;
            r.where = Whereabouts.legacy(r.alive, scoutAway);
            r.dead = !r.alive;
            r.seenHomeAt = r.lastSeen;
            if (scoutAway) {
                r.leftAt = r.departedAt;
                r.expectedBy = r.departedAt + overdueTicks();
            }
            if (!r.alive) {
                r.since = r.lastSeen;
                r.diedAt = r.lastSeen;
                r.witnessed = true;
            }
        }
        r.alive = r.where.counted();
        return r;
    }

    /** {@code war.scoutOverdueTicks}, or its default before the config is loaded. */
    private static long overdueTicks() {
        try {
            return com.factionclash.config.FCConfig.SCOUT_OVERDUE_TICKS.get();
        } catch (IllegalStateException e) {
            return 24000;
        }
    }
}
