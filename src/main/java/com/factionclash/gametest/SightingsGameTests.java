package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.FactionDirector;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.RollCall;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroManager;
import com.factionclash.hero.HeroRegistry;
import com.factionclash.logic.campaign.Intel;
import com.factionclash.logic.campaign.Sightings;
import com.factionclash.logic.campaign.Sightings.Sighting;
import com.factionclash.logic.campaign.Sightings.Source;
import com.factionclash.logic.campaign.Whereabouts;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.util.Messages;
import com.factionclash.work.ScoutJob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The sightings behind the war council's reserve ({@code docs/design/recon.md} 5.5 and 10.2 items 14 to 18, task R11):
 * enemy soldiers and heroes our people saw away from their bases raise what the council plans against, only upward and
 * only on top of a fresh good recon. At home (the alarm's sweep: in our home area, seen by a member standing in it), by
 * a scout away (carried home, told at the station, lost if it dies) and by a recon scout on its watch; falls seen mark
 * them (a soldier's for good, a hero's until half a day after its return). The council tests run on the {@code platform}
 * as {@link KnowledgeGameTests}' do (a test base of walls half 2, its home area 6 blocks from the centre at relative (12,
 * 2, 12), the enemy a bare record 200 blocks off); the scout tests run a real recon trip in {@code mobility_field}
 * ({@link ReconTripGameTests#field}). Each test has its own batch, but the static save and reload.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class SightingsGameTests {
    private SightingsGameTests() {
    }

    static final FactionType SIDE = KnowledgeGameTests.SIDE, ENEMY = KnowledgeGameTests.ENEMY, THIRD = FactionType.EMBER_LEGION;
    private static final int[][] GARRISON = KnowledgeGameTests.GARRISON;

    /** The council at its raid interval: the war's first gate long passed, our last raid an interval ago. */
    private static void atInterval(GameTestBases.Handle t, FactionState f, long now) {
        int interval = FCConfig.RAID_INTERVAL_TICKS.get();
        t.data.startedAt = now - interval - 1;
        f.lastRaidAt = now - interval - 1;
    }

    private static List<FactionNpc> garrison(GameTestBases.Handle t) {
        List<FactionNpc> ours = new ArrayList<>();
        for (int[] p : GARRISON) {
            ours.add(KnowledgeGameTests.member(t, p[0], p[1], NpcRole.SWORDSMAN));
        }
        return ours;
    }

    /**
     * A slice hero's fixture as {@code id}, given to {@code faction} with its home at {@code home} and standing at relative
     * (x, 2, z), still (no AI); the cleanup forgets it.
     */
    static HeroEntity enemyHero(GameTestBases.Handle t, String slice, String id, FactionType faction, BaseState home, int x, int z) {
        String text = HeroAbilityGameTests.slice(slice).replace("\"" + slice + "\"", "\"" + id + "\"");
        HeroRegistry.Transient fx = HeroRegistry.registerTransient(id, HeroAbilityGameTests.visual("staff"), text);
        MinecraftServer server = t.helper.getLevel().getServer();
        t.onCleanup(() -> {
            HeroManager.forget(server, id);
            fx.close();
        });
        if (HeroRegistry.get(id) == null || HeroRegistry.get(id).def() == null) {
            throw new GameTestAssertException("the fixture " + id + " did not load: " + HeroRegistry.report().problems().report());
        }
        HeroManager.Result r = HeroManager.give(server, id, faction, home, t.helper.absolutePos(new BlockPos(x, 2, z)));
        if (!r.ok()) {
            throw new GameTestAssertException("give failed: " + r.message());
        }
        HeroEntity hero = r.hero();
        hero.setNoAi(true);
        return t.track(hero);
    }

    /** A sighting of the kind the tests file by hand: one of {@code faction}'s soldiers at the given power and time. */
    private static Sighting soldier(FactionType faction, double power, long at, BlockPos where, Source source, String by, int base) {
        return new Sighting(UUID.randomUUID(), faction.id, "", power, at, where.getX(), where.getZ(), source, by, base, false);
    }

    // ------------------------------------------------------------------ at home

    /**
     * The home sweep and a fall at home (recon.md 5.5, task R11): an enemy soldier in our home area that a member standing
     * there sees is a sighting at our walls, with its face, faction, place and visible power. One just outside the home
     * area is none, though the alarm's box holds it and a member sees it; neither is an enemy worker, nor a soldier boxed
     * in stone where nobody sees or hears it. The soldier seen falling stays fallen in the ledger; so does one that falls
     * outside our home area in sight of a member standing in it.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "sightings_home")
    public static void aSoldierSeenAtHomeIsASightingAndItsFallMarksIt(GameTestHelper h) {
        GameTestBases.Handle t = KnowledgeGameTests.base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        KnowledgeGameTests.keepRaidState(t, f);
        List<BlockPos> box = new ArrayList<>();
        t.onCleanup(() -> box.forEach(p -> h.setBlock(p, Blocks.AIR)));
        try {
            KnowledgeGameTests.member(t, 12, 12, NpcRole.SWORDSMAN);
            FactionNpc near = KnowledgeGameTests.stranger(t, ENEMY, 12, 15, NpcRole.SWORDSMAN);
            FactionNpc far = KnowledgeGameTests.stranger(t, ENEMY, 12, 21, NpcRole.SWORDSMAN);
            FactionNpc worker = KnowledgeGameTests.stranger(t, ENEMY, 14, 15, NpcRole.FARMER);
            // a soldier shut in a stone box inside our home area: nobody sees it, nobody stands within earshot
            for (int x = 6; x <= 8; x++) {
                for (int z = 6; z <= 8; z++) {
                    for (int y = 2; y <= 5; y++) {
                        if (x != 7 || z != 7 || y == 5) {
                            BlockPos p = new BlockPos(x, y, z);
                            h.setBlock(p, Blocks.STONE);
                            box.add(p);
                        }
                    }
                }
            }
            FactionNpc hidden = KnowledgeGameTests.stranger(t, ENEMY, 7, 7, NpcRole.SWORDSMAN);
            t.assertTrue(RollCall.inHomeArea(t.data, SIDE, level, near.blockPosition()) && RollCall.inHomeArea(t.data, SIDE, level, hidden.blockPosition())
                    && !RollCall.inHomeArea(t.data, SIDE, level, far.blockPosition()), "the test's places are off");
            long now = level.getGameTime();
            FactionDirector.defenceAlarm(level, t.base);
            Sighting s = f.sightings.get(near.getUUID());
            t.assertTrue(s != null && s.source() == Source.HOME && !s.fallen() && s.faction().equals(ENEMY.id) && !s.isHero()
                    && s.at() == now && s.x() == near.blockPosition().getX() && s.z() == near.blockPosition().getZ()
                    && Math.abs(s.power() - KnowledgeGameTests.power(near)) < 1e-9, "a soldier seen at home: " + s);
            t.assertTrue(f.sightings.get(far.getUUID()) == null, "one outside our home area is no sighting at home: " + f.sightings.get(far.getUUID()));
            t.assertTrue(f.sightings.get(worker.getUUID()) == null, "an enemy worker is no fighter: " + f.sightings.get(worker.getUUID()));
            t.assertTrue(f.sightings.get(hidden.getUUID()) == null, "nobody saw the boxed soldier: " + f.sightings.get(hidden.getUUID()));
            t.assertTrue(f.sightings.size() == 1, "one sighting: " + f.sightings.all());
            // the soldier falls in our home area: seen to fall, for good
            KnowledgeGameTests.kill(near);
            s = f.sightings.get(near.getUUID());
            t.assertTrue(s != null && s.fallen() && !Sightings.counts(s, now + 1, 12000), "the fall seen at home: " + s);
            // the one outside falls in sight of our member at home: seen to fall too
            KnowledgeGameTests.kill(far);
            Sighting sf = f.sightings.get(far.getUUID());
            t.assertTrue(sf != null && sf.fallen() && sf.source() == Source.HOME, "a fall seen from home: " + sf);
            FactionDirector.defenceAlarm(level, t.base);
            t.assertTrue(f.sightings.get(near.getUUID()).fallen(), "a later sweep undid the fall");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * recon.md 10.2 item 14: two enemy soldiers stand in our home area in sight of a member, then walk off; a good recon of
     * their base counts one other. The plan holds three (one counted and the two seen at our walls, more than the share of
     * one), and the launch line says what the reserve rests on.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "sightings_walls")
    public static void aSoldierSeenAtOurWallsJoinsTheReserve(GameTestHelper h) {
        GameTestBases.Handle t = KnowledgeGameTests.base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        KnowledgeGameTests.keepRaidState(t, f);
        try {
            List<Messages.Said> lines = KnowledgeGameTests.recording(t);
            long now = level.getGameTime();
            atInterval(t, f, now);
            garrison(t);
            BaseState enemy = KnowledgeGameTests.enemyBase(t, ENEMY, t.base.center.offset(0, 0, 200));
            FactionNpc a = KnowledgeGameTests.stranger(t, ENEMY, 11, 16, NpcRole.SWORDSMAN);
            FactionNpc b = KnowledgeGameTests.stranger(t, ENEMY, 13, 16, NpcRole.SWORDSMAN);
            double gear = KnowledgeGameTests.power(a);
            FactionDirector.defenceAlarm(level, t.base);
            t.assertTrue(f.sightings.get(a.getUUID()) != null && f.sightings.get(b.getUUID()) != null, "the two at our walls: " + f.sightings.all());
            // they walk off, out of our home area
            KnowledgeGameTests.put(t, a, 3, 22);
            KnowledgeGameTests.put(t, b, 21, 22);
            KnowledgeGameTests.deliverRecon(t, f, enemy, KnowledgeGameTests.counted(1, gear, now + 1, Map.of()), now + 1);
            KnowledgeGameTests.raids(t, f, now + 2);
            String council = FactionDirector.LAST_COUNCIL.get(SIDE);
            t.assertTrue(f.raidTarget == enemy.id && council != null && council.contains(String.format(Locale.ROOT, "(soldiers %.1f,", 3 * gear)),
                    "the plan should hold the one counted and the two seen at our walls: " + council);
            String line = "Launching a raid with 3 soldiers on the Grove Covenant settlement reported at " + enemy.center.toShortString()
                    + ", against about 1 defender (scout Edrin Cole, day " + Whereabouts.day(now + 1) + ") and a reserve of 2 (2 Grove Covenant"
                    + " soldiers seen at our walls on day " + Whereabouts.day(now) + ", not seen to fall)";
            t.assertTrue(KnowledgeGameTests.said(lines, SIDE, line), "the launch line should say what the reserve rests on: " + lines);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * recon.md 10.2 item 15: four enemy soldiers seen at our walls; two of them are killed there in sight of our people,
     * and the other two are among the faces the delivered recon counted in their yard. None of them is in the reserve: the
     * plan is the count and its share, "a reserve of 1 (half again the 2 counted)". (Kept, the two fallen or the two counted
     * again would make a reserve of 2 seen at our walls.)
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "sightings_fall_face")
    public static void aSoldierSeenToFallIsNoReserveAndAFaceCountedInTheWatchIsCountedOnce(GameTestHelper h) {
        GameTestBases.Handle t = KnowledgeGameTests.base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        KnowledgeGameTests.keepRaidState(t, f);
        try {
            List<Messages.Said> lines = KnowledgeGameTests.recording(t);
            long now = level.getGameTime();
            atInterval(t, f, now);
            garrison(t);
            BaseState enemy = KnowledgeGameTests.enemyBase(t, ENEMY, t.base.center.offset(0, 0, 200));
            List<FactionNpc> seen = new ArrayList<>();
            for (int x : new int[]{10, 11, 13, 14}) {
                seen.add(KnowledgeGameTests.stranger(t, ENEMY, x, 16, NpcRole.SWORDSMAN));
            }
            double gear = KnowledgeGameTests.power(seen.get(0));
            FactionDirector.defenceAlarm(level, t.base);
            t.assertTrue(f.sightings.size() == 4, "four seen at our walls: " + f.sightings.all());
            KnowledgeGameTests.kill(seen.get(0));
            KnowledgeGameTests.kill(seen.get(1));
            t.assertTrue(f.sightings.get(seen.get(0).getUUID()).fallen() && f.sightings.get(seen.get(1).getUUID()).fallen(),
                    "the two killed at our walls were not seen to fall: " + f.sightings.all());
            FactionDirector.defenceAlarm(level, t.base);
            Map<UUID, Double> faces = new LinkedHashMap<>();
            faces.put(seen.get(2).getUUID(), gear);
            faces.put(seen.get(3).getUUID(), gear);
            KnowledgeGameTests.deliverRecon(t, f, enemy, KnowledgeGameTests.recon(12, true, now + 1, faces, Map.of()), now + 1);
            KnowledgeGameTests.raids(t, f, now + 2);
            String line = "Launching a raid with 3 soldiers on the Grove Covenant settlement reported at " + enemy.center.toShortString()
                    + ", against about 2 defenders (scout Edrin Cole, day " + Whereabouts.day(now + 1) + ") and a reserve of 1 (half again the 2 counted)";
            t.assertTrue(f.raidTarget == enemy.id && KnowledgeGameTests.said(lines, SIDE, line),
                    "the fallen and the faces counted should not be in the reserve: " + FactionDirector.LAST_COUNCIL.get(SIDE) + " / " + lines);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * recon.md 10.2 item 16, its second half: the target's yard watched empty, and six of its soldiers seen leaving it by
     * the recon scout from its pillar (filed as that watch's sightings, both sides in the same gear). The plan is those six
     * (no share: half of none is none): a council with a party of three waits, "4 needed"; one with a party of four goes.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "sightings_emptied")
    public static void anEmptiedBaseWhoseArmyWasSeenLeaving(GameTestHelper h) {
        GameTestBases.Handle t = KnowledgeGameTests.base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        KnowledgeGameTests.keepRaidState(t, f);
        try {
            List<Messages.Said> lines = KnowledgeGameTests.recording(t);
            long now = level.getGameTime();
            atInterval(t, f, now);
            List<FactionNpc> ours = garrison(t);
            double gear = KnowledgeGameTests.power(ours.get(0));
            BaseState enemy = KnowledgeGameTests.enemyBase(t, ENEMY, t.base.center.offset(0, 0, 200));
            KnowledgeGameTests.deliverRecon(t, f, enemy, KnowledgeGameTests.counted(0, 0, now, Map.of()), now);
            for (int i = 0; i < 6; i++) {
                f.sightings.file(soldier(ENEMY, gear, now - 600 + i, enemy.center.offset(40, 0, i), Source.WATCH, "Edrin Cole", enemy.id));
            }
            KnowledgeGameTests.raids(t, f, now + 1);
            String against = "an empty yard (scout Edrin Cole, day " + Whereabouts.day(now) + ") and a reserve of 6 (6 Grove Covenant soldiers"
                    + " seen leaving by scout Edrin Cole on day " + Whereabouts.day(now - 595) + ", not seen to fall)";
            String council = FactionDirector.LAST_COUNCIL.get(SIDE);
            t.assertTrue(f.raidTarget < 0 && council != null && council.contains("waiting on the Grove Covenant settlement: 3 soldiers to send, 4 needed against "
                    + against), "three should wait against the six seen leaving: " + council);
            KnowledgeGameTests.member(t, 12, 14, NpcRole.SWORDSMAN); // a seventh: a party of four
            KnowledgeGameTests.raids(t, f, now + 2);
            t.assertTrue(f.raidTarget == enemy.id && f.raiders.size() == 4 && KnowledgeGameTests.said(lines, SIDE, "Launching a raid with 4 soldiers on the Grove"
                    + " Covenant settlement reported at " + enemy.center.toShortString() + ", against " + against),
                    "four should go against the six seen leaving: " + FactionDirector.LAST_COUNCIL.get(SIDE) + " / " + lines);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * recon.md 10.2 item 17: an enemy hero seen at our walls, and a delivered recon of her base counting two soldiers and no
     * hero. The plan holds her (the watch missed her): she holds our three soldiers back ("the defending heroes held it
     * off": the count and its share alone would let them go), and the line names her and how our people know of her.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "sightings_hero_walls")
    public static void aHeroSeenAtOurWallsJoinsThePlan(GameTestHelper h) {
        GameTestBases.Handle t = KnowledgeGameTests.base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        KnowledgeGameTests.keepRaidState(t, f);
        try {
            long now = level.getGameTime();
            atInterval(t, f, now);
            List<FactionNpc> ours = garrison(t);
            double gear = KnowledgeGameTests.power(ours.get(0));
            BaseState enemy = KnowledgeGameTests.enemyBase(t, ENEMY, t.base.center.offset(0, 0, 200));
            String id = "fx_sight_walls";
            HeroEntity hero = enemyHero(t, "hinae_shirasuzu", id, ENEMY, enemy, 12, 16);
            FactionDirector.defenceAlarm(level, t.base);
            Sighting s = f.sightings.get(hero.getUUID());
            t.assertTrue(s != null && s.hero().equals(id) && s.source() == Source.HOME && !s.fallen(), "the hero at our walls: " + f.sightings.all());
            t.assertTrue(s.power() > 2 * gear, "the hero's war power is too small for this test: " + s.power());
            hero.discard(); // (she walks off)
            KnowledgeGameTests.deliverRecon(t, f, enemy, KnowledgeGameTests.counted(2, gear, now + 1, Map.of()), now + 1);
            KnowledgeGameTests.raids(t, f, now + 2);
            String council = FactionDirector.LAST_COUNCIL.get(SIDE);
            String name = HeroRegistry.get(id).name();
            t.assertTrue(f.raidTarget < 0 && council != null && council.contains("(the defending heroes held it off)")
                    && council.contains("waiting on the Grove Covenant settlement: 3 soldiers to send, ")
                    && council.contains(" needed against about 2 defenders (scout Edrin Cole, day " + Whereabouts.day(now + 1) + ") and a reserve of 1"
                    + " (half again the 2 counted) and " + name + " (seen at our walls on day " + Whereabouts.day(now) + ")"),
                    "the hero seen at our walls should be in the plan: " + council);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * recon.md 5.5's hero fall (task R11): an enemy hero of the Grove Covenant falls in the Tide Alliance's home area in
     * sight of its member, killed by nobody of the Tide (before, only the killer's faction heard of a hero's fall). The
     * Tide files the fall, at her war power and her own id; she stays in its plans until half a day after her expected
     * return, and the council's line names her as seen to fall at our walls.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "sightings_hero_fall")
    public static void aHerosFallIsFiledByEveryFactionWhosePeopleSawIt(GameTestHelper h) {
        GameTestBases.Handle t = KnowledgeGameTests.base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        KnowledgeGameTests.keepRaidState(t, f);
        try {
            long now = level.getGameTime();
            atInterval(t, f, now);
            List<FactionNpc> ours = garrison(t);
            double gear = KnowledgeGameTests.power(ours.get(0));
            BaseState enemy = KnowledgeGameTests.enemyBase(t, ENEMY, t.base.center.offset(0, 0, 200));
            String id = "fx_sight_fall";
            HeroEntity hero = enemyHero(t, "hinae_shirasuzu", id, ENEMY, enemy, 12, 16);
            UUID uuid = hero.getUUID();
            hero.kill();
            t.assertTrue(!hero.isAlive(), "the hero did not fall");
            Sighting s = f.sightings.get(uuid);
            long revive = FCConfig.HERO_REVIVE_SECONDS.get() * 20L;
            t.assertTrue(s != null && s.hero().equals(id) && s.fallen() && s.source() == Source.HOME && s.power() > 0,
                    "the hero's fall seen at our walls was not filed: " + f.sightings.all());
            t.assertTrue(Sightings.counts(s, now + revive + Sightings.FRESH_TICKS - 5, revive) && !Sightings.counts(s, now + revive + Sightings.FRESH_TICKS + 5, revive),
                    "a hero seen to fall counts until half a day after her expected return: " + s);
            KnowledgeGameTests.deliverRecon(t, f, enemy, KnowledgeGameTests.counted(2, gear, now + 1, Map.of()), now + 1);
            KnowledgeGameTests.raids(t, f, now + 2);
            String council = FactionDirector.LAST_COUNCIL.get(SIDE);
            t.assertTrue(council != null && council.contains("and " + HeroRegistry.get(id).name() + " (seen to fall at our walls on day " + Whereabouts.day(now) + ")"),
                    "the council should plan for the hero seen to fall: " + council);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Task R9 (recon.md 8): a raid knows the heroes the recon it was launched on saw at home. A recon of an empty yard but
     * for a hero (the backbone counts her at nothing alone) launches, and the raid keeps her id; a later raid launched on a
     * recon that saw none keeps none.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "sightings_raid_heroes")
    public static void aRaidKnowsTheHeroesItsReconSaw(GameTestHelper h) {
        GameTestBases.Handle t = KnowledgeGameTests.base(h, 40);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        KnowledgeGameTests.keepRaidState(t, f);
        try {
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            atInterval(t, f, now);
            garrison(t);
            BaseState enemy = KnowledgeGameTests.enemyBase(t, ENEMY, t.base.center.offset(0, 0, 200));
            KnowledgeGameTests.deliverRecon(t, f, enemy, KnowledgeGameTests.counted(0, 0, now, Map.of("review_hero", 2.0)), now);
            KnowledgeGameTests.raids(t, f, now + 1);
            t.assertTrue(f.raidTarget == enemy.id && f.raidReconHeroes.equals(Set.of("review_hero")),
                    "the raid should know the hero its recon saw: " + f.raidReconHeroes + " / " + FactionDirector.LAST_COUNCIL.get(SIDE));
            CompoundTag saved = f.save();
            t.assertTrue(FactionState.load(saved).raidReconHeroes.equals(Set.of("review_hero")), "the raid's recon heroes were not saved");
            f.raidTarget = -1;
            f.raiders.clear();
            f.lastParty.clear();
            f.raidEndNews = null;
            f.lastRaidAt = now + 2 - interval - 1;
            KnowledgeGameTests.deliverRecon(t, f, enemy, KnowledgeGameTests.counted(0, 0, now + 2, Map.of()), now + 2);
            KnowledgeGameTests.raids(t, f, now + 3);
            t.assertTrue(f.raidTarget == enemy.id && f.raidReconHeroes.isEmpty(), "a raid on a recon that saw no hero knows of none: "
                    + f.raidReconHeroes);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ saving

    /**
     * The sightings survive a save and reload (recon.md 7): the faction's ledger (a soldier at our walls, a hero seen to
     * fall by a scout, a soldier seen leaving from a pillar with the base it was watched at) and its last raid's recon
     * heroes with the faction's state; what a scout carries home with its job.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void sightingsSurviveASaveAndReload(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 3, 40);
        try {
            FactionState fs = new FactionState(SIDE);
            List<Sighting> filed = List.of(
                    new Sighting(UUID.randomUUID(), ENEMY.id, "", 1.75, 1000, 10, -20, Source.HOME, "", Sightings.NO_BASE, false),
                    new Sighting(UUID.randomUUID(), ENEMY.id, "hinae", 6.8, 2000, 30, 40, Source.FIELD, "Edrin Cole", Sightings.NO_BASE, false).fell(2100),
                    new Sighting(UUID.randomUUID(), THIRD.id, "", 1.0, 3000, 50, 60, Source.WATCH, "Edrin Cole", 990123, false));
            filed.forEach(fs.sightings::file);
            fs.raidReconHeroes.add("hinae");
            FactionState back = FactionState.load(fs.save());
            t.assertTrue(back != null && List.copyOf(back.sightings.all()).equals(filed), "the faction's sightings after a reload: "
                    + (back == null ? null : back.sightings.all()));
            t.assertTrue(back.raidReconHeroes.equals(Set.of("hinae")), "the raid's recon heroes after a reload: " + back.raidReconHeroes);
            FactionNpc scout = t.npc(12, 12, NpcRole.SCOUT);
            scout.setNoAi(true);
            ScoutJob job = (ScoutJob) scout.job();
            job.carry(filed.get(0));
            job.carry(filed.get(1));
            Entity again = ReconTripGameTests.reload(t, ReconTripGameTests.unload(scout));
            t.assertTrue(again instanceof FactionNpc n && n.job() instanceof ScoutJob j
                    && List.copyOf(j.carriedSightings().all()).equals(filed.subList(0, 2)), "what the scout carries after a reload: "
                    + (again instanceof FactionNpc n && n.job() instanceof ScoutJob j ? j.carriedSightings().all() : again));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ a scout away (a real recon trip in mobility_field)

    /**
     * recon.md 10.2 item 18, and the watch's and the carried fall's sightings: our scout on its recon trip meets an Ember
     * Legion soldier in the field and sees it fall there, and from its pillar sees a Grove Covenant soldier outside the
     * target's walls box (leaving it; a low wall hides it from the ground on our side, so only the watch sees it). The faction knows of neither until the scout reports at the station; then its
     * sightings hold the Ember soldier's fall, met by the scout, and the Grove soldier seen leaving on its watch of the
     * target. The scout carries nothing after it has told them.
     */
    @GameTest(template = HeroMobilityGameTests.FIELD, timeoutTicks = 4000, batch = "sightings_scout_home", skyAccess = true)
    public static void aScoutCarriesItsSightingsHome(GameTestHelper h) {
        ReconTripGameTests.Field fl = ReconTripGameTests.field(h, 4000, 4, true);
        GameTestBases.Handle t = fl.t();
        FactionState f = fl.f();
        ServerLevel level = h.getLevel();
        FactionNpc met, leaving;
        try {
            met = KnowledgeGameTests.stranger(t, THIRD, 10, 15, NpcRole.SWORDSMAN);
            leaving = ReconTripGameTests.defender(t, 16, 7);
            // a low wall hides it from the ground on our side (from home, on the way out and back): only from the pillar
            // does the scout see it, outside the target's walls box
            for (int z = 3; z <= 11; z++) {
                for (int y = 2; y <= 3; y++) {
                    BlockPos p = new BlockPos(14, y, z);
                    h.setBlock(p, Blocks.STONE);
                    fl.built().add(p);
                }
            }
            t.assertTrue(!RollCall.inHomeArea(t.data, SIDE, level, met.blockPosition()) && !RollCall.inHomeArea(t.data, SIDE, level, leaving.blockPosition()),
                    "the enemy soldiers stand in our home area");
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        ReconTripGameTests.Trip trip = new ReconTripGameTests.Trip();
        boolean[] killed = {false};
        t.onEachTick(() -> {
            ReconTripGameTests.follow(fl, trip);
            ScoutJob job = fl.job();
            FactionNpc scout = fl.scout();
            Sighting early = job.carriedSightings().get(leaving.getUUID());
            t.assertTrue(early == null || early.source() == Source.WATCH, "the Grove soldier behind the wall was seen from the ground: " + early);
            if (trip.filed == null) {
                t.assertTrue(f.sightings.get(met.getUUID()) == null && f.sightings.get(leaving.getUUID()) == null,
                        "a sighting known before the scout reported: " + f.sightings.all());
                Sighting carried = job.carriedSightings().get(met.getUUID());
                if (!killed[0] && carried != null && !RollCall.inHomeArea(t.data, SIDE, level, scout.blockPosition())
                        && scout.distanceTo(met) <= FCConfig.WITNESS_RANGE.get() - 2 && scout.hasLineOfSight(met)) {
                    t.assertTrue(carried.source() == Source.FIELD && carried.by().equals(scout.npcName()), "met in the field: " + carried);
                    KnowledgeGameTests.kill(met);
                    killed[0] = true;
                    Sighting fell = job.carriedSightings().get(met.getUUID());
                    t.assertTrue(fell != null && fell.fallen(), "the fall it saw away was not carried: " + fell);
                }
                return;
            }
            t.assertTrue(killed[0], "the scout never had the Ember soldier in its carried sightings in sight away from home");
            Sighting m = f.sightings.get(met.getUUID()), l = f.sightings.get(leaving.getUUID());
            t.assertTrue(m != null && m.fallen() && m.source() == Source.FIELD && m.by().equals(scout.npcName()) && m.faction().equals(THIRD.id),
                    "the Ember soldier's fall met by the scout: " + m);
            t.assertTrue(l != null && !l.fallen() && l.source() == Source.WATCH && l.base() == fl.target().id && l.by().equals(scout.npcName()),
                    "the Grove soldier seen leaving on the watch: " + l);
            t.assertTrue(job.carriedSightings().size() == 0, "the scout still carries what it told: " + job.carriedSightings().all());
            t.assertTrue(trip.filed.recon != null && !trip.filed.recon.faces().contains(leaving.getUUID()), "the soldier outside the box was counted");
            t.succeed();
        });
    }

    /**
     * recon.md 10.2 item 18, its second half: a scout that met an enemy soldier in the field and is killed before it
     * reports brings nothing home: its faction never learns of the sighting.
     */
    @GameTest(template = HeroMobilityGameTests.FIELD, timeoutTicks = 3000, batch = "sightings_scout_dead", skyAccess = true)
    public static void aScoutKilledBeforeItReportsBringsNoSighting(GameTestHelper h) {
        ReconTripGameTests.Field fl = ReconTripGameTests.field(h, 3000, 4, true);
        GameTestBases.Handle t = fl.t();
        FactionState f = fl.f();
        FactionNpc met;
        try {
            met = KnowledgeGameTests.stranger(t, THIRD, 10, 15, NpcRole.SWORDSMAN);
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        long[] deadAt = {-1};
        t.onEachTick(() -> {
            long now = h.getLevel().getGameTime();
            if (deadAt[0] < 0) {
                if (fl.job().carriedSightings().get(met.getUUID()) != null) {
                    KnowledgeGameTests.kill(fl.scout());
                    t.assertTrue(!fl.scout().isAlive(), "the scout did not die");
                    deadAt[0] = now;
                }
                return;
            }
            t.assertTrue(f.sightings.get(met.getUUID()) == null, "a dead scout's sighting reached home: " + f.sightings.all());
            if (now - deadAt[0] >= 100) {
                t.succeed();
            }
        });
    }
}
