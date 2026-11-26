package com.factionclash.work;

import com.factionclash.campaign.BaseWatch;
import com.factionclash.campaign.Report;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.campaign.Intel;
import com.factionclash.logic.campaign.ReconOptic;
import com.factionclash.logic.campaign.ReconRules;
import com.factionclash.logic.campaign.ReconRules.Side;
import com.factionclash.logic.campaign.ReconWatch;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A recon scout's trip ({@code docs/design/recon.md} 3.5 and 3.6, task R4b): what its order named (the target as its
 * faction reported it: position, faction, kind), the sides it tries in order, how far it got, the column it climbs at,
 * its pillar, the looks of its climb, its watch and what it brings home. {@link ScoutJob} drives it; this holds the state
 * and saves it with the job, so a reload resumes the trip where it was: on the way, on the pillar, midway through the
 * watch (the faces it counted kept by UUID, {@link BaseWatch#saveWatch}).
 *
 * <p>What it brings home is held apart from the scout's other reports ({@link #brought}): never capped with them, told at
 * the station first, lost with the scout if it dies on the way.</p>
 */
final class ReconTrip {
    /** Where the trip stands. */
    enum Phase {
        /** At home: dirt from the storehouse, or dug by hand, before it sets out. */
        PREPARE,
        /** On the way to the side it tries, round the base outside the standoff where it must. */
        APPROACH,
        /** Walking to the foot of a vantage column on that side. */
        FOOT,
        /** At the column: the climb and its looks, the watch, the way down. */
        PILLAR,
        /**
         * Done at the target: going home with what it saw, first round the base by the corners of the standoff's square to
         * the side facing home when it is on another ({@link #waypoints}), not across the enemy's alarm box (a monster
         * met on the way is stepped round; enemy soldiers that turn it home in a retreat send it straight home, as on
         * any trip).
         */
        DONE
    }

    /** What it does at its column. */
    enum Step {
        /** Building up and looking after each block (or the look from the ground at a lair). */
        CLIMB,
        /** Watching from the top. */
        WATCH,
        /** Taking the pillar down (to the next side, or home). */
        DOWN
    }

    /** Why the trip at the target ended. */
    enum End {
        /** It has not ended. */
        NONE,
        /** The watch is over (or the trip's time ran out): home with the report. */
        HOME,
        /** Hurt, or its pillar broken under it: down, and home in a retreat. */
        RETREAT
    }

    // ---- the order, as the faction knows its target ----
    final BlockPos target;
    final FactionType subject;
    final Report.Kind kind;
    final boolean urgent;
    /** From home to the target (blocks), for the trip's plan ({@code Whereabouts.reconPlan}). */
    final double distance;
    /** The sides this trip tries, in order ({@link ReconRules#tripSides}), and the one it is at. */
    final List<Side> sides;
    int side;
    /** The sides tried so far on this trip (a mask). */
    int tried;
    Phase phase = Phase.PREPARE;

    // ---- the column ----
    /** The foot of the column it walks to or stands at (null: none chosen on this side yet), and its rise over the floor. */
    BlockPos foot;
    int rise;
    /** The pillar at the column (saved). */
    Scaffold.Pillar pillar;
    Step step = Step.CLIMB;
    /** The first height whose look was good (-1: none yet), and the height of the climb's last look (-1: none). */
    int firstGood = -1;
    int lookedAt = -1;
    /** The best single look of the trip (for a blind report): its yard points, middle, time and height. */
    int bestPoints;
    boolean bestMiddle;
    long bestAt;
    int bestHeight;
    /** The watch (null until it starts), when it started, and the vantage it is kept from ("pillar 12"). */
    ReconWatch watch;
    long watchStartedAt;
    String vantage = "";
    /**
     * The instrument its looks were taken through ({@link ReconOptic#id}: "spyglass", empty for the eyes; task R12), for
     * the report's tag: set at each look and at the watch's start.
     */
    String optic = "";
    End end = End.NONE;
    String why = "";

    // ---- not saved: worked out again after a load ----
    /** The columns found on this side, best first, and the one it tries; the corners still to walk by; the walk's ticks. */
    List<ReconVantage.Vantage> columns;
    int columnIndex;
    List<ReconRules.Point> waypoints;
    int walkTicks, routeFailBase;
    /** How long the leg it walks now may take (worked out at the leg's start from its length). */
    long legBudget;

    ReconTrip(BlockPos target, FactionType subject, Report.Kind kind, boolean urgent, double distance, List<Side> sides) {
        this.target = target.immutable();
        this.subject = subject;
        this.kind = kind;
        this.urgent = urgent;
        this.distance = distance;
        this.sides = List.copyOf(sides);
    }

    boolean lair() {
        return kind == Report.Kind.LAIR;
    }

    /** "the Stoneguard settlement", "the Hollow lair". */
    String what() {
        return com.factionclash.campaign.ReconOrder.what(subject, kind);
    }

    /** The side it is at now (null once every side was tried). */
    Side current() {
        return side < sides.size() ? sides.get(side) : null;
    }

    /** A look from height {@code height}: kept when it is the best of the trip (most points, then the middle). */
    void looked(ReconWatch.Sample s, int height) {
        lookedAt = height;
        if (s == null) {
            return;
        }
        boolean better = bestAt <= 0 || s.points() > bestPoints || s.points() == bestPoints && s.middle() && !bestMiddle;
        if (better) {
            bestPoints = s.points();
            bestMiddle = s.middle();
            bestAt = Math.max(1, s.at());
            bestHeight = height;
        }
    }

    /**
     * What the trip found at the target ({@code docs/design/recon.md} 4): the watch's focused report (good, or cut short
     * before {@link ReconRules#MIN_WATCH_TICKS} and so blind), else a blind report from its best look ("could not see
     * inside", with the share of the yard that look had: no count), else nothing (it never looked at the yard). By
     * {@code by}, the scout's name.
     */
    Intel.Recon result(String by) {
        if (watch != null && !watch.empty()) {
            return watch.recon(vantage, by, optic);
        }
        if (bestAt > 0) {
            String v = ReconRules.vantage(bestHeight, rise);
            return new Intel.Recon(0, 0, Map.of(), Intel.coverage(bestPoints), bestMiddle, 0, bestAt, v, by, Set.of(), optic);
        }
        return null;
    }

    /**
     * The report it brings to the station: its target at the reported position, with what it found ({@link #result}) and
     * the sides it tried; null when it found nothing and tried no side (it never got there). Only one with a recon is
     * filed among the faction's reports; one without tells the order its sides ({@code ReconOrders.atStation}).
     */
    Report brought(String by, long now) {
        Intel.Recon r = result(by);
        if (r == null && tried == 0) {
            return null;
        }
        String note = what().substring(4) + (r == null ? ", no vantage found" : r.good() ? ", " + r.soldiers()
                + (lair() ? " monsters" : " soldiers") + " counted from " + r.vantage() + through(r) : ", could not see inside");
        Report rep = new Report(kind, target, r == null ? now : Math.max(1, r.at()), subject, r == null || !r.good() ? 0 : r.soldiers(),
                Character.toUpperCase(note.charAt(0)) + note.substring(1));
        rep.recon = r;
        rep.reconSidesTried = tried;
        return rep;
    }

    /** " with a spyglass" for a recon watched through an instrument, nothing for the eyes. */
    static String through(Intel.Recon r) {
        ReconOptic o = ReconOptic.of(r);
        return o.none() ? "" : " with " + o.name();
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("target", target.asLong());
        if (subject != null) {
            t.putString("subject", subject.id);
        }
        t.putString("kind", kind.name());
        t.putBoolean("urgent", urgent);
        t.putDouble("distance", distance);
        int[] s = new int[sides.size()];
        for (int i = 0; i < s.length; i++) {
            s[i] = sides.get(i).ordinal();
        }
        t.putIntArray("sides", s);
        t.putInt("side", side);
        t.putInt("tried", tried);
        t.putString("phase", phase.name());
        if (foot != null) {
            t.putLong("foot", foot.asLong());
            t.putInt("rise", rise);
        }
        if (pillar != null) {
            t.put("pillar", pillar.save());
        }
        t.putString("step", step.name());
        t.putInt("firstGood", firstGood);
        t.putInt("bestPoints", bestPoints);
        t.putBoolean("bestMiddle", bestMiddle);
        t.putLong("bestAt", bestAt);
        t.putInt("bestHeight", bestHeight);
        if (watch != null) {
            t.put("watch", BaseWatch.saveWatch(watch));
            t.putLong("watchStartedAt", watchStartedAt);
        }
        t.putString("vantage", vantage);
        if (!optic.isEmpty()) {
            t.putString("optic", optic);
        }
        t.putString("end", end.name());
        t.putString("why", why);
        return t;
    }

    /** A trip as {@link #save} wrote it, or null when it cannot be read (an unknown kind). */
    static ReconTrip load(CompoundTag t) {
        Report.Kind kind;
        try {
            kind = Report.Kind.valueOf(t.getString("kind"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        List<Side> sides = new ArrayList<>();
        for (int o : t.getIntArray("sides")) {
            if (o >= 0 && o < Side.values().length) {
                sides.add(Side.values()[o]);
            }
        }
        ReconTrip r = new ReconTrip(BlockPos.of(t.getLong("target")), t.contains("subject") ? FactionType.byId(t.getString("subject")) : null,
                kind, t.getBoolean("urgent"), t.getDouble("distance"), sides);
        r.side = t.getInt("side");
        r.tried = t.getInt("tried") & Side.ALL;
        r.phase = valueOf(Phase.class, t.getString("phase"), Phase.APPROACH);
        if (t.contains("foot")) {
            r.foot = BlockPos.of(t.getLong("foot"));
            r.rise = t.getInt("rise");
        }
        r.pillar = t.contains("pillar") ? Scaffold.Pillar.load(t.getCompound("pillar")) : null;
        r.step = valueOf(Step.class, t.getString("step"), Step.CLIMB);
        r.firstGood = t.contains("firstGood") ? t.getInt("firstGood") : -1;
        r.bestPoints = t.getInt("bestPoints");
        r.bestMiddle = t.getBoolean("bestMiddle");
        r.bestAt = t.getLong("bestAt");
        r.bestHeight = t.getInt("bestHeight");
        if (t.contains("watch")) {
            r.watch = BaseWatch.loadWatch(t.getCompound("watch"));
            r.watchStartedAt = t.getLong("watchStartedAt");
        }
        r.vantage = t.getString("vantage");
        r.optic = ReconOptic.byId(t.getString("optic")).id();
        r.end = valueOf(End.class, t.getString("end"), End.NONE);
        r.why = t.getString("why");
        if (r.phase == Phase.PILLAR && r.pillar == null) {
            r.phase = Phase.FOOT; // (a pillar that could not be read: the column is walked to again)
        }
        if (r.phase == Phase.FOOT && r.foot == null) {
            r.phase = Phase.APPROACH;
        }
        return r;
    }

    private static <E extends Enum<E>> E valueOf(Class<E> c, String name, E fallback) {
        try {
            return Enum.valueOf(c, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
