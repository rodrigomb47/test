package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.FactionDirector;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.campaign.ReconOrders;
import com.factionclash.campaign.Report;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.campaign.ReconRules;
import com.factionclash.logic.campaign.ReconRules.Side;
import com.factionclash.logic.campaign.Whereabouts;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.storage.FactionStorage;
import com.factionclash.util.Messages;
import com.factionclash.work.ReconVantage;
import com.factionclash.work.ScoutJob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The fixes of the focused reconnaissance round's two reviews ({@code docs/design/recon.md} 17): the council judges every
 * target it may raid, not only the one with the fewest heads; a scout that lost its trip gives its order back; a scout
 * given up as lost that comes home late counts its attempt once; after an errand home a scout done at the target reports
 * instead of walking back out to its corners. The trips run in the 32 x 32 {@code mobility_field} of
 * {@link ReconTripGameTests}, with {@code skyAccess = true}.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class ReconReviewGameTests {
    private ReconReviewGameTests() {
    }

    private static final String FIELD = HeroMobilityGameTests.FIELD;

    // ------------------------------------------------------------------ the council judges every candidate

    /**
     * Review finding 1: the council ranked its targets by the plan's heads in each target's own soldiers, judged only the
     * first and waited on it while another it could beat stood by. Our three soldiers carry iron swords. Target A's recon
     * counted 2 soldiers in better gear than ours (plan 3 of them: two thirds is 2 of theirs, more than our 3 can match);
     * target B's counted 4 bare soldiers (plan 6, two thirds 4 bare ones: our 3 match them). A alone: the council waits on
     * A. A and B: it raids B, though A has the fewer heads and is nearer.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "recon_review_two_gears")
    public static void theCouncilRaidsATargetItCanBeatWhileAnotherMustWait(GameTestHelper h) {
        GameTestBases.Handle t = KnowledgeGameTests.base(h, 40);
        ServerLevel level = h.getLevel();
        FactionType side = KnowledgeGameTests.SIDE;
        FactionState f = t.data.faction(side);
        KnowledgeGameTests.keepRaidState(t, f);
        try {
            KnowledgeGameTests.atLeast(level, 100_000);
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - 2L * interval;
            f.lastRaidAt = now - interval - 1; // the interval is over
            f.raidTarget = -1;
            f.raiders.clear();
            f.lastParty.clear();
            f.raidEndNews = null;
            FactionDirector.LAST_COUNCIL.remove(side);
            List<FactionNpc> ours = new ArrayList<>();
            for (int[] p : KnowledgeGameTests.GARRISON) {
                FactionNpc n = KnowledgeGameTests.member(t, p[0], p[1], NpcRole.SWORDSMAN);
                n.equip(new ItemStack(Items.IRON_SWORD));
                ours.add(n);
            }
            double gear = KnowledgeGameTests.power(ours.get(0));
            for (FactionNpc n : ours) {
                t.assertTrue(KnowledgeGameTests.power(n) == gear, "our soldiers are not all in the same gear");
            }
            t.assertTrue(3 * gear >= 4.0, "three of our soldiers should match four bare ones: " + gear);
            BaseState a = KnowledgeGameTests.enemyBase(t, KnowledgeGameTests.ENEMY, t.base.center.offset(150, 0, 0));
            BaseState b = KnowledgeGameTests.enemyBase(t, FactionType.EMBER_LEGION, t.base.center.offset(0, 0, 300));
            KnowledgeGameTests.deliverRecon(t, f, a, KnowledgeGameTests.counted(2, 1.7 * gear, now - 100, java.util.Map.of()), now - 100);
            KnowledgeGameTests.raids(t, f, now);
            String council = FactionDirector.LAST_COUNCIL.get(side);
            t.assertTrue(f.raidTarget < 0 && council != null && council.contains("waiting on the Grove Covenant settlement: 3 soldiers to send, 4 needed"),
                    "A alone should hold the council back: " + council);
            KnowledgeGameTests.deliverRecon(t, f, b, KnowledgeGameTests.counted(4, 1.0, now - 50, java.util.Map.of()), now - 50);
            KnowledgeGameTests.raids(t, f, now + 1);
            t.assertTrue(f.raidTarget == b.id && f.raiders.size() == 3, "the council waited on A while it could beat B: "
                    + FactionDirector.LAST_COUNCIL.get(side));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ an order whose trip was lost

    /**
     * Review finding 2: a scout that took an order and lost its trip (its job rebuilt by a retraining away and back between
     * two council passes, or a trip that did not load) kept the order for good: it explored, its report never told the
     * order anything, and the faction's one ordinary order stayed full. Now it gives the order back at home and takes it
     * again at once, as an order whose scout was retrained is open again for another: a new trip, and a good recon.
     */
    @GameTest(template = FIELD, timeoutTicks = 4000, batch = "recon_review_lost_trip", skyAccess = true)
    public static void aScoutThatLostItsTripTakesItsOrderAgain(GameTestHelper h) {
        ReconTripGameTests.Field fl = ReconTripGameTests.field(h, 4000, 4, true);
        GameTestBases.Handle t = fl.t();
        try {
            ReconTripGameTests.defender(t, 17, 21);
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        UUID scout = fl.scout().getUUID();
        long[] rebuilt = {-1}, firstTaken = {-1};
        ReconTripGameTests.Trip trip = new ReconTripGameTests.Trip();
        t.onEachTick(() -> {
            long now = h.getLevel().getGameTime();
            ScoutJob job = fl.job();
            if (rebuilt[0] < 0) {
                if (fl.order().taken() && !job.reconPhase().equals("none")) {
                    // retrained away and back before the next council pass: its job (and the trip it held) is new
                    firstTaken[0] = fl.order().takenAt;
                    fl.scout().role(NpcRole.SWORDSMAN);
                    fl.scout().role(NpcRole.SCOUT);
                    t.assertTrue(fl.job() != job && fl.job().reconPhase().equals("none"), "the retraining should rebuild its job");
                    t.assertTrue(fl.f().reconOrders.contains(fl.order()) && scout.equals(fl.order().scout), "the order is still its");
                    rebuilt[0] = now;
                }
                return;
            }
            if (now - rebuilt[0] == 400) {
                t.assertTrue(scout.equals(fl.order().scout) && fl.order().takenAt > firstTaken[0] && !fl.job().reconPhase().equals("none"),
                        "a scout that lost its trip should take its order again: taken by " + fl.order().scout + " at " + fl.order().takenAt
                                + " (first " + firstTaken[0] + "), trip " + fl.job().reconPhase() + ", " + fl.job().stateName());
            }
            ReconTripGameTests.follow(fl, trip);
            if (trip.filed == null) {
                return;
            }
            t.assertTrue(trip.reported == ReconRules.ScoutReport.GOOD && trip.filed.recon != null && trip.filed.recon.good(),
                    "the new trip should bring a good recon: " + trip.reported + ", " + trip.filed.recon);
            t.succeed();
        });
    }

    // ------------------------------------------------------------------ a scout given up as lost, home late

    /**
     * Review findings 4 and 8: a scout the roll call gave up as missing ended its order as lost (one failed attempt), then
     * came home and delivered a blind recon of the same trip, which counted a second failure: the back-off doubled for one
     * attempt. The scout of {@code aBlindReconIsDeliveredAndBacksTheTargetOff} (a ring 14 high) is reported missing once it
     * has tried the west side, and the council's next pass ends its order as lost. When it comes home with its blind
     * report, the target still counts one failure, at the order's end, and both sides tried.
     */
    @GameTest(template = FIELD, timeoutTicks = 6000, batch = "recon_review_lost_then_blind", skyAccess = true)
    public static void aScoutGivenUpAsLostCountsItsAttemptOnce(GameTestHelper h) {
        ReconTripGameTests.Field fl = ReconTripGameTests.field(h, 6000, 14, true);
        GameTestBases.Handle t = fl.t();
        List<Messages.Said> lines = KnowledgeGameTests.recording(t);
        long[] lostAt = {-1};
        t.onEachTick(() -> {
            long now = h.getLevel().getGameTime();
            ScoutJob job = fl.job();
            if (lostAt[0] < 0) {
                if ((job.reconTried() & Side.WEST.bit()) != 0 && !job.reconPhase().startsWith("DONE")) {
                    NpcRecord rec = t.data.record(fl.scout().getUUID());
                    rec.where(Whereabouts.State.MISSING, now); // (in the same tick as the pass: the roll call cannot see it back first)
                    ReconOrders.settle(fl.f(), now);
                    Report kept = fl.kept();
                    t.assertTrue(!fl.f().reconOrders.contains(fl.order()) && kept.reconFailures == 1 && kept.reconFailedAt == now,
                            "a missing scout's order ends as lost, one failure: " + kept.reconFailures + " at " + kept.reconFailedAt);
                    lostAt[0] = now;
                }
                return;
            }
            if (!KnowledgeGameTests.said(lines, ReconTripGameTests.SIDE, "could not see inside the walls")) {
                return;
            }
            Report kept = fl.kept();
            t.assertTrue(kept.recon == null && kept.reconFailures == 1 && kept.reconFailedAt == lostAt[0],
                    "one trip, one failed attempt: failures " + kept.reconFailures + " at " + kept.reconFailedAt + " (lost at " + lostAt[0] + ")");
            t.assertTrue(kept.reconSidesTried == (Side.WEST.bit() | Side.EAST.bit()), "the sides its trip tried still count: " + kept.reconSidesTried);
            t.succeed();
        });
    }

    // ------------------------------------------------------------------ an errand home after the watch

    /**
     * Review finding 6, on its pillar: the scout watches the west side (home lies mostly north of the target, so its way
     * out goes round by a corner). Past half a minute of its watch it is hungry with nothing in its pack, and food waits
     * in the storehouse: it comes down, walks home and eats. Then it reports at the station with its good recon and never
     * sets out again for the target's corners.
     */
    @GameTest(template = FIELD, timeoutTicks = 4000, batch = "recon_review_errand_pillar", skyAccess = true)
    public static void anErrandHomeFromThePillarEndsInTheReport(GameTestHelper h) {
        errandAfterTheWatch(h, true);
    }

    /** Review finding 6, on its way round: the scout is hungry as it walks round the target to the side facing home. */
    @GameTest(template = FIELD, timeoutTicks = 4000, batch = "recon_review_errand_round", skyAccess = true)
    public static void anErrandHomeOnTheWayRoundEndsInTheReport(GameTestHelper h) {
        errandAfterTheWatch(h, false);
    }

    static void errandAfterTheWatch(GameTestHelper h, boolean onPillar) {
        ReconTripGameTests.Field fl = ReconTripGameTests.field(h, 4000, 4, true);
        GameTestBases.Handle t = fl.t();
        try {
            ReconTripGameTests.defender(t, 17, 21);
            // bread in the storehouse: it sees it there before it sets out (a scout away knows the storehouse as it left it)
            FactionStorage.insert(h.getLevel(), t.base, new ItemStack(Items.BREAD, 8));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        ReconTripGameTests.Trip trip = new ReconTripGameTests.Trip();
        long[] hungryAt = {-1}, ateAt = {-1};
        boolean[] wentHome = {false};
        BlockPos storage = t.base.point(BaseState.STORAGE_STAND);
        t.onEachTick(() -> {
            ReconTripGameTests.follow(fl, trip);
            ScoutJob job = fl.job();
            FactionNpc n = fl.scout();
            long now = h.getLevel().getGameTime();
            if (hungryAt[0] < 0) {
                boolean due = onPillar ? trip.sawWatch && now - trip.watchFrom >= 700 && job.reconPhase().equals("PILLAR/WATCH")
                        : job.reconPhase().startsWith("DONE") && job.reconWaypointsLeft() > 0;
                if (!due) {
                    return;
                }
                // the side it finished on is not the one facing home: its way out goes round by a corner
                BaseState target = fl.target();
                int hx = t.base.center.getX() - target.center.getX(), hz = t.base.center.getZ() - target.center.getZ();
                int fx = n.getBlockX() - target.center.getX(), fz = n.getBlockZ() - target.center.getZ();
                t.assertTrue(!ReconRules.round(fx, fz, ReconRules.region(hx, hz), ReconVantage.band(target).standoff(), hx, hz).isEmpty(),
                        "the test needs a side whose way out goes round the base");
                for (int i = 0; i < n.inventory().getContainerSize(); i++) {
                    if (FactionNpc.isMeal(n.inventory().getItem(i), n)) {
                        n.inventory().setItem(i, ItemStack.EMPTY); // it has eaten what it packed
                    }
                }
                n.hunger().set(4, 0, 0);
                hungryAt[0] = now;
                return;
            }
            if (!wentHome[0] && n.blockPosition().closerThan(storage, 3)) {
                wentHome[0] = true;
            }
            // home on its errand, its trip is over: never again on its way out to the target's corners (in this small field
            // the corner lies by home, so the walk back out is short; the state says it)
            t.assertTrue(!wentHome[0] || trip.filed != null || !job.stateName().equals("OUTBOUND"), "home on its errand it is still "
                    + "out on its trip, bound for the target's corners: " + job.stateName() + " " + job.reconPhase() + ", "
                    + job.reconWaypointsLeft() + " corners left");
            if (ateAt[0] < 0) {
                if (n.hunger().foodLevel() > 6) {
                    t.assertTrue(wentHome[0], "it should have gone home to eat: " + job.stateName() + " " + job.reconPhase());
                    ateAt[0] = now; // it ate at home
                }
                return;
            }
            if (trip.filed == null) {
                return;
            }
            t.assertTrue(trip.reported == ReconRules.ScoutReport.GOOD && trip.filed.recon != null && trip.filed.recon.good(),
                    "its good recon reported after the errand: " + trip.reported + ", " + trip.filed.recon);
            t.succeed();
        });
    }
}
