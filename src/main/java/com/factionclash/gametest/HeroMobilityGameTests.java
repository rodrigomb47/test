package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroMovement;
import com.factionclash.hero.HeroSounds;
import com.factionclash.logic.hero.BossRules;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroClips;
import com.factionclash.logic.hero.LeapSolver;
import com.factionclash.logic.hero.MobilityRules;
import com.factionclash.logic.hero.MovementRules;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import com.factionclash.work.WorkGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Heroes, task A10: the mobility engine (MOBILITY_AND_WORK 1-2, 4.2 and 17.2's ground and air tests for the slice
 * heroes' families). The level-scaled attribute modifiers ({@code mobilityScalesWithLevel}: the table of 15.9 at levels
 * 1, 10 and 20, every feature at level 1), a leap measured against {@link LeapSolver} (the solver's apex, length and
 * ticks are what the entity really does on vanilla's air physics), Kagenori's crane leap with its crouch tell and roll
 * against Benika's heavy landing, Natsuho's air jumps and no fall damage, climbing at the ladder rate and being knocked
 * off by an NPC soldier's blow and a mock player's alike, Tobimaru's wall kicks up a chimney, Yugen's travel bonus and
 * sprint burst and both off in a fight (against an NPC soldier and against a mock player), Benika in a cobweb, Hinae
 * swimming, Ilsabet in powder snow, Ambrose's broom flight with its stamina, its forced landing and its exhaustion,
 * gliding down a cliff, air poise from an NPC archer's and a mock player's arrows and blows the same, the downing it
 * ends in, a stagger in the air that is a downing, the fight's air budget and ceiling counted from an NPC soldier and a
 * mock player alike, and workers that move as before. The tests run in their own 32 x 24 x 32 field
 * ({@code mobility_field}): heroes leap, climb and fly higher than a platform's 8 blocks. Each test that runs a hero has
 * a batch of its own; the heroes' jobs are taken away so nothing but the engine moves them.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroMobilityGameTests {
    private HeroMobilityGameTests() {
    }

    static final String FIELD = "mobility_field";
    private static final FactionType SIDE = FactionType.EMBER_LEGION, FOE = FactionType.GROVE_COVENANT;

    // ------------------------------------------------------------------ fixtures

    /**
     * A slice hero's fixture as {@code as} (its own id when they are equal), at relative (x, 2, z), at {@code level},
     * with no job (nothing but the mobility engine and its fight move it).
     */
    static HeroEntity sliceHero(GameTestBases.Handle t, String slice, String as, int x, int z, int level) {
        String text = HeroAbilityGameTests.slice(slice);
        if (!as.equals(slice)) {
            text = text.replace("\"" + slice + "\"", "\"" + as + "\"");
        }
        String style = switch (slice) {
            case "kagenori_tsurumine", "tobimaru_usugumo" -> "katana";
            case "ambrose_nettlewick", "hinae_shirasuzu" -> "staff";
            case "benika_akagane" -> "hammer";
            case "ilsabet_frostmere" -> "sword_shield";
            default -> "fists";
        };
        HeroEntity hero = HeroAbilityGameTests.hero(t, as, style, text, x, z);
        hero.goalSelector.removeAllGoals(g -> g instanceof WorkGoal);
        hero.restoreProgress(level, 0, Map.of());
        HeroAbilityGameTests.noRegen(hero);
        return hero;
    }

    /**
     * Only the engine and the test move the hero: every goal but the one that holds it while an action runs is taken
     * away (no walking, no target of its own, no moves), its AI and physics stay on.
     */
    static HeroEntity still(HeroEntity hero) {
        hero.goalSelector.removeAllGoals(g -> !(g instanceof com.factionclash.combat.HeroHoldGoal));
        hero.targetSelector.removeAllGoals(g -> true);
        return hero;
    }

    /** An NPC soldier of the foe at relative (x, y, z) with its AI on and no goals (only what is done to it moves it). */
    static FactionNpc bareNpc(GameTestBases.Handle t, int x, int y, int z) {
        FactionNpc n = t.track(t.helper.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(x, y, z)));
        n.faction(FOE);
        n.role(NpcRole.SWORDSMAN);
        n.goalSelector.removeAllGoals(g -> true);
        n.targetSelector.removeAllGoals(g -> true);
        return n;
    }

    static String mode(HeroEntity h) {
        return HeroActions.LOCO_MODES.get(HeroActions.locoMode(h.loco()));
    }

    /** The one-shot clip {@code DATA_LOCO} names now (null: none). */
    static String oneShot(HeroEntity h) {
        int l = h.loco();
        return HeroClips.oneShotClip(HeroActions.locoMode(l), HeroActions.locoOneShot(l));
    }

    static double floorY(GameTestHelper h) {
        return h.absolutePos(new BlockPos(0, 2, 0)).getY();
    }

    static Vec3 at(GameTestHelper h, double x, double y, double z) {
        BlockPos o = h.absolutePos(new BlockPos(0, 0, 0));
        return new Vec3(o.getX() + x, o.getY() + y, o.getZ() + z);
    }

    static void column(GameTestHelper h, int x, int z, int from, int to) {
        for (int y = from; y <= to; y++) {
            h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
    }

    static AttributeModifier modifier(LivingEntity e, Holder<Attribute> a, String id) {
        AttributeInstance inst = e.getAttribute(a);
        return inst == null ? null : inst.getModifier(ResourceLocation.parse(id));
    }

    static void near(GameTestBases.Handle t, double expected, double actual, double eps, String what) {
        t.assertTrue(Math.abs(expected - actual) <= eps, what + ": expected " + expected + ", got " + actual);
    }

    static Arrow shoot(LivingEntity from, LivingEntity at) {
        Arrow a = new Arrow(from.level(), from, new ItemStack(Items.ARROW), null);
        Vec3 o = from.getEyePosition();
        a.moveTo(o.x, o.y, o.z, 0F, 0F);
        Vec3 d = at.getBoundingBox().getCenter().subtract(o);
        a.shoot(d.x, d.y, d.z, 3.0F, 0F);
        from.level().addFreshEntity(a);
        return a;
    }

    // ------------------------------------------------------------------ leaps: the real arc is the solver's (1.2, 1.6, 3.3)

    /**
     * {@code leapMatchesLeapSolver}: Kagenori at level 20 (leaper II: a 6-block gap, a 2.5-block jump) leaps a 5-block
     * ditch. Before the impulse he crouches 0.3 s without moving ({@code move.crouch_jump}, the tell); the arc's highest
     * point, its length and its ticks in the air are the ones {@link LeapSolver} worked out from the same blocks (within
     * 0.03 blocks and a tick), he lands on the target cell and rolls ({@code move.land_roll}, leaper II). Then vanilla's
     * own jump with his jump strength (0.62) rises the 2.52 blocks the solver's apex gives.
     */
    @GameTest(template = FIELD, timeoutTicks = 200, batch = "hero_mob_leap")
    public static void leapMatchesLeapSolver(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        try {
            for (int x = 7; x <= 11; x++) {
                for (int z = 22; z <= 24; z++) {
                    h.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                    h.setBlock(new BlockPos(x, 0, z), Blocks.AIR);
                }
            }
            HeroEntity kage = sliceHero(t, "kagenori_tsurumine", "fx_mob_leap", 6, 23, 20);
            BlockPos start = h.absolutePos(new BlockPos(6, 2, 23)), target = h.absolutePos(new BlockPos(12, 2, 23));
            double[] startX = {0}, maxY = {-1e9};
            int[] tellAt = {-1}, leftAt = {-1}, jumpAt = {-1};
            double[] jumpTop = {-1e9}, jumpFrom = {0};
            h.runAtTickTime(4, t.guard(() -> {
                t.assertTrue(kage.onGround(), "Kagenori is not standing");
                t.assertTrue(Math.abs(kage.getAttributeValue(Attributes.JUMP_STRENGTH) - LeapSolver.attributeFor(2.5)) < 1e-6,
                        "jump strength " + kage.getAttributeValue(Attributes.JUMP_STRENGTH));
                String no = kage.movement().leapTo(target);
                t.assertTrue(no == null, "no leap: " + no);
                tellAt[0] = (int) (h.getTick());
                startX[0] = kage.getX();
            }));
            t.onEachTick(() -> {
                if (tellAt[0] < 0) {
                    return;
                }
                long since = h.getTick() - tellAt[0];
                HeroMovement m = kage.movement();
                if (since >= 1 && since <= HeroMovement.TELL_TICKS - 1) {
                    t.assertTrue(m.state() == HeroMovement.State.TELL, "the tell: " + m.state() + " at +" + since);
                    t.assertTrue(Math.abs(kage.getX() - startX[0]) < 1e-6, "he moved during the tell");
                    t.assertTrue("move.crouch_jump".equals(oneShot(kage)), "the tell shows " + oneShot(kage));
                }
                if (m.state() == HeroMovement.State.LEAP) {
                    if (leftAt[0] < 0) {
                        leftAt[0] = (int) (h.getTick());
                    }
                    maxY[0] = Math.max(maxY[0], kage.getY());
                    t.assertTrue(mode(kage).equals("airborne"), "in the air: " + mode(kage));
                }
                HeroMovement.LeapResult r = m.lastLeap();
                if (r != null && jumpAt[0] < 0) {
                    t.assertTrue(r.refusal() == null && r.landed() && r.onTarget(), "the leap: " + r);
                    LeapSolver.Leap plan = r.plan();
                    near(t, plan.apex(), maxY[0] - start.getY(), 0.03, "the apex against the solver's");
                    near(t, plan.landingX(), r.along(), 0.03, "the length against the solver's");
                    t.assertTrue(Math.abs(r.ticks() - plan.ticks()) <= 1, "ticks in the air " + r.ticks() + ", the solver's " + plan.ticks());
                    t.assertTrue("move.land_roll".equals(oneShot(kage)), "a leaper II rolls: " + oneShot(kage));
                    t.assertTrue(modifier(kage, Attributes.MOVEMENT_SPEED, FactionClash.MODID + ":mobility_landing") == null,
                            "the roll has no landing slowdown");
                    jumpAt[0] = (int) (h.getTick() + 15);
                }
                if (jumpAt[0] >= 0 && h.getTick() == jumpAt[0]) {
                    t.assertTrue(kage.onGround(), "not standing for the jump");
                    jumpFrom[0] = kage.getY();
                    kage.getJumpControl().jump(); // vanilla's own jump, with his jump strength
                }
                if (jumpAt[0] >= 0 && h.getTick() > jumpAt[0]) {
                    jumpTop[0] = Math.max(jumpTop[0], kage.getY());
                    if (h.getTick() > jumpAt[0] + 3 && kage.onGround()) {
                        near(t, LeapSolver.apex(LeapSolver.attributeFor(2.5)), jumpTop[0] - jumpFrom[0], 0.03, "vanilla's jump");
                        t.succeed();
                    }
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code mobilityScalesWithLevel}: the same fixture acrobat III (Natsuho) and leaper II (Kagenori) at levels 1, 10 and
     * 20: jump strength, air-jump height, gap and climb link widths and safe fall equal 15.9, and at level 1 every feature
     * is there (no unlock on level-up, only numbers): Natsuho makes both air jumps (reaching 2.9 blocks; a third is
     * refused) and Kagenori rolls out of a leap.
     */
    @GameTest(template = FIELD, timeoutTicks = 220, batch = "hero_mob_levels")
    public static void mobilityScalesWithLevel(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 220);
        try {
            int[] levels = {1, 10, 20};
            HeroEntity[] cats = new HeroEntity[3], samurai = new HeroEntity[3];
            for (int i = 0; i < 3; i++) {
                cats[i] = sliceHero(t, "natsuho_komezaki", "fx_mob_nat" + levels[i], 6 + i * 8, 6, levels[i]);
                samurai[i] = sliceHero(t, "kagenori_tsurumine", "fx_mob_kag" + levels[i], 6 + i * 8, 14, levels[i]);
            }
            double[] natJump = {1.40, 1.45, 1.50}, natAir = {0.75, 0.99, 1.25}, kagJump = {2.00, 2.24, 2.50};
            int[] gaps = {3, 4, 6}, climbs = {3, 4, 6};
            double[] kagFall = {7.2, 8.5, 10.0};
            for (int i = 0; i < 3; i++) {
                MobilityRules.Profile n = cats[i].movement().profile(), k = samurai[i].movement().profile();
                String at = " at level " + levels[i];
                near(t, natJump[i], n.jumpHeight(), 0.005, "Natsuho's jump" + at);
                near(t, LeapSolver.attributeFor(natJump[i]), cats[i].getAttributeValue(Attributes.JUMP_STRENGTH), 1e-6,
                        "Natsuho's jump strength" + at);
                near(t, natAir[i], n.airJumpHeight(), 0.005, "Natsuho's air jump" + at);
                t.assertTrue(n.gap() == gaps[i] && n.climbHeight() == climbs[i], "Natsuho's gap " + n.gap() + " and climb "
                        + n.climbHeight() + at);
                near(t, 0, cats[i].getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 1e-6, "Natsuho's fall damage" + at);
                t.assertTrue(n.count(MobilityRules.Count.AIR_JUMPS) == 2 && n.has(MobilityRules.Feature.WALL_KICK)
                        && n.has(MobilityRules.Feature.MANTLE) && n.has(MobilityRules.Feature.LANDING_ROLL), "Natsuho's features" + at);
                near(t, kagJump[i], k.jumpHeight(), 0.005, "Kagenori's jump" + at);
                near(t, LeapSolver.attributeFor(kagJump[i]), samurai[i].getAttributeValue(Attributes.JUMP_STRENGTH), 1e-6,
                        "Kagenori's jump strength" + at);
                t.assertTrue(k.gap() == gaps[i], "Kagenori's gap " + k.gap() + at);
                near(t, kagFall[i], samurai[i].getAttributeValue(Attributes.SAFE_FALL_DISTANCE), 0.05, "Kagenori's safe fall" + at);
                t.assertTrue(k.has(MobilityRules.Feature.LANDING_ROLL), "Kagenori's roll" + at);
                near(t, MobilityRules.WORKER_STEP, samurai[i].getAttributeValue(Attributes.STEP_HEIGHT), 1e-6,
                        "Kagenori (no step feature) steps as a worker" + at);
                near(t, MobilityRules.FULL_STEP, cats[i].getAttributeValue(Attributes.STEP_HEIGHT), 1e-6, "Natsuho steps 1.0" + at);
            }
            HeroEntity cat = cats[0], kage = samurai[0];
            double[] base = {0}, top = {-1e9};
            int[] jumps = {0};
            boolean[] refused = {false}, rolled = {false};
            h.runAtTickTime(4, t.guard(() -> {
                base[0] = cat.getY();
                t.assertTrue(cat.movement().jump() == null, "Natsuho does not jump");
                BlockPos to = kage.blockPosition().east(4);
                t.assertTrue(kage.movement().leapTo(to) == null, "Kagenori at level 1 does not leap 3 blocks");
            }));
            t.onEachTick(() -> {
                if (base[0] == 0) {
                    return;
                }
                top[0] = Math.max(top[0], cat.getY());
                if (!cat.onGround() && cat.getDeltaMovement().y <= 0 && jumps[0] < 2) {
                    String no = cat.movement().airJump();
                    t.assertTrue(no == null, "air jump " + (jumps[0] + 1) + " at level 1: " + no);
                    jumps[0]++;
                } else if (jumps[0] == 2 && !refused[0] && !cat.onGround() && cat.getDeltaMovement().y <= 0) {
                    refused[0] = cat.movement().airJump() != null;
                    t.assertTrue(refused[0], "a third air jump");
                }
                rolled[0] |= "move.land_roll".equals(oneShot(kage));
                if (refused[0] && cat.onGround() && rolled[0]) {
                    near(t, 1.40 + 0.75 + 0.75, top[0] - base[0], 0.12, "the reach with both air jumps at level 1");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code kagenoriCraneLeapAndBenikasHeavyLanding}: at level 1 both leap a 3-block gap; Kagenori (leaper II) rolls
     * out of it with no slowdown, Benika (leaper I) lands heavily ({@code move.land_heavy}) and is slowed for 0.5 s; both
     * play the take-off tell first and land on their target cells.
     */
    @GameTest(template = FIELD, timeoutTicks = 120, batch = "hero_mob_crane")
    public static void kagenoriCraneLeapAndBenikasHeavyLanding(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            for (int x = 7; x <= 9; x++) {
                h.setBlock(new BlockPos(x, 1, 20), Blocks.AIR);
                h.setBlock(new BlockPos(x, 1, 26), Blocks.AIR);
            }
            HeroEntity kage = sliceHero(t, "kagenori_tsurumine", "fx_mob_crane", 6, 20, 1);
            HeroEntity benika = sliceHero(t, "benika_akagane", "fx_mob_oni", 6, 26, 1);
            still(kage);
            still(benika);
            boolean[] tell = {false, false}, roll = {false}, heavy = {false}, slowed = {false};
            h.runAtTickTime(4, t.guard(() -> {
                t.assertTrue(kage.movement().leapTo(kage.blockPosition().east(4)) == null, "Kagenori");
                t.assertTrue(benika.movement().leapTo(benika.blockPosition().east(4)) == null, "Benika");
            }));
            t.onEachTick(() -> {
                tell[0] |= "move.crouch_jump".equals(oneShot(kage));
                tell[1] |= "move.crouch_jump".equals(oneShot(benika));
                roll[0] |= "move.land_roll".equals(oneShot(kage));
                heavy[0] |= "move.land_heavy".equals(oneShot(benika));
                slowed[0] |= modifier(benika, Attributes.MOVEMENT_SPEED, FactionClash.MODID + ":mobility_landing") != null;
                t.assertTrue(modifier(kage, Attributes.MOVEMENT_SPEED, FactionClash.MODID + ":mobility_landing") == null,
                        "Kagenori's roll has no slowdown");
                HeroMovement.LeapResult a = kage.movement().lastLeap(), b = benika.movement().lastLeap();
                if (a != null && b != null && roll[0] && heavy[0] && slowed[0]) {
                    t.assertTrue(a.onTarget() && b.onTarget(), "the leaps: " + a + " / " + b);
                    t.assertTrue(tell[0] && tell[1], "a leap starts with its crouch tell");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ acrobats (1.7)

    /**
     * {@code acrobatAirJumpNoFallDamage}: Natsuho jumps off a 10-block tower, makes her two air jumps and drops 12
     * blocks to the floor unhurt (acrobat III: no fall damage, the vanilla cat rule), rolling as she lands; an NPC
     * soldier pushed off the same height is hurt.
     */
    @GameTest(template = FIELD, timeoutTicks = 160, batch = "hero_mob_acrobat")
    public static void acrobatAirJumpNoFallDamage(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            column(h, 6, 20, 2, 11);
            column(h, 20, 20, 2, 11);
            HeroEntity cat = still(sliceHero(t, "natsuho_komezaki", "fx_mob_drop", 8, 8, 1));
            FactionNpc soldier = bareNpc(t, 20, 12, 20);
            HeroAbilityGameTests.noRegen(soldier);
            Vec3 top = at(h, 6.5, 12, 20.5);
            cat.moveTo(top.x, top.y, top.z, 0F, 0F);
            float[] hp = {0, 0};
            int[] air = {0};
            boolean[] started = {false}, rolled = {false};
            h.runAtTickTime(4, t.guard(() -> {
                hp[0] = cat.getHealth();
                hp[1] = soldier.getHealth();
                t.assertTrue(cat.movement().jump() == null, "no jump from the tower");
                cat.setDeltaMovement(0.25, cat.getDeltaMovement().y, 0);
                started[0] = true;
            }));
            t.onEachTick(() -> {
                if (!started[0]) {
                    return;
                }
                if (h.getTick() < 10) {
                    soldier.setDeltaMovement(0.3, soldier.getDeltaMovement().y, 0);
                }
                if (!cat.onGround() && cat.getDeltaMovement().y <= 0 && air[0] < 2 && cat.getY() > top.y - 1) {
                    t.assertTrue(cat.movement().airJump() == null, "air jump " + (air[0] + 1));
                    air[0]++;
                }
                rolled[0] |= "move.land_roll".equals(oneShot(cat));
                // (the engine sees the landing on her next tick)
                if (h.getTick() > 20 && rolled[0] && cat.onGround() && cat.getY() < top.y - 8 && soldier.onGround() && soldier.getY() < top.y - 8) {
                    t.assertTrue(air[0] == 2, "both air jumps: " + air[0]);
                    t.assertTrue(cat.getHealth() == hp[0], "Natsuho was hurt by the fall: " + (hp[0] - cat.getHealth()));
                    t.assertTrue(soldier.getHealth() < hp[1], "the soldier took no fall damage from 10 blocks");
                    t.assertTrue(rolled[0], "she rolls as she lands");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code tobimaruWallKicksUpAChimney}: Tobimaru at level 20 in a 1x1 shaft kicks from wall to wall (never off the
     * same wall twice in a row) and climbs it higher than his jump and both air jumps reach (4.0 blocks).
     */
    @GameTest(template = FIELD, timeoutTicks = 160, batch = "hero_mob_chimney")
    public static void tobimaruWallKicksUpAChimney(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            int cx = 20, cz = 20;
            for (int y = 2; y <= 12; y++) {
                h.setBlock(new BlockPos(cx - 1, y, cz), Blocks.STONE);
                h.setBlock(new BlockPos(cx + 1, y, cz), Blocks.STONE);
                h.setBlock(new BlockPos(cx, y, cz - 1), Blocks.STONE);
                h.setBlock(new BlockPos(cx, y, cz + 1), Blocks.STONE);
            }
            HeroEntity ninja = still(sliceHero(t, "tobimaru_usugumo", "fx_mob_chimney", 8, 8, 20));
            Vec3 in = at(h, cx + 0.5, 2, cz + 0.5);
            ninja.moveTo(in.x, in.y, in.z, 0F, 0F);
            double[] base = {0}, top = {-1e9};
            int[] kicks = {0};
            boolean[] sameRefused = {false};
            h.runAtTickTime(5, t.guard(() -> {
                base[0] = ninja.getY();
                t.assertTrue(ninja.movement().jump() == null, "no jump in the shaft");
                ninja.setDeltaMovement(-0.15, ninja.getDeltaMovement().y, 0); // toward the west wall
            }));
            t.onEachTick(() -> {
                if (base[0] == 0) {
                    return;
                }
                top[0] = Math.max(top[0], ninja.getY());
                if (!ninja.onGround() && ninja.getDeltaMovement().y <= 0.02 && ninja.movement().touchingWall() != null) {
                    String no = ninja.movement().wallKick();
                    if (no == null) {
                        kicks[0]++;
                        if (!sameRefused[0]) {
                            String again = ninja.movement().wallKick();
                            sameRefused[0] = again != null;
                        }
                    }
                }
                if (top[0] - base[0] > 5.0) {
                    t.assertTrue(kicks[0] >= 3, "wall kicks: " + kicks[0]);
                    t.assertTrue(sameRefused[0], "a second kick at once (off the wall it just left) was not refused");
                    t.assertTrue("cling".equals(mode(ninja)) || "airborne".equals(mode(ninja)), "mode " + mode(ninja));
                    t.succeed();
                }
            });
            h.runAtTickTime(150, t.guard(() -> t.fail("Tobimaru reached " + (top[0] - base[0]) + " blocks with " + kicks[0] + " kicks")));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ climbing (1.8, 3.4)

    /**
     * {@code climberScalesWallAndIsKnockedOff}: Tobimaru (climber II, 9 blocks at level 1) climbs a 5-high base wall at
     * vanilla's ladder rate (0.1176 a tick, 2.35 blocks a second) and steps onto its top; Natsuho (climber I, 3 blocks at
     * level 1) climbs a 6-high wall to her limit and clings there still. An NPC soldier's blow knocks her off (she lets
     * go and cannot grip for 0.5 s), and so does a mock player's blow, and a knock-back of 0.5 from anyone.
     */
    @GameTest(template = FIELD, timeoutTicks = 260, batch = "hero_mob_climb")
    public static void climberScalesWallAndIsKnockedOff(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 260);
        try {
            for (int z = 4; z <= 8; z++) {
                column(h, 9, z, 2, 6); // a 5-high wall
            }
            for (int z = 18; z <= 22; z++) {
                column(h, 9, z, 2, 7); // a 6-high wall
            }
            HeroEntity ninja = still(sliceHero(t, "tobimaru_usugumo", "fx_mob_climb", 8, 6, 1));
            HeroEntity cat = still(sliceHero(t, "natsuho_komezaki", "fx_mob_cling", 8, 20, 1));
            FactionNpc soldier = HeroAbilityGameTests.foe(t, 6, 20, 100);
            soldier.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
            ServerPlayer player = HeroAbilityGameTests.player(t, 6, 22, FOE, 100);
            double floor = floorY(h);
            List<Double> rise = new ArrayList<>();
            double[] lastY = {0};
            int[] stage = {0};
            int[] stageAt = {0};
            h.runAtTickTime(4, t.guard(() -> {
                t.assertTrue(ninja.movement().climb(Direction.EAST) == null, "Tobimaru does not climb");
                t.assertTrue(cat.movement().climb(Direction.EAST) == null, "Natsuho does not climb");
                lastY[0] = ninja.getY();
            }));
            t.onEachTick(() -> {
                if (h.getTick() <= 4) {
                    return;
                }
                if (ninja.movement().state() == HeroMovement.State.CLIMB && !ninja.onGround()) {
                    rise.add(ninja.getY() - lastY[0]);
                    t.assertTrue(mode(ninja).equals("climb") && ninja.onClimbable(), "climbing shows " + mode(ninja));
                }
                lastY[0] = ninja.getY();
                HeroMovement cm = cat.movement();
                switch (stage[0]) {
                    case 0 -> {
                        if (cm.clinging()) {
                            near(t, floor + 3, cat.getY(), 0.15, "Natsuho clings at her climb height");
                            t.assertTrue(cat.isNoGravity() && "cling".equals(mode(cat)), "clinging: " + mode(cat));
                            stage[0] = 1;
                            stageAt[0] = (int) (h.getTick());
                        }
                    }
                    case 1 -> {
                        if (h.getTick() - stageAt[0] == 10) {
                            near(t, floor + 3, cat.getY(), 0.15, "a cling holds still");
                            HeroBossGameTests.blow(cat, soldier, 1);
                            t.assertTrue(cm.state() == HeroMovement.State.NONE && !cat.isNoGravity(), "a soldier's blow: " + cm.state());
                            t.assertTrue(cm.climb(Direction.EAST) != null, "she gripped again at once");
                            stage[0] = 2;
                            stageAt[0] = (int) (h.getTick());
                        }
                    }
                    case 2 -> {
                        if (h.getTick() - stageAt[0] >= HeroMovement.GRIP_AFTER + 2 && cat.onGround()) {
                            t.assertTrue(cm.climb(Direction.EAST) == null, "Natsuho does not climb again");
                            stage[0] = 3;
                        }
                    }
                    case 3 -> {
                        if (cm.clinging()) {
                            HeroBossGameTests.blow(cat, player, 1);
                            t.assertTrue(cm.state() == HeroMovement.State.NONE, "a player's blow: " + cm.state());
                            stage[0] = 4;
                            stageAt[0] = (int) (h.getTick());
                        }
                    }
                    case 4 -> {
                        if (h.getTick() - stageAt[0] >= HeroMovement.GRIP_AFTER + 2 && cat.onGround()) {
                            t.assertTrue(cm.climb(Direction.EAST) == null, "Natsuho does not climb a third time");
                            stage[0] = 5;
                        }
                    }
                    case 5 -> {
                        if (cm.clinging()) {
                            cat.knockback(0.6, -1, 0);
                            t.assertTrue(cm.state() == HeroMovement.State.NONE, "a knock-back of 0.6: " + cm.state());
                            stage[0] = 6;
                        }
                    }
                    default -> {
                        if (ninja.movement().state() == HeroMovement.State.NONE && ninja.onGround() && ninja.getY() > floor + 4.9) {
                            double sum = 0;
                            int n = 0;
                            for (int i = 2; i < rise.size() - 2; i++) {
                                sum += rise.get(i);
                                n++;
                            }
                            t.assertTrue(n >= 20, "Tobimaru climbed only " + n + " ticks");
                            near(t, MobilityRules.CLIMB_UP_SPEED / 20, sum / n, 0.006, "the climb rate (blocks a tick)");
                            t.succeed();
                        }
                    }
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ runners and chargers (1.4, 1.5)

    /**
     * {@code yugenSprintsWhileTravellingNotInAFight}: Yugen (runner III, level 1) travelling down a straight 24-block lane
     * runs with his travel bonus (+12%) and starts his sprint burst (+24%, {@code move.sprint}); both are off at once when
     * a fight starts, against an NPC soldier as against a mock player, and his step height (not a travel bonus) stays.
     */
    @GameTest(template = FIELD, timeoutTicks = 160, batch = "hero_mob_runner")
    public static void yugenSprintsWhileTravellingNotInAFight(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            HeroEntity a = sliceHero(t, "yugen_kagaribi", "fx_mob_fox_a", 2, 8, 1);
            HeroEntity b = sliceHero(t, "yugen_kagaribi", "fx_mob_fox_b", 2, 24, 1);
            String runner = FactionClash.MODID + ":mobility_runner", burst = FactionClash.MODID + ":mobility_burst";
            boolean[] sprinted = {false, false};
            boolean[] fought = {false};
            h.runAtTickTime(3, t.guard(() -> {
                Vec3 ea = at(h, 29.5, 2, 8.5), eb = at(h, 29.5, 2, 24.5);
                t.assertTrue(a.getNavigation().moveTo(ea.x, ea.y, ea.z, 1.0), "no path for Yugen A");
                t.assertTrue(b.getNavigation().moveTo(eb.x, eb.y, eb.z, 1.0), "no path for Yugen B");
            }));
            t.onEachTick(() -> {
                if (h.getTick() < 12 || fought[0]) {
                    return;
                }
                for (HeroEntity y : List.of(a, b)) {
                    if (!y.getNavigation().isDone()) {
                        AttributeModifier m = modifier(y, Attributes.MOVEMENT_SPEED, runner);
                        t.assertTrue(m != null && Math.abs(m.amount() - 0.12) < 1e-4, "his travel bonus " + m);
                    }
                }
                sprinted[0] |= a.movement().bursting() && "sprint".equals(mode(a))
                        && modifier(a, Attributes.MOVEMENT_SPEED, burst) != null
                        && Math.abs(modifier(a, Attributes.MOVEMENT_SPEED, burst).amount() - 0.24) < 1e-4;
                sprinted[1] |= b.movement().bursting() && "sprint".equals(mode(b));
                if (sprinted[0] && sprinted[1]) {
                    fought[0] = true;
                    // the enemies come only now, so nothing but the test starts the fight
                    FactionNpc soldier = HeroAbilityGameTests.foe(t, 16, 12, 100);
                    ServerPlayer player = HeroAbilityGameTests.player(t, 16, 20, FOE, 100);
                    a.setTarget(soldier);
                    b.setTarget(player);
                    h.runAfterDelay(2, t.guard(() -> {
                        for (HeroEntity y : List.of(a, b)) {
                            t.assertTrue(y.combat().boss().fighting(), "no fight for " + y.heroId());
                            t.assertTrue(modifier(y, Attributes.MOVEMENT_SPEED, runner) == null, y.heroId() + " keeps his travel bonus");
                            t.assertTrue(modifier(y, Attributes.MOVEMENT_SPEED, burst) == null && !y.movement().bursting(),
                                    y.heroId() + " keeps his sprint");
                            near(t, MobilityRules.FULL_STEP, y.getAttributeValue(Attributes.STEP_HEIGHT), 1e-6, "his step height");
                        }
                        t.succeed();
                    }));
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code chargerIgnoresCobweb}: Benika (charger III, from level 1) pushed through a row of cobwebs keeps her pace; an
     * NPC soldier pushed the same way through the same row is slowed to a quarter.
     */
    @GameTest(template = FIELD, timeoutTicks = 80, batch = "hero_mob_web")
    public static void chargerIgnoresCobweb(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 80);
        try {
            for (int x = 4; x <= 24; x++) {
                h.setBlock(new BlockPos(x, 2, 20), Blocks.COBWEB);
                h.setBlock(new BlockPos(x, 2, 26), Blocks.COBWEB);
            }
            HeroEntity benika = still(sliceHero(t, "benika_akagane", "fx_mob_web", 5, 20, 1));
            FactionNpc soldier = bareNpc(t, 5, 2, 26);
            double[] from = {0, 0};
            h.runAtTickTime(5, t.guard(() -> {
                from[0] = benika.getX();
                from[1] = soldier.getX();
            }));
            t.onEachTick(() -> {
                if (h.getTick() >= 5 && h.getTick() < 45) {
                    benika.setDeltaMovement(0.15, benika.getDeltaMovement().y, 0);
                    soldier.setDeltaMovement(0.15, soldier.getDeltaMovement().y, 0);
                }
                if (h.getTick() == 50) {
                    double hero = benika.getX() - from[0], npc = soldier.getX() - from[1];
                    t.assertTrue(hero > 3 && hero > npc * 3, "Benika moved " + hero + " blocks, the soldier " + npc);
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ water and snow (1.12, 1.13)

    /**
     * {@code swimmerCeilingOneSwimsFasterAndSnowstrideNeverFreezes}: Hinae (swimmer I, level 1) has swim speed x1.3,
     * water movement efficiency 0.2 and an oxygen bonus of 1.2, and wades across shallow water much faster than with her
     * mobility switched off ({@code heroes.mobility.enabled}); Ilsabet (snowstride I) stands in powder snow and never
     * freezes while an NPC soldier beside her starts freezing.
     */
    @GameTest(template = FIELD, timeoutTicks = 200, batch = "hero_mob_water")
    public static void swimmerCeilingOneSwimsFasterAndSnowstrideNeverFreezes(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        boolean was = FCConfig.MOBILITY_ENABLED.get();
        t.onCleanup(() -> FCConfig.MOBILITY_ENABLED.set(was));
        try {
            for (int x = 3; x <= 30; x++) {
                h.setBlock(new BlockPos(x, 2, 23), Blocks.STONE);
                h.setBlock(new BlockPos(x, 2, 27), Blocks.STONE);
                for (int z = 24; z <= 26; z++) {
                    h.setBlock(new BlockPos(x, 2, z), x == 3 || x == 30 ? Blocks.STONE : Blocks.WATER);
                }
            }
            for (int z = 12; z <= 18; z += 6) {
                h.setBlock(new BlockPos(6, 2, z), Blocks.POWDER_SNOW);
                h.setBlock(new BlockPos(6, 3, z), Blocks.POWDER_SNOW);
            }
            HeroEntity hinae = still(sliceHero(t, "hinae_shirasuzu", "fx_mob_swim", 5, 25, 1));
            HeroEntity ilsabet = still(sliceHero(t, "ilsabet_frostmere", "fx_mob_snow", 6, 12, 1));
            FactionNpc soldier = bareNpc(t, 6, 2, 18);
            // 15.9 at level 1 (rounded there): x1.30, 0.20 (0.198), 1.2
            near(t, 1.3, hinae.getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED), 1e-4, "swim speed");
            near(t, 0.198, hinae.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY), 1e-4, "water movement efficiency");
            near(t, 1.2, hinae.getAttributeValue(Attributes.OXYGEN_BONUS), 1e-4, "oxygen bonus");
            Vec3 start = at(h, 5.5, 2, 25.5), end = at(h, 29.5, 2, 25.5);
            double[] x0 = {0, 0}, speed = {0, 0};
            t.onEachTick(() -> {
                long k = (int) (h.getTick());
                hinae.getMoveControl().setWantedPosition(end.x, end.y, end.z, 1.0);
                if (k == 20) {
                    x0[0] = hinae.getX();
                } else if (k == 49) {
                    // (before the config switch below, which every hero reads)
                    t.assertTrue(ilsabet.isInPowderSnow && soldier.isInPowderSnow, "not in powder snow");
                    t.assertTrue(ilsabet.getTicksFrozen() == 0, "Ilsabet froze: " + ilsabet.getTicksFrozen());
                    t.assertTrue(soldier.getTicksFrozen() > 0, "the soldier does not freeze");
                } else if (k == 50) {
                    speed[0] = (hinae.getX() - x0[0]) / 30;
                    FCConfig.MOBILITY_ENABLED.set(false);
                    hinae.movement().refresh();
                    hinae.moveTo(start.x, start.y, start.z, 0F, 0F);
                    hinae.setDeltaMovement(Vec3.ZERO);
                } else if (k == 70) {
                    t.assertTrue(Math.abs(hinae.getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED) - 1.0) < 1e-6,
                            "mobility off keeps her swim speed");
                    x0[1] = hinae.getX();
                } else if (k == 100) {
                    speed[1] = (hinae.getX() - x0[1]) / 30;
                    FCConfig.MOBILITY_ENABLED.set(was);
                    hinae.movement().refresh();
                    t.assertTrue(speed[0] > speed[1] * 1.5, "Hinae wades at " + speed[0] + " a tick, " + speed[1] + " without her mobility");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ flight (2.1, 2.2)

    /**
     * {@code ambroseBroomFlightStaminaAndLanding}: Ambrose (flyer II on his staff-broom, level 1) takes off with the
     * 0.6 s tell held still ({@code move.takeoff} in the ride mode, {@code cues.takeoff} and his own broom take-off),
     * rises about 1.5 blocks and flies to a point on his broom (the ride mode, no gravity) while his stamina drains; under
     * 25% he heads down and lands with no fall damage ({@code move.fly_land}, {@code cues.landing}), tired, and cannot
     * take off again under his 42%; flying out of stamina he glides down exhausted, pants on the ground
     * ({@code move.winded}) and cannot take off until 50%.
     */
    @GameTest(template = FIELD, timeoutTicks = 420, batch = "hero_mob_flight")
    public static void ambroseBroomFlightStaminaAndLanding(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 420);
        HeroSounds.Recorder rec = HeroSounds.record();
        t.onCleanup(rec::close);
        try {
            HeroEntity amb = still(sliceHero(t, "ambrose_nettlewick", "ambrose_nettlewick", 8, 20, 1));
            HeroMovement m = amb.movement();
            double floor = floorY(h);
            int[] stage = {0}, at = {0};
            double[] x0 = {0}, s0 = {0};
            float[] hp = {0};
            h.runAtTickTime(4, t.guard(() -> {
                near(t, 27, m.stamina().max(), 0.01, "stamina at level 1 (15.9)");
                near(t, 0.42, m.profile().takeOffShare(), 0.005, "the take-off threshold at level 1");
                hp[0] = amb.getHealth();
                x0[0] = amb.getX();
                String no = m.takeOff();
                t.assertTrue(no == null, "no take-off: " + no);
                at[0] = (int) (h.getTick());
                t.assertTrue(!rec.of("cue:takeoff").isEmpty(), "no take-off cue");
                String own = HeroSounds.map("ambrose_nettlewick").mobility().get("takeoff");
                t.assertTrue(own != null && !rec.of(own).isEmpty(), "no broom take-off sound " + own);
            }));
            t.onEachTick(() -> {
                if (at[0] == 0) {
                    return;
                }
                long k = h.getTick() - at[0];
                switch (stage[0]) {
                    case 0 -> {
                        if (k >= 1 && k <= 10) {
                            t.assertTrue(m.state() == HeroMovement.State.TAKEOFF && "move.takeoff".equals(oneShot(amb))
                                    && "ride".equals(mode(amb)), "the tell: " + m.state() + " " + oneShot(amb) + " " + mode(amb));
                            t.assertTrue(Math.abs(amb.getX() - x0[0]) < 1e-6 && amb.onGround(), "he moved in the tell");
                        }
                        if (m.state() == HeroMovement.State.FLY && k > 12) {
                            t.assertTrue(amb.isNoGravity() && "ride".equals(mode(amb)), "flying on his broom: " + mode(amb));
                            near(t, floor + MobilityRules.TAKE_OFF_PUSH, amb.getY(), 0.35, "the take-off push");
                            Vec3 to = at(h, 18.5, 6, 20.5);
                            t.assertTrue(m.flyTo(to) == null, "flyTo");
                            s0[0] = m.stamina().value();
                            stage[0] = 1;
                            at[0] = (int) (h.getTick());
                        }
                    }
                    case 1 -> {
                        Vec3 to = at(h, 18.5, 6, 20.5);
                        if (amb.position().distanceTo(to) < 0.6) {
                            t.assertTrue(m.stamina().value() < s0[0], "no stamina spent flying");
                            t.assertTrue(!HeroActions.locoTired(amb.loco()), "tired too soon");
                            m.setStaminaShare(0.27);
                            stage[0] = 2;
                        } else if (k > 120) {
                            t.fail("he did not reach the point: " + amb.position() + " for " + to);
                        }
                    }
                    case 2 -> {
                        if (m.state() == HeroMovement.State.LANDING) {
                            t.assertTrue("move.fly_land".equals(oneShot(amb)), "the landing shows " + oneShot(amb));
                            t.assertTrue(!rec.of("cue:landing").isEmpty(), "no landing cue");
                            stage[0] = 3;
                        }
                    }
                    case 3 -> {
                        if (m.state() == HeroMovement.State.NONE && amb.onGround()) {
                            t.assertTrue(amb.getHealth() == hp[0], "the landing hurt him");
                            t.assertTrue(HeroActions.locoTired(amb.loco()), "not tired under 25%");
                            String no = m.takeOff();
                            t.assertTrue(no != null && no.contains("threshold"), "a take-off under 42%: " + no);
                            m.setStaminaShare(1.0);
                            t.assertTrue(m.takeOff() == null, "no second take-off");
                            stage[0] = 4;
                            at[0] = (int) (h.getTick());
                        }
                    }
                    case 4 -> {
                        if (m.state() == HeroMovement.State.FLY && k > 30) {
                            m.setStaminaShare(0);
                            stage[0] = 5;
                        }
                    }
                    case 5 -> {
                        if (m.state() == HeroMovement.State.GLIDE) {
                            t.assertTrue("glide".equals(mode(amb)) && m.stamina().exhausted(), "out of stamina it glides");
                            stage[0] = 6;
                        }
                    }
                    case 6 -> {
                        if (m.state() == HeroMovement.State.NONE && amb.onGround()) {
                            stage[0] = 7;
                            at[0] = (int) (h.getTick());
                        }
                    }
                    default -> {
                        if (k > 12) {
                            t.assertTrue("winded".equals(mode(amb)), "exhausted on the ground: " + mode(amb));
                            String no = m.takeOff();
                            t.assertTrue(no != null && no.contains("exhausted"), "an exhausted take-off: " + no);
                            t.succeed();
                        }
                    }
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** Ambrose (or a copy) in the air: taken off and flown to {@code up} blocks above where he stood. */
    static void airborne(GameTestBases.Handle t, HeroEntity amb, double up) {
        String no = amb.movement().takeOff();
        t.assertTrue(no == null, "no take-off: " + no);
        t.assertTrue(amb.movement().flyTo(amb.position().add(0, up, 0)) == null, "no flight");
    }

    /**
     * {@code flyerShotDownByArrows}: Ambrose hovering in the air takes 20 air poise from each arrow, an NPC archer's and a
     * mock player's alike; the fifth arrow downs him ({@code cues.downed}): he falls with his horizontal speed cut, lies
     * 2 s ({@code move.downed}) taking 20% more damage, gains 30 poise and cannot take off for 12 s.
     */
    @GameTest(template = FIELD, timeoutTicks = 260, batch = "hero_mob_arrows")
    public static void flyerShotDownByArrows(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 260);
        HeroSounds.Recorder rec = HeroSounds.record();
        t.onCleanup(rec::close);
        try {
            HeroEntity amb = still(sliceHero(t, "ambrose_nettlewick", "fx_mob_arrows", 16, 16, 1));
            FactionNpc archer = HeroAbilityGameTests.foe(t, 16, 8, 100);
            archer.role(NpcRole.ARCHER);
            ServerPlayer player = HeroAbilityGameTests.player(t, 16, 24, FOE, 100);
            FactionNpc lyingHitter = HeroAbilityGameTests.foe(t, 18, 16, 100), standingHitter = HeroAbilityGameTests.foe(t, 14, 16, 100);
            HeroMovement m = amb.movement();
            int[] stage = {0}, shots = {0}, at = {0};
            double[] poise = {0};
            float[] lying = {0};
            h.runAtTickTime(4, t.guard(() -> airborne(t, amb, 3)));
            t.onEachTick(() -> {
                long now = (int) (h.getTick());
                switch (stage[0]) {
                    case 0 -> {
                        if (m.state() == HeroMovement.State.FLY && now > 40 && amb.getY() > floorY(h) + 2.5) {
                            stage[0] = 1;
                            at[0] = (int) now;
                        }
                    }
                    case 1 -> {
                        if ((now - at[0]) % 5 == 0 && shots[0] < 5) {
                            double expected = shots[0] * 20;
                            near(t, expected, m.airPoise().value(), 1e-6, "air poise before arrow " + (shots[0] + 1));
                            shoot(shots[0] % 2 == 0 ? archer : player, amb);
                            shots[0]++;
                        }
                        if (m.downs() == 1) {
                            t.assertTrue(shots[0] == 5, "downed after " + shots[0] + " arrows");
                            t.assertTrue(m.state() == HeroMovement.State.DOWNED_FALL && !amb.isNoGravity(), "falling: " + m.state());
                            t.assertTrue(!rec.of("cue:downed").isEmpty(), "no downed cue");
                            BossRules.Poise p = amb.combat().poise();
                            p.decay(h.getLevel().getGameTime());
                            poise[0] = p.value();
                            t.assertTrue(poise[0] >= BossRules.DOWNING_POISE, "a downing adds 30 poise: " + poise[0]);
                            stage[0] = 2;
                        }
                    }
                    case 2 -> {
                        if (m.state() == HeroMovement.State.DOWNED) {
                            t.assertTrue("move.downed".equals(oneShot(amb)) && "downed".equals(mode(amb)), "lying: " + oneShot(amb));
                            lying[0] = HeroBossGameTests.blow(amb, lyingHitter, 4);
                            stage[0] = 3;
                        }
                    }
                    case 3 -> {
                        if (m.state() == HeroMovement.State.NONE) {
                            float standing = HeroBossGameTests.blow(amb, standingHitter, 4);
                            near(t, 1.2, lying[0] / standing, 0.02, "damage taken while downed against standing (" + lying[0]
                                    + " / " + standing + ")");
                            String no = m.takeOffRefusal();
                            t.assertTrue(no != null && no.contains("downed"), "a take-off within 12 s of the downing: " + no);
                            t.succeed();
                        }
                    }
                    default -> {
                    }
                }
                if (stage[0] == 1 && now - at[0] > 60) {
                    t.fail("five arrows did not down him: air poise " + m.airPoise().value());
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code meleeOnALowFlyerAddsAirPoise}: a blow from below on a flyer within 3 blocks adds 25 air poise, an NPC
     * soldier's and a mock player's the same, and a falling (critical) blow 40. A leaper (Kagenori) in the air on his own
     * jump takes a blow's air poise too; lifted by a knock-back he takes none (air poise knocks a hero out of its own
     * airtime: four quick blows never "shoot down" a hero standing on the ground).
     */
    @GameTest(template = FIELD, timeoutTicks = 120, batch = "hero_mob_melee_air")
    public static void meleeOnALowFlyerAddsAirPoise(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            HeroEntity amb = still(sliceHero(t, "ambrose_nettlewick", "fx_mob_melee_air", 16, 16, 1));
            FactionNpc soldier = HeroAbilityGameTests.foe(t, 17, 16, 100);
            ServerPlayer player = HeroAbilityGameTests.player(t, 15, 16, FOE, 100);
            ServerPlayer jumper = HeroAbilityGameTests.player(t, 16, 17, FOE, 100);
            HeroEntity kage = still(sliceHero(t, "kagenori_tsurumine", "fx_mob_bounce", 6, 6, 1));
            FactionNpc bouncer = HeroAbilityGameTests.foe(t, 7, 6, 100);
            h.runAtTickTime(4, t.guard(() -> {
                t.assertTrue(amb.movement().takeOff() == null, "no take-off");
                kage.setDeltaMovement(0, 0.42, 0); // lifted (a knock-back's lift), not his own jump
                kage.setOnGround(false);
            }));
            h.runAtTickTime(6, t.guard(() -> {
                t.assertTrue(!kage.onGround(), "Kagenori is not in the air");
                HeroBossGameTests.blow(kage, bouncer, 1);
                t.assertTrue(kage.movement().airPoise().value() == 0, "a lifted hero took air poise: " + kage.movement().airPoise().value());
            }));
            h.runAtTickTime(24, t.guard(() -> t.assertTrue(kage.movement().jump() == null, "Kagenori does not jump")));
            h.runAtTickTime(27, t.guard(() -> {
                HeroBossGameTests.blow(kage, player, 1);
                near(t, 25, kage.movement().airPoise().value(), 1e-6, "a blow on a leaper in the air on his own jump");
            }));
            boolean[] done = {false};
            t.onEachTick(() -> {
                HeroMovement m = amb.movement();
                if (done[0] || m.state() != HeroMovement.State.FLY || h.getTick() < 30) {
                    return;
                }
                done[0] = true;
                double a0 = m.airPoise().value();
                HeroBossGameTests.blow(amb, soldier, 1);
                double bySoldier = m.airPoise().value() - a0;
                HeroBossGameTests.blow(amb, player, 1);
                double byPlayer = m.airPoise().value() - a0 - bySoldier;
                jumper.fallDistance = 1F;
                jumper.setOnGround(false);
                HeroBossGameTests.blow(amb, jumper, 1);
                double byCrit = m.airPoise().value() - a0 - bySoldier - byPlayer;
                near(t, 25, bySoldier, 1e-6, "a soldier's blow");
                near(t, 25, byPlayer, 1e-6, "a player's blow");
                near(t, 40, byCrit, 1e-6, "a critical blow");
                t.succeed();
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code flyerDownedByStaggerFallsAndIsDowned}: a full poise bar while Ambrose flies is a downing, not a kneel in the
     * air (7.6): he falls with no control, lies 2 s downed, and never kneels staggered.
     */
    @GameTest(template = FIELD, timeoutTicks = 200, batch = "hero_mob_stagger")
    public static void flyerDownedByStaggerFallsAndIsDowned(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        try {
            HeroEntity amb = still(sliceHero(t, "ambrose_nettlewick", "fx_mob_stagger", 16, 16, 1));
            HeroMovement m = amb.movement();
            int[] stage = {0};
            h.runAtTickTime(4, t.guard(() -> airborne(t, amb, 3)));
            t.onEachTick(() -> {
                switch (stage[0]) {
                    case 0 -> {
                        if (m.state() == HeroMovement.State.FLY && h.getTick() > 40 && amb.getY() > floorY(h) + 2.5) {
                            BossRules.Poise p = amb.combat().poise();
                            p.decay(h.getLevel().getGameTime());
                            t.assertTrue(amb.combat().poiseDamage(p.max() - p.value() + 1), "a full bar did not stagger");
                            t.assertTrue(m.downs() == 1 && m.state() == HeroMovement.State.DOWNED_FALL, "not downed: " + m.state());
                            t.assertTrue(!amb.combat().staggered(), "it kneels in the air");
                            stage[0] = 1;
                        }
                    }
                    case 1 -> {
                        t.assertTrue(!amb.combat().staggered(), "a stagger's kneel after the downing");
                        if (m.state() == HeroMovement.State.DOWNED) {
                            t.assertTrue(amb.onGround() && "downed".equals(mode(amb)), "lying: " + mode(amb));
                            stage[0] = 2;
                        }
                    }
                    default -> {
                        if (m.state() == HeroMovement.State.NONE) {
                            t.succeed();
                        }
                    }
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code gliderDescendsCliffUnhurt}: Ambrose stepping off a 10-block tower opens his glide by himself (1.9: a drop of
     * 4+ blocks), sinks at his glide rate and lands well out from the tower ({@code move.land}) with no fall damage; a
     * snowball hitting him in a second glide ends it, and he does not glide again before he lands.
     */
    @GameTest(template = FIELD, timeoutTicks = 260, batch = "hero_mob_glide")
    public static void gliderDescendsCliffUnhurt(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 260);
        try {
            column(h, 3, 16, 2, 11);
            HeroEntity amb = still(sliceHero(t, "ambrose_nettlewick", "fx_mob_glide", 8, 8, 1));
            Vec3 top = at(h, 3.5, 12, 16.5);
            amb.moveTo(top.x, top.y, top.z, -90F, 0F);
            FactionNpc thrower = HeroAbilityGameTests.foe(t, 28, 16, 100);
            HeroMovement m = amb.movement();
            int[] stage = {0};
            float[] hp = {0};
            boolean[] glided = {false};
            double[] lastY = {0};
            h.runAtTickTime(4, t.guard(() -> hp[0] = amb.getHealth()));
            t.onEachTick(() -> {
                long k = (int) (h.getTick());
                switch (stage[0]) {
                    case 0 -> {
                        if (k >= 5 && k < 9) {
                            amb.setDeltaMovement(0.3, amb.getDeltaMovement().y, 0);
                        }
                        if (m.state() == HeroMovement.State.GLIDE) {
                            if (glided[0]) {
                                t.assertTrue(amb.getY() - lastY[0] >= -0.2 - 1e-3, "sinking faster than 4 b/s: " + (amb.getY() - lastY[0]));
                            }
                            glided[0] = true;
                            t.assertTrue("glide".equals(mode(amb)), "gliding shows " + mode(amb));
                        }
                        lastY[0] = amb.getY();
                        if (glided[0] && m.state() == HeroMovement.State.NONE && amb.onGround()) {
                            t.assertTrue(amb.getHealth() == hp[0], "the glide hurt him");
                            t.assertTrue(amb.getX() - top.x > 12, "he landed only " + (amb.getX() - top.x) + " blocks out");
                            amb.moveTo(top.x, top.y, top.z, -90F, 0F);
                            amb.setDeltaMovement(Vec3.ZERO);
                            stage[0] = 1;
                        }
                    }
                    case 1 -> {
                        if (amb.onGround() && amb.getY() > top.y - 0.1) {
                            amb.setDeltaMovement(0.3, amb.getDeltaMovement().y, 0);
                        }
                        if (m.state() == HeroMovement.State.GLIDE && amb.getY() < top.y - 2) {
                            Snowball s = new Snowball(h.getLevel(), thrower);
                            Vec3 c = amb.getBoundingBox().getCenter();
                            s.moveTo(c.x + 1.5, c.y, c.z, 0F, 0F);
                            s.shoot(-1, 0, 0, 1.5F, 0F);
                            h.getLevel().addFreshEntity(s);
                            stage[0] = 2;
                        }
                    }
                    case 2 -> {
                        if (m.state() == HeroMovement.State.NONE && !amb.onGround()) {
                            stage[0] = 3;
                        }
                    }
                    case 3 -> {
                        t.assertTrue(m.state() != HeroMovement.State.GLIDE, "he glided again after the snowball");
                        if (amb.onGround()) {
                            t.succeed();
                        }
                    }
                    default -> {
                    }
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the fight's air rules, counted from any enemy (2.3, 5.1.4)

    /**
     * {@code flyerAirBudgetAndCeilingCountNpcAndPlayerAlike}: two copies of Ambrose, one fighting an NPC soldier and one a
     * mock player, take off together and fly for a point 15 blocks up: both stay at most 6 blocks above the ground (the
     * fight's ceiling, from the highest engaged enemy standing on a block), both must land 8 s after their take-off
     * (the air budget per take-off), on the same tick, and neither may take off again before 6 s on the ground.
     */
    @GameTest(template = FIELD, timeoutTicks = 320, batch = "hero_mob_budget")
    public static void flyerAirBudgetAndCeilingCountNpcAndPlayerAlike(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 320);
        try {
            HeroEntity a = still(sliceHero(t, "ambrose_nettlewick", "fx_mob_budget_npc", 6, 6, 1));
            HeroEntity b = still(sliceHero(t, "ambrose_nettlewick", "fx_mob_budget_pl", 6, 26, 1));
            FactionNpc soldier = HeroAbilityGameTests.foe(t, 10, 6, 100);
            ServerPlayer player = HeroAbilityGameTests.player(t, 10, 26, FOE, 100);
            double floor = floorY(h);
            double[] top = {-1e9, -1e9};
            long[] landed = {-1, -1};
            h.runAtTickTime(3, t.guard(() -> {
                a.setTarget(soldier);
                b.setTarget(player);
            }));
            h.runAtTickTime(6, t.guard(() -> {
                t.assertTrue(a.combat().boss().fighting() && b.combat().boss().fighting(), "no fights");
                t.assertTrue(a.movement().takeOff() == null && b.movement().takeOff() == null, "no take-offs");
            }));
            h.runAtTickTime(24, t.guard(() -> {
                t.assertTrue(a.movement().flyTo(a.position().add(20, 15, 0)) == null, "A flies");
                t.assertTrue(b.movement().flyTo(b.position().add(20, 15, 0)) == null, "B flies");
            }));
            t.onEachTick(() -> {
                if (h.getTick() < 6) {
                    return;
                }
                HeroEntity[] both = {a, b};
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = both[i];
                    top[i] = Math.max(top[i], x.getY());
                    if (landed[i] < 0 && x.movement().state() == HeroMovement.State.LAND) {
                        landed[i] = (int) (h.getTick());
                    }
                    if (x.getTarget() == null) {
                        x.setTarget(i == 0 ? soldier : player);
                    }
                }
                if (landed[0] > 0 && landed[1] > 0 && a.onGround() && b.onGround() && a.movement().state() == HeroMovement.State.NONE
                        && b.movement().state() == HeroMovement.State.NONE) {
                    t.assertTrue(landed[0] == landed[1], "the NPC's flyer landed at " + landed[0] + ", the player's at " + landed[1]);
                    t.assertTrue(landed[0] >= 6 + 12 + 158 && landed[0] <= 6 + 12 + 166, "landed at tick " + landed[0]
                            + " (8 s after the take-off push)");
                    near(t, top[0], top[1], 1e-6, "the two heights");
                    t.assertTrue(top[0] - floor <= BossRules.FIGHT_CEILING + 0.2, "it rose " + (top[0] - floor) + " blocks in a fight");
                    t.assertTrue(top[0] - floor >= BossRules.FIGHT_CEILING - 0.6, "it rose only " + (top[0] - floor) + " blocks");
                    a.movement().setStaminaShare(1); // (its stamina is not what is checked here)
                    b.movement().setStaminaShare(1);
                    String no = a.movement().takeOffRefusal(), no2 = b.movement().takeOffRefusal();
                    t.assertTrue(no != null && no.contains("long enough") && no.equals(no2), "a take-off at once: " + no + " / " + no2);
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ workers are unchanged (decision 1)

    /**
     * {@code workersMoveUnchanged} (static): a worker's jump strength, step height, safe fall, fall damage, swim speed and
     * fall distance are vanilla's with no mobility modifier, and it is never on a climbable for touching a wall; a hero
     * with mobility switched off has none either.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void workersMoveUnchanged(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 40);
        boolean was = FCConfig.MOBILITY_ENABLED.get();
        t.onCleanup(() -> FCConfig.MOBILITY_ENABLED.set(was));
        try {
            h.setBlock(new BlockPos(7, 2, 6), Blocks.STONE);
            FactionNpc worker = t.npc(6, 6, NpcRole.MINER);
            HeroEntity hero = sliceHero(t, "natsuho_komezaki", "fx_mob_static", 12, 12, 20);
            h.runAtTickTime(3, t.guard(() -> {
                near(t, LeapSolver.PLAIN_JUMP, worker.getAttributeValue(Attributes.JUMP_STRENGTH), 1e-6, "a worker's jump strength");
                near(t, MobilityRules.WORKER_STEP, worker.getAttributeValue(Attributes.STEP_HEIGHT), 1e-6, "a worker's step");
                near(t, MobilityRules.WORKER_SAFE_FALL, worker.getAttributeValue(Attributes.SAFE_FALL_DISTANCE), 1e-6, "a worker's safe fall");
                near(t, 1, worker.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 1e-6, "a worker's fall damage");
                near(t, 1, worker.getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED), 1e-6, "a worker's swim speed");
                t.assertTrue(worker.getMaxFallDistance() == 3, "max fall distance " + worker.getMaxFallDistance());
                t.assertTrue(!worker.onClimbable(), "a worker against a wall is not climbing");
                for (Holder<Attribute> a : List.of(Attributes.JUMP_STRENGTH, Attributes.STEP_HEIGHT, Attributes.SAFE_FALL_DISTANCE,
                        Attributes.MOVEMENT_SPEED, Attributes.FALL_DAMAGE_MULTIPLIER, Attributes.MOVEMENT_EFFICIENCY)) {
                    AttributeInstance inst = worker.getAttribute(a);
                    t.assertTrue(inst == null || inst.getModifiers().stream().noneMatch(x -> x.id().getPath().startsWith("mobility")),
                            "a mobility modifier on a worker's " + a);
                }
                t.assertTrue(!hero.movement().modifiers().isEmpty(), "the hero has no mobility");
                FCConfig.MOBILITY_ENABLED.set(false);
                hero.movement().refresh();
                t.assertTrue(hero.movement().modifiers().isEmpty() && !hero.movement().profile().any(), "mobility off");
                near(t, LeapSolver.PLAIN_JUMP, hero.getAttributeValue(Attributes.JUMP_STRENGTH), 1e-6, "mobility off: the jump");
                near(t, 1, hero.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 1e-6, "mobility off: fall damage");
                FCConfig.MOBILITY_ENABLED.set(was);
                hero.movement().refresh();
                near(t, 0, hero.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 1e-6, "mobility back on");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
