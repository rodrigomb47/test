package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseWatch;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.FactionDirector;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.campaign.Report;
import com.factionclash.campaign.RollCall;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.logic.campaign.Intel;
import com.factionclash.logic.campaign.ReconRules;
import com.factionclash.logic.campaign.ReconWatch;
import com.factionclash.logic.campaign.Whereabouts;
import com.factionclash.logic.campaign.Whereabouts.State;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import com.factionclash.util.Messages;
import com.factionclash.work.CaveExplorerJob;
import com.factionclash.work.Job;
import com.factionclash.work.MinerJob;
import com.factionclash.work.ScoutJob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * A faction's AI knows only what its own people know (knowledge review of 2026-11-19, {@code docs/design/knowledge.md}):
 * the war council judges an enemy garrison only by a focused recon its scouts brought home ({@code docs/design/recon.md},
 * task R5: a passing look, a raid party's look and the soldiers seen falling at our walls make no estimate), plans against
 * what the scout counted and a reserve, keeps believing a base stands until one of its people sees the ruins; a raid nobody lived to report on is taken as lost by its plan; a miner's trip is
 * planned at the storehouse; a cave survey is the faction's only when the explorer hands it in; retraining and the
 * blacksmith see only members at home; experience picked up away is carried home; the alarm needs someone to see the
 * intruder; the lookout's line is heard only at home; a lair misses a monster that fell far off only later. Each test
 * registers a small test base (walls half 2, work radius 3: its home area reaches 6 blocks from the centre at relative
 * (12, 2, 12)) and has its own batch.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class KnowledgeGameTests {
    private KnowledgeGameTests() {
    }

    static final FactionType SIDE = FactionType.TIDE_ALLIANCE, ENEMY = FactionType.GROVE_COVENANT;
    /** Inside the test base's home area, and outside it (Chebyshev 9 from the centre; the edge at 6). */
    private static final int HOME_X = 12, HOME_Z = 15, FAR_X = 12, FAR_Z = 21;
    /** Our soldiers at home, inside the walls' box the council drafts from. */
    static final int[][] GARRISON = {{10, 10}, {11, 10}, {13, 10}, {14, 10}, {10, 14}, {14, 14}};

    static GameTestBases.Handle base(GameTestHelper h, int timeout) {
        return GameTestBases.register(h, SIDE, 2, 3, timeout);
    }

    static FactionNpc member(GameTestBases.Handle t, int x, int z, NpcRole role) {
        FactionNpc n = t.npc(x, z, role);
        n.setNoAi(true);
        return n;
    }

    /** An NPC of another faction (no roster entry) at relative (x, 2, z); the cleanup discards it. */
    private static FactionNpc stranger(GameTestBases.Handle t, FactionType faction, int x, int z, NpcRole role) {
        FactionNpc n = t.helper.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(x, 2, z));
        n.faction(faction);
        n.role(role);
        n.setNoAi(true);
        return t.track(n);
    }

    private static void put(GameTestBases.Handle t, LivingEntity e, int x, int z) {
        Vec3 at = Vec3.atBottomCenterOf(t.helper.absolutePos(new BlockPos(x, 2, z)));
        e.moveTo(at.x, at.y, at.z, e.getYRot(), 0F);
        e.setDeltaMovement(Vec3.ZERO);
    }

    /** Moves a member and lets its faction see where it is now (its record timer's call). */
    private static void goTo(GameTestBases.Handle t, FactionNpc n, int x, int z) {
        put(t, n, x, z);
        RollCall.sighting(n, t.data.record(n.getUUID()));
    }

    private static void kill(LivingEntity e) {
        e.invulnerableTime = 0;
        e.hurt(e.damageSources().genericKill(), Float.MAX_VALUE);
    }

    /** An enemy settlement record at relative (x, 2, z) with a tiny walls box; the cleanup removes it. */
    static BaseState enemyBase(GameTestBases.Handle t, FactionType faction, BlockPos center) {
        BaseState b = new BaseState();
        b.faction = faction;
        b.kind = BaseState.Kind.SETTLEMENT;
        b.center = center;
        b.name = "Knowledge test " + faction.id;
        b.testHalf = 1;
        b.testRadius = 2;
        t.data.addBase(b);
        t.onCleanup(() -> t.data.bases.remove(b.id));
        return b;
    }

    /** A look of ours delivered home (as a scout's report reaches the station). */
    static void deliver(GameTestBases.Handle t, FactionState f, Report r, long now) {
        r.deliveredAt = now;
        r.by = "a test scout";
        f.addReport(r);
    }

    /**
     * The raid fields the director's council touches, restored by the cleanup; the faction's reports of {@code about} removed.
     * The council starts from a faction that knows no enemy base and has no recon order, strike or urgent order (the world's
     * faction state is shared by every test, and a base report another test or an older run left would draw a recon order,
     * recon.md 2.1, into this test's council line); the cleanup restores them too.
     */
    static void keepRaidState(GameTestBases.Handle t, FactionState f) {
        long oldStarted = t.data.startedAt, oldLast = f.lastRaidAt, oldRaid = f.raidStartedAt, oldRally = f.raidRally,
                oldMuster = f.raidMusteredAt, oldPlan = f.raidPlanTicks;
        int oldLaunched = f.raidsLaunched, oldParty = f.raidParty;
        float oldPower = f.soldierPower;
        List<Report> oldKnowledge = new ArrayList<>(f.knowledge);
        java.util.Map<FactionType, Long> oldStruck = new java.util.HashMap<>(f.struckBy);
        List<com.factionclash.campaign.ReconOrder> oldOrders = new ArrayList<>(f.reconOrders);
        java.util.Map<FactionType, Long> oldUrgent = new java.util.HashMap<>(f.reconUrgentAt);
        t.onCleanup(() -> {
            t.data.startedAt = oldStarted;
            f.lastRaidAt = oldLast;
            f.raidStartedAt = oldRaid;
            f.raidRally = oldRally;
            f.raidMusteredAt = oldMuster;
            f.raidPlanTicks = oldPlan;
            f.raidsLaunched = oldLaunched;
            f.raidParty = oldParty;
            f.soldierPower = oldPower;
            f.raidTarget = -1;
            f.raiders.clear();
            f.lastParty.clear();
            f.raidEndNews = null;
            f.raidLast = null;
            f.raidIntel = null;
            f.knowledge.clear();
            f.knowledge.addAll(oldKnowledge);
            f.struckBy.clear();
            f.struckBy.putAll(oldStruck);
            f.reconOrders.clear();
            f.reconOrders.addAll(oldOrders);
            f.reconUrgentAt.clear();
            f.reconUrgentAt.putAll(oldUrgent);
            FactionDirector.LAST_COUNCIL.remove(f.type);
        });
        f.knowledge.removeIf(Report::base);
        f.struckBy.clear();
        f.reconOrders.clear();
        f.reconUrgentAt.clear();
    }

    /** The director's war council (private), run as the director runs it for the base. */
    static void raids(GameTestBases.Handle t, FactionState f, long at) {
        call(FactionDirector.class, null, "raids", new Class<?>[]{ServerLevel.class, CampaignData.class, FactionState.class,
                BaseState.class, long.class}, t.helper.getLevel(), t.data, f, t.base, at);
    }

    private static Object call(Class<?> c, Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method m = c.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e.getCause() != null ? e.getCause() : e);
        }
    }

    private static void set(Class<?> c, Object target, String name, Object value) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object get(Class<?> c, Object target, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A test run early in a fresh GameTest world (a filtered run) has too little world time for a worker to have been idle
     * for half a day: the world's game time is moved on to {@code ticks} then (never back).
     */
    static void atLeast(ServerLevel level, long ticks) {
        if (level.getGameTime() < ticks && level.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData d) {
            d.setGameTime(ticks);
        }
    }

    static List<Messages.Said> recording(GameTestBases.Handle t) {
        List<Messages.Said> lines = Messages.record();
        t.onCleanup(Messages::stopRecording);
        return lines;
    }

    private static boolean said(List<Messages.Said> lines, FactionType f, String part) {
        synchronized (lines) {
            return lines.stream().anyMatch(l -> l.faction() == f && l.line().contains(part));
        }
    }

    // ------------------------------------------------------------------ the war council (known leak 1)

    /** A good recon of an enemy base counting {@code soldiers} faces of {@code power} each, watched at {@code at}. */
    static Intel.Recon counted(int soldiers, double power, long at, java.util.Map<String, Double> heroes) {
        java.util.Map<java.util.UUID, Double> faces = new java.util.LinkedHashMap<>();
        for (int i = 0; i < soldiers; i++) {
            faces.put(java.util.UUID.randomUUID(), power);
        }
        return recon(12, true, at, faces, heroes);
    }

    /** A recon of {@code b}'s yard delivered home at {@code at} (as a scout's report reaches the station). */
    static void deliverRecon(GameTestBases.Handle t, FactionState f, BaseState b, Intel.Recon r, long at) {
        Report rep = delivered(b.isSettlement() ? Report.Kind.ENEMY_BASE : Report.Kind.LAIR, b.center, b.faction, at, r, ReconRules.Side.WEST.bit());
        f.addReport(rep);
    }

    /** The faction's kept report of {@code b}. */
    static Report kept(FactionState f, BaseState b) {
        return f.knowledge.stream().filter(r -> r.subject == b.faction && r.pos.equals(b.center)).findFirst().orElseThrow();
    }

    /** One of our soldiers' power as our council and an enemy's scout reckon it (the armour worn, the weapon held). */
    static double power(FactionNpc n) {
        return (double) call(FactionDirector.class, null, "power", new Class<?>[]{FactionNpc.class}, n);
    }

    /**
     * recon.md 10.2 item 4 (task R5): known leak 1's live case under the owner's rule (2026-11-25), "Only estimate enemy
     * defence when the scout or other related role makes a report of what he saw while focused on this particilar task".
     * Our scout passes the enemy's yard and sees six soldiers there: its passing look says where the base is and that it
     * stands, and carries no count. The council has no focused report of it: it does not launch, says it waits for a
     * scout's report, and orders a recon. The garrison marches out and a second passing look sees the yard empty: still no
     * launch. A focused recon counting the six makes the council wait on strength (three to send, six needed against six
     * and a reserve of three); one that finds the yard empty lets it go (item 16, its first half: half of none is none).
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "knowledge_garrison")
    public static void noLaunchWithoutAFocusedReport(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 60);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        try {
            t.assertTrue(f.raidTarget < 0, "the faction already has a raid under way");
            List<Messages.Said> lines = recording(t);
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval - 1;
            List<FactionNpc> ours = new ArrayList<>();
            for (int[] p : GARRISON) {
                ours.add(member(t, p[0], p[1], NpcRole.SWORDSMAN));
            }
            BaseState enemy = enemyBase(t, ENEMY, h.absolutePos(new BlockPos(12, 2, 21)));
            List<FactionNpc> garrison = new ArrayList<>();
            int[][] posts = {{11, 20}, {13, 20}, {11, 21}, {13, 21}, {11, 22}, {13, 22}};
            for (int[] p : posts) {
                garrison.add(stranger(t, ENEMY, p[0], p[1], NpcRole.SWORDSMAN));
            }
            FactionNpc scout = member(t, 12, 16, NpcRole.SCOUT);
            Report first = BaseWatch.look(level, scout, enemy, now);
            t.assertTrue(first != null && !first.fallen && first.count == 0 && first.recon == null
                    && first.note.equals(Report.positionNote(ENEMY, Report.Kind.ENEMY_BASE)), "a passing look carries no count: "
                    + (first == null ? null : first.count + " / " + first.note));
            deliver(t, f, first, now);
            raids(t, f, now);
            String council = FactionDirector.LAST_COUNCIL.get(SIDE);
            t.assertTrue(f.raidTarget < 0 && council != null && council.contains("waiting for a scout's report on " + ENEMY.id + " base "
                    + enemy.id) && council.contains("; recon ordered day"), "no focused report: the council should wait for one: " + council);
            t.assertTrue(f.reconOrders.size() == 1 && f.reconOrders.get(0).target.equals(enemy.center), "no recon ordered: " + f.reconOrders);
            // the garrison marches out where none of ours sees it; a second passing look sees the yard empty
            for (FactionNpc g : garrison) {
                g.discard();
            }
            Report second = BaseWatch.look(level, scout, enemy, now + 2);
            t.assertTrue(second != null && second.count == 0 && second.recon == null, "the second passing look: " + (second == null ? null : second.note));
            deliver(t, f, second, now + 2);
            raids(t, f, now + 2);
            t.assertTrue(f.raidTarget < 0 && lines.stream().noneMatch(l -> l.line().contains("Launching a raid")),
                    "a passing look of an empty yard made an estimate: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            // a focused recon of the six, in our soldiers' gear: three to send, six needed against the six and a reserve of three
            deliverRecon(t, f, enemy, counted(6, power(ours.get(0)), now + 3, java.util.Map.of()), now + 3);
            raids(t, f, now + 4);
            council = FactionDirector.LAST_COUNCIL.get(SIDE);
            t.assertTrue(f.raidTarget < 0 && council != null && council.contains("base " + enemy.id + " (settlement)") && council.contains("wait")
                    && council.contains("waiting on the Grove Covenant settlement: 3 soldiers to send, 6 needed against about 6 defenders"
                    + " (scout Edrin Cole, day " + Whereabouts.day(now + 3) + ") and a reserve of 3 (half again the 6 counted)"),
                    "three against six counted and a reserve of three should wait: " + council);
            // the yard watched empty: the council goes with its smallest party
            deliverRecon(t, f, enemy, counted(0, 0, now + 5, java.util.Map.of()), now + 5);
            raids(t, f, now + 6);
            t.assertTrue(f.raidTarget == enemy.id && f.raiders.size() == 3, "the council did not strike the yard its scout watched empty: "
                    + FactionDirector.LAST_COUNCIL.get(SIDE));
            t.assertTrue(said(lines, SIDE, "Launching a raid with 3 soldiers on the Grove Covenant settlement reported at "
                    + enemy.center.toShortString() + ", against an empty yard (scout Edrin Cole, day " + Whereabouts.day(now + 5)
                    + ") and no soldier of theirs seen away"), "the launch line: " + lines);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * recon.md 10.2 item 5 (task R5): enemy soldiers killed at our walls in sight of home no longer lower any estimate.
     * Our scout's fresh recon counted six at the enemy settlement; four of them then fall inside our home area: the kept
     * recon still counts six, and their faction struck us. Inside the interval the council judges only the striker's
     * settlement, and only on a recon made since the strike: the one it has is older, so it waits for a scout's report and,
     * once the alarm has been quiet half a minute, orders an urgent recon of that base. The urgent recon finds two: the
     * counter-attack goes at 3 to 2 on its own plan (the count, no share). A soldier of theirs killed where none of ours
     * saw is no strike.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "knowledge_losses")
    public static void lossesAtOurWallsDoNotMoveTheEstimate(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 60);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        int oldWitness = FCConfig.WITNESS_RANGE.get();
        t.onCleanup(() -> FCConfig.WITNESS_RANGE.set(oldWitness));
        try {
            List<Messages.Said> lines = recording(t);
            atLeast(level, 100_000);
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - 2L * interval;
            f.lastRaidAt = now - interval / 2; // inside the interval: only a counter-attack may go
            for (int[] p : GARRISON) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState enemy = enemyBase(t, ENEMY, h.absolutePos(new BlockPos(12, 2, 21)));
            Intel.Recon six = counted(6, 1.0, now - 100, java.util.Map.of());
            deliverRecon(t, f, enemy, six, now - 100);
            // one dies far from home where none of ours sees it (the scouts see 4 blocks here): no strike
            FCConfig.WITNESS_RANGE.set(4);
            kill(stranger(t, ENEMY, 12, 23, NpcRole.SWORDSMAN));
            t.assertTrue(f.struckAt(ENEMY) == 0, "a death none of ours saw was a strike: " + f.struckBy);
            // four fall inside our home area: a strike by their faction, and the recon untouched
            for (int i = 0; i < 4; i++) {
                kill(stranger(t, ENEMY, 11 + i, 13, NpcRole.SWORDSMAN));
            }
            long struck = f.struckAt(ENEMY);
            t.assertTrue(struck >= now, "the soldiers seen falling at our walls were no strike: " + f.struckBy);
            t.assertTrue(kept(f, enemy).recon == six && kept(f, enemy).recon.soldiers() == 6,
                    "the soldiers seen falling at our walls moved the estimate: " + kept(f, enemy).recon);
            raids(t, f, now + 1);
            String council = FactionDirector.LAST_COUNCIL.get(SIDE);
            t.assertTrue(f.raidTarget < 0 && council != null && council.contains("waiting for a scout's report on " + ENEMY.id
                    + " base " + enemy.id + " (settlement: its last recon is older than their strike)"),
                    "a counter-attack on a recon from before the strike: " + council);
            t.assertTrue(f.reconOrders.isEmpty(), "an urgent order while the fight at home is on: " + f.reconOrders);
            raids(t, f, struck + ReconRules.QUIET_TICKS);
            t.assertTrue(f.reconOrders.size() == 1 && f.reconOrders.get(0).urgent && f.reconOrders.get(0).target.equals(enemy.center),
                    "no urgent recon of the striker's base once the fight was over: " + f.reconOrders);
            // the urgent recon counts the two left at home: 3 to 2 on the count alone goes
            long at = struck + ReconRules.QUIET_TICKS + 500;
            deliverRecon(t, f, enemy, counted(2, 1.0, at, java.util.Map.of()), at);
            raids(t, f, at + 1);
            t.assertTrue(f.raidTarget == enemy.id && said(lines, SIDE, "Counter-attack! Launching a raid with 3 soldiers on the Grove Covenant"
                    + " settlement reported at " + enemy.center.toShortString() + ", against about 2 defenders (scout Edrin Cole, day "
                    + Whereabouts.day(at) + ") and no soldier of theirs seen away"), "no counter-attack on the urgent recon: "
                    + FactionDirector.LAST_COUNCIL.get(SIDE) + " / " + lines);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * recon.md 10.2 items 13 and 15's review (task R5): inside the raid interval the council chooses its target among the
     * settlements of the factions that struck us since our last raid. The Ember Legion's base, a bystander's, has a fresh
     * recon of an empty yard; the Grove Covenant struck us, and its base's recon counts two. Before the strike the council
     * judges nothing inside the interval and writes no line; after it, it waits for a recon made since the strike, then
     * counter-attacks the Grove at 3 to 2 (three against the two counted: no share in a counter-attack's plan), not the
     * weaker bystander. At the interval both are candidates again, and the weaker goes first.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "knowledge_bystander")
    public static void aCounterAttackIsNotBlockedByAWeakerBystander(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 60);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        try {
            List<Messages.Said> lines = recording(t);
            atLeast(level, 100_000);
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - 2L * interval;
            f.lastRaidAt = now - interval / 2; // inside the interval, the counter-attack window open, the order window not yet
            t.assertTrue(interval / 2 > ReconRules.LEAD_TICKS + 1000 && interval / 2 > ReconRules.urgentGap(interval),
                    "the raid interval is too short for this test: " + interval);
            for (int[] p : GARRISON) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState grove = enemyBase(t, ENEMY, t.base.center.offset(200, 0, 0));
            BaseState ember = enemyBase(t, FactionType.EMBER_LEGION, t.base.center.offset(0, 0, 250));
            deliverRecon(t, f, ember, counted(0, 0, now - 100, java.util.Map.of()), now - 100);
            deliverRecon(t, f, grove, counted(2, 1.0, now - 100, java.util.Map.of()), now - 100);
            raids(t, f, now);
            t.assertTrue(f.raidTarget < 0 && FactionDirector.LAST_COUNCIL.get(SIDE) == null,
                    "inside the interval with no strike the council judged something: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            f.struck(ENEMY, now + 10);
            raids(t, f, now + 20);
            String council = FactionDirector.LAST_COUNCIL.get(SIDE);
            t.assertTrue(f.raidTarget < 0 && council != null && council.contains("waiting for a scout's report on " + ENEMY.id + " base " + grove.id),
                    "the striker's base needs a recon made since its strike; the bystander is no candidate: " + council);
            long at = now + 800;
            deliverRecon(t, f, grove, counted(2, 1.0, at, java.util.Map.of()), at);
            raids(t, f, at + 1);
            t.assertTrue(f.raidTarget == grove.id && f.raiders.size() == 3 && said(lines, SIDE, "Counter-attack!"),
                    "the weaker bystander blocked the counter-attack on the striker: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            // at the interval both are candidates again: the weaker base goes first
            f.raidTarget = -1;
            f.raiders.clear();
            f.lastParty.clear();
            f.raidEndNews = null;
            lines.clear();
            f.lastRaidAt = at + 2 - interval - 1;
            raids(t, f, at + 2);
            t.assertTrue(f.raidTarget == ember.id && !said(lines, SIDE, "Counter-attack!"), "at the interval the weaker base should go first: "
                    + FactionDirector.LAST_COUNCIL.get(SIDE));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * recon.md 10.2 item 3, the council's side (task R5; R4b's {@code aBlindReconIsDeliveredAndBacksTheTargetOff} runs the
     * scout): a recon that could not see inside ("could not see inside the walls": 3 of the 25 points), and one that saw
     * twelve points but not the yard's middle (only its corner), are blind. Neither is "about 0": the council does not
     * launch, says it waits for a scout's report on a target backing off, and orders no recon of it until the back-off is
     * over.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_blind")
    public static void aBlindReconIsNotUsed(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        try {
            atLeast(level, 100_000);
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval - 1;
            for (int[] p : GARRISON) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState enemy = enemyBase(t, ENEMY, t.base.center.offset(0, 0, 200));
            deliverRecon(t, f, enemy, recon(3, true, now, java.util.Map.of(), java.util.Map.of()), now);
            t.assertTrue(kept(f, enemy).recon == null && kept(f, enemy).reconFailures == 1, "a blind recon was kept: " + kept(f, enemy).recon);
            raids(t, f, now + 1);
            String council = FactionDirector.LAST_COUNCIL.get(SIDE);
            t.assertTrue(f.raidTarget < 0 && council != null && council.contains("waiting for a scout's report on " + ENEMY.id + " base "
                    + enemy.id + " (settlement: backing off after 1 failed recon)"), "a blind recon was used: " + council);
            t.assertTrue(f.reconOrders.isEmpty(), "a recon ordered while the target backs off: " + f.reconOrders);
            // the yard's corner only: twelve points but not its middle
            deliverRecon(t, f, enemy, recon(12, false, now + 2, java.util.Map.of(), java.util.Map.of()), now + 2);
            raids(t, f, now + 3);
            t.assertTrue(f.raidTarget < 0 && kept(f, enemy).recon == null && kept(f, enemy).reconFailures == 2,
                    "a look without the yard's middle was used: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            long over = now + 2 + ReconRules.backoffTicks(2);
            raids(t, f, over - 1);
            t.assertTrue(f.reconOrders.isEmpty() && f.raidTarget < 0, "a recon ordered before the back-off is over: " + f.reconOrders);
            raids(t, f, over);
            t.assertTrue(f.reconOrders.size() == 1 && f.raidTarget < 0, "no recon ordered after the back-off: " + f.reconOrders);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * recon.md 10.2 item 7 (task R5): a good recon is fresh for half a day (12000 ticks in, 12001 out). An empty yard
     * watched 12001 ticks ago: the council does not launch, says its last recon is stale, and orders a new one. Watched
     * 12000 ticks ago it still goes.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_stale")
    public static void aStaleReportOrdersANewRecon(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        try {
            atLeast(level, 100_000);
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            for (int[] p : GARRISON) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState enemy = enemyBase(t, ENEMY, t.base.center.offset(0, 0, 200));
            deliverRecon(t, f, enemy, counted(0, 0, now, java.util.Map.of()), now);
            long stale = now + ReconRules.FRESH_TICKS + 1;
            t.data.startedAt = stale - interval - 1;
            f.lastRaidAt = stale - interval - 1;
            raids(t, f, stale);
            String council = FactionDirector.LAST_COUNCIL.get(SIDE);
            t.assertTrue(f.raidTarget < 0 && council != null && council.contains("waiting for a scout's report on " + ENEMY.id + " base "
                    + enemy.id + " (settlement: its last recon is stale)"), "a stale recon was used: " + council);
            t.assertTrue(f.reconOrders.size() == 1 && !f.reconOrders.get(0).urgent && f.reconOrders.get(0).target.equals(enemy.center),
                    "no new recon ordered: " + f.reconOrders);
            f.reconOrders.clear();
            raids(t, f, stale - 1);
            t.assertTrue(f.raidTarget == enemy.id, "a recon 12000 ticks old is still fresh: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * recon.md 10.2 item 6 (task R5): a raid party at its target sees the yard full of soldiers; its raiders are attacking,
     * not watching, so no recon results: the party keeps nothing of the yard, the faction's report keeps no recon, and the
     * council does not launch on what its raiders saw. The ruins a party sees still come home: the base razed by another
     * faction while our raider looks on is kept as ruins and the raid ends "found in ruins" (a party that breaks the heart
     * itself: {@link #aPartyThatBreaksTheHeartBringsTheRuinsHome}).
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_partylook")
    public static void theRaidPartysLookIsNotAnEstimate(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        List<Messages.Said> lines = recording(t);
        try {
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval - 1;
            for (int[] p : GARRISON) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState enemy = enemyBase(t, ENEMY, h.absolutePos(new BlockPos(12, 2, 21)));
            deliver(t, f, new Report(Report.Kind.ENEMY_BASE, enemy.center, now, ENEMY, 0, Report.positionNote(ENEMY, Report.Kind.ENEMY_BASE)), now);
            int[][] posts = {{11, 20}, {13, 20}, {11, 22}, {13, 22}};
            for (int[] p : posts) {
                stranger(t, ENEMY, p[0], p[1], NpcRole.SWORDSMAN);
            }
            // our raid on it under way: its raider in full view of the yard
            FactionNpc raider = member(t, 12, 16, NpcRole.SWORDSMAN);
            t.assertTrue(BaseWatch.reconSample(level, raider, enemy, now).soldiers().size() == 4, "the raider does not see the yard's soldiers");
            f.raidTarget = enemy.id;
            f.raiders.add(raider.getUUID());
            f.lastParty.add(raider.getUUID());
            f.raidParty = 1;
            f.raidStartedAt = now;
            f.raidMusteredAt = now;
            f.raidPlanTicks = 100_000;
            raids(t, f, now + 1);
            t.assertTrue(f.raidIntel == null, "the raid party kept a look at the yard: " + (f.raidIntel == null ? null : f.raidIntel.note));
            t.assertTrue(kept(f, enemy).recon == null && kept(f, enemy).count == 0, "the raid party's look became a recon: "
                    + kept(f, enemy).recon + " / " + kept(f, enemy).note);
            // the Ember Legion razes it while our raider looks on: the heart's place in view, the party keeps the ruins
            enemy.destroyed = true;
            enemy.destroyedBy = FactionType.EMBER_LEGION;
            enemy.destroyedAt = now + 1;
            raids(t, f, now + 2);
            t.assertTrue(f.raidIntel != null && f.raidIntel.fallen, "the raider saw the heart's place and kept no ruins: " + f.raidIntel);
            f.leaveRaid(raider.getUUID());
            raids(t, f, now + 3);
            t.assertTrue(f.raidTarget < 0 && said(lines, SIDE, "found in ruins"), "the raid did not end with its party home, ruins found");
            Report ours = kept(f, enemy);
            t.assertTrue(ours.fallen && ours.recon == null && ours.reconFailures == 0, "the ruins the party saw: fallen " + ours.fallen
                    + ", recon " + ours.recon);
            f.lastRaidAt = now + 4 - interval - 1;
            raids(t, f, now + 4);
            t.assertTrue(f.raidTarget < 0 && lines.stream().noneMatch(l -> l.line().contains("Launching a raid")),
                    "the council launched on its raiders' look: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * A base another faction destroyed is still believed standing until one of ours sees the ruins: the council still
     * picks it (a raid on ruins is the player-like mistake); a scout that sees its heart gone reports it fallen, and the
     * council no longer picks it. A raider sees a fall only within sight of the heart's place, not from afar.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_ruins")
    public static void aFallIsKnownOnlyWhenOurPeopleSeeTheRuins(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        try {
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval - 1;
            for (int[] p : GARRISON) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState enemy = enemyBase(t, ENEMY, h.absolutePos(new BlockPos(12, 2, 21)));
            Report seen = new Report(Report.Kind.ENEMY_BASE, enemy.center, now, ENEMY, 0, "an empty yard");
            seen.recon = counted(0, 0, now, java.util.Map.of()); // its scout watched the yard empty
            deliver(t, f, seen, now);
            // Ember razes it far from any of ours
            enemy.destroyed = true;
            enemy.destroyedBy = FactionType.EMBER_LEGION;
            raids(t, f, now);
            t.assertTrue(f.raidTarget == enemy.id, "the council knew of a fall none of its people saw: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            f.raidTarget = -1;
            f.raiders.clear();
            f.lastRaidAt = now - interval - 1;
            FactionNpc scout = member(t, 12, 16, NpcRole.SCOUT);
            t.assertTrue(BaseWatch.seesFall(scout, enemy), "the scout beside the ruins does not see them");
            BaseState far = enemyBase(t, ENEMY, h.absolutePos(new BlockPos(12, 2, 320)));
            far.destroyed = true;
            t.assertTrue(!BaseWatch.seesFall(scout, far), "a fall 300 blocks off was seen");
            Report ruins = BaseWatch.look(level, scout, enemy, now + 1);
            t.assertTrue(ruins != null && ruins.fallen, "the scout did not report the ruins: " + (ruins == null ? null : ruins.note));
            deliver(t, f, ruins, now + 1);
            t.assertTrue(f.knowledge.stream().anyMatch(r -> r.subject == ENEMY && r.pos.equals(enemy.center) && r.fallen),
                    "the ruins did not replace the standing report");
            FactionDirector.LAST_COUNCIL.remove(SIDE);
            raids(t, f, now + 2);
            t.assertTrue(f.raidTarget < 0 && FactionDirector.LAST_COUNCIL.get(SIDE) == null,
                    "the council still weighs ruins its scout reported: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Live check of the knowledge review (2026-11-19): two parties broke their target's heart and came home "in victory",
     * but none of the looks the director takes of the target from up to three raiders had the heart's place in view after
     * the fall, so the report they brought home was a standing base, and the Tide Alliance's council went on weighing the
     * ruins of Stoneguard (with a hero it had seen there) as its next target. The raider that broke the heart saw it go: a
     * party that broke its target's heart brings the ruins home, and the council no longer picks them.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_raidwon")
    public static void aPartyThatBreaksTheHeartBringsTheRuinsHome(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        List<Messages.Said> lines = recording(t);
        try {
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval - 1;
            for (int[] p : GARRISON) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState enemy = enemyBase(t, ENEMY, t.base.center.offset(0, 0, 300));
            Report seen = new Report(Report.Kind.ENEMY_BASE, enemy.center, now, ENEMY, 0, "an empty yard");
            seen.recon = counted(0, 0, now, java.util.Map.of()); // its scout watched the yard empty
            deliver(t, f, seen, now);
            raids(t, f, now);
            t.assertTrue(f.raidTarget == enemy.id && f.raiders.size() == 3, "no raid of three: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            List<java.util.UUID> party = List.copyOf(f.raiders);
            // one of the party breaks the heart; no look the director takes has the heart's place in view (here: the
            // raiders stand 300 blocks off, so it takes none)
            enemy.destroyed = true;
            enemy.destroyedBy = SIDE;
            enemy.destroyedAt = now + 10;
            raids(t, f, now + 100);
            t.assertTrue(f.raidIntel != null && f.raidIntel.fallen, "the party that broke the heart did not keep the ruins it saw: "
                    + (f.raidIntel == null ? null : f.raidIntel.note));
            // the raiders come home, the last one tells the raid's end
            for (java.util.UUID u : party) {
                f.leaveRaid(u);
            }
            raids(t, f, now + 200);
            t.assertTrue(f.raidTarget < 0, "the raid did not end with its party home");
            t.assertTrue(said(lines, SIDE, "ended in victory"), "no victory told at home");
            t.assertTrue(f.knowledge.stream().anyMatch(r -> r.subject == ENEMY && r.pos.equals(enemy.center) && r.fallen),
                    "the ruins the party saw did not replace the standing report");
            f.lastRaidAt = now - interval - 1;
            FactionDirector.LAST_COUNCIL.remove(SIDE);
            raids(t, f, now + 300);
            t.assertTrue(f.raidTarget < 0 && FactionDirector.LAST_COUNCIL.get(SIDE) == null,
                    "the council weighs the ruins its own party made: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Live check of a faction's fall (2026-11-25): Stoneguard's council sent a party on the Grove Covenant's settlement,
     * which the Ember Legion had razed 1.6 days before where none of Stoneguard saw. Its raiders saw the heart gone and
     * turned to loot the ruins, but the raid "has ended" with no word of them: the party kept only the director's looks
     * of the yard, none with the heart's place in view, so its council went on weighing Grove's ruins as a standing base.
     * A raider that sees its target in ruins keeps them for the party: the raid ends "found in ruins", the ruins replace
     * the standing report, and the council no longer picks them.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_raidruins")
    public static void aPartyThatFindsRuinsBringsThemHome(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        List<Messages.Said> lines = recording(t);
        try {
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval - 1;
            for (int[] p : GARRISON) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState enemy = enemyBase(t, ENEMY, h.absolutePos(new BlockPos(FAR_X, 2, FAR_Z)));
            Report seen = new Report(Report.Kind.ENEMY_BASE, enemy.center, now, ENEMY, 0, "an empty yard");
            seen.recon = counted(0, 0, now, java.util.Map.of()); // its scout watched the yard empty
            deliver(t, f, seen, now);
            // the Ember Legion razed it, unseen by us; our raid on it is under way, its raider on the march beside the ruins
            enemy.destroyed = true;
            enemy.destroyedBy = FactionType.EMBER_LEGION;
            enemy.destroyedAt = now;
            FactionNpc raider = member(t, HOME_X, HOME_Z + 1, NpcRole.SWORDSMAN);
            f.raidTarget = enemy.id;
            f.raiders.add(raider.getUUID());
            f.raidParty = 1;
            f.raidStartedAt = now;
            f.raidMusteredAt = now; // (past the muster)
            t.assertTrue(BaseWatch.seesFall(raider, enemy), "the raider beside the ruins does not see them");
            // its guard job on the march: it sees the heart gone and turns to what is left there
            call(com.factionclash.work.GuardJob.class, raider.job(), "run", new Class<?>[0]);
            Object state = get(com.factionclash.work.GuardJob.class, raider.job(), "state");
            t.assertTrue(!"RAID_TRAVEL".equals(String.valueOf(state)), "the raider did not see the fall: " + state);
            // it comes home and the raid ends there; no look the director took had the heart's place in view (it took none)
            f.leaveRaid(raider.getUUID());
            raids(t, f, now + 100);
            t.assertTrue(f.raidTarget < 0, "the raid did not end with its party home");
            t.assertTrue(said(lines, SIDE, "found in ruins"), "the raid's end told no ruins");
            t.assertTrue(!said(lines, SIDE, "ended in victory"), "a victory was told for ruins another faction made");
            t.assertTrue(f.knowledge.stream().anyMatch(r -> r.subject == ENEMY && r.pos.equals(enemy.center) && r.fallen),
                    "the ruins the raider saw did not replace the standing report");
            f.lastRaidAt = now - interval - 1;
            FactionDirector.LAST_COUNCIL.remove(SIDE);
            raids(t, f, now + 200);
            t.assertTrue(f.raidTarget < 0 && FactionDirector.LAST_COUNCIL.get(SIDE) == null,
                    "the council weighs the ruins its party found: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the raid's plan (known leak 5)

    /**
     * Known leak 5: the council launches a raid on a settlement 300 blocks away and keeps its own plan for it (the march to
     * the rally point, the muster's longest wait, the assault, the march home). The party is wiped out where none of ours
     * sees it: its raiders are expected back by the plan, and the council takes the raid as lost at the plan plus the
     * grace, when its raiders go missing; not at the raid's 3-day limit.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "knowledge_raidplan")
    public static void aRaidWipedOutUnseenIsTakenAsLostByItsPlan(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 60);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        int oldWitness = FCConfig.WITNESS_RANGE.get();
        t.onCleanup(() -> FCConfig.WITNESS_RANGE.set(oldWitness));
        try {
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval - 1;
            for (int[] p : GARRISON) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState enemy = enemyBase(t, ENEMY, t.base.center.offset(0, 0, 300));
            Report seen = new Report(Report.Kind.ENEMY_BASE, enemy.center, now, ENEMY, 0, "an empty yard");
            seen.recon = counted(0, 0, now, java.util.Map.of()); // its scout watched the yard empty
            deliver(t, f, seen, now);
            raids(t, f, now);
            t.assertTrue(f.raidTarget == enemy.id && f.raiders.size() == 3, "no raid of three: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            BlockPos rally = BlockPos.of(f.raidRally);
            long plan = Whereabouts.raidPlanTicks(Math.sqrt(t.base.center.distSqr(rally)), 300);
            t.assertTrue(f.raidPlanTicks == plan, "the raid's plan " + f.raidPlanTicks + ", not " + plan);
            t.assertTrue(plan + FCConfig.MISSING_GRACE_TICKS.get() < Whereabouts.TICKS_PER_DAY, "a 300-block raid's plan: " + plan);
            // the party marches out (seen leaving: expected back by the plan) and dies where none of ours sees it
            FCConfig.WITNESS_RANGE.set(4);
            List<NpcRecord> party = new ArrayList<>();
            int x = 4;
            for (java.util.UUID u : List.copyOf(f.raiders)) {
                FactionNpc r = (FactionNpc) level.getEntity(u);
                goTo(t, r, x, FAR_Z);
                x += 8;
                NpcRecord rec = t.data.record(u);
                party.add(rec);
                t.assertTrue(rec.where == State.AWAY && rec.expectedBy == f.raidStartedAt + plan,
                        "a raider is expected back by the raid's plan: " + rec.expectedBy + " for " + (f.raidStartedAt + plan));
            }
            for (NpcRecord rec : party) {
                kill(level.getEntity(rec.uuid) instanceof LivingEntity e ? e : null);
                t.assertTrue(rec.dead && rec.alive && !rec.witnessed, "a raider's death should be unseen: " + rec.where);
            }
            raids(t, f, now + 1);
            t.assertTrue(f.raidTarget < 0 && f.raidEndNews != null, "the party should end on the spot, unheard at home");
            long grace = FCConfig.MISSING_GRACE_TICKS.get(), lost = f.raidStartedAt + plan + grace;
            t.assertTrue(lost < f.raidStartedAt + Whereabouts.RAID_LIMIT_TICKS, "the plan should end long before the raid's limit");
            raids(t, f, lost);
            t.assertTrue(f.raidEndNews != null, "the council gave its raid up before its plan and the grace passed");
            raids(t, f, lost + 1);
            t.assertTrue(f.raidEndNews == null, "past its plan and the grace the council still waited (the 3-day limit?)");
            RollCall.upkeep(level, t.data, t.base, lost + 1);
            for (NpcRecord rec : party) {
                t.assertTrue(rec.where == State.MISSING, "its raiders should be missing by then: " + rec.where);
            }
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the miner's shift (known leak 6)

    /**
     * Known leak 6: a miner's trip is planned at the storehouse (its shift: until nightfall, at least half a day), so
     * crossing the home area's edge again later on the same shift keeps the same expected return; before, each crossing
     * set a new half-day plan from that moment.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "knowledge_shift")
    public static void aMinersTripIsItsShiftNotEachCrossing(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 60);
        ServerLevel level = h.getLevel();
        try {
            FactionNpc miner = member(t, HOME_X, HOME_Z, NpcRole.MINER);
            MinerJob job = (MinerJob) miner.job();
            long start = level.getGameTime();
            set(Job.class, job, "unloadedAt", start); // it has just left the storehouse, at dawn
            set(Job.class, job, "unloadedDayTime", 0L);
            long end = job.shiftEnd(start, level.getDayTime());
            t.assertTrue(end == start + 13000, "a shift from dawn to nightfall: " + (end - start));
            NpcRecord r = t.data.record(miner.getUUID());
            goTo(t, miner, FAR_X, FAR_Z);
            long first = r.expectedBy;
            t.assertTrue(r.where == State.AWAY && first > end && first <= end + Whereabouts.walkTicks(16),
                    "away on its shift: expected back by its end plus the walk home, " + first + " for a shift ending " + end);
            goTo(t, miner, HOME_X, HOME_Z);
            t.assertTrue(r.where == State.HERE, "back in the home area: " + r.where);
            h.runAfterDelay(30, t.guard(() -> {
                goTo(t, miner, FAR_X, FAR_Z);
                t.assertTrue(r.where == State.AWAY && r.expectedBy == first,
                        "crossing again later on the same shift set a new plan: " + r.expectedBy + " for " + first);
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the cave survey (known leak 2)

    /**
     * Known leak 2: a cave explorer coming home with its survey has not told it yet: the base has no cave workplace and no
     * need for a cave miner. Once it hands the survey in at the station, the cave is the faction's: a workplace with
     * marked ores, and the director wants a miner for it.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_survey")
    public static void aSurveyIsTheFactionsOnlyWhenTheExplorerIsHome(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        try {
            BlockPos station = new BlockPos(6, 2, 8);
            h.setBlock(station, com.factionclash.registry.ModBlocks.REPORTING_STATION.get().defaultBlockState());
            t.base.points.put(BaseState.STATION, h.absolutePos(station));
            t.onCleanup(() -> h.setBlock(station, Blocks.AIR.defaultBlockState()));
            FactionNpc explorer = member(t, FAR_X, FAR_Z, NpcRole.CAVE_EXPLORER);
            CaveExplorerJob job = (CaveExplorerJob) explorer.job();
            BlockPos cave = h.absolutePos(new BlockPos(12, 1, 22)), mouth = h.absolutePos(new BlockPos(12, 2, 22));
            set(CaveExplorerJob.class, job, "cavity", cave);
            set(CaveExplorerJob.class, job, "entrance", mouth);
            @SuppressWarnings("unchecked") List<Long> waypoints = (List<Long>) get(CaveExplorerJob.class, job, "waypoints");
            @SuppressWarnings("unchecked") List<Integer> parents = (List<Integer>) get(CaveExplorerJob.class, job, "parents");
            @SuppressWarnings("unchecked") java.util.Set<Long> ores = (java.util.Set<Long>) get(CaveExplorerJob.class, job, "ores");
            waypoints.add(mouth.asLong());
            parents.add(-1);
            ores.add(cave.asLong());
            set(CaveExplorerJob.class, job, "endReason", "the test");
            long now = level.getGameTime();
            boolean caveKnown = t.base.workplaces.stream().map(t.data::workplace)
                    .anyMatch(w -> w != null && w.type == WorkplaceState.Type.CAVE);
            t.assertTrue(!caveKnown, "the base knows a cave its explorer has not handed in");
            t.assertTrue(FactionDirector.needs(level, t.data, t.base, now).stream().noneMatch(n -> n.reason().contains("surveyed cave")),
                    "the director wants a miner for a survey still in the explorer's head");
            // home at the station: it hands the survey in
            put(t, explorer, 6, 9);
            set(CaveExplorerJob.class, job, "timer", 40);
            call(CaveExplorerJob.class, job, "report", new Class<?>[]{BaseState.class}, t.base);
            WorkplaceState w = t.base.workplaces.stream().map(t.data::workplace)
                    .filter(x -> x != null && x.type == WorkplaceState.Type.CAVE).findFirst().orElse(null);
            t.assertTrue(w != null && !w.marks.isEmpty(), "the survey was not handed in at the station");
            t.assertTrue(FactionDirector.needs(level, t.data, t.base, now).stream().anyMatch(n -> n.reason().contains("surveyed cave")),
                    "the director does not want a miner for the cave handed in");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ retraining and the blacksmith (known leak 3)

    /**
     * Known leak 3: a fisherman idle for half a day but away from home is not retrained (nobody at home sees it idle) and
     * the blacksmith does not plan for its gear; seen at home, it is retrained and is the blacksmith's crew.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_retrain")
    public static void retrainingAndTheBlacksmithSeeOnlyMembersAtHome(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        try {
            FactionNpc smith = member(t, 10, 12, NpcRole.BLACKSMITH);
            FactionNpc fisher = member(t, HOME_X, HOME_Z, NpcRole.FISHERMAN);
            atLeast(level, FactionDirector.RETRAIN_IDLE_TICKS + 2); // (half a day idle needs half a day of world time)
            long now = level.getGameTime();
            set(Job.class, fisher.job(), "idleSince", now - FactionDirector.RETRAIN_IDLE_TICKS - 1);
            goTo(t, fisher, FAR_X, FAR_Z);
            t.assertTrue(t.data.record(fisher.getUUID()).where == State.AWAY, "the fisherman should be away");
            @SuppressWarnings("unchecked") List<FactionNpc> crew = (List<FactionNpc>) call(com.factionclash.work.BlacksmithJob.class,
                    smith.job(), "members", new Class<?>[0]);
            t.assertTrue(!crew.contains(fisher), "the blacksmith plans for a member away");
            FactionDirector.Need need = new FactionDirector.Need(NpcRole.MINER, "the test");
            Class<?>[] types = {ServerLevel.class, CampaignData.class, FactionState.class, BaseState.class, FactionDirector.Need.class, long.class};
            boolean away = (Boolean) call(FactionDirector.class, null, "retrainIdleWorker", types, level, t.data, f, t.base, need, now);
            t.assertTrue(!away && fisher.getRole() == NpcRole.FISHERMAN, "a fisherman away was retrained where it stood");
            goTo(t, fisher, HOME_X, HOME_Z);
            @SuppressWarnings("unchecked") List<FactionNpc> crew2 = (List<FactionNpc>) call(com.factionclash.work.BlacksmithJob.class,
                    smith.job(), "members", new Class<?>[0]);
            t.assertTrue(crew2.contains(fisher), "the blacksmith does not plan for a member at home");
            set(Job.class, fisher.job(), "idleSince", now - FactionDirector.RETRAIN_IDLE_TICKS - 1);
            boolean home = (Boolean) call(FactionDirector.class, null, "retrainIdleWorker", types, level, t.data, f, t.base, need, now);
            t.assertTrue(home && fisher.getRole() == NpcRole.MINER, "an idle fisherman at home was not retrained");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ experience carried home

    /**
     * Experience a member picks up away from home is carried, not banked; it reaches the faction when the member is
     * home, and a member that dies away drops what it carried as orbs.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_xp")
    public static void experiencePickedUpAwayIsCarriedHome(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        try {
            FactionNpc miner = member(t, HOME_X, HOME_Z, NpcRole.MINER);
            goTo(t, miner, FAR_X, FAR_Z);
            long bank = f.xpBank;
            level.addFreshEntity(new ExperienceOrb(level, miner.getX(), miner.getY(), miner.getZ(), 7));
            call(FactionNpc.class, miner, "pickUpExperience", new Class<?>[0]);
            t.assertTrue(miner.carriedXp() == 7 && f.xpBank == bank, "picked up away: carried " + miner.carriedXp()
                    + ", banked " + (f.xpBank - bank));
            goTo(t, miner, HOME_X, HOME_Z);
            call(FactionNpc.class, miner, "bankCarriedXp", new Class<?>[0]);
            t.assertTrue(miner.carriedXp() == 0 && f.xpBank == bank + 7, "home: banked " + (f.xpBank - bank));
            goTo(t, miner, FAR_X, FAR_Z);
            level.addFreshEntity(new ExperienceOrb(level, miner.getX(), miner.getY(), miner.getZ(), 5));
            call(FactionNpc.class, miner, "pickUpExperience", new Class<?>[0]);
            t.assertTrue(miner.carriedXp() == 5, "the second pick-up: " + miner.carriedXp());
            Vec3 at = miner.position();
            kill(miner);
            int dropped = level.getEntitiesOfClass(ExperienceOrb.class, new AABB(at, at).inflate(3)).stream()
                    .mapToInt(o -> o.getValue() * Math.max(1, o.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getInt("Count"))).sum();
            t.assertTrue(dropped == 5 && f.xpBank == bank + 7, "what it carried should drop where it died: " + dropped);
            level.getEntitiesOfClass(ExperienceOrb.class, new AABB(at, at).inflate(3)).forEach(ExperienceOrb::discard);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the storehouse seen from afar

    /**
     * A hungry worker away judges the storehouse's food by what it saw there last (it does not see the storehouse fill
     * up or run dry from far water); at home it counts it.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_food")
    public static void aWorkerAwayKnowsTheStorehouseAsItLastSawIt(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        try {
            FactionNpc fisher = member(t, HOME_X, HOME_Z, NpcRole.FISHERMAN);
            Class<?>[] types = {BaseState.class};
            int before = (Integer) call(Job.class, fisher.job(), "storehouseFood", types, t.base);
            put(t, fisher, FAR_X, FAR_Z);
            net.minecraft.world.item.ItemStack rest = com.factionclash.storage.FactionStorage.insert(level, t.base,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 16));
            t.assertTrue(rest.isEmpty(), "the bread did not fit in the storehouse");
            int away = (Integer) call(Job.class, fisher.job(), "storehouseFood", types, t.base);
            t.assertTrue(away == before, "a worker away saw the storehouse fill up: " + before + " -> " + away);
            put(t, fisher, HOME_X, HOME_Z);
            int home = (Integer) call(Job.class, fisher.job(), "storehouseFood", types, t.base);
            t.assertTrue(home >= before + 16, "at home it counts the storehouse: " + home);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the alarm

    /**
     * An intruder inside the walls box that none of the base's people sees (shut in stone) is not handed to the guards;
     * once a guard can see it, it is.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_alarm")
    public static void theAlarmNeedsSomeoneToSeeTheIntruder(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        List<BlockPos> box = new ArrayList<>();
        t.onCleanup(() -> box.forEach(p -> h.setBlock(p, Blocks.AIR.defaultBlockState())));
        try {
            FactionNpc guard = member(t, 8, 8, NpcRole.SWORDSMAN);
            for (int x = 14; x <= 16; x++) {
                for (int z = 14; z <= 16; z++) {
                    for (int y = 2; y <= 4; y++) {
                        if (x != 15 || z != 15 || y == 4) {
                            BlockPos p = new BlockPos(x, y, z);
                            h.setBlock(p, Blocks.STONE.defaultBlockState());
                            box.add(p);
                        }
                    }
                }
            }
            FactionState f = t.data.faction(SIDE);
            f.struckBy.remove(ENEMY); // (the cleanup restores the faction's strikes)
            f.struckBy.remove(FactionType.EMBER_LEGION);
            FactionNpc intruder = stranger(t, ENEMY, 15, 15, NpcRole.SWORDSMAN);
            int hidden = FactionDirector.defenceAlarm(level, t.base);
            t.assertTrue(hidden == 0 && guard.getTarget() == null, "a hidden intruder was handed to the guards: " + hidden);
            t.assertTrue(f.struckAt(ENEMY) == 0, "a hidden intruder was a strike: " + f.struckBy);
            box.forEach(p -> h.setBlock(p, Blocks.AIR.defaultBlockState()));
            box.clear();
            long now = level.getGameTime();
            int seen = FactionDirector.defenceAlarm(level, t.base);
            t.assertTrue(seen >= 1 && guard.getTarget() == intruder, "an intruder in sight was not raised: " + seen);
            t.assertTrue(f.struckAt(ENEMY) == now, "a soldier noticed inside our walls was no strike (recon.md 6): " + f.struckBy);
            // a worker of another faction passing through is noticed, but strikes nobody
            stranger(t, FactionType.EMBER_LEGION, 9, 12, NpcRole.FARMER);
            FactionDirector.defenceAlarm(level, t.base);
            t.assertTrue(f.struckAt(FactionType.EMBER_LEGION) == 0, "a worker passing through was a strike: " + f.struckBy);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the lookout (known leak 4)

    /**
     * Known leak 4: a lookout hero at its base's work belt but outside the home area sees an enemy and calls the guards
     * near, but its faction hears nothing; inside the home area its faction hears "spots 1 enemy".
     */
    @GameTest(template = "platform", timeoutTicks = 120, batch = "knowledge_lookout")
    public static void theLookoutsLineIsHeardOnlyAtHome(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 120);
        try {
            List<Messages.Said> lines = recording(t);
            String id = "fx_knowledge_lookout";
            HeroEntity hero = HeroAbilityGameTests.hero(t, id, "katana", HeroAbilityGameTests.def(id, HeroAbilityGameTests.MELEE,
                    "striker", "", ", \"talents\": [\"lookout\"]"), FAR_X, FAR_Z);
            hero.setNoAi(true);
            stranger(t, ENEMY, 14, 23, NpcRole.SWORDSMAN);
            h.runAfterDelay(40, t.guard(() -> {
                t.assertTrue(!said(lines, SIDE, "spots"), "the lookout's line was heard outside the home area: " + lines);
                put(t, hero, HOME_X, HOME_Z);
            }));
            h.runAfterDelay(80, t.guard(() -> {
                t.assertTrue(said(lines, SIDE, "spots 1 enemy"), "the lookout's line was not heard at home: " + lines);
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the Hollow's roll call

    /**
     * Equal treatment: a lair monster that falls far from its lair stays on the lair's list (the lair misses it only when
     * it stays away, and does not replace it at once); one that falls at its lair is missed at once.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_lair")
    public static void aLairMissesAMonsterThatFellFarOffOnlyLater(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        try {
            BaseState lair = new BaseState();
            lair.faction = FactionType.HOLLOW;
            lair.kind = BaseState.Kind.LAIR;
            lair.center = h.absolutePos(new BlockPos(12, 2, 12)).offset(200, 0, 0);
            lair.name = "Knowledge test lair";
            t.data.addBase(lair);
            t.onCleanup(() -> t.data.bases.remove(lair.id));
            Zombie far = t.track(h.spawn(EntityType.ZOMBIE, new BlockPos(20, 2, 20)));
            far.getPersistentData().putInt(com.factionclash.campaign.BaseBuilder.LAIR_TAG, lair.id);
            lair.lairMobs.add(far.getUUID());
            kill(far);
            t.assertTrue(lair.lairMobs.contains(far.getUUID()), "a monster that fell 200 blocks from its lair was missed at once");
            lair.center = far.blockPosition(); // the same death at the lair itself
            Zombie near = t.track(h.spawn(EntityType.ZOMBIE, new BlockPos(20, 2, 18)));
            near.getPersistentData().putInt(com.factionclash.campaign.BaseBuilder.LAIR_TAG, lair.id);
            lair.lairMobs.add(near.getUUID());
            kill(near);
            t.assertTrue(!lair.lairMobs.contains(near.getUUID()), "a monster that fell at its lair was not missed");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the review of the knowledge review (2026-11-19)

    /** Two more of our soldiers at home, so the council has ten inside the walls' box. */
    private static final int[][] TEN = {{10, 10}, {11, 10}, {13, 10}, {14, 10}, {10, 14}, {14, 14}, {10, 12}, {14, 12}, {11, 14}, {13, 14}};

    /**
     * Review finding 1: home expects its raiders back by the raid's plan, so the party on the spot keeps the same plan. It
     * gives the raid up when it must turn home to be back by it; before, it raided on until the 3-day limit while home
     * reported its live raiders missing and then presumed them dead (and replaced them).
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "knowledge_raidturn")
    public static void aRaidPartyTurnsHomeByItsPlanAndHomeNeverGivesUpALiveRaid(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 60);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        try {
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval - 1;
            for (int[] p : GARRISON) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState enemy = enemyBase(t, ENEMY, t.base.center.offset(0, 0, 300));
            Report seen = new Report(Report.Kind.ENEMY_BASE, enemy.center, now, ENEMY, 0, "an empty yard");
            seen.recon = counted(0, 0, now, java.util.Map.of()); // its scout watched the yard empty
            deliver(t, f, seen, now);
            raids(t, f, now);
            t.assertTrue(f.raidTarget == enemy.id && f.raiders.size() == 3, "no raid of three: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            long start = f.raidStartedAt, plan = f.raidPlanTicks;
            // the party marches out (seen leaving: expected back by the plan) and fights on at the target, alive
            List<NpcRecord> party = new ArrayList<>();
            int x = 4;
            for (java.util.UUID u : List.copyOf(f.raiders)) {
                goTo(t, (FactionNpc) level.getEntity(u), x, FAR_Z);
                x += 8;
                party.add(t.data.record(u));
            }
            long grace = FCConfig.MISSING_GRACE_TICKS.get(), presume = FCConfig.PRESUMED_DEAD_TICKS.get();
            long turn = start + plan - Whereabouts.walkTicks(300);
            long[] hours = {start + plan / 2, turn - 1, turn + 1, start + plan + grace + 1, start + plan + grace + presume + 1};
            boolean onBeforeTurn = false, onAfterTurn = true;
            for (long at : hours) {
                raids(t, f, at);
                RollCall.upkeep(level, t.data, t.base, at);
                for (NpcRecord rec : party) {
                    t.assertTrue(rec.where == State.HERE || rec.where == State.AWAY || f.raidTarget < 0,
                            "home gave up a live raider (" + rec.where + ") while its party still raided on the spot, "
                                    + (at - start) + " ticks after the launch (plan " + plan + ")");
                }
                if (at == turn - 1) {
                    onBeforeTurn = f.raidTarget == enemy.id;
                }
                if (at == turn + 1) {
                    onAfterTurn = f.raidTarget >= 0;
                }
            }
            t.assertTrue(onBeforeTurn, "the party gave its raid up before its plan said");
            t.assertTrue(!onAfterTurn, "the party raided on past the time it had to turn home to be back by its plan");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Review finding 1, its other half: what home believes is not the party's to know. A raider its faction at home has
     * presumed dead (missing too long) stays on the party's roll while the raid is on; before, home's belief pulled it
     * off the party where it stood and sent it home.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_raidroll")
    public static void homesBeliefDoesNotPullARaiderOffItsParty(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        try {
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval - 1;
            for (int[] p : GARRISON) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState enemy = enemyBase(t, ENEMY, t.base.center.offset(0, 0, 300));
            Report seen = new Report(Report.Kind.ENEMY_BASE, enemy.center, now, ENEMY, 0, "an empty yard");
            seen.recon = counted(0, 0, now, java.util.Map.of()); // its scout watched the yard empty
            deliver(t, f, seen, now);
            raids(t, f, now);
            t.assertTrue(f.raidTarget == enemy.id && f.raiders.size() == 3, "no raid of three: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            java.util.UUID u = f.raiders.get(0);
            goTo(t, (FactionNpc) level.getEntity(u), 4, FAR_Z);
            NpcRecord rec = t.data.record(u);
            rec.where(State.PRESUMED_DEAD, now + 1); // home's view only: the raider lives and fights on
            raids(t, f, now + 1);
            t.assertTrue(f.raidTarget == enemy.id && f.raiders.contains(u),
                    "home's belief pulled a live raider off its party on the spot (" + f.raiders.size() + " left)");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Review finding 2: no base is ever rebuilt, so ruins a faction's people saw stay ruins. A later look that sees the
     * yard but not the heart's place (from outside the walls, or behind a block) used to replace the fallen report with a
     * standing one, and the council sent raid after raid to the ruins.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_ruinsstay")
    public static void ruinsSeenStayRuinsWhenOnlyTheYardIsSeenAgain(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        BlockPos screen = new BlockPos(12, 2, 20);
        t.onCleanup(() -> h.setBlock(screen, Blocks.AIR));
        try {
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval - 1;
            for (int[] p : GARRISON) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState enemy = enemyBase(t, ENEMY, h.absolutePos(new BlockPos(12, 2, 21)));
            enemy.destroyed = true;
            enemy.destroyedBy = FactionType.EMBER_LEGION;
            FactionNpc scout = member(t, 12, 16, NpcRole.SCOUT);
            Report ruins = BaseWatch.look(level, scout, enemy, now);
            t.assertTrue(ruins != null && ruins.fallen, "the scout did not see the ruins: " + (ruins == null ? null : ruins.note));
            deliver(t, f, ruins, now);
            // later the heart's place is out of view (a block before it), the yard behind still in view
            h.setBlock(screen, Blocks.STONE);
            Report yard = BaseWatch.look(level, scout, enemy, now + 1);
            t.assertTrue(yard != null && !yard.fallen && yard.count == 0 && yard.recon == null,
                    "the second look should see the yard but not the heart's place: " + (yard == null ? null : yard.note));
            t.assertTrue(ScoutJob.mergeOnTrip(ruins, yard).fallen, "a later look on the same trip forgot the ruins it saw");
            deliver(t, f, yard, now + 1);
            t.assertTrue(f.knowledge.stream().anyMatch(r -> r.subject == ENEMY && r.pos.equals(enemy.center) && r.fallen),
                    "a later look at the yard rebuilt ruins the faction had seen");
            FactionDirector.LAST_COUNCIL.remove(SIDE);
            raids(t, f, now + 2);
            t.assertTrue(f.raidTarget < 0 && FactionDirector.LAST_COUNCIL.get(SIDE) == null,
                    "the council weighs ruins its scout had seen: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * recon.md 5.1 and 5.3 (task R5): the heroes in the estimate are those the watch saw, at their war power at full
     * strength, under the soldier backbone; none is carried over from an older report. A recon counting two soldiers and a
     * hero holds three soldiers back, and the council's line says the defending hero held it off; a newer recon counting
     * the two and no hero lets them go (the hero went raiding, or fell, or stayed indoors for the whole watch).
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_heroes_watch")
    public static void aHeroCountsOnlyInTheWatchThatSawHer(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        try {
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval - 1;
            List<FactionNpc> ours = new ArrayList<>();
            for (int[] p : GARRISON) {
                ours.add(member(t, p[0], p[1], NpcRole.SWORDSMAN));
            }
            double gear = power(ours.get(0)); // (their soldiers in our gear, the hero worth six of them)
            BaseState enemy = enemyBase(t, ENEMY, t.base.center.offset(0, 0, 200));
            deliverRecon(t, f, enemy, counted(2, gear, now, java.util.Map.of("review_hero", 6.0 * gear)), now);
            raids(t, f, now);
            String council = FactionDirector.LAST_COUNCIL.get(SIDE);
            t.assertTrue(f.raidTarget < 0 && council != null && council.contains("(the defending heroes held it off)")
                    && council.contains("against about 2 defenders and review_hero (scout Edrin Cole"),
                    "a hero the watch saw should hold three soldiers back: " + council);
            deliverRecon(t, f, enemy, counted(2, gear, now + 100, java.util.Map.of()), now + 100);
            raids(t, f, now + 100);
            t.assertTrue(f.raidTarget == enemy.id, "a hero seen in an older watch still holds the council back: "
                    + FactionDirector.LAST_COUNCIL.get(SIDE));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * recon.md 10.2 item 13 and 15.5 (task R5): the knowledge review's ten-soldier test, rewritten for the reserve. Ten
     * soldiers at home send six (a third stays). Against a recon counting seven in the same gear the plan is eleven (seven
     * and half again, four), two thirds of it seven: they wait, "6 soldiers to send, 7 needed". Against a recon counting
     * six the plan is nine, two thirds six: they go. Two thirds of a plan of half again is the count, head for head.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_twothirds")
    public static void tenSoldiersAttackSixCountedAndWaitOnSeven(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        try {
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval - 1;
            List<FactionNpc> ours = new ArrayList<>();
            for (int[] p : TEN) {
                ours.add(member(t, p[0], p[1], NpcRole.SWORDSMAN));
            }
            double gear = power(ours.get(0));
            for (FactionNpc n : ours) {
                t.assertTrue(power(n) == gear, "our soldiers are not all in the same gear");
            }
            BaseState enemy = enemyBase(t, ENEMY, t.base.center.offset(0, 0, 200));
            deliverRecon(t, f, enemy, counted(7, gear, now, java.util.Map.of()), now);
            raids(t, f, now);
            String council = FactionDirector.LAST_COUNCIL.get(SIDE);
            t.assertTrue(f.raidTarget < 0 && council != null && council.contains("6 soldiers to send, 7 needed against about 7 defenders")
                    && council.contains("a reserve of 4 (half again the 7 counted)"), "six against seven counted should wait: " + council);
            deliverRecon(t, f, enemy, counted(6, gear, now + 1, java.util.Map.of()), now + 1);
            raids(t, f, now + 1);
            t.assertTrue(f.raidTarget == enemy.id && f.raiders.size() == 6, "ten soldiers waited against six counted: "
                    + FactionDirector.LAST_COUNCIL.get(SIDE));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * recon.md 5.5 (task R5; the owner's round keeps the recommended default, question 2): a lair is judged on the monsters
     * its scout counted, with no reserve. Three soldiers raid a lair counted at four in their own gear (two thirds of four is
     * two); with half again in the plan (six) they would need four. Its line names the monsters and no reserve.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_lair_recon")
    public static void aLairIsJudgedOnItsMonstersWithNoReserve(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        List<Messages.Said> lines = recording(t);
        try {
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval - 1;
            List<FactionNpc> ours = new ArrayList<>();
            for (int[] p : GARRISON) {
                ours.add(member(t, p[0], p[1], NpcRole.SWORDSMAN));
            }
            double gear = power(ours.get(0));
            BaseState lair = new BaseState();
            lair.faction = FactionType.HOLLOW;
            lair.kind = BaseState.Kind.LAIR;
            lair.center = t.base.center.offset(0, 0, 200);
            lair.name = "Knowledge test lair";
            t.data.addBase(lair);
            t.onCleanup(() -> t.data.bases.remove(lair.id));
            deliverRecon(t, f, lair, counted(4, gear, now, java.util.Map.of()), now);
            raids(t, f, now);
            t.assertTrue(f.raidTarget == lair.id && f.raiders.size() == 3, "three soldiers did not raid a lair counted at four: "
                    + FactionDirector.LAST_COUNCIL.get(SIDE));
            t.assertTrue(said(lines, SIDE, "Launching a raid with 3 soldiers on the Hollow lair reported at " + lair.center.toShortString()
                    + ", against about 4 monsters (scout Edrin Cole, day " + Whereabouts.day(now) + ")")
                    && !said(lines, SIDE, "reserve"), "the lair's launch line: " + lines);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * recon.md 7 (task R5): an operator's raid ({@code /fc admin raid}) goes whatever the council would say, and its line
     * says what the faction knows of the target: "with no scout's report of it" with no usable recon, else the plan of its
     * recon as at the interval (the count and half again).
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_forceraid")
    public static void anOperatorsRaidSaysWhatItRestsOn(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        List<Messages.Said> lines = recording(t);
        try {
            long now = level.getGameTime();
            List<FactionNpc> ours = new ArrayList<>();
            for (int[] p : GARRISON) {
                ours.add(member(t, p[0], p[1], NpcRole.SWORDSMAN));
            }
            BaseState enemy = enemyBase(t, ENEMY, t.base.center.offset(0, 0, 200));
            deliver(t, f, new Report(Report.Kind.ENEMY_BASE, enemy.center, now, ENEMY, 0, Report.positionNote(ENEMY, Report.Kind.ENEMY_BASE)), now);
            String done = FactionDirector.forceRaid(level, t.data, f, enemy);
            t.assertTrue(f.raidTarget == enemy.id && said(lines, SIDE, "By order! Launching a raid with 3 soldiers on the Grove Covenant settlement"
                    + " reported at " + enemy.center.toShortString() + ", with no scout's report of it"), "the operator's raid with no recon: "
                    + done + " / " + lines);
            f.raidTarget = -1;
            f.raiders.clear();
            f.lastParty.clear();
            lines.clear();
            deliverRecon(t, f, enemy, counted(2, power(ours.get(0)), now + 1, java.util.Map.of()), now + 1);
            done = FactionDirector.forceRaid(level, t.data, f, enemy);
            t.assertTrue(f.raidTarget == enemy.id && said(lines, SIDE, "By order! Launching a raid with 3 soldiers on the Grove Covenant settlement"
                    + " reported at " + enemy.center.toShortString() + ", against about 2 defenders (scout Edrin Cole, day " + Whereabouts.day(now + 1)
                    + ") and a reserve of 1 (half again the 2 counted)"), "the operator's raid on a recon: " + done + " / " + lines);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * A passing look tells where a base is and that it stands, and counts nobody (recon.md 3.6, task R5): behind a wall two
     * blocks high the enemy's soldiers and the yard are out of sight, but the walls' top is in view, so the base is
     * reported; with the wall gone, both soldiers in sight, the report is the same: no count, no heroes, no recon.
     * {@link #aReconSampleCountsOnlySoldiersInSight} counts them on a focused watch's sample.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_wall")
    public static void aPassingLookCountsNobody(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        List<BlockPos> wall = new ArrayList<>();
        for (int x = 8; x <= 16; x++) {
            for (int y = 2; y <= 3; y++) {
                wall.add(new BlockPos(x, y, 19));
            }
        }
        t.onCleanup(() -> wall.forEach(p -> h.setBlock(p, Blocks.AIR)));
        try {
            long now = level.getGameTime();
            BaseState enemy = enemyBase(t, ENEMY, h.absolutePos(new BlockPos(12, 2, 21)));
            stranger(t, ENEMY, 11, 22, NpcRole.SWORDSMAN);
            stranger(t, ENEMY, 13, 22, NpcRole.SWORDSMAN);
            FactionNpc scout = member(t, 12, 16, NpcRole.SCOUT);
            wall.forEach(p -> h.setBlock(p, Blocks.STONE));
            Report hidden = BaseWatch.look(level, scout, enemy, now);
            String note = Report.positionNote(ENEMY, Report.Kind.ENEMY_BASE);
            t.assertTrue(hidden != null && hidden.count == 0 && hidden.recon == null && hidden.note.equals(note),
                    "behind the wall the base should be reported, counting nobody: " + (hidden == null ? null : hidden.note));
            wall.forEach(p -> h.setBlock(p, Blocks.AIR));
            Report open = BaseWatch.look(level, scout, enemy, now + 1);
            t.assertTrue(open != null && open.count == 0 && open.recon == null && open.note.equals(note),
                    "with the wall gone a passing look still counts nobody: " + (open == null ? null : open.count + " / " + open.note));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ focused reconnaissance (docs/design/recon.md, task R2)

    /**
     * recon.md 10.2 item 13: {@link #aScoutCountsOnlySoldiersInSight} on a focused watch's sample. Behind the wall the
     * sample holds no soldier and not the yard's middle; with the wall gone it holds both soldiers, by their UUIDs, and the
     * middle; a watch of those samples counts the two once each. A base beyond the recon's sight gives no sample, and a
     * lair's grid reaches its monsters' leash.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_recon_wall")
    public static void aReconSampleCountsOnlySoldiersInSight(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        List<BlockPos> wall = new ArrayList<>();
        for (int x = 8; x <= 16; x++) {
            for (int y = 2; y <= 3; y++) {
                wall.add(new BlockPos(x, y, 19));
            }
        }
        t.onCleanup(() -> wall.forEach(p -> h.setBlock(p, Blocks.AIR)));
        try {
            long now = level.getGameTime();
            BaseState enemy = enemyBase(t, ENEMY, h.absolutePos(new BlockPos(12, 2, 21)));
            FactionNpc a = stranger(t, ENEMY, 11, 22, NpcRole.SWORDSMAN);
            FactionNpc b = stranger(t, ENEMY, 13, 22, NpcRole.SWORDSMAN);
            FactionNpc scout = member(t, 12, 16, NpcRole.SCOUT);
            wall.forEach(p -> h.setBlock(p, Blocks.STONE));
            ReconWatch.Sample hidden = BaseWatch.reconSample(level, scout, enemy, now);
            t.assertTrue(hidden != null && hidden.soldiers().isEmpty() && !hidden.middle(),
                    "the recon sample counted soldiers or the middle behind a wall: " + hidden);
            wall.forEach(p -> h.setBlock(p, Blocks.AIR));
            ReconWatch.Sample open = BaseWatch.reconSample(level, scout, enemy, now + 40);
            t.assertTrue(open != null && open.soldiers().keySet().equals(java.util.Set.of(a.getUUID(), b.getUUID())) && open.middle()
                    && open.points() > hidden.points(), "with the wall gone the sample should hold both soldiers by face, the middle and more"
                    + " of the yard: " + open + " / behind the wall: " + hidden);
            ReconWatch watch = new ReconWatch();
            watch.add(hidden);
            watch.add(open);
            watch.add(BaseWatch.reconSample(level, scout, enemy, now + 80));
            Intel.Recon r = watch.recon("ground", scout.npcName());
            t.assertTrue(r.soldiers() == 2 && r.faces().equals(java.util.Set.of(a.getUUID(), b.getUUID())) && r.watchTicks() == 80,
                    "a watch of three samples should count the two soldiers once each: " + r);
            // beyond the recon's sight (72 blocks from the centre): no sample
            BaseState far = enemyBase(t, ENEMY, t.base.center.offset(0, 0, 200));
            t.assertTrue(BaseWatch.reconSample(level, scout, far, now) == null, "a base 200 blocks off was sampled");
            t.assertTrue(BaseWatch.RECON_SIGHT == 72, "the recon's sight: " + BaseWatch.RECON_SIGHT);
            // the grids: a settlement's yard inside its walls, a lair's monsters' ground out to their leash
            BaseState lair = new BaseState();
            lair.kind = BaseState.Kind.LAIR;
            lair.faction = FactionType.HOLLOW;
            lair.center = new BlockPos(0, 64, 0);
            BaseState town = new BaseState();
            town.kind = BaseState.Kind.SETTLEMENT;
            town.center = new BlockPos(0, 64, 0);
            t.assertTrue(BaseWatch.reconReach(lair) == BaseWatch.LAIR_REACH && BaseWatch.LAIR_REACH == 10,
                    "a lair's recon grid reaches " + BaseWatch.reconReach(lair));
            t.assertTrue(BaseWatch.reconReach(town) == com.factionclash.structure.BaseLayout.HALF - 2,
                    "a settlement's recon grid reaches " + BaseWatch.reconReach(town));
            Vec3 middle = BaseWatch.points(lair, 10).get(ReconWatch.MIDDLE);
            t.assertTrue(middle.equals(new Vec3(0.5, 65.5, 0.5)) && BaseWatch.points(lair, 10).get(0).equals(new Vec3(-9.5, 65.5, -9.5)),
                    "the grid's middle point is not the centre: " + middle);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** A focused recon as a scout's watch gives it (recon.md 4). */
    static Intel.Recon recon(int points, boolean middle, long at, java.util.Map<java.util.UUID, Double> faces,
                             java.util.Map<String, Double> heroes) {
        double power = faces.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return new Intel.Recon(faces.size(), power, heroes, Intel.coverage(points), middle, 1200, at, "pillar 12", "Edrin Cole",
                faces.keySet());
    }

    /** A base report a scout delivers, with the recon its watch gave (null: a passing look) and the sides its trip tried. */
    static Report delivered(Report.Kind kind, BlockPos pos, FactionType subject, long observedAt, Intel.Recon recon, int sides) {
        Report r = new Report(kind, pos, observedAt, subject, recon == null ? 0 : recon.soldiers(), "a test report");
        r.recon = recon;
        r.reconSidesTried = sides;
        r.deliveredAt = observedAt;
        r.by = "a test scout";
        return r;
    }

    /**
     * recon.md 10.2 (task R2): a faction's recon of a base, its back-off, its open orders and the strikes it saw survive a
     * save and a reload of the campaign (format 5), each face and power exact; so do what an order's scout told at the
     * station and when the council last ordered an urgent recon of each enemy (task R3). ({@link #oldReportsLoadAsPositions}
     * loads a format-4 save.)
     */
    @GameTest(template = "platform", timeoutTicks = 20, batch = "workers_static")
    public static void aReconAndTheOrdersSurviveASaveAndReload(GameTestHelper h) {
        var provider = h.getLevel().registryAccess();
        FactionState f = new FactionState(SIDE);
        BlockPos town = new BlockPos(100, 64, 200), lairAt = new BlockPos(-300, 70, 40);
        java.util.UUID u1 = java.util.UUID.randomUUID(), u2 = java.util.UUID.randomUUID(), scout = java.util.UUID.randomUUID();
        java.util.Map<java.util.UUID, Double> faces = new java.util.LinkedHashMap<>();
        faces.put(u1, 1.375);
        faces.put(u2, 2.0625);
        Intel.Recon good = recon(12, true, 5000, faces, java.util.Map.of("hinae", 6.8125));
        f.addReport(delivered(Report.Kind.ENEMY_BASE, town, ENEMY, 5000, good, ReconRules.Side.SOUTH.bit()));
        int sides = ReconRules.Side.WEST.bit() | ReconRules.Side.EAST.bit();
        f.addReport(delivered(Report.Kind.ENEMY_BASE, town, ENEMY, 6000, recon(5, false, 6000, java.util.Map.of(), java.util.Map.of()), sides));
        f.addReport(delivered(Report.Kind.LAIR, lairAt, FactionType.HOLLOW, 7000, recon(3, true, 7000, java.util.Map.of(), java.util.Map.of()),
                ReconRules.Side.NORTH.bit()));
        com.factionclash.campaign.ReconOrder urgent = new com.factionclash.campaign.ReconOrder(town, ENEMY, Report.Kind.ENEMY_BASE, 3, 8000, true);
        urgent.scout = scout;
        urgent.takenAt = 8100;
        urgent.reported = ReconRules.ScoutReport.EMPTY; // (its scout told at the station, R3: the next pass ends it)
        urgent.reportedSides = ReconRules.Side.EAST.bit();
        com.factionclash.campaign.ReconOrder ordinary = new com.factionclash.campaign.ReconOrder(lairAt, FactionType.HOLLOW, Report.Kind.LAIR, 3, 8200, false);
        f.reconOrders.add(urgent);
        f.reconOrders.add(ordinary);
        f.struck(ENEMY, 7500);
        f.struck(FactionType.EMBER_LEGION, 7600);
        f.reconUrgentAt.put(ENEMY, 8000L);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("format", CampaignData.FORMAT);
        tag.putBoolean("started", true);
        net.minecraft.nbt.ListTag factions = new net.minecraft.nbt.ListTag();
        factions.add(f.save());
        tag.put("factions", factions);
        net.minecraft.nbt.CompoundTag saved = CampaignData.load(tag, provider).save(new net.minecraft.nbt.CompoundTag(), provider);
        h.assertTrue(saved.getInt("format") == 5, "the campaign format: " + saved.getInt("format"));
        FactionState back = CampaignData.load(saved, provider).factions.get(SIDE);
        Report kept = back.knowledge.stream().filter(r -> r.kind == Report.Kind.ENEMY_BASE).findFirst().orElseThrow();
        h.assertTrue(good.equals(kept.recon), "the recon did not come back whole: " + kept.recon + " / " + good);
        h.assertTrue(kept.recon.faces().equals(java.util.Set.of(u1, u2)) && kept.recon.power() == (1.375 + 2.0625) / 2
                && kept.recon.heroes().get("hinae") == 6.8125, "the faces, powers and heroes: " + kept.recon);
        h.assertTrue(kept.reconFailures == 1 && kept.reconFailedAt == 6000 && kept.reconSidesTried == sides,
                "the back-off after the blind look: " + kept.reconFailures + " at " + kept.reconFailedAt + ", sides " + kept.reconSidesTried);
        Report lair = back.knowledge.stream().filter(r -> r.kind == Report.Kind.LAIR).findFirst().orElseThrow();
        h.assertTrue(lair.recon == null && lair.reconFailures == 1 && lair.reconFailedAt == 7000 && lair.reconSidesTried == ReconRules.Side.NORTH.bit(),
                "a first, blind recon keeps no recon and one failed attempt: " + lair.recon + ", " + lair.reconFailures);
        h.assertTrue(back.reconOrders.size() == 2, "the orders: " + back.reconOrders);
        com.factionclash.campaign.ReconOrder u = back.reconOrders.get(0), o = back.reconOrders.get(1);
        h.assertTrue(u.target.equals(town) && u.subject == ENEMY && u.kind == Report.Kind.ENEMY_BASE && u.homeBase == 3 && u.orderedAt == 8000
                && u.urgent && scout.equals(u.scout) && u.takenAt == 8100 && u.reported == ReconRules.ScoutReport.EMPTY
                && u.reportedSides == ReconRules.Side.EAST.bit(), "the urgent order: " + u);
        h.assertTrue(o.target.equals(lairAt) && o.subject == FactionType.HOLLOW && o.kind == Report.Kind.LAIR && o.orderedAt == 8200
                && !o.urgent && !o.taken() && o.takenAt == 0 && o.reported == ReconRules.ScoutReport.NONE, "the ordinary order: " + o);
        h.assertTrue(back.struckBy.equals(java.util.Map.of(ENEMY, 7500L, FactionType.EMBER_LEGION, 7600L)), "the strikes: " + back.struckBy);
        h.assertTrue(back.reconUrgentAt.equals(java.util.Map.of(ENEMY, 8000L)), "the urgent orders' times: " + back.reconUrgentAt);
        // a watch saved with its scout's job midway (R4b saves it): a face seen before the save and one after are two faces,
        // the points seen on either side of it join, and the watch runs on from its first sample
        int before = 1 << ReconWatch.MIDDLE, after = 1 | 1 << 24;
        java.util.UUID u3 = java.util.UUID.randomUUID(); // u1 seen only before the save, u2 before and after, u3 only after
        ReconWatch mid = new ReconWatch();
        mid.add(new ReconWatch.Sample(before, java.util.Map.of(u1, 1.375), java.util.Map.of("hinae", 6.8125), 1000));
        mid.add(new ReconWatch.Sample(before, java.util.Map.of(u1, 1.375, u2, 2.0625), java.util.Map.of(), 1040));
        ReconWatch resumed = BaseWatch.loadWatch(BaseWatch.saveWatch(mid));
        resumed.add(new ReconWatch.Sample(after, java.util.Map.of(u2, 2.0625), java.util.Map.of(), 1080));
        resumed.add(new ReconWatch.Sample(after, java.util.Map.of(u2, 2.0625, u3, 1.0), java.util.Map.of(), 1120));
        h.assertTrue(resumed.soldiers().keySet().equals(java.util.Set.of(u1, u2, u3)) && resumed.power() == (1.375 + 2.0625 + 1.0) / 3,
                "a watch resumed after a save should count three faces once each: " + resumed.soldiers());
        h.assertTrue(resumed.first() == 1000 && resumed.watchTicks() == 120 && resumed.mask() == (before | after) && resumed.middle()
                && resumed.heroes().equals(java.util.Map.of("hinae", 6.8125)), "a watch resumed after a save: first " + resumed.first()
                + ", " + resumed.watchTicks() + " ticks, mask " + resumed.mask() + ", heroes " + resumed.heroes());
        h.assertTrue(BaseWatch.loadWatch(BaseWatch.saveWatch(new ReconWatch())).empty(), "an empty watch saved and loaded is not empty");
        h.succeed();
    }

    /**
     * recon.md 10.2 item 10 (task R5): a report saved before the council's switch, with an old look at the yard, the losses
     * seen at our walls and the heroes seen there, loads as a position only: no recon, no count, a passing look's note, and
     * none of the old tags written back. Its base is judged again by a recon. A format-4 faction loads with no orders and
     * no strikes; a raid party's look saved with it is dropped, and the ruins a party saw are kept.
     */
    @GameTest(template = "platform", timeoutTicks = 20, batch = "workers_static")
    public static void oldReportsLoadAsPositions(GameTestHelper h) {
        var provider = h.getLevel().registryAccess();
        BlockPos town = new BlockPos(100, 64, 200);
        FactionState old = new FactionState(SIDE);
        old.knowledge.add(new Report(Report.Kind.ENEMY_BASE, town, 4000, ENEMY, 3, "Grove Covenant settlement, 3 soldiers seen, 60% of the yard in view"));
        net.minecraft.nbt.CompoundTag oldFaction = old.save();
        net.minecraft.nbt.CompoundTag report = oldFaction.getList("knowledge", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        net.minecraft.nbt.CompoundTag look = new net.minecraft.nbt.CompoundTag();
        look.putInt("soldiers", 3);
        look.putDouble("visible", 0.6);
        look.putDouble("power", 1.0);
        look.putDouble("heroPower", 6.0);
        look.putLong("at", 4000);
        report.put("look", look);
        report.putString("heroes", "hinae=6.0@4000");
        report.putInt("losses", 2);
        report.putLong("lossAt", 4500);
        oldFaction.remove("reconOrders");
        oldFaction.remove("struckBy");
        oldFaction.remove("reconUrgentAt");
        Report partyLook = new Report(Report.Kind.ENEMY_BASE, town, 4100, ENEMY, 5, "Grove Covenant settlement, 5 soldiers seen");
        oldFaction.put("raidIntel", partyLook.save());
        net.minecraft.nbt.CompoundTag oldTag = new net.minecraft.nbt.CompoundTag();
        oldTag.putInt("format", 4);
        oldTag.putBoolean("started", true);
        net.minecraft.nbt.ListTag oldFactions = new net.minecraft.nbt.ListTag();
        oldFactions.add(oldFaction);
        oldTag.put("factions", oldFactions);
        FactionState loaded = CampaignData.load(oldTag, provider).factions.get(SIDE);
        Report r4 = loaded.knowledge.get(0);
        h.assertTrue(r4.recon == null && r4.reconFailures == 0 && r4.count == 0 && r4.pos.equals(town) && r4.subject == ENEMY
                && r4.note.equals(Report.positionNote(ENEMY, Report.Kind.ENEMY_BASE)), "a format-4 report: recon " + r4.recon + ", count "
                + r4.count + ", note " + r4.note);
        net.minecraft.nbt.CompoundTag back = r4.save();
        for (String tag : new String[]{"look", "heroes", "losses", "lossAt"}) {
            h.assertTrue(!back.contains(tag), "an old tag was written back: " + tag);
        }
        h.assertTrue(loaded.reconOrders.isEmpty() && loaded.struckBy.isEmpty() && loaded.reconUrgentAt.isEmpty(), "a format-4 faction has orders or strikes: "
                + loaded.reconOrders + " / " + loaded.struckBy);
        h.assertTrue(loaded.raidIntel == null, "a raid party's look was kept: " + loaded.raidIntel);
        oldFaction.put("raidIntel", BaseWatch.ruins(enemyRecord(town), 4200).save());
        net.minecraft.nbt.ListTag again = new net.minecraft.nbt.ListTag();
        again.add(oldFaction);
        oldTag.put("factions", again);
        FactionState ruins = CampaignData.load(oldTag, provider).factions.get(SIDE);
        h.assertTrue(ruins.raidIntel != null && ruins.raidIntel.fallen, "the ruins a party saw were dropped: " + ruins.raidIntel);
        h.succeed();
    }

    /** A Grove settlement record at {@code at}, outside any world (for the ruins a report names). */
    private static BaseState enemyRecord(BlockPos at) {
        BaseState b = new BaseState();
        b.faction = ENEMY;
        b.kind = BaseState.Kind.SETTLEMENT;
        b.center = at;
        return b;
    }

    /**
     * recon.md 4's table on the faction's kept report (task R2), by delivery and the watches' times, not by the reports'
     * {@code observedAt}: a passing look never clears the recon; a blind one keeps it and counts a failed attempt; a newer
     * good one replaces it and clears the back-off; a late scout's newer watch delivered after a newer passing look still
     * replaces it, and a late scout's older watch in a report observed later does not; ruins drop it for good. A first
     * report that brings a blind recon keeps no recon and one failure.
     */
    @GameTest(template = "platform", timeoutTicks = 20, batch = "workers_static")
    public static void aKeptReconFollowsTheMergeTableByDelivery(GameTestHelper h) {
        FactionState f = new FactionState(SIDE);
        BlockPos at = new BlockPos(500, 64, -200);
        java.util.function.Supplier<Report> kept = () -> f.knowledge.stream().filter(r -> r.kind == Report.Kind.ENEMY_BASE).findFirst().orElseThrow();
        java.util.Map<java.util.UUID, Double> two = java.util.Map.of(java.util.UUID.randomUUID(), 1.0, java.util.UUID.randomUUID(), 1.0);
        int w = ReconRules.Side.WEST.bit(), e = ReconRules.Side.EAST.bit(), s = ReconRules.Side.SOUTH.bit();
        f.addReport(delivered(Report.Kind.ENEMY_BASE, at, ENEMY, 1000, null, 0));
        h.assertTrue(kept.get().recon == null && kept.get().reconFailures == 0, "a passing look made a recon");
        f.addReport(delivered(Report.Kind.ENEMY_BASE, at, ENEMY, 2000, recon(4, true, 2000, two, java.util.Map.of()), w));
        h.assertTrue(kept.get().recon == null && kept.get().reconFailures == 1 && kept.get().reconFailedAt == 2000 && kept.get().reconSidesTried == w,
                "a blind recon is kept or not counted: " + kept.get().recon + ", " + kept.get().reconFailures);
        Intel.Recon g1 = recon(12, true, 3000, two, java.util.Map.of());
        f.addReport(delivered(Report.Kind.ENEMY_BASE, at, ENEMY, 3000, g1, e));
        h.assertTrue(g1.equals(kept.get().recon) && kept.get().reconFailures == 0 && kept.get().reconSidesTried == 0,
                "a good recon did not replace the blind attempts: " + kept.get().recon + ", " + kept.get().reconFailures);
        f.addReport(delivered(Report.Kind.ENEMY_BASE, at, ENEMY, 5000, null, 0));
        h.assertTrue(g1.equals(kept.get().recon), "a newer passing look cleared the recon");
        // a late scout: its report observed before the kept one, its watch newer than the kept recon
        Intel.Recon g2 = recon(15, true, 4000, java.util.Map.of(), java.util.Map.of());
        f.addReport(delivered(Report.Kind.ENEMY_BASE, at, ENEMY, 4000, g2, 0));
        h.assertTrue(g2.equals(kept.get().recon), "a late scout's newer watch was dropped for an older report: " + kept.get().recon);
        // a report observed later, with a watch older than the kept recon
        f.addReport(delivered(Report.Kind.ENEMY_BASE, at, ENEMY, 6000, recon(20, true, 2500, two, java.util.Map.of()), 0));
        h.assertTrue(g2.equals(kept.get().recon), "an older watch replaced the kept recon: " + kept.get().recon);
        f.addReport(delivered(Report.Kind.ENEMY_BASE, at, ENEMY, 7000, recon(6, false, 7000, java.util.Map.of(), java.util.Map.of()), e | s));
        h.assertTrue(g2.equals(kept.get().recon) && kept.get().reconFailures == 1 && kept.get().reconFailedAt == 7000
                && kept.get().reconSidesTried == (e | s), "a blind recon after a good one: " + kept.get().recon + ", " + kept.get().reconFailures);
        // a late scout's passing look, observed long before the kept report: the back-off stays
        f.addReport(delivered(Report.Kind.ENEMY_BASE, at, ENEMY, 100, null, 0));
        h.assertTrue(g2.equals(kept.get().recon) && kept.get().reconFailures == 1 && kept.get().reconFailedAt == 7000
                && kept.get().reconSidesTried == (e | s), "a late passing look changed the back-off: " + kept.get().reconFailures);
        // a late scout's blind watch, newer than the kept recon, in a report observed before the kept one: one more failure
        f.addReport(delivered(Report.Kind.ENEMY_BASE, at, ENEMY, 6500, recon(5, true, 7500, java.util.Map.of(), java.util.Map.of()), w));
        h.assertTrue(g2.equals(kept.get().recon) && kept.get().reconFailures == 2 && kept.get().reconFailedAt == 7500
                && kept.get().reconSidesTried == (e | s | w), "a late blind watch: " + kept.get().reconFailures + " at " + kept.get().reconFailedAt);
        Intel.Recon g3 = recon(9, true, 8000, two, java.util.Map.of());
        f.addReport(delivered(Report.Kind.ENEMY_BASE, at, ENEMY, 8000, g3, w));
        h.assertTrue(g3.equals(kept.get().recon) && kept.get().reconFailures == 0 && kept.get().reconSidesTried == 0,
                "a newer good recon: " + kept.get().recon + ", " + kept.get().reconFailures);
        Report ruins = delivered(Report.Kind.ENEMY_BASE, at, ENEMY, 9000, null, 0);
        ruins.fallen = true;
        f.addReport(ruins);
        h.assertTrue(kept.get().fallen && kept.get().recon == null, "ruins kept the recon: " + kept.get().recon);
        f.addReport(delivered(Report.Kind.ENEMY_BASE, at, ENEMY, 10_000, recon(12, true, 10_000, two, java.util.Map.of()), 0));
        h.assertTrue(kept.get().fallen && kept.get().recon == null, "ruins took a recon again: " + kept.get().recon);
        h.assertTrue(f.knowledge.size() == 1, "one base, one report: " + f.knowledge.size());
        // on one trip, two looks at a base: the newer watch, good or blind, is what the scout carries home
        Report first = delivered(Report.Kind.ENEMY_BASE, at, ENEMY, 1000, recon(12, true, 1000, two, java.util.Map.of()), w);
        Report blind = delivered(Report.Kind.ENEMY_BASE, at, ENEMY, 1500, recon(3, true, 1500, java.util.Map.of(), java.util.Map.of()), e);
        Report carried = ScoutJob.mergeOnTrip(first, blind);
        h.assertTrue(carried.recon == blind.recon && carried.reconSidesTried == (w | e), "a trip's newer watch: " + carried.recon);
        h.assertTrue(ScoutJob.mergeOnTrip(blind, delivered(Report.Kind.ENEMY_BASE, at, ENEMY, 1600, null, 0)).recon == blind.recon,
                "a passing look on the trip dropped the blind watch");
        h.succeed();
    }
}
