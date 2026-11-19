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
 * the war council judges an enemy garrison by its scouts' looks (a garrison that marched out unseen is still believed at
 * home; one seen gone is raided), lowers it by the soldiers it saw fall at its walls, keeps believing a base stands until
 * one of its people sees the ruins; a raid nobody lived to report on is taken as lost by its plan; a miner's trip is
 * planned at the storehouse; a cave survey is the faction's only when the explorer hands it in; retraining and the
 * blacksmith see only members at home; experience picked up away is carried home; the alarm needs someone to see the
 * intruder; the lookout's line is heard only at home; a lair misses a monster that fell far off only later; a pact ally
 * is told of an offensive when it is launched. Each test registers a small test base (walls half 2, work radius 3: its
 * home area reaches 6 blocks from the centre at relative (12, 2, 12)) and has its own batch.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class KnowledgeGameTests {
    private KnowledgeGameTests() {
    }

    private static final FactionType SIDE = FactionType.TIDE_ALLIANCE, ENEMY = FactionType.GROVE_COVENANT;
    /** Inside the test base's home area, and outside it (Chebyshev 9 from the centre; the edge at 6). */
    private static final int HOME_X = 12, HOME_Z = 15, FAR_X = 12, FAR_Z = 21;
    /** Our soldiers at home, inside the walls' box the council drafts from. */
    private static final int[][] GARRISON = {{10, 10}, {11, 10}, {13, 10}, {14, 10}, {10, 14}, {14, 14}};

    private static GameTestBases.Handle base(GameTestHelper h, int timeout) {
        return GameTestBases.register(h, SIDE, 2, 3, timeout);
    }

    private static FactionNpc member(GameTestBases.Handle t, int x, int z, NpcRole role) {
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
    private static BaseState enemyBase(GameTestBases.Handle t, FactionType faction, BlockPos center) {
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
    private static void deliver(GameTestBases.Handle t, FactionState f, Report r, long now) {
        r.deliveredAt = now;
        r.by = "a test scout";
        f.addReport(r);
    }

    /** The raid fields the director's council touches, restored by the cleanup; the faction's reports of {@code about} removed. */
    private static void keepRaidState(GameTestBases.Handle t, FactionState f) {
        long oldStarted = t.data.startedAt, oldLast = f.lastRaidAt, oldRaid = f.raidStartedAt, oldRally = f.raidRally,
                oldMuster = f.raidMusteredAt, oldPlan = f.raidPlanTicks;
        int oldLaunched = f.raidsLaunched, oldParty = f.raidParty;
        float oldPower = f.soldierPower;
        List<Report> oldKnowledge = new ArrayList<>(f.knowledge);
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
            FactionDirector.LAST_COUNCIL.remove(f.type);
        });
    }

    /** The director's war council (private), run as the director runs it for the base. */
    private static void raids(GameTestBases.Handle t, FactionState f, long at) {
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
    private static void atLeast(ServerLevel level, long ticks) {
        if (level.getGameTime() < ticks && level.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData d) {
            d.setGameTime(ticks);
        }
    }

    private static List<Messages.Said> recording(GameTestBases.Handle t) {
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

    /**
     * Known leak 1, the live case (Stoneguard's garrison left on a raid and two councils struck the emptied base within
     * five seconds): our scout counts six soldiers in the enemy's yard; the council, with three soldiers to send, waits.
     * The garrison marches out where none of ours sees it: the council still believes it at home and still waits. Our
     * scout looks again and sees the yard empty: once that look is home, the council raids, "against about 0 defenders,
     * as seen on day N".
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "knowledge_garrison")
    public static void aCouncilRaidsAGarrisonOnlyWhenItsScoutSawItGone(GameTestHelper h) {
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
            for (int[] p : GARRISON) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState enemy = enemyBase(t, ENEMY, h.absolutePos(new BlockPos(12, 2, 21)));
            List<FactionNpc> garrison = new ArrayList<>();
            int[][] posts = {{11, 20}, {13, 20}, {11, 21}, {13, 21}, {11, 22}, {13, 22}};
            for (int[] p : posts) {
                garrison.add(stranger(t, ENEMY, p[0], p[1], NpcRole.SWORDSMAN));
            }
            FactionNpc scout = member(t, 12, 16, NpcRole.SCOUT);
            Report first = BaseWatch.look(level, scout, enemy, now);
            t.assertTrue(first != null && first.look != null && first.look.soldiers() == 6 && first.look.visible() > 0.9
                    && !first.fallen, "the scout's look at the garrison: " + (first == null ? null : first.note));
            deliver(t, f, first, now);
            raids(t, f, now);
            String council = FactionDirector.LAST_COUNCIL.get(SIDE);
            t.assertTrue(f.raidTarget < 0 && council != null && council.contains("base " + enemy.id) && council.contains("wait"),
                    "three soldiers against the six seen should wait: " + council);
            // the garrison marches out where none of ours sees it
            for (FactionNpc g : garrison) {
                g.discard();
            }
            raids(t, f, now + 1);
            t.assertTrue(f.raidTarget < 0, "the council struck a garrison that left unseen (it read the enemy's own records)");
            t.assertTrue(lines.stream().noneMatch(l -> l.line().contains("Launching a raid")), "a raid was announced: " + lines);
            // our scout looks again: the yard is empty, and its report reaches home
            Report second = BaseWatch.look(level, scout, enemy, now + 2);
            t.assertTrue(second != null && second.look != null && second.look.soldiers() == 0, "the second look: "
                    + (second == null ? null : second.note));
            deliver(t, f, second, now + 2);
            raids(t, f, now + 2);
            t.assertTrue(f.raidTarget == enemy.id, "the council did not strike the garrison its scout saw gone: "
                    + FactionDirector.LAST_COUNCIL.get(SIDE));
            t.assertTrue(said(lines, SIDE, "against about 0 defenders, as seen on day"), "the launch line: " + lines);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * The counter-attack comes from what the faction saw: our scout counted six soldiers at the enemy settlement, four of
     * them then fell at our own walls in sight of home. The council lowers its estimate by them (two left) and, inside
     * the interval, counter-attacks the weakened enemy. Soldiers of that faction killed where none of ours saw do not
     * count.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "knowledge_losses")
    public static void enemySoldiersSeenFallingAtHomeLowerTheEstimate(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 60);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        int oldWitness = FCConfig.WITNESS_RANGE.get();
        t.onCleanup(() -> FCConfig.WITNESS_RANGE.set(oldWitness));
        try {
            List<Messages.Said> lines = recording(t);
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval / 2; // inside the interval: only a counter-attack may go
            for (int[] p : GARRISON) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState enemy = enemyBase(t, ENEMY, h.absolutePos(new BlockPos(12, 2, 21)));
            Report seen = new Report(Report.Kind.ENEMY_BASE, enemy.center, now, ENEMY, 6, "6 soldiers seen");
            seen.look = new Intel.Look(6, 1.0, 1.0, 0, now);
            deliver(t, f, seen, now);
            // one dies far from home where none of ours sees it (the scouts see 4 blocks here): no loss known
            FCConfig.WITNESS_RANGE.set(4);
            FactionNpc unseen = stranger(t, ENEMY, 12, 23, NpcRole.SWORDSMAN);
            kill(unseen);
            Report ours = f.knowledge.stream().filter(r -> r.subject == ENEMY && r.pos.equals(enemy.center)).findFirst().orElseThrow();
            t.assertTrue(ours.losses == 0, "a death none of ours saw lowered the estimate: " + ours.losses);
            // four fall inside our home area
            for (int i = 0; i < 4; i++) {
                FactionNpc g = stranger(t, ENEMY, 11 + i, 13, NpcRole.SWORDSMAN);
                kill(g);
            }
            t.assertTrue(ours.losses == 4, "the soldiers seen falling at our walls: " + ours.losses);
            raids(t, f, now + 1);
            t.assertTrue(f.raidTarget == enemy.id && said(lines, SIDE, "Counter-attack!"),
                    "no counter-attack into the weakened enemy: " + FactionDirector.LAST_COUNCIL.get(SIDE));
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
            seen.look = new Intel.Look(0, 1.0, 0, 0, now);
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
            seen.look = new Intel.Look(0, 1.0, 0, 0, now);
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
            FactionNpc intruder = stranger(t, ENEMY, 15, 15, NpcRole.SWORDSMAN);
            int hidden = FactionDirector.defenceAlarm(level, t.base);
            t.assertTrue(hidden == 0 && guard.getTarget() == null, "a hidden intruder was handed to the guards: " + hidden);
            box.forEach(p -> h.setBlock(p, Blocks.AIR.defaultBlockState()));
            box.clear();
            int seen = FactionDirector.defenceAlarm(level, t.base);
            t.assertTrue(seen >= 1 && guard.getTarget() == intruder, "an intruder in sight was not raised: " + seen);
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

    // ------------------------------------------------------------------ the pact's offensives

    /**
     * A pact member's offensive is told to its allies when it is launched (target, party power, plan): an ally's council
     * joins from that notice, never from the ally's own raid party on the spot.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_pact")
    public static void anAllyIsToldOfAnOffensiveWhenItIsLaunched(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        CampaignData data = t.data;
        FactionState f = data.faction(SIDE);
        FactionState ally = data.faction(FactionType.STONEGUARD);
        keepRaidState(t, f);
        FactionType oldAgainst = data.pactAgainst;
        java.util.Set<FactionType> oldMembers = java.util.EnumSet.noneOf(FactionType.class);
        oldMembers.addAll(data.pactMembers);
        t.onCleanup(() -> {
            data.pactAgainst = oldAgainst;
            data.pactMembers.clear();
            data.pactMembers.addAll(oldMembers);
            ally.pactOffensives.remove(SIDE);
        });
        try {
            for (int[] p : GARRISON) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState ember = enemyBase(t, FactionType.EMBER_LEGION, t.base.center.offset(0, 0, 200));
            data.pactAgainst = FactionType.EMBER_LEGION;
            data.pactMembers.clear();
            data.pactMembers.add(SIDE);
            data.pactMembers.add(FactionType.STONEGUARD);
            String r = FactionDirector.forceRaid(level, data, f, ember);
            t.assertTrue(f.raidTarget == ember.id, "no raid: " + r);
            FactionState.Offensive o = ally.pactOffensives.get(SIDE);
            t.assertTrue(o != null && o.target() == ember.id && o.power() >= f.raidParty && o.until() == f.raidStartedAt + f.raidPlanTicks,
                    "the ally was not told of the offensive as launched: " + o);
            // the party's losses on the spot are its own: the notice the ally holds does not change with them
            f.raiders.clear();
            t.assertTrue(ally.pactOffensives.get(SIDE) == o, "the ally's notice changed with the party's roll");
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
            seen.look = new Intel.Look(0, 1.0, 0, 0, now);
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
            seen.look = new Intel.Look(0, 1.0, 0, 0, now);
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
            t.assertTrue(yard != null && !yard.fallen && yard.look != null && yard.look.visible() > 0,
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
     * Review finding 3: enemy soldiers seen falling at our walls lower the estimate while the loss is fresh (a
     * counter-attack), but a base hires replacements, so a loss counts for less as it ages, as a look does. Before, eight
     * of nine seen falling three days ago still made the garrison one soldier, and the council struck it.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_lossesfade")
    public static void soldiersSeenFallingLongAgoAreReplacedInTheEstimate(GameTestHelper h) {
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
            BaseState enemy = enemyBase(t, ENEMY, t.base.center.offset(0, 0, 200));
            Report seen = new Report(Report.Kind.ENEMY_BASE, enemy.center, now, ENEMY, 9, "9 soldiers seen");
            seen.look = new Intel.Look(9, 1.0, 2.0, 0, now);
            deliver(t, f, seen, now);
            // eight of them fall at our walls, in sight of home
            for (int i = 0; i < 8; i++) {
                kill(stranger(t, ENEMY, 10 + i % 5, 13 + i / 5, NpcRole.SWORDSMAN));
            }
            Report ours = f.knowledge.stream().filter(r -> r.subject == ENEMY && r.pos.equals(enemy.center)).findFirst().orElseThrow();
            t.assertTrue(ours.losses == 8, "the soldiers seen falling at our walls: " + ours.losses);
            raids(t, f, now + 3L * Whereabouts.TICKS_PER_DAY);
            t.assertTrue(f.raidTarget < 0, "three days after its losses the garrison is still believed one soldier strong: "
                    + FactionDirector.LAST_COUNCIL.get(SIDE));
            raids(t, f, now + 1);
            t.assertTrue(f.raidTarget == enemy.id, "fresh losses should still weaken the estimate: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Review finding 4: a hero seen at a base counts in the estimate while the sighting is fresh, and less as it ages (a
     * hero goes raiding, falls, or was only passing through). Before, a hero seen once was counted there for ever at full
     * strength, and a council never struck that base again.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_heroesfade")
    public static void aHeroSeenLongAgoCountsForLessAsTheSightingAges(GameTestHelper h) {
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
            BaseState enemy = enemyBase(t, ENEMY, t.base.center.offset(0, 0, 200));
            Report first = new Report(Report.Kind.ENEMY_BASE, enemy.center, now, ENEMY, 4, "4 soldiers and a hero seen");
            first.look = new Intel.Look(4, 1.0, 1.0, 6.0, now);
            first.heroes = "review_hero=6.0"; // as saved before the sightings kept their time
            deliver(t, f, first, now);
            raids(t, f, now);
            t.assertTrue(f.raidTarget < 0, "a hero seen there just now should hold three soldiers back: " + FactionDirector.LAST_COUNCIL.get(SIDE));
            long later = now + 3L * Whereabouts.TICKS_PER_DAY;
            Report second = new Report(Report.Kind.ENEMY_BASE, enemy.center, later, ENEMY, 4, "4 soldiers seen");
            second.look = new Intel.Look(4, 1.0, 1.0, 0, later);
            deliver(t, f, second, later);
            raids(t, f, later);
            t.assertTrue(f.raidTarget == enemy.id, "a hero seen there three days ago still holds the council back: "
                    + FactionDirector.LAST_COUNCIL.get(SIDE));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Review finding 5: against a garrison none of its people has seen (assumed like its own), the council's rule "attack
     * with two thirds of the defence" compared a party of every soldier beyond a third with two thirds of the whole
     * garrison: only 9 and 12 soldiers ever matched it (at exact equality), so ten soldiers waited where nine attacked.
     * Two thirds is counted in whole soldiers, as the council itself rounds its garrison.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_twothirds")
    public static void tenSoldiersAttackAGarrisonLikeTheirOwnWhereNineDo(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        keepRaidState(t, f);
        try {
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval - 1;
            for (int[] p : TEN) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState enemy = enemyBase(t, ENEMY, t.base.center.offset(0, 0, 200));
            Report walls = new Report(Report.Kind.ENEMY_BASE, enemy.center, now, ENEMY, 0, "its walls seen, nothing of the yard");
            walls.look = new Intel.Look(0, 0.0, 0, 0, now);
            deliver(t, f, walls, now);
            raids(t, f, now);
            t.assertTrue(f.raidTarget == enemy.id && f.raiders.size() == 6, "ten soldiers waited against a garrison like their own: "
                    + FactionDirector.LAST_COUNCIL.get(SIDE));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * A scout counts only the soldiers it sees: two of the enemy's soldiers behind a wall two blocks high are not in its
     * look, nor the yard behind it (the walls' top is, so the base is reported); with the wall gone it counts both.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "knowledge_wall")
    public static void aScoutCountsOnlySoldiersInSight(GameTestHelper h) {
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
            t.assertTrue(hidden != null && hidden.look != null && hidden.look.soldiers() == 0,
                    "the scout counted soldiers behind a wall: " + (hidden == null ? null : hidden.note));
            wall.forEach(p -> h.setBlock(p, Blocks.AIR));
            Report open = BaseWatch.look(level, scout, enemy, now + 1);
            t.assertTrue(open != null && open.look != null && open.look.soldiers() == 2 && open.look.visible() > hidden.look.visible(),
                    "with the wall gone the scout should see both soldiers and more of the yard: " + (open == null ? null : open.note)
                            + " / behind the wall: " + hidden.note);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
