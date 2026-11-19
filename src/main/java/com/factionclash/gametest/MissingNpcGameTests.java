package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.FactionDirector;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.HeroState;
import com.factionclash.campaign.News;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.campaign.Report;
import com.factionclash.campaign.RollCall;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroManager;
import com.factionclash.logic.campaign.Whereabouts;
import com.factionclash.logic.campaign.Whereabouts.Event;
import com.factionclash.logic.campaign.Whereabouts.State;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

import static com.factionclash.gametest.HeroAbilityGameTests.MELEE;
import static com.factionclash.gametest.HeroBossGameTests.IDLE_MOVE;

/**
 * Deaths far from home are not known at once ({@code docs/design/missing_npcs.md}, the owner's request of 2026-11-13):
 * a faction knows only what its people saw, and knowledge travels home with someone. Each test registers a small test
 * base (walls half 2, work radius 3: its home area reaches 6 blocks from the centre at relative (12, 2, 12)), so a member
 * at relative (12, 2, 21) is away while the whole platform lies within a witness's 32 blocks. Members stand still
 * ({@code noAi}); the director's waits and a member's sighting (its record timer's call) are run by the tests at the
 * times they name, and the roll call's lines are recorded. Each test has its own batch and cleans up.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class MissingNpcGameTests {
    private MissingNpcGameTests() {
    }

    private static final FactionType SIDE = FactionType.TIDE_ALLIANCE;
    /** Inside the test base's home area, and well outside it (Chebyshev 9 from the centre, the home area's edge at 6). */
    private static final int HOME_X = 12, HOME_Z = 15, FAR_X = 12, FAR_Z = 21;

    private static GameTestBases.Handle base(GameTestHelper h, int timeout) {
        return GameTestBases.register(h, SIDE, 2, 3, timeout);
    }

    private static List<RollCall.Told> recording(GameTestBases.Handle t) {
        List<RollCall.Told> lines = RollCall.record();
        t.onCleanup(RollCall::stopRecording);
        return lines;
    }

    /** A still member of the base in {@code role} at relative (x, 2, z). */
    private static FactionNpc member(GameTestBases.Handle t, int x, int z, NpcRole role) {
        FactionNpc n = t.npc(x, z, role);
        n.setNoAi(true);
        return n;
    }

    /** Moves an entity to the middle of relative cell (x, 2, z). */
    private static void put(GameTestBases.Handle t, LivingEntity e, int x, int z) {
        Vec3 at = Vec3.atBottomCenterOf(t.helper.absolutePos(new BlockPos(x, 2, z)));
        e.moveTo(at.x, at.y, at.z, e.getYRot(), 0F);
        e.setDeltaMovement(Vec3.ZERO);
    }

    /** Moves a member and lets its faction see where it is now (what its record timer does every 2 s). */
    private static void goTo(GameTestBases.Handle t, FactionNpc n, int x, int z) {
        put(t, n, x, z);
        RollCall.sighting(n, t.data.record(n.getUUID()));
    }

    private static void kill(LivingEntity e) {
        e.invulnerableTime = 0;
        e.hurt(e.damageSources().genericKill(), Float.MAX_VALUE);
    }

    private static List<RollCall.Told> about(List<RollCall.Told> lines, UUID who) {
        synchronized (lines) {
            return lines.stream().filter(l -> who.equals(l.who())).toList();
        }
    }

    private static boolean said(List<RollCall.Told> lines, UUID who, Event e) {
        return about(lines, who).stream().anyMatch(l -> l.event() == e);
    }

    /** The swordsmen the faction counts at this test base. */
    private static long swordsmen(GameTestBases.Handle t) {
        return t.data.faction(SIDE).roster.values().stream()
                .filter(x -> x.alive && x.baseId == t.base.id && x.role == NpcRole.SWORDSMAN).count();
    }

    private static long grace() {
        return FCConfig.MISSING_GRACE_TICKS.get();
    }

    private static long presume() {
        return FCConfig.PRESUMED_DEAD_TICKS.get();
    }

    // ------------------------------------------------------------------ the owner's case

    /**
     * A scout leaves the home area (away, expected back by its expedition's bound) and is killed far away where nobody
     * sees it: nothing is known, it still counts, nothing is said. Past its expected return plus the grace it is missing
     * (still counted: no scout is hired); after the presumed-dead wait it is presumed dead, no longer counted, and the
     * base's needs ask for a scout to replace it.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "missing_scout")
    public static void aScoutKilledFarAwayIsMissingThenPresumedDeadThenReplaced(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 100);
        try {
            List<RollCall.Told> lines = recording(t);
            ServerLevel level = h.getLevel();
            FactionNpc scout = member(t, HOME_X, HOME_Z, NpcRole.SCOUT);
            NpcRecord r = t.data.record(scout.getUUID());
            RollCall.sighting(scout, r);
            t.assertTrue(r.where == State.HERE && r.alive, "a member at home is here: " + r.where);
            long left = level.getGameTime();
            goTo(t, scout, FAR_X, FAR_Z);
            t.assertTrue(r.where == State.AWAY && r.leftAt == left, "it was not seen leaving: " + r.where);
            t.assertTrue(r.expectedBy == left + FCConfig.SCOUT_OVERDUE_TICKS.get(),
                    "a scout's trip is its expedition's bound: expected " + r.expectedBy + ", left " + left);
            kill(scout);
            t.assertTrue(scout.isDeadOrDying() && r.dead && !r.witnessed, "the scout should have died unseen");
            t.assertTrue(r.alive && r.where == State.AWAY, "a death nobody saw is not known: " + r.where);
            t.assertTrue(about(lines, scout.getUUID()).isEmpty(), "something was said: " + lines);
            t.assertTrue(FactionDirector.needs(level, t.data, t.base, left).stream().noneMatch(n -> n.role() == NpcRole.SCOUT),
                    "a scout believed alive was replaced");
            // the director's waits: nothing until the expected return plus the grace
            RollCall.upkeep(level, t.data, t.base, r.expectedBy + grace());
            t.assertTrue(r.where == State.AWAY, "missing before the grace ran out");
            long missingAt = r.expectedBy + grace() + 1;
            RollCall.upkeep(level, t.data, t.base, missingAt);
            t.assertTrue(r.where == State.MISSING && r.alive && r.since == missingAt, "not missing after the grace: " + r.where);
            t.assertTrue(said(lines, scout.getUUID(), Event.MISSING), "no missing line: " + lines);
            t.assertTrue(about(lines, scout.getUUID()).get(0).line().contains("is missing: expected back by day"), "the line: " + lines);
            t.assertTrue(FactionDirector.needs(level, t.data, t.base, missingAt).stream().noneMatch(n -> n.role() == NpcRole.SCOUT),
                    "a missing scout is not replaced yet");
            RollCall.upkeep(level, t.data, t.base, missingAt + presume() - 1);
            t.assertTrue(r.where == State.MISSING, "presumed dead before the wait ran out");
            RollCall.upkeep(level, t.data, t.base, missingAt + presume());
            t.assertTrue(r.where == State.PRESUMED_DEAD && !r.alive, "not presumed dead after the wait: " + r.where);
            t.assertTrue(said(lines, scout.getUUID(), Event.PRESUMED_DEAD), "no presumed-dead line: " + lines);
            t.assertTrue(about(lines, scout.getUUID()).stream().anyMatch(l -> l.event() == Event.PRESUMED_DEAD
                    && l.line().endsWith("is presumed dead (missing since day " + Whereabouts.day(missingAt) + ")")), "the line: " + lines);
            t.assertTrue(FactionDirector.needs(level, t.data, t.base, missingAt + presume()).stream()
                    .anyMatch(n -> n.role() == NpcRole.SCOUT && n.reason().contains("replacing")), "the scout is not replaced");
            t.assertTrue(t.data.faction(SIDE).roster.get(scout.getUUID()) == r, "the record must be kept");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * A death inside the home area is known at once ("was killed"); so is one outside it seen by a member standing at
     * home; neither waits for anything.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "missing_home")
    public static void aDeathNearTheBaseOrSeenFromHomeIsKnownAtOnce(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 100);
        try {
            List<RollCall.Told> lines = recording(t);
            FactionNpc farmer = member(t, HOME_X, HOME_Z, NpcRole.FARMER);
            NpcRecord r = t.data.record(farmer.getUUID());
            kill(farmer);
            t.assertTrue(r.where == State.KNOWN_DEAD && !r.alive && r.dead, "a death at home is known at once: " + r.where);
            t.assertTrue(about(lines, farmer.getUUID()).stream().anyMatch(l -> l.event() == Event.KILLED && l.line().endsWith("(Farmer) was killed")),
                    "no line: " + lines);
            // outside the home area, seen by a member at home 6 blocks away
            FactionNpc woodcutter = member(t, HOME_X, HOME_Z, NpcRole.WOODCUTTER);
            FactionNpc watcher = member(t, HOME_X + 3, HOME_Z, NpcRole.FARMER);
            NpcRecord w = t.data.record(woodcutter.getUUID());
            goTo(t, woodcutter, FAR_X, FAR_Z);
            t.assertTrue(w.where == State.AWAY, "the woodcutter did not leave");
            kill(woodcutter);
            t.assertTrue(w.where == State.KNOWN_DEAD && !w.alive && w.witnessed, "seen from home is known at once: " + w.where);
            t.assertTrue(said(lines, woodcutter.getUUID(), Event.KILLED), "no line: " + lines);
            t.assertTrue(!t.data.faction(SIDE).news.containsKey(watcher.getUUID()), "a witness at home carries nothing: it told");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Two members away; one is killed and the other sees it (4 blocks, a clear line): nothing is known at home, the
     * witness carries the news. When it comes home its record timer tells it: known dead, "brings news ... was killed
     * near x, z on day N".
     */
    @GameTest(template = "platform", timeoutTicks = 120, batch = "missing_witness")
    public static void aWitnessWhoSeesADeathAndComesHomeReportsIt(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 120);
        try {
            List<RollCall.Told> lines = recording(t);
            FactionNpc victim = member(t, HOME_X, HOME_Z, NpcRole.WOODCUTTER);
            FactionNpc witness = member(t, HOME_X, HOME_Z, NpcRole.SCOUT);
            witness.setNpcName("Mara Vey");
            NpcRecord v = t.data.record(victim.getUUID());
            goTo(t, victim, FAR_X, FAR_Z);
            goTo(t, witness, FAR_X - 4, FAR_Z);
            kill(victim);
            FactionState f = t.data.faction(SIDE);
            t.assertTrue(v.alive && v.where == State.AWAY && v.witnessed, "known at once with no witness at home: " + v.where);
            List<News> carried = f.news.get(witness.getUUID());
            t.assertTrue(carried != null && carried.size() == 1 && carried.get(0).id.equals(victim.getUUID()),
                    "the witness carries nothing: " + f.news);
            t.assertTrue(about(lines, victim.getUUID()).isEmpty(), "said before anyone came home: " + lines);
            h.runAfterDelay(5, t.guard(() -> put(t, witness, HOME_X, HOME_Z))); // home: its own record timer tells
            h.runAfterDelay(60, t.guard(() -> {
                t.assertTrue(v.where == State.KNOWN_DEAD && !v.alive, "the witness came home and told nothing: " + v.where);
                RollCall.Told l = about(lines, victim.getUUID()).stream().filter(x -> x.event() == Event.KILLED_REPORTED).findFirst().orElse(null);
                t.assertTrue(l != null && l.line().startsWith("Mara Vey brings news: ") && l.line().contains("(Woodcutter) was killed near "),
                        "the line: " + lines);
                t.assertTrue(f.news.get(witness.getUUID()) == null, "the news was not handed over");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** As above, but the witness is killed away too, unseen: its news dies with it, and nothing is known of either. */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "missing_dead_witness")
    public static void aDeadWitnessCarriesNothing(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 100);
        try {
            List<RollCall.Told> lines = recording(t);
            FactionNpc victim = member(t, HOME_X, HOME_Z, NpcRole.WOODCUTTER);
            FactionNpc witness = member(t, HOME_X, HOME_Z, NpcRole.SCOUT);
            NpcRecord v = t.data.record(victim.getUUID()), w = t.data.record(witness.getUUID());
            goTo(t, victim, FAR_X, FAR_Z);
            goTo(t, witness, FAR_X - 4, FAR_Z);
            kill(victim);
            FactionState f = t.data.faction(SIDE);
            t.assertTrue(f.news.containsKey(witness.getUUID()), "the witness should carry the news first");
            put(t, witness, 2, 21); // out of sight of no one: it dies alone
            kill(witness);
            t.assertTrue(!f.news.containsKey(witness.getUUID()), "a dead witness still carries: " + f.news);
            t.assertTrue(v.alive && v.where == State.AWAY && w.alive && w.where == State.AWAY,
                    "a death nobody alive saw became known: " + v.where + ", " + w.where);
            t.assertTrue(lines.isEmpty(), "something was said: " + lines);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * A member player away who sees a death carries it as an NPC witness would, and tells it when it reports at its
     * faction's station; a player who dies loses what it carried (the death event drops it).
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "missing_player")
    public static void aMemberPlayerReportsWhatItSawAtTheStation(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 100);
        try {
            List<RollCall.Told> lines = recording(t);
            FactionNpc victim = member(t, HOME_X, HOME_Z, NpcRole.MINER);
            FactionNpc second = member(t, HOME_X, HOME_Z, NpcRole.FARMER);
            NpcRecord v = t.data.record(victim.getUUID()), s = t.data.record(second.getUUID());
            ServerPlayer p = HeroAbilityGameTests.player(t, FAR_X - 4, FAR_Z, SIDE, 20);
            goTo(t, victim, FAR_X, FAR_Z);
            goTo(t, second, FAR_X, FAR_Z - 1);
            kill(victim);
            FactionState f = t.data.faction(SIDE);
            // the farmer away saw it too; take its copy (it is told by whoever comes home first) to test the player alone
            f.news.remove(second.getUUID());
            t.assertTrue(f.news.containsKey(p.getUUID()) && v.where == State.AWAY, "the player carries nothing: " + f.news);
            t.assertTrue(RollCall.playerReport(p, t.base) == 1, "the report told nothing");
            t.assertTrue(v.where == State.KNOWN_DEAD && said(lines, victim.getUUID(), Event.KILLED_REPORTED), "not reported: " + lines);
            // a second death it sees, then it dies: nothing to report
            kill(second);
            t.assertTrue(f.news.containsKey(p.getUUID()), "the player did not see the second death");
            net.neoforged.neoforge.common.CommonHooks.onLivingDeath(p, p.damageSources().genericKill());
            t.assertTrue(!f.news.containsKey(p.getUUID()) && s.where == State.AWAY, "a dead player still carries: " + f.news);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ a return

    /**
     * A raider (on a raid, its trip bounded by the raid's) is presumed dead after its waits and replaced; it walks back
     * in alive: it rejoins (counted again, here, its own sword still in hand), "presumed dead, has come back and
     * rejoins", and its replacement stays.
     */
    @GameTest(template = "platform", timeoutTicks = 140, batch = "missing_raider")
    public static void aRaiderPresumedDeadWhoComesBackRejoins(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 140);
        GameTestBases.Handle target = GameTestBases.register(h, FactionType.STONEGUARD, 2, 3, 0);
        t.onCleanup(target::cleanup);
        FactionState f = t.data.faction(SIDE);
        t.onCleanup(() -> {
            f.raidTarget = -1;
            f.raiders.clear();
        });
        try {
            List<RollCall.Told> lines = recording(t);
            ServerLevel level = h.getLevel();
            FactionNpc raider = member(t, HOME_X, HOME_Z, NpcRole.SWORDSMAN);
            raider.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            NpcRecord r = t.data.record(raider.getUUID());
            long now = level.getGameTime();
            f.raidTarget = target.base.id;
            f.raiders.add(raider.getUUID());
            f.raidStartedAt = now;
            goTo(t, raider, FAR_X, FAR_Z);
            t.assertTrue(r.where == State.AWAY && r.expectedBy == now + Whereabouts.RAID_LIMIT_TICKS,
                    "a raider is expected back by the raid's bound (the target is beside home): " + r.expectedBy + " at " + now);
            RollCall.upkeep(level, t.data, t.base, r.expectedBy + grace() + 1);
            RollCall.upkeep(level, t.data, t.base, r.since + presume());
            t.assertTrue(r.where == State.PRESUMED_DEAD && !r.alive && !r.dead, "not presumed dead: " + r.where);
            t.assertTrue(swordsmen(t) == 0, "a presumed-dead raider still counts");
            FactionNpc replacement = member(t, HOME_X + 2, HOME_Z, NpcRole.SWORDSMAN); // what the recruitment brings
            h.runAfterDelay(5, t.guard(() -> put(t, raider, HOME_X, HOME_Z))); // it walks back in
            h.runAfterDelay(60, t.guard(() -> {
                t.assertTrue(r.where == State.HERE && r.alive, "it did not rejoin: " + r.where);
                RollCall.Told l = about(lines, raider.getUUID()).stream().filter(x -> x.event() == Event.REJOINED).findFirst().orElse(null);
                t.assertTrue(l != null && l.line().endsWith("(Swordsman), presumed dead, has come back and rejoins"), "the line: " + lines);
                t.assertTrue(raider.getMainHandItem().is(Items.IRON_SWORD), "its gear is its own: " + raider.getMainHandItem());
                t.assertTrue(swordsmen(t) == 2 && t.data.record(replacement.getUUID()).alive,
                        "the returner and its replacement both count: " + swordsmen(t));
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * A hero killed far away where nobody sees it: its faction hears nothing (no fall, no "was killed"), it still counts,
     * and its revival is timed as always ({@code heroes.reviveSeconds}). When it rises at home, that is how its faction
     * learns: "fell where none of the faction saw it and returns to ...". A hero killed at home says its fall at once.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "missing_hero")
    public static void aHeroKilledFarAwayRisesAtHomeAndThatIsHowItsFactionLearns(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 100);
        try {
            List<RollCall.Told> lines = recording(t);
            ServerLevel level = h.getLevel();
            String id = "fixture_far_hero";
            HeroEntity hero = HeroAbilityGameTests.hero(t, id, "katana", HeroAbilityGameTests.def(id, MELEE, "striker", IDLE_MOVE, ""),
                    HOME_X, HOME_Z);
            hero.setNoAi(true);
            UUID first = hero.getUUID();
            NpcRecord r = t.data.record(first);
            goTo(t, hero, FAR_X, FAR_Z);
            t.assertTrue(r.where == State.AWAY, "the hero did not leave: " + r.where);
            long now = level.getGameTime();
            kill(hero);
            HeroState s = t.data.heroes.get(id);
            t.assertTrue(hero.isDeadOrDying() && !s.alive, "the hero did not fall");
            t.assertTrue(Math.abs(s.reviveAt - (now + FCConfig.HERO_REVIVE_SECONDS.get() * 20L)) <= 1,
                    "the revival is not unchanged: " + s.reviveAt + " at " + now);
            t.assertTrue(r.alive && r.where == State.AWAY, "a hero's fall nobody saw is known: " + r.where);
            t.assertTrue(about(lines, first).isEmpty(), "its fall was told: " + lines);
            s.reviveAt = level.getGameTime(); // its ten minutes are over
            HeroManager.tick(level.getServer());
            HeroEntity back = HeroManager.entity(level.getServer(), id);
            t.assertTrue(back != null && back.isAlive() && !back.getUUID().equals(first), "the hero did not rise");
            back.setNoAi(true);
            RollCall.Told risen = about(lines, back.getUUID()).stream().filter(x -> x.event() == Event.KILLED_REPORTED).findFirst().orElse(null);
            t.assertTrue(risen != null && risen.line().contains("fell where none of the faction saw it and returns to " + t.base.name),
                    "its rising did not tell its fall: " + lines);
            t.assertTrue(t.data.faction(SIDE).roster.get(first) == null && t.data.record(back.getUUID()).where == State.HERE,
                    "the old entry should be replaced by the risen hero's");
            // a fall at home is told at once, and its rising is a plain return
            lines.clear();
            kill(back);
            t.assertTrue(said(lines, back.getUUID(), Event.KILLED), "a fall at home was not told: " + lines);
            t.assertTrue(about(lines, back.getUUID()).stream().anyMatch(x -> x.line().contains("has fallen at")), "the line: " + lines);
            s.reviveAt = level.getGameTime();
            HeroManager.tick(level.getServer());
            HeroEntity again = HeroManager.entity(level.getServer(), id);
            t.assertTrue(again != null && about(lines, again.getUUID()).stream().anyMatch(x -> x.event() == Event.BACK
                    && x.line().endsWith("returns to " + t.base.name + ".")), "a known fall's return: " + lines);
            again.setNoAi(true);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ old worlds

    /**
     * Saves from before the roll call load: a living member is here, a scout that had set out is away (expected back by
     * its departure plus the overdue bound), a dead one known dead; a faction with no news list has no news. The new
     * fields and the news survive a save and a load.
     */
    @GameTest(template = "platform", timeoutTicks = 20, batch = "workers_static")
    public static void oldRosterEntriesAndFactionsLoad(GameTestHelper h) {
        CompoundTag oldScout = oldEntry(NpcRole.SCOUT, true, 5000, 6000);
        CompoundTag oldFarmer = oldEntry(NpcRole.FARMER, true, 0, 7000);
        CompoundTag oldDead = oldEntry(NpcRole.MINER, false, 0, 8000);
        NpcRecord scout = NpcRecord.load(oldScout), farmer = NpcRecord.load(oldFarmer), dead = NpcRecord.load(oldDead);
        h.assertTrue(scout.where == State.AWAY && scout.alive && scout.expectedBy == 5000 + FCConfig.SCOUT_OVERDUE_TICKS.get(),
                "an old scout away: " + scout.where + " by " + scout.expectedBy);
        h.assertTrue(farmer.where == State.HERE && farmer.alive && !farmer.dead, "an old living member: " + farmer.where);
        h.assertTrue(dead.where == State.KNOWN_DEAD && !dead.alive && dead.dead, "an old dead member: " + dead.where);
        // a faction saved before: no news list
        CompoundTag ft = new CompoundTag();
        ft.putString("type", SIDE.id);
        ft.putBoolean("alive", true);
        ListTag ros = new ListTag();
        ros.add(oldScout);
        ft.put("roster", ros);
        FactionState f = FactionState.load(ft);
        h.assertTrue(f != null && f.news.isEmpty() && f.roster.size() == 1, "an old faction did not load");
        // the new fields and the news round-trip
        dead.diedPos = new BlockPos(10, 64, -20);
        dead.witnessed = false;
        NpcRecord again = NpcRecord.load(dead.save());
        h.assertTrue(again.where == State.KNOWN_DEAD && again.dead && !again.witnessed && new BlockPos(10, 64, -20).equals(again.diedPos),
                "a record did not round-trip");
        UUID carrier = UUID.randomUUID();
        f.news.computeIfAbsent(carrier, k -> new java.util.ArrayList<>()).add(new News(scout.uuid, News.Kind.DEATH, "Old Scout (Scout)",
                false, 3, 4, 9000));
        FactionState back = FactionState.load(f.save());
        h.assertTrue(back.news.get(carrier) != null && back.news.get(carrier).size() == 1
                && back.news.get(carrier).get(0).id.equals(scout.uuid) && back.news.get(carrier).get(0).at == 9000, "the news did not round-trip");
        h.succeed();
    }

    /** A roster entry as saved before 2026-11-19 (no whereabouts keys). */
    private static CompoundTag oldEntry(NpcRole role, boolean alive, long departed, long seen) {
        CompoundTag t = new CompoundTag();
        t.putUUID("uuid", UUID.randomUUID());
        t.putString("name", "Old " + role.displayName);
        t.putString("role", role.id);
        t.putInt("base", 1);
        t.putBoolean("alive", alive);
        t.putLong("pos", BlockPos.ZERO.asLong());
        t.putLong("seen", seen);
        t.putLong("departed", departed);
        t.putInt("level", 1);
        return t;
    }

    // ------------------------------------------------------------------ the review of 2026-11-19

    /** The director's {@code raids} (private), run as the director runs it for the base. */
    private static void raids(GameTestBases.Handle t, FactionState f, long at) {
        try {
            var m = FactionDirector.class.getDeclaredMethod("raids", ServerLevel.class, CampaignData.class, FactionState.class,
                    BaseState.class, long.class);
            m.setAccessible(true);
            m.invoke(null, t.helper.getLevel(), t.data, f, t.base, at);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A far settlement of the Grove the faction's scouts reported (no one of it lives there): a raid target. */
    private static BaseState reportedTarget(GameTestBases.Handle t, FactionState f, long now) {
        BaseState target = new BaseState();
        target.faction = FactionType.GROVE_COVENANT;
        target.kind = BaseState.Kind.SETTLEMENT;
        target.center = new BlockPos(940_000, 70, 940_000);
        target.name = "Missing review raid target";
        t.data.addBase(target);
        Report seen = new Report(Report.Kind.ENEMY_BASE, target.center, now, FactionType.GROVE_COVENANT, 0, "");
        f.knowledge.add(seen);
        long oldStarted = t.data.startedAt, oldLastRaid = f.lastRaidAt, oldRaidStarted = f.raidStartedAt, oldRally = f.raidRally,
                oldMustered = f.raidMusteredAt;
        int oldLaunched = f.raidsLaunched, oldParty = f.raidParty;
        float oldPower = f.soldierPower;
        t.onCleanup(() -> {
            t.data.bases.remove(target.id);
            f.knowledge.remove(seen);
            f.raidTarget = -1;
            f.raiders.clear();
            f.lastParty.clear();
            f.raidEndNews = null;
            t.data.startedAt = oldStarted;
            f.lastRaidAt = oldLastRaid;
            f.raidStartedAt = oldRaidStarted;
            f.raidRally = oldRally;
            f.raidMusteredAt = oldMustered;
            f.raidsLaunched = oldLaunched;
            f.raidParty = oldParty;
            f.soldierPower = oldPower;
        });
        return target;
    }

    /**
     * The war council hears of its raid's end only through its people: a party whose one raider died where none of the
     * faction saw it ends on the spot, but the council, which heard nothing, sends no new raid until the raid's own bound
     * has passed. A party whose every raider died in sight of home is known to be gone: the council plans again at once.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "missing_rev_raid")
    public static void theCouncilWaitsForNewsOfARaidNobodyLivedToReportOn(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 100);
        ServerLevel level = h.getLevel();
        CampaignData data = t.data;
        FactionState f = data.faction(SIDE);
        long now = level.getGameTime();
        int interval = FCConfig.RAID_INTERVAL_TICKS.get();
        int oldWitness = FCConfig.WITNESS_RANGE.get();
        t.onCleanup(() -> FCConfig.WITNESS_RANGE.set(oldWitness));
        try {
            t.assertTrue(f.raidTarget < 0, "the faction already has a raid under way");
            BaseState target = reportedTarget(t, f, now);
            List<RollCall.Told> lines = recording(t);
            // six swordsmen at home, inside the walls' box the council drafts from
            int[][] spots = {{10, 10}, {11, 10}, {13, 10}, {14, 10}, {10, 14}, {14, 14}};
            for (int[] p : spots) {
                member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            data.startedAt = now - interval - 1;
            // the last raid: one raider, away, killed where none of the faction sees it (the swordsmen see 4 blocks here)
            FCConfig.WITNESS_RANGE.set(4);
            FactionNpc raider = member(t, HOME_X, HOME_Z, NpcRole.SWORDSMAN);
            NpcRecord r = data.record(raider.getUUID());
            f.raidTarget = target.id;
            f.raiders.clear();
            f.raiders.add(raider.getUUID());
            f.lastParty.clear();
            f.raidParty = 1;
            f.raidStartedAt = now;
            f.lastRaidAt = now - interval - 1;
            goTo(t, raider, FAR_X, FAR_Z);
            kill(raider);
            t.assertTrue(r.dead && r.alive && !r.witnessed, "the raider's death should be unseen: " + r.where);
            raids(t, f, now);
            t.assertTrue(f.raidTarget < 0 && f.raiders.isEmpty(), "a party nobody lives in ends on the spot");
            t.assertTrue(f.raidEndNews != null, "the council heard at once of an end nobody brought home");
            int launched = f.raidsLaunched;
            raids(t, f, now + 1);
            t.assertTrue(f.raidTarget < 0 && f.raidsLaunched == launched, "the council sent a new raid after a party it heard nothing of");
            t.assertTrue(lines.isEmpty(), "something was said: " + lines);
            // past the raid's own bound the council takes it as over, and plans again
            long later = now + Whereabouts.RAID_LIMIT_TICKS + 1;
            f.lastRaidAt = later - interval - 1;
            raids(t, f, later);
            t.assertTrue(f.raidTarget == target.id && f.raidEndNews == null, "past the raid's bound the council should plan again");
            // a raid whose one raider dies in sight of home: the council knows the party is gone and plans again at once
            FCConfig.WITNESS_RANGE.set(oldWitness);
            f.raidTarget = -1;
            f.raiders.clear();
            FactionNpc second = member(t, HOME_X, HOME_Z, NpcRole.SWORDSMAN);
            f.raidTarget = target.id;
            f.raiders.add(second.getUUID());
            f.lastParty.clear();
            f.raidParty = 1;
            f.raidStartedAt = later;
            goTo(t, second, FAR_X, FAR_Z);
            kill(second);
            t.assertTrue(data.record(second.getUUID()).where == State.KNOWN_DEAD, "a death seen from home is known");
            raids(t, f, later + 1);
            t.assertTrue(f.raidTarget < 0, "the raid did not end");
            f.lastRaidAt = later + 2 - interval - 1;
            raids(t, f, later + 2);
            t.assertTrue(f.raidTarget == target.id, "every raider known dead: the council knows its raid is over");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code campaign.farDeathsUnknown} turned off while a death is still unknown: under the old rule every death is
     * known at once, so the next roll call knows it ("was killed") instead of counting the dead member for ever.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "missing_rev_oldrule")
    public static void aDeathNotYetKnownIsKnownOnceFarDeathsAreKnownAgain(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 60);
        boolean was = FCConfig.FAR_DEATHS_UNKNOWN.get();
        t.onCleanup(() -> FCConfig.FAR_DEATHS_UNKNOWN.set(was));
        try {
            List<RollCall.Told> lines = recording(t);
            FactionNpc scout = member(t, HOME_X, HOME_Z, NpcRole.SCOUT);
            NpcRecord r = t.data.record(scout.getUUID());
            goTo(t, scout, FAR_X, FAR_Z);
            kill(scout);
            t.assertTrue(r.dead && r.alive && r.where == State.AWAY, "the death should be unknown first: " + r.where);
            FCConfig.FAR_DEATHS_UNKNOWN.set(false);
            RollCall.upkeep(h.getLevel(), t.data, t.base, h.getLevel().getGameTime());
            t.assertTrue(r.where == State.KNOWN_DEAD && !r.alive, "under the old rule the death is still not known: " + r.where);
            t.assertTrue(about(lines, scout.getUUID()).stream().anyMatch(l -> l.event() == Event.KILLED && l.line().endsWith("(Scout) was killed")),
                    "the line: " + lines);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * A hero whose fall nobody of its faction saw, rising where it fell outside every home area: nobody of its faction is
     * there, so nothing is said; its new entry keeps the faction's view (away, the same expected return) and it carries
     * its own fall home, told when it is back.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "missing_rev_rise")
    public static void aHeroRisingUnseenFarFromHomeTellsItsFallWhenItIsHome(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 100);
        try {
            List<RollCall.Told> lines = recording(t);
            ServerLevel level = h.getLevel();
            String id = "fixture_far_phoenix";
            HeroEntity hero = HeroAbilityGameTests.hero(t, id, "katana", HeroAbilityGameTests.def(id, MELEE, "striker", IDLE_MOVE,
                    ", \"revival\": {\"seconds\": 120, \"where\": \"where_it_fell\"}"), HOME_X, HOME_Z);
            hero.setNoAi(true);
            UUID first = hero.getUUID();
            NpcRecord r = t.data.record(first);
            goTo(t, hero, FAR_X, FAR_Z);
            long left = r.leftAt, expected = r.expectedBy;
            t.assertTrue(r.where == State.AWAY, "the hero did not leave: " + r.where);
            kill(hero);
            HeroState s = t.data.heroes.get(id);
            t.assertTrue(!s.alive && r.where == State.AWAY, "its fall should be unknown: " + r.where);
            s.reviveAt = level.getGameTime();
            HeroManager.tick(level.getServer());
            HeroEntity back = HeroManager.entity(level.getServer(), id);
            t.assertTrue(back != null && back.isAlive() && !back.getUUID().equals(first), "the hero did not rise");
            back.setNoAi(true);
            t.assertTrue(!RollCall.inHomeArea(t.data, SIDE, level, back.blockPosition()), "it should rise where it fell: " + back.blockPosition());
            t.assertTrue(about(lines, back.getUUID()).isEmpty() && about(lines, first).isEmpty(), "its faction heard of it: " + lines);
            NpcRecord nr = t.data.record(back.getUUID());
            t.assertTrue(nr != null && nr.where == State.AWAY && nr.alive && nr.leftAt == left && nr.expectedBy == expected,
                    "the risen hero's entry should keep its faction's view: " + (nr == null ? null : nr.where + " by " + nr.expectedBy));
            List<News> carried = t.data.faction(SIDE).news.get(back.getUUID());
            t.assertTrue(carried != null && carried.size() == 1 && carried.get(0).kind == News.Kind.OWN, "it carries nothing: " + carried);
            goTo(t, back, HOME_X, HOME_Z); // home: it tells its fall itself
            t.assertTrue(about(lines, back.getUUID()).stream().anyMatch(l -> l.line().endsWith("where none of the faction saw it, and has risen again")),
                    "its own fall was not told: " + lines);
            t.assertTrue(nr.where == State.HERE && t.data.faction(SIDE).news.get(back.getUUID()) == null, "not home: " + nr.where);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** A level gained away from home is heard when the member comes home; one gained at home, at once. */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "missing_rev_level")
    public static void aLevelGainedAwayIsToldWhenTheMemberIsHome(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 60);
        try {
            List<RollCall.Told> lines = recording(t);
            FactionNpc miner = member(t, HOME_X, HOME_Z, NpcRole.MINER);
            goTo(t, miner, FAR_X, FAR_Z);
            int before = miner.npcLevel();
            miner.awardXp(400);
            t.assertTrue(miner.npcLevel() > before, "no level gained");
            t.assertTrue(about(lines, miner.getUUID()).isEmpty(), "a level gained away was heard at once: " + lines);
            List<News> carried = t.data.faction(SIDE).news.get(miner.getUUID());
            t.assertTrue(carried != null && !carried.isEmpty() && carried.get(0).kind == News.Kind.OWN
                    && carried.get(0).text.contains(" reached level "), "it carries nothing home: " + carried);
            goTo(t, miner, HOME_X, HOME_Z);
            t.assertTrue(about(lines, miner.getUUID()).stream().anyMatch(l -> l.line().contains(" reached level " + miner.npcLevel())),
                    "not told on coming home: " + lines);
            lines.clear();
            int now = miner.npcLevel();
            miner.awardXp(2000);
            t.assertTrue(miner.npcLevel() == now || about(lines, miner.getUUID()).stream().anyMatch(l -> l.line().contains(" reached level ")),
                    "a level gained at home was not heard at once: " + lines);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * The raid party's gathering at the rally point is seen by the party, not by home: the line travels home with the
     * raiders who were there.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "missing_rev_gather")
    public static void theRaidPartysGatheringIsCarriedHome(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 200);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        try {
            t.assertTrue(f.raidTarget < 0, "the faction already has a raid under way");
            long now = level.getGameTime();
            BaseState target = reportedTarget(t, f, now);
            List<RollCall.Told> lines = recording(t);
            FactionNpc raider = t.npc(FAR_X, FAR_Z, NpcRole.SWORDSMAN);
            raider.equip(new ItemStack(Items.IRON_SWORD));
            RollCall.sighting(raider, t.data.record(raider.getUUID()));
            f.raidTarget = target.id;
            f.raiders.clear();
            f.raiders.add(raider.getUUID());
            f.raidParty = 1;
            f.raidStartedAt = now;
            f.raidMusteredAt = 0;
            // the rally where the raider stands (the job looks for it on the column's top: on the test frame's roof here)
            BlockPos rally = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    raider.blockPosition());
            raider.moveTo(rally.getX() + 0.5, rally.getY(), rally.getZ() + 0.5, 0F, 0F);
            f.raidRally = rally.asLong();
            h.runAtTickTime(190, () -> {
                if (f.raidMusteredAt == 0) {
                    t.fail("the raider never mustered: " + raider.status() + " at " + raider.blockPosition() + ", rally "
                            + BlockPos.of(f.raidRally));
                }
            });
            t.onEachTick(() -> {
                if (f.raidMusteredAt == 0) {
                    return;
                }
                t.assertTrue(lines.stream().noneMatch(l -> l.line().contains("has gathered")), "the gathering was heard at home at once: " + lines);
                List<News> carried = f.news.get(raider.getUUID());
                t.assertTrue(carried != null && carried.stream().anyMatch(n -> n.text.contains("has gathered - assault on")),
                        "the raider carries nothing home: " + carried);
                t.succeed();
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * The station tells who is away, missing and presumed dead as the faction knows it (a member dead far away and unseen
     * is still away), and a workplace held by a presumed-dead member says so instead of "dead".
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "missing_rev_station")
    public static void theStationTellsWhoIsAwayMissingOrPresumedDead(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        try {
            ServerLevel level = h.getLevel();
            long now = level.getGameTime();
            FactionNpc farmer = member(t, HOME_X, HOME_Z, NpcRole.FARMER);
            goTo(t, farmer, FAR_X, FAR_Z);
            kill(farmer); // unseen: to the faction it is away
            NpcRecord missing = t.data.record(t.absentWorker(NpcRole.MINER));
            missing.name = "Mara Vey";
            missing.where(State.MISSING, now);
            NpcRecord presumed = t.data.record(t.absentWorker(NpcRole.MINER));
            presumed.name = "Tobin Ash";
            presumed.where(State.PRESUMED_DEAD, now);
            WorkplaceState cave1 = t.addWorkplace(WorkplaceState.Type.CAVE, t.base.center.offset(5, 0, 0));
            cave1.assigned = missing.uuid;
            WorkplaceState cave2 = t.addWorkplace(WorkplaceState.Type.CAVE, t.base.center.offset(-5, 0, 0));
            cave2.assigned = presumed.uuid;
            List<String> info = com.factionclash.info.BaseInfo.baseLines(level, t.data, t.base);
            String roll = info.stream().filter(l -> l.startsWith("Away ")).findFirst().orElse(null);
            t.assertTrue(roll != null && roll.startsWith("Away 1; missing: " + missing.name + " (Miner) since day " + Whereabouts.day(now))
                    && roll.endsWith("; presumed dead: " + presumed.name + " (Miner)"), "the roll line: " + roll + " in " + info);
            t.assertTrue(info.stream().anyMatch(l -> l.startsWith("Surveyed cave #" + cave1.id) && l.contains("miner " + missing.name + " (missing)")),
                    "a missing miner's cave: " + info);
            t.assertTrue(info.stream().anyMatch(l -> l.startsWith("Surveyed cave #" + cave2.id) && l.contains("miner " + presumed.name + " (presumed dead)")),
                    "a presumed-dead miner's cave: " + info);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * "Adventure" by a member killed is made when the faction learns of the death, not at the death: a death seen only by
     * a member away makes it when the witness comes home and tells it.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "missing_rev_adventure")
    public static void anUnseenDeathMakesNoAdventureUntilItIsKnown(GameTestHelper h) {
        GameTestBases.Handle t = base(h, 40);
        CampaignData data = t.data;
        FactionState f = data.faction(SIDE);
        String id = "minecraft:adventure/root";
        FactionState.Achieved oldAchieved = f.achievements.get(id);
        java.util.Set<String> oldProgress = f.advancementProgress.containsKey(id) ? new java.util.HashSet<>(f.advancementProgress.get(id)) : null;
        boolean wasStarted = data.started;
        t.onCleanup(() -> {
            data.started = wasStarted;
            f.achievements.remove(id);
            f.advancementProgress.remove(id);
            if (oldAchieved != null) {
                f.achievements.put(id, oldAchieved);
            }
            if (oldProgress != null) {
                f.advancementProgress.put(id, oldProgress);
            }
        });
        try {
            data.started = true;
            f.achievements.remove(id);
            f.advancementProgress.remove(id);
            FactionNpc victim = member(t, HOME_X, HOME_Z, NpcRole.WOODCUTTER);
            FactionNpc witness = member(t, HOME_X, HOME_Z, NpcRole.SCOUT);
            goTo(t, victim, FAR_X, FAR_Z);
            goTo(t, witness, FAR_X - 4, FAR_Z);
            kill(victim);
            java.util.Set<String> met = f.advancementProgress.get(id);
            t.assertTrue(!f.achievements.containsKey(id) && (met == null || !met.contains("killed_by_something")),
                    "a death nobody at home knows of made Adventure at once");
            goTo(t, witness, HOME_X, HOME_Z); // it tells the death
            t.assertTrue(f.achievements.containsKey(id) && f.achievements.get(id).by().equals(victim.npcName()),
                    "the death told at home did not make Adventure: " + f.achievements.get(id));
            data.started = wasStarted;
            t.succeed();
        } catch (RuntimeException | Error e) {
            data.started = wasStarted;
            t.cleanup();
            throw e;
        }
    }
}
