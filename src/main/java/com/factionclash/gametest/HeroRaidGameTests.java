package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.FactionDirector;
import com.factionclash.campaign.FactionState;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroMovement;
import com.factionclash.hero.HeroRaid;
import com.factionclash.logic.hero.HeroLinks;
import com.factionclash.logic.hero.WallEntry;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.work.WorkGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.factionclash.gametest.HeroFightMobilityGameTests.moveBase;
import static com.factionclash.gametest.HeroMobilityGameTests.FIELD;
import static com.factionclash.gametest.HeroMobilityGameTests.floorY;
import static com.factionclash.gametest.HeroMobilityGameTests.sliceHero;
import static com.factionclash.gametest.HeroMobilityGameTests.still;
import static com.factionclash.gametest.HeroNavigationGameTests.fill;
import static com.factionclash.gametest.HeroNavigationGameTests.rel;
import static com.factionclash.gametest.HeroNavigationGameTests.taken;
import static com.factionclash.gametest.HeroNavigationGameTests.where;

/**
 * Heroes, task A12: raids over walls and the defenders' answer (MOBILITY_AND_WORK 5.2 and 17.2). A flyer on a raid flies
 * over a hostile wall and presses the button of its iron gate for the soldiers of its party, who walk in through the
 * gate and never over the wall, and once the raid ends it is out of the walls; a climber climbs a hostile wall and the
 * base's alarm has sounded by the time it is on top; a hero takes a way over a wall only with a way out from inside
 * (else the gate); archers take an airborne enemy hero over the soldier they were shooting and their arrows down it;
 * an enemy hero in the yard draws the two nearest guards and the home hero, and one seen in the air on its approach
 * raises the alarm. Each test has its own batch.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroRaidGameTests {
    private HeroRaidGameTests() {
    }

    private static final FactionType SIDE = FactionType.EMBER_LEGION, FOE = FactionType.GROVE_COVENANT;

    /**
     * A hostile walled yard: the test base {@code b} moved to relative (cx, 2, cz), walls {@code half} out, 5 high, a gate
     * of two door blocks in the middle of its west wall (an oak or an iron door), its heart at the centre; the yard's
     * floor {@code sunk} blocks lower than the field's.
     */
    static void walledYard(GameTestHelper h, BaseState b, int cx, int cz, int half, Block door, int sunk) {
        moveBase(h, b, cx, cz, half);
        fill(h, cx - half, 2, cz - half, cx + half, 6, cz + half, Blocks.STONE_BRICKS);
        fill(h, cx - half + 1, 2, cz - half + 1, cx + half - 1, 6, cz + half - 1, Blocks.AIR);
        if (sunk > 0) {
            fill(h, cx - half + 1, 2 - sunk, cz - half + 1, cx + half - 1, 1, cz + half - 1, Blocks.AIR);
        }
        BlockPos g = new BlockPos(cx - half, 2, cz);
        h.setBlock(g, door.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(g.above(), door.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        b.points.put(BaseState.GATE_WEST, h.absolutePos(g));
        b.points.put(BaseState.HEART, h.absolutePos(new BlockPos(cx, 2 - sunk, cz)));
        b.points.put(BaseState.STORAGE_STAND, h.absolutePos(new BlockPos(cx + 2, 2 - sunk, cz + 2)));
    }

    /** Its faction's raid on {@code target} under way with {@code raiders} in the party (put back by the cleanup). */
    static FactionState raid(GameTestBases.Handle t, BaseState target, FactionNpc... raiders) {
        FactionState f = t.data.faction(t.base.faction);
        int tgt = f.raidTarget, party = f.raidParty;
        long mustered = f.raidMusteredAt, rally = f.raidRally, started = f.raidStartedAt;
        java.util.List<java.util.UUID> before = java.util.List.copyOf(f.raiders);
        java.util.List<String> reconHeroes = java.util.List.copyOf(f.raidReconHeroes);
        t.onCleanup(() -> {
            f.raidReconHeroes.clear();
            f.raidReconHeroes.addAll(reconHeroes);
            f.raidTarget = tgt;
            f.raidParty = party;
            f.raidMusteredAt = mustered;
            f.raidRally = rally;
            f.raidStartedAt = started;
            f.raiders.clear();
            f.raiders.addAll(before);
        });
        f.raiders.clear();
        for (FactionNpc n : raiders) {
            f.raiders.add(n.getUUID());
        }
        long now = t.helper.getLevel().getGameTime();
        f.raidTarget = target.id;
        f.raidParty = raiders.length;
        f.raidMusteredAt = now;
        f.raidReconHeroes.clear(); // (launched on no recon that saw a hero, unless the test says so)
        f.raidRally = Long.MIN_VALUE;
        f.raidStartedAt = now;
        return f;
    }

    /** A soldier of the raiding side with no job and no target: the test walks it to the heart. */
    static FactionNpc raider(GameTestBases.Handle t, int x, int z) {
        FactionNpc n = t.npc(x, z, NpcRole.SWORDSMAN);
        n.goalSelector.removeAllGoals(g -> g instanceof WorkGoal);
        n.targetSelector.removeAllGoals(g -> true);
        return n;
    }

    /** A guard of the defending base (its AI on, no job, no target goals: only the alarm gives it one). */
    static FactionNpc guard(GameTestBases.Handle t, int x, int z) {
        FactionNpc n = t.npc(x, z, NpcRole.SWORDSMAN);
        n.goalSelector.removeAllGoals(g -> g instanceof WorkGoal);
        n.targetSelector.removeAllGoals(g -> true);
        n.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        return n;
    }

    /**
     * {@code heroRaiderFliesInAndOpensTheWay}: a hostile yard whose gate is an iron door with a button inside; Ambrose
     * (a flyer, level 20) and two soldiers of his party outside. He chooses the flight over the wall (his way out: his
     * flight again) and opening the way as his goal, flies over the wall, lands inside and presses the button whenever the
     * door is shut while soldiers wait; they walk in through the gate, and neither ever stands on the wall. The test then
     * ends the raid, and he is out of the walls within 30 s.
     */
    @GameTest(template = FIELD, timeoutTicks = 1400, batch = "hero_raid_fly", skyAccess = true)
    public static void heroRaiderFliesInAndOpensTheWay(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 11, 1400);
        GameTestBases.Handle foe = GameTestBases.register(h, FOE, 5, 11, 1400);
        try {
            moveBase(h, t.base, 3, 3, 1);
            walledYard(h, foe.base, 22, 16, 5, Blocks.IRON_DOOR, 0);
            h.setBlock(new BlockPos(18, 3, 17), Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL)
                    .setValue(ButtonBlock.FACING, Direction.EAST));
            HeroEntity amb = sliceHero(t, "ambrose_nettlewick", "fx_raid_fly", 6, 16, 20);
            amb.targetSelector.removeAllGoals(g -> true);
            FactionNpc s1 = raider(t, 7, 14), s2 = raider(t, 7, 18);
            FactionState f = raid(t, foe.base, amb, s1, s2);
            BlockPos heart = foe.base.point(BaseState.HEART);
            int wallTop = (int) floorY(h) + 5;
            int[] over = {-1}, allIn = {-1}, ended = {-1};
            boolean[] flewOver = {false};
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 3) {
                    return;
                }
                for (FactionNpc s : new FactionNpc[]{s1, s2}) {
                    double[] r = rel(h, s);
                    t.assertTrue(r[1] < 3.5, "a soldier is on the wall at " + where(h, s));
                    if (now % 10 == 0 && s.getNavigation().isDone()) {
                        s.getNavigation().moveTo(heart.getX() + 0.5, heart.getY(), heart.getZ() + 0.5, 1.0);
                    }
                }
                double[] r = rel(h, amb);
                if (r[0] > 16.5 && r[0] < 17.5 && amb.getY() > wallTop) {
                    flewOver[0] = true;
                }
                HeroRaid raid = amb.raid();
                if (over[0] < 0 && BaseZones.insideWalls(foe.base, amb.blockPosition(), -1)) {
                    over[0] = now;
                    t.assertTrue(raid.entry() != null && raid.entry().way() == WallEntry.Way.FLY_OVER, "in by " + raid.entry());
                    t.assertTrue(raid.entry().exit() == WallEntry.Exit.FLIGHT, "out by " + raid.entry().exit());
                    t.assertTrue(raid.goal() == WallEntry.Goal.OPEN_THE_WAY, "goal " + raid.goal());
                    t.assertTrue(flewOver[0] && amb.travel().flights >= 1, "it did not fly over the wall: " + raid.describe());
                }
                if (allIn[0] < 0 && over[0] > 0 && BaseZones.insideWalls(foe.base, s1.blockPosition(), -1)
                        && BaseZones.insideWalls(foe.base, s2.blockPosition(), -1)) {
                    allIn[0] = now;
                    t.assertTrue(raid.presses >= 1, "nobody pressed the button");
                    f.raidTarget = -1; // the raid ends: it goes home, out of the walls
                    f.raiders.clear();
                    ended[0] = now;
                }
                if (ended[0] > 0 && !BaseZones.insideWalls(foe.base, amb.blockPosition(), 0)) {
                    t.assertTrue(now - ended[0] <= 600, "out of the walls after " + (now - ended[0]) + " ticks");
                    FactionClash.LOGGER.info("GAMETEST raid by air: over at {}, the party in at {}, out {} ticks after the end",
                            over[0] - 3, allIn[0] - 3, now - ended[0]);
                    t.succeed();
                }
            });
            h.runAtTickTime(1395, t.guard(() -> t.fail("raid: " + amb.raid().describe() + " at " + where(h, amb) + ", soldiers at "
                    + where(h, s1) + " " + where(h, s2) + ", over " + over[0] + ", in " + allIn[0] + ", ended " + ended[0])));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            foe.cleanup();
            throw e;
        }
    }

    /**
     * {@code climberRaiderScalesHostileWall}: Tobimaru (a climber II: 5 blocks and more from level 1) on a raid climbs the
     * 5-high wall of a hostile yard with an oak gate (never through the gate) and is inside; the base's alarm has given
     * its guard Tobimaru as a target by the time he tops the wall, or within 2 s of it.
     */
    @GameTest(template = FIELD, timeoutTicks = 600, batch = "hero_raid_climb")
    public static void climberRaiderScalesHostileWall(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 11, 600);
        GameTestBases.Handle foe = GameTestBases.register(h, FOE, 5, 11, 600);
        try {
            moveBase(h, t.base, 3, 3, 1);
            walledYard(h, foe.base, 22, 16, 5, Blocks.OAK_DOOR, 0);
            HeroEntity tobi = sliceHero(t, "tobimaru_usugumo", "fx_raid_climb", 9, 12, 1);
            tobi.targetSelector.removeAllGoals(g -> true);
            tobi.goalSelector.removeAllGoals(g -> g instanceof com.factionclash.combat.HeroAbilityGoal);
            FactionNpc g = guard(foe, 24, 18);
            g.setNoAi(true); // (it only has to be given him: a guard that came out to fight would end the climb)
            raid(t, foe.base, tobi);
            double floor = floorY(h);
            int[] topped = {-1}, alarmed = {-1};
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 3) {
                    return;
                }
                double[] r = rel(h, tobi);
                if (now % 20 == 0) {
                    FactionDirector.defenceAlarm(h.getLevel(), foe.base); // (the director's sweep, every second in a campaign)
                }
                t.assertTrue(!(r[0] > 16.4 && r[0] < 17.6 && Math.abs(r[2] - 16.5) < 1.2 && r[1] < 4), "he went through the gate");
                if (topped[0] < 0 && tobi.getY() >= floor + 5 - 0.1) {
                    topped[0] = now;
                }
                if (alarmed[0] < 0 && g.getTarget() == tobi) {
                    alarmed[0] = now;
                }
                if (topped[0] > 0 && BaseZones.insideWalls(foe.base, tobi.blockPosition(), -1)
                        && (alarmed[0] > 0 || now > topped[0] + 40)) {
                    t.assertTrue(tobi.raid().entry() != null && tobi.raid().entry().way() == WallEntry.Way.CLIMB_OVER,
                            "in by " + tobi.raid().entry());
                    t.assertTrue(taken(tobi, HeroLinks.Kind.CLIMB) >= 1 || tobi.raid().overStarts >= 1, "no climb");
                    t.assertTrue(alarmed[0] > 0 && alarmed[0] <= topped[0] + 40, "the alarm at " + alarmed[0] + ", on top at " + topped[0]);
                    FactionClash.LOGGER.info("GAMETEST climber raid: on top at {}, the alarm at {}", topped[0] - 3, alarmed[0] - 3);
                    t.succeed();
                }
            });
            h.runAtTickTime(595, t.guard(() -> t.fail("raid: " + tobi.raid().describe() + " at " + where(h, tobi) + " "
                    + tobi.movement().state() + ", topped " + topped[0] + ", alarmed " + alarmed[0])));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            foe.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroRaidsOverAWallOnlyWithAWayOut}: two hostile yards, each 5 high outside and sunk 1 inside (6 inside), one
     * with an oak gate and one with an iron door and nothing inside to open it. Natsuho at level 20 (a climber I's 5
     * blocks, an acrobat III's 3-block jump-up, no fall damage) chooses to climb over the first (her way out: the gate)
     * but the gate of the second, whose inside face she can neither climb nor jump and whose gate she cannot open from
     * inside: no way over without a way out.
     */
    @GameTest(template = FIELD, timeoutTicks = 60, batch = "hero_raid_wayout")
    public static void heroRaidsOverAWallOnlyWithAWayOut(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 11, 60);
        GameTestBases.Handle open = GameTestBases.register(h, FOE, 4, 11, 60);
        GameTestBases.Handle shut = GameTestBases.register(h, FOE, 4, 11, 60);
        try {
            moveBase(h, t.base, 3, 3, 1);
            walledYard(h, open.base, 9, 22, 4, Blocks.OAK_DOOR, 1);
            walledYard(h, shut.base, 23, 22, 4, Blocks.IRON_DOOR, 1);
            HeroEntity a = sliceHero(t, "natsuho_komezaki", "fx_raid_out_a", 9, 8, 20);
            HeroEntity b = sliceHero(t, "natsuho_komezaki", "fx_raid_out_b", 23, 8, 20);
            h.runAtTickTime(5, t.guard(() -> {
                a.raid().choose(open.base);
                b.raid().choose(shut.base);
                t.assertTrue(a.raid().entry().way() == WallEntry.Way.CLIMB_OVER && a.raid().entry().exit() == WallEntry.Exit.GATE,
                        "with a gate that opens from inside: " + a.raid().entry());
                t.assertTrue(b.raid().entry().way() == WallEntry.Way.GATE && b.raid().entry().exit() == WallEntry.Exit.NONE,
                        "with no way out: " + b.raid().entry());
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            open.cleanup();
            shut.cleanup();
            throw e;
        }
    }

    /**
     * {@code archersShootAirborneHeroFirst}: two archers of a base, shooting an enemy soldier of the other side, turn to an
     * enemy hero flying near (Ambrose hovering 2.8 blocks up) within a second, keep it while it flies (never trading it
     * for the weaker soldier: the ranged focus rule), and their arrows (20 air poise each) bring it down.
     */
    @GameTest(template = FIELD, timeoutTicks = 500, batch = "hero_raid_archers")
    public static void archersShootAirborneHeroFirst(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 11, 500);
        GameTestBases.Handle foe = GameTestBases.register(h, FOE, 2, 11, 500);
        try {
            moveBase(h, t.base, 3, 3, 1);
            moveBase(h, foe.base, 12, 6, 2);
            FactionNpc[] archers = new FactionNpc[4];
            for (int i = 0; i < 4; i++) {
                FactionNpc a = foe.npc(8 + 4 * i, 6, NpcRole.ARCHER);
                a.goalSelector.removeAllGoals(g -> g instanceof WorkGoal);
                a.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                a.setAmmo(new ItemStack(Items.ARROW));
                archers[i] = a;
            }
            FactionNpc soldier = HeroAbilityGameTests.noRegen(t.npc(3, 14, NpcRole.SWORDSMAN));
            soldier.setNoAi(true);
            HeroEntity amb = still(sliceHero(t, "ambrose_nettlewick", "fx_raid_archers", 18, 18, 1));
            double floor = floorY(h);
            h.runAtTickTime(3, t.guard(() -> {
                for (FactionNpc a : archers) {
                    a.setTarget(soldier);
                }
                t.assertTrue(amb.movement().takeOff() == null, "no take-off");
            }));
            h.runAtTickTime(20, t.guard(() -> t.assertTrue(amb.movement().flyTo(new net.minecraft.world.phys.Vec3(amb.getX(),
                    floor + 2.8, amb.getZ())) == null, "no flight")));
            int[] turned = {-1}, hits = {0};
            double[] poiseTop = {0};
            float[] hp = {0};
            StringBuilder log = new StringBuilder();
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (amb.getHealth() < hp[0]) {
                    hits[0]++;
                    if (hits[0] <= 6) {
                        net.minecraft.world.damagesource.DamageSource ds = amb.getLastDamageSource();
                        log.append(" [").append(now).append(' ').append(amb.movement().state()).append(' ')
                                .append(ds == null ? "?" : ds.getMsgId() + "/" + ds.getDirectEntity()).append(']');
                    }
                }
                hp[0] = amb.getHealth();
                poiseTop[0] = Math.max(poiseTop[0], amb.movement().airPoise().value());
                if (now < 30) {
                    return;
                }
                if (turned[0] < 0 && java.util.Arrays.stream(archers).allMatch(a -> a.getTarget() == amb)) {
                    turned[0] = now;
                    t.assertTrue(now <= 30 + 30, "the archers turned after " + (now - 30) + " ticks");
                }
                if (turned[0] > 0 && amb.movement().downs() == 0 && amb.movement().flying()) {
                    // kept: no archer trades the hero in the air for the weaker soldier on the ground (the focus rule)
                    for (FactionNpc a : archers) {
                        t.assertTrue(a.getTarget() == amb, "an archer turned back to " + a.getTarget() + " at " + now);
                    }
                }
                if (turned[0] > 0 && amb.movement().downs() >= 1) {
                    t.assertTrue(amb.movement().state() == HeroMovement.State.DOWNED_FALL
                            || amb.movement().state() == HeroMovement.State.DOWNED, "downed: " + amb.movement().state());
                    FactionClash.LOGGER.info("GAMETEST archers turned after {} ticks; the flyer was downed at {}", turned[0] - 30, now);
                    t.succeed();
                }
            });
            h.runAtTickTime(495, t.guard(() -> t.fail("turned " + turned[0] + ", targets " + archers[0].getTarget() + " / "
                    + archers[1].getTarget() + ", " + amb.movement().describe() + ", hits " + hits[0] + ", air poise up to "
                    + poiseTop[0] + ";" + log)));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            foe.cleanup();
            throw e;
        }
    }

    /**
     * {@code guardsAndHomeHeroInterceptIntruder}: an enemy hero lands in the yard of a base with four guards and its home
     * hero. The alarm gives it to the two nearest guards and the home hero within 2 s; the two far guards stay as they
     * were. An enemy hero flying 14 blocks over the yard counts too (the box now reaches 16 up), and one hovering 18 up,
     * over the box, is seen on its approach by a guard below and raises the alarm as well.
     */
    @GameTest(template = FIELD, timeoutTicks = 200, batch = "hero_raid_intercept")
    public static void guardsAndHomeHeroInterceptIntruder(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 11, 200);
        GameTestBases.Handle foe = GameTestBases.register(h, FOE, 6, 11, 200);
        try {
            moveBase(h, t.base, 3, 3, 1);
            moveBase(h, foe.base, 16, 16, 6);
            FactionNpc near1 = guard(foe, 12, 12), near2 = guard(foe, 13, 11), far1 = guard(foe, 24, 24), far2 = guard(foe, 25, 23);
            HeroEntity home = sliceHero(foe, "kagenori_tsurumine", "fx_raid_home", 20, 14, 10);
            home.targetSelector.removeAllGoals(g -> true);
            HeroEntity intruder = still(sliceHero(t, "natsuho_komezaki", "fx_raid_intruder", 14, 14, 10));
            int[] seen = {-1};
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 3) {
                    return;
                }
                if (now == 5) {
                    FactionDirector.defenceAlarm(h.getLevel(), foe.base);
                }
                if (seen[0] < 0 && near1.getTarget() == intruder && near2.getTarget() == intruder && home.getTarget() == intruder) {
                    seen[0] = now;
                    t.assertTrue(now <= 5 + 40, "intercepted after " + (now - 5) + " ticks");
                    t.assertTrue(far1.getTarget() != intruder && far2.getTarget() != intruder, "a far guard left its post");
                    t.succeed();
                }
            });
            h.runAtTickTime(195, t.guard(() -> t.fail("targets: " + near1.getTarget() + ", " + near2.getTarget() + ", home "
                    + home.getTarget())));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            foe.cleanup();
            throw e;
        }
    }

    /**
     * The alarm box 16 up and the approach seen (5.2): two enemy heroes hover over a base, one 14 blocks up (inside the
     * box now; over the old 12) and one 18 up (over the box, 24 blocks or less from a guard that sees it). The alarm sets
     * a guard on each.
     */
    @GameTest(template = FIELD, timeoutTicks = 200, batch = "hero_raid_alarm_up", skyAccess = true)
    public static void alarmSeesAFlyerOverTheWallAndOnItsApproach(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 11, 200);
        GameTestBases.Handle foe = GameTestBases.register(h, FOE, 6, 11, 200);
        try {
            moveBase(h, t.base, 3, 3, 1);
            moveBase(h, foe.base, 16, 16, 6);
            FactionNpc g1 = guard(foe, 12, 12), g2 = guard(foe, 20, 20), g3 = guard(foe, 12, 20), g4 = guard(foe, 20, 12);
            HeroEntity low = still(sliceHero(t, "ambrose_nettlewick", "fx_raid_alarm_14", 12, 18, 1));
            HeroEntity high = still(sliceHero(t, "ambrose_nettlewick", "fx_raid_alarm_18", 20, 14, 1));
            double floor = floorY(h);
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 3) {
                    return;
                }
                // they hang where they are put (the test holds them; flight is not what is checked here)
                low.setNoGravity(true);
                high.setNoGravity(true);
                low.moveTo(low.getX(), floor + 14, low.getZ());
                high.moveTo(high.getX(), floor + 18, high.getZ());
                if (now % 20 == 0) {
                    FactionDirector.defenceAlarm(h.getLevel(), foe.base);
                }
                java.util.Set<Object> targets = new java.util.HashSet<>();
                for (FactionNpc g : new FactionNpc[]{g1, g2, g3, g4}) {
                    targets.add(g.getTarget());
                }
                if (targets.contains(low) && targets.contains(high)) {
                    low.setNoGravity(false);
                    high.setNoGravity(false);
                    t.succeed();
                }
            });
            h.runAtTickTime(195, t.guard(() -> t.fail("guards' targets: " + g1.getTarget() + " / " + g2.getTarget())));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            foe.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ task R9: the home hero known from the recon

    /** Kagenori (a striker) as {@code as} with the focus {@code heroes_first}, at level 10, with no job and no target of its own. */
    static HeroEntity hunter(GameTestBases.Handle t, String as, int x, int z) {
        String text = HeroAbilityGameTests.slice("kagenori_tsurumine").replace("\"kagenori_tsurumine\"", "\"" + as + "\"");
        if (!text.contains("\"focus\": \"strongest\"")) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("Kagenori's fixture has no focus to change");
        }
        HeroEntity hero = HeroAbilityGameTests.hero(t, as, "katana", text.replace("\"focus\": \"strongest\"", "\"focus\": \"heroes_first\""), x, z);
        hero.goalSelector.removeAllGoals(g -> g instanceof WorkGoal);
        hero.targetSelector.removeAllGoals(g -> true);
        hero.restoreProgress(10, 0, java.util.Map.of());
        HeroAbilityGameTests.noRegen(hero);
        return hero;
    }

    /**
     * {@code aRaidingHeroHuntsTheHeroItsReconSaw} (docs/design/recon.md 8, task R9): a raiding hero whose focus is
     * {@code heroes_first} hunts the defending hero when the recon its raid was launched on saw one at home, or when it
     * sees one inside the walls now; a hero of the base merely somewhere within 64 blocks (outside the walls, in its sight)
     * is neither, so its goal is not the hunt (it was, before: any of the base's heroes within 64 blocks made one known).
     */
    @GameTest(template = FIELD, timeoutTicks = 60, batch = "hero_raid_known")
    public static void aRaidingHeroHuntsTheHeroItsReconSaw(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 11, 60);
        GameTestBases.Handle foe = GameTestBases.register(h, FOE, 5, 11, 60);
        try {
            moveBase(h, t.base, 3, 3, 1);
            walledYard(h, foe.base, 22, 16, 5, Blocks.OAK_DOOR, 0);
            FactionState f = t.data.faction(SIDE);
            java.util.List<String> before = java.util.List.copyOf(f.raidReconHeroes);
            t.onCleanup(() -> {
                f.raidReconHeroes.clear();
                f.raidReconHeroes.addAll(before);
            });
            HeroEntity kage = hunter(t, "fx_raid_known", 9, 16);
            HeroEntity home = sliceHero(foe, "hinae_shirasuzu", "fx_raid_home", 12, 4, 1);
            home.setNoAi(true);
            h.runAtTickTime(5, t.guard(() -> {
                t.assertTrue(home.baseId() == foe.base.id && kage.distanceTo(home) < 64 && kage.hasLineOfSight(home)
                        && !BaseZones.insideWalls(foe.base, home.blockPosition(), 0), "the defending hero should stand within 64 blocks, in"
                        + " sight, outside the walls: base " + home.baseId() + " of " + foe.base.id + ", " + kage.distanceTo(home) + " blocks, sight "
                        + kage.hasLineOfSight(home));
                f.raidReconHeroes.clear();
                kage.raid().choose(foe.base);
                t.assertTrue(kage.raid().goal() != WallEntry.Goal.HUNT_THE_HERO, "it hunted a hero no recon saw at home: " + kage.raid().goal());
                f.raidReconHeroes.add("fx_raid_home");
                kage.raid().choose(foe.base);
                t.assertTrue(kage.raid().goal() == WallEntry.Goal.HUNT_THE_HERO, "it did not hunt the hero its recon saw: " + kage.raid().goal());
                // seen inside the walls now: the hunt, with or without the recon
                f.raidReconHeroes.clear();
                home.moveTo(h.absolutePos(new BlockPos(24, 2, 18)).getX() + 0.5, floorY(h), h.absolutePos(new BlockPos(24, 2, 18)).getZ() + 0.5);
                kage.moveTo(h.absolutePos(new BlockPos(20, 2, 14)).getX() + 0.5, floorY(h), h.absolutePos(new BlockPos(20, 2, 14)).getZ() + 0.5);
                t.assertTrue(kage.hasLineOfSight(home), "the two heroes inside the yard do not see each other");
                kage.raid().choose(foe.base);
                t.assertTrue(kage.raid().goal() == WallEntry.Goal.HUNT_THE_HERO, "it did not hunt the hero it saw inside: " + kage.raid().goal());
                t.succeed();
                foe.cleanup();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            foe.cleanup();
            throw e;
        }
    }

    /**
     * {@code aHuntForAHeroNobodySeesEndsAfterAMinute} (task R9): its raid was launched on a recon that saw a hero at home,
     * so the raider hunts her; she is nowhere in its sight (away, or indoors). It looks for her across the yard, and after
     * a minute without seeing her ({@code WallEntry.HUNT_UNSEEN_TICKS}) the hunt ends and it joins the party at the heart
     * (before, the minute's clock was set back every tick while it saw nobody, so only the lack of progress ended it).
     */
    @GameTest(template = FIELD, timeoutTicks = 1800, batch = "hero_raid_hunt_ends")
    public static void aHuntForAHeroNobodySeesEndsAfterAMinute(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 11, 1800);
        GameTestBases.Handle foe = GameTestBases.register(h, FOE, 5, 11, 1800);
        try {
            moveBase(h, t.base, 3, 3, 1);
            walledYard(h, foe.base, 22, 16, 5, Blocks.OAK_DOOR, 0);
            HeroEntity kage = hunter(t, "fx_raid_hunt", 18, 12);
            FactionState f = raid(t, foe.base, kage);
            f.raidReconHeroes.add("fx_nobody_home");
            int[] chosen = {-1};
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                HeroRaid r = kage.raid();
                if (chosen[0] < 0 && r.goal() != null) {
                    chosen[0] = now;
                    t.assertTrue(r.goal() == WallEntry.Goal.HUNT_THE_HERO, "the goal should be the hunt: " + r.goal());
                }
                if (chosen[0] >= 0 && r.goalsEnded > 0) {
                    int took = now - chosen[0];
                    t.assertTrue(r.lastEnd == WallEntry.GoalEnd.DONE && took >= WallEntry.HUNT_UNSEEN_TICKS - 5
                            && took <= WallEntry.HUNT_UNSEEN_TICKS + 60, "the hunt for nobody in sight should end after its minute unseen: "
                            + r.lastEnd + " after " + took + " ticks");
                    t.assertTrue(r.stage() == HeroRaid.Stage.HEART, "after the hunt it should join the party at the heart: " + r.describe());
                    t.succeed();
                    foe.cleanup();
                }
            });
            h.runAtTickTime(1790, t.guard(() -> t.fail("the hunt never ended: " + kage.raid().describe() + ", chosen at " + chosen[0])));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            foe.cleanup();
            throw e;
        }
    }
}
