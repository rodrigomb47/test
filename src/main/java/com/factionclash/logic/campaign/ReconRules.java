package com.factionclash.logic.campaign;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Focused reconnaissance (the owner's rule of 2026-11-25: "Only estimate enemy defence when the scout or other related role
 * makes a report of what he saw while focused on this particilar task"; {@code docs/design/recon.md}). A war council
 * judges an enemy base only by a scout it ordered to watch that one base: the scout walks there, climbs a dirt pillar
 * outside the guards' reach, watches the yard for a minute, takes the pillar down and carries what it saw home. Pure rules,
 * no Minecraft classes: when the council orders a recon and which target first, the back-off, how an order ends, the
 * vantage band and the side order, the climb, the hold on the pillar, and which targets the council may judge inside its
 * raid interval. The report itself is {@link Intel.Recon}; the plan built on it is {@link Reserve}.
 *
 * <p>Task R1, additive: R3 places orders by them, R4a's column search ({@code work.ReconVantage}) reads the side order,
 * the band and the columns' order ({@link Band} and {@link #GROUND_ORDER} came with it), R4b runs the scout and R5
 * switched the council to them ({@link #candidate}, {@link #answersStrike}, {@link #verdict}).
 */
public final class ReconRules {
    private ReconRules() {
    }

    // ---- The look (recon.md 3.4) ----

    /** The yard points a look samples ({@code BaseWatch}'s 5 x 5 grid). */
    public static final int POINTS = 25;
    /** PROPOSED: a good look has at least this many of the {@link #POINTS} in view (32%), and the middle. */
    public static final int MIN_POINTS = 8;
    /** PROPOSED: the planned watch, a minute: from where the scout stops it counts 99.4% of a guard garrison. */
    public static final long WATCH_TICKS = 1200;
    /** PROPOSED: a watch cut short is good only past this (95% counted on average, the worst vantage 80%). */
    public static final long MIN_WATCH_TICKS = 600;
    /** PROPOSED: a report is fresh for half a day; a council never launches on an older one. */
    public static final long FRESH_TICKS = 12000;

    // ---- Orders (recon.md 2) ----

    /** PROPOSED: the council orders a recon this long before its raid interval ends (equal to {@link #FRESH_TICKS}). */
    public static final long LEAD_TICKS = 12000;
    /** PROPOSED: a target backs off this long after a failed attempt, doubling with each one in a row... */
    public static final long RETRY_TICKS = 12000;
    /** ...to this at most. */
    public static final long RETRY_MAX_TICKS = 48000;
    /** PROPOSED: an order no scout took this long asks for a second scout (the owner agreed, 2026-11-25). */
    public static final long WAIT_TICKS = 6000;
    /** PROPOSED: the second-scout need stops at this many scouts a base. */
    public static final int MAX_SCOUTS = 2;
    /** At most one ordinary and one urgent order open per faction. */
    public static final int ORDERS_MAX = 2;
    /**
     * An urgent order waits until the base's alarm has been quiet this long (the fight is over, so the scout does not walk
     * out through the attackers): the alarm's own memory ({@code FactionDirector.NOTICE_MEMORY}).
     */
    public static final long QUIET_TICKS = 600;

    // ---- The trip (recon.md 2.5 and 3.5) ----

    /**
     * PROPOSED: the trip plan's allowance at the target: two sides (a climb and a descent of up to 20 blocks each, about
     * 660 ticks), the walk round between them (about 200 blocks, 2000 ticks at the plan's pace) and a hold of up to
     * {@link #HOLD_MAX_TICKS}.
     */
    public static final long VANTAGE_TICKS = 4800;
    /** PROPOSED: the nearest a settlement's vantage comes to its centre (Chebyshev): no yard place within a guard's 48 sees it. */
    public static final int STANDOFF = 61;
    /** PROPOSED: the focused look's reach (horizontal, from the centre); a passing look keeps 64. */
    public static final int SIGHT = 72;
    /** PROPOSED: a lair's vantage, on the ground: the leash (10) + a zombie's follow range (35) + 1... */
    public static final int LAIR_STANDOFF = 46;
    /** ...to the passing look's 64. */
    public static final int LAIR_SIGHT = 64;
    /** A settlement's vantage columns lie up to this far to either side of the side's axis. */
    public static final int LATERAL = 8;
    /** PROPOSED: the tallest pillar (12 to 14 on a gate side's axis, 17 to 21 on the other sides of a grown base). */
    public static final int PILLAR_MAX = 20;
    /** PROPOSED: it climbs this many blocks past the first good look (the worst vantage then counts 95%, not 89%). */
    public static final int CLIMB_PAST = 2;
    /** It looks from the ground at the foot, then after each block from this height up. */
    public static final int LOOK_FROM = 4;
    /** PROPOSED: dirt it carries: {@link #PILLAR_MAX} + the woodcutter's 2 spare ({@code TreeShape.PILLAR_SPARE}). */
    public static final int BLOCKS = 22;
    /** PROPOSED: sides tried a trip: a blind side, then another. */
    public static final int SIDES = 2;
    /** PROPOSED: an enemy in sight this close keeps the scout up on its pillar (it does not start down into it). */
    public static final int THREAT_RANGE = 16;
    /** PROPOSED: the longest it waits on the pillar after its watch for an enemy to leave (inside {@link #VANTAGE_TICKS}). */
    public static final long HOLD_MAX_TICKS = 1200;

    // ---- When the council orders a recon (recon.md 2.1) ----

    /**
     * When the raid interval ends: the last raid (or, before any raid, the campaign's start: the first war's gate) plus the
     * interval.
     */
    public static long nextRaidAt(long lastRaidAt, long startedAt, long interval) {
        return Math.max(lastRaidAt, startedAt) + interval;
    }

    /** Whether ordinary orders may be placed: the interval is nearly ripe ({@link #LEAD_TICKS} before it ends, or later). */
    public static boolean inWindow(long now, long nextRaidAt) {
        return now >= nextRaidAt - LEAD_TICKS;
    }

    /**
     * Whether the council places orders at all this pass: never while its own raid is out (or it still waits for news of
     * its end), never when it could not send a party, and only in the window or when an urgent recon is due.
     */
    public static boolean ordersNow(boolean raidOut, int party, boolean inWindow, boolean urgentDue) {
        return !raidOut && party > 0 && (inWindow || urgentDue);
    }

    /** The counter-attack window and the urgent orders' spacing: a quarter of the raid interval. */
    public static long urgentGap(long interval) {
        return interval / 4;
    }

    /** Whether a faction struck us since our last raid ({@code struckAt}: its soldiers seen at our walls; 0: never). */
    public static boolean struckSince(long struckAt, long lastRaidAt) {
        return struckAt > 0 && struckAt > lastRaidAt;
    }

    /**
     * Whether the raid interval is over at {@code now} (the council raids at the interval): past the first war's gate
     * ({@code startedAt} plus the interval) and an interval since our last raid ({@link #nextRaidAt}).
     */
    public static boolean intervalOver(long now, long lastRaidAt, long startedAt, long interval) {
        return now >= nextRaidAt(lastRaidAt, startedAt, interval);
    }

    /**
     * Whether the counter-attack window is open at {@code now} (recon.md 6): inside the raid interval since our last raid,
     * past its first quarter ({@link #urgentGap}), and past the first war's gate ({@code startedAt} plus the interval). The
     * war council's gate and the urgent order's read this one rule. Before our first raid it is never open: the first
     * war's gate ends the interval too, and the council raids there as at any interval's end (the review of the focused
     * reconnaissance round, finding 3: an urgent recon ordered before it was spent for nothing).
     */
    public static boolean counterWindow(long now, long lastRaidAt, long startedAt, long interval) {
        return now - lastRaidAt >= urgentGap(interval) && now - startedAt >= interval && !intervalOver(now, lastRaidAt, startedAt, interval);
    }

    /**
     * Whether an urgent recon of a striker's nearest reported base is due (recon.md 2.1 and 6): it struck us since our last
     * raid, the counter-attack window is open ({@link #counterWindow}: never before our first raid), our alarm has been
     * quiet {@link #QUIET_TICKS} ({@code alarmAt}: when it last noticed an intruder; 0: never), no urgent order went to
     * that faction within a quarter of the interval ({@code lastUrgentAt}; 0: never), and no good recon of that base was
     * made since the strike ({@code goodReconAt}: the kept good recon's time; 0: none). A strike seen while our own raid
     * was out is acted on after the raid's end, since {@link #ordersNow} places nothing while it is out. Outside the
     * window the striker's base is watched by the ordinary orders of the window before the interval ends.
     */
    public static boolean urgentDue(long now, long struckAt, long lastRaidAt, long startedAt, long interval, long alarmAt,
                                    long lastUrgentAt, long goodReconAt) {
        long gap = urgentGap(interval);
        return struckSince(struckAt, lastRaidAt) && counterWindow(now, lastRaidAt, startedAt, interval) && now - alarmAt >= QUIET_TICKS
                && (lastUrgentAt <= 0 || now - lastUrgentAt >= gap) && goodReconAt <= struckAt;
    }

    /** Whether a base's kept recon calls for a new one: none, or stale (older than {@link #FRESH_TICKS}). */
    public static boolean needsRecon(Intel.Recon kept, long now) {
        return kept == null || now - kept.at() > FRESH_TICKS;
    }

    /** How long a target backs off after {@code failures} failed attempts in a row: 0, 12000, 24000, then 48000 at most. */
    public static long backoffTicks(int failures) {
        if (failures <= 0) {
            return 0;
        }
        return failures > 3 ? RETRY_MAX_TICKS : Math.min(RETRY_MAX_TICKS, RETRY_TICKS << (failures - 1));
    }

    /** Whether a target is backing off now: its last failure is younger than its back-off. */
    public static boolean backingOff(Intel.ReconFile file, long now) {
        return file != null && file.failures() > 0 && now < file.failedAt() + backoffTicks(file.failures());
    }

    /**
     * Whether a target qualifies for an ordinary order: it stands, no order on it is open, it is not backing off, and it has
     * no fresh good recon (never judged, stale, or every attempt since the last good one blind).
     */
    public static boolean ordinaryQualifies(Intel.ReconFile file, long now, boolean ordered) {
        Intel.ReconFile f = file == null ? Intel.ReconFile.NONE : file;
        return !f.fallen() && !ordered && !backingOff(f, now) && needsRecon(f.recon(), now);
    }

    /**
     * Whether a striker's nearest base qualifies for an urgent order ({@link #urgentDue} holds for its faction): it stands,
     * no order on it is open, it is not backing off, and no good recon of it was made since the strike (a fresh one from
     * before the strike does not stop it: the strikers have left their walls since).
     */
    public static boolean urgentQualifies(Intel.ReconFile file, long now, boolean ordered, long struckAt) {
        Intel.ReconFile f = file == null ? Intel.ReconFile.NONE : file;
        return !f.fallen() && !ordered && !backingOff(f, now) && (f.recon() == null || f.recon().at() <= struckAt);
    }

    /**
     * A base the council could order a recon of: its key (the caller's), whether it qualifies for an urgent order and for
     * an ordinary one (in the window), whether it was ever judged (has a good recon, fresh or not), the defence its last
     * good recon showed (in soldiers; read only to choose which base to watch first, never to launch) and its distance from
     * our base.
     */
    public record Target(int key, boolean urgent, boolean ordinary, boolean judged, double lastDefence, double distance) {
    }

    /** An order to place: on which target, and whether it is the urgent one. */
    public record Placement(Target target, boolean urgent) {
    }

    /** The ordinary orders' priority: a target never judged first, nearest first; then a stale one, the weakest first, then the nearest. */
    static final Comparator<Target> ORDINARY = Comparator.comparing((Target t) -> t.judged())
            .thenComparingDouble(t -> t.judged() ? t.lastDefence() : 0)
            .thenComparingDouble(Target::distance)
            .thenComparingInt(Target::key);

    /**
     * The next order to place (one at a time, recon.md 2.1), or null: an urgent target first, the nearest, while no urgent
     * order is open; else an ordinary one by {@link #ORDINARY} while no ordinary order is open. Lairs and settlements are in
     * one list.
     */
    public static Placement next(Collection<Target> targets, boolean urgentOpen, boolean ordinaryOpen) {
        if (!urgentOpen) {
            Target best = targets.stream().filter(Target::urgent)
                    .min(Comparator.comparingDouble(Target::distance).thenComparingInt(Target::key)).orElse(null);
            if (best != null) {
                return new Placement(best, true);
            }
        }
        if (!ordinaryOpen) {
            Target best = targets.stream().filter(Target::ordinary).min(ORDINARY).orElse(null);
            if (best != null) {
                return new Placement(best, false);
            }
        }
        return null;
    }

    /** What an order's scout brought to the station about its target, if it reported. */
    public enum ScoutReport {
        /** It has not reported since it took the order. */
        NONE,
        /** A good recon of the target. */
        GOOD,
        /** A blind recon ("could not see inside"). */
        BLIND,
        /** No recon of it (it retreated, or found no vantage within reach). */
        EMPTY
    }

    /** How an order stands after a pass (recon.md 2.1's endings). */
    public enum Ending {
        /** Still open. */
        OPEN,
        /** Done: its scout reported a good recon. */
        DONE,
        /** A failed attempt: a blind recon, already counted in the back-off when it was merged ({@link Intel#mergeRecon}). */
        BLIND,
        /** A failed attempt: its scout came home with no recon of the target. */
        EMPTY_HANDED,
        /** A failed attempt: its scout is missing, or known or presumed dead. */
        LOST,
        /** Its scout is no longer a scout: the order is open again for another, and it is not a failure. */
        REOPENED,
        /** Dropped: untaken while the target turned fresh by other means or was seen in ruins, or untaken for half a day. */
        DROPPED;

        /** Whether the order ends now. */
        public boolean ends() {
            return this != OPEN && this != REOPENED;
        }

        /**
         * Whether the caller must count a failed attempt for this ending ({@link Intel#reconFailed}): an empty-handed or
         * lost scout. A blind recon counted when it was merged, so it is not counted again.
         */
        public boolean countsFailure() {
            return this == EMPTY_HANDED || this == LOST;
        }
    }

    /**
     * How an order ends (recon.md 2.1). Taken ({@code taken}): by what its scout reported ({@code report}), else when its
     * scout is missing, presumed or known dead ({@code scout}: its roll-call state; null when the faction has no record of
     * it), else reopened when it is no longer a scout. Untaken: dropped when the target has a good recon made since the order
     * ({@code file}), was seen in ruins, or the order waited {@link #FRESH_TICKS}. A taken order whose target turns to ruins
     * stays open until its scout reports or is lost (the table drops only untaken orders).
     */
    public static Ending settle(boolean taken, ScoutReport report, Whereabouts.State scout, boolean stillScout, long orderedAt,
                                long now, Intel.ReconFile file) {
        Intel.ReconFile f = file == null ? Intel.ReconFile.NONE : file;
        if (taken) {
            switch (report == null ? ScoutReport.NONE : report) {
                case GOOD:
                    return Ending.DONE;
                case BLIND:
                    return Ending.BLIND;
                case EMPTY:
                    return Ending.EMPTY_HANDED;
                default:
                    break;
            }
            if (scout == null || scout == Whereabouts.State.MISSING || !scout.counted()) {
                return Ending.LOST;
            }
            return stillScout ? Ending.OPEN : Ending.REOPENED;
        }
        if (f.fallen() || f.recon() != null && f.recon().at() > orderedAt || now - orderedAt >= FRESH_TICKS) {
            return Ending.DROPPED;
        }
        return Ending.OPEN;
    }

    /**
     * Whether an order nobody took asks for a second scout (recon.md 2.4): it has waited {@link #WAIT_TICKS} and the base
     * has fewer than {@link #MAX_SCOUTS} scouts (with none, the "no scout left" need hires one anyway).
     */
    public static boolean secondScoutNeeded(boolean taken, long orderedAt, long now, int scouts) {
        return !taken && now - orderedAt >= WAIT_TICKS && scouts < MAX_SCOUTS;
    }

    // ---- The vantage (recon.md 3.5 and 3.7) ----

    /** The four sides of a base, each with its outward direction (north is -z, as in the game). */
    public enum Side {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        /** Every side's bit. */
        public static final int ALL = 15;

        public final int dx;
        public final int dz;

        Side(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        /** This side's bit in a mask of sides. */
        public int bit() {
            return 1 << ordinal();
        }

        /** Whether {@code mask} holds this side. */
        public boolean in(int mask) {
            return (mask & bit()) != 0;
        }
    }

    /**
     * The order in which a scout tries a base's sides (recon.md 3.5): the gate side facing home first, then the other gate
     * side, then the side facing home, then the last. {@code toHomeX} and {@code toHomeZ} point from the target's centre to
     * our base; {@code gates} is the mask of sides with a gate (a settlement's west and east; every side of a lair, whose
     * four walls have gaps). Ties (home straight along an axis) go in the order north, east, south, west.
     */
    public static List<Side> sideOrder(int toHomeX, int toHomeZ, int gates) {
        List<Side> out = new ArrayList<>(List.of(Side.values()));
        out.sort(Comparator.comparing((Side s) -> !s.in(gates))
                .thenComparingLong(s -> -((long) s.dx * toHomeX + (long) s.dz * toHomeZ)));
        return out;
    }

    /**
     * The sides a trip tries, at most {@link #SIDES} (recon.md 3.5): the sides not yet tried since the last good recon
     * ({@code tried}, a mask), in {@code order}, then the tried ones. So a blind side is not tried first twice and two trips
     * cover all four; once all four were tried and every one was blind, a trip starts again at the first.
     */
    public static List<Side> tripSides(List<Side> order, int tried) {
        List<Side> out = new ArrayList<>();
        for (Side s : order) {
            if (!s.in(tried)) {
                out.add(s);
            }
        }
        for (Side s : order) {
            if (s.in(tried)) {
                out.add(s);
            }
        }
        return out.subList(0, Math.min(SIDES, out.size()));
    }

    /** The mask of {@code sides}. */
    public static int mask(Collection<Side> sides) {
        int m = 0;
        for (Side s : sides) {
            m |= s.bit();
        }
        return m;
    }

    /**
     * Whether a column {@code dx}, {@code dz} from a base's centre lies in the vantage band (recon.md 3.5): a settlement's
     * at a Chebyshev distance of at least {@link #STANDOFF} (beyond the reach of every guard in the yard, for a player as for
     * an NPC) and a horizontal distance of at most {@link #SIGHT}; a lair's from {@link #LAIR_STANDOFF} to
     * {@link #LAIR_SIGHT}.
     */
    public static boolean inBand(int dx, int dz, boolean lair) {
        return inBand(dx, dz, Band.of(lair));
    }

    /**
     * A vantage band: the nearest its columns come to the base's centre ({@code standoff}, Chebyshev), the farthest
     * ({@code sight}, horizontal) and how far they lie to either side of the side's axis ({@code lateral}). The game uses
     * {@link #of}; a GameTest base gives a scaled one (its transient {@code testStandoff} and {@code testSight}), since
     * the real band does not fit a test field.
     */
    public record Band(int standoff, int sight, int lateral) {
        /** A settlement's ({@link #STANDOFF} to {@link #SIGHT}, {@link #LATERAL} to either side) or a lair's (on the axis). */
        public static Band of(boolean lair) {
            return of(lair, ReconOptic.NONE);
        }

        /**
         * The band a scout watching through {@code optic} uses (task R12, recon.md 5.6): a settlement's runs from the
         * standoff to the instrument's reach (61 to 96 with a spyglass). A lair's is the same whatever the scout carries:
         * it is watched from the ground through the gaps in its walls (3.7), where the design measured the look only out
         * to 64, so no instrument is taken out there.
         */
        public static Band of(boolean lair, ReconOptic optic) {
            return lair ? new Band(LAIR_STANDOFF, LAIR_SIGHT, 0) : new Band(STANDOFF, optic.sight(), LATERAL);
        }
    }

    /** Whether a column {@code dx}, {@code dz} from a base's centre lies in {@code band} (as {@link #inBand(int, int, boolean)}). */
    public static boolean inBand(int dx, int dz, Band band) {
        long sight = band.sight();
        return Math.max(Math.abs(dx), Math.abs(dz)) >= band.standoff() && (long) dx * dx + (long) dz * dz <= sight * sight;
    }

    /** A vantage column: where it is from the base's centre, how far off the side's axis and how far out along it. */
    public record Column(int dx, int dz, int lateral, int depth) {
    }

    /**
     * A side's vantage columns in the band, in the order the scout prefers them (recon.md 3.5): on the side's axis first,
     * then one block off it, and so on to {@link #LATERAL} (the left before the right); at each, the nearest the standoff
     * first. A lair's are on the gap's axis only, on the ground (3.7). The game side keeps only the ones it can walk to with
     * a solid top and open sky ({@code work.ReconVantage}) and ranks them by {@link #GROUND_ORDER}, which prefers the
     * higher ground along each line.
     */
    public static List<Column> columns(Side side, boolean lair) {
        return columns(side, Band.of(lair));
    }

    /** A side's vantage columns in {@code band}, in the order of {@link #columns(Side, boolean)}. */
    public static List<Column> columns(Side side, Band band) {
        List<Column> out = new ArrayList<>();
        for (int off = 0; off <= band.lateral(); off++) {
            for (int sign : off == 0 ? new int[]{1} : new int[]{-1, 1}) {
                int l = off * sign;
                for (int depth = band.standoff(); ; depth++) {
                    int dx = side.dx != 0 ? side.dx * depth : l;
                    int dz = side.dz != 0 ? side.dz * depth : l;
                    if (!inBand(dx, dz, band)) {
                        break;
                    }
                    out.add(new Column(dx, dz, l, depth));
                }
            }
        }
        return out;
    }

    /**
     * A vantage column the scout can stand on (the game side found it walkable ground with a solid top, open sky, a
     * loaded chunk, and in no base's plan or alarm box): the column, and how high its standing ground lies above the
     * target's floor, in blocks (negative below it).
     */
    public record Ground(Column column, int rise) {
    }

    /**
     * The order in which the scout prefers the columns it can stand on (recon.md 3.5, task R4a): the side's axis first,
     * then one block off it, and so on (the left before the right, as {@link #columns}), since a column off the axis of a
     * grown base needs up to 4 blocks more of pillar (table 3.3); along each line, the highest standing ground first (a
     * shorter pillar), then the nearest the standoff. On flat land this is {@link #columns}' order.
     */
    public static final Comparator<Ground> GROUND_ORDER = Comparator.comparingInt((Ground g) -> Math.abs(g.column().lateral()))
            .thenComparingInt(g -> g.column().lateral())
            .thenComparingInt(g -> -g.rise())
            .thenComparingInt(g -> g.column().depth());

    /** {@code found} in {@link #GROUND_ORDER}, best first (a new list). */
    public static List<Ground> rank(Collection<Ground> found) {
        List<Ground> out = new ArrayList<>(found);
        out.sort(GROUND_ORDER);
        return out;
    }

    /**
     * {@code found} best first when the scout watches through an instrument that reaches past the eyes (task R12): the
     * columns within the eyes' reach ({@code eyes}, horizontal from the centre) first, in {@link #GROUND_ORDER}, then the
     * deeper ones the instrument opens, in the same order. A farther column needs a taller pillar for the same look (recon.md
     * 5.6: a gate side's 12 blocks at 61 are 19 at 96), so higher ground deep in the band never pulls the scout off a
     * column the eyes would have used: the instrument never makes a side worse. It helps where trees, water, cliffs or a
     * base's plan leave the eyes' band without a column to stand on. With {@code eyes} at the band's own reach this is
     * {@link #rank(Collection)}.
     */
    public static List<Ground> rank(Collection<Ground> found, int eyes) {
        long reach = (long) eyes * eyes;
        List<Ground> out = new ArrayList<>(found);
        out.sort(Comparator.comparing((Ground g) -> (long) g.column().dx() * g.column().dx()
                + (long) g.column().dz() * g.column().dz() > reach).thenComparing(GROUND_ORDER));
        return out;
    }

    // ---- The walk round (recon.md 3.5, step 2; task R4b) ----

    /** A point {@code dx}, {@code dz} from a base's centre. */
    public record Point(int dx, int dz) {
    }

    /** The side of a base a point {@code dx}, {@code dz} from its centre lies on: the axis it is farther along (ties: east or west). */
    public static Side region(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Side.EAST : Side.WEST;
        }
        return dz >= 0 ? Side.SOUTH : Side.NORTH;
    }

    /** The corner between two sides next to each other, {@code ring} out on both axes. */
    static Point corner(Side a, Side b, int ring) {
        return new Point((a.dx + b.dx) * ring, (a.dz + b.dz) * ring);
    }

    /**
     * The corners a scout walks by, from where it stands ({@code fromDx}, {@code fromDz} from the target's centre) to side
     * {@code to}, so that it goes round the base outside the square of half {@code ring} and never across its middle (its
     * alarm box lies well inside the standoff, recon.md 3.5): none when it is on that side already, the corner between
     * the two for a side next to its own, and two corners for the opposite side, round by the side facing home
     * ({@code toHomeX}, {@code toHomeZ} point from the target to our base; the way home leads that way anyway), north or
     * east on a tie.
     */
    public static List<Point> round(int fromDx, int fromDz, Side to, int ring, int toHomeX, int toHomeZ) {
        Side from = region(fromDx, fromDz);
        if (from == to) {
            return List.of();
        }
        boolean opposite = from.dx == -to.dx && from.dz == -to.dz;
        if (!opposite) {
            return List.of(corner(from, to, ring));
        }
        // the two sides beside both: the one facing home more (north or east on a tie)
        Side a = from.dx == 0 ? Side.EAST : Side.NORTH, b = from.dx == 0 ? Side.WEST : Side.SOUTH;
        long da = (long) a.dx * toHomeX + (long) a.dz * toHomeZ, db = (long) b.dx * toHomeX + (long) b.dz * toHomeZ;
        Side via = db > da ? b : a;
        return List.of(corner(from, via, ring), corner(via, to, ring));
    }

    /** What the scout does after a look from its pillar of {@code height} blocks. */
    public enum Climb {
        /** One more block. */
        UP,
        /** Stop here and watch. */
        WATCH,
        /** No good look under the cap: take the pillar down and walk round to the next side. */
        NEXT_SIDE
    }

    /**
     * Whether one look is good enough to watch from (recon.md 3.4): at least {@link #MIN_POINTS} of the yard's points and its
     * middle in view. The watch from there must still last {@link #MIN_WATCH_TICKS} for its report to be good.
     */
    public static boolean goodLook(int points, boolean middle) {
        return points >= MIN_POINTS && middle;
    }

    /** The vantage a watch is kept from, for its report: "pillar 12", "high ground" (a hill, no pillar), "ground". */
    public static String vantage(int pillar, int rise) {
        return pillar > 0 ? "pillar " + pillar : rise > 0 ? "high ground" : "ground";
    }

    /** Whether the scout looks at this height: once from the ground, then after each block from {@link #LOOK_FROM} up. */
    public static boolean looksAt(int height) {
        return height == 0 || height >= LOOK_FROM;
    }

    /** Where it stops: {@link #CLIMB_PAST} above the first good look, never past {@link #PILLAR_MAX}. */
    public static int stopHeight(int firstGood) {
        return Math.min(PILLAR_MAX, Math.max(0, firstGood) + CLIMB_PAST);
    }

    /**
     * The climb (recon.md 3.5, step 4): {@code firstGood} is the first height whose look was good (-1 while none was): it
     * goes on up to {@link #stopHeight} and watches there; with no good look it climbs to the cap and then tries the next
     * side.
     */
    public static Climb climb(int height, int firstGood) {
        if (firstGood >= 0) {
            return height >= stopHeight(firstGood) ? Climb.WATCH : Climb.UP;
        }
        return height >= PILLAR_MAX ? Climb.NEXT_SIDE : Climb.UP;
    }

    /** Whether an enemy (a soldier, a hero or a player) keeps the scout up: in its sight within {@link #THREAT_RANGE}. */
    public static boolean threat(double distance, boolean inSight) {
        return inSight && distance <= THREAT_RANGE;
    }

    /** What the scout on its pillar does now. */
    public enum Perch {
        /** Go on watching. */
        WATCH,
        /** The watch is over but an enemy is near: hold still on top. */
        HOLD,
        /** Come down, block by block, and go home with the report. */
        DOWN,
        /** Hurt, or a block of its pillar broken under it: the watch ends, it comes down and retreats home. */
        RETREAT
    }

    /**
     * The scout on its pillar (recon.md 3.5, "If enemies come near"): a hit or a broken pillar block brings it down at once;
     * otherwise it watches for {@code watchTicks} from {@code watchStartedAt}, enemies near or not (what it sees still
     * counts), and after the watch it holds while an enemy is near ({@code enemyNear}, {@link #threat}), at most
     * {@link #HOLD_MAX_TICKS}, then comes down regardless. What it saw of a watch cut short counts only when the watch had
     * lasted {@link #MIN_WATCH_TICKS} and was good ({@link Intel.Recon#good}).
     */
    public static Perch perch(long now, long watchStartedAt, long watchTicks, boolean enemyNear, boolean hurt, boolean pillarBroken) {
        if (hurt || pillarBroken) {
            return Perch.RETREAT;
        }
        long watchEnd = watchStartedAt + watchTicks;
        if (now < watchEnd) {
            return Perch.WATCH;
        }
        return enemyNear && now - watchEnd < HOLD_MAX_TICKS ? Perch.HOLD : Perch.DOWN;
    }

    // ---- The council's choice (recon.md 5.4 and 6) ----

    /**
     * Whether the council considers any target this pass: at the interval always; inside it (after a quarter of it) only
     * when a faction struck us since our last raid. Otherwise it does nothing and writes no waiting line, so the time is
     * never counted as waiting for a report.
     */
    public static boolean considersTargets(boolean intervalOver, boolean anyStruckSinceLastRaid) {
        return intervalOver || anyStruckSinceLastRaid;
    }

    /**
     * Whether a reported base is a candidate target: at the interval every base with a usable report; inside it only a
     * settlement of a faction that struck us since our last raid, so a weaker bystander's base cannot be chosen, fail the
     * strike test and block the counter-attack on the striker.
     */
    public static boolean candidate(boolean intervalOver, boolean settlement, boolean struckSinceLastRaid) {
        return intervalOver || settlement && struckSinceLastRaid;
    }

    /**
     * Whether a recon made at {@code reconAt} answers a strike made at {@code struckAt} (recon.md 6, task R5): the
     * counter-attack rests on a good recon made since the striker's last strike, the urgent recon (or any made after the
     * strike). One from before the strike watched a garrison whose raiders had not yet marched on us, or had not yet been
     * beaten at our walls, so it says nothing of how the strike left them; as for the urgent order ({@link #urgentDue},
     * {@link #urgentQualifies}), it does not answer it. A later strike by the same faction dates {@code struckAt} again, so
     * a recon made between the two strikes no longer answers until a newer one comes home.
     */
    public static boolean answersStrike(long reconAt, long struckAt) {
        return reconAt > struckAt;
    }

    /** The council's verdict on its chosen target. */
    public enum Verdict {
        /** A raid at the interval. */
        ATTACK,
        /** A counter-attack inside the interval. */
        COUNTER_ATTACK,
        /** Not yet, or not strong enough. */
        WAIT
    }

    /**
     * The verdict on a target ({@code plan}: its plan's power, {@code unit}: one of its soldiers, {@link Reserve.Plan}): at
     * the interval, two thirds of the plan ({@link Intel#twoThirds}); inside it, 3 to 2 ({@link Intel#threeToTwo}) against
     * the counter-attack's own plan, and only against a faction that struck us since our last raid: an ordinary recon that
     * arrives early never launches a raid before the interval.
     */
    public static Verdict verdict(boolean intervalOver, boolean struckSinceLastRaid, double party, double plan, double unit) {
        if (intervalOver) {
            return Intel.twoThirds(party, plan, unit) ? Verdict.ATTACK : Verdict.WAIT;
        }
        return struckSinceLastRaid && Intel.threeToTwo(party, plan, unit) ? Verdict.COUNTER_ATTACK : Verdict.WAIT;
    }
}
