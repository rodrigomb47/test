package com.factionclash.logic.campaign;

/**
 * A faction's fall (the owner's rule of 2026-11-25: "if a faction gets eliminated, all of their members dies"). When its
 * last base falls, every member dies with it, wherever it is: the NPCs and heroes in loaded places at once, a member away
 * in an unloaded place when it next loads, a member player at once (or at its next login). Its heroes do not come back:
 * each becomes a free hero with its level kept. Pure rules, no Minecraft classes.
 */
public final class Elimination {
    private Elimination() {
    }

    /**
     * Whether a faction has fallen in this campaign: not alive, with the time of its elimination recorded. A faction that
     * was never eliminated has no such time (0), whatever its {@code alive} flag says: a faction with no campaign state, or
     * an old save's faction whose flag was never written, is not a fallen one, so its members (a GameTest's NPCs among
     * them) never die for it.
     */
    public static boolean fallen(boolean alive, long eliminatedAt) {
        return !alive && eliminatedAt > 0;
    }

    /** The time recorded as a faction's elimination: the game time, never 0 (0 means "never eliminated"). */
    public static long stamp(long gameTime) {
        return Math.max(1, gameTime);
    }

    /** What becomes of a hero of a faction that falls. */
    public enum HeroFate {
        /** Alive and loaded: it is killed where it stands; its own death is told by the kill feed and frees it. */
        DIES_THERE,
        /** Alive in an unloaded place: it dies with its faction unseen; one kill-feed line now, and it is free. */
        TOLD_AND_FREED,
        /** Already dead, waiting for its revival: it never comes back (no base to rise at); free, nothing more told. */
        FREED
    }

    /** {@code alive}: the campaign's record of the hero; {@code loaded}: a living entity of it is in a loaded place. */
    public static HeroFate heroFate(boolean alive, boolean loaded) {
        if (!alive) {
            return HeroFate.FREED;
        }
        return loaded ? HeroFate.DIES_THERE : HeroFate.TOLD_AND_FREED;
    }
}
