package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroFightMobility;
import com.factionclash.hero.HeroMovement;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.factionclash.gametest.HeroMobilityGameTests.FIELD;
import static com.factionclash.gametest.HeroMobilityGameTests.mode;
import static com.factionclash.gametest.HeroMobilityGameTests.oneShot;
import static com.factionclash.gametest.HeroMobilityGameTests.sliceHero;
import static com.factionclash.gametest.HeroMobilityGameTests.still;

/**
 * The mobility polish of the 2026-11-19 live check (HEROES_V2 15.2 "Mobility in game", "Observed, not changed"): a
 * flight's landing shows no tick of the ride loop after {@code move.fly_land}; a downing in the air goes from
 * {@code move.fall} to {@code move.downed} on the tick it touches down, with no idle frame; a dive that reached its
 * marker just above the ground touches down with its landing one-shot, not a run; an exhausted flyer that lands stands
 * and catches its breath ({@code move.winded}, MOBILITY_AND_WORK 2.2) instead of walking on, until it is rested or a
 * fight calls it (an NPC soldier's blow and a mock player's alike); and no move starts on a reach flight (MOBILITY_AND_WORK
 * 3.7 and 7.4), against an NPC archer and a mock player on a pillar alike.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroMobilityPolishGameTests {
    private HeroMobilityPolishGameTests() {
    }

    private static final FactionType SIDE = FactionType.EMBER_LEGION, FOE = FactionType.GROVE_COVENANT;

    /**
     * Ambrose (on his broom) flies and lands: once {@code move.fly_land} has played, the shown mode is the ground's, never
     * the ride's; a copy downed in the air shows {@code move.downed} on the very tick it is on the ground.
     */
    @GameTest(template = FIELD, timeoutTicks = 300, batch = "hero_polish_seams", skyAccess = true)
    public static void landingAndDowningShowNoSeam(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            HeroEntity a = still(sliceHero(t, "ambrose_nettlewick", "fx_polish_land", 8, 12, 20));
            HeroEntity b = still(sliceHero(t, "ambrose_nettlewick", "fx_polish_down", 20, 12, 20));
            HeroMovement ma = a.movement(), mb = b.movement();
            int[] stage = {0, 0};
            StringBuilder bad = new StringBuilder();
            h.runAtTickTime(4, t.guard(() -> {
                t.assertTrue(ma.takeOff() == null && mb.takeOff() == null, "no take-off");
            }));
            t.onEachTick(() -> {
                long k = h.getTick();
                if (k < 5) {
                    return;
                }
                // a: up 4, then the landing
                switch (stage[0]) {
                    case 0 -> {
                        if (ma.state() == HeroMovement.State.FLY) {
                            ma.flyTo(a.position().add(0, 4, 0));
                            stage[0] = 1;
                        }
                    }
                    case 1 -> {
                        if (ma.state() == HeroMovement.State.FLY && a.getY() > HeroMobilityGameTests.floorY(h) + 3.5) {
                            t.assertTrue(ma.land() == null, "no landing");
                            stage[0] = 2;
                        }
                    }
                    case 2 -> {
                        if (ma.state() == HeroMovement.State.LANDING && oneShot(a) == null && "ride".equals(mode(a))) {
                            bad.append(" a: the ride loop at tick ").append(k).append(" after move.fly_land;");
                        }
                        if (ma.state() == HeroMovement.State.NONE && a.onGround()) {
                            stage[0] = 3;
                        }
                    }
                    default -> {
                    }
                }
                // b: up 5, then downed: on the ground it shows move.downed at once
                switch (stage[1]) {
                    case 0 -> {
                        if (mb.state() == HeroMovement.State.FLY) {
                            mb.flyTo(b.position().add(0, 5, 0));
                            stage[1] = 1;
                        }
                    }
                    case 1 -> {
                        if (mb.state() == HeroMovement.State.FLY && b.getY() > HeroMobilityGameTests.floorY(h) + 4.5) {
                            t.assertTrue(mb.down(HeroMovement.Cause.OPERATOR), "not downed");
                            stage[1] = 2;
                        }
                    }
                    case 2 -> {
                        if (b.onGround()) {
                            if (!"downed".equals(mode(b))) {
                                bad.append(" b: on the ground at tick ").append(k).append(" in ").append(mode(b))
                                        .append(" (").append(mb.state()).append("), not move.downed;");
                            }
                            stage[1] = 3;
                        }
                    }
                    default -> {
                    }
                }
                if (stage[0] == 3 && stage[1] == 3) {
                    t.assertTrue(bad.length() == 0, bad.toString());
                    t.succeed();
                }
            });
            h.runAtTickTime(290, t.guard(() -> t.fail("stages " + stage[0] + "/" + stage[1] + ": " + ma.state() + ", " + mb.state()
                    + bad)));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Ambrose dives from 6 up at a marker 1.5 blocks over the ground ahead: the dive ends there, in the air, and when he
     * comes down the last bit his landing one-shot plays, as after any dive, for at least 8 ticks (the 0.5 s touch-down),
     * so no run shows while the client's smoothed speed falls.
     */
    @GameTest(template = FIELD, timeoutTicks = 260, batch = "hero_polish_dive", skyAccess = true)
    public static void diveEndingInTheAirTouchesDownWithItsLanding(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 260);
        try {
            HeroEntity a = still(sliceHero(t, "ambrose_nettlewick", "fx_polish_dive", 8, 8, 20));
            HeroMovement m = a.movement();
            int[] stage = {0}, shot = {0};
            h.runAtTickTime(4, t.guard(() -> t.assertTrue(m.takeOff() == null, "no take-off")));
            t.onEachTick(() -> {
                if (h.getTick() < 5) {
                    return;
                }
                double floor = HeroMobilityGameTests.floorY(h);
                switch (stage[0]) {
                    case 0 -> {
                        if (m.state() == HeroMovement.State.FLY) {
                            m.flyTo(a.position().add(0, 6, 0));
                            stage[0] = 1;
                        }
                    }
                    case 1 -> {
                        if (m.state() == HeroMovement.State.FLY && a.getY() > floor + 5.5) {
                            String no = m.dive(new Vec3(a.getX() + 6, floor + 1.5, a.getZ() + 6));
                            t.assertTrue(no == null, "no dive: " + no);
                            stage[0] = 2;
                        }
                    }
                    case 2 -> {
                        if (m.state() == HeroMovement.State.NONE) {
                            t.assertTrue(!a.onGround(), "the dive came down on the ground, not at its marker in the air");
                            stage[0] = 3;
                        }
                    }
                    case 3 -> {
                        if (a.onGround()) {
                            t.assertTrue(oneShot(a) != null, "no landing one-shot at the dive's touch-down: " + mode(a));
                            stage[0] = 4;
                        }
                    }
                    default -> {
                        if (oneShot(a) != null) {
                            shot[0]++;
                        } else {
                            t.assertTrue(shot[0] >= 8, "the touch-down's landing played " + shot[0] + " ticks");
                            t.succeed();
                        }
                    }
                }
            });
            h.runAtTickTime(250, t.guard(() -> t.fail("stage " + stage[0] + ": " + m.state() + " " + mode(a))));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Three copies of Ambrose fly out of stamina and glide down exhausted. The first, sent on a walk every tick as its job
     * would, stands and pants ({@code move.winded}) where it landed until its stamina is back at 50%, then walks; the
     * second's rest ends at once at an NPC soldier's blow, the third's at a mock player's, on the same tick.
     */
    @GameTest(template = FIELD, timeoutTicks = 520, batch = "hero_polish_winded", skyAccess = true)
    public static void exhaustedFlyerCatchesItsBreathUntilAFightCalls(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 520);
        try {
            HeroEntity[] am = {
                    still(sliceHero(t, "ambrose_nettlewick", "fx_polish_rest", 6, 6, 1)),
                    still(sliceHero(t, "ambrose_nettlewick", "fx_polish_rest_npc", 6, 16, 1)),
                    still(sliceHero(t, "ambrose_nettlewick", "fx_polish_rest_pl", 6, 26, 1))};
            FactionNpc soldier = HeroAbilityGameTests.foe(t, 12, 16, 40);
            ServerPlayer player = HeroAbilityGameTests.player(t, 12, 26, FOE, 40);
            int[] stage = {0, 0, 0}, restAt = {-1, -1, -1};
            Vec3[] where = new Vec3[3];
            h.runAtTickTime(4, t.guard(() -> {
                for (HeroEntity x : am) {
                    t.assertTrue(x.movement().takeOff() == null, "no take-off");
                }
            }));
            t.onEachTick(() -> {
                int k = (int) h.getTick();
                if (k < 5) {
                    return;
                }
                for (int i = 0; i < 3; i++) {
                    HeroEntity x = am[i];
                    HeroMovement m = x.movement();
                    switch (stage[i]) {
                        case 0 -> {
                            if (m.state() == HeroMovement.State.FLY) {
                                m.flyTo(x.position().add(0, 4, 0));
                                stage[i] = 1;
                            }
                        }
                        case 1 -> {
                            if (m.state() == HeroMovement.State.FLY && x.getY() > HeroMobilityGameTests.floorY(h) + 3) {
                                m.setStaminaShare(0); // out of stamina: it glides down exhausted
                                stage[i] = 2;
                            }
                        }
                        case 2 -> {
                            if (x.onGround() && m.state() != HeroMovement.State.GLIDE) {
                                t.assertTrue(m.winded(), x.heroId() + " landed exhausted and did not rest: " + m.state());
                                restAt[i] = k;
                                where[i] = x.position();
                                stage[i] = 3;
                            }
                        }
                        default -> {
                        }
                    }
                }
                // the first: its job's walk every tick, held while it pants
                if (stage[0] == 3) {
                    HeroEntity x = am[0];
                    x.getNavigation().moveTo(where[0].x + 10, where[0].y, where[0].z, 1.0);
                    if (x.movement().winded()) {
                        t.assertTrue(x.position().distanceTo(where[0]) < 0.5, "it walked on while winded: "
                                + x.position().distanceTo(where[0]));
                        t.assertTrue(oneShot(x) != null || "winded".equals(mode(x)), "winded shows " + mode(x)); // (after move.land)
                        t.assertTrue(x.movement().stamina().exhausted(), "rested but still panting");
                    } else {
                        t.assertTrue(!x.movement().stamina().exhausted(), "the rest ended while still exhausted");
                        t.assertTrue(k - restAt[0] >= 100, "a rest of only " + (k - restAt[0]) + " ticks");
                        stage[0] = 4;
                    }
                }
                // the second and third: a blow from an NPC soldier and from a mock player, 20 ticks into the rest
                LivingEntity[] by = {null, soldier, player};
                for (int i = 1; i < 3; i++) {
                    if (stage[i] == 3 && k - restAt[i] == 20) {
                        am[i].invulnerableTime = 0;
                        am[i].hurt(by[i] instanceof ServerPlayer p ? p.damageSources().playerAttack(p)
                                : by[i].damageSources().mobAttack(by[i]), 1F);
                        stage[i] = 4;
                        restAt[i] = k;
                    } else if (stage[i] == 4 && k - restAt[i] == 1) {
                        t.assertTrue(!am[i].movement().winded(), am[i].heroId() + " rests on under a blow");
                        stage[i] = 5;
                    }
                }
                if (stage[0] == 4 && stage[1] == 5 && stage[2] == 5) {
                    t.succeed();
                }
            });
            h.runAtTickTime(510, t.guard(() -> t.fail("stages " + stage[0] + "/" + stage[1] + "/" + stage[2] + ", first: "
                    + am[0].movement().state() + " stamina " + am[0].movement().stamina().value())));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** A melee flyer (Kagenori's kit with wings, aerial only in phase 3) with a rain it may cast in every phase. */
    private static HeroEntity rainFlyer(GameTestBases.Handle t, String id, int x, int z) {
        String slice = "kagenori_tsurumine";
        String text = HeroAbilityGameTests.slice(slice).replace("\"" + slice + "\"", "\"" + id + "\"");
        String rain = "{\"id\": \"test_rain\", \"name\": \"Test Rain\", \"form\": \"rain\", \"class\": \"area\", \"band\": [\"any\"],"
                + " \"phases\": [1, 2, 3], \"count\": 3, \"radius\": 2, \"duration\": 1, \"cooldown\": 4, \"weight\": 100,"
                + " \"frames\": {\"windup\": 1.0, \"active\": 0.5, \"recovery\": 0.5}, \"effects\": [{\"type\": \"damage\", \"amount\": 1}]}, ";
        text = text.replaceFirst("\"moves\": \\[", "\"moves\": [" + java.util.regex.Matcher.quoteReplacement(rain));
        int i = text.indexOf("\"mobility\"");
        int e = text.indexOf('\n', i);
        text = text.substring(0, i) + "\"mobility\": {\"primary\": {\"trait\": \"flyer\", \"tier\": 2}, \"carrier\": {\"kind\":"
                + " \"wings\", \"part\": \"wings\"}, \"aerial\": {\"phases\": [3]}}" + text.substring(e);
        HeroEntity hero = HeroAbilityGameTests.hero(t, id, "katana", text, x, z);
        hero.goalSelector.removeAllGoals(g -> g instanceof com.factionclash.work.WorkGoal);
        hero.targetSelector.removeAllGoals(g -> true);
        hero.restoreProgress(20, 0, java.util.Map.of());
        HeroAbilityGameTests.noRegen(hero);
        return hero;
    }

    /**
     * MOBILITY_AND_WORK 3.7 and 7.4: a flyer outside its aerial phases takes off only to reach a target no walk
     * reaches, with no moves in the air. Two copies, one against an NPC archer and one against a mock player, each on a
     * 1x1 pillar 5 high: both make a reach flight, and neither starts its rain (or any move) while up on it.
     */
    @GameTest(template = FIELD, timeoutTicks = 320, batch = "hero_polish_reach", skyAccess = true)
    public static void noMoveStartsOnAReachFlight(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 320);
        try {
            HeroNavigationGameTests.fill(h, 12, 2, 8, 12, 6, 8, Blocks.STONE);
            HeroNavigationGameTests.fill(h, 12, 2, 22, 12, 6, 22, Blocks.STONE);
            HeroEntity a = rainFlyer(t, "fx_polish_reach_npc", 6, 8);
            HeroEntity b = rainFlyer(t, "fx_polish_reach_pl", 6, 22);
            FactionNpc archer = HeroAbilityGameTests.foe(t, 14, 8, 400);
            archer.role(NpcRole.ARCHER);
            ServerPlayer player = HeroAbilityGameTests.player(t, 14, 22, FOE, 400);
            LivingEntity[] foes = {archer, player};
            HeroEntity[] heroes = {a, b};
            BlockPos[] tops = {h.absolutePos(new BlockPos(12, 7, 8)), h.absolutePos(new BlockPos(12, 7, 22))};
            int[] upTicks = {0, 0};
            StringBuilder bad = new StringBuilder();
            t.onEachTick(() -> {
                if (h.getTick() < 3) {
                    return;
                }
                for (int i = 0; i < 2; i++) {
                    LivingEntity f = foes[i];
                    f.moveTo(tops[i].getX() + 0.5, tops[i].getY(), tops[i].getZ() + 0.5);
                    f.setDeltaMovement(0, 0, 0);
                    f.invulnerableTime = Math.max(0, f.invulnerableTime - 1);
                    HeroEntity x = heroes[i];
                    x.setTarget(f);
                    boolean reach = !x.onGround() && x.combat().air().air() == HeroFightMobility.Air.REACH;
                    if (reach) {
                        upTicks[i]++;
                        if (x.combat().running()) {
                            bad.append(" ").append(x.heroId()).append(" runs ").append(x.combat().runningMove())
                                    .append(" on a reach flight at tick ").append(h.getTick()).append(';');
                        }
                    }
                }
                t.assertTrue(bad.length() == 0, bad.toString());
            });
            h.runAtTickTime(300, t.guard(() -> {
                t.assertTrue(bad.length() == 0, bad.toString());
                t.assertTrue(a.combat().air().reachFlights >= 1 && b.combat().air().reachFlights >= 1, "no reach flight: "
                        + a.combat().air().reachFlights + "/" + b.combat().air().reachFlights);
                t.assertTrue(upTicks[0] > 20 && upTicks[1] > 20, "too little time up there: " + upTicks[0] + "/" + upTicks[1]);
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
