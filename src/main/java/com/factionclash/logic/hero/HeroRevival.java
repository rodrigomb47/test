package com.factionclash.logic.hero;

/**
 * When a fallen hero comes back (HEROES_V2 3.4, v1 25.4): {@code heroes.reviveSeconds} after its death, at its home
 * base. Pure, unit-tested ({@code HeroRevivalTest}).
 */
public final class HeroRevival {
    private HeroRevival() {
    }

    /** The game time a hero that fell at {@code now} returns (always later than {@code now}). */
    public static long reviveAt(long now, int reviveSeconds) {
        return now + Math.max(1, reviveSeconds) * 20L;
    }

    /** Whether a fallen hero is due. */
    public static boolean due(long now, long reviveAt) {
        return reviveAt > 0 && now >= reviveAt;
    }

    /** "returns in 10:00": the time left as minutes and seconds, rounded up to the next second. */
    public static String countdown(long ticksLeft) {
        long s = Math.max(0, (ticksLeft + 19) / 20);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }
}
