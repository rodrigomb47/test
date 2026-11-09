package com.factionclash.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * The level depths of the old fixed hut mine (pure, unit-tested), kept only to convert mines saved by older
 * versions and to lay out the first steps of a hut. The old mine went down in fixed tiers: tier 1 six blocks below
 * the surface, then the ore bands of tiers 2-4, then a free floor every 6 blocks down to the mining floor, and once
 * the shaft was at the bottom, "infill" levels in the middle of every wide gap. This code is the old code, moved
 * here unchanged, so converted levels line up exactly with the tunnels the old miner dug. New mines choose their
 * layers with {@link MinePlanner}.
 */
public final class LegacyMine {
    /** The old level depths (absolute Y): tier 1 is 6 below the surface; then the vanilla ore bands. */
    public static final int[] TIER_TARGET_Y = {Integer.MIN_VALUE, 0, 32, 0, -48};
    public static final int MIN_Y = -56;

    private LegacyMine() {
    }

    /**
     * Number of spiral steps from the hut floor down to the landing of {@code level} (1-based; levels above 4 are
     * free extra floors 6 blocks apart). Landings always sit on a step whose cell has f = 2. Returns -1 when the
     * level would be below the world's mining floor.
     */
    public static int landingSteps(int surfaceY, int level) {
        if (level <= 0) {
            return 0;
        }
        int[] all = ALL_LANDINGS.computeIfAbsent(surfaceY, y -> {
            List<Integer> regular = regularLandings(y);
            List<Integer> list = new ArrayList<>(regular);
            list.addAll(infillLandings(regular));
            return list.stream().mapToInt(Integer::intValue).toArray();
        });
        return level <= all.length ? all[level - 1] : -1;
    }

    /** Landing steps of every level (regular then infill) by surface height: the same for every call, so cached. */
    private static final java.util.Map<Integer, int[]> ALL_LANDINGS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Landing steps of the levels dug downward: tiers 1-4, then a floor every 6 blocks down to the mining floor. */
    static List<Integer> regularLandings(int surfaceY) {
        List<Integer> out = new ArrayList<>();
        int prev = 0;
        for (int t = 1; t < 64; t++) {
            int target = t == 1 ? surfaceY - 6 : (t <= 4 ? TIER_TARGET_Y[t] : surfaceY - prev - 6);
            int n = SpiralMine.landingAtOrBelow(Math.max(prev + 6, surfaceY - target));
            if (surfaceY - n < MIN_Y) {
                break;
            }
            out.add(n);
            prev = n;
        }
        return out;
    }

    /**
     * Once the shaft is at the bottom, more branch mines fit between levels that are far apart: one in the middle
     * of every gap of 12 or more blocks, deepest first, until no such gap remains. The spiral already passes
     * these depths, so they need no digging down.
     */
    static List<Integer> infillLandings(List<Integer> regular) {
        List<Integer> all = new ArrayList<>(regular);
        List<Integer> added = new ArrayList<>();
        boolean changed = true;
        while (changed && added.size() < 32) {
            changed = false;
            all.sort(null);
            for (int i = all.size() - 1; i > 0; i--) {
                int upper = all.get(i - 1), lower = all.get(i);
                if (lower - upper < 12) {
                    continue;
                }
                int mid = SpiralMine.landingAtOrBelow((upper + lower) / 2);
                if (mid - upper >= 6 && lower - mid >= 6) {
                    all.add(mid);
                    added.add(mid);
                    changed = true;
                    break;
                }
            }
        }
        return added;
    }

    /** True if a level is dug downward (the spiral must be extended to reach it) rather than an infill level. */
    public static boolean isRegular(int surfaceY, int level) {
        return level <= regularLandings(surfaceY).size();
    }

    /** Number of regular levels (tiers 1-4 and the floors below them, not the infill levels in between). */
    public static int regularLevels(int surfaceY) {
        return regularLandings(surfaceY).size();
    }

    /** Absolute Y of a level's tunnels, or Integer.MIN_VALUE if the level does not exist. */
    public static int levelY(int surfaceY, int level) {
        int n = landingSteps(surfaceY, level);
        return n < 0 ? Integer.MIN_VALUE : surfaceY - n;
    }

    // ------------------------------------------------------------------ converting an old mine

    /** One converted level: its landing step, progress through its branch-mine plan and how often it was lengthened. */
    public record Level(int step, int progress, int rounds) {
    }

    /**
     * An old mine as a level list. {@code cur} is the index of the level being worked (-1 with no levels);
     * {@code upgradeStep} is the shaft-digging step when the old miner was digging down to {@code levels[cur]}, else -1.
     */
    public record Migrated(List<Level> levels, int bottom, int cur, int upgradeStep) {
    }

    /**
     * Converts the old fixed-tier state of a hut mine (no world access). The old mine had {@code tier + subLevel}
     * levels at the landings above, in that order; the old miner worked the deepest one, or level {@code revisit}
     * during a lengthening round. Rounds: every regular level was lengthened {@code reach} times, except that during
     * a round the levels above {@code revisit} were still one round short; infill levels were never lengthened. Only
     * the level being worked keeps its progress (longer plans start with the shorter ones, so it stays valid); the
     * miner walks quickly through the already-dug part of the others. A descent in progress becomes the next level,
     * appended as the current one: a deeper one keeps its shaft step (the old shaft plan ran from the deepest landing
     * to it, as the new one does from the bottom), and an infill level, which the shaft already passes, needs none.
     */
    public static Migrated migrate(int surfaceY, int tier, int subLevel, int progress, int reach, int revisit, int upgradeStep) {
        int n = tier + subLevel;
        List<Level> levels = new ArrayList<>();
        if (n <= 0) {
            return new Migrated(levels, 0, -1, -1);
        }
        int regular = regularLevels(surfaceY);
        int oc = revisit > 0 ? revisit : n;
        int bottom = 0;
        for (int i = 1; i <= n; i++) {
            int step = landingSteps(surfaceY, i);
            if (step < 0) {
                break;
            }
            int rounds;
            if (i > regular) {
                rounds = 0;
            } else if (revisit <= 0) {
                rounds = reach;
            } else {
                rounds = i >= revisit ? reach : Math.max(0, reach - 1);
            }
            levels.add(new Level(step, i == oc ? Math.max(0, progress) : 0, Math.max(0, rounds)));
            bottom = Math.max(bottom, step);
        }
        if (levels.isEmpty()) {
            return new Migrated(levels, 0, -1, -1);
        }
        int cur = Math.min(oc, levels.size()) - 1;
        int shaft = -1;
        if (upgradeStep >= 0 && revisit <= 0) {
            int next = landingSteps(surfaceY, n + 1);
            if (next > bottom) {
                levels.add(new Level(next, 0, 0));
                cur = levels.size() - 1;
                shaft = upgradeStep;
            } else if (next > 0) {
                levels.add(new Level(next, 0, 0));
                cur = levels.size() - 1;
            }
        }
        return new Migrated(levels, bottom, cur, shaft);
    }
}
