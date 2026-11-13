package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEffects;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroManager;
import com.factionclash.hero.HeroRegistry;
import com.factionclash.hero.HeroStatus;
import com.factionclash.hero.HeroTimingFiles;
import com.factionclash.hero.entity.HeroDecoy;
import com.factionclash.hero.entity.HeroProjectile;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroClips;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroIdentity;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.logic.hero.HeroTimings;
import com.factionclash.logic.hero.MovePlan.MoveStage;
import com.factionclash.logic.hero.MoveTiming;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static com.factionclash.gametest.HeroAbilityGameTests.MELEE;
import static com.factionclash.gametest.HeroAbilityGameTests.foe;
import static com.factionclash.gametest.HeroAbilityGameTests.force;
import static com.factionclash.gametest.HeroAbilityGameTests.heroHit;
import static com.factionclash.gametest.HeroAbilityGameTests.husk;
import static com.factionclash.gametest.HeroAbilityGameTests.move;
import static com.factionclash.gametest.HeroAbilityGameTests.near;
import static com.factionclash.gametest.HeroAbilityGameTests.player;

/**
 * Heroes, task A5: the move runner, format 2's forms and effects (HEROES_V2 4.8, 6.3-6.6, 14.5; SLICE_PLAN 2 "Move
 * runner": {@code DATA_ACTION} is set at wind-up start, the hit lands exactly {@code frames.windup} ticks later, speed
 * scaling and interrupts work). Fixture heroes run with a timing file of their own registered for the test (the
 * generated files are never touched), and a visual file that lists their moves, so the code the server sends is the one
 * the client turns into the move's clip. <b>Equal treatment</b> (6.15): the counter, the grab, the wave, the guard
 * break, the barrier, the tether and the unblockable hit are each checked on an NPC soldier and on a mock player with
 * the same numbers. Each test that runs a hero has a batch of its own.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroMoveGameTests {
    private HeroMoveGameTests() {
    }

    private static final FactionType SIDE = FactionType.EMBER_LEGION, FOE = FactionType.GROVE_COVENANT;

    // ------------------------------------------------------------------ fixtures

    /** A visual file whose animation chain starts with {@code file} and whose move list is {@code moves}. */
    static String visual(String file, String... moves) {
        StringBuilder m = new StringBuilder();
        for (String s : moves) {
            m.append(m.length() == 0 ? "" : ",").append('"').append(s).append('"');
        }
        return "{\"form\":\"adult\",\"build\":\"standard\",\"base\":\"adult_f\",\"style\":\"katana\",\"height_px\":52,"
                + "\"head_y\":42,\"render_scale\":0.56,\"scale\":1.0,\"animation_chain\":[\"" + file + "\",\"_style_katana\",\"_core\"],"
                + "\"moves\":[" + m + "],\"texture\":{\"width\":256,\"height\":128}}";
    }

    /** A common hero of {@code role} with {@code moves}, its own {@code boss} block and more top-level fields. */
    static String def(String id, String role, String moves, String boss, String more) {
        return "{\"format\": 2, \"id\": \"" + id + "\", \"name\": \"" + HeroIdentity.nameFromId(id) + "\", \"gender\": \"female\","
                + " \"archetype\": \"samurai\", \"rarity\": \"common\", \"behaviour\": {\"combat_role\": \"" + role + "\"},"
                + " \"basic_attack\": " + MELEE + ", \"moves\": [" + moves + "], \"boss\": {\"weak_points\": [\"back\"],"
                + " \"phases\": [{\"at\": 100}]" + boss + "}, \"personality\": {\"temperament\": \"stoic\"},"
                + " \"leader\": {\"eligible\": false}" + more + "}";
    }

    static String def(String id, String moves) {
        return def(id, "striker", moves, "", "");
    }

    /** A hero of {@code faction} from a visual and a definition, at relative (x, 2, z); the cleanup removes it. */
    static HeroEntity hero(GameTestBases.Handle t, String id, String visualJson, String definition, FactionType faction, int x, int z) {
        HeroRegistry.Transient fx = HeroRegistry.registerTransient(id, visualJson, definition);
        MinecraftServer server = t.helper.getLevel().getServer();
        t.onCleanup(() -> {
            HeroManager.forget(server, id);
            fx.close();
        });
        if (HeroRegistry.get(id) == null || HeroRegistry.get(id).def() == null) {
            throw new GameTestAssertException("the fixture " + id + " did not load: " + HeroRegistry.report().problems().report());
        }
        HeroManager.Result r = HeroManager.give(server, id, t.base.faction, t.base, t.helper.absolutePos(new BlockPos(x, 2, z)));
        if (!r.ok()) {
            throw new GameTestAssertException("give failed: " + r.message());
        }
        if (faction != t.base.faction) {
            r.hero().faction(faction); // an enemy hero: of the other side, living at the test base
        }
        return r.hero();
    }

    static HeroEntity hero(GameTestBases.Handle t, String id, String definition, int x, int z) {
        return hero(t, id, visual(id), definition, SIDE, x, z);
    }

    /**
     * A timing file of the test's own, {@code file}, with one action clip: its wind-up, active and recovery ticks and its
     * {@code hit} markers (ticks from the action's start), as herogen writes them.
     */
    static void timing(GameTestBases.Handle t, String file, String clip, int windup, int active, int recovery, int... hits) {
        StringBuilder ev = new StringBuilder();
        for (int hit : hits) {
            ev.append(ev.length() == 0 ? "" : ",").append("{\"tick\":").append(hit).append(",\"type\":\"hit\"}");
        }
        int total = windup + active + recovery;
        String json = "{\"file\":\"" + file + "\",\"format\":2,\"animations\":{\"" + clip + "\":{\"kind\":\"action\",\"loop\":\"once\","
                + "\"ticks\":" + (total - 2) + ",\"transition_ticks\":2,\"total_ticks\":" + total + ",\"events\":[" + ev + "],"
                + "\"frame_ticks\":{\"windup\":" + windup + ",\"active\":" + active + ",\"recovery\":" + recovery + "}}}}";
        AutoCloseable c = HeroTimingFiles.registerTransient(file, HeroTimings.parse(file, json));
        t.onCleanup(() -> {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        });
    }

    static long now(GameTestHelper h) {
        return h.getLevel().getGameTime();
    }

    /** Sets a block for the test and puts back what was there at the cleanup. */
    static void block(GameTestBases.Handle t, int x, int y, int z, BlockState s) {
        BlockPos p = new BlockPos(x, y, z);
        BlockPos abs = t.helper.absolutePos(p);
        BlockState before = t.helper.getLevel().getBlockState(abs);
        t.helper.setBlock(p, s);
        t.onCleanup(() -> t.helper.getLevel().setBlockAndUpdate(abs, before));
    }

    /** A mock player whose raised shield blocks (it ages its item use as a living entity's tick does). */
    static ServerPlayer blockingPlayer(GameTestBases.Handle t, int x, int z, FactionType faction, double health) {
        ServerLevel level = t.helper.getLevel();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "hero-shield-player")) {
            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isBlocking() {
                return HeroStatus.shieldUp(this);
            }

            @Override
            public void tick() {
                baseTick();
            }
        };
        Vec3 at = Vec3.atBottomCenterOf(t.helper.absolutePos(new BlockPos(x, 2, z)));
        p.moveTo(at.x, at.y, at.z, 0F, 0F);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        p.setHealth((float) health);
        p.setOnGround(true);
        try {
            java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(p, 0);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("cannot clear the mock player's spawn invulnerability: " + e);
        }
        com.factionclash.campaign.CampaignData data = com.factionclash.campaign.CampaignData.get(level.getServer());
        data.players.put(p.getUUID(), faction);
        level.addFreshEntity(p);
        t.onCleanup(() -> {
            data.players.remove(p.getUUID());
            p.discard();
        });
        return p;
    }

    /** An NPC soldier of {@link #FOE} that keeps its shield raised (its target is an archer far off, as soldiers do). */
    static FactionNpc shieldBearer(GameTestBases.Handle t, int x, int z, double health, FactionNpc archer) {
        FactionNpc n = foe(t, x, z, health);
        n.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        n.setTarget(archer);
        n.startUsingItem(InteractionHand.OFF_HAND);
        return n;
    }

    /** An archer of the heroes' side, still, that a shield bearer raises its shield against. */
    static FactionNpc archer(GameTestBases.Handle t, int x, int z) {
        FactionNpc a = t.track(t.helper.spawn(com.factionclash.registry.ModEntities.FACTION_NPC.get(), new BlockPos(x, 2, z)));
        a.faction(SIDE);
        a.role(NpcRole.ARCHER);
        a.setNoAi(true);
        return a;
    }

    private static final String DMG4 = "\"effects\": [{\"type\": \"damage\", \"amount\": 4}]";

    // ------------------------------------------------------------------ the runner: DATA_ACTION, frames, speed

    /**
     * {@code heroMoveSetsActionAtWindup}: a hero picks its move by itself (after the fight's opening gap, 6.3) and, on
     * the tick its wind-up starts, {@code DATA_ACTION} carries the move's code, the one the client turns into
     * {@code move.slash} through the hero's visual move list, at speed 1; the plan takes its wind-up from the timing
     * file (14 ticks), not from the definition's own frames (10).
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "hero_mv_action")
    public static void heroMoveSetsActionAtWindup(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        try {
            String id = "fx_mv_action";
            timing(t, id, "move.slash", 14, 1, 13, 14);
            HeroEntity hero = hero(t, id, visual(id, "slash"), def(id, move("slash", "melee", "\"cooldown\": 8, \"frames\":"
                    + " {\"windup\": 0.5, \"active\": 0.05, \"recovery\": 0.5}, " + DMG4)), SIDE, 6, 6);
            Husk husk = husk(t, 8, 6, 200);
            hero.setTarget(husk);
            long engaged = now(h);
            t.onEachTick(() -> {
                if (hero.combat().lastMoveStart() != now(h)) {
                    t.assertTrue(hero.combat().lastMoveStart() < 0, "a move started without being seen on its first tick");
                    return;
                }
                int code = HeroActions.code(hero.action());
                t.assertTrue(code == HeroActions.FIRST_MOVE, "DATA_ACTION at the wind-up's start: code " + code);
                String clip = HeroClips.actionClip(code, hero.entry().visual().look().moves(), hero.entry().visual().look().states());
                t.assertTrue("move.slash".equals(clip), "the client would play " + clip);
                near(t, 1.0, HeroActions.speed(hero.action()), "speed");
                t.assertTrue(hero.combat().lastPlan().windup() == 14, "the timing file's wind-up: " + hero.combat().lastPlan());
                t.assertTrue(hero.stateFlag(HeroActions.STATE_WINDUP), "no wind-up flag");
                t.assertTrue(now(h) - engaged >= 30, "it started " + (now(h) - engaged) + " ticks after the fight began");
                t.succeed();
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroHitLandsAfterWindupFrames}: the hit lands exactly {@code frames.windup} ticks (the timing file's 14)
     * after the tick {@code DATA_ACTION} was set; the clip's code ends when the run does.
     */
    @GameTest(template = "platform", timeoutTicks = 220, batch = "hero_mv_hit")
    public static void heroHitLandsAfterWindupFrames(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 220);
        try {
            String id = "fx_mv_hit";
            timing(t, id, "move.slash", 14, 1, 13, 14);
            HeroEntity hero = hero(t, id, visual(id, "slash"), def(id, move("slash", "melee", "\"cooldown\": 8, " + DMG4)), SIDE, 6, 6);
            Husk husk = husk(t, 8, 6, 200);
            hero.setTarget(husk);
            long[] set = {-1}, hit = {-1};
            float[] last = {200};
            t.onEachTick(() -> {
                long now = now(h);
                if (set[0] < 0 && hero.combat().lastMoveStart() == now) {
                    t.assertTrue(HeroActions.code(hero.action()) == HeroActions.FIRST_MOVE, "no move code");
                    set[0] = now;
                }
                if (set[0] >= 0 && hit[0] < 0 && husk.getHealth() < last[0] - 1e-3 && heroHit(husk, hero)) {
                    hit[0] = now; // the move's hit (the hero's damage type), not a basic blow before it
                    t.assertTrue(hit[0] - set[0] == 14, "the hit landed " + (hit[0] - set[0]) + " ticks after DATA_ACTION (14)");
                    t.assertTrue(hero.combat().lastRelease() == now, "the release tick");
                }
                last[0] = husk.getHealth();
                if (hit[0] > 0 && !hero.combat().running()) {
                    t.assertTrue(HeroActions.code(hero.action()) != HeroActions.FIRST_MOVE, "the move's code outlived its run");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroSpeedScalesFrames}: Haste X doubles a light move's speed: {@code DATA_ACTION} plays its clip at 2.0 and
     * the hit lands after 12 of the file's 24 wind-up ticks; a heavy move is held to its 0.8 s minimum (16 ticks), so its
     * clip plays at the slower speed that keeps it there, and its hit still lands on the clip's marker.
     */
    @GameTest(template = "platform", timeoutTicks = 260, batch = "hero_mv_speed")
    public static void heroSpeedScalesFrames(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 260);
        try {
            String a = "fx_mv_fast", b = "fx_mv_heavy";
            timing(t, a, "move.jab", 24, 1, 18, 24);
            timing(t, b, "move.smash", 18, 1, 24, 18);
            HeroEntity light = hero(t, a, visual(a, "jab"), def(a, move("jab", "melee", "\"cooldown\": 8, " + DMG4)), SIDE, 4, 4);
            HeroEntity heavy = hero(t, b, visual(b, "smash"), def(b, move("smash", "melee", "\"class\": \"heavy\", \"cooldown\": 8, "
                    + DMG4)), SIDE, 16, 18);
            Husk hl = husk(t, 6, 4, 200), hh = husk(t, 18, 18, 200);
            for (HeroEntity x : List.of(light, heavy)) {
                x.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 2000, 9)); // Haste X: speed 2.0
            }
            light.setTarget(hl);
            heavy.setTarget(hh);
            Map<HeroEntity, long[]> seen = new HashMap<>();
            seen.put(light, new long[] {-1, -1});
            seen.put(heavy, new long[] {-1, -1});
            Map<Husk, Float> lastHealth = new HashMap<>();
            t.onEachTick(() -> {
                long now = now(h);
                for (HeroEntity x : List.of(light, heavy)) {
                    long[] s = seen.get(x);
                    Husk target = x == light ? hl : hh;
                    if (s[0] < 0 && x.combat().lastMoveStart() == now) {
                        s[0] = now;
                        double sp = HeroActions.speed(x.action());
                        near(t, x.combat().lastSpeed(), sp, "the clip's speed is the plan's");
                        if (x == light) {
                            near(t, 2.0, sp, "Haste X doubles a light move");
                            t.assertTrue(x.combat().lastPlan().windup() == 12, "24 ticks at 2x: " + x.combat().lastPlan());
                        } else {
                            t.assertTrue(sp > 1.0 && sp <= 18 / 16.0 + 1e-9, "a heavy move held by its minimum: " + sp);
                            int w = x.combat().lastPlan().windup();
                            t.assertTrue(w >= 16 && w == MoveTiming.at(18, sp), "the heavy wind-up " + w);
                        }
                    }
                    float was = lastHealth.getOrDefault(target, 200F);
                    lastHealth.put(target, target.getHealth());
                    if (s[0] >= 0 && s[1] < 0 && target.getHealth() < was - 1e-3 && heroHit(target, x)) {
                        s[1] = now;
                        t.assertTrue(now - s[0] == x.combat().lastPlan().windup(), x.heroId() + " hit after " + (now - s[0])
                                + " ticks, its wind-up is " + x.combat().lastPlan().windup());
                    }
                }
                if (seen.get(light)[1] > 0 && seen.get(heavy)[1] > 0) {
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroKnockbackInterruptsWindup}: a plain blow's knock-back (0.4) leaves a wind-up alone; one of 1.0 stops it:
     * nothing lands, the {@code interrupt} clip plays, half the cooldown comes back. A {@code super_armor} move takes the
     * same knock-back and lands.
     */
    @GameTest(template = "platform", timeoutTicks = 160, batch = "hero_mv_knock")
    public static void heroKnockbackInterruptsWindup(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            String a = "fx_mv_knock", b = "fx_mv_armour";
            String cleave = "\"class\": \"heavy\", \"cooldown\": 10, \"frames\": {\"windup\": 1.0, \"active\": 0.1, \"recovery\": 0.8}, ";
            HeroEntity plain = hero(t, a, def(a, move("cleave", "melee", cleave + DMG4)), 4, 4);
            HeroEntity armour = hero(t, b, def(b, move("cleave", "melee", cleave + "\"super_armor\": true, " + DMG4)), 16, 18);
            Husk hp = husk(t, 6, 4, 200), ha = husk(t, 18, 18, 200);
            plain.setNoAi(true);
            armour.setNoAi(true);
            h.runAtTickTime(2, t.guard(() -> {
                force(t, plain, hp, "cleave");
                force(t, armour, ha, "cleave");
            }));
            long[] knocked = {-1};
            long[] readyBefore = {0};
            h.runAtTickTime(8, t.guard(() -> {
                plain.knockback(0.4, 1, 0);
                t.assertTrue(plain.combat().running() && plain.combat().stage() == MoveStage.WINDUP, "a plain blow interrupted it");
                HeroDef.Move m = plain.definition().v2().move("cleave");
                readyBefore[0] = plain.combat().selector().readyAt(m, now(h));
                plain.knockback(1.0, 1, 0);
                armour.knockback(1.0, 1, 0);
                knocked[0] = now(h);
                t.assertTrue(!plain.combat().running(), "a knock-back of 1.0 did not interrupt the wind-up");
                t.assertTrue(HeroActions.code(plain.action()) == HeroActions.actionCode("interrupt"), "no interrupt clip");
                long cd = HeroRules.ticks(m.ability().cooldown());
                near(t, Math.max(now(h), readyBefore[0] - cd / 2), plain.combat().selector().readyAt(m, now(h)), "half the cooldown back");
                t.assertTrue(armour.combat().running(), "a super-armour move was interrupted");
            }));
            t.onEachTick(() -> {
                if (knocked[0] < 0) {
                    return;
                }
                t.assertTrue(!heroHit(hp, plain), "the interrupted move still landed");
                if (now(h) - knocked[0] >= 40) {
                    t.assertTrue(heroHit(ha, armour), "the super-armour move did not land");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroTargetLostCancelsWindup}: a target that leaves reach during the wind-up calls the move off (v1 10.3):
     * nothing lands, its clip stops, its charge is kept whole.
     */
    @GameTest(template = "platform", timeoutTicks = 120, batch = "hero_mv_lost")
    public static void heroTargetLostCancelsWindup(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            String id = "fx_mv_lost";
            timing(t, id, "move.cleave", 20, 1, 16, 20);
            HeroEntity hero = hero(t, id, visual(id, "cleave"), def(id, move("cleave", "melee", "\"class\": \"heavy\", \"cooldown\": 10, "
                    + DMG4)), SIDE, 4, 4);
            hero.setNoAi(true);
            Husk husk = husk(t, 6, 4, 200);
            h.runAtTickTime(2, t.guard(() -> force(t, hero, husk, "cleave")));
            h.runAtTickTime(4, t.guard(() -> t.assertTrue(HeroActions.code(hero.action()) == HeroActions.FIRST_MOVE, "no move code")));
            h.runAtTickTime(8, t.guard(() -> {
                Vec3 far = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(18, 2, 18)));
                husk.teleportTo(far.x, far.y, far.z);
            }));
            h.runAtTickTime(10, t.guard(() -> {
                t.assertTrue(!hero.combat().running(), "the wind-up went on after its target left");
                t.assertTrue(HeroActions.code(hero.action()) == HeroActions.CODE_NONE, "the move's clip went on");
                HeroDef.Move m = hero.definition().v2().move("cleave");
                t.assertTrue(hero.combat().selector().charges(m, now(h), 0) == 1, "the charge was spent");
            }));
            h.runAtTickTime(40, t.guard(() -> {
                t.assertTrue(husk.getHealth() >= 200, "the called-off move landed");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the forms (6.5)

    /**
     * {@code comboStrikesTimed}: a combo's three strikes land on the ticks of its clip's three {@code hit} markers (10,
     * 14 and 19 after {@code DATA_ACTION}), each strike its own damage.
     */
    @GameTest(template = "platform", timeoutTicks = 220, batch = "hero_mv_combo")
    public static void comboStrikesTimed(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 220);
        try {
            String id = "fx_mv_combo";
            timing(t, id, "move.flurry", 10, 10, 8, 10, 14, 19);
            String strikes = "\"strikes\": [{\"effects\": [{\"type\": \"damage\", \"amount\": 2}]}, {\"effects\": [{\"type\": \"damage\","
                    + " \"amount\": 3}]}, {\"effects\": [{\"type\": \"damage\", \"amount\": 4}]}]";
            HeroEntity hero = hero(t, id, visual(id, "flurry"), def(id, move("flurry", "combo", "\"class\": \"light\", \"cooldown\": 8, "
                    + strikes)), SIDE, 6, 6);
            Husk husk = husk(t, 8, 6, 200);
            hero.setTarget(husk);
            long[] start = {-1};
            float[] last = {200};
            List<Long> hits = new ArrayList<>();
            t.onEachTick(() -> {
                long now = now(h);
                if (start[0] < 0 && hero.combat().lastMoveStart() == now) {
                    start[0] = now;
                }
                if (start[0] >= 0 && husk.getHealth() < last[0] - 1e-3 && heroHit(husk, hero)) {
                    hits.add(now - start[0]); // a strike (the hero's damage type), not a basic blow before the move
                }
                last[0] = husk.getHealth();
                if (hits.size() == 3) {
                    t.assertTrue(hits.equals(List.of(10L, 14L, 19L)), "the strikes landed at " + hits);
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code chargeWallStagger}: a charge runs straight through the enemy in its way (hit once, knocked on) and stops
     * at a wall; with {@code wall_stun} the wall fills the hero's own poise by 40, and a hero already at 15 of its 50
     * staggers there (the stagger hook: no move for 2.5 s, the {@code stagger} clip).
     */
    @GameTest(template = "platform", timeoutTicks = 160, batch = "hero_mv_charge")
    public static void chargeWallStagger(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            String charge = move("ram", "charge", "\"class\": \"heavy\", \"range\": 12, \"speed\": 160, \"wall_stun\": true,"
                    + " \"cooldown\": 9, \"effects\": [{\"type\": \"damage\", \"amount\": 3}]");
            String a = "fx_mv_ram", b = "fx_mv_ram2";
            HeroEntity first = hero(t, a, def(a, "vanguard", charge, ", \"poise\": 50", ""), 3, 6);
            HeroEntity second = hero(t, b, def(b, "vanguard", charge, ", \"poise\": 50", ""), 3, 18);
            Husk h1 = husk(t, 6, 6, 200), h2 = husk(t, 6, 18, 200);
            for (int y = 2; y <= 4; y++) {
                for (int dz = -2; dz <= 2; dz++) {
                    block(t, 14, y, 6 + dz, Blocks.STONE.defaultBlockState());
                    block(t, 14, y, 18 + dz, Blocks.STONE.defaultBlockState());
                }
            }
            // with their AI on: a mob without AI does not move by its velocity
            h.runAtTickTime(2, t.guard(() -> {
                force(t, first, h1, "ram");
                force(t, second, h2, "ram");
            }));
            // its fight has begun (a new fight empties the bar), then it is already at 15
            h.runAtTickTime(5, t.guard(() -> second.combat().poiseDamage(15)));
            long[] staggerSeen = {-1};
            double[] seenPoise = {0, 0};
            t.onEachTick(() -> {
                if (first.combat().poise() != null) {
                    seenPoise[0] = Math.max(seenPoise[0], first.combat().poise().value());
                    seenPoise[1] = Math.max(seenPoise[1], second.combat().poise().value());
                }
                if (staggerSeen[0] < 0 && second.combat().staggered()) {
                    staggerSeen[0] = now(h);
                    t.assertTrue(HeroActions.code(second.action()) == HeroActions.actionCode("stagger"), "no stagger clip");
                    t.assertTrue(!second.combat().running(), "a staggered hero's move went on");
                }
                if (first.combat().lastRelease() > 0 && !first.combat().running()) {
                    t.assertTrue(h1.getHealth() < 200 && h2.getHealth() < 200, "the charge did not hit the enemy in its way");
                    t.assertTrue(first.getX() > h.absolutePos(new BlockPos(11, 2, 6)).getX(), "the charge stopped short of the wall: "
                            + first.position() + " (the wall at x " + h.absolutePos(new BlockPos(14, 2, 6)).getX() + ")");
                    near(t, 40, first.combat().poise().value(), "the wall's poise on the hero");
                    t.assertTrue(!first.combat().staggered(), "40 of 50 staggered it");
                    t.assertTrue(staggerSeen[0] > 0, "15 + 40 of 50 poise did not stagger the second hero (highest poise seen "
                            + seenPoise[1] + ")");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code waveMissesJumpingTarget}: a ring rolls out along the ground: an NPC soldier and a mock player standing on
     * it are hit for the same damage; an NPC soldier and a mock player in the air when the front passes are not.
     */
    @GameTest(template = "platform", timeoutTicks = 120, batch = "hero_mv_wave")
    public static void waveMissesJumpingTarget(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            String id = "fx_mv_wave";
            HeroEntity hero = hero(t, id, def(id, move("stomp", "wave", "\"class\": \"area\", \"radius\": 8, \"speed\": 8,"
                    + " \"waves\": 1, \"cooldown\": 12, " + DMG4)), 12, 12);
            hero.setNoAi(true);
            FactionNpc ground = foe(t, 12, 8, 100), air = foe(t, 12, 16, 100);
            ServerPlayer pg = player(t, 16, 12, FOE, 100), pa = player(t, 8, 12, FOE, 100);
            air.setNoGravity(true);
            air.setPos(air.getX(), air.getY() + 0.6, air.getZ());
            pa.setPos(pa.getX(), pa.getY() + 0.6, pa.getZ());
            h.runAtTickTime(2, t.guard(() -> force(t, hero, ground, "stomp")));
            t.onEachTick(() -> {
                air.setOnGround(false);
                pa.setOnGround(false);
                pg.setOnGround(true);
            });
            h.runAtTickTime(70, t.guard(() -> {
                t.assertTrue(ground.getHealth() < 100, "the wave missed the soldier on the ground");
                t.assertTrue(pg.getHealth() < 100, "the wave missed the player on the ground");
                near(t, 100 - ground.getHealth(), 100 - pg.getHealth(), "the same damage to the soldier and the player");
                t.assertTrue(air.getHealth() >= 100, "the wave hit a soldier in the air");
                t.assertTrue(pa.getHealth() >= 100, "the wave hit a player in the air");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code rainImpactsAfterDelay}: three impacts are called 7 ticks apart around the target and each lands its 0.9 s
     * delay (18 ticks) after its call, on the enemies within 1.5 blocks of it: nothing is hit before the first lands.
     */
    @GameTest(template = "platform", timeoutTicks = 140, batch = "hero_mv_rain")
    public static void rainImpactsAfterDelay(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 140);
        try {
            String id = "fx_mv_rain";
            HeroEntity hero = hero(t, id, def(id, move("hail", "rain", "\"class\": \"area\", \"count\": 3, \"radius\": 3, \"duration\": 1,"
                    + " \"delay\": 0.9, \"range\": 16, \"cooldown\": 14, " + DMG4)), 4, 12);
            hero.setNoAi(true);
            List<Husk> grid = new ArrayList<>();
            Vec3 c = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(14, 2, 12)));
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    Husk m = husk(t, 14, 12, 500);
                    m.moveTo(c.x + i * 1.5, c.y, c.z + j * 1.5, 0F, 0F);
                    grid.add(m);
                }
            }
            Husk centre = grid.get(4);
            h.runAtTickTime(2, t.guard(() -> force(t, hero, centre, "hail")));
            Map<Husk, Float> before = new HashMap<>();
            Set<Long> landed = new TreeSet<>();
            t.onEachTick(() -> {
                long now = now(h);
                for (Husk m : grid) {
                    float was = before.getOrDefault(m, 500F);
                    if (m.getHealth() < was - 1e-3) {
                        landed.add(now);
                    }
                    before.put(m, m.getHealth());
                }
                long r = hero.combat().lastRelease();
                if (r > 0 && now >= r + 40) {
                    t.assertTrue(landed.equals(new TreeSet<>(List.of(r + 18, r + 25, r + 32))),
                            "impacts landed at " + landed + " after the release at " + r);
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code sweepStopsAtBlocks}: a 180-degree sweep strikes the enemy in the open in front, not the one behind a wall
     * inside its arc, and not the one behind the hero.
     */
    @GameTest(template = "platform", timeoutTicks = 120, batch = "hero_mv_sweep")
    public static void sweepStopsAtBlocks(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            String id = "fx_mv_sweep";
            HeroEntity hero = hero(t, id, def(id, move("arc", "sweep", "\"class\": \"area\", \"range\": 6, \"arc\": 180, \"duration\": 1,"
                    + " \"width\": 1, \"cooldown\": 14, " + DMG4)), 6, 12);
            hero.setNoAi(true);
            Husk open = husk(t, 10, 12, 200), walled = husk(t, 8, 8, 200), behind = husk(t, 2, 12, 200);
            for (int y = 2; y <= 4; y++) {
                for (int x = 6; x <= 9; x++) {
                    block(t, x, y, 10, Blocks.STONE.defaultBlockState());
                }
            }
            h.runAtTickTime(2, t.guard(() -> force(t, hero, open, "arc")));
            boolean[] struck = {false};
            t.onEachTick(() -> {
                if (!struck[0] && open.getHealth() < 200) {
                    // a hurt's source is forgotten after 2 s: read it on the tick it lands
                    t.assertTrue(heroHit(open, hero), "the open enemy was hurt by something else");
                    struck[0] = true;
                }
                t.assertTrue(walled.getHealth() >= 200, "the sweep went through a wall");
                t.assertTrue(behind.getHealth() >= 200, "the sweep hit outside its arc");
                if (now(h) > 2 && hero.combat().lastRelease() > 0 && !hero.combat().running()) {
                    t.assertTrue(struck[0], "the sweep missed the enemy in the open");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code grabHoldsAndThrowsSafely}: a grab holds an NPC soldier and a mock player at the hero's hand for 1 s (their
     * velocity set toward the hand, never a teleport), then throws them away from the lava straight ahead (never toward
     * it, 6.12) and its damage lands on both alike; a grab that finds nobody in reach leaves the hero in a 1.2 s recovery.
     */
    @GameTest(template = "platform", timeoutTicks = 160, batch = "hero_mv_grab")
    public static void grabHoldsAndThrowsSafely(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            String grab = move("seize", "grab", "\"class\": \"heavy\", \"range\": 2.5, \"throw\": 3, \"up\": 1, \"cooldown\": 10, "
                    + "\"frames\": {\"windup\": 0.9, \"active\": 1.0, \"recovery\": 1.2}, " + DMG4);
            String a = "fx_mv_grab", b = "fx_mv_grab2", c = "fx_mv_grab3";
            HeroEntity hn = hero(t, a, def(a, "vanguard", grab, "", ""), 4, 4);
            HeroEntity hp = hero(t, b, def(b, "vanguard", grab, "", ""), 4, 16);
            HeroEntity miss = hero(t, c, def(c, "vanguard", grab, "", ""), 20, 22);
            for (HeroEntity x : List.of(hn, hp, miss)) {
                x.setNoAi(true);
            }
            FactionNpc npc = foe(t, 6, 4, 100);
            // with its AI, so the hold and the throw really move it (a mob without AI does not move by its velocity), but
            // no walking of its own: it neither steps out of reach in the wind-up nor away from the lava by itself
            npc.setNoAi(false);
            npc.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            ServerPlayer pl = player(t, 6, 16, FOE, 100);
            Husk far = husk(t, 20, 18, 200);
            for (int x = 8; x <= 18; x++) {
                for (int dz = -1; dz <= 1; dz++) {
                    block(t, x, 1, 4 + dz, Blocks.LAVA.defaultBlockState());
                    block(t, x, 1, 16 + dz, Blocks.LAVA.defaultBlockState());
                }
            }
            h.runAtTickTime(2, t.guard(() -> {
                force(t, hn, npc, "seize");
                force(t, hp, pl, "seize");
                force(t, miss, far, "seize");
            }));
            int[] held = {0, 0};
            boolean[] thrown = {false, false};
            t.onEachTick(() -> {
                t.assertTrue(!npc.isOnFire() && !npc.isInLava(), "the soldier was thrown toward the lava");
                List<LivingEntity> targets = List.of(npc, pl);
                List<HeroEntity> heroes = List.of(hn, hp);
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = heroes.get(i);
                    LivingEntity g = targets.get(i);
                    if (thrown[i]) {
                        continue;
                    }
                    Vec3 v = g.getDeltaMovement();
                    if (g.getHealth() < 100) {
                        // the throw: away from the lava ahead, its damage on it
                        thrown[i] = true;
                        Vec3 flat = new Vec3(v.x, 0, v.z).normalize();
                        t.assertTrue(flat.x < 0.75, "thrown toward the lava ahead: " + v);
                        t.assertTrue(!HeroEffects.landingHazard(g, flat.x, flat.z, 3), "a throw into danger: " + v);
                    } else if (x.combat().stage() == MoveStage.ACTIVE && x.combat().lastRelease() > 0) {
                        // held at the hero's hand: pulled in (a velocity, never a teleport) and kept close
                        Vec3 toHero = x.position().subtract(g.position());
                        t.assertTrue(g.distanceTo(x) <= 2.6, "a held target got away: " + g.distanceTo(x));
                        if (g == pl) {
                            t.assertTrue(v.x * toHero.x + v.z * toHero.z >= -1e-6, "the held player pushed away: " + v);
                        }
                        held[i]++;
                    }
                }
                if (thrown[0] && thrown[1]) {
                    t.assertTrue(held[0] >= 18 && held[1] >= 18, "held " + held[0] + " and " + held[1] + " ticks");
                    near(t, 100 - npc.getHealth(), 100 - pl.getHealth(), "the grab's damage, soldier and player");
                    MoveStage ms = miss.combat().stage();
                    t.assertTrue(ms == MoveStage.RECOVERY || !miss.combat().running(), "the missed grab " + ms);
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code counterAbsorbsMelee}: a counter stance catches an NPC soldier's blow and a mock player's blow alike: a
     * fifth of each lands (absorb 80%), the riposte strikes and stuns the attacker for the same numbers, and
     * {@code on_counter} fires; an arrow is not countered (it lands in full, the stance stays up); a hero not in its
     * stance takes the whole blow.
     */
    @GameTest(template = "platform", timeoutTicks = 120, batch = "hero_mv_counter")
    public static void counterAbsorbsMelee(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            String counter = move("still", "counter", "\"class\": \"heavy\", \"stance\": 1.5, \"absorb\": 80, \"cooldown\": 10,"
                    + " \"frames\": {\"windup\": 1.5, \"active\": 0.1, \"recovery\": 0.8}, \"effects\": [{\"type\": \"damage\","
                    + " \"amount\": 5}, {\"type\": \"stun\", \"seconds\": 1}]");
            String passive = ", \"passives\": [{\"name\": \"Calm\", \"trigger\": \"on_counter\", \"form\": \"self\", \"cooldown\": 1,"
                    + " \"effects\": [{\"type\": \"stat\", \"stat\": \"armor\", \"percent\": 10, \"seconds\": 5}]}]";
            String a = "fx_mv_ctr", b = "fx_mv_ctr2", c = "fx_mv_ctr3";
            HeroEntity vsNpc = hero(t, a, def(a, "striker", counter, "", passive), 4, 4);
            HeroEntity vsPlayer = hero(t, b, def(b, "striker", counter, "", passive), 4, 16);
            HeroEntity control = hero(t, c, def(c, "striker", counter, "", passive), 18, 10);
            for (HeroEntity x : List.of(vsNpc, vsPlayer, control)) {
                x.setNoAi(true);
            }
            FactionNpc npc = foe(t, 6, 4, 100);
            ServerPlayer pl = player(t, 6, 16, FOE, 100);
            FactionNpc other = foe(t, 20, 10, 100);
            h.runAtTickTime(2, t.guard(() -> {
                force(t, vsNpc, npc, "still");
                force(t, vsPlayer, pl, "still");
            }));
            float[] lost = new float[3];
            h.runAtTickTime(8, t.guard(() -> {
                // arrows are not countered: they land in full and the stance stays up
                float b1 = vsNpc.getHealth(), b2 = vsPlayer.getHealth();
                vsNpc.hurt(vsNpc.damageSources().arrow(new Arrow(h.getLevel(), npc, new ItemStack(Items.ARROW), null), npc), 6);
                vsPlayer.hurt(vsPlayer.damageSources().arrow(new Arrow(h.getLevel(), pl, new ItemStack(Items.ARROW), null), pl), 6);
                t.assertTrue(vsNpc.combat().countering() && vsPlayer.combat().countering(), "an arrow took the stance");
                near(t, b1 - vsNpc.getHealth(), b2 - vsPlayer.getHealth(), "the arrows, soldier's and player's");
                t.assertTrue(b1 - vsNpc.getHealth() > 2, "an arrow was absorbed");
            }));
            // the blows come after the arrows' hurt cooldown: an archer's arrows and blows share one (6.2, kept per attacker
            // since A6a, so clearing the entity's invulnerableTime no longer resets it)
            h.runAtTickTime(19, t.guard(() -> {
                float b1 = vsNpc.getHealth(), b2 = vsPlayer.getHealth(), b3 = control.getHealth();
                vsNpc.hurt(vsNpc.damageSources().mobAttack(npc), 10);
                vsPlayer.hurt(vsPlayer.damageSources().playerAttack(pl), 10);
                control.hurt(control.damageSources().mobAttack(other), 10);
                lost[0] = b1 - vsNpc.getHealth();
                lost[1] = b2 - vsPlayer.getHealth();
                lost[2] = b3 - control.getHealth();
                near(t, lost[0], lost[1], "the countered blows, soldier's and player's");
                near(t, lost[2] * 0.2, lost[0], "a fifth of the blow lands");
            }));
            h.runAtTickTime(23, t.guard(() -> {
                t.assertTrue(npc.getHealth() < 100 && pl.getHealth() < 100, "the riposte did not land");
                near(t, 100 - npc.getHealth(), 100 - pl.getHealth(), "the riposte, on the soldier and on the player");
                t.assertTrue(HeroStatus.stunned(npc) && HeroStatus.stunned(pl), "the riposte's stun");
                t.assertTrue(vsNpc.combat().passiveFirings() == 1 && vsPlayer.combat().passiveFirings() == 1, "on_counter");
                t.assertTrue(!vsNpc.combat().countering(), "the stance answered once and stays up");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code barrageReaims}: each shot of a barrage is aimed at where its target stands when it fires (the target moves
     * between shots), and the shots carry their {@code per_shot} lists in turn (a still target gets all three).
     */
    @GameTest(template = "platform", timeoutTicks = 140, batch = "hero_mv_barrage")
    public static void barrageReaims(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 140);
        try {
            String barrage = move("vials", "barrage", "\"class\": \"light\", \"count\": 3, \"interval\": 0.4, \"range\": 16,"
                    + " \"projectile\": \"slime_ball\", \"cooldown\": 12, \"effects\": [{\"type\": \"damage\", \"amount\": 1}],"
                    + " \"per_shot\": [[{\"type\": \"effect\", \"id\": \"slowness\", \"level\": 1, \"seconds\": 10}],"
                    + " [{\"type\": \"effect\", \"id\": \"weakness\", \"level\": 1, \"seconds\": 10}],"
                    + " [{\"type\": \"effect\", \"id\": \"mining_fatigue\", \"level\": 1, \"seconds\": 10}]]");
            String a = "fx_mv_bar", b = "fx_mv_bar2";
            HeroEntity mover = hero(t, a, def(a, "marksman", barrage, "", ""), 4, 4);
            HeroEntity still = hero(t, b, def(b, "marksman", barrage, "", ""), 4, 18);
            mover.setNoAi(true);
            still.setNoAi(true);
            FactionNpc moving = foe(t, 12, 4, 100), standing = foe(t, 10, 18, 100);
            h.runAtTickTime(2, t.guard(() -> {
                force(t, mover, moving, "vials");
                force(t, still, standing, "vials");
            }));
            Set<Integer> seen = new HashSet<>();
            int[] aimed = {0};
            t.onEachTick(() -> {
                long r = mover.combat().lastRelease();
                if (r > 0 && (now(h) - r == 3 || now(h) - r == 11)) {
                    moving.teleportTo(moving.getX(), moving.getY(), moving.getZ() + 2); // it steps aside between shots
                }
                AABB box = new AABB(mover.blockPosition()).inflate(3);
                for (HeroProjectile p : h.getLevel().getEntitiesOfClass(HeroProjectile.class, box, p -> p.getOwner() == mover)) {
                    if (seen.add(p.getId())) {
                        Vec3 to = moving.position().add(0, moving.getBbHeight() * 0.6, 0).subtract(p.position()).normalize();
                        Vec3 v = p.getDeltaMovement().normalize();
                        t.assertTrue(v.dot(to) > 0.995, "shot " + seen.size() + " not aimed at the target now: " + v + " vs " + to);
                        aimed[0]++;
                    }
                }
                if (aimed[0] == 3 && standing.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && standing.hasEffect(MobEffects.WEAKNESS)
                        && standing.hasEffect(MobEffects.DIG_SLOWDOWN)) {
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the effects (6.6)

    /**
     * {@code barrierBreaksAfterHits}: a barrier of 3 hits blocks all damage, a soldier's and a player's blows alike each
     * taking one hit (fire is blocked and takes none); the third blow breaks it and the fourth lands.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "hero_mv_barrier")
    public static void barrierBreaksAfterHits(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 100);
        try {
            String id = "fx_mv_barrier";
            HeroEntity hero = hero(t, id, def(id, "support", move("talismans", "self", "\"class\": \"heavy\", \"cooldown\": 20,"
                    + " \"effects\": [{\"type\": \"barrier\", \"hits\": 3, \"seconds\": 10}]"), "", ""), 6, 6);
            hero.setNoAi(true);
            FactionNpc npc = foe(t, 8, 6, 100);
            ServerPlayer pl = player(t, 4, 6, FOE, 100);
            h.runAtTickTime(2, t.guard(() -> force(t, hero, npc, "talismans")));
            h.runAtTickTime(40, t.guard(() -> {
                t.assertTrue(hero.combat().barrierHits() == 3, "no barrier: " + hero.combat().barrierHits());
                float full = hero.getHealth();
                t.assertTrue(!hero.hurt(hero.damageSources().inFire(), 2), "fire went through");
                t.assertTrue(hero.combat().barrierHits() == 3, "fire took a hit");
                t.assertTrue(!hero.hurt(hero.damageSources().mobAttack(npc), 5), "a soldier's blow went through");
                t.assertTrue(!hero.hurt(hero.damageSources().playerAttack(pl), 5), "a player's blow went through");
                t.assertTrue(hero.combat().barrierHits() == 1, "two blows, two hits: " + hero.combat().barrierHits());
                t.assertTrue(!hero.hurt(hero.damageSources().mobAttack(npc), 5), "the third blow went through");
                t.assertTrue(hero.combat().barrierHits() == 0, "the barrier held after its hits");
                near(t, full, hero.getHealth(), "nothing landed through the barrier");
                t.assertTrue(hero.hurt(hero.damageSources().playerAttack(pl), 5), "the fourth blow was blocked");
                t.assertTrue(hero.getHealth() < full, "the fourth blow did nothing");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code guardBreakDisablesShield}: a hero's {@code guard_break} knocks a mock player's and an NPC soldier's raised
     * shields down for the same 3 s: neither goes up again (the soldier tries every tick, the player is asked to) until
     * then, both go up after; the player's shield also shows vanilla's cooldown.
     */
    @GameTest(template = "platform", timeoutTicks = 160, batch = "hero_mv_guard")
    public static void guardBreakDisablesShield(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            String id = "fx_mv_guard";
            HeroEntity hero = hero(t, id, def(id, move("splitter", "nova", "\"class\": \"heavy\", \"radius\": 4, \"cooldown\": 12,"
                    + " \"effects\": [{\"type\": \"damage\", \"amount\": 1}, {\"type\": \"guard_break\", \"seconds\": 3}]")), 12, 12);
            hero.setNoAi(true);
            FactionNpc arch = archer(t, 20, 4);
            FactionNpc npc = shieldBearer(t, 14, 12, 100, arch);
            ServerPlayer pl = player(t, 10, 12, FOE, 100);
            pl.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            pl.startUsingItem(InteractionHand.OFF_HAND);
            h.runAtTickTime(2, t.guard(() -> {
                t.assertTrue(HeroStatus.shieldUp(npc) && HeroStatus.shieldUp(pl), "the shields are not up");
                force(t, hero, npc, "splitter");
            }));
            long[] broke = {-1};
            long[] upAgain = {-1, -1};
            t.onEachTick(() -> {
                long now = now(h);
                if (broke[0] < 0) {
                    if (hero.combat().lastRelease() > 0) {
                        broke[0] = now;
                        t.assertTrue(!HeroStatus.shieldUp(npc) && !HeroStatus.shieldUp(pl), "a shield stayed up");
                        t.assertTrue(pl.getCooldowns().isOnCooldown(Items.SHIELD), "the player's shield has no cooldown");
                    }
                    return;
                }
                if (!pl.isUsingItem()) {
                    pl.startUsingItem(InteractionHand.OFF_HAND);
                }
                boolean[] up = {HeroStatus.shieldUp(npc), HeroStatus.shieldUp(pl)};
                for (int i = 0; i < 2; i++) {
                    if (up[i] && upAgain[i] < 0) {
                        upAgain[i] = now;
                    }
                }
                if (upAgain[0] > 0 && upAgain[1] > 0) {
                    long ticks = HeroRules.ticks(3);
                    t.assertTrue(upAgain[0] - broke[0] >= ticks && upAgain[0] - broke[0] <= ticks + 1,
                            "the soldier's shield was down " + (upAgain[0] - broke[0]) + " ticks");
                    t.assertTrue(upAgain[0] == upAgain[1], "the soldier's and the player's shields came back on different ticks: "
                            + upAgain[0] + " " + upAgain[1]);
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code callGuardsRetargets}: {@code call_guards} with a count of 2 sends the two nearest free soldiers of the
     * hero's faction at its target; a third soldier and a hero of its side are not called; nothing is spawned.
     */
    @GameTest(template = "platform", timeoutTicks = 80, batch = "hero_mv_guards")
    public static void callGuardsRetargets(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 80);
        try {
            String id = "fx_mv_guards";
            HeroEntity hero = hero(t, id, def(id, move("call", "self", "\"class\": \"light\", \"cooldown\": 20,"
                    + " \"effects\": [{\"type\": \"call_guards\", \"count\": 2}]")), 12, 12);
            hero.setNoAi(true);
            List<FactionNpc> soldiers = new ArrayList<>();
            for (int[] p : new int[][] {{14, 12}, {12, 16}, {20, 20}}) {
                FactionNpc s = t.npc(p[0], p[1], NpcRole.SWORDSMAN);
                s.setNoAi(true);
                soldiers.add(s);
            }
            FactionNpc enemy = foe(t, 6, 12, 100);
            int before = h.getLevel().getEntitiesOfClass(FactionNpc.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40)).size();
            h.runAtTickTime(2, t.guard(() -> force(t, hero, enemy, "call")));
            h.runAtTickTime(40, t.guard(() -> {
                t.assertTrue(soldiers.get(0).getTarget() == enemy && soldiers.get(1).getTarget() == enemy,
                        "the two nearest did not come: " + soldiers.get(0).getTarget() + ", " + soldiers.get(1).getTarget());
                t.assertTrue(soldiers.get(2).getTarget() != enemy, "a third soldier came for a call of two");
                int after = h.getLevel().getEntitiesOfClass(FactionNpc.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40)).size();
                t.assertTrue(after == before, "something was spawned");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code silencePoiseOnHeroes}: a {@code silence} keeps an enemy hero from starting any move for its seconds (it
     * starts one after), and a {@code poise} effect fills its poise bar, the second at full staggering it (the stagger
     * hook: no move, the {@code stagger} clip, {@code on_stagger}).
     */
    @GameTest(template = "platform", timeoutTicks = 240, batch = "hero_mv_silence")
    public static void silencePoiseOnHeroes(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 240);
        try {
            String a = "fx_mv_hush", b = "fx_mv_hushed";
            HeroEntity caster = hero(t, a, def(a, "controller", move("hush", "bolt", "\"class\": \"light\", \"range\": 12, \"cooldown\": 5,"
                    + " \"effects\": [{\"type\": \"silence\", \"seconds\": 3}, {\"type\": \"poise\", \"amount\": 30}]"), "", ""), 4, 12);
            caster.setNoAi(true);
            String stag = ", \"passives\": [{\"name\": \"Dazed\", \"trigger\": \"on_stagger\", \"form\": \"self\", \"cooldown\": 1,"
                    + " \"effects\": [{\"type\": \"stat\", \"stat\": \"armor\", \"percent\": 10, \"seconds\": 5}]}]";
            HeroEntity victim = hero(t, b, visual(b), def(b, "striker", move("poke", "bolt", "\"class\": \"light\", \"range\": 12,"
                    + " \"cooldown\": 4, " + DMG4), ", \"poise\": 50", stag), FOE, 10, 12);
            Husk dummy = husk(t, 14, 12, 500);
            victim.setTarget(dummy);
            h.runAtTickTime(2, t.guard(() -> force(t, caster, victim, "hush")));
            long[] silencedAt = {-1};
            t.onEachTick(() -> {
                long now = now(h);
                if (silencedAt[0] < 0) {
                    if (HeroStatus.silenced(victim)) {
                        silencedAt[0] = now;
                        near(t, 30, victim.combat().poise().value(), "the first poise effect");
                        t.assertTrue(!victim.combat().staggered(), "30 of 50 staggered it");
                    }
                    return;
                }
                if (now < silencedAt[0] + HeroRules.ticks(3)) {
                    t.assertTrue(victim.combat().lastMoveStart() < silencedAt[0] || !HeroStatus.silenced(victim),
                            "a silenced hero started a move");
                    return;
                }
                if (now == silencedAt[0] + HeroRules.ticks(3) + 1) {
                    caster.combat().forceMove("hush");
                }
                if (victim.combat().staggered()) {
                    t.assertTrue(HeroActions.code(victim.action()) == HeroActions.actionCode("stagger"), "no stagger clip");
                    t.assertTrue(victim.combat().passiveFirings() >= 1, "on_stagger did not fire");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code tetherPullsBackNpcAndPlayer}: a tether binds an NPC soldier and a mock player to where they stood; within
     * its length nothing pulls, past it both are pulled back toward their anchor at the same speed.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "hero_mv_tether")
    public static void tetherPullsBackNpcAndPlayer(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 100);
        try {
            String id = "fx_mv_tether";
            HeroEntity hero = hero(t, id, def(id, "controller", move("chain", "nova", "\"class\": \"light\", \"radius\": 4,"
                    + " \"cooldown\": 8, \"effects\": [{\"type\": \"tether\", \"seconds\": 4, \"max\": 4}]"), "", ""), 12, 12);
            hero.setNoAi(true);
            FactionNpc npc = foe(t, 14, 12, 100);
            ServerPlayer pl = player(t, 10, 12, FOE, 100);
            h.runAtTickTime(2, t.guard(() -> force(t, hero, npc, "chain")));
            h.runAtTickTime(30, t.guard(() -> {
                t.assertTrue(npc.getDeltaMovement().horizontalDistance() < 0.05, "a pull within the tether's length");
                npc.moveTo(npc.getX() + 6, npc.getY(), npc.getZ());
                pl.moveTo(pl.getX() - 6, pl.getY(), pl.getZ()); // a mock player's own teleport goes to its (fake) client
                npc.setDeltaMovement(Vec3.ZERO);
                pl.setDeltaMovement(Vec3.ZERO);
            }));
            h.runAtTickTime(31, t.guard(() -> {
                Vec3 vn = npc.getDeltaMovement(), vp = pl.getDeltaMovement();
                t.assertTrue(vn.x < -0.1, "the soldier was not pulled back: " + vn);
                t.assertTrue(vp.x > 0.1, "the player was not pulled back: " + vp);
                t.assertTrue(pl.hurtMarked || vp.x > 0.1, "the player's pull was not sent");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code spawnAddMakesDecoys}: {@code spawn_add} puts decoys of its side beside the hero ({@code adds_alive}); at
     * most 3 live (a fourth pops the oldest); any hit pops one ({@code on_add_died} fires); an enemy hero takes a decoy
     * for an enemy; the rest pop after 10 s.
     */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "hero_mv_decoy")
    public static void spawnAddMakesDecoys(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            String a = "fx_mv_decoy", b = "fx_mv_rival";
            String passive = ", \"passives\": [{\"name\": \"Smoke\", \"trigger\": \"on_add_died\", \"form\": \"self\", \"cooldown\": 1,"
                    + " \"effects\": [{\"type\": \"stat\", \"stat\": \"speed\", \"percent\": 10, \"seconds\": 3}]}]";
            HeroEntity hero = hero(t, a, def(a, "skirmisher", move("clones", "self", "\"class\": \"heavy\", \"cooldown\": 16,"
                    + " \"effects\": [{\"type\": \"spawn_add\", \"add\": \"decoy\", \"count\": 2}]"), "", passive), 12, 12);
            hero.setNoAi(true);
            HeroEntity rival = hero(t, b, visual(b), def(b, ""), FOE, 4, 4);
            rival.setNoAi(true);
            FactionNpc npc = foe(t, 20, 20, 100);
            h.runAtTickTime(2, t.guard(() -> force(t, hero, npc, "clones")));
            List<HeroDecoy> mine = new ArrayList<>();
            h.runAtTickTime(40, t.guard(() -> {
                mine.addAll(h.getLevel().getEntitiesOfClass(HeroDecoy.class, new AABB(hero.blockPosition()).inflate(8),
                        d -> d.owner() == hero));
                t.assertTrue(mine.size() == 2 && hero.combat().addsAlive() == 2, "decoys " + mine.size());
                t.assertTrue(mine.get(0).getHealth() <= 1, "a decoy has 1 health");
                t.assertTrue(com.factionclash.hero.HeroForms.enemy(rival, mine.get(0)), "an enemy hero is not fooled");
                t.assertTrue(!com.factionclash.hero.HeroForms.enemy(hero, mine.get(0)), "its own hero strikes its decoy");
                t.assertTrue(hero.combat().forceMove("clones"), "a second cast");
            }));
            h.runAtTickTime(80, t.guard(() -> {
                t.assertTrue(hero.combat().addsAlive() == 3, "at most 3 decoys: " + hero.combat().addsAlive());
                long gone = mine.stream().filter(HeroDecoy::isRemoved).count();
                t.assertTrue(gone == 1, "one of the first two (the oldest) should have popped for the new ones: " + gone);
                HeroDecoy d = h.getLevel().getEntitiesOfClass(HeroDecoy.class, new AABB(hero.blockPosition()).inflate(8),
                        x -> x.owner() == hero && !x.isRemoved()).get(0);
                int firings = hero.combat().passiveFirings();
                d.hurt(d.damageSources().mobAttack(npc), 0.5F);
                t.assertTrue(d.isRemoved() && hero.combat().addsAlive() == 2, "a hit did not pop it");
                t.assertTrue(hero.combat().passiveFirings() > firings, "on_add_died did not fire");
            }));
            h.runAtTickTime(300 - 20, t.guard(() -> {
                t.assertTrue(hero.combat().addsAlive() == 0, "decoys outlived their 10 s: " + hero.combat().addsAlive());
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code channelBreaksAfterItsShare}: damage of a channelled move's share (15% of max health) in its active part
     * breaks it: no more waves roll, the {@code interrupt} clip plays and a 2 s recovery follows. Since A6a one blow
     * takes at most 12% (the per-hit cap), so the share comes from two soldiers' blows: the first does not break it.
     */
    @GameTest(template = "platform", timeoutTicks = 160, batch = "hero_mv_channel")
    public static void channelBreaksAfterItsShare(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            String id = "fx_mv_channel";
            HeroEntity hero = hero(t, id, def(id, move("tremor", "wave", "\"class\": \"area\", \"radius\": 6, \"speed\": 8,"
                    + " \"waves\": 3, \"interval\": 1.0, \"channel\": {\"interrupt\": 15}, \"cooldown\": 14, \"frames\": {\"windup\": 1.0,"
                    + " \"active\": 2.2, \"recovery\": 0.8}, " + DMG4)), 12, 12);
            hero.setNoAi(true);
            FactionNpc npc = foe(t, 14, 12, 500);
            FactionNpc hitter = foe(t, 10, 12, 100), second = foe(t, 10, 14, 100);
            h.runAtTickTime(2, t.guard(() -> force(t, hero, npc, "tremor")));
            long[] broke = {-1};
            float[] afterFirst = {0};
            t.onEachTick(() -> {
                long r = hero.combat().lastRelease();
                long now = now(h);
                if (r > 0 && broke[0] < 0 && now == r + 8) {
                    afterFirst[0] = npc.getHealth();
                    t.assertTrue(afterFirst[0] < 500, "the first wave did not land");
                    hero.hurt(hero.damageSources().mobAttack(hitter), (float) (hero.getMaxHealth() * 0.1));
                    t.assertTrue(hero.combat().stage() == MoveStage.ACTIVE, "one blow under the cap broke the channel");
                    hero.hurt(hero.damageSources().mobAttack(second), (float) (hero.getMaxHealth() * 0.1));
                    broke[0] = now;
                    t.assertTrue(hero.combat().stage() == MoveStage.RECOVERY, "the channel held: " + hero.combat().stage());
                    t.assertTrue(HeroActions.code(hero.action()) == HeroActions.actionCode("interrupt"), "no interrupt clip");
                }
                if (broke[0] > 0 && now == broke[0] + 39) {
                    t.assertTrue(hero.combat().running(), "the 2 s recovery ended early");
                }
                if (broke[0] > 0 && now == broke[0] + 45) {
                    t.assertTrue(!hero.combat().running(), "the recovery lasted more than 2 s");
                    near(t, afterFirst[0], npc.getHealth(), "a wave rolled after the channel broke");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code unblockableGoesThroughShields}: a grab's throw (unblockable, as every grab is) lands its damage through a
     * raised, blocking shield, a mock player's and an NPC soldier's alike; the same blow from a blockable move is
     * stopped by the soldier's shield, and, as that move {@code breaks_guard}, knocks the shield aside (the axe rule).
     */
    @GameTest(template = "platform", timeoutTicks = 160, batch = "hero_mv_unblock")
    public static void unblockableGoesThroughShields(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            String grab = move("seize", "grab", "\"class\": \"heavy\", \"range\": 2.5, \"throw\": 1, \"cooldown\": 10, "
                    + "\"frames\": {\"windup\": 0.9, \"active\": 1.0, \"recovery\": 1.2}, " + DMG4);
            String jab = move("jab", "melee", "\"class\": \"light\", \"cooldown\": 6, \"breaks_guard\": true, " + DMG4);
            String a = "fx_mv_ub", b = "fx_mv_ub2", c = "fx_mv_ub3";
            HeroEntity hn = hero(t, a, def(a, "vanguard", grab, "", ""), 4, 4);
            HeroEntity hp = hero(t, b, def(b, "vanguard", grab, "", ""), 4, 16);
            HeroEntity hj = hero(t, c, def(c, "striker", jab, "", ""), 18, 10);
            for (HeroEntity x : List.of(hn, hp, hj)) {
                x.setNoAi(true);
            }
            FactionNpc arch = archer(t, 2, 22);
            FactionNpc npc = shieldBearer(t, 6, 4, 100, arch), blocker = shieldBearer(t, 20, 10, 100, arch);
            ServerPlayer pl = blockingPlayer(t, 6, 16, FOE, 100);
            pl.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            pl.startUsingItem(InteractionHand.OFF_HAND);
            for (LivingEntity e : List.<LivingEntity>of(npc, blocker, pl)) {
                // face the heroes (west), so the shield covers the blow
                e.setYRot(90F);
                e.setYHeadRot(90F);
                e.yBodyRot = 90F;
            }
            h.runAtTickTime(10, t.guard(() -> {
                t.assertTrue(npc.isBlocking() && pl.isBlocking() && blocker.isBlocking(), "a shield is not blocking");
                force(t, hn, npc, "seize");
                force(t, hp, pl, "seize");
                force(t, hj, blocker, "jab");
            }));
            h.runAtTickTime(90, t.guard(() -> {
                t.assertTrue(npc.getHealth() < 100, "the grab's damage was blocked by the soldier's shield");
                t.assertTrue(pl.getHealth() < 100, "the grab's damage was blocked by the player's shield");
                near(t, 100 - npc.getHealth(), 100 - pl.getHealth(), "the same damage through both shields");
                t.assertTrue(blocker.getHealth() >= 100, "a blockable blow went through a raised shield");
                t.assertTrue(HeroStatus.guardBroken(blocker) && !HeroStatus.shieldUp(blocker),
                        "a breaks_guard blow left the shield up");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
