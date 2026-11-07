package com.factionclash.logic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Experience curve and affix rolling. Affixes are rolled once per level gained and persisted. */
public final class AffixRoller {
    private AffixRoller() {
    }

    /** PROPOSED curve: reaching level n+1 from n costs 40 + 30n experience. */
    public static int xpToNext(int level) {
        return 40 + 30 * Math.max(0, level);
    }

    public static final int MAX_LEVEL = 30;

    public record LevelResult(int level, int xp, int levelsGained) {
    }

    /** Adds experience and reports how many levels were gained (each grants exactly one affix roll). */
    public static LevelResult addXp(int level, int xp, int gained) {
        if (gained <= 0 || level >= MAX_LEVEL) {
            return new LevelResult(level, level >= MAX_LEVEL ? 0 : xp, 0);
        }
        int lv = level;
        int cur = xp + gained;
        int levels = 0;
        while (lv < MAX_LEVEL && cur >= xpToNext(lv)) {
            cur -= xpToNext(lv);
            lv++;
            levels++;
        }
        if (lv >= MAX_LEVEL) {
            cur = 0;
        }
        return new LevelResult(lv, cur, levels);
    }

    /**
     * Rolls one affix the NPC can still gain. Job-specific affixes for its job are weighted double.
     * Returns null when every applicable affix is already capped (no stat grows further).
     */
    public static Affix roll(Affix.Job job, Map<Affix, Integer> current, RandomGenerator random) {
        List<Affix> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (Affix a : Affix.values()) {
            if (!a.appliesTo(job)) {
                continue;
            }
            if (current.getOrDefault(a, 0) >= a.maxStacks) {
                continue;
            }
            int w = a.isGeneral() ? 1 : 2;
            pool.add(a);
            weights.add(w);
            total += w;
        }
        if (pool.isEmpty()) {
            return null;
        }
        int r = random.nextInt(total);
        for (int i = 0; i < pool.size(); i++) {
            r -= weights.get(i);
            if (r < 0) {
                return pool.get(i);
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** Applies a roll to a copy of the map, never exceeding caps. */
    public static Map<Affix, Integer> apply(Map<Affix, Integer> current, Affix affix) {
        Map<Affix, Integer> out = new EnumMap<>(Affix.class);
        out.putAll(current);
        if (affix != null) {
            out.merge(affix, 1, (a, b) -> Math.min(affix.maxStacks, a + b));
        }
        return out;
    }

    public static double total(Map<Affix, Integer> current, Affix affix) {
        return Math.min(current.getOrDefault(affix, 0), affix.maxStacks) * affix.perStack;
    }
}
