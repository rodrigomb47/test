package com.factionclash.logic.campaign;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A faction's sightings of enemy soldiers and heroes away from their base ({@code docs/design/recon.md} 5.5): seen at
 * our walls, met by a scout in the field, or seen by a recon scout on its watch. They feed the war council's reserve
 * ({@link Reserve}) only upward, and only on top of a fresh good recon: they never make an estimate. Pure rules, no
 * Minecraft classes: the ledger (the newest sighting per entity, a soldier's fall that sticks, ageing, the cap) and the
 * base a sighting counts for.
 *
 * <p>Task R10 made the ledger; task R11 writes it (at home, by scouts carrying it home, by the recon watch, and the falls
 * seen; {@code campaign/EnemySightings}) and lets the council's plan read it ({@code FactionState.sightings}).
 */
public final class Sightings {
    /** PROPOSED: the most sightings a faction keeps (several raid parties' worth), the oldest dropped first. */
    public static final int MAX = 64;
    /** PROPOSED: a sighting counts as long as a recon does, half a day (a hero seen falling: from its expected return). */
    public static final long FRESH_TICKS = ReconRules.FRESH_TICKS;
    /** A sighting with no base watched (one at home or in the field: counted for the nearest reported base, {@link #attribute}). */
    public static final int NO_BASE = -1;

    /** Who saw it. */
    public enum Source {
        /** A member NPC or hero at home: at our walls. */
        HOME,
        /** A scout on any trip, carried home and told at the station. */
        FIELD,
        /** A recon scout on its watch, told with its report (a soldier leaving, a patrol below the pillar, a hero in the yard). */
        WATCH
    }

    /**
     * One sighting: the entity's UUID (never the runtime id, which changes at a reload), its faction's id, a hero's id
     * ({@code ""} for a soldier), its visible power (a hero's war power at full strength), when and where it was seen, by
     * which source and by whom ({@code by}: a scout's name; "" at home), the base watched ({@link #NO_BASE} unless a watch's)
     * and whether it was seen to fall.
     */
    public record Sighting(UUID id, String faction, String hero, double power, long at, int x, int z, Source source, String by,
                           int base, boolean fallen) {
        public Sighting {
            faction = faction == null ? "" : faction;
            hero = hero == null ? "" : hero;
            by = by == null ? "" : by;
            power = Math.max(0, power);
            source = source == null ? Source.HOME : source;
        }

        /** Whether this is a hero's sighting (a hero is told apart by its id, a soldier only by its face). */
        public boolean isHero() {
            return !hero.isEmpty();
        }

        /** The same entity seen to fall at {@code when}. */
        public Sighting fell(long when) {
            return new Sighting(id, faction, hero, power, when, x, z, source, by, base, true);
        }
    }

    /**
     * The last tick a sighting counts at: half a day after it was seen. A hero seen to fall rises at home after the revival
     * every faction knows ({@code reviveTicks}, {@code heroes.reviveSeconds}; not a hero's own revival talent, which nobody
     * of ours can see), so its sighting counts from the fall until half a day after that return. A soldier seen to fall
     * counts nothing, but is kept as long as a sighting is, so a later sighting cannot make it count again.
     */
    public static long countsUntil(Sighting s, long reviveTicks) {
        long from = s.fallen() && s.isHero() ? s.at() + Math.max(0, reviveTicks) : s.at();
        return from + FRESH_TICKS;
    }

    /** Whether a sighting counts now: not a soldier seen to fall, and no older than {@link #countsUntil} (12000 in, 12001 out). */
    public static boolean counts(Sighting s, long now, long reviveTicks) {
        if (s.fallen() && !s.isHero()) {
            return false;
        }
        return now <= countsUntil(s, reviveTicks);
    }

    private final Map<UUID, Sighting> byId = new LinkedHashMap<>();

    /**
     * Files a sighting (or a fall): the newest per UUID is kept, except that a soldier seen to fall stays fallen (a later
     * sighting does not undo it; a hero's does, since a hero rises again). Past {@link #MAX} the oldest is dropped.
     */
    public void file(Sighting s) {
        if (s == null || s.id() == null) {
            return;
        }
        Sighting old = byId.get(s.id());
        if (old != null) {
            if (!s.isHero() && !old.isHero() && (old.fallen() || s.fallen())) {
                s = old.fallen() ? old : s; // a soldier's fall sticks
            } else if (old.at() > s.at()) {
                s = old; // an older sighting told late: the newer stays
            }
        }
        byId.put(s.id(), s);
        while (byId.size() > MAX) {
            Sighting oldest = null;
            for (Sighting e : byId.values()) {
                if (oldest == null || e.at() < oldest.at()) {
                    oldest = e;
                }
            }
            byId.remove(oldest.id());
        }
    }

    /** Files every sighting of {@code other} ({@link #file}): what a scout carried home joins its faction's ledger. */
    public void fileAll(Sightings other) {
        if (other != null) {
            for (Sighting s : List.copyOf(other.byId.values())) {
                file(s);
            }
        }
    }

    /** Forgets every sighting. */
    public void clear() {
        byId.clear();
    }

    /** A copy of this ledger (the same sightings, in the same order). */
    public Sightings copy() {
        Sightings c = new Sightings();
        c.byId.putAll(byId);
        return c;
    }

    /** Drops every sighting past {@link #countsUntil}. */
    public void prune(long now, long reviveTicks) {
        byId.values().removeIf(s -> now > countsUntil(s, reviveTicks));
    }

    /** The sighting of an entity, or null. */
    public Sighting get(UUID id) {
        return byId.get(id);
    }

    /** Every sighting kept, in the order filed (for saving). */
    public Collection<Sighting> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int size() {
        return byId.size();
    }

    /** A base of an enemy faction that this faction has reported (its key is the caller's), at {@code x}, {@code z}. */
    public record Base(int key, String faction, int x, int z) {
    }

    /**
     * The base a sighting counts for (recon.md 5.5): a watch's, the base watched; any other, its faction's reported base
     * nearest to where it was seen ({@code reported}: the faction's reported bases that stand), as {@code BaseWatch}
     * places losses today; {@link #NO_BASE} when the faction knows none of its bases.
     */
    public static int attribute(Sighting s, Collection<Base> reported) {
        if (s.base() != NO_BASE) {
            return s.base();
        }
        Base best = null;
        long bestD = Long.MAX_VALUE;
        for (Base b : reported) {
            if (!b.faction().equals(s.faction())) {
                continue;
            }
            long dx = (long) b.x() - s.x(), dz = (long) b.z() - s.z();
            long d = dx * dx + dz * dz;
            if (d < bestD || d == bestD && best != null && b.key() < best.key()) {
                best = b;
                bestD = d;
            }
        }
        return best == null ? NO_BASE : best.key();
    }

    /** The sightings that count for the base {@code key} ({@link #attribute}), counting or not: {@link Reserve#plan} filters. */
    public List<Sighting> forBase(int key, Collection<Base> reported) {
        List<Sighting> out = new ArrayList<>();
        for (Sighting s : byId.values()) {
            if (attribute(s, reported) == key) {
                out.add(s);
            }
        }
        return out;
    }
}
