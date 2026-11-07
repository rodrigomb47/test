package com.factionclash.logic;

/** Player experience levels, with the vanilla formulas (points needed per level: 7 + 2L, 37 + 5(L-15), 112 + 9(L-30)). */
public final class XpLevels {
    private XpLevels() {
    }

    /** Points needed to go from {@code level} to the next one. */
    public static int needForNext(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        if (level >= 15) {
            return 37 + (level - 15) * 5;
        }
        return 7 + level * 2;
    }

    /** Total points at the start of {@code level}. */
    public static long pointsFor(int level) {
        long p = 0;
        for (int l = 0; l < level; l++) {
            p += needForNext(l);
        }
        return p;
    }

    public static int levelFor(long points) {
        int level = 0;
        while (points >= needForNext(level)) {
            points -= needForNext(level);
            level++;
        }
        return level;
    }

    /** Points left after spending {@code cost} levels; like a player, the progress into the current level is kept. */
    public static long spend(long points, int cost) {
        int level = levelFor(points);
        if (cost >= level) {
            return 0;
        }
        double progress = (points - pointsFor(level)) / (double) needForNext(level);
        int after = level - cost;
        return pointsFor(after) + (long) Math.floor(progress * needForNext(after));
    }
}
