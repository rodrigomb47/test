package com.factionclash.logic.campaign;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What a scout keeps over one watch of a base (recon.md 3.6): every sample it takes from its vantage (one every 40 ticks)
 * is added here, and at the watch's end this becomes its focused report ({@link #recon}). Pure, no Minecraft classes; the
 * game side takes the samples ({@code campaign/BaseWatch.reconSample}) and saves the watch with the scout's job.
 *
 * <p>It keeps the union of the yard points seen (a mask of {@link ReconRules#POINTS} bits, so a point seen once counts), the
 * distinct soldiers seen, by UUID, each with its visible power at its last sighting, the heroes seen, by hero id, with
 * their war power at full strength, and the watch's first and last sample times. Soldiers are kept by UUID, not by the
 * runtime entity id, which changes when a chunk reloads: a watch saved and loaded midway still counts each soldier once
 * (recon.md 15, item 4). This models a player who watches a yard for a minute and keeps track of each soldier by where it
 * walks and what it wears.
 */
public final class ReconWatch {
    /** The grid's side: {@link ReconRules#POINTS} = 5 x 5 points. */
    public static final int GRID = 5;
    /** The yard's middle point's bit in a mask: the grid point (2, 2), point index x * {@link #GRID} + z. */
    public static final int MIDDLE = (GRID / 2) * GRID + GRID / 2;
    /** Every point's bit. */
    public static final int ALL_POINTS = (1 << ReconRules.POINTS) - 1;

    /**
     * One look from the vantage: the yard points in view ({@code mask}, bit x * {@link #GRID} + z of the 5 x 5 grid), the
     * soldiers in sight by UUID with their visible power (a lair: its monsters, power 1 each), the heroes in sight by hero
     * id with their war power at full strength, and when it was taken.
     */
    public record Sample(int mask, Map<UUID, Double> soldiers, Map<String, Double> heroes, long at) {
        public Sample {
            mask &= ALL_POINTS;
            soldiers = soldiers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(soldiers));
            heroes = heroes == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(heroes));
        }

        /** Whether the yard's middle was in view. */
        public boolean middle() {
            return (mask & (1 << MIDDLE)) != 0;
        }

        /** The yard points in view, of {@link ReconRules#POINTS}. */
        public int points() {
            return Integer.bitCount(mask);
        }
    }

    private int mask;
    private final Map<UUID, Double> soldiers = new LinkedHashMap<>();
    private final Map<String, Double> heroes = new LinkedHashMap<>();
    private long first = -1;
    private long last = -1;

    /** An empty watch: nothing sampled yet. */
    public ReconWatch() {
    }

    /**
     * A watch as it was saved midway ({@link #mask()}, {@link #soldiers()}, {@link #heroes()}, {@link #first()},
     * {@link #last()}): later samples go on counting the same soldiers once.
     */
    public static ReconWatch restore(int mask, Map<UUID, Double> soldiers, Map<String, Double> heroes, long first, long last) {
        ReconWatch w = new ReconWatch();
        w.mask = mask & ALL_POINTS;
        if (soldiers != null) {
            w.soldiers.putAll(soldiers);
        }
        if (heroes != null) {
            w.heroes.putAll(heroes);
        }
        w.first = first;
        w.last = last;
        return w;
    }

    /**
     * Adds one sample: its points join the union, each soldier counts once (its power is the one of its newest sighting),
     * each hero once (likewise), and the watch runs from its first sample to its newest. A null sample (the base out of
     * sight, beyond {@link ReconRules#SIGHT}) changes nothing.
     */
    public void add(Sample s) {
        if (s == null) {
            return;
        }
        mask |= s.mask();
        soldiers.putAll(s.soldiers());
        heroes.putAll(s.heroes());
        if (first < 0 || s.at() < first) {
            first = s.at();
        }
        last = Math.max(last, s.at());
    }

    /** Whether no sample was added yet. */
    public boolean empty() {
        return first < 0;
    }

    /** The union of the yard points seen. */
    public int mask() {
        return mask;
    }

    /** Whether the yard's middle was seen at least once. */
    public boolean middle() {
        return (mask & (1 << MIDDLE)) != 0;
    }

    /** The yard points seen at least once, of {@link ReconRules#POINTS}. */
    public int points() {
        return Integer.bitCount(mask);
    }

    /** The distinct soldiers seen, by UUID, with the visible power of each at its newest sighting (insertion order). */
    public Map<UUID, Double> soldiers() {
        return Collections.unmodifiableMap(soldiers);
    }

    /** The heroes seen, by hero id, with their war power at full strength. */
    public Map<String, Double> heroes() {
        return Collections.unmodifiableMap(heroes);
    }

    /** The first sample's time (-1: none yet). */
    public long first() {
        return first;
    }

    /** The newest sample's time (-1: none yet). */
    public long last() {
        return last;
    }

    /** How long it has watched: from its first sample to its newest (0 with none or one). */
    public long watchTicks() {
        return first < 0 ? 0 : last - first;
    }

    /** The mean visible power of the distinct soldiers seen (0 when none was seen). */
    public double power() {
        if (soldiers.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (double p : soldiers.values()) {
            sum += Math.max(0, p);
        }
        return sum / soldiers.size();
    }

    /**
     * The focused report of this watch (recon.md 4): the soldiers counted once each, by face, their mean power, the
     * heroes seen, the share of the yard's points seen at least once, the middle, how long it watched, its last sample's
     * time, the vantage and the scout's name. Whether it is good or blind is {@link Intel.Recon#good}'s to say. Watched
     * with the eyes.
     */
    public Intel.Recon recon(String vantage, String by) {
        return recon(vantage, by, ReconOptic.NONE.id());
    }

    /** {@link #recon(String, String)} watched through the instrument {@code optic} names (task R12: "spyglass"). */
    public Intel.Recon recon(String vantage, String by, String optic) {
        return new Intel.Recon(soldiers.size(), power(), heroes, Intel.coverage(points()), middle(), watchTicks(),
                Math.max(0, last), vantage, by, soldiers.keySet(), optic);
    }
}
