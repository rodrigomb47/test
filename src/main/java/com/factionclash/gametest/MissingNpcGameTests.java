package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.FactionDirector;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.HeroState;
import com.factionclash.campaign.News;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.campaign.RollCall;
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
}
