package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.FactionDirector;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.ReconOrder;
import com.factionclash.campaign.ReconOrders;
import com.factionclash.campaign.Report;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.campaign.Intel;
import com.factionclash.logic.campaign.ReconRules;
import com.factionclash.logic.campaign.Whereabouts.State;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.factionclash.gametest.KnowledgeGameTests.ENEMY;
import static com.factionclash.gametest.KnowledgeGameTests.GARRISON;
import static com.factionclash.gametest.KnowledgeGameTests.SIDE;

/**
 * The war council's recon orders ({@code docs/design/recon.md} 2.1 and 6, task R3): placed and settled each council pass
 * ({@code FactionDirector.raids}). No scout takes an order before task R4b, so these tests drive the scout's side by hand:
 * the order taken at home ({@link ReconOrders#take}), what the scout tells at the station ({@link ReconOrders#atStation}),
 * and its roster record (on its way, missing, presumed or known dead, retrained). They run the council on the
 * {@code platform} as {@code KnowledgeGameTests}' council tests do (recon.md 10.2: the council tests drive a delivered
 * recon; no scout walks here), each within one tick of its own batch, on the Tide Alliance's test base with its reports of
 * enemy bases 150 to 700 blocks off (records only: no base stands there, so the council's judgement, unchanged in R3, has no
 * target to weigh unless the test gives it one).
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class ReconOrderGameTests {
    private ReconOrderGameTests() {
    }

    /**
     * The test base, with the faction's war state kept for the cleanup and cleared of what other tests left behind. A
     * filtered run starts in a fresh world: its game time is moved on, so that every time a test writes (half a day back,
     * a strike before our last raid) is after the world's start.
     */
    private static FactionState fresh(GameTestBases.Handle t) {
        KnowledgeGameTests.atLeast(t.helper.getLevel(), 100_000);
        FactionState f = t.data.faction(SIDE);
        KnowledgeGameTests.keepRaidState(t, f); // (no base report, order, strike or urgent order of another test is left)
        f.raidTarget = -1;
        f.raiders.clear();
        f.lastParty.clear();
        f.raidEndNews = null;
        FactionDirector.LAST_COUNCIL.remove(SIDE);
        return f;
    }

    /** Six swordsmen at home: the council could send a party of three. */
    private static List<FactionNpc> garrison(GameTestBases.Handle t) {
        List<FactionNpc> out = new java.util.ArrayList<>();
        for (int[] p : GARRISON) {
            out.add(KnowledgeGameTests.member(t, p[0], p[1], NpcRole.SWORDSMAN));
        }
        return out;
    }

    /** A base report our scouts delivered (a passing look: where it is, no recon), {@code dx} blocks east of our base. */
    private static Report reported(GameTestBases.Handle t, FactionState f, Report.Kind kind, FactionType subject, int dx, long at) {
        Report r = new Report(kind, t.base.center.offset(dx, 0, 0), at, subject, 0, "walls seen");
        KnowledgeGameTests.deliver(t, f, r, at);
        return f.knowledge.stream().filter(k -> k.kind == kind && k.subject == subject && k.pos.equals(r.pos)).findFirst().orElseThrow();
    }

    /** A focused recon of {@code r}'s base, good (12 of the 25 points and the middle) or blind, watched at {@code at}. */
    private static Report recon(Report r, boolean good, long at, int soldiers) {
        Map<UUID, Double> faces = new java.util.LinkedHashMap<>();
        for (int i = 0; i < soldiers; i++) {
            faces.put(UUID.randomUUID(), 1.0);
        }
        Intel.Recon watch = KnowledgeGameTests.recon(good ? 12 : 3, good, at, faces, Map.of());
        return KnowledgeGameTests.delivered(r.kind, r.pos, r.subject, at, watch, ReconRules.Side.WEST.bit());
    }

    private static ReconOrder orderOn(FactionState f, Report r) {
        return f.reconOrders.stream().filter(o -> o.about(r)).findFirst().orElse(null);
    }

    private static Report kept(FactionState f, Report r) {
        return f.knowledge.stream().filter(k -> k.sameSubject(r)).findFirst().orElseThrow();
    }

    /** An order on {@code r} written at {@code at}, as the council places one (the endings test builds them by hand). */
    private static ReconOrder order(GameTestBases.Handle t, FactionState f, Report r, long at) {
        ReconOrder o = new ReconOrder(r.pos, r.subject, r.kind, t.base.id, at, false);
        f.reconOrders.add(o);
        return o;
    }

    // ------------------------------------------------------------------ the window

    /**
     * recon.md 2.1: an ordinary order is placed half a day before the raid interval ends ({@code RECON_LEAD}), not a tick
     * earlier, once a target: never twice while it is open. Before any raid the window leads the first war's gate (the
     * soldier census now runs before the council's gates, so it is known there whether a party could go). No order while
     * the council could send no party, nor while it still waits for the news of its last raid's end. The council's line
     * names the open order, beside its judgement once it weighs a target.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "recon_orders_window")
    public static void aReconIsOrderedHalfADayBeforeTheIntervalEndsAndNotBefore(GameTestHelper h) {
        GameTestBases.Handle t = KnowledgeGameTests.base(h, 40);
        FactionState f = fresh(t);
        try {
            long now = h.getLevel().getGameTime(), lead = ReconRules.LEAD_TICKS;
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.assertTrue(interval > lead, "the raid interval is no longer than the order's lead: " + interval);
            t.data.startedAt = now - 2L * interval;
            f.lastRaidAt = now; // the interval ends at now + interval; the window opens half a day before it
            long opens = now + interval - lead;
            Report target = reported(t, f, Report.Kind.ENEMY_BASE, ENEMY, 200, now);
            KnowledgeGameTests.raids(t, f, opens);
            t.assertTrue(f.reconOrders.isEmpty(), "an order with no party to send: " + f.reconOrders);
            List<FactionNpc> soldiers = garrison(t);
            KnowledgeGameTests.raids(t, f, opens - 1);
            t.assertTrue(f.reconOrders.isEmpty() && FactionDirector.LAST_COUNCIL.get(SIDE) == null,
                    "an order before the window: " + f.reconOrders + " / " + FactionDirector.LAST_COUNCIL.get(SIDE));
            KnowledgeGameTests.raids(t, f, opens);
            ReconOrder o = orderOn(f, target);
            t.assertTrue(f.reconOrders.size() == 1 && o != null && !o.urgent && !o.taken() && o.orderedAt == opens
                    && o.homeBase == t.base.id && o.kind == Report.Kind.ENEMY_BASE && o.subject == ENEMY, "the window's order: " + f.reconOrders);
            String line = FactionDirector.LAST_COUNCIL.get(SIDE);
            t.assertTrue(line != null && line.contains("recon ordered day") && line.contains("Grove Covenant settlement at "
                    + target.pos.toShortString()) && line.contains("not taken yet"), "the council's order line: " + line);
            KnowledgeGameTests.raids(t, f, opens + 1);
            t.assertTrue(f.reconOrders.size() == 1, "a target ordered twice: " + f.reconOrders);
            // before any raid: the window leads the first war's gate, where the council does not judge yet
            f.reconOrders.clear();
            f.lastRaidAt = 0;
            t.data.startedAt = now;
            KnowledgeGameTests.raids(t, f, opens - 1);
            t.assertTrue(f.reconOrders.isEmpty(), "an order before the first war's window: " + f.reconOrders);
            KnowledgeGameTests.raids(t, f, opens);
            t.assertTrue(f.reconOrders.size() == 1 && orderOn(f, target) != null, "no order before the first war's gate: " + f.reconOrders);
            // while the council waits for the news of its last raid's end, it orders nothing
            f.reconOrders.clear();
            t.data.startedAt = now - 2L * interval;
            f.lastRaidAt = now;
            f.raidEndNews = UUID.randomUUID();
            f.lastParty.add(soldiers.get(0).getUUID());
            f.raidStartedAt = opens;
            f.raidPlanTicks = 100_000;
            KnowledgeGameTests.raids(t, f, opens + 10);
            t.assertTrue(f.reconOrders.isEmpty() && f.raidEndNews != null, "an order while the last raid is out: " + f.reconOrders);
            f.raidEndNews = null;
            KnowledgeGameTests.raids(t, f, opens + 11);
            t.assertTrue(f.reconOrders.size() == 1, "no order once the raid's end is known: " + f.reconOrders);
            // once the council weighs the target (unchanged until R5: a garrison like its own, 6, and a party of 3 waits),
            // its line carries the open order after its judgement
            KnowledgeGameTests.enemyBase(t, ENEMY, target.pos);
            f.lastRaidAt = opens - interval - 1;
            KnowledgeGameTests.raids(t, f, opens + 12);
            line = FactionDirector.LAST_COUNCIL.get(SIDE);
            t.assertTrue(f.raidTarget < 0 && line != null && line.contains(": party ") && line.contains("wait")
                    && line.contains("; recon ordered day"), "the judgement and its order: " + line);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ how many, and which first

    /**
     * recon.md 2.1: one ordinary and one urgent order open at most, one a target. In the window with a strike seen: the
     * ordinary one goes to the nearest base never judged (a lair), the urgent one to the striker's nearest settlement, and
     * no third order is placed, however many targets qualify. A target judged fresh is not ordered; a stale one comes after
     * every base never judged, the weakest of them first, then the nearest.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "recon_orders_limits")
    public static void oneOrderATargetAndTwoAFaction(GameTestHelper h) {
        GameTestBases.Handle t = KnowledgeGameTests.base(h, 40);
        FactionState f = fresh(t);
        try {
            long now = h.getLevel().getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - 2L * interval;
            f.lastRaidAt = now - interval + 1000; // in the window, and the counter-attack window open
            garrison(t);
            Report lair = reported(t, f, Report.Kind.LAIR, FactionType.HOLLOW, 150, now);
            Report grove = reported(t, f, Report.Kind.ENEMY_BASE, ENEMY, 200, now);
            Report ember = reported(t, f, Report.Kind.ENEMY_BASE, FactionType.EMBER_LEGION, 250, now);
            Report groveFar = reported(t, f, Report.Kind.ENEMY_BASE, ENEMY, 500, now);
            f.struck(ENEMY, now - 700); // the Grove struck us, and the fight has been over a while
            KnowledgeGameTests.raids(t, f, now);
            ReconOrder ordinary = orderOn(f, lair), urgent = orderOn(f, grove);
            t.assertTrue(f.reconOrders.size() == 2 && ordinary != null && !ordinary.urgent && urgent != null && urgent.urgent
                    && f.urgentOrderedAt(ENEMY) == now, "an ordinary order on the nearest base never judged and an urgent one on the "
                    + "striker's nearest settlement: " + f.reconOrders);
            KnowledgeGameTests.raids(t, f, now + 1);
            t.assertTrue(f.reconOrders.size() == 2 && orderOn(f, ember) == null && orderOn(f, groveFar) == null,
                    "more than two orders open: " + f.reconOrders);
            // the lair's report turns fresh by other means: its order is dropped and the next ordinary one is placed, on the
            // nearest base never judged that no order is open on: not the striker's nearer settlement, which has the urgent one
            f.addReport(recon(lair, true, now + 2, 4));
            KnowledgeGameTests.raids(t, f, now + 3);
            t.assertTrue(f.reconOrders.size() == 2 && orderOn(f, lair) == null && orderOn(f, ember) != null && !orderOn(f, ember).urgent
                    && orderOn(f, grove) == urgent, "the next ordinary order: " + f.reconOrders);
            // which target first: every base never judged before a stale one; among the stale, the weakest, then the nearest
            f.reconOrders.clear();
            f.struckBy.clear();
            f.knowledge.removeIf(Report::base);
            Report fresh = reported(t, f, Report.Kind.ENEMY_BASE, ENEMY, 100, now);
            f.addReport(recon(fresh, true, now - 100, 1));
            Report strong = reported(t, f, Report.Kind.ENEMY_BASE, FactionType.EMBER_LEGION, 150, now);
            f.addReport(recon(strong, true, now - ReconRules.FRESH_TICKS - 1, 5));
            Report weak = reported(t, f, Report.Kind.ENEMY_BASE, FactionType.STONEGUARD, 400, now);
            f.addReport(recon(weak, true, now - ReconRules.FRESH_TICKS - 1, 1));
            Report unseen = reported(t, f, Report.Kind.ENEMY_BASE, ENEMY, 700, now);
            KnowledgeGameTests.raids(t, f, now + 4);
            t.assertTrue(f.reconOrders.size() == 1 && orderOn(f, unseen) != null, "a base never judged first, even the farthest: "
                    + f.reconOrders);
            f.reconOrders.clear();
            f.addReport(recon(unseen, true, now + 4, 2));
            KnowledgeGameTests.raids(t, f, now + 5);
            t.assertTrue(f.reconOrders.size() == 1 && orderOn(f, weak) != null, "the weakest stale base before a nearer, stronger one: "
                    + f.reconOrders);
            t.assertTrue(orderOn(f, fresh) == null, "a fresh base ordered");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the endings

    /**
     * recon.md 2.1's table, every ending, by what the faction knows (its scouts' records, what they told at the station,
     * its reports): a good recon delivered ends the order; a blind one ends it as one failed attempt (counted once, when the
     * report was filed); a scout home with no recon of the target, or missing, presumed or known dead, or one the roster
     * does not know, ends it as a failed attempt (the target backs off); a retrained scout opens it again for another, with
     * no failure; one nobody took is dropped after half a day (not a tick sooner), when the target turns fresh by other
     * means, or when the target is seen in ruins; a taken order on ruins waits for its scout. Outside the window, with no
     * strike and no party, the pass only settles.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "recon_orders_endings")
    public static void everyReconOrderEnds(GameTestHelper h) {
        GameTestBases.Handle t = KnowledgeGameTests.base(h, 40);
        FactionState f = fresh(t);
        try {
            long now = h.getLevel().getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - 2L * interval;
            f.lastRaidAt = now; // the window opens at now + interval - 12000: every pass here is before it
            t.assertTrue(interval - ReconRules.LEAD_TICKS > ReconRules.FRESH_TICKS + 500, "the raid interval is too short for this test");
            Report[] base = new Report[12];
            for (int i = 0; i < base.length; i++) {
                base[i] = reported(t, f, Report.Kind.ENEMY_BASE, ENEMY, 200 + 100 * i, now);
            }
            UUID[] scout = new UUID[base.length];
            for (int i = 0; i < scout.length; i++) {
                scout[i] = t.absentWorker(NpcRole.SCOUT);
            }
            // a good recon, and a blind one
            long at = now + 100;
            ReconOrder done = order(t, f, base[0], now), blind = order(t, f, base[1], now);
            ReconOrders.take(done, scout[0], now + 10);
            ReconOrders.take(blind, scout[1], now + 10);
            Report good = recon(base[0], true, at, 3), dark = recon(base[1], false, at, 0);
            t.assertTrue(ReconOrders.atStation(f, scout[0], good) && ReconOrders.atStation(f, scout[1], dark), "no order taken by the scouts");
            KnowledgeGameTests.deliver(t, f, good, at);
            KnowledgeGameTests.deliver(t, f, dark, at);
            KnowledgeGameTests.raids(t, f, at);
            t.assertTrue(f.reconOrders.isEmpty(), "an order its scout answered is still open: " + f.reconOrders);
            t.assertTrue(kept(f, base[0]).recon != null && kept(f, base[0]).recon.soldiers() == 3 && kept(f, base[0]).reconFailures == 0,
                    "the good recon: " + kept(f, base[0]).recon);
            t.assertTrue(kept(f, base[1]).recon == null && kept(f, base[1]).reconFailures == 1, "a blind recon is one failed attempt, not "
                    + kept(f, base[1]).reconFailures);
            // home with no recon of the target (the sides it tried told), and missing
            at = now + 200;
            ReconOrder empty = order(t, f, base[2], now), missing = order(t, f, base[3], now);
            ReconOrders.take(empty, scout[2], now + 10);
            ReconOrders.take(missing, scout[3], now + 10);
            ReconOrders.atStation(f, scout[2], KnowledgeGameTests.delivered(Report.Kind.ENEMY_BASE, base[2].pos, ENEMY, at, null,
                    ReconRules.Side.EAST.bit()));
            f.roster.get(scout[3]).where(State.MISSING, at);
            KnowledgeGameTests.raids(t, f, at);
            t.assertTrue(f.reconOrders.isEmpty(), "an empty-handed or missing scout's order is still open: " + f.reconOrders);
            t.assertTrue(kept(f, base[2]).reconFailures == 1 && kept(f, base[2]).reconFailedAt == at
                    && ReconRules.Side.EAST.in(kept(f, base[2]).reconSidesTried), "an empty-handed scout: " + kept(f, base[2]).reconFailures
                    + " failures, sides " + kept(f, base[2]).reconSidesTried);
            t.assertTrue(kept(f, base[3]).reconFailures == 1 && ReconRules.backingOff(kept(f, base[3]).reconFile(), at + 1),
                    "a missing scout's target does not back off: " + kept(f, base[3]).reconFailures);
            // presumed dead, known dead, and a scout the roster does not know
            at = now + 300;
            ReconOrder presumed = order(t, f, base[4], now), dead = order(t, f, base[5], now), unknown = order(t, f, base[6], now);
            ReconOrders.take(presumed, scout[4], now + 10);
            ReconOrders.take(dead, scout[5], now + 10);
            ReconOrders.take(unknown, UUID.randomUUID(), now + 10);
            f.roster.get(scout[4]).where(State.PRESUMED_DEAD, at);
            f.roster.get(scout[5]).where(State.KNOWN_DEAD, at);
            KnowledgeGameTests.raids(t, f, at);
            t.assertTrue(f.reconOrders.isEmpty(), "a lost scout's order is still open: " + f.reconOrders);
            for (int i = 4; i <= 6; i++) {
                t.assertTrue(kept(f, base[i]).reconFailures == 1, "a lost scout is no failed attempt at base " + i);
            }
            // retrained: open again for another scout, no failure; on its way: open, its line says so
            at = now + 400;
            ReconOrder retrained = order(t, f, base[7], now), away = order(t, f, base[8], now);
            ReconOrders.take(retrained, scout[7], now + 10);
            ReconOrders.take(away, scout[8], now + 10);
            f.roster.get(scout[7]).role = NpcRole.MINER;
            f.roster.get(scout[8]).where(State.AWAY, now + 20);
            KnowledgeGameTests.raids(t, f, at);
            t.assertTrue(f.reconOrders.size() == 2 && f.reconOrders.contains(retrained) && !retrained.taken() && retrained.takenAt == 0
                    && kept(f, base[7]).reconFailures == 0, "a retrained scout's order: " + f.reconOrders);
            t.assertTrue(f.reconOrders.contains(away) && away.taken(), "a scout on its way lost its order: " + f.reconOrders);
            String line = FactionDirector.LAST_COUNCIL.get(SIDE);
            t.assertTrue(line != null && line.contains("Absent Scout on the way") && line.contains("not taken yet"), "the order line: " + line);
            // untaken: dropped once the target turned fresh by other means; at half a day, not a tick sooner
            f.reconOrders.remove(away);
            ReconOrder waiting = order(t, f, base[9], now);
            f.addReport(recon(base[7], true, now + 450, 2)); // another scout's recon, made after the order
            KnowledgeGameTests.raids(t, f, now + ReconRules.FRESH_TICKS - 1);
            t.assertTrue(f.reconOrders.size() == 1 && f.reconOrders.contains(waiting), "the orders a tick before half a day: " + f.reconOrders);
            KnowledgeGameTests.raids(t, f, now + ReconRules.FRESH_TICKS);
            t.assertTrue(f.reconOrders.isEmpty(), "an order nobody took for half a day: " + f.reconOrders);
            // ruins: an untaken order is dropped; a taken one waits for its scout
            at = now + ReconRules.FRESH_TICKS + 10;
            ReconOrder ruinsUntaken = order(t, f, base[10], at), ruinsTaken = order(t, f, base[11], at);
            ReconOrders.take(ruinsTaken, scout[11], at);
            f.roster.get(scout[11]).where(State.AWAY, at);
            for (int i = 10; i <= 11; i++) {
                Report ruins = new Report(Report.Kind.ENEMY_BASE, base[i].pos, at, ENEMY, 0, "ruins");
                ruins.fallen = true;
                KnowledgeGameTests.deliver(t, f, ruins, at);
            }
            KnowledgeGameTests.raids(t, f, at + 1);
            t.assertTrue(f.reconOrders.size() == 1 && f.reconOrders.contains(ruinsTaken), "the orders on ruins: " + f.reconOrders);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the urgent order

    /**
     * recon.md 2.1 and 6: an enemy faction that struck us since our last raid gets an urgent recon of its settlement nearest
     * our base, outside the ordinary window, once the counter-attack window is open (a quarter of the interval since our
     * last raid) and our alarm has been quiet half a minute (the fight is over: an intruder noticed, a monster too, or a
     * strike seen). Not for a strike before our last raid; at most once a quarter interval per faction; not again after a
     * good recon made since the strike; not while the council waits for its raid's news (the strike is acted on after it).
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "recon_orders_urgent")
    public static void anUrgentReconOnceTheFightAtHomeIsOver(GameTestHelper h) {
        GameTestBases.Handle t = KnowledgeGameTests.base(h, 40);
        FactionState f = fresh(t);
        try {
            long now = h.getLevel().getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            long gap = ReconRules.urgentGap(interval);
            t.assertTrue(interval - ReconRules.LEAD_TICKS > 2 * gap + 1000, "the raid interval is too short for this test");
            t.data.startedAt = now - 2L * interval;
            List<FactionNpc> soldiers = garrison(t);
            Report ember = reported(t, f, Report.Kind.ENEMY_BASE, FactionType.EMBER_LEGION, 150, now);
            Report grove = reported(t, f, Report.Kind.ENEMY_BASE, ENEMY, 200, now);
            Report groveFar = reported(t, f, Report.Kind.ENEMY_BASE, ENEMY, 400, now);
            // a monster in our yard just now: the alarm is not quiet yet, though the strike was long ago
            f.lastRaidAt = now - gap - 1000;
            f.struck(ENEMY, now - 5000);
            Zombie zombie = t.track(h.spawn(EntityType.ZOMBIE, new BlockPos(12, 2, 13)));
            zombie.setNoAi(true);
            FactionDirector.defenceAlarm(h.getLevel(), t.base);
            zombie.discard();
            KnowledgeGameTests.raids(t, f, now + 100);
            t.assertTrue(f.reconOrders.isEmpty(), "an urgent order while a monster was in our yard: " + f.reconOrders);
            KnowledgeGameTests.raids(t, f, now + ReconRules.QUIET_TICKS);
            t.assertTrue(f.reconOrders.size() == 1 && orderOn(f, grove) != null && orderOn(f, grove).urgent,
                    "no urgent order once the alarm was quiet: " + f.reconOrders);
            // a strike before our last raid; then one since, but the counter-attack window is still shut
            f.reconOrders.clear();
            f.reconUrgentAt.clear();
            f.lastRaidAt = now + 1000;
            long open = f.lastRaidAt + gap;
            f.struckBy.put(ENEMY, now + 999);
            KnowledgeGameTests.raids(t, f, open);
            t.assertTrue(f.reconOrders.isEmpty(), "an urgent order for a strike before our last raid: " + f.reconOrders);
            f.struckBy.put(ENEMY, open - 300);
            KnowledgeGameTests.raids(t, f, open - 1);
            t.assertTrue(f.reconOrders.isEmpty(), "an urgent order before the counter-attack window: " + f.reconOrders);
            KnowledgeGameTests.raids(t, f, open);
            t.assertTrue(f.reconOrders.isEmpty(), "an urgent order while the fight at home is on: " + f.reconOrders);
            long placed = open + 300;
            KnowledgeGameTests.raids(t, f, placed);
            ReconOrder urgent = orderOn(f, grove);
            String line = FactionDirector.LAST_COUNCIL.get(SIDE);
            t.assertTrue(f.reconOrders.size() == 1 && urgent != null && urgent.urgent && f.urgentOrderedAt(ENEMY) == placed
                    && orderOn(f, groveFar) == null && orderOn(f, ember) == null, "the urgent order on the striker's nearest settlement, "
                    + "and no ordinary one outside the window: " + f.reconOrders);
            t.assertTrue(line != null && line.contains("urgent recon ordered day"), "the urgent order's line: " + line);
            // its scout brings a good recon: no new urgent order for that strike
            UUID scout = t.absentWorker(NpcRole.SCOUT);
            ReconOrders.take(urgent, scout, placed + 10);
            Report good = recon(grove, true, placed + 2000, 2);
            ReconOrders.atStation(f, scout, good);
            KnowledgeGameTests.deliver(t, f, good, placed + 2500);
            KnowledgeGameTests.raids(t, f, placed + 2500);
            t.assertTrue(f.reconOrders.isEmpty(), "a new order after the good recon that answered the strike: " + f.reconOrders);
            // a new strike inside the quarter interval since the last urgent order: none until the quarter has passed
            f.struck(ENEMY, placed + 3000);
            KnowledgeGameTests.raids(t, f, placed + gap - 1);
            t.assertTrue(f.reconOrders.isEmpty(), "two urgent orders within a quarter interval: " + f.reconOrders);
            KnowledgeGameTests.raids(t, f, placed + gap);
            t.assertTrue(f.reconOrders.size() == 1 && orderOn(f, grove) != null && orderOn(f, grove).urgent,
                    "no urgent order a quarter interval later: " + f.reconOrders);
            // a strike seen while the council waits for its raid's news is acted on once the news is in
            f.reconOrders.clear();
            f.reconUrgentAt.clear();
            long later = placed + gap + 10;
            f.struck(ENEMY, later - 700);
            f.raidEndNews = UUID.randomUUID();
            f.lastParty.add(soldiers.get(0).getUUID());
            f.raidStartedAt = later;
            f.raidPlanTicks = 100_000;
            KnowledgeGameTests.raids(t, f, later);
            t.assertTrue(f.reconOrders.isEmpty(), "an urgent order while the last raid is out: " + f.reconOrders);
            f.raidEndNews = null;
            KnowledgeGameTests.raids(t, f, later + 1);
            t.assertTrue(f.reconOrders.size() == 1 && orderOn(f, grove) != null && orderOn(f, grove).urgent,
                    "no urgent order after the raid's end: " + f.reconOrders);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
