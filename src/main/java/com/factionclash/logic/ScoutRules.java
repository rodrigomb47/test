package com.factionclash.logic;

/** Pure rules for scouting rewards and overdue detection. PROPOSED values (see BALANCE.md). */
public final class ScoutRules {
    private ScoutRules() {
    }

    /** Experience for one delivered report: base value by kind plus 1 per 32 blocks from home. */
    public static int reportXp(int kindValue, double distanceFromHome) {
        return Math.max(0, kindValue) + (int) Math.floor(Math.max(0, distanceFromHome) / 32.0);
    }

    /** A scout is overdue once it has been away longer than the allowed expedition time. */
    public static boolean isOverdue(long departedAt, long now, long allowedTicks) {
        return departedAt > 0 && now - departedAt > allowedTicks;
    }
}
