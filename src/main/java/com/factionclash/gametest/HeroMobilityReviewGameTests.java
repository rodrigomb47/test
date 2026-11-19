package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroEscape;
import com.factionclash.hero.HeroGroundNavigation;
import com.factionclash.hero.HeroMovement;
import com.factionclash.logic.hero.EscapeRules;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroLinks;
import com.factionclash.logic.hero.WallEntry;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcNavigator;
import com.factionclash.registry.ModEntities;
import com.factionclash.work.WorkGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.factionclash.gametest.HeroFightMobilityGameTests.moveBase;
import static com.factionclash.gametest.HeroMobilityGameTests.FIELD;
import static com.factionclash.gametest.HeroMobilityGameTests.at;
import static com.factionclash.gametest.HeroMobilityGameTests.column;
import static com.factionclash.gametest.HeroMobilityGameTests.floorY;
import static com.factionclash.gametest.HeroMobilityGameTests.sliceHero;
import static com.factionclash.gametest.HeroMobilityGameTests.still;
import static com.factionclash.gametest.HeroNavigationGameTests.fill;
import static com.factionclash.gametest.HeroNavigationGameTests.heroState;
import static com.factionclash.gametest.HeroNavigationGameTests.where;

/**
 * Heroes: the review of A10-A12 (the mobility engine, its navigation and its fights; HEROES_V2 15.2 "Review of
 * A10-A12"). What the review found and fixed, each with the test that failed before the fix:
 * <ul>
 * <li>{@code reloadMidFlightDiveOrClimbStaysPhysical}: a hero saved in flight, in a dive, clinging or climbing comes back
 * physical: the flyer comes down (no hover nobody steers), the diver does not hang in the air on vanilla's saved
 * {@code NoGravity}, the climbers climb down their face (no cling nobody ends, no fall), all unhurt.</li>
 * <li>{@code reloadMidEscapeRunsOnAndKeepsAway}: an escape saved while it runs goes on after the load, with its refuge
 * and whom it fled (the stay away), so a restart neither ends it nor sends it back at those it fled.</li>
 * <li>{@code raiderWithNoWayOutWaitsAtTheGate} and {@code raiderWithNoWayOutGoesInOnlyByTheGate}: a raiding hero whose
 * way in is the gate (no way out over the wall: an iron door with nothing inside to open it, an inside face it can
 * neither climb nor jump) never crosses the wall by its links: it goes to the gate and waits there while it is shut, and
 * goes in through it, never over the wall, while it stands open; {@code raiderAtAShutGateGoesRoundToAnOpenOne}: with
 * another of the base's gates open it goes round to that one instead of waiting at the nearest.</li>
 * <li>{@code airJumpCountsTheFallFromItsApex}: an acrobat I's air jump starts its fall again, so it lands the fall from
 * the air jump's apex, not that plus what it had fallen before the jump.</li>
 * <li>{@code aFailedLeapLeavesItsLinkDead}: a leap pushed off its landing leaves its link dead and the trip goes on (no
 * test had noticed a failed link that was never marked dead).</li>
 * <li>{@code phaseCrossedInTheAirLandsFirstForNpcsAndPlayers}: a flyer that crosses a phase threshold in an aerial window
 * lands first and plays the transition on the ground (before, the transition began in the air and the landing never
 * ran).</li>
 * <li>{@code sixteenHeroesNavigateWithinBudget}: sixteen heroes (leapers, climbers, acrobats and flyers) cross a ditch and
 * a ledge back and forth; the server time per hero tick (their whole tick and their trips' path searches) is logged and
 * held under a generous bound, and every one of them gets across.</li>
 * </ul>
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroMobilityReviewGameTests {
    private HeroMobilityReviewGameTests() {
    }

    private static final FactionType SIDE = FactionType.EMBER_LEGION, FOE = FactionType.GROVE_COVENANT;

    /**
     * A save and a load as the server does them at a restart or a chunk unload: the entity goes, a new one is read from
     * its tag and joins the level (the same UUID, so it is the campaign's hero).
     */
    static HeroEntity reload(GameTestBases.Handle t, HeroEntity hero) {
        CompoundTag tag = hero.saveWithoutId(new CompoundTag());
        ServerLevel level = t.helper.getLevel();
        hero.discard();
        HeroEntity copy = ModEntities.HERO.get().create(level);
        copy.load(tag);
        t.assertTrue(level.addFreshEntity(copy), "the loaded copy of " + hero.heroId() + " did not join the level");
        return t.track(copy);
    }

    // ------------------------------------------------------------------ save and load mid-action

    /**
     * {@code reloadMidFlightDiveOrClimbStaysPhysical}: Ambrose hovering 8 blocks up, a second Ambrose the tick he starts a
     * dive there, Tobimaru clinging 4 blocks up a wall and a second Tobimaru climbing it are each saved and loaded. The
     * flyer and the diver come down by a powered landing, unhurt from higher than their safe fall of 6 (neither hangs in
     * the air: before the review the flight came back as a hover that only its stamina ended, and the dive as an entity
     * with vanilla's NoGravity saved and no state to clear it),
     * the climbers climb down the face they were on (before, the cling came back as a cling that nothing ended, and a
     * climb as a fall); each stands on the floor within 6 s, with gravity and the health it had.
     */
    @GameTest(template = FIELD, timeoutTicks = 400, batch = "hero_rev_reload", skyAccess = true)
    public static void reloadMidFlightDiveOrClimbStaysPhysical(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 400);
        try {
            moveBase(h, t.base, 16, 34, 0);
            fill(h, 24, 2, 12, 24, 12, 28, Blocks.STONE.defaultBlockState()); // a wall 11 high along x 24
            String[] kinds = {"fly", "dive", "cling", "climb"};
            HeroEntity[] heroes = {
                    still(sliceHero(t, "ambrose_nettlewick", "fx_rev_fly", 6, 8, 20)),
                    still(sliceHero(t, "ambrose_nettlewick", "fx_rev_dive", 14, 8, 20)),
                    still(sliceHero(t, "tobimaru_usugumo", "fx_rev_cling", 23, 16, 20)),
                    still(sliceHero(t, "tobimaru_usugumo", "fx_rev_climb", 23, 24, 20))};
            double floor = floorY(h);
            float[] health = new float[4];
            int[] reloadedAt = {-1, -1, -1, -1}, downAt = {-1, -1, -1, -1};
            HeroMovement.State[] firstState = new HeroMovement.State[4];
            h.runAtTickTime(4, t.guard(() -> {
                for (int i = 0; i < 4; i++) {
                    health[i] = heroes[i].getHealth();
                }
                for (int i = 0; i < 2; i++) {
                    t.assertTrue(heroes[i].movement().takeOff() == null, kinds[i] + ": no take-off");
                }
                for (int i = 2; i < 4; i++) {
                    t.assertTrue(heroes[i].movement().climb(Direction.EAST) == null, kinds[i] + ": no climb");
                }
            }));
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 5) {
                    return;
                }
                boolean done = true;
                for (int i = 0; i < 4; i++) {
                    HeroEntity x = heroes[i];
                    HeroMovement m = x.movement();
                    if (reloadedAt[i] < 0) {
                        done = false;
                        if (i < 2 && m.state() == HeroMovement.State.FLY && x.getY() < floor + 8) {
                            m.flyTo(new Vec3(x.getX(), floor + 8.5, x.getZ()));
                            continue;
                        }
                        if (x.getY() < floor + (i < 2 ? 8 : 4)) {
                            continue;
                        }
                        if (i == 1) {
                            t.assertTrue(m.dive(new Vec3(x.getX() + 8, floor, x.getZ())) == null, "no dive");
                        } else if (i == 2) {
                            t.assertTrue(m.cling() == null, "no cling");
                        }
                        String before = m.state().name();
                        HeroEntity copy = still(reload(t, x));
                        heroes[i] = copy;
                        reloadedAt[i] = now;
                        firstState[i] = copy.movement().state();
                        FactionClash.LOGGER.info("GAMETEST reload {}: saved {} at {} blocks up, loaded as {}", kinds[i], before,
                                String.format(java.util.Locale.ROOT, "%.2f", copy.getY() - floor), firstState[i]);
                        continue;
                    }
                    if (downAt[i] < 0) {
                        done = false;
                        if (x.onGround() && x.getY() < floor + 0.5 && !m.controls()) {
                            downAt[i] = now;
                            t.assertTrue(!x.isNoGravity(), kinds[i] + ": standing with no gravity");
                            t.assertTrue(x.getHealth() == health[i], kinds[i] + " was hurt: " + (health[i] - x.getHealth()));
                            if (i >= 2) {
                                t.assertTrue(firstState[i] == HeroMovement.State.CLIMB_DOWN, kinds[i] + ": loaded as "
                                        + firstState[i] + ", not climbing down its face");
                            } else {
                                t.assertTrue(firstState[i] == HeroMovement.State.LAND, kinds[i] + ": loaded as " + firstState[i]
                                        + ", not landing");
                            }
                        } else {
                            t.assertTrue(now - reloadedAt[i] <= 120, kinds[i] + " is still up 6 s after the load: "
                                    + where(h, x) + " " + m.state() + (x.isNoGravity() ? ", no gravity" : ""));
                        }
                    }
                }
                if (done) {
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code reloadMidEscapeRunsOnAndKeepsAway}: Natsuho (an escaping kind) breaks off from an NPC soldier; saved and
     * loaded 10 ticks into her escape, she is still escaping (the flag the client reads, its refuge or the wild point,
     * whom she fled) and runs on: before the review a load ended the escape and forgot whom she fled.
     */
    @GameTest(template = FIELD, timeoutTicks = 200, batch = "hero_rev_reload_escape")
    public static void reloadMidEscapeRunsOnAndKeepsAway(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        try {
            moveBase(h, t.base, 16, 34, 0);
            HeroEntity cat = sliceHero(t, "natsuho_komezaki", "fx_rev_escape", 10, 16, 20);
            cat.targetSelector.removeAllGoals(g -> true);
            FactionNpc foe = HeroAbilityGameTests.foe(t, 13, 16, 200);
            HeroEntity[] now = {cat};
            Vec3[] origin = new Vec3[1], dest = new Vec3[1], loadedAt = new Vec3[1];
            h.runAtTickTime(5, t.guard(() -> {
                cat.setTarget(foe);
                t.assertTrue(cat.combat().boss().escape().start(EscapeRules.Trigger.LOW_HEALTH, true), "no escape");
                origin[0] = cat.combat().boss().escape().origin();
            }));
            h.runAtTickTime(15, t.guard(() -> {
                HeroEscape before = cat.combat().boss().escape();
                t.assertTrue(before.fleeing(), "not fleeing before the save");
                dest[0] = before.destination();
                HeroEntity copy = reload(t, cat);
                copy.goalSelector.removeAllGoals(g -> g instanceof WorkGoal);
                copy.targetSelector.removeAllGoals(g -> true);
                now[0] = copy;
                loadedAt[0] = copy.position();
                HeroEscape e = copy.combat().boss().escape();
                t.assertTrue(e.running() && e.fleeing(), "the escape ended with the load");
                t.assertTrue(copy.stateFlag(HeroActions.STATE_ESCAPING), "the client's escaping flag is off after the load");
                t.assertTrue(e.keepsAwayFrom(foe), "it forgot whom it fled");
                t.assertTrue(e.origin().distanceTo(new Vec3(origin[0].x, e.origin().y, origin[0].z)) < 1e-6,
                        "the fight it left moved: " + e.origin() + " for " + origin[0]);
                t.assertTrue(e.destination().distanceTo(dest[0]) < 1.5, "it runs elsewhere: " + e.destination() + " for " + dest[0]);
            }));
            h.runAtTickTime(75, t.guard(() -> {
                HeroEntity copy = now[0];
                t.assertTrue(copy.combat().boss().escape().running(), "the escape ended");
                double moved = copy.position().distanceTo(loadedAt[0]);
                t.assertTrue(moved >= 2, "it did not run on after the load (" + String.format(java.util.Locale.ROOT, "%.2f", moved)
                        + " blocks)");
                t.assertTrue(copy.getTarget() != foe, "it took the soldier it fled as its target");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ a raid's way in with no way out

    /**
     * {@code raiderWithNoWayOutWaitsAtTheGate}: a hostile yard 5 high outside and 6 inside with an iron door and nothing
     * inside to open it; Natsuho at level 20 (her climb reaches the outside top, she drops anywhere unhurt, but she can
     * neither climb nor jump the inside face, nor open the door from inside) raids it. Her way in is the gate, and her
     * path search takes no link over that wall: she walks round to the outside of the gate and waits there, never on or
     * inside the wall (before the review her links climbed her over it, into a yard she could not leave).
     */
    @GameTest(template = FIELD, timeoutTicks = 520, batch = "hero_rev_raid_noway")
    public static void raiderWithNoWayOutWaitsAtTheGate(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 11, 520);
        GameTestBases.Handle shut = GameTestBases.register(h, FOE, 4, 11, 520);
        try {
            moveBase(h, t.base, 3, 3, 1);
            HeroRaidGameTests.walledYard(h, shut.base, 20, 20, 4, Blocks.IRON_DOOR, 1);
            HeroEntity cat = sliceHero(t, "natsuho_komezaki", "fx_rev_raid_shut", 20, 6, 20);
            cat.targetSelector.removeAllGoals(g -> true);
            HeroRaidGameTests.raid(t, shut.base, cat);
            BlockPos gate = shut.base.point(BaseState.GATE_WEST);
            BlockPos outside = gate.offset(-2, 0, 0);
            int[] atGate = {-1};
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 3) {
                    return;
                }
                t.assertTrue(!BaseZones.insideWalls(shut.base, cat.blockPosition(), 0), "she crossed the wall: "
                        + where(h, cat) + " " + cat.raid().describe() + ", " + heroState(h, cat));
                if (atGate[0] < 0 && NpcNavigator.within(cat, outside, 2.5)) {
                    atGate[0] = now;
                    t.assertTrue(cat.raid().entry() != null && cat.raid().entry().way() == WallEntry.Way.GATE,
                            "in by " + cat.raid().entry());
                }
                if (now >= 500) {
                    HeroGroundNavigation n = (HeroGroundNavigation) cat.getNavigation();
                    t.assertTrue(atGate[0] > 0, "she never got to the gate: " + heroState(h, cat) + ", " + cat.raid().describe());
                    t.assertTrue(NpcNavigator.within(cat, outside, 3.5), "she left the gate: " + heroState(h, cat));
                    FactionClash.LOGGER.info("GAMETEST raid with no way out: at the gate at {}, {} links over the wall refused,"
                            + " climbs taken {}", atGate[0] - 3, n.evaluator().closedRefused,
                            n.taken.getOrDefault(HeroLinks.Kind.CLIMB, 0));
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            shut.cleanup();
            throw e;
        }
    }

    /**
     * {@code raiderWithNoWayOutGoesInOnlyByTheGate}: the same yard with its iron door standing open, and Natsuho coming
     * from the east, the far side from the gate. Her way in is still the gate (an iron door with nothing inside to open
     * it is no way out once it shuts), so while it stands open she walks round to it and in through it: no link takes
     * her onto the wall or over it, the shorter way (before the review her path climbed the east wall and dropped into the
     * yard).
     */
    @GameTest(template = FIELD, timeoutTicks = 520, batch = "hero_rev_raid_open")
    public static void raiderWithNoWayOutGoesInOnlyByTheGate(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 11, 520);
        GameTestBases.Handle shut = GameTestBases.register(h, FOE, 4, 11, 520);
        try {
            moveBase(h, t.base, 3, 3, 1);
            HeroRaidGameTests.walledYard(h, shut.base, 20, 20, 4, Blocks.IRON_DOOR, 1);
            for (int y = 2; y <= 3; y++) {
                BlockPos d = new BlockPos(16, y, 20);
                h.setBlock(d, h.getBlockState(d).setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true));
            }
            HeroEntity cat = sliceHero(t, "natsuho_komezaki", "fx_rev_raid_open", 29, 20, 20);
            cat.targetSelector.removeAllGoals(g -> true);
            HeroRaidGameTests.raid(t, shut.base, cat);
            BlockPos gate = shut.base.point(BaseState.GATE_WEST);
            double floor = floorY(h);
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 3) {
                    return;
                }
                BlockPos p = cat.blockPosition();
                boolean onRing = BaseZones.insideWalls(shut.base, p, 0) && !BaseZones.insideWalls(shut.base, p, -1);
                t.assertTrue(!onRing || p.getX() == gate.getX() && p.getZ() == gate.getZ(), "she is on the wall, not in the"
                        + " gateway: " + where(h, cat) + " " + cat.raid().describe() + ", " + heroState(h, cat));
                t.assertTrue(!(BaseZones.insideWalls(shut.base, p, 0) && cat.getY() > floor + 1.5), "she is up on the wall: "
                        + where(h, cat) + ", " + heroState(h, cat));
                if (BaseZones.insideWalls(shut.base, p, -1) && cat.onGround()) {
                    t.assertTrue(cat.raid().entry() != null && cat.raid().entry().way() == WallEntry.Way.GATE,
                            "in by " + cat.raid().entry());
                    HeroGroundNavigation n = (HeroGroundNavigation) cat.getNavigation();
                    FactionClash.LOGGER.info("GAMETEST raid through an open gate with no way out: inside at {}, {} links over the"
                            + " wall refused, links taken {}", now - 3, n.evaluator().closedRefused, n.taken);
                    t.succeed();
                }
            });
            h.runAtTickTime(515, t.guard(() -> t.fail("she never got in: " + heroState(h, cat) + ", " + cat.raid().describe())));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            shut.cleanup();
            throw e;
        }
    }

    /**
     * {@code raiderAtAShutGateGoesRoundToAnOpenOne}: the same yard (not sunk) with its west gate an iron door nothing
     * opens and an oak door on the east wall, and Yugen (a runner: the gate with the soldiers) coming from the west, by
     * the shut gate. The way in is the gate; since one of the base's gates opens, he goes round to it and in, rather
     * than waiting at the nearest gate for good (the review's first wait looked only at the nearest gate).
     */
    @GameTest(template = FIELD, timeoutTicks = 620, batch = "hero_rev_raid_round")
    public static void raiderAtAShutGateGoesRoundToAnOpenOne(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 11, 620);
        GameTestBases.Handle yard = GameTestBases.register(h, FOE, 4, 11, 620);
        try {
            moveBase(h, t.base, 3, 3, 1);
            HeroRaidGameTests.walledYard(h, yard.base, 20, 20, 4, Blocks.IRON_DOOR, 0);
            BlockPos east = new BlockPos(24, 2, 20);
            h.setBlock(east, Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING,
                    Direction.WEST).setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
            h.setBlock(east.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING,
                    Direction.WEST).setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            yard.base.points.put(BaseState.GATE_EAST, h.absolutePos(east));
            HeroEntity fox = sliceHero(t, "yugen_kagaribi", "fx_rev_raid_round", 9, 20, 20);
            fox.targetSelector.removeAllGoals(g -> true);
            HeroRaidGameTests.raid(t, yard.base, fox);
            double floor = floorY(h);
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 3) {
                    return;
                }
                t.assertTrue(!(BaseZones.insideWalls(yard.base, fox.blockPosition(), 0) && fox.getY() > floor + 1.5),
                        "he is up on the wall: " + where(h, fox) + ", " + heroState(h, fox));
                if (BaseZones.insideWalls(yard.base, fox.blockPosition(), -1) && fox.onGround()) {
                    t.assertTrue(fox.raid().entry() != null && !fox.raid().entry().overTheWall(), "in by " + fox.raid().entry());
                    t.assertTrue(fox.getX() > h.absolutePos(new BlockPos(20, 2, 20)).getX(), "he came in on the west side, by the"
                            + " shut gate: " + where(h, fox));
                    FactionClash.LOGGER.info("GAMETEST raid round to an open gate: inside at {} ({})", now - 3, where(h, fox));
                    t.succeed();
                }
            });
            h.runAtTickTime(615, t.guard(() -> t.fail("he never got in: " + heroState(h, fox) + ", " + fox.raid().describe())));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            yard.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the fall after an air jump

    /**
     * {@code airJumpCountsTheFallFromItsApex}: an acrobat I at level 20 (a safe fall of 8 blocks, one air jump) jumps off
     * a 10-block tower, air-jumps 4 blocks under its top and lands from the air jump's apex, about 7 blocks: unhurt.
     * Before the review the 5 blocks fallen before the air jump still counted, about 12 blocks in all, and the landing
     * hurt her.
     */
    @GameTest(template = FIELD, timeoutTicks = 160, batch = "hero_rev_airjump_fall")
    public static void airJumpCountsTheFallFromItsApex(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            moveBase(h, t.base, 16, 34, 0);
            column(h, 8, 20, 2, 11);
            HeroEntity acro = still(HeroNavigationGameTests.withMobility(t, "natsuho_komezaki", "fx_rev_acro1",
                    "{\"primary\": {\"trait\": \"acrobat\", \"tier\": 1}}", 4, 4, 20));
            Vec3 top = at(h, 8.5, 12, 20.5);
            acro.moveTo(top.x, top.y, top.z, 0F, 0F);
            float[] hp = {0};
            boolean[] started = {false}, jumped = {false};
            double[] fallenBefore = {0};
            h.runAtTickTime(4, t.guard(() -> {
                double safe = acro.movement().profile().safeFall();
                t.assertTrue(safe > 7 && safe < 10, "an acrobat I's safe fall at level 20: " + safe);
                hp[0] = acro.getHealth();
                t.assertTrue(acro.movement().jump() == null, "no jump from the tower");
                acro.setDeltaMovement(0.25, acro.getDeltaMovement().y, 0);
                started[0] = true;
            }));
            t.onEachTick(() -> {
                if (!started[0] || h.getTick() < 6) {
                    return;
                }
                if (!jumped[0] && !acro.onGround() && acro.getY() < top.y - 4) {
                    fallenBefore[0] = acro.fallDistance;
                    t.assertTrue(acro.movement().airJump() == null, "no air jump");
                    jumped[0] = true;
                }
                if (jumped[0] && acro.onGround() && acro.getY() < top.y - 8) {
                    FactionClash.LOGGER.info("GAMETEST air jump fall: {} blocks fallen before the air jump, health {} -> {}",
                            String.format(java.util.Locale.ROOT, "%.2f", fallenBefore[0]), hp[0], acro.getHealth());
                    t.assertTrue(fallenBefore[0] > 3.5, "too little fallen before the air jump: " + fallenBefore[0]);
                    t.assertTrue(acro.getHealth() == hp[0], "the landing hurt her: " + (hp[0] - acro.getHealth()));
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ a failed link (3.3)

    /**
     * {@code aFailedLeapLeavesItsLinkDead}: Kagenori at level 10 leaps a ditch 4 wide on his trip; a push in the air (no
     * blow, no hurt) carries him 2 blocks off his landing cell. That link failed with nobody to blame: it is dead (left
     * out of his searches for 10 minutes, 3.3) and the trip goes on from where he landed to its end. Before the review
     * no test noticed when a failed link was never marked dead.
     */
    @GameTest(template = FIELD, timeoutTicks = 600, batch = "hero_rev_dead_link")
    public static void aFailedLeapLeavesItsLinkDead(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 600);
        try {
            moveBase(h, t.base, 16, 34, 0);
            fill(h, 13, 0, 6, 16, 1, 24, Blocks.AIR.defaultBlockState());
            HeroEntity kage = HeroNavigationGameTests.traveller(t, "kagenori_tsurumine", "fx_rev_dead", 7, 15, 10);
            HeroNavigationGameTests.Trip trip = new HeroNavigationGameTests.Trip(kage, h.absolutePos(new BlockPos(22, 2, 15)));
            boolean[] pushed = {false};
            int[] failedAt = {-1};
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 3) {
                    return;
                }
                trip.step(h);
                HeroGroundNavigation n = (HeroGroundNavigation) kage.getNavigation();
                if (!pushed[0] && kage.movement().state() == HeroMovement.State.LEAP && kage.movement().airborneForPoise()) {
                    pushed[0] = true;
                    kage.setDeltaMovement(kage.getDeltaMovement().add(0, 0, 0.3));
                }
                if (pushed[0] && failedAt[0] < 0 && n.linksFailed > 0) {
                    failedAt[0] = now;
                    t.assertTrue(n.deadCount() >= 1, "the failed leap's link is not dead: " + heroState(h, kage));
                }
                if (trip.arrivedAt >= 0) {
                    t.assertTrue(pushed[0] && failedAt[0] > 0, "the leap never failed: " + heroState(h, kage));
                    t.assertTrue(n.deadCount() >= 1, "no dead link at the end: " + heroState(h, kage));
                    t.assertTrue(trip.failed == 0, "the trip was given up: " + heroState(h, kage));
                    t.succeed();
                }
            });
            h.runAtTickTime(595, t.guard(() -> t.fail("not there: " + heroState(h, kage))));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ a phase threshold crossed in the air (7.4)

    /**
     * {@code phaseCrossedInTheAirLandsFirstForNpcsAndPlayers}: two copies of Ambrose (phase 2 at 60%, a 1.2 s
     * transition) in an aerial window, one over an NPC soldier and one over a mock player. Up in the air each drops to
     * 58%: it lands at once (the fight mobility's phase landing) and the transition plays on the ground, never in the
     * air, the same for both. Before the review the transition began in the air on the tick the threshold was crossed,
     * so the landing never ran.
     */
    @GameTest(template = FIELD, timeoutTicks = 300, batch = "hero_rev_phase_air", skyAccess = true)
    public static void phaseCrossedInTheAirLandsFirstForNpcsAndPlayers(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            HeroEntity a = sliceHero(t, "ambrose_nettlewick", "fx_rev_phase_npc", 6, 6, 20);
            HeroEntity b = sliceHero(t, "ambrose_nettlewick", "fx_rev_phase_pl", 6, 24, 20);
            for (HeroEntity x : new HeroEntity[]{a, b}) {
                x.goalSelector.removeAllGoals(g -> g instanceof WorkGoal || g instanceof com.factionclash.combat.HeroAbilityGoal
                        || g instanceof com.factionclash.combat.HeroThrowGoal);
                x.targetSelector.removeAllGoals(g -> true);
                HeroAbilityGameTests.noRegen(x);
            }
            FactionNpc soldier = HeroAbilityGameTests.foe(t, 11, 6, 200);
            net.minecraft.server.level.ServerPlayer player = HeroAbilityGameTests.player(t, 11, 24, FOE, 200);
            soldier.setNoAi(true);
            double floor = floorY(h);
            int[] crossed = {-1, -1}, landing = {-1, -1}, ground = {-1, -1}, began = {-1, -1};
            h.runAtTickTime(3, t.guard(() -> {
                a.setTarget(soldier);
                b.setTarget(player);
            }));
            h.runAtTickTime(6, t.guard(() -> {
                String na = a.combat().air().aerialTakeOff(), nb = b.combat().air().aerialTakeOff();
                t.assertTrue(na == null && nb == null, "no aerial window: " + na + " / " + nb);
            }));
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 8) {
                    return;
                }
                HeroEntity[] both = {a, b};
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = both[i];
                    x.setTarget(i == 0 ? soldier : player);
                    HeroMovement.State st = x.movement().state();
                    if (crossed[i] < 0) {
                        if (st == HeroMovement.State.FLY && x.getY() - floor > 3) {
                            t.assertTrue(x.combat().boss().transitionCount() == 0, x.heroId() + " played a transition already");
                            x.setHealth(x.getMaxHealth() * 0.58F);
                            crossed[i] = now;
                        }
                        continue;
                    }
                    if (landing[i] < 0 && st == HeroMovement.State.LAND) {
                        landing[i] = now;
                    }
                    // (down: on the floor and out of its flight; vanilla's onGround can lag a tick after a powered landing)
                    boolean down = x.getY() - floor < 0.05 && !x.movement().flying();
                    if (ground[i] < 0 && down) {
                        ground[i] = now;
                    }
                    if (began[i] < 0 && x.combat().boss().transitionCount() > 0) {
                        began[i] = now;
                        t.assertTrue(down, x.heroId() + "'s transition began in the air: "
                                + where(h, x) + " " + st + ", " + x.combat().air().describe());
                    }
                }
                if (began[0] > 0 && began[1] > 0) {
                    for (int i = 0; i < 2; i++) {
                        HeroEntity x = both[i];
                        t.assertTrue(landing[i] >= 0 && landing[i] - crossed[i] <= 2, x.heroId() + " landed "
                                + (landing[i] - crossed[i]) + " ticks after the threshold");
                        t.assertTrue(x.combat().air().phaseLandings == 1, x.heroId() + ": phase landings "
                                + x.combat().air().phaseLandings);
                        t.assertTrue(began[i] - ground[i] <= 3, x.heroId() + "'s transition began " + (began[i] - ground[i])
                                + " ticks after it was down");
                        t.assertTrue(x.combat().boss().phase() == 2, x.heroId() + " in phase " + x.combat().boss().phase());
                    }
                    t.assertTrue(began[0] - crossed[0] == began[1] - crossed[1], "the NPC's flyer began its transition "
                            + (began[0] - crossed[0]) + " ticks after the threshold, the player's " + (began[1] - crossed[1]));
                    FactionClash.LOGGER.info("GAMETEST phase in the air: landing {} and {} ticks after the threshold, the"
                            + " transition {} and {} ticks after", landing[0] - crossed[0], landing[1] - crossed[1],
                            began[0] - crossed[0], began[1] - crossed[1]);
                    t.succeed();
                }
            });
            h.runAtTickTime(295, t.guard(() -> t.fail("crossed " + crossed[0] + "/" + crossed[1] + ", landing " + landing[0] + "/"
                    + landing[1] + ", down " + ground[0] + "/" + ground[1] + ", transition " + began[0] + "/" + began[1] + "; "
                    + a.movement().state() + " " + a.combat().air().describe() + " / " + b.movement().state() + " "
                    + b.combat().air().describe())));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ sixteen heroes navigating (3.9)

    /** Server time per hero tick allowed by this test (its whole tick and its trips' path searches): generous. */
    static final double NAV_TICK_MS_LIMIT = 1.0;

    /**
     * {@code sixteenHeroesNavigateWithinBudget}: sixteen heroes at level 20, four each of Kagenori (leaper II), Tobimaru
     * (acrobat III, climber II), Natsuho (acrobat III, climber I) and Ambrose (flyer II), each in its own lane across the
     * field, go back and forth over a ditch 3 wide and 2 deep and a ledge 2 high (gap leaps, jump-ups, climbs, drops and
     * flights) for 40 s. The server time of their ticks and of their trips' calls (the path searches with their links)
     * is logged per hero tick and held under 1 ms; every hero gets across at least once.
     */
    @GameTest(template = FIELD, timeoutTicks = 1000, batch = "hero_rev_sixteen_nav", skyAccess = true)
    public static void sixteenHeroesNavigateWithinBudget(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 1000);
        try {
            moveBase(h, t.base, 16, 34, 0);
            fill(h, 9, 0, 0, 11, 1, 31, Blocks.AIR.defaultBlockState()); // a ditch 3 wide, 2 deep
            fill(h, 18, 2, 0, 19, 3, 31, Blocks.STONE.defaultBlockState()); // a ledge 2 high, 2 wide
            String[] kits = {"kagenori_tsurumine", "tobimaru_usugumo", "natsuho_komezaki", "ambrose_nettlewick"};
            List<HeroEntity> heroes = new ArrayList<>();
            List<HeroNavigationGameTests.Trip> trips = new ArrayList<>();
            int[] crossings = new int[16];
            for (int i = 0; i < 16; i++) {
                int z = 1 + 2 * i;
                HeroEntity x = HeroNavigationGameTests.traveller(t, kits[i % 4], "fx_rev16_" + i, 2, z, 20);
                heroes.add(x);
                trips.add(new HeroNavigationGameTests.Trip(x, h.absolutePos(new BlockPos(29, 2, z))));
            }
            long[] travelNanos = {0};
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 3) {
                    return;
                }
                if (now == 20) {
                    HeroEntity.resetTickStats();
                    travelNanos[0] = 0;
                }
                long start = System.nanoTime();
                for (int i = 0; i < 16; i++) {
                    HeroNavigationGameTests.Trip tr = trips.get(i);
                    if (tr.step(h)) {
                        crossings[i]++;
                        int z = 1 + 2 * i;
                        boolean far = tr.dest.getX() - h.absolutePos(BlockPos.ZERO).getX() > 15;
                        trips.set(i, new HeroNavigationGameTests.Trip(heroes.get(i),
                                h.absolutePos(new BlockPos(far ? 2 : 29, 2, z))));
                    }
                }
                if (now >= 20) {
                    travelNanos[0] += System.nanoTime() - start;
                }
                if (now == 820) {
                    long[] s = HeroEntity.tickStats();
                    double tick = s[2] == 0 ? 0 : s[0] / 1e6 / s[2], trip = s[2] == 0 ? 0 : travelNanos[0] / 1e6 / s[2];
                    int links = 0, failed = 0, flights = 0, recoveries = 0, across = 0;
                    for (int i = 0; i < 16; i++) {
                        HeroEntity x = heroes.get(i);
                        HeroGroundNavigation n = (HeroGroundNavigation) x.getNavigation();
                        links += n.linksTaken;
                        failed += n.linksFailed;
                        flights += x.travel().flights;
                        recoveries += x.navigator().recoveries;
                        across += crossings[i] > 0 ? 1 : 0;
                    }
                    FactionClash.LOGGER.info("GAMETEST sixteen heroes navigating, {} hero ticks: {} ms a hero tick in its own tick,"
                                    + " {} ms in its trips' calls, {} ms in all; crossings {}, links {} (failed {}), flights {},"
                                    + " stuck recoveries {}", s[2], String.format(java.util.Locale.ROOT, "%.4f", tick),
                            String.format(java.util.Locale.ROOT, "%.4f", trip), String.format(java.util.Locale.ROOT, "%.4f", tick + trip),
                            java.util.Arrays.toString(crossings), links, failed, flights, recoveries);
                    t.assertTrue(s[2] >= 16 * 780, "too few hero ticks timed: " + s[2]);
                    for (int i = 0; i < 16; i++) {
                        t.assertTrue(crossings[i] > 0, "a hero never got across: " + heroState(h, heroes.get(i)));
                    }
                    t.assertTrue(links > 0 && flights > 0, "no links or no flights: " + links + ", " + flights);
                    t.assertTrue(tick + trip < NAV_TICK_MS_LIMIT, "a hero tick took " + (tick + trip) + " ms on average");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
