package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.combat.HeroComeBackGoal;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroFightMobility;
import com.factionclash.hero.HeroMovement;
import com.factionclash.logic.hero.BossRules;
import com.factionclash.logic.hero.EscapeRules;
import com.factionclash.logic.hero.HeroLinks;
import com.factionclash.logic.hero.MovePlan;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import com.factionclash.work.WorkGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.factionclash.gametest.HeroMobilityGameTests.FIELD;
import static com.factionclash.gametest.HeroMobilityGameTests.floorY;
import static com.factionclash.gametest.HeroMobilityGameTests.sliceHero;
import static com.factionclash.gametest.HeroMobilityGameTests.still;
import static com.factionclash.gametest.HeroNavigationGameTests.fill;
import static com.factionclash.gametest.HeroNavigationGameTests.rel;
import static com.factionclash.gametest.HeroNavigationGameTests.taken;
import static com.factionclash.gametest.HeroNavigationGameTests.where;

/**
 * Heroes, task A12: mobility in a fight and in an escape (MOBILITY_AND_WORK 3.7, 5.1, 7.3-7.7 and 17.2). A flyer that
 * keeps above every engaged enemy's reach is brought down by the reach clock, under the fight's ceiling, the same tick
 * against an NPC soldier and a mock player; a dive lands on its decal, touches down and goes again while its window
 * has dives and lands for its full recovery after the last, an NPC soldier and a mock player hit alike; a hero on a
 * cliff no engaged enemy can climb comes back within reach; a flyer and a climber reach an enemy on a 1x1 pillar at
 * once (a mock player or an NPC archer alike) and land basic attacks from the air and the cling while a soldier waits
 * out of reach; Natsuho escapes over a wall the soldiers must go round, and Ambrose flies off to his refuge, from NPC
 * soldiers and a mock player alike; an escape that starts in the air flies on from there. Each test has its own batch.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroFightMobilityGameTests {
    private HeroFightMobilityGameTests() {
    }

    private static final FactionType SIDE = FactionType.EMBER_LEGION, FOE = FactionType.GROVE_COVENANT;

    // ------------------------------------------------------------------ fixtures

    /** An enemy soldier with its AI on (it walks and swings), an iron sword, no job; {@code target} its target. */
    static FactionNpc chaser(GameTestBases.Handle t, int x, int z, LivingEntity target) {
        FactionNpc n = HeroAbilityGameTests.foe(t, x, z, 100);
        n.setNoAi(false);
        n.goalSelector.removeAllGoals(g -> g instanceof WorkGoal);
        n.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        n.setTarget(target);
        return n;
    }

    /** Moves a test base's record to relative (x, 2, z) with walls half-size {@code half} (its spawn there too). */
    static void moveBase(GameTestHelper h, BaseState b, int x, int z, int half) {
        b.center = h.absolutePos(new BlockPos(x, 2, z));
        b.points.put(BaseState.SPAWN, b.center);
        b.points.put(BaseState.MIN, b.center.offset(-(half + 1), -1, -(half + 1)));
        b.points.put(BaseState.MAX, b.center.offset(half + 1, 8, half + 1));
        b.testHalf = half;
    }

    /** A melee hero that flies (Kagenori's kit and level with a flyer II's mobility, aerial only in phase 3). */
    static HeroEntity meleeFlyer(GameTestBases.Handle t, String id, int x, int z, int level) {
        return HeroNavigationGameTests.withMobility(t, "kagenori_tsurumine", id, "{\"primary\": {\"trait\": \"flyer\", \"tier\": 2},"
                + " \"carrier\": {\"kind\": \"wings\", \"part\": \"wings\"}, \"aerial\": {\"phases\": [3]}}", x, z, level);
    }

    /** The share of max health a blow of {@code share} lands from now (under the 12% cap). */
    static void dropTo(HeroEntity hero, double share) {
        hero.setHealth((float) (hero.getMaxHealth() * share));
    }

    // ------------------------------------------------------------------ the reach clock and the ceiling (5.1.3, 5.1.6)

    /**
     * {@code bossFlyerKeepsAirBudget}'s reach half: two copies of Ambrose, one fighting an NPC soldier and one a mock
     * player, take off together and fly for a point 15 blocks up. Both stay under the fight's ceiling (6 above the
     * ground, the highest engaged enemy's feet), and both land when the reach clock fires: 4 s after they rose more than
     * 3 blocks over their enemy's feet, on the same tick, well before their 8 s air budget.
     */
    @GameTest(template = FIELD, timeoutTicks = 260, batch = "hero_fm_clock")
    public static void flyerOutOfReachIsBroughtDownByTheClock(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 260);
        try {
            HeroEntity a = still(sliceHero(t, "ambrose_nettlewick", "fx_fm_clock_npc", 6, 6, 1));
            HeroEntity b = still(sliceHero(t, "ambrose_nettlewick", "fx_fm_clock_pl", 6, 26, 1));
            FactionNpc soldier = HeroAbilityGameTests.foe(t, 10, 6, 100);
            ServerPlayer player = HeroAbilityGameTests.player(t, 10, 26, FOE, 100);
            double floor = floorY(h);
            int[] above = {-1, -1}, landed = {-1, -1}, pushed = {-1, -1};
            double[] top = {-1e9, -1e9};
            boolean[] held = {false};
            h.runAtTickTime(3, t.guard(() -> {
                a.setTarget(soldier);
                b.setTarget(player);
            }));
            h.runAtTickTime(6, t.guard(() -> {
                t.assertTrue(a.movement().takeOff() == null && b.movement().takeOff() == null, "no take-offs");
            }));
            h.runAtTickTime(24, t.guard(() -> {
                t.assertTrue(a.movement().flyTo(a.position().add(20, 15, 0)) == null, "A flies");
                t.assertTrue(b.movement().flyTo(b.position().add(20, 15, 0)) == null, "B flies");
            }));
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 6) {
                    return;
                }
                HeroEntity[] both = {a, b};
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = both[i];
                    top[i] = Math.max(top[i], x.getY());
                    if (pushed[i] < 0 && x.movement().state() == HeroMovement.State.RISE) {
                        pushed[i] = now;
                    }
                    if (above[i] < 0 && x.getY() - floor > BossRules.REACH_ABOVE) {
                        above[i] = now;
                    }
                    if (landed[i] < 0 && x.movement().state() == HeroMovement.State.LAND) {
                        landed[i] = now;
                    }
                    if (x.getTarget() == null) {
                        x.setTarget(i == 0 ? soldier : player);
                    }
                }
                // a soldier under a hero in the air waits out of reach for it to land (3.8): no chase, no pillar
                if (now >= 40 && a.getY() - floor > 4 && a.movement().flying() && soldier.isAlive()) {
                    if (soldier.isNoAi()) {
                        // (still until the flyer is up, so both copies' fights start alike; then on its feet with no
                        // target of its own until the flyer is near the fight's ceiling, more than 5 over it: its first
                        // look is at a hero above its planning height)
                        soldier.setNoAi(false);
                        soldier.goalSelector.removeAllGoals(g -> g instanceof WorkGoal);
                        soldier.targetSelector.removeAllGoals(g -> true);
                        soldier.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                    }
                    if (a.getY() - floor > 5.5) {
                        soldier.setTarget(a);
                        held[0] |= soldier.pursuit().mode() == com.factionclash.combat.Pursuit.Mode.HOLD;
                    }
                    t.assertTrue(soldier.pursuit().pillars == 0, "the soldier pillared toward a flyer");
                }
                if (landed[0] > 0 && landed[1] > 0) {
                    t.assertTrue(held[0], "the soldier did not hold under the flyer: " + soldier.pursuit().mode());
                    t.assertTrue(landed[0] == landed[1], "the NPC's flyer landed at " + landed[0] + ", the player's at " + landed[1]);
                    t.assertTrue(above[0] == above[1], "out of reach at " + above[0] + " and " + above[1]);
                    int clock = landed[0] - above[0];
                    t.assertTrue(clock >= BossRules.REACH_LIMIT_TICKS - 1 && clock <= BossRules.REACH_LIMIT_TICKS + 2,
                            "the reach clock brought it down " + clock + " ticks after it rose out of reach");
                    t.assertTrue(landed[0] < pushed[0] + 150, "landed at " + landed[0] + ": the air budget, not the clock");
                    t.assertTrue(top[0] - floor <= BossRules.FIGHT_CEILING + 0.2 && top[0] - floor >= BossRules.FIGHT_CEILING - 0.6,
                            "it rose " + (top[0] - floor) + " blocks in a fight: the ceiling is 6");
                    t.assertTrue(a.combat().air().clockFires == 1 && b.combat().air().clockFires == 1, "one clock fire each");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the dive (7.3, 7.4)

    /** Ambrose's kit with a dive (from the air, 3 dives a window, aerial in every phase). */
    static String diveKit(String id) {
        String text = HeroAbilityGameTests.slice("ambrose_nettlewick").replace("\"ambrose_nettlewick\"", "\"" + id + "\"");
        String dive = "{\"id\": \"broom_dive\", \"name\": \"Broom Dive\", \"form\": \"dive\", \"class\": \"heavy\","
                + " \"band\": [\"any\"], \"phases\": [1, 2, 3], \"range\": 10, \"radius\": 2, \"from\": \"air\", \"cooldown\": 4,"
                + " \"frames\": {\"windup\": 0.8, \"active\": 0.2, \"recovery\": 1.0}, \"effects\": [{\"type\": \"damage\", \"amount\": 4}]}, ";
        text = text.replaceFirst("\"moves\": \\[", "\"moves\": [" + java.util.regex.Matcher.quoteReplacement(dive));
        int i = text.indexOf("\"aerial\"");
        int e = text.indexOf('}', i);
        return text.substring(0, i) + "\"aerial\": {\"phases\": [1, 2, 3], \"max_air\": 8, \"dives\": 3" + text.substring(e);
    }

    /** One dive's record: its marker, where it came down, the target's health before and after. */
    static final class DiveRun {
        final HeroEntity hero;
        final LivingEntity foe;
        int dives, impacts, touchAndGo, stage;
        Vec3 marker;
        double off = -1;
        float before;
        int lastImpactAt = -1, recoveryTicks;
        boolean wasDiving;

        DiveRun(HeroEntity hero, LivingEntity foe) {
            this.hero = hero;
            this.foe = foe;
        }

        /** One tick; returns whether its third dive has landed and its recovery has been seen. */
        boolean step(GameTestBases.Handle t, int now, double floor) {
            HeroMovement mv = hero.movement();
            boolean diving = mv.diving();
            if (wasDiving && !diving) {
                impacts++;
                off = Math.hypot(hero.getX() - marker.x, hero.getZ() - marker.z);
                t.assertTrue(off <= 1.5, hero.heroId() + "'s dive " + impacts + " came down " + off + " from its decal");
                t.assertTrue(foe.getHealth() < before, hero.heroId() + "'s dive " + impacts + " did not hit");
                lastImpactAt = now;
            }
            wasDiving = diving;
            if (mv.state() == HeroMovement.State.FLY && hero.getY() > floor + 2.5 && !hero.combat().running() && dives == impacts
                    && dives < 3) {
                marker = foe.position();
                before = foe.getHealth();
                t.assertTrue(hero.combat().forceMove("broom_dive"), hero.heroId() + ": no dive started");
                dives++;
            }
            if (impacts == 3 && lastImpactAt >= 0) {
                if (hero.combat().running() && hero.combat().stage() == MovePlan.MoveStage.RECOVERY && hero.onGround()) {
                    recoveryTicks++;
                }
                if (!hero.combat().running() && now > lastImpactAt + 5) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * {@code diveLandsOnDecalAndRecovers}: two flyers with a dive (3 a window) in an aerial window, one over an NPC soldier
     * and one over a mock player. Each dive comes down within 1.5 blocks of where its decal was drawn (the target's spot
     * as the wind-up began) and hits; the first two touch down for 0.5 s and rise again (touch-and-go), the third lands
     * for the full recovery (at least 1 s on the ground in the move's recovery: the punish window) and the window closes.
     * The same for both.
     */
    @GameTest(template = FIELD, timeoutTicks = 500, batch = "hero_fm_dive", skyAccess = true)
    public static void diveLandsOnDecalAndRecovers(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 500);
        try {
            HeroEntity a = HeroAbilityGameTests.hero(t, "fx_fm_dive_npc", "staff", diveKit("fx_fm_dive_npc"), 6, 6);
            HeroEntity b = HeroAbilityGameTests.hero(t, "fx_fm_dive_pl", "staff", diveKit("fx_fm_dive_pl"), 6, 24);
            for (HeroEntity x : new HeroEntity[]{a, b}) {
                x.goalSelector.removeAllGoals(g -> g instanceof WorkGoal || g instanceof com.factionclash.combat.HeroAbilityGoal
                        || g instanceof com.factionclash.combat.HeroThrowGoal);
                x.targetSelector.removeAllGoals(g -> true);
                x.restoreProgress(20, 0, java.util.Map.of());
                HeroAbilityGameTests.noRegen(x);
            }
            FactionNpc soldier = HeroAbilityGameTests.foe(t, 11, 6, 200);
            ServerPlayer player = HeroAbilityGameTests.player(t, 11, 24, FOE, 200);
            DiveRun ra = new DiveRun(a, soldier), rb = new DiveRun(b, player);
            double floor = floorY(h);
            h.runAtTickTime(3, t.guard(() -> {
                a.setTarget(soldier);
                b.setTarget(player);
            }));
            h.runAtTickTime(6, t.guard(() -> {
                String na = a.combat().air().aerialTakeOff(), nb = b.combat().air().aerialTakeOff();
                t.assertTrue(na == null && nb == null, "no aerial window: " + na + " / " + nb);
            }));
            boolean[] done = {false, false};
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 8) {
                    return;
                }
                a.setTarget(soldier);
                b.setTarget(player);
                done[0] |= ra.step(t, now, floor);
                done[1] |= rb.step(t, now, floor);
                if (done[0] && done[1]) {
                    HeroFightMobility fa = a.combat().air(), fb = b.combat().air();
                    t.assertTrue(fa.touchAndGos == 2 && fb.touchAndGos == 2, "touch-and-go " + fa.touchAndGos + " / " + fb.touchAndGos);
                    t.assertTrue(ra.recoveryTicks >= 18 && rb.recoveryTicks >= 18, "the punish window: " + ra.recoveryTicks + " / "
                            + rb.recoveryTicks + " ticks of recovery on the ground");
                    t.assertTrue(fa.air() == HeroFightMobility.Air.NONE && !fa.window().isOpen(), "the window stays open");
                    t.assertTrue(a.onGround() && b.onGround(), "not on the ground after the last dive");
                    t.assertTrue(Math.abs(soldier.getHealth() - player.getHealth()) < 1e-3, "the dives hit the NPC for "
                            + (200 - soldier.getHealth()) + " and the player for " + (200 - player.getHealth()));
                    t.succeed();
                }
            });
            h.runAtTickTime(495, t.guard(() -> t.fail("dives: " + ra.dives + "/" + ra.impacts + " and " + rb.dives + "/" + rb.impacts
                    + "; " + a.movement().describe() + " / " + a.combat().air().describe())));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ a fighting hero comes back (5.1.15)

    /**
     * {@code fightingHeroComesBack}, shared: Kagenori (level 20) stands on a 3x3 cliff 6 high, fighting an enemy on the
     * ground below that no ordinary person reaches up there (walking, a 2-block tower, a 3-block reach): an NPC soldier or a
     * mock player, the same rules. His reach clock fires after 4 s there (no sooner) and he comes back down toward the
     * enemy, within its reach in a few seconds more; his own goals are only the hold and the come-back.
     */
    private static void comesBack(GameTestHelper h, boolean player) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 400);
        try {
            fill(h, 18, 2, 14, 20, 7, 16, Blocks.STONE);
            HeroEntity kage = still(sliceHero(t, "kagenori_tsurumine", player ? "fx_fm_back_pl" : "fx_fm_back_npc", 14, 15, 20));
            kage.goalSelector.addGoal(2, new HeroComeBackGoal(kage));
            LivingEntity foe = player ? HeroAbilityGameTests.player(t, 12, 15, FOE, 100) : HeroAbilityGameTests.foe(t, 12, 15, 100);
            BlockPos top = h.absolutePos(new BlockPos(19, 8, 15));
            kage.moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 90F, 0F);
            int[] backAt = {-1};
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 3) {
                    return;
                }
                kage.setTarget(foe);
                HeroFightMobility air = kage.combat().air();
                if (now < 3 + BossRules.REACH_LIMIT_TICKS - 2) {
                    t.assertTrue(air.clockFires == 0, "the clock fired after " + (now - 3) + " ticks");
                    t.assertTrue(kage.getY() > top.getY() - 0.5, "it came down before its clock: " + where(h, kage));
                }
                if (air.clockFires > 0 && backAt[0] < 0 && kage.distanceTo(foe) <= 3.5) {
                    backAt[0] = now;
                    t.assertTrue(now <= 3 + BossRules.REACH_LIMIT_TICKS + 20 + 140, "it came back after " + (now - 3) + " ticks");
                    FactionClash.LOGGER.info("GAMETEST {} came back within reach after {} ticks", kage.heroId(), now - 3);
                    t.succeed();
                }
            });
            h.runAtTickTime(395, t.guard(() -> t.fail("not back: " + where(h, kage) + ", clock " + kage.combat().air().clockTicks()
                    + ", fires " + kage.combat().air().clockFires + ", out " + kage.combat().air().outOfReach())));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    @GameTest(template = FIELD, timeoutTicks = 400, batch = "hero_fm_back_npc")
    public static void fightingHeroComesBackFromAnNpc(GameTestHelper h) {
        comesBack(h, false);
    }

    @GameTest(template = FIELD, timeoutTicks = 400, batch = "hero_fm_back_player")
    public static void fightingHeroComesBackFromAPlayer(GameTestHelper h) {
        comesBack(h, true);
    }

    // ------------------------------------------------------------------ reaching a 1x1 pillar at once (3.7)

    /**
     * {@code heroReachesPillarPlayerAtOnce}, shared: an enemy (a mock player, or an NPC archer: the same rules) stands on a
     * 1x1 pillar 5 high. A melee flyer outside its aerial phases (a reach flight) and Tobimaru (a climber II) are in melee
     * reach of it within 4 s with no planning and land basic attacks from the air and from the wall, where no cell beside
     * the target holds them; an ordinary soldier of their side does what its pursuit does today: it waits out of reach,
     * never on the pillar.
     */
    private static void pillar(GameTestHelper h, boolean player) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            fill(h, 16, 2, 16, 16, 6, 16, Blocks.STONE);
            String p = player ? "pl" : "npc";
            HeroEntity flyer = meleeFlyer(t, "fx_fm_pillar_fly_" + p, 10, 16, 20);
            HeroEntity tobi = HeroMobilityGameTests.sliceHero(t, "tobimaru_usugumo", "fx_fm_pillar_climb_" + p, 22, 16, 20);
            for (HeroEntity x : new HeroEntity[]{flyer, tobi}) {
                x.goalSelector.removeAllGoals(g -> g instanceof com.factionclash.combat.HeroAbilityGoal);
                x.targetSelector.removeAllGoals(g -> true);
            }
            FactionNpc soldier = t.npc(16, 10, NpcRole.SWORDSMAN);
            soldier.goalSelector.removeAllGoals(g -> g instanceof WorkGoal);
            soldier.targetSelector.removeAllGoals(g -> true);
            soldier.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            LivingEntity foe;
            if (player) {
                foe = HeroAbilityGameTests.player(t, 16, 20, FOE, 400);
            } else {
                FactionNpc archer = HeroAbilityGameTests.foe(t, 16, 20, 400);
                archer.role(NpcRole.ARCHER);
                foe = archer;
            }
            BlockPos top = h.absolutePos(new BlockPos(16, 7, 16));
            foe.moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 0F, 0F);
            int[] reach = {-1, -1};
            boolean[] struck = {false, false};
            double[] climbTop = {0};
            boolean[] onWall = {false};
            float[] hp = {0};
            int[] stamp = {0, 0};
            StringBuilder log = new StringBuilder();
            HeroMovement.State[] last = {null};
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 3) {
                    hp[0] = foe.getHealth();
                    return;
                }
                foe.moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5); // it stands still on its pillar
                foe.setDeltaMovement(0, 0, 0);
                if (player) {
                    // a real player counts its hurt cooldown down in ServerPlayer.tick, which the mock player skips: without
                    // this, after the flyer's first blow only a harder blow than it could ever land on the mock player
                    foe.invulnerableTime = Math.max(0, foe.invulnerableTime - 1);
                }
                HeroEntity[] both = {flyer, tobi};
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = both[i];
                    x.setTarget(foe);
                    if (reach[i] < 0 && com.factionclash.hero.HeroForms.inMeleeReach(x, foe)) {
                        reach[i] = now;
                    }
                    // (both strike: a blow inside the other's hurt cooldown is the vanilla rule's, so each one's own blows count)
                    if (foe.getLastHurtByMob() == x && foe.getLastHurtByMobTimestamp() != stamp[i]) {
                        stamp[i] = foe.getLastHurtByMobTimestamp();
                        struck[i] = true;
                    }
                }
                if ((tobi.movement().state() != last[0] || now % 10 == 0 && now >= 50 && now <= 140) && log.length() < 2400) {
                    last[0] = tobi.movement().state();
                    log.append(String.format(java.util.Locale.ROOT, " [d %.2f f %s r %s c %s by %s]",
                            com.factionclash.hero.HeroForms.distance(foe, tobi.position().add(0, tobi.getBbHeight() / 2, 0)),
                            tobi.combat().boss().fighting(), tobi.combat().running(), tobi.combat().air().describe().length() > 0
                                    ? tobi.combat().air().linkStrikes : -1, foe.getLastHurtByMob() == null ? "-"
                                    : foe.getLastHurtByMob() == flyer ? "fly@" + foe.getLastHurtByMobTimestamp() : "x"));
                    double[] r = rel(h, tobi);
                    log.append(String.format(java.util.Locale.ROOT, " %d:%s(%.1f,%.1f,%.1f)", now, last[0], r[0], r[1], r[2]));
                }
                hp[0] = foe.getHealth();
                climbTop[0] = Math.max(climbTop[0], rel(h, tobi)[1]);
                onWall[0] |= tobi.movement().onWall();
                soldier.setTarget(foe);
                t.assertTrue(soldier.getY() < top.getY() - 2, "the soldier is on the pillar");
                if (reach[0] >= 0 && reach[1] >= 0 && struck[0] && struck[1]) {
                    t.assertTrue(reach[0] - 3 <= 80 && reach[1] - 3 <= 80, "in reach after " + (reach[0] - 3) + " and "
                            + (reach[1] - 3) + " ticks");
                    t.assertTrue(flyer.combat().air().reachFlights >= 1, "no reach flight");
                    t.assertTrue(flyer.combat().air().linkStrikes >= 1, "no blow from the air");
                    t.assertTrue(tobi.combat().air().linkStrikes >= 1, "no blow from the wall");
                    t.assertTrue(flyer.pursuit().plans == 0 && tobi.pursuit().plans == 0 && flyer.pursuit().pillars == 0
                            && tobi.pursuit().pillars == 0, "a hero planned its way");
                    t.assertTrue(soldier.pursuit().mode() != com.factionclash.combat.Pursuit.Mode.CHASE
                            || soldier.distanceTo(foe) > 2.5, "the soldier stands in reach");
                    FactionClash.LOGGER.info("GAMETEST pillar {}: flyer in reach after {} ticks, climber after {}", p,
                            reach[0] - 3, reach[1] - 3);
                    t.succeed();
                }
            });
            h.runAtTickTime(295, t.guard(() -> t.fail("reach " + reach[0] + "/" + reach[1] + ", struck " + struck[0] + "/"
                    + struck[1] + "; flyer " + where(h, flyer) + " " + flyer.combat().air().describe() + "; climber "
                    + where(h, tobi) + " " + tobi.movement().state() + " up to " + climbTop[0] + ", on a wall " + onWall[0]
                    + ", link strikes " + tobi.combat().air().linkStrikes + ", pursuit " + tobi.pursuit().mode() + ", climbs "
                    + taken(tobi, HeroLinks.Kind.CLIMB) + ", foe hp " + foe.getHealth() + "; climber states" + log)));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    @GameTest(template = FIELD, timeoutTicks = 300, batch = "hero_fm_pillar_player", skyAccess = true)
    public static void heroReachesPillarPlayerAtOnce(GameTestHelper h) {
        pillar(h, true);
    }

    @GameTest(template = FIELD, timeoutTicks = 300, batch = "hero_fm_pillar_npc", skyAccess = true)
    public static void heroReachesPillarArcherAtOnce(GameTestHelper h) {
        pillar(h, false);
    }

    // ------------------------------------------------------------------ escape with mobility (7.7)

    /**
     * A {@code move_self} hop "back" from a blow goes away from whoever dealt it, whichever way the hero faces (v1 13.4;
     * found by the wall escape below: the blow that starts an escape clears the target first, and Natsuho's Nine Lives
     * then hopped 6 blocks behind her spawn facing, as often as not into the soldiers). Two copies of Natsuho with no
     * target, facing away from their attacker so that "behind the facing" would be toward it, drop under 30%: one by an NPC
     * soldier's blow, one by a mock player's. Each ends at least 4 blocks farther from its attacker, both the same way.
     */
    @GameTest(template = FIELD, timeoutTicks = 80, batch = "hero_fm_hop_back")
    public static void hopBackFromABlowGoesAwayFromItForNpcsAndPlayers(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 11, 80);
        try {
            moveBase(h, t.base, 52, 6, 0);
            HeroEntity byNpc = still(sliceHero(t, "natsuho_komezaki", "fx_fm_hop_npc", 12, 6, 20));
            HeroEntity byPl = still(sliceHero(t, "natsuho_komezaki", "fx_fm_hop_pl", 12, 20, 20));
            FactionNpc soldier = HeroAbilityGameTests.foe(t, 8, 6, 100);
            ServerPlayer player = HeroBossGameTests.swordPlayer(t, 8, 20);
            double[] start = new double[2];
            h.runAtTickTime(3, t.guard(() -> {
                HeroEntity[] both = {byNpc, byPl};
                LivingEntity[] by = {soldier, player};
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = both[i];
                    // facing away from its attacker (yaw -90: toward +x), so behind its facing is toward the attacker
                    x.setYRot(-90F);
                    x.setYHeadRot(-90F);
                    x.yBodyRot = -90F;
                    x.setTarget(null);
                    dropTo(x, 0.32);
                    start[i] = x.distanceTo(by[i]);
                    HeroBossGameTests.blow(x, by[i], (float) (x.getMaxHealth() * 0.04));
                }
            }));
            h.runAtTickTime(30, t.guard(() -> {
                HeroEntity[] both = {byNpc, byPl};
                LivingEntity[] by = {soldier, player};
                double[] moved = new double[2];
                for (int i = 0; i < 2; i++) {
                    moved[i] = both[i].distanceTo(by[i]) - start[i];
                    // (a blow's own knock-back is under 3 blocks; the 6-block hop is the rest)
                    t.assertTrue(moved[i] >= 4, both[i].heroId() + " hopped " + moved[i] + " blocks away from its attacker, now at "
                            + where(h, both[i]));
                }
                t.assertTrue(Math.abs(moved[0] - moved[1]) < 1, "not the same hop: " + moved[0] + " / " + moved[1]);
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code escapingHeroLeavesPursuerBehind}, over a wall: two copies of Natsuho (level 20: acrobat III, climber I with 5
     * blocks) are brought under their threshold afield, one by NPC soldiers' blows and one by a mock player's. Their refuge
     * (their home base) lies beyond a 5-high wall whose way round is at its far end. Each escapes over the wall by a climb or
     * a jump-up link (never round by its end, never with the stuck recovery) within 4 s, while the chasing soldiers walk
     * round and are never on the wall; neither is caught. (The blow sets off her Nine Lives, whose hop back lands her at
     * the wall's foot: the climb must start there at once.)
     */
    @GameTest(template = FIELD, timeoutTicks = 600, batch = "hero_fm_esc_wall")
    public static void natsuhoEscapesOverAWallASoldierMustGoRound(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 11, 600);
        try {
            // (her home's walls box plus 16 must not reach her: she stands for her home and would not run there)
            moveBase(h, t.base, 52, 6, 0);
            fill(h, 18, 2, 0, 18, 6, 28, Blocks.STONE_BRICKS);
            // (a head start of 12 blocks: the climb's approach, its crouch and 5 blocks at ladder speed take about 3 s)
            HeroEntity byNpc = sliceHero(t, "natsuho_komezaki", "fx_fm_esc_nat_npc", 14, 6, 20);
            HeroEntity byPl = sliceHero(t, "natsuho_komezaki", "fx_fm_esc_nat_pl", 14, 12, 20);
            for (HeroEntity x : new HeroEntity[]{byNpc, byPl}) {
                x.goalSelector.removeAllGoals(g -> g instanceof com.factionclash.combat.HeroAbilityGoal);
                x.targetSelector.removeAllGoals(g -> true);
            }
            FactionNpc s1 = chaser(t, 2, 3, byNpc), s2 = chaser(t, 2, 6, byNpc);
            ServerPlayer p = HeroBossGameTests.swordPlayer(t, 4, 10);
            int[] crossed = {-1, -1};
            h.runAtTickTime(3, t.guard(() -> {
                for (HeroEntity x : new HeroEntity[]{byNpc, byPl}) {
                    int flee = x.definition().v2().end().fleeAt();
                    dropTo(x, (flee + 1.5) / 100.0);
                }
                HeroBossGameTests.blow(byNpc, s1, (float) (byNpc.getMaxHealth() * 0.04));
                HeroBossGameTests.blow(byPl, p, (float) (byPl.getMaxHealth() * 0.04));
                t.assertTrue(byNpc.combat().boss().escape().running() && byPl.combat().boss().escape().running(),
                        "no escapes: " + byNpc.getHealth() + " / " + byPl.getHealth());
            }));
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 4) {
                    return;
                }
                for (FactionNpc s : new FactionNpc[]{s1, s2}) {
                    double[] r = rel(h, s);
                    t.assertTrue(!(r[0] > 17 && r[0] < 19.5 && r[2] < 28.5 && r[1] > 3.5), "a soldier is on the wall at " + where(h, s));
                    if (s.isAlive()) {
                        s.setTarget(byNpc);
                    }
                }
                HeroEntity[] both = {byNpc, byPl};
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = both[i];
                    double[] r = rel(h, x);
                    t.assertTrue(x.isAlive(), x.heroId() + " was caught at " + where(h, x) + ": " + ("escape " + x.combat().boss().escape().current() + " fleeing " + x.combat().boss().escape().fleeing() + " cornered " + x.combat().boss().escape().cornered() + " last stands " + x.combat().boss().escape().lastStands())
                            + ", " + x.movement().state());
                    // (a climb starts at the wall's foot at once: about 3.5 s over; a hero that jumps at the wall first is late)
                    t.assertTrue(crossed[i] >= 0 || now - 3 <= 80, x.heroId() + " not over 4 s after breaking off: "
                            + HeroNavigationGameTests.heroState(h, x));
                    if (crossed[i] < 0 && r[0] > 19.3) {
                        crossed[i] = now;
                        t.assertTrue(r[2] < 28.5, x.heroId() + " went round by the wall's end: " + where(h, x));
                        t.assertTrue(taken(x, HeroLinks.Kind.CLIMB) + taken(x, HeroLinks.Kind.JUMP_UP) >= 1
                                || x.movement().state() == HeroMovement.State.CLIMB, x.heroId() + " took no link over: "
                                + HeroNavigationGameTests.heroState(h, x));
                        t.assertTrue(x.navigator().recoveries == 0, "a stuck recovery");
                    }
                }
                if (crossed[0] > 0 && crossed[1] > 0) {
                    double[] a = rel(h, s1), b = rel(h, s2);
                    t.assertTrue(a[0] < 19 || a[2] > 28 || b[0] < 19 || b[2] > 28, "the soldiers were over too");
                    FactionClash.LOGGER.info("GAMETEST Natsuho over the wall after {} and {} ticks", crossed[0] - 3, crossed[1] - 3);
                    t.succeed();
                }
            });
            h.runAtTickTime(595, t.guard(() -> t.fail("not over: " + HeroNavigationGameTests.heroState(h, byNpc) + " / "
                    + HeroNavigationGameTests.heroState(h, byPl))));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code escapingHeroLeavesPursuerBehind}, by air: two copies of Ambrose (level 20) brought under their threshold
     * afield, one by an NPC soldier's blow and one by a mock player's, take off at once (no air budget, the travel
     * ceiling) and fly to their refuge across the field; they rise well out of every pursuer's reach, take no blow in the
     * air and the escape ends at the refuge, as for anyone.
     */
    @GameTest(template = FIELD, timeoutTicks = 600, batch = "hero_fm_esc_fly", skyAccess = true)
    public static void ambroseFliesOffFromNpcsAndAPlayer(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 11, 600);
        try {
            moveBase(h, t.base, 28, 28, 1);
            HeroEntity byNpc = sliceHero(t, "ambrose_nettlewick", "fx_fm_esc_amb_npc", 4, 4, 20);
            HeroEntity byPl = sliceHero(t, "ambrose_nettlewick", "fx_fm_esc_amb_pl", 4, 10, 20);
            for (HeroEntity x : new HeroEntity[]{byNpc, byPl}) {
                x.goalSelector.removeAllGoals(g -> g instanceof com.factionclash.combat.HeroAbilityGoal
                        || g instanceof com.factionclash.combat.HeroThrowGoal);
                x.targetSelector.removeAllGoals(g -> true);
            }
            FactionNpc s1 = chaser(t, 2, 3, byNpc);
            ServerPlayer p = HeroBossGameTests.swordPlayer(t, 2, 10);
            double floor = floorY(h);
            double[] high = {0, 0};
            float[] hpAir = {-1, -1};
            boolean[] over = {false, false};
            h.runAtTickTime(3, t.guard(() -> {
                for (HeroEntity x : new HeroEntity[]{byNpc, byPl}) {
                    int flee = x.definition().v2().end().fleeAt();
                    dropTo(x, (flee + 1.5) / 100.0);
                }
                HeroBossGameTests.blow(byNpc, s1, (float) (byNpc.getMaxHealth() * 0.04));
                HeroBossGameTests.blow(byPl, p, (float) (byPl.getMaxHealth() * 0.04));
                t.assertTrue(byNpc.combat().boss().escape().running() && byPl.combat().boss().escape().running(), "no escapes");
            }));
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 4) {
                    return;
                }
                if (s1.isAlive()) {
                    s1.setTarget(byNpc);
                }
                HeroEntity[] both = {byNpc, byPl};
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = both[i];
                    t.assertTrue(x.isAlive(), x.heroId() + " was caught");
                    high[i] = Math.max(high[i], x.getY() - floor);
                    if (hpAir[i] >= 0 && x.getY() - floor > 4 && x.getHealth() < hpAir[i] - 1e-3) {
                        // a blow or a shot from a pursuer that landed this tick, up in the air (not its own landing on some
                        // ledge on the way; a blow during its take-off tell, still on the ground beside the soldier, is fair)
                        net.minecraft.world.damagesource.DamageSource ds = x.getLastDamageSource();
                        t.assertTrue(ds == null || ds.getEntity() != s1 && ds.getEntity() != p, x.heroId() + " was hit in the air: "
                                + (ds == null ? "?" : ds.getMsgId() + " by " + ds.getEntity()) + " at " + where(h, x));
                    }
                    if (x.movement().flying() || hpAir[i] >= 0) {
                        hpAir[i] = x.getHealth(); // its health last tick, from its take-off on
                    }
                    if (!over[i] && !x.combat().boss().escape().running()) {
                        over[i] = true;
                        t.assertTrue(x.travel().flights >= 1, x.heroId() + " never flew: " + HeroNavigationGameTests.heroState(h, x));
                        t.assertTrue(high[i] >= 4, x.heroId() + " rose only " + high[i]);
                        t.assertTrue(t.base.contains(x.blockPosition(), 2), x.heroId() + " ended its escape at " + where(h, x));
                    }
                }
                if (over[0] && over[1]) {
                    FactionClash.LOGGER.info("GAMETEST Ambrose flew off: up to {} and {} blocks", high[0], high[1]);
                    t.succeed();
                }
            });
            h.runAtTickTime(595, t.guard(() -> t.fail("still escaping: " + HeroNavigationGameTests.heroState(h, byNpc) + " / "
                    + HeroNavigationGameTests.heroState(h, byPl))));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code airborneHeroEscapesOrDiesInTheAir}: Ambrose in an aerial window drops under his threshold to a blow (an NPC
     * soldier's for one copy, a mock player's for the other): he escapes from where he is, flying on (no landing first,
     * 7.6), toward his refuge. A last-stand flyer killed in the air falls dead; nothing kneels.
     */
    @GameTest(template = FIELD, timeoutTicks = 200, batch = "hero_fm_air_escape", skyAccess = true)
    public static void airborneHeroEscapesOrDiesInTheAir(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 11, 200);
        try {
            moveBase(h, t.base, 28, 28, 1);
            HeroEntity a = sliceHero(t, "ambrose_nettlewick", "fx_fm_airesc_npc", 4, 4, 20);
            HeroEntity b = sliceHero(t, "ambrose_nettlewick", "fx_fm_airesc_pl", 4, 12, 20);
            String last = "fx_fm_air_last";
            // Ambrose's kit as a last-stand hero (its authored when_losing)
            String lastText = HeroAbilityGameTests.slice("ambrose_nettlewick").replace("\"ambrose_nettlewick\"", "\"" + last + "\"")
                    .replaceFirst("\"personality\": \\{", "\"personality\": {\"when_losing\": \"last_stand\", ");
            HeroEntity maniac = HeroAbilityGameTests.hero(t, last, "staff", lastText, 4, 22);
            maniac.restoreProgress(20, 0, java.util.Map.of());
            for (HeroEntity x : new HeroEntity[]{a, b, maniac}) {
                x.goalSelector.removeAllGoals(g -> g instanceof com.factionclash.combat.HeroAbilityGoal
                        || g instanceof com.factionclash.combat.HeroThrowGoal || g instanceof WorkGoal);
                x.targetSelector.removeAllGoals(g -> true);
            }
            FactionNpc s = HeroAbilityGameTests.foe(t, 6, 4, 100);
            ServerPlayer p = HeroBossGameTests.swordPlayer(t, 6, 12);
            FactionNpc s2 = HeroAbilityGameTests.foe(t, 6, 22, 100);
            int[] escapedAt = {-1, -1};
            h.runAtTickTime(3, t.guard(() -> {
                a.setTarget(s);
                b.setTarget(p);
                maniac.setTarget(s2);
            }));
            h.runAtTickTime(6, t.guard(() -> {
                for (HeroEntity x : new HeroEntity[]{a, b, maniac}) {
                    String no = x.combat().air().aerialTakeOff();
                    t.assertTrue(no == null, x.heroId() + ": " + no);
                }
            }));
            t.onEachTick(() -> {
                int now = (int) h.getTick();
                if (now < 30) {
                    return;
                }
                HeroEntity[] both = {a, b};
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = both[i];
                    if (escapedAt[i] < 0 && x.movement().state() == HeroMovement.State.FLY) {
                        dropTo(x, (x.definition().v2().end().fleeAt() + 1.5) / 100.0);
                        HeroBossGameTests.blow(x, i == 0 ? s : p, (float) (x.getMaxHealth() * 0.04));
                        t.assertTrue(x.combat().boss().escape().running(), x.heroId() + " did not escape");
                        escapedAt[i] = now;
                    }
                    if (escapedAt[i] >= 0 && now - escapedAt[i] <= 20) {
                        HeroMovement.State st = x.movement().state();
                        t.assertTrue(st != HeroMovement.State.LAND && st != HeroMovement.State.LANDING && !x.onGround(),
                                x.heroId() + " came down first: " + st);
                    }
                }
                if (maniac.isAlive() && maniac.movement().state() == HeroMovement.State.FLY && now > 40) {
                    maniac.hurt(maniac.damageSources().mobAttack(s2), 1000F);
                    maniac.setHealth(0);
                    maniac.die(maniac.damageSources().mobAttack(s2));
                }
                if (escapedAt[0] >= 0 && escapedAt[1] >= 0 && now - Math.max(escapedAt[0], escapedAt[1]) > 20 && !maniac.isAlive()) {
                    t.assertTrue(a.travel().flying() && b.travel().flying(), "not flying on: " + a.movement().state() + " / "
                            + b.movement().state() + ", flights ended " + a.travel().lastEnd() + " / " + b.travel().lastEnd()
                            + ", escaping " + a.combat().boss().escape().running() + " / " + b.combat().boss().escape().running());
                    t.assertTrue(!maniac.combat().boss().escape().running() && maniac.combat().boss().escape().escapes() == 0,
                            "a last-stand hero escaped");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
