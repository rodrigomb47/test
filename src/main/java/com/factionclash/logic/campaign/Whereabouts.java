package com.factionclash.logic.campaign;

/**
 * What a faction knows of where its members are and whether they live (the owner's request of 2026-11-13: deaths far
 * from home are not known at once; {@code docs/design/missing_npcs.md}). Pure rules: the states, the steps between
 * them, the trip plans, who knows a death at once, and the faction's chat lines. The game side is
 * {@code campaign/RollCall}.
 *
 * <p>A faction knows only what its people saw, and knowledge travels home with someone: a death inside a home area, or
 * seen by a member at home, is known at once; one seen by a member away is told when that member comes home; one nobody
 * saw is learned only by waiting: a member not back by its trip's expected return (plus a grace) is missing, and after
 * a further wait presumed dead and replaced. A presumed-dead member who comes home rejoins.
 */
public final class Whereabouts {
    private Whereabouts() {
    }

    /** The faction's view of one member. */
    public enum State {
        /** Last seen inside a home area of its faction. */
        HERE,
        /** Seen leaving; expected back by its trip's plan. */
        AWAY,
        /** Not back by its expected return plus the grace: not replaced yet, its workplace held. */
        MISSING,
        /** Missing for the presumed-dead wait: no longer counted, replaced; its record is kept so a return can rejoin. */
        PRESUMED_DEAD,
        /** A death the faction learned of. */
        KNOWN_DEAD;

        /** Whether the faction counts the member (needs, recruitment, workplaces, the garrison). */
        public boolean counted() {
            return this == HERE || this == AWAY || this == MISSING;
        }

        /** The state saved under {@code name}, or {@code fallback} when it is none of them. */
        public static State byName(String name, State fallback) {
            for (State s : values()) {
                if (s.name().equals(name)) {
                    return s;
                }
            }
            return fallback;
        }
    }

    /** What a step tells the faction (NONE and LEFT say nothing). */
    public enum Event {
        NONE, LEFT, BACK, REJOINED, MISSING, PRESUMED_DEAD,
        /** A death known at once (inside a home area, or seen by a member at home). */
        KILLED,
        /** A death a witness brought home. */
        KILLED_REPORTED,
        /** A death a witness brought home, of a member already presumed dead. */
        KILLED_CONFIRMED
    }

    /** The state after a step, and what it tells. */
    public record Step(State state, Event event) {
        public boolean changed(State before) {
            return state != before || event != Event.NONE;
        }
    }

    /** The waits: past the expected return plus {@code grace} a member is missing; missing for {@code presume}, presumed dead. */
    public record Waits(long grace, long presume) {
    }

    /** Who knows a death when it happens. */
    public enum Knowing {
        /** Inside a home area, or seen by a member at home. */
        AT_ONCE,
        /** Seen only by members away: they carry the news home. */
        CARRIED,
        /** Nobody saw it: the faction learns by waiting. */
        UNSEEN
    }

    public static final int TICKS_PER_DAY = 24000;
    /** PROPOSED: 10 ticks a block (0.1 blocks a tick), half a walking NPC's pace, for detours, stops and nights. */
    public static final double TICKS_PER_BLOCK = 10.0;
    /** PROPOSED: the plan of a trip with no plan of its own (a woodcutter foraging, a hero's duty): half a day. */
    public static final long DEFAULT_TRIP_TICKS = 12000;
    /** A raid's own bound: {@code FactionDirector} ends a raid this long after its launch. */
    public static final long RAID_LIMIT_TICKS = 72000;
    /** A trader's own bound: it gives a trip up after a day on the road ({@code TraderJob}). */
    public static final long TRADER_ROAD_TICKS = 24000;

    /** Ticks to walk {@code blocks} at {@link #TICKS_PER_BLOCK}. */
    public static long walkTicks(double blocks) {
        return (long) Math.ceil(Math.max(0, blocks) * TICKS_PER_BLOCK);
    }

    /**
     * A plan from now: what is left of a trip bounded at {@code boundAt} (never negative), plus the walk back over
     * {@code blocksBack}.
     */
    public static long planFrom(long now, long boundAt, double blocksBack) {
        return Math.max(0, boundAt - now) + walkTicks(blocksBack);
    }

    /** A scout's expedition: departure + the overdue bound (it turns home at half of it); no departure: the whole bound. */
    public static long scoutPlan(long now, long departedAt, long overdueTicks) {
        return departedAt <= 0 ? overdueTicks : Math.max(0, departedAt + overdueTicks - now);
    }

    /** A raider: launch + the raid's bound, plus the walk back from the target. */
    public static long raidPlan(long now, long raidStartedAt, double blocksBack) {
        return planFrom(now, raidStartedAt + RAID_LIMIT_TICKS, blocksBack);
    }

    /** The director's check of an away or missing member. */
    public static Step waits(State s, long now, long expectedBy, long since, Waits w) {
        if (s == State.AWAY && now > expectedBy + w.grace()) {
            return new Step(State.MISSING, Event.MISSING);
        }
        if (s == State.MISSING && now - since >= w.presume()) {
            return new Step(State.PRESUMED_DEAD, Event.PRESUMED_DEAD);
        }
        return new Step(s, Event.NONE);
    }

    /**
     * Seen alive inside a home area: here again. A missing member is back; one presumed (or, by some mishap, believed)
     * dead rejoins.
     */
    public static Step seenHome(State s) {
        return switch (s) {
            case MISSING -> new Step(State.HERE, Event.BACK);
            case PRESUMED_DEAD, KNOWN_DEAD -> new Step(State.HERE, Event.REJOINED);
            default -> new Step(State.HERE, Event.NONE);
        };
    }

    /** Seen outside every home area: a member last seen at home has left (its trip's plan is set then). */
    public static Step seenAway(State s) {
        return s == State.HERE ? new Step(State.AWAY, Event.LEFT) : new Step(s, Event.NONE);
    }

    /**
     * Who knows a death: everyone at once when far deaths are known ({@code farDeathsUnknown} false), when it fell inside
     * a home area, or when a member at home saw it; else the members away who saw it carry it; else nobody.
     */
    public static Knowing atDeath(boolean farDeathsUnknown, boolean inHomeArea, int witnessesAtHome, int witnessesAway) {
        if (!farDeathsUnknown || inHomeArea || witnessesAtHome > 0) {
            return Knowing.AT_ONCE;
        }
        return witnessesAway > 0 ? Knowing.CARRIED : Knowing.UNSEEN;
    }

    /** The faction learns of a death, at once or by news; a death already known tells nothing. */
    public static Step learnsDeath(State s, boolean byNews) {
        if (s == State.KNOWN_DEAD) {
            return new Step(s, Event.NONE);
        }
        Event e = !byNews ? Event.KILLED : s == State.PRESUMED_DEAD ? Event.KILLED_CONFIRMED : Event.KILLED_REPORTED;
        return new Step(State.KNOWN_DEAD, e);
    }

    /**
     * An old record (saved before the whereabouts): alive is here, or away for a scout that had set out (expected back
     * by its departure plus the overdue bound); not alive is known dead.
     */
    public static State legacy(boolean alive, boolean scoutAway) {
        return !alive ? State.KNOWN_DEAD : scoutAway ? State.AWAY : State.HERE;
    }

    /** The day a tick falls on, from 1. */
    public static int day(long t) {
        return (int) Math.floorDiv(Math.max(0, t), TICKS_PER_DAY) + 1;
    }

    /**
     * The faction's chat line for an event, or null when it says nothing. {@code who} is "Name (Role)" (a hero: its name);
     * {@code carrier} the one who brought the news; {@code x}, {@code z} and {@code when} where and when it died;
     * {@code expectedBy} and {@code since} the record's; {@code hero} words a hero's fall as "has fallen".
     */
    public static String line(Event e, String who, String carrier, int x, int z, long when, long expectedBy, long since,
                              boolean hero) {
        String died = hero ? " has fallen" : " was killed";
        return switch (e) {
            case NONE, LEFT -> null;
            case KILLED -> who + died;
            case KILLED_REPORTED -> carrier + " brings news: " + who + died + " near " + x + ", " + z + " on day " + day(when);
            case KILLED_CONFIRMED -> carrier + " brings news: " + who + ", presumed dead," + (hero ? " fell" : " was killed")
                    + " near " + x + ", " + z + " on day " + day(when);
            case MISSING -> who + " is missing: expected back by day " + day(expectedBy);
            case PRESUMED_DEAD -> who + " is presumed dead (missing since day " + day(since) + ")";
            case BACK -> who + " is back";
            case REJOINED -> who + ", presumed dead, has come back and rejoins";
        };
    }

    /** A line someone carried home (a raid's end, an enemy hero's fall): "W brings news: ...". */
    public static String carried(String carrier, String line) {
        return carrier + " brings news: " + line;
    }

    /** A hero whose fall nobody saw, rising at home: how its faction learns of it. */
    public static String unseenFall(String hero, String base) {
        return hero + " fell where none of the faction saw it and returns to " + base;
    }
}
