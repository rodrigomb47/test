package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroFightMobility;
import com.factionclash.hero.HeroMovement;
import com.factionclash.logic.hero.BossRules;
import com.factionclash.logic.hero.HeroLinks;
import com.factionclash.npc.NpcRole;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.factionclash.gametest.HeroMobilityGameTests.FIELD;
import static com.factionclash.gametest.HeroMobilityGameTests.oneShot;
import static com.factionclash.gametest.HeroMobilityGameTests.sliceHero;
import static com.factionclash.gametest.HeroMobilityGameTests.still;
import static com.factionclash.gametest.HeroNavigationGameTests.fill;
import static com.factionclash.gametest.HeroNavigationGameTests.heroState;
import static com.factionclash.gametest.HeroNavigationGameTests.rel;
import static com.factionclash.gametest.HeroNavigationGameTests.taken;
import static com.factionclash.gametest.HeroNavigationGameTests.where;

/**
 * Heroes: the faults the mobility live check found (2026-11-19, `HEROES_V2.md` 15.2 "Mobility in game"), each with
 * the hero and the shape of ground it happened on. A chimney link in a 1-wide shaft never touched a wall, so it only
 * air-jumped and fell back, and the trip ended in DigOut and the stuck hop; the mantle at the top of a climb never
 * happened (vanilla's horizontal collision stays on until the feet clear the top); a glide opened for a tick on the lip
 * of a ditch (the drop was read under the hero's middle only); a flyer in an aerial phase stood in a pit out of every
 * enemy's reach while its reach clock fired again and again (its come-back walked, which it cannot). The pit test runs
 * against an NPC archer and a mock player with the same numbers. Each test has a batch of its own.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroMobilityLiveGameTests {
    private HeroMobilityLiveGameTests() {
    }

    private static final FactionType SIDE = FactionType.EMBER_LEGION, FOE = FactionType.GROVE_COVENANT;

    /**
     * {@code chimneyLinkKicksOutOfAOneWideShaft}: Natsuho at level 1 (acrobat III, climber I: her 2.9-block jump and her
     * 3-block climb reach nowhere near the top) stands at the bottom of a closed 1x2 shaft 8 deep and goes to a cell on
     * the platform it is cut in. The only way is the chimney link: she leans to a side of the shaft, kicks off it, then
     * off the other, up and out, with no failed link, no stuck recovery and no block of the shaft broken (DigOut).
     */
    @GameTest(template = FIELD, timeoutTicks = 400, batch = "hero_live_chimney", skyAccess = true)
    public static void chimneyLinkKicksOutOfAOneWideShaft(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 400);
        try {
            fill(h, 10, 2, 10, 16, 9, 16, Blocks.STONE);
            fill(h, 13, 2, 13, 13, 9, 14, Blocks.AIR);
            HeroEntity nat = HeroNavigationGameTests.traveller(t, "natsuho_komezaki", "fx_live_chimney", 13, 13, 1);
            HeroNavigationGameTests.Trip trip = new HeroNavigationGameTests.Trip(nat, h.absolutePos(new BlockPos(11, 10, 11)));
            int[] kicks = {0}, toggle = {-1};
            t.onEachTick(() -> {
                if (h.getTick() < 3) {
                    return;
                }
                trip.step(h);
                // a wall kick names the cling's special one-shot on DATA_LOCO (MovementRules: cling special = wall kick)
                // (each new one-shot flips DATA_LOCO's sequence toggle, so two kicks in a row both count)
                int tg = com.factionclash.logic.hero.HeroActions.locoToggle(nat.loco()) ? 1 : 0;
                boolean kick = "move.wall_kick".equals(oneShot(nat));
                if (kick && tg != toggle[0]) {
                    kicks[0]++;
                }
                toggle[0] = kick ? tg : -1;
                if (trip.arrivedAt >= 0) {
                    for (int y = 2; y <= 9; y++) {
                        for (int[] c : new int[][]{{12, 13}, {14, 13}, {12, 14}, {14, 14}, {13, 12}, {13, 15}}) {
                            t.assertTrue(h.getBlockState(new BlockPos(c[0], y, c[1])).is(Blocks.STONE),
                                    "the shaft was dug at (" + c[0] + ", " + y + ", " + c[1] + "): " + heroState(h, nat));
                        }
                    }
                    t.assertTrue(taken(nat, HeroLinks.Kind.CHIMNEY) >= 1, "no chimney link: " + heroState(h, nat));
                    t.assertTrue(HeroNavigationGameTests.nav(nat).linksFailed == 0, "a link failed: " + heroState(h, nat));
                    t.assertTrue(nat.navigator().recoveries == 0, "a stuck recovery: " + heroState(h, nat));
                    t.assertTrue(kicks[0] >= 2, "wall kicks: " + kicks[0]);
                    FactionClash.LOGGER.info("GAMETEST chimney: out after {} ticks with {} wall kicks", trip.arrivedAt - 3, kicks[0]);
                    t.succeed();
                }
            });
            h.runAtTickTime(395, t.guard(() -> t.fail("not out: " + heroState(h, nat) + ", wall kicks " + kicks[0])));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code climbEndsInAMantleForAcrobatsAndClimbersIII}: Tobimaru at level 1 (acrobat III: the mantle) climbs a 4-high
     * wall and, once the face ends at his head, mantles onto its top: the state and the {@code move.mantle} one-shot of
     * {@code DATA_LOCO}. A climber II with no acrobat (no mantle) beside him climbs the same wall and steps over it as
     * before, with no mantle. Both stand on the top, unhurt.
     */
    @GameTest(template = FIELD, timeoutTicks = 200, batch = "hero_live_mantle")
    public static void climbEndsInAMantleForAcrobatsAndClimbersIII(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        try {
            fill(h, 6, 2, 16, 24, 5, 16, Blocks.STONE);
            HeroEntity tobi = still(sliceHero(t, "tobimaru_usugumo", "fx_live_mantle", 10, 17, 1));
            HeroEntity plain = still(HeroNavigationGameTests.withMobility(t, "kagenori_tsurumine", "fx_live_nomantle",
                    "{\"primary\": {\"trait\": \"climber\", \"tier\": 2}}", 20, 17, 1));
            HeroEntity[] both = {tobi, plain};
            boolean[] mantleState = {false, false}, mantleClip = {false, false}, started = {false};
            float[] hp = new float[2];
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 3) {
                    return;
                }
                if (!started[0]) {
                    started[0] = true;
                    for (int i = 0; i < 2; i++) {
                        hp[i] = both[i].getHealth();
                        String no = both[i].movement().climb(Direction.NORTH);
                        t.assertTrue(no == null, both[i].heroId() + " does not climb: " + no);
                    }
                    return;
                }
                for (int i = 0; i < 2; i++) {
                    mantleState[i] |= both[i].movement().state() == HeroMovement.State.MANTLE;
                    mantleClip[i] |= "move.mantle".equals(oneShot(both[i]));
                }
                boolean done = true;
                for (HeroEntity x : both) {
                    done &= x.onGround() && x.movement().state() == HeroMovement.State.NONE && rel(h, x)[1] > 5.9;
                }
                if (done) {
                    t.assertTrue(mantleState[0] && mantleClip[0], "Tobimaru did not mantle (state " + mantleState[0] + ", clip "
                            + mantleClip[0] + ")");
                    t.assertTrue(!mantleState[1] && !mantleClip[1], "a climber II with no mantle mantled");
                    for (int i = 0; i < 2; i++) {
                        t.assertTrue(Math.abs(rel(h, both[i])[1] - 6) < 0.05, both[i].heroId() + " is not on the top: "
                                + where(h, both[i]));
                        t.assertTrue(both[i].getHealth() >= hp[i], both[i].heroId() + " was hurt");
                    }
                    FactionClash.LOGGER.info("GAMETEST mantle: both on the top at tick {}", now);
                    t.succeed();
                }
            });
            h.runAtTickTime(195, t.guard(() -> t.fail("not on the top: Tobimaru " + where(h, tobi) + " " + tobi.movement().state()
                    + " mantle " + mantleState[0] + "/" + mantleClip[0] + ", climber II " + where(h, plain) + " "
                    + plain.movement().state())));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code glideOpensOnlyOverADropUnderTheWholeFootprint}: Ambrose (a flyer: he glides) drops 2 blocks onto a
     * platform 7 high, his middle over a 1-wide ditch cut in it and his box over its lip: he lands on the lip and never
     * opens a glide. A second copy drops into a 3-wide pit beside it (9 blocks under him) and opens his glide as before.
     */
    @GameTest(template = FIELD, timeoutTicks = 160, batch = "hero_live_glide_lip", skyAccess = true)
    public static void glideOpensOnlyOverADropUnderTheWholeFootprint(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            fill(h, 4, 2, 4, 18, 8, 12, Blocks.STONE);
            fill(h, 9, 2, 4, 9, 8, 12, Blocks.AIR);
            fill(h, 13, 2, 4, 15, 8, 12, Blocks.AIR);
            HeroEntity lip = still(sliceHero(t, "ambrose_nettlewick", "fx_live_glide_lip", 6, 15, 1));
            HeroEntity pit = still(sliceHero(t, "ambrose_nettlewick", "fx_live_glide_pit", 16, 15, 1));
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            boolean[] glided = {false, false}, dropped = {false};
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 3) {
                    return;
                }
                if (!dropped[0]) {
                    dropped[0] = true;
                    // his middle at x 9.2 over the ditch (x 9), his box (0.6 wide) from 8.9: over the platform's x 8
                    lip.moveTo(o.getX() + 9.2, o.getY() + 11, o.getZ() + 8.5, 0F, 0F);
                    pit.moveTo(o.getX() + 14.5, o.getY() + 11, o.getZ() + 8.5, 0F, 0F);
                    return;
                }
                glided[0] |= lip.movement().gliding();
                glided[1] |= pit.movement().gliding();
                t.assertTrue(!glided[0], "a glide opened on the lip of the ditch at " + where(h, lip));
                if (lip.onGround() && now > 20 && glided[1]) {
                    t.assertTrue(Math.abs(rel(h, lip)[1] - 9) < 0.05, "not on the lip: " + where(h, lip));
                    t.succeed();
                }
            });
            h.runAtTickTime(155, t.guard(() -> t.fail("lip " + where(h, lip) + " glided " + glided[0] + "; the pit's copy "
                    + where(h, pit) + " glided " + glided[1])));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code flyerInAPitComesBackByAirForNpcsAndPlayers}: two copies of Ambrose with an aerial window in every phase (so
     * his take-offs are his move selector's, which this test leaves out), each in a pit 6 deep, each fighting an enemy on
     * the rim (an NPC archer and a mock player: the same rules) who cannot get down to him, as he cannot walk up. His
     * reach clock fires after 4 s and his come-back takes off at once: on the same tick for both, within a second of the
     * clock, never before it.
     */
    @GameTest(template = FIELD, timeoutTicks = 300, batch = "hero_live_pit", skyAccess = true)
    public static void flyerInAPitComesBackByAirForNpcsAndPlayers(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            HeroEntity[] heroes = new HeroEntity[2];
            LivingEntity[] foes = new LivingEntity[2];
            int[] zs = {4, 20};
            for (int i = 0; i < 2; i++) {
                int z = zs[i];
                fill(h, 4, 2, z, 10, 7, z + 6, Blocks.STONE);
                fill(h, 6, 2, z + 2, 8, 7, z + 4, Blocks.AIR);
                String id = i == 0 ? "fx_live_pit_npc" : "fx_live_pit_pl";
                HeroEntity amb = HeroAbilityGameTests.hero(t, id, "staff", HeroFightMobilityGameTests.diveKit(id), 7, z + 3);
                amb.goalSelector.removeAllGoals(g -> g instanceof com.factionclash.work.WorkGoal
                        || g instanceof com.factionclash.combat.HeroAbilityGoal);
                amb.targetSelector.removeAllGoals(g -> true);
                amb.restoreProgress(1, 0, java.util.Map.of());
                HeroAbilityGameTests.noRegen(amb);
                heroes[i] = amb;
                if (i == 0) {
                    FactionNpc archer = HeroAbilityGameTests.foe(t, 7, z, 400);
                    archer.role(NpcRole.ARCHER);
                    foes[i] = archer;
                } else {
                    foes[i] = HeroAbilityGameTests.player(t, 7, z, FOE, 400);
                }
            }
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            int[] fired = {-1, -1}, tookOff = {-1, -1};
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                for (int i = 0; i < 2; i++) {
                    LivingEntity foe = foes[i];
                    foe.moveTo(o.getX() + 7.5, o.getY() + 8, o.getZ() + zs[i] + 0.5); // on the rim, still
                    foe.setDeltaMovement(0, 0, 0);
                    if (now < 3) {
                        continue;
                    }
                    HeroEntity amb = heroes[i];
                    amb.setTarget(foe);
                    HeroFightMobility air = amb.combat().air();
                    if (fired[i] < 0 && air.clockFires > 0) {
                        fired[i] = now;
                    }
                    if (tookOff[i] < 0 && air.reachFlights > 0) {
                        tookOff[i] = now;
                        t.assertTrue(fired[i] >= 0, amb.heroId() + " took off before its clock fired");
                    }
                }
                if (tookOff[0] >= 0 && tookOff[1] >= 0) {
                    t.assertTrue(tookOff[0] == tookOff[1], "the NPC's copy took off at " + tookOff[0] + ", the player's at "
                            + tookOff[1]);
                    t.assertTrue(fired[0] == fired[1], "the clocks fired at " + fired[0] + " and " + fired[1]);
                    t.assertTrue(fired[0] - 3 >= BossRules.REACH_LIMIT_TICKS - 1, "the clock fired after " + (fired[0] - 3));
                    t.assertTrue(tookOff[0] - fired[0] <= 20, "took off " + (tookOff[0] - fired[0]) + " ticks after the clock");
                    FactionClash.LOGGER.info("GAMETEST pit: clock at {}, take-off at {}", fired[0] - 3, tookOff[0] - 3);
                    t.succeed();
                }
            });
            h.runAtTickTime(295, t.guard(() -> t.fail("no take-off: NPC's copy " + where(h, heroes[0]) + " "
                    + heroes[0].combat().air().describe() + "; player's copy " + where(h, heroes[1]) + " "
                    + heroes[1].combat().air().describe())));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
