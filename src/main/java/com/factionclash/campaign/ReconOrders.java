package com.factionclash.campaign;

import com.factionclash.faction.FactionType;
import com.factionclash.logic.campaign.Intel;
import com.factionclash.logic.campaign.ReconRules;
import com.factionclash.npc.NpcRole;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The war council's recon orders ({@code docs/design/recon.md} 2.1, task R3): each council pass ({@code
 * FactionDirector.raids}, after the raid under way and the wait for its news, so a faction gives no orders while its own
 * raid is out) first settles the open orders by what the faction knows, then places the next ones. Everything here is the
 * faction's own: its reports of enemy bases, the strikes it saw ({@link FactionState#struckBy}), its roster's view of its
 * scouts ({@link NpcRecord#where}, {@link NpcRecord#role}) and what they told at the station. It never reads an enemy's
 * records.
 *
 * <p>An order is written at the station; a scout takes it at home ({@link #take}) and tells at the station what it saw of
 * the target ({@link #atStation}): a scout's recon trip since task R4b, and the council judges by the recons the orders
 * bring since task R5.
 */
public final class ReconOrders {
    private ReconOrders() {
    }

    /** The faction's report of an order's target (the same subject within 40 blocks, the nearest), or null when it has none. */
    public static Report report(FactionState f, ReconOrder o) {
        Report best = null;
        for (Report r : f.knowledge) {
            if (o.about(r) && (best == null || r.pos.distSqr(o.target) < best.pos.distSqr(o.target))) {
                best = r;
            }
        }
        return best;
    }

    /** Whether an order on the base of report {@code r} is open. */
    static boolean ordered(FactionState f, Report r) {
        for (ReconOrder o : f.reconOrders) {
            if (o.about(r)) {
                return true;
            }
        }
        return false;
    }

    /** The open order its scout {@code scout} took, or null. */
    public static ReconOrder takenBy(FactionState f, UUID scout) {
        for (ReconOrder o : f.reconOrders) {
            if (scout != null && scout.equals(o.scout)) {
                return o;
            }
        }
        return null;
    }

    /** A scout takes an order at home (task R4b's {@code ScoutJob}: at the station or the storehouse, before it sets out). */
    public static void take(ReconOrder o, UUID scout, long now) {
        o.scout = scout;
        o.takenAt = now;
        o.reported = ReconRules.ScoutReport.NONE;
        o.reportedSides = 0;
    }

    /**
     * A scout tells at the station what it saw of the base it was ordered to watch (task R4b's {@code ScoutJob.report} calls
     * this before it delivers its reports): {@code brought} is its report of that base (null: none), with the recon its watch
     * gave and the sides its trip tried. A good recon, a blind one, or none of the target is the order's ending at the next
     * council pass ({@link #settle}). Returns whether the scout had taken an order.
     */
    public static boolean atStation(FactionState f, UUID scout, Report brought) {
        ReconOrder o = takenBy(f, scout);
        if (o == null || o.reported != ReconRules.ScoutReport.NONE) {
            return false;
        }
        boolean ofTarget = brought != null && o.about(brought);
        if (ofTarget && brought.recon != null) {
            o.reported = brought.recon.good() ? ReconRules.ScoutReport.GOOD : ReconRules.ScoutReport.BLIND;
        } else {
            o.reported = ReconRules.ScoutReport.EMPTY;
            o.reportedSides = ofTarget ? brought.reconSidesTried : 0;
        }
        return true;
    }

    /**
     * How the open orders stand now (recon.md 2.1's table, {@link ReconRules#settle}): one its scout answered with a good
     * recon is done; with a blind one it ends, that failure counted once already when the report was filed
     * ({@link Intel#mergeRecon}); with no recon of the target, or when its scout is missing or known or presumed dead, it
     * ends as a failed attempt on the target ({@link Intel#reconFailed}: the back-off); one whose scout was retrained is
     * open again for another; one nobody took is dropped after half a day, when the target turned fresh by other means, or
     * when it was seen in ruins. An order whose target the faction no longer has a report of is dropped while untaken.
     * Returns whether anything changed.
     */
    public static boolean settle(FactionState f, long now) {
        boolean changed = false;
        for (Iterator<ReconOrder> it = f.reconOrders.iterator(); it.hasNext(); ) {
            ReconOrder o = it.next();
            Report r = report(f, o);
            if (r == null && !o.taken()) {
                it.remove(); // (a report forgotten: nothing left to watch)
                changed = true;
                continue;
            }
            NpcRecord scout = o.scout == null ? null : f.roster.get(o.scout);
            ReconRules.Ending e = ReconRules.settle(o.taken(), o.reported, scout == null ? null : scout.where,
                    scout != null && scout.role == NpcRole.SCOUT, o.orderedAt, now, r == null ? null : r.reconFile());
            if (e != ReconRules.Ending.OPEN) {
                log(f, o, now, e); // the operators' log (tools/rolltrack.py): how the order ended, before it is changed
            }
            if (e == ReconRules.Ending.REOPENED) {
                o.scout = null;
                o.takenAt = 0;
                o.reported = ReconRules.ScoutReport.NONE;
                o.reportedSides = 0;
                changed = true;
            } else if (e.ends()) {
                if (e.countsFailure() && r != null) {
                    r.reconFile(Intel.reconFailed(r.reconFile(), now, o.reportedSides));
                }
                it.remove();
                changed = true;
            }
        }
        return changed;
    }

    /**
     * One {@code FC_RECON} line in the server log for {@code tools/rolltrack.py} (recon.md 10.4, task R6): how an order
     * ended ({@code ending}: done, blind, empty-handed, lost, reopened or dropped), with what the order held. The faction's
     * own records only; the operators read it, no faction does.
     */
    private static void log(FactionState f, ReconOrder o, long now, ReconRules.Ending ending) {
        com.google.gson.JsonObject j = new com.google.gson.JsonObject();
        j.addProperty("ev", "order_end");
        j.addProperty("t", now);
        j.addProperty("faction", f.type.id);
        j.addProperty("target", o.target == null ? "" : o.target.toShortString());
        j.addProperty("subject", o.subject == null ? "" : o.subject.id);
        j.addProperty("kind", o.kind == null ? "" : o.kind.name());
        j.addProperty("urgent", o.urgent);
        j.addProperty("orderedAt", o.orderedAt);
        j.addProperty("takenAt", o.takenAt);
        NpcRecord s = o.scout == null ? null : f.roster.get(o.scout);
        j.addProperty("scout", o.scout == null ? "" : o.scout.toString());
        j.addProperty("scoutName", s == null ? "" : s.name);
        j.addProperty("reported", o.reported.name());
        j.addProperty("reportedSides", o.reportedSides);
        j.addProperty("ending", ending.name());
        com.factionclash.FactionClash.LOGGER.info("FC_RECON {}", j);
    }

    /**
     * Places the council's next orders this pass (recon.md 2.1): none while it could send no party ({@code party}, the
     * census), and otherwise only when the interval is nearly ripe ({@code nextRaidAt}, {@link ReconRules#inWindow}) or an
     * urgent recon is due. Urgent: of each faction that struck us since our last raid, its reported settlement nearest our
     * base, once our alarm has been quiet a while ({@code alarmAt}: the last intruder it noticed or strike it saw), the
     * counter-attack window is open, and no urgent order went to that faction within a quarter of the interval
     * ({@link ReconRules#urgentDue}). Ordinary, in the window only: a settlement or lair never judged, stale or only blind,
     * not backing off. One order a target, one urgent and one ordinary a faction ({@link ReconRules#next}). Returns the
     * orders placed.
     */
    public static List<ReconOrder> place(FactionState f, BaseState home, long now, int party, long interval, long nextRaidAt, long alarmAt) {
        List<Report> bases = new ArrayList<>();
        for (Report r : f.knowledge) {
            if (r.base() && !r.fallen && r.subject != null && r.pos != null && Diplomacy.hostile(f.type, r.subject)) {
                bases.add(r);
            }
        }
        // the urgent recons due: each striker's reported settlement nearest our base
        FactionType[] urgentFor = new FactionType[bases.size()];
        boolean anyUrgent = false;
        for (var strike : f.struckBy.entrySet()) {
            FactionType enemy = strike.getKey();
            int nearest = -1;
            for (int i = 0; i < bases.size(); i++) {
                Report r = bases.get(i);
                if (r.kind == Report.Kind.ENEMY_BASE && r.subject == enemy
                        && (nearest < 0 || home.center.distSqr(r.pos) < home.center.distSqr(bases.get(nearest).pos))) {
                    nearest = i;
                }
            }
            if (nearest < 0) {
                continue; // none of its settlements is reported: the faction does not know where they live
            }
            Intel.Recon kept = bases.get(nearest).reconFile().recon();
            if (ReconRules.urgentDue(now, strike.getValue(), f.lastRaidAt, interval, alarmAt, f.urgentOrderedAt(enemy),
                    kept == null ? 0 : kept.at())) {
                urgentFor[nearest] = enemy;
                anyUrgent = true;
            }
        }
        boolean inWindow = ReconRules.inWindow(now, nextRaidAt);
        if (!ReconRules.ordersNow(false, party, inWindow, anyUrgent)) {
            return List.of();
        }
        List<ReconRules.Target> targets = new ArrayList<>();
        for (int i = 0; i < bases.size(); i++) {
            Report r = bases.get(i);
            Intel.ReconFile file = r.reconFile();
            boolean ordered = ordered(f, r);
            boolean urgent = urgentFor[i] != null && ReconRules.urgentQualifies(file, now, ordered, f.struckAt(urgentFor[i]));
            boolean ordinary = inWindow && ReconRules.ordinaryQualifies(file, now, ordered);
            if (urgent || ordinary) {
                Intel.Recon kept = file.recon();
                targets.add(new ReconRules.Target(i, urgent, ordinary, kept != null, kept == null ? 0 : defence(kept),
                        Math.sqrt(home.center.distSqr(r.pos))));
            }
        }
        boolean urgentOpen = f.reconOrders.stream().anyMatch(o -> o.urgent);
        boolean ordinaryOpen = f.reconOrders.stream().anyMatch(o -> !o.urgent);
        List<ReconOrder> placed = new ArrayList<>();
        for (ReconRules.Placement p = ReconRules.next(targets, urgentOpen, ordinaryOpen); p != null;
             p = ReconRules.next(targets, urgentOpen, ordinaryOpen)) {
            Report r = bases.get(p.target().key());
            ReconOrder o = new ReconOrder(r.pos, r.subject, r.kind, home.id, now, p.urgent());
            f.reconOrders.add(o);
            placed.add(o);
            if (p.urgent()) {
                f.reconUrgentAt.put(r.subject, now);
                urgentOpen = true;
            } else {
                ordinaryOpen = true;
            }
            int key = p.target().key();
            targets.removeIf(t -> t.key() == key); // one order a target
        }
        return placed;
    }

    /**
     * The defence a base's last good recon showed, in soldiers (read only to choose which base to watch first, never to
     * launch): its soldiers, and the heroes seen as so many of them under the soldier backbone.
     */
    static double defence(Intel.Recon r) {
        Intel.Estimate e = new Intel.Estimate(r.soldiers(), Math.max(1, r.power()), r.heroPower());
        return com.factionclash.logic.hero.HeroRules.countedPower(e.soldierPower(), e.heroPower()) / e.power();
    }

    /**
     * The council's line on its open orders ({@code LAST_COUNCIL}, read by {@code /fc admin dump}), or null with none open:
     * "recon ordered day 4.10 of the Grove Covenant settlement at 400, 67, 16, not taken yet", "urgent recon ordered day
     * 4.30 of ..., Edrin Cole on the way" (its scout's name as the roster knows it), "..., Edrin Cole reported".
     */
    public static String line(FactionState f) {
        if (f.reconOrders.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ReconOrder o : f.reconOrders) {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append(o.urgent ? "urgent recon" : "recon").append(String.format(Locale.ROOT, " ordered day %.2f", o.orderedAt / 24000.0))
                    .append(" of ").append(ReconOrder.what(o.subject, o.kind)).append(" at ").append(o.target.toShortString());
            if (!o.taken()) {
                sb.append(", not taken yet");
            } else {
                NpcRecord s = f.roster.get(o.scout);
                sb.append(", ").append(s == null || s.name.isEmpty() ? "its scout" : s.name)
                        .append(o.reported == ReconRules.ScoutReport.NONE ? " on the way" : " reported");
            }
        }
        return sb.toString();
    }
}
