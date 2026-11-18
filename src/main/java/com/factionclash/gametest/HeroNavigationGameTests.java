package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroGroundNavigation;
import com.factionclash.hero.HeroMovement;
import com.factionclash.logic.hero.HeroLinks;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcNavigator;
import com.factionclash.npc.NpcPathNavigation;
import com.factionclash.npc.NpcRole;
import com.factionclash.work.WorkGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * Heroes, task A11: navigation with mobility (MOBILITY_AND_WORK 3 and 17.2's link and flight tests). A leaper leaps a
 * 4-wide ditch a walker goes round; a leaper and an acrobat (with its air jumps) jump up a 2-block ledge; a climber
 * scales a 6-high wall and climbs down its far side; a hero crosses a wall while a soldier walks round to the gateway
 * (walls are terrain for heroes, ordinary NPCs unchanged); a leaper leaps a lava channel with a wide landing out of a
 * fight and walks round it with a hostile near, an NPC soldier and a mock player alike (the risky-gap rule); a flyer
 * crosses a ravine and lands, falls back to the ground at the edge of loaded terrain, ends its flight under a canopy
 * and walks on, and walks a tunnel it cannot take off in; heroes cross an obstacle course with no more stuck recoveries
 * than a worker; a hero reaches an enemy on a ledge at once with a link (an NPC and a mock player alike), with no
 * planning and no wait; and workers keep their navigation. Each test that runs a hero has a batch of its own; the
 * heroes' jobs are taken away so their trips are the test's.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroNavigationGameTests {
    private HeroNavigationGameTests() {
    }

    static final String FIELD = HeroMobilityGameTests.FIELD;
    private static final FactionType SIDE = FactionType.EMBER_LEGION, FOE = FactionType.GROVE_COVENANT;

    // ------------------------------------------------------------------ fixtures

    /** A slice hero on a trip of the test's: no job, no target of its own. */
    static HeroEntity traveller(GameTestBases.Handle t, String slice, String id, int x, int z, int level) {
        HeroEntity hero = HeroMobilityGameTests.sliceHero(t, slice, id, x, z, level);
        hero.targetSelector.removeAllGoals(g -> true);
        return hero;
    }

    /** A slice hero's kit with another mobility block (a climber I, an acrobat with no climbing). */
    static HeroEntity withMobility(GameTestBases.Handle t, String slice, String id, String mobility, int x, int z,
                                   int level) {
        String text = HeroAbilityGameTests.slice(slice).replace("\"" + slice + "\"", "\"" + id + "\"");
        int i = text.indexOf("\"mobility\"");
        int e = text.indexOf('\n', i);
        text = text.substring(0, i) + "\"mobility\": " + mobility + text.substring(e);
        HeroEntity hero = HeroAbilityGameTests.hero(t, id, "katana", text, x, z);
        hero.goalSelector.removeAllGoals(g -> g instanceof WorkGoal);
        hero.targetSelector.removeAllGoals(g -> true);
        hero.restoreProgress(level, 0, Map.of());
        HeroAbilityGameTests.noRegen(hero);
        return hero;
    }

    /** A worker (or a soldier) of the hero's faction with no job and no target: only the test's trip moves it. */
    static FactionNpc walker(GameTestBases.Handle t, int x, int z, NpcRole role) {
        FactionNpc n = t.npc(x, z, role);
        n.goalSelector.removeAllGoals(g -> g instanceof WorkGoal);
        n.targetSelector.removeAllGoals(g -> true);
        return n;
    }

    static HeroGroundNavigation nav(HeroEntity h) {
        return (HeroGroundNavigation) h.getNavigation();
    }

    static int taken(HeroEntity h, HeroLinks.Kind k) {
        return nav(h).taken.getOrDefault(k, 0);
    }

    static void fill(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    h.setBlock(new BlockPos(x, y, z), s);
                }
            }
        }
    }

    static void fill(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
        fill(h, x0, y0, z0, x1, y1, z1, b.defaultBlockState());
    }

    static BlockState leaves() {
        return Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    /** Relative x, y, z of an entity in the test's field. */
    static double[] rel(GameTestHelper h, Entity e) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        return new double[]{e.getX() - o.getX(), e.getY() - o.getY(), e.getZ() - o.getZ()};
    }

    static String where(GameTestHelper h, Entity e) {
        double[] r = rel(h, e);
        return String.format(java.util.Locale.ROOT, "(%.1f, %.1f, %.1f)", r[0], r[1], r[2]);
    }

    static String heroState(GameTestHelper h, HeroEntity hero) {
        HeroGroundNavigation n = nav(hero);
        return hero.heroId() + " at " + where(h, hero) + ", " + hero.movement().state() + ", links taken " + n.taken
                + ", failed " + n.linksFailed + ", dead " + n.deadCount() + ", flights " + hero.travel().flights
                + ", recoveries " + hero.navigator().recoveries + ", last problem '" + hero.navigator().lastProblem() + "'";
    }

    /** One trip's state across ticks: arrived (the tick), the results seen. */
    static final class Trip {
        final FactionNpc npc;
        final BlockPos dest;
        int arrivedAt = -1;
        int failed;

        Trip(FactionNpc npc, BlockPos dest) {
            this.npc = npc;
            this.dest = dest;
        }

        boolean step(GameTestHelper h) {
            if (arrivedAt >= 0) {
                return true;
            }
            NpcNavigator.Result r = npc.navigator().travel(dest, 1.5, 1.0);
            if (r == NpcNavigator.Result.ARRIVED) {
                arrivedAt = (int) h.getTick();
                npc.navigator().reset();
                return true;
            }
            if (r == NpcNavigator.Result.FAILED) {
                failed++;
                npc.navigator().reset();
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ ground links (3.2-3.4)

    /**
     * {@code leaperCrossesGapWithoutBridge}: Kagenori at level 10 (a 4-block gap) and a worker both go to the far side of
     * a ditch 4 wide and 2 deep that runs across most of the field. He leaps it (a gap link, the solver's arc), never
     * going round; the worker walks round its end and never into it; he gets there first, and nothing is stuck.
     */
    @GameTest(template = FIELD, timeoutTicks = 900, batch = "hero_nav_gap")
    public static void leaperCrossesAGapAWalkerGoesRound(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 900);
        try {
            fill(h, 13, 0, 6, 16, 1, 24, Blocks.AIR);
            HeroEntity kage = traveller(t, "kagenori_tsurumine", "fx_nav_gap", 7, 15, 10);
            FactionNpc worker = walker(t, 7, 12, NpcRole.HAULER);
            Trip hero = new Trip(kage, h.absolutePos(new BlockPos(22, 2, 15)));
            Trip walk = new Trip(worker, h.absolutePos(new BlockPos(22, 2, 12)));
            double[] walkerFar = {0};
            t.onEachTick(() -> {
                if (h.getTick() < 3) {
                    return;
                }
                hero.step(h);
                walk.step(h);
                double[] k = rel(h, kage), w = rel(h, worker);
                t.assertTrue(k[2] > 5.5 && k[2] < 25.5, "the leaper went round the ditch: " + heroState(h, kage));
                if (k[0] > 13 && k[0] < 17) {
                    t.assertTrue(k[1] > 1.5, "the leaper fell into the ditch: " + heroState(h, kage));
                }
                if (w[0] > 13 && w[0] < 17) {
                    t.assertTrue(w[1] > 1.5, "the worker went into the ditch at " + where(h, worker));
                }
                walkerFar[0] = Math.max(walkerFar[0], Math.abs(w[2] - 15));
                if (hero.arrivedAt >= 0 && walk.arrivedAt >= 0) {
                    t.assertTrue(taken(kage, HeroLinks.Kind.GAP) >= 1, "no gap link: " + heroState(h, kage));
                    t.assertTrue(walkerFar[0] > 9, "the worker did not go round (" + walkerFar[0] + ")");
                    t.assertTrue(hero.arrivedAt < walk.arrivedAt, "the leaper " + hero.arrivedAt + ", the worker " + walk.arrivedAt);
                    t.assertTrue(kage.navigator().recoveries == 0, "a stuck recovery: " + heroState(h, kage));
                    t.succeed();
                }
            });
            h.runAtTickTime(895, t.guard(() -> t.fail("not there: " + heroState(h, kage) + "; worker at " + where(h, worker)
                    + " arrived " + walk.arrivedAt)));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code leaperClimbsTwoBlockLedge}: a plateau 2 blocks high across the field. Kagenori at level 20 (a 2.5-block jump)
     * jumps up onto it; an acrobat III at level 1 (a 1.4-block jump and two air jumps of 0.75) gets up with its air jumps
     * at the top of its jump. Both take a jump-up link (the crouch, then up and over the edge) and arrive on top.
     */
    @GameTest(template = FIELD, timeoutTicks = 500, batch = "hero_nav_ledge")
    public static void leaperAndAcrobatJumpUpATwoBlockLedge(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 500);
        try {
            fill(h, 16, 2, 0, 31, 3, 31, Blocks.STONE);
            HeroEntity kage = traveller(t, "kagenori_tsurumine", "fx_nav_ledge_k", 8, 9, 20);
            HeroEntity acro = withMobility(t, "natsuho_komezaki", "fx_nav_ledge_a", "{\"primary\": {\"trait\": \"acrobat\", \"tier\": 3}}",
                    8, 21, 1);
            Trip a = new Trip(kage, h.absolutePos(new BlockPos(22, 4, 9))), b = new Trip(acro, h.absolutePos(new BlockPos(22, 4, 21)));
            int[] airJumps = {0};
            t.onEachTick(() -> {
                if (h.getTick() < 3) {
                    return;
                }
                a.step(h);
                b.step(h);
                airJumps[0] = Math.max(airJumps[0], acro.movement().airJumpsUsed());
                if (a.arrivedAt >= 0 && b.arrivedAt >= 0) {
                    t.assertTrue(taken(kage, HeroLinks.Kind.JUMP_UP) >= 1, "Kagenori: " + heroState(h, kage));
                    t.assertTrue(taken(acro, HeroLinks.Kind.JUMP_UP) >= 1, "the acrobat: " + heroState(h, acro));
                    t.assertTrue(airJumps[0] >= 1, "the acrobat got up without an air jump");
                    t.assertTrue(kage.navigator().recoveries == 0 && acro.navigator().recoveries == 0, "a stuck recovery");
                    t.succeed();
                }
            });
            h.runAtTickTime(495, t.guard(() -> t.fail("not there: " + heroState(h, kage) + "; " + heroState(h, acro))));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code climberScalesWall} (A11): a wall 6 high across the field. A climber I at level 20 (climbs 6, falls 3 safely)
     * climbs up its face at the ladder rate, steps onto its top and climbs down the far face (a drop of 6 would hurt it),
     * unhurt; then walks on.
     */
    @GameTest(template = FIELD, timeoutTicks = 600, batch = "hero_nav_climb")
    public static void climberScalesASixHighWallAndClimbsDown(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 600);
        try {
            fill(h, 15, 2, 0, 15, 7, 31, Blocks.STONE);
            HeroEntity climber = withMobility(t, "kagenori_tsurumine", "fx_nav_climber", "{\"primary\": {\"trait\": \"climber\", \"tier\": 1}}",
                    10, 15, 20);
            Trip trip = new Trip(climber, h.absolutePos(new BlockPos(22, 2, 15)));
            float[] health = {0};
            double[] top = {0};
            boolean[] down = {false};
            t.onEachTick(() -> {
                if (h.getTick() < 3) {
                    health[0] = climber.getHealth();
                    return;
                }
                t.assertTrue(climber.movement().profile().climbHeight() == 6, "climb height " + climber.movement().profile().climbHeight());
                trip.step(h);
                top[0] = Math.max(top[0], rel(h, climber)[1]);
                down[0] |= climber.movement().state() == HeroMovement.State.CLIMB_DOWN;
                if (trip.arrivedAt >= 0) {
                    t.assertTrue(taken(climber, HeroLinks.Kind.CLIMB) >= 1 && taken(climber, HeroLinks.Kind.CLIMB_DOWN) >= 1,
                            "climbed up and down: " + heroState(h, climber));
                    t.assertTrue(down[0], "it never climbed down the face");
                    t.assertTrue(top[0] >= 7.9, "it never stood on the wall (" + top[0] + ")");
                    t.assertTrue(climber.getHealth() >= health[0], "hurt: " + climber.getHealth() + " of " + health[0]);
                    t.assertTrue(climber.navigator().recoveries == 0, "a stuck recovery");
                    t.succeed();
                }
            });
            h.runAtTickTime(595, t.guard(() -> t.fail("not there: " + heroState(h, climber))));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Walls are terrain for heroes, not for soldiers (3.7): a walled yard 5 high (a base wall's height) with its gateway
     * on the far side. Tobimaru (climber II: 9 blocks at level 1) climbs the near wall and drops inside (no fall damage);
     * an ordinary soldier walks round to the gateway and in through it, never onto a wall.
     */
    @GameTest(template = FIELD, timeoutTicks = 900, batch = "hero_nav_wall")
    public static void heroCrossesAWallASoldierGoesThroughTheGateway(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 900);
        try {
            fill(h, 18, 2, 8, 18, 6, 22, Blocks.STONE_BRICKS);
            fill(h, 28, 2, 8, 28, 6, 22, Blocks.STONE_BRICKS);
            fill(h, 18, 2, 8, 28, 6, 8, Blocks.STONE_BRICKS);
            fill(h, 18, 2, 22, 28, 6, 22, Blocks.STONE_BRICKS);
            fill(h, 28, 2, 14, 28, 4, 15, Blocks.AIR); // the gateway, on the far side
            HeroEntity tobi = traveller(t, "tobimaru_usugumo", "fx_nav_wall", 8, 15, 1);
            FactionNpc soldier = walker(t, 8, 12, NpcRole.SWORDSMAN);
            Trip hero = new Trip(tobi, h.absolutePos(new BlockPos(23, 2, 15)));
            Trip walk = new Trip(soldier, h.absolutePos(new BlockPos(23, 2, 13)));
            boolean[] soldierGate = {false};
            t.onEachTick(() -> {
                if (h.getTick() < 3) {
                    return;
                }
                hero.step(h);
                walk.step(h);
                double[] k = rel(h, tobi), s = rel(h, soldier);
                t.assertTrue(hero.arrivedAt >= 0 || k[0] < 27, "the hero went round by the gateway: " + heroState(h, tobi));
                t.assertTrue(s[1] < 3.5, "the soldier is on a wall at " + where(h, soldier));
                soldierGate[0] |= s[0] > 27.5 && s[2] > 13.5 && s[2] < 16.5;
                if (hero.arrivedAt >= 0 && walk.arrivedAt >= 0) {
                    t.assertTrue(taken(tobi, HeroLinks.Kind.CLIMB) >= 1, "no climb: " + heroState(h, tobi));
                    t.assertTrue(soldierGate[0], "the soldier did not come in through the gateway");
                    t.assertTrue(tobi.navigator().recoveries == 0, "a stuck recovery: " + heroState(h, tobi));
                    t.succeed();
                }
            });
            h.runAtTickTime(895, t.guard(() -> t.fail("not there: " + heroState(h, tobi) + "; soldier at " + where(h, soldier)
                    + " arrived " + walk.arrivedAt)));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code leaperRefusesLavaGapInFight}, shared: a lava channel 3 wide across most of the field (a risky gap: a fall
     * into it would kill), the way round at its end. {@code enemy}: none, an NPC soldier or a mock player of a hostile
     * faction standing within 16 blocks (it does nothing). Out of a fight the leaper leaps it, the landing being wide;
     * with a hostile near, whoever it is, it walks round.
     */
    private static void lavaChannel(GameTestHelper h, String enemy) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 900);
        try {
            fill(h, 13, 1, 0, 15, 1, 20, Blocks.AIR);
            fill(h, 13, 0, 0, 15, 0, 20, Blocks.LAVA);
            HeroEntity kage = traveller(t, "kagenori_tsurumine", "fx_nav_lava_" + enemy, 8, 10, 10);
            LivingEntity foe = switch (enemy) {
                case "npc" -> HeroAbilityGameTests.foe(t, 6, 3, 20);
                case "player" -> HeroAbilityGameTests.player(t, 6, 3, FOE, 20);
                default -> null;
            };
            Trip trip = new Trip(kage, h.absolutePos(new BlockPos(21, 2, 10)));
            double[] far = {0};
            float[] health = {0};
            t.onEachTick(() -> {
                if (h.getTick() < 3) {
                    health[0] = kage.getHealth();
                    return;
                }
                trip.step(h);
                double[] k = rel(h, kage);
                far[0] = Math.max(far[0], k[2]);
                t.assertTrue(!kage.isInLava() && !kage.isOnFire(), "in the lava: " + heroState(h, kage));
                if (foe != null && kage.distanceTo(foe) < 15 && k[0] > 12) {
                    t.assertTrue(taken(kage, HeroLinks.Kind.GAP) == 0, "it leapt the lava with a hostile near: " + heroState(h, kage));
                }
                if (trip.arrivedAt >= 0) {
                    if (foe == null) {
                        t.assertTrue(taken(kage, HeroLinks.Kind.GAP) >= 1 && far[0] < 20, "out of a fight it leaps: "
                                + heroState(h, kage) + ", z up to " + far[0]);
                    } else {
                        t.assertTrue(taken(kage, HeroLinks.Kind.GAP) == 0 && far[0] > 20, "with a hostile near it walks round: "
                                + heroState(h, kage) + ", z up to " + far[0]);
                    }
                    t.assertTrue(kage.getHealth() >= health[0], "hurt");
                    t.succeed();
                }
            });
            h.runAtTickTime(895, t.guard(() -> t.fail("not there: " + heroState(h, kage))));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    @GameTest(template = FIELD, timeoutTicks = 900, batch = "hero_nav_lava")
    public static void leaperLeapsALavaChannelOutOfAFight(GameTestHelper h) {
        lavaChannel(h, "none");
    }

    @GameTest(template = FIELD, timeoutTicks = 900, batch = "hero_nav_lava_npc")
    public static void leaperWalksRoundALavaChannelWithAnNpcFoeNear(GameTestHelper h) {
        lavaChannel(h, "npc");
    }

    @GameTest(template = FIELD, timeoutTicks = 900, batch = "hero_nav_lava_player")
    public static void leaperWalksRoundALavaChannelWithAPlayerFoeNear(GameTestHelper h) {
        lavaChannel(h, "player");
    }

    // ------------------------------------------------------------------ flight (3.5)

    /**
     * {@code flyerCrossesRiverAndLands}: a ravine 8 wide across the whole field that nobody walks out of. Ambrose (flyer
     * II, level 20) has no ground route, so he takes off, crosses at the cruise layer, picks the destination's cell as his
     * landing and comes down on it, unhurt; the ground navigation does the last step.
     */
    @GameTest(template = FIELD, timeoutTicks = 600, batch = "hero_nav_ravine", skyAccess = true)
    public static void flyerCrossesARavineAndLands(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 600);
        try {
            fill(h, 12, 0, 0, 19, 1, 31, Blocks.AIR);
            HeroEntity amb = traveller(t, "ambrose_nettlewick", "fx_nav_ravine", 5, 15, 20);
            Trip trip = new Trip(amb, h.absolutePos(new BlockPos(26, 2, 15)));
            float[] health = {0};
            double[] high = {0};
            boolean[] flew = {false};
            t.onEachTick(() -> {
                if (h.getTick() < 3) {
                    health[0] = amb.getHealth();
                    return;
                }
                trip.step(h);
                double[] r = rel(h, amb);
                flew[0] |= amb.movement().flying();
                if (r[0] > 12 && r[0] < 20) {
                    t.assertTrue(r[1] > 2.5, "low over the ravine: " + heroState(h, amb));
                    high[0] = Math.max(high[0], r[1]);
                }
                if (trip.arrivedAt >= 0) {
                    t.assertTrue(flew[0] && amb.travel().flights == 1 && amb.travel().landed == 1, "one flight that landed: "
                            + heroState(h, amb));
                    t.assertTrue(amb.movement().state() == HeroMovement.State.NONE && amb.onGround(), "still in the air");
                    t.assertTrue(amb.getHealth() >= health[0], "hurt");
                    t.assertTrue(amb.movement().stamina().share() < 1, "no stamina spent");
                    t.succeed();
                }
            });
            h.runAtTickTime(595, t.guard(() -> t.fail("not there: " + heroState(h, amb))));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * The ground fallback at the edge of loaded terrain (3.5): Ambrose flies toward a destination 300 blocks off; the
     * terrain from 20 blocks into the field on reads as not loaded (the test's seam: a test field's own chunks all tick,
     * and its barrier walls stand where unloaded terrain would begin). His legs end at the last station over loaded
     * terrain; he hovers there at most 3 s and lands short of the edge, and the trip goes on on foot (no take-off again at
     * once). The planner never reads past the edge: he never flies over it.
     */
    @GameTest(template = FIELD, timeoutTicks = 600, batch = "hero_nav_edge", skyAccess = true)
    public static void flightFallsBackToTheGroundAtTheLoadedEdge(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 600);
        int edgeX = h.absolutePos(new BlockPos(20, 0, 0)).getX();
        com.factionclash.hero.HeroTravel.unloadedForTests = (x, z) -> x >= edgeX;
        t.onCleanup(() -> com.factionclash.hero.HeroTravel.unloadedForTests = null);
        try {
            HeroEntity amb = traveller(t, "ambrose_nettlewick", "fx_nav_edge", 4, 15, 20);
            Trip trip = new Trip(amb, h.absolutePos(new BlockPos(300, 2, 15)));
            int[] landedAt = {-1};
            int[] maxHover = {0};
            boolean[] flew = {false};
            t.onEachTick(() -> {
                if (h.getTick() < 3) {
                    return;
                }
                trip.step(h);
                HeroMovement m = amb.movement();
                double[] r = rel(h, amb);
                if (amb.travel().flying()) {
                    flew[0] = true;
                    t.assertTrue(r[0] < 20, "it flew over terrain that is not loaded: " + heroState(h, amb));
                }
                boolean hovering = m.state() == HeroMovement.State.FLY && amb.getDeltaMovement().horizontalDistance() < 0.05;
                if (hovering && amb.travel().flying() && r[0] > 10) {
                    maxHover[0]++; // ticks hovering at the edge (the descent and the landing after it are not)
                }
                if (landedAt[0] < 0 && amb.travel().edgeEnds >= 1 && !amb.travel().flying() && amb.onGround()) {
                    landedAt[0] = (int) h.getTick();
                }
                if (landedAt[0] >= 0) {
                    t.assertTrue(!amb.travel().flying() && amb.travel().flights == 1, "it took off again at once: " + heroState(h, amb));
                }
                if (landedAt[0] >= 0 && h.getTick() >= landedAt[0] + 40) {
                    t.assertTrue(flew[0] && "the edge of loaded terrain".equals(amb.travel().lastEnd()), "ended: " + amb.travel().lastEnd());
                    t.assertTrue(maxHover[0] <= 3 * 20 + 25, "it hovered " + maxHover[0] + " ticks at the edge");
                    t.assertTrue(amb.navigator().target() != null && trip.failed == 0, "the trip did not go on on foot: " + heroState(h, amb));
                    t.succeed();
                }
            });
            h.runAtTickTime(595, t.guard(() -> t.fail("no fallback: " + heroState(h, amb) + ", hovered " + maxHover[0])));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code flyerUnderCanopyLandsAndWalks}, as built: a leaf roof 4 blocks over the ground where Ambrose stands (just the
     * headroom a take-off needs) on a trip of 41 blocks, long enough to fly. A trip's take-off needs the sky over him
     * too, so he never takes off under the leaves (he would only bump his head on them, as he did on a 4-high building's
     * ceiling in the campaign): he walks out from under them, may fly from the open, and gets there with no bump and no
     * stuck recovery.
     */
    @GameTest(template = FIELD, timeoutTicks = 1000, batch = "hero_nav_canopy", skyAccess = true)
    public static void flyerUnderACanopyWalksOutBeforeItFlies(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 1000);
        try {
            fill(h, 0, 6, 0, 14, 6, 14, leaves());
            HeroEntity amb = traveller(t, "ambrose_nettlewick", "fx_nav_canopy", 1, 1, 20);
            Trip trip = new Trip(amb, h.absolutePos(new BlockPos(31, 2, 31)));
            boolean[] bumped = {false};
            t.onEachTick(() -> {
                if (h.getTick() < 3) {
                    return;
                }
                trip.step(h);
                double[] r = rel(h, amb);
                if (r[0] < 15.3 && r[2] < 15.3) {
                    t.assertTrue(!amb.travel().flying() && !amb.movement().flying(), "it took off under the leaves: "
                            + heroState(h, amb));
                }
                bumped[0] |= amb.movement().flying() && amb.verticalCollision && !amb.verticalCollisionBelow;
                t.assertTrue(!bumped[0], "it bumped its head: " + heroState(h, amb));
                if (trip.arrivedAt >= 0) {
                    t.assertTrue(amb.travel().stuckEnds == 0, "a flight ended stuck: " + heroState(h, amb));
                    t.assertTrue(amb.navigator().recoveries == 0, "a stuck recovery: " + heroState(h, amb));
                    t.succeed();
                }
            });
            h.runAtTickTime(995, t.guard(() -> t.fail("not there: " + heroState(h, amb) + ", ended " + amb.travel().lastEnd())));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * A leaf roof 4 blocks up over a strip across the field (x 2-20), Ambrose in the open right beside it, his
     * destination in the open beyond it. He flies, and climbing toward his cruise layer he would pass under the strip's
     * edge on the slant: the probe sees it and he rises in place until he is over it, then crosses and lands. No head
     * bump, no stuck end, no recovery.
     */
    @GameTest(template = FIELD, timeoutTicks = 1000, batch = "hero_nav_canopy_edge", skyAccess = true)
    public static void flyerBesideACanopyRisesClearOfItsEdge(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 1000);
        try {
            fill(h, 2, 6, 0, 20, 6, 31, leaves());
            HeroEntity amb = traveller(t, "ambrose_nettlewick", "fx_nav_canopy_edge", 1, 1, 20);
            Trip trip = new Trip(amb, h.absolutePos(new BlockPos(31, 2, 31)));
            boolean[] bumped = {false};
            t.onEachTick(() -> {
                if (h.getTick() < 3) {
                    return;
                }
                trip.step(h);
                bumped[0] |= amb.movement().flying() && amb.verticalCollision && !amb.verticalCollisionBelow;
                t.assertTrue(!bumped[0], "it bumped its head on the strip's edge: " + heroState(h, amb));
                t.assertTrue(amb.travel().stuckEnds == 0, "the flight ended stuck: " + heroState(h, amb));
                if (trip.arrivedAt >= 0) {
                    t.assertTrue(amb.travel().flights >= 1 && amb.travel().landed >= 1, "it did not fly over: "
                            + heroState(h, amb));
                    t.assertTrue(amb.navigator().recoveries == 0, "a stuck recovery: " + heroState(h, amb));
                    t.succeed();
                }
            });
            h.runAtTickTime(995, t.guard(() -> t.fail("not there: " + heroState(h, amb) + ", ended " + amb.travel().lastEnd())));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code flyerUnderCanopyLandsAndWalks}'s stuck end: Ambrose takes off in the open on a 41-block trip, and once he
     * flies a stone roof is put over the whole field 2 blocks over his head (someone built over him). Climbing, he bumps
     * his head; after 10 ticks of it the flight ends stuck in the air, he lands unhurt, that leg is dead, and the trip
     * finishes on foot under the roof with no stuck recovery.
     */
    @GameTest(template = FIELD, timeoutTicks = 1000, batch = "hero_nav_roofed", skyAccess = true)
    public static void flightRoofedOverEndsStuckAndWalks(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 1000);
        try {
            HeroEntity amb = traveller(t, "ambrose_nettlewick", "fx_nav_roofed", 1, 1, 20);
            Trip trip = new Trip(amb, h.absolutePos(new BlockPos(31, 2, 31)));
            float[] health = {0};
            boolean[] bumped = {false}, roofed = {false};
            t.onEachTick(() -> {
                if (h.getTick() < 3) {
                    health[0] = amb.getHealth();
                    return;
                }
                trip.step(h);
                if (!roofed[0] && amb.movement().state() == HeroMovement.State.FLY) {
                    roofed[0] = true;
                    int y = (int) Math.ceil(amb.getBoundingBox().maxY) + 2 - h.absolutePos(BlockPos.ZERO).getY();
                    fill(h, 0, y, 0, 31, y, 31, Blocks.STONE);
                }
                bumped[0] |= amb.movement().flying() && amb.verticalCollision && !amb.verticalCollisionBelow;
                if (trip.arrivedAt >= 0) {
                    t.assertTrue(roofed[0], "it never flew");
                    t.assertTrue(bumped[0], "it never bumped its head on the roof");
                    t.assertTrue(amb.travel().flights == 1 && amb.travel().stuckEnds == 1, "one flight, ended stuck: "
                            + heroState(h, amb));
                    t.assertTrue("stuck in the air".equals(amb.travel().lastEnd()), "ended: " + amb.travel().lastEnd());
                    t.assertTrue(amb.navigator().recoveries == 0, "a stuck recovery: " + heroState(h, amb));
                    t.assertTrue(amb.getHealth() >= health[0], "hurt");
                    t.succeed();
                }
            });
            h.runAtTickTime(995, t.guard(() -> t.fail("not there: " + heroState(h, amb) + ", ended " + amb.travel().lastEnd())));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code flyerWalksInTunnel}: a stone roof 3 blocks over the whole field (less than the 4 a take-off needs). Ambrose's
     * 41-block trip would be flown in the open; here he never takes off and walks it.
     */
    @GameTest(template = FIELD, timeoutTicks = 1000, batch = "hero_nav_tunnel", skyAccess = true)
    public static void flyerWalksInATunnel(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 1000);
        try {
            fill(h, 0, 5, 0, 31, 5, 31, Blocks.STONE);
            HeroEntity amb = traveller(t, "ambrose_nettlewick", "fx_nav_tunnel", 1, 1, 20);
            Trip trip = new Trip(amb, h.absolutePos(new BlockPos(31, 2, 31)));
            t.onEachTick(() -> {
                if (h.getTick() < 3) {
                    return;
                }
                trip.step(h);
                t.assertTrue(amb.travel().flights == 0 && !amb.movement().flying(), "it took off under the roof");
                if (trip.arrivedAt >= 0) {
                    t.assertTrue(amb.navigator().recoveries == 0, "a stuck recovery: " + heroState(h, amb));
                    t.succeed();
                }
            });
            h.runAtTickTime(995, t.guard(() -> t.fail("not there: " + heroState(h, amb))));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ never stuck where a worker is not (17.3)

    /**
     * Heroes are never stuck where a worker is not: an obstacle course across the field (a step, a fence with a gap per
     * lane, a ditch with a bridge per lane, a tree in each lane, a step down). Tobimaru (acrobat and climber), Kagenori
     * (leaper), Ambrose (flyer) and a worker cross it in lanes of their own; everyone arrives, and no hero needs more
     * stuck recoveries than the worker.
     */
    @GameTest(template = FIELD, timeoutTicks = 1200, batch = "hero_nav_course", skyAccess = true)
    public static void heroesAreNeverStuckWhereAWorkerIsNot(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 1200);
        try {
            int[] lanes = {4, 11, 18, 25};
            fill(h, 6, 2, 0, 23, 2, 31, Blocks.STONE); // a step up (the ground's top at 3 from x 6 to 23)
            fill(h, 10, 3, 0, 10, 3, 31, Blocks.OAK_FENCE);
            fill(h, 14, 1, 0, 16, 2, 31, Blocks.AIR); // a ditch 3 wide, 2 deep
            for (int z : lanes) {
                h.setBlock(new BlockPos(10, 3, z + 2), Blocks.AIR); // a gap in the fence per lane
                fill(h, 14, 2, z + 2, 16, 2, z + 2, Blocks.STONE); // a bridge per lane
                fill(h, 20, 3, z, 20, 7, z, Blocks.OAK_LOG); // a tree per lane
                fill(h, 19, 6, z - 1, 21, 7, z + 1, leaves());
                h.setBlock(new BlockPos(20, 6, z), Blocks.OAK_LOG);
                h.setBlock(new BlockPos(20, 7, z), Blocks.OAK_LOG);
            }
            HeroEntity tobi = traveller(t, "tobimaru_usugumo", "fx_nav_course_t", 2, lanes[0], 1);
            HeroEntity kage = traveller(t, "kagenori_tsurumine", "fx_nav_course_k", 2, lanes[1], 10);
            HeroEntity amb = traveller(t, "ambrose_nettlewick", "fx_nav_course_a", 2, lanes[2], 20);
            FactionNpc worker = walker(t, 2, lanes[3], NpcRole.HAULER);
            FactionNpc[] all = {tobi, kage, amb, worker};
            Trip[] trips = new Trip[4];
            for (int i = 0; i < 4; i++) {
                trips[i] = new Trip(all[i], h.absolutePos(new BlockPos(29, 2, lanes[i])));
            }
            t.onEachTick(() -> {
                if (h.getTick() < 3) {
                    return;
                }
                boolean done = true;
                for (Trip tr : trips) {
                    done &= tr.step(h);
                }
                if (done) {
                    int w = worker.navigator().recoveries;
                    for (HeroEntity hero : new HeroEntity[]{tobi, kage, amb}) {
                        t.assertTrue(hero.navigator().recoveries <= w, "more stuck recoveries than the worker's " + w + ": "
                                + heroState(h, hero));
                    }
                    for (Trip tr : trips) {
                        t.assertTrue(tr.failed == 0, tr.npc.getName().getString() + " gave a trip up");
                    }
                    t.succeed();
                }
            });
            h.runAtTickTime(1195, t.guard(() -> t.fail("not there: " + heroState(h, tobi) + "; " + heroState(h, kage) + "; "
                    + heroState(h, amb) + "; worker at " + where(h, worker) + " arrived " + trips[3].arrivedAt)));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ reach: mobility first, no wait (3.7, 3.8)

    /**
     * {@code heroReachesPillarPlayerAtOnce}, the ledge case, shared: an enemy (an NPC soldier or a mock player, the same
     * rules) stands on a platform 2 blocks high with no way up for a walker. Kagenori at level 20 chases it: his path
     * search finds the jump-up link at once, so he is up and lands a blow within 4 s, with no planning (no pillar, no
     * breach) and no 10 s wait.
     */
    private static void ledgeEnemy(GameTestHelper h, boolean player) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            fill(h, 18, 2, 14, 20, 3, 16, Blocks.STONE);
            HeroEntity kage = HeroMobilityGameTests.sliceHero(t, "kagenori_tsurumine", player ? "fx_nav_reach_p" : "fx_nav_reach_n",
                    10, 15, 20);
            kage.goalSelector.removeAllGoals(g -> g instanceof com.factionclash.combat.HeroAbilityGoal
                    || g instanceof com.factionclash.combat.HeroThrowGoal);
            kage.targetSelector.removeAllGoals(g -> true);
            LivingEntity foe = player ? HeroAbilityGameTests.player(t, 19, 15, FOE, 200) : HeroAbilityGameTests.foe(t, 19, 15, 200);
            BlockPos top = h.absolutePos(new BlockPos(19, 4, 15));
            foe.moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 0F, 0F);
            float[] start = {0};
            int[] hitAt = {-1};
            t.onEachTick(() -> {
                if (h.getTick() < 3) {
                    start[0] = foe.getHealth();
                    return;
                }
                if (kage.getTarget() != foe) {
                    kage.setTarget(foe);
                }
                foe.setDeltaMovement(0, Math.min(0, foe.getDeltaMovement().y), 0);
                if (hitAt[0] < 0 && foe.getHealth() < start[0]) {
                    hitAt[0] = (int) h.getTick();
                    HeroLinks.Link run = nav(kage).running();
                    t.assertTrue(taken(kage, HeroLinks.Kind.JUMP_UP) >= 1 || run != null && run.kind() == HeroLinks.Kind.JUMP_UP,
                            "no jump-up: " + heroState(h, kage));
                    t.assertTrue(kage.pursuit().plans == 0 && kage.pursuit().pillars == 0 && kage.pursuit().breaks == 0,
                            "it planned its way: plans " + kage.pursuit().plans + ", pillars " + kage.pursuit().pillars);
                    t.assertTrue(hitAt[0] <= 3 + 80, "it took " + (hitAt[0] - 3) + " ticks");
                    FactionClash.LOGGER.info("GAMETEST {} hit its ledge enemy after {} ticks", kage.heroId(), hitAt[0] - 3);
                    t.succeed();
                }
            });
            h.runAtTickTime(295, t.guard(() -> t.fail("no blow: " + heroState(h, kage) + ", pursuit " + kage.pursuit().mode())));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    @GameTest(template = FIELD, timeoutTicks = 300, batch = "hero_nav_reach_npc")
    public static void heroReachesAnNpcOnALedgeAtOnce(GameTestHelper h) {
        ledgeEnemy(h, false);
    }

    @GameTest(template = FIELD, timeoutTicks = 300, batch = "hero_nav_reach_player")
    public static void heroReachesAPlayerOnALedgeAtOnce(GameTestHelper h) {
        ledgeEnemy(h, true);
    }

    // ------------------------------------------------------------------ workers unchanged (3.8)

    /**
     * {@code workersMoveUnchanged}, the navigation's half (static): a worker's navigation and path search are the faction
     * NPCs' own (no hero class), it has no trip helper, never waits for a link, carries a 3x3 ticket and drops what
     * vanilla drops; a hero's navigation is the hero's, and with mobility switched off it offers no link.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void workersNavigateUnchanged(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 40);
        try {
            FactionNpc worker = t.npc(6, 6, NpcRole.MINER);
            HeroEntity hero = HeroMobilityGameTests.sliceHero(t, "natsuho_komezaki", "fx_nav_static", 12, 12, 20);
            h.runAtTickTime(3, t.guard(() -> {
                t.assertTrue(worker.getNavigation().getClass() == NpcPathNavigation.class, "a worker's navigation "
                        + worker.getNavigation().getClass());
                t.assertTrue(worker.getNavigation().getNodeEvaluator().getClass() == NpcPathNavigation.GateEvaluator.class,
                        "a worker's path search " + worker.getNavigation().getNodeEvaluator().getClass());
                t.assertTrue(worker.travelHelper() == null && !worker.travelBusy() && worker.travelTicketRadius() == 1,
                        "a worker's trips have a helper");
                t.assertTrue(worker.getMaxFallDistance() == 3, "a worker's drop " + worker.getMaxFallDistance());
                t.assertTrue(hero.getNavigation() instanceof HeroGroundNavigation && hero.travelHelper() != null,
                        "a hero's navigation");
                t.assertTrue(hero.getMaxFallDistance() == HeroEntity.MAX_DROP, "no fall damage: drops " + hero.getMaxFallDistance());
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
