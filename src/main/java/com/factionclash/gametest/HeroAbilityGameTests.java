package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.HeroState;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroDamage;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroManager;
import com.factionclash.hero.HeroRegistry;
import com.factionclash.hero.HeroStatus;
import com.factionclash.hero.entity.HeroProjectile;
import com.factionclash.hero.entity.HeroZone;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.Stat;
import com.factionclash.logic.hero.HeroIdentity;
import com.factionclash.logic.hero.MoveSelector;
import com.factionclash.logic.hero.Sense;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Heroes, task A3: the ability core. A hero picks its moves with the move selector after the fight's opening gap and
 * runs them (wind-up, hits, recovery); the v1 forms and the combo reach exactly whom they should (enemies, never
 * allies, animals or what a wall hides); the effects of HERO_FORMAT.md 32 phase 4 do what they say and end on time;
 * energy builds and the ultimate spends it; cooldowns and energy survive a save; projectiles change no block; a
 * thrown hero throws. <b>Equal treatment</b> (HEROES_V2 6.15): every effect that can land on a player is checked on an
 * NPC soldier and on a mock player (a survival {@link FakePlayer} in the level, of an enemy faction), with the same
 * numbers for both: the same combo damage, the same knock-back and pull, the same stat and potion, the same shield and
 * the same slot cap. Fixture heroes are format-2 definitions registered for the test; the slice heroes' fixtures come
 * from {@code src/test/resources/heroes/slice} ({@code -Dfactionclash.sliceFixtures}, set by the GameTest run). Each
 * test that runs a hero has a batch of its own.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroAbilityGameTests {
    private HeroAbilityGameTests() {
    }

    private static final double EPS = 1e-3;
    /** The heroes' side and their enemies' (hostile with no pact). */
    private static final FactionType SIDE = FactionType.EMBER_LEGION, FOE = FactionType.GROVE_COVENANT;

    // ------------------------------------------------------------------ fixtures

    private static String visual(String style) {
        return "{\"form\":\"adult\",\"build\":\"standard\",\"base\":\"adult_f\",\"style\":\"" + style + "\",\"height_px\":52,"
                + "\"head_y\":42,\"render_scale\":0.56,\"scale\":1.0,\"animation_chain\":[\"_fallback\",\"_style_fists\",\"_core\"],"
                + "\"texture\":{\"width\":256,\"height\":128}}";
    }

    static final String MELEE = "{\"style\": \"melee\"}";

    /** A common striker (84 boss health at level 1) with these moves and extra top-level fields. */
    static String def(String id, String basic, String role, String moves, String more) {
        return "{\"format\": 2, \"id\": \"" + id + "\", \"name\": \"" + HeroIdentity.nameFromId(id) + "\", \"gender\": \"female\","
                + " \"archetype\": \"samurai\", \"rarity\": \"common\", \"behaviour\": {\"combat_role\": \"" + role + "\"},"
                + " \"basic_attack\": " + basic + ", \"moves\": [" + moves + "], \"boss\": {\"weak_points\": [\"back\"],"
                + " \"phases\": [{\"at\": 100}]}, \"personality\": {\"temperament\": \"stoic\"}, \"leader\": {\"eligible\": false}"
                + more + "}";
    }

    /** A move; {@code fields} are its own (form fields, effects, weight...). */
    static String move(String id, String form, String fields) {
        return "{\"id\": \"" + id + "\", \"name\": \"" + id + "\", \"form\": \"" + form + "\", " + fields + "}";
    }

    /** A hero of {@link #SIDE} from a definition text, given at relative (x, 2, z); the cleanup removes it. */
    static HeroEntity hero(GameTestBases.Handle t, String id, String style, String definition, int x, int z) {
        HeroRegistry.Transient fx = HeroRegistry.registerTransient(id, visual(style), definition);
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
        return r.hero();
    }

    /** A slice hero's fixture text ({@code src/test/resources/heroes/slice}). */
    static String slice(String id) {
        String dir = System.getProperty("factionclash.sliceFixtures");
        if (dir == null) {
            throw new GameTestAssertException("-Dfactionclash.sliceFixtures is not set (the GameTest run in build.gradle sets it)");
        }
        try {
            return Files.readString(Path.of(dir, id + ".json"));
        } catch (IOException e) {
            throw new GameTestAssertException("no slice fixture " + id + ": " + e);
        }
    }

    /** An enemy soldier of {@link #FOE} at relative (x, 2, z), still (no AI), with {@code health} max health. */
    static FactionNpc foe(GameTestBases.Handle t, int x, int z, double health) {
        FactionNpc n = t.track(t.helper.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(x, 2, z)));
        n.faction(FOE);
        n.role(NpcRole.SWORDSMAN);
        n.setNoAi(true);
        n.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        n.setHealth((float) health);
        n.setOnGround(true);
        return noRegen(n);
    }

    /**
     * No natural healing during the test (food under 18, as a player's would be), so what it loses is what it was
     * dealt; the mock player does not heal either.
     */
    static FactionNpc noRegen(FactionNpc n) {
        n.hunger().set(17, 0F, 0F);
        return n;
    }

    /** A still husk at relative (x, 2, z) with {@code health} max health. */
    static Husk husk(GameTestBases.Handle t, int x, int z, double health) {
        Husk m = t.track(t.helper.spawn(EntityType.HUSK, new BlockPos(x, 2, z)));
        m.setNoAi(true);
        m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        m.setHealth((float) health);
        return m;
    }

    /**
     * A survival mock player standing at relative (x, 2, z), a member of {@code faction} (null: none), in the level
     * so areas find it. A NeoForge {@link FakePlayer} that can be hurt; it ticks like a living entity (its effects run
     * out, its fire burns) but never moves on its own. The cleanup removes it and its membership.
     */
    static ServerPlayer player(GameTestBases.Handle t, int x, int z, FactionType faction, double health) {
        ServerLevel level = t.helper.getLevel();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "hero-test-player")) {
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
            // a freshly joined player is invulnerable for 3 s (ServerPlayer.spawnInvulnerableTime); this one has been here
            java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(p, 0);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("cannot clear the mock player's spawn invulnerability: " + e);
        }
        CampaignData data = CampaignData.get(level.getServer());
        if (faction != null) {
            data.players.put(p.getUUID(), faction);
        }
        level.addFreshEntity(p);
        t.onCleanup(() -> {
            data.players.remove(p.getUUID());
            p.discard();
        });
        return p;
    }

    /** Starts {@code move} of {@code hero} at {@code target} now, outside the selector (the fixtures' moves weigh 0). */
    static void force(GameTestBases.Handle t, HeroEntity hero, LivingEntity target, String move) {
        hero.setTarget(target);
        t.assertTrue(hero.combat().forceMove(move), "the hero would not start " + move + " (running: "
                + hero.combat().runningMove() + ")");
    }

    static void near(GameTestBases.Handle t, double expected, double actual, String what) {
        t.assertTrue(Math.abs(expected - actual) < EPS, what + ": expected " + expected + ", got " + actual);
    }

    static boolean heroHit(LivingEntity e, HeroEntity hero) {
        DamageSource s = e.getLastDamageSource();
        return s != null && s.is(HeroDamage.HERO) && s.getEntity() == hero;
    }

    private static final String WEIGHT0 = "\"weight\": 0";

    // ------------------------------------------------------------------ the selector runs moves (v1 31.2, 6.3)

    /**
     * {@code heroSkillDamagesEnemy}: a hero fighting a husk picks its move by itself after the fight's opening gap (no
     * sooner than 1.5 s plus the 0.4 s wind-up, 6.3), the move hurts the husk with the hero's damage type, the use
     * gives the hero the skill's energy, and the campaign counts the cast.
     */
    @GameTest(template = "platform", timeoutTicks = 260, batch = "hero_ab_skill")
    public static void heroSkillDamagesEnemy(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 260);
        try {
            String id = "fx_ab_skill";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker",
                    move("cut", "melee", "\"cooldown\": 6, \"effects\": [{\"type\": \"damage\", \"amount\": 6}]"), ""), 6, 6);
            Husk husk = husk(t, 8, 6, 200);
            hero.setTarget(husk);
            long engaged = h.getLevel().getGameTime();
            t.onEachTick(() -> {
                if (hero.combat().lastRelease() > 0 && heroHit(husk, hero)) {
                    long after = hero.combat().lastRelease() - engaged;
                    t.assertTrue(after >= 38, "the move landed " + after + " ticks after the fight began (gap + wind-up >= 38)");
                    t.assertTrue("cut".equals(hero.combat().lastMove()), "move " + hero.combat().lastMove());
                    t.assertTrue(husk.getHealth() < 200, "the husk was not hurt");
                    t.assertTrue(hero.energy() >= 10, "a skill's use gives its energy_gain: " + hero.energy());
                    HeroState s = hero.campaignRecord();
                    t.assertTrue(s != null && s.casts.getOrDefault("cut", 0) >= 1, "casts " + (s == null ? null : s.casts));
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroUltimateAtFullEnergy}: the ultimate waits for 100 energy; the hero's own basic hits fill it, the
     * selector then picks the ultimate (a big fight: a husk of 100 health), which spends all the energy and lands.
     */
    @GameTest(template = "platform", timeoutTicks = 400, batch = "hero_ab_ult")
    public static void heroUltimateAtFullEnergy(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 400);
        try {
            String id = "fx_ab_ult";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker", "",
                    ", \"ultimate\": {\"id\": \"quake\", \"name\": \"Quake\", \"form\": \"nova\", \"radius\": 5,"
                            + " \"effects\": [{\"type\": \"damage\", \"amount\": 8}], \"line\": \"Quake!\"}"), 6, 6);
            Husk husk = husk(t, 8, 6, 100);
            hero.setEnergy(95);
            hero.setTarget(husk);
            boolean[] seen = new boolean[1];
            t.onEachTick(() -> {
                boolean ult = "quake".equals(hero.combat().lastMove());
                if (!ult) {
                    t.assertTrue(hero.combat().lastMoveStart() < 0, "another move started");
                    return;
                }
                if (!seen[0]) {
                    seen[0] = true;
                    t.assertTrue(hero.energy() == 0, "the ultimate did not spend the energy: " + hero.energy());
                    t.assertTrue(hero.lastLine().equals("Quake!"), "the ultimate's line: " + hero.lastLine());
                }
                if (hero.combat().lastRelease() > 0 && heroHit(husk, hero)) {
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Cooldowns and energy survive a save (HEROES_V2 3.3, SLICE_PLAN 2): after a cast, the hero's NBT loaded into a new
     * entity has the same energy and the move still cooling down to the same game time.
     */
    @GameTest(template = "platform", timeoutTicks = 260, batch = "hero_ab_save")
    public static void cooldownsAndEnergySurviveASave(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 260);
        try {
            String id = "fx_ab_save";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker",
                    move("cut", "melee", "\"cooldown\": 30, \"effects\": [{\"type\": \"damage\", \"amount\": 3}]"), ""), 6, 6);
            Husk husk = husk(t, 8, 6, 300);
            hero.setTarget(husk);
            t.onEachTick(() -> {
                if (hero.combat().lastMoveStart() < 0) {
                    return;
                }
                long now = h.getLevel().getGameTime();
                MoveSelector s = hero.combat().selector();
                HeroDef.Move cut = hero.definition().v2().move("cut");
                long ready = s.readyAt(cut, now);
                t.assertTrue(ready > now + 400, "30 s of cooldown after the cast: ready at " + ready + ", now " + now);
                int energy = hero.energy();
                t.assertTrue(energy > 0, "no energy after a cast");
                CompoundTag tag = hero.saveWithoutId(new CompoundTag());
                HeroEntity loaded = ModEntities.HERO.get().create(h.getLevel());
                loaded.load(tag);
                t.assertTrue(loaded.energy() == energy, "energy " + loaded.energy() + " after loading, " + energy + " before");
                MoveSelector back = loaded.combat().selector();
                t.assertTrue(back != null && back.readyAt(cut, now) == ready && back.charges(cut, now, energy) == 0,
                        "the cooldown did not survive: ready at " + (back == null ? -1 : back.readyAt(cut, now)));
                t.succeed();
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code cooldown_cut} and {@code energy} (v1 13.5): a self move cuts the running cooldown of the hero's other
     * move by its seconds (never under half) and gives its energy beside the move's own {@code energy_gain}.
     */
    @GameTest(template = "platform", timeoutTicks = 80, batch = "hero_ab_cut")
    public static void cooldownCutAndEnergyEffects(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 80);
        try {
            String id = "fx_ab_cut";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker",
                    move("cut", "melee", "\"cooldown\": 20, \"effects\": [{\"type\": \"damage\", \"amount\": 2}]") + ", "
                            + move("rally", "self", WEIGHT0 + ", \"effects\": [{\"type\": \"cooldown_cut\", \"seconds\": 3},"
                            + " {\"type\": \"energy\", \"amount\": 20}]"), ""), 6, 6);
            hero.setNoAi(true);
            long now = h.getLevel().getGameTime();
            MoveSelector s = hero.combat().selector();
            HeroDef.Move cut = hero.definition().v2().move("cut");
            s.fightStarted(now - 2000);
            Sense sense = new Sense().target(new Sense.TargetState().distance(2));
            MoveSelector.Pick p = s.check(now, sense);
            t.assertTrue(p != null && p.move() == cut, "the selector did not book cut: " + p);
            long ready = s.readyAt(cut, now);
            near(t, now + 400, ready, "20 s");
            int e0 = hero.energy();
            t.assertTrue(hero.combat().forceMove("rally"), "rally did not start");
            h.runAtTickTime(20, t.guard(() -> {
                long n = h.getLevel().getGameTime();
                near(t, ready - 60, s.readyAt(cut, n), "3 s sooner");
                t.assertTrue(hero.energy() == e0 + 10 + 20, "energy " + e0 + " -> " + hero.energy() + " (10 for the use, 20 given)");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ forms (v1 10.1)

    /**
     * {@code heroNovaSparesAlliesAndAnimals}: a nova hurts and slows the enemies within its radius (a husk, an enemy
     * soldier) and nobody else: not an allied soldier, not a cow, not an enemy outside it. (The slowness shows the
     * routing itself spares allies; their damage would also be stopped by the friendly-fire rule.)
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "hero_ab_nova")
    public static void heroNovaSparesAlliesAndAnimals(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 60);
        try {
            String id = "fx_ab_nova";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker",
                    move("burst", "nova", WEIGHT0 + ", \"radius\": 4, \"effects\": [{\"type\": \"damage\", \"amount\": 4},"
                            + " {\"type\": \"effect\", \"id\": \"slowness\", \"level\": 1, \"seconds\": 3}]"), ""), 8, 8);
            hero.setNoAi(true);
            Husk husk = husk(t, 10, 8, 100);
            FactionNpc enemy = foe(t, 8, 10, 100);
            FactionNpc ally = noRegen(t.npc(6, 8, NpcRole.SWORDSMAN));
            ally.setNoAi(true);
            Cow cow = t.track(h.spawn(EntityType.COW, new BlockPos(8, 2, 6)));
            cow.setNoAi(true);
            FactionNpc far = foe(t, 14, 8, 100);
            float allyH = ally.getHealth(), cowH = cow.getHealth();
            force(t, hero, husk, "burst");
            h.runAtTickTime(35, t.guard(() -> {
                t.assertTrue(husk.getHealth() < 100 && heroHit(husk, hero), "the husk was spared");
                t.assertTrue(enemy.getHealth() < 100 && heroHit(enemy, hero), "the enemy soldier was spared");
                near(t, allyH, ally.getHealth(), "the ally was hurt");
                t.assertTrue(!ally.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the ally was slowed like an enemy");
                t.assertTrue(enemy.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the enemy soldier was not slowed");
                near(t, cowH, cow.getHealth(), "the cow was hurt");
                t.assertTrue(!cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the cow was slowed");
                near(t, 100, far.getHealth(), "an enemy 6 blocks away was hurt by a 4-block nova");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * A chain jumps from its target to the nearest new enemy within its radius, 0.15 s apart, never twice to one, and
     * stops when no enemy is left within reach.
     */
    @GameTest(template = "platform", timeoutTicks = 80, batch = "hero_ab_chain")
    public static void chainJumpsBetweenEnemies(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 80);
        try {
            String id = "fx_ab_chain";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker",
                    move("arc", "chain", WEIGHT0 + ", \"range\": 10, \"radius\": 4, \"count\": 4,"
                            + " \"effects\": [{\"type\": \"damage\", \"amount\": 3}]"), ""), 4, 6);
            hero.setNoAi(true);
            Husk[] row = {husk(t, 7, 6, 100), husk(t, 10, 6, 100), husk(t, 13, 6, 100)};
            Husk away = husk(t, 13, 16, 100);
            long[] first = new long[3];
            force(t, hero, row[0], "arc");
            t.onEachTick(() -> {
                for (int i = 0; i < 3; i++) {
                    if (first[i] == 0 && row[i].getHealth() < 100) {
                        first[i] = h.getLevel().getGameTime();
                    }
                }
            });
            h.runAtTickTime(60, t.guard(() -> {
                t.assertTrue(first[0] > 0 && first[1] > 0 && first[2] > 0, "a link was missed");
                t.assertTrue(first[1] - first[0] == 3 && first[2] - first[1] == 3, "0.15 s a jump: " + first[0] + ", "
                        + first[1] + ", " + first[2]);
                float one = 100 - row[0].getHealth();
                for (Husk m : row) {
                    near(t, one, 100 - m.getHealth(), "every link hit once");
                }
                near(t, 100, away.getHealth(), "the chain jumped 10 blocks");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * A cone reaches what is in front of the hero and not what is behind it; a line toward a husk behind a stone wall
     * hurts what is before the wall and stops there (v1 10.1).
     */
    @GameTest(template = "platform", timeoutTicks = 120, batch = "hero_ab_shapes")
    public static void coneAndLineReachOnlyTheirShape(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            String id = "fx_ab_shapes";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker",
                    move("fan", "cone", WEIGHT0 + ", \"range\": 5, \"angle\": 90, \"effects\": [{\"type\": \"damage\", \"amount\": 3}]")
                            + ", " + move("lance", "line", WEIGHT0 + ", \"range\": 10, \"width\": 1,"
                            + " \"effects\": [{\"type\": \"damage\", \"amount\": 3}]"), ""), 6, 12);
            hero.setNoAi(true);
            Husk front = husk(t, 9, 12, 100);
            Husk behind = husk(t, 3, 12, 100);
            Husk walled = husk(t, 14, 12, 100);
            for (int y = 2; y <= 4; y++) {
                for (int z = 10; z <= 14; z++) {
                    h.setBlock(new BlockPos(11, y, z), Blocks.STONE);
                }
            }
            t.onCleanup(() -> {
                for (int y = 2; y <= 4; y++) {
                    for (int z = 10; z <= 14; z++) {
                        h.setBlock(new BlockPos(11, y, z), Blocks.AIR);
                    }
                }
            });
            force(t, hero, front, "fan");
            h.runAtTickTime(40, t.guard(() -> {
                t.assertTrue(front.getHealth() < 100, "the cone missed what is in front");
                near(t, 100, behind.getHealth(), "the cone reached behind the hero");
                force(t, hero, walled, "lance");
            }));
            float[] afterCone = new float[1];
            h.runAtTickTime(41, t.guard(() -> afterCone[0] = front.getHealth()));
            h.runAtTickTime(100, t.guard(() -> {
                t.assertTrue(front.getHealth() < afterCone[0], "the line missed what is before the wall");
                near(t, 100, walled.getHealth(), "the line went through a stone wall");
                near(t, 100, behind.getHealth(), "the line went backwards");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroZonePulses}: a zone pulses 1 + duration / interval times on who is inside, its first pulse when the
     * move's wind-up ends (format 2 folds the delay into the wind-up), and is gone after its last.
     */
    @GameTest(template = "platform", timeoutTicks = 160, batch = "hero_ab_zone")
    public static void heroZonePulses(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            String id = "fx_ab_zone";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker",
                    move("pool", "zone", WEIGHT0 + ", \"range\": 12, \"radius\": 3, \"zone_at\": \"target\", \"delay\": 1,"
                            + " \"duration\": 2, \"interval\": 1, \"effects\": [{\"type\": \"damage\", \"amount\": 2}]"), ""), 4, 8);
            hero.setNoAi(true);
            Husk inside = husk(t, 10, 8, 100);
            FactionNpc outside = foe(t, 10, 13, 100);
            List<Long> pulses = new ArrayList<>();
            float[] last = {100};
            force(t, hero, inside, "pool");
            long start = h.getLevel().getGameTime();
            t.onEachTick(() -> {
                if (inside.getHealth() < last[0] - 1e-4) {
                    pulses.add(h.getLevel().getGameTime() - start);
                    last[0] = inside.getHealth();
                }
            });
            h.runAtTickTime(140, t.guard(() -> {
                t.assertTrue(pulses.size() == 3, "pulses at " + pulses + " (1 + 2 s / 1 s = 3)");
                t.assertTrue(pulses.get(1) - pulses.get(0) == 20 && pulses.get(2) - pulses.get(1) == 20, "1 s apart: " + pulses);
                // format 2: the wind-up covers the zone's delay (the reader raised it), so it pulses as the wind-up ends
                t.assertTrue(pulses.get(0) >= 20, "the first pulse came before the wind-up: " + pulses);
                near(t, 100, outside.getHealth(), "an enemy outside the zone was hurt");
                t.assertTrue(h.getLevel().getEntitiesOfClass(HeroZone.class, new AABB(t.base.center).inflate(30)).isEmpty(),
                        "the zone outlived its pulses");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroProjectileLeavesNoFire}: a shot that looks like a fire charge, fired into a wooden wall, sets nothing
     * alight, breaks nothing, drops no item and is gone.
     */
    @GameTest(template = "platform", timeoutTicks = 80, batch = "hero_ab_nofire")
    public static void heroProjectileLeavesNoFire(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 80);
        try {
            String id = "fx_ab_nofire";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker",
                    move("ember", "shot", WEIGHT0 + ", \"range\": 16, \"projectile\": \"fire_charge\","
                            + " \"effects\": [{\"type\": \"damage\", \"amount\": 2, \"element\": \"fire\"}]"), ""), 4, 12);
            hero.setNoAi(true);
            Husk husk = husk(t, 16, 12, 100);
            for (int y = 2; y <= 5; y++) {
                for (int z = 9; z <= 15; z++) {
                    h.setBlock(new BlockPos(10, y, z), Blocks.OAK_PLANKS);
                }
            }
            t.onCleanup(() -> {
                for (int x = 8; x <= 12; x++) {
                    for (int y = 2; y <= 6; y++) {
                        for (int z = 8; z <= 16; z++) {
                            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                        }
                    }
                }
            });
            force(t, hero, husk, "ember");
            boolean[] flew = new boolean[1];
            t.onEachTick(() -> flew[0] |= !h.getLevel().getEntitiesOfClass(HeroProjectile.class,
                    new AABB(t.base.center).inflate(30)).isEmpty());
            h.runAtTickTime(60, t.guard(() -> {
                t.assertTrue(flew[0], "no projectile flew");
                for (int x = 8; x <= 12; x++) {
                    for (int y = 2; y <= 6; y++) {
                        for (int z = 8; z <= 16; z++) {
                            BlockPos p = new BlockPos(x, y, z);
                            t.assertTrue(!h.getBlockState(p).is(Blocks.FIRE), "fire at " + p);
                            if (x == 10 && y <= 5 && z >= 9 && z <= 15) {
                                t.assertTrue(h.getBlockState(p).is(Blocks.OAK_PLANKS), "the wall lost a plank at " + p);
                            }
                        }
                    }
                }
                AABB area = new AABB(t.base.center).inflate(30);
                t.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, area).isEmpty(), "an item dropped");
                t.assertTrue(h.getLevel().getEntitiesOfClass(HeroProjectile.class, area).isEmpty(), "the projectile lingers");
                near(t, 100, husk.getHealth(), "the shot went through the wall");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroLeapMovesPhysically}: a leap is a physical arc (no tick moves the hero more than 1.5 blocks, it leaves
     * the ground), lands beside its target, hurts it on landing and costs the hero no fall damage.
     */
    @GameTest(template = "platform", timeoutTicks = 120, batch = "hero_ab_leap")
    public static void heroLeapMovesPhysically(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            String id = "fx_ab_leap";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker",
                    move("pounce", "leap", WEIGHT0 + ", \"range\": 8, \"radius\": 2,"
                            + " \"effects\": [{\"type\": \"damage\", \"amount\": 4}]"), ""), 4, 6);
            Husk husk = husk(t, 10, 6, 100);
            float hp = hero.getHealth();
            Vec3[] prev = {hero.position()};
            double[] maxStep = {0};
            boolean[] air = {false};
            force(t, hero, husk, "pounce");
            t.onEachTick(() -> {
                Vec3 p = hero.position();
                maxStep[0] = Math.max(maxStep[0], p.distanceTo(prev[0]));
                prev[0] = p;
                air[0] |= !hero.onGround();
                if (husk.getHealth() < 100 && heroHit(husk, hero) && hero.onGround()) {
                    t.assertTrue(air[0], "the hero never left the ground");
                    t.assertTrue(maxStep[0] <= 1.5, "a tick moved the hero " + maxStep[0] + " blocks");
                    t.assertTrue(hero.distanceTo(husk) <= 3.5, "it landed " + hero.distanceTo(husk) + " from its target");
                    near(t, hp, hero.getHealth(), "fall damage from its own leap");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** A dash pushes the hero along the ground toward its target and hits enemies within 1.5 blocks of its path. */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "hero_ab_dash")
    public static void heroDashMovesAndHitsAlongItsPath(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 100);
        try {
            String id = "fx_ab_dash";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker",
                    move("lunge", "dash", WEIGHT0 + ", \"range\": 8, \"effects\": [{\"type\": \"damage\", \"amount\": 3}]"), ""), 4, 6);
            Husk husk = husk(t, 9, 6, 100);
            double x0 = hero.getX();
            Vec3[] prev = {hero.position()};
            double[] maxStep = {0};
            force(t, hero, husk, "lunge");
            t.onEachTick(() -> {
                Vec3 p = hero.position();
                maxStep[0] = Math.max(maxStep[0], p.distanceTo(prev[0]));
                prev[0] = p;
                if (husk.getHealth() < 100 && heroHit(husk, hero)) {
                    t.assertTrue(hero.getX() - x0 >= 2.5, "the dash did not carry the hero: " + (hero.getX() - x0));
                    t.assertTrue(maxStep[0] <= 1.0, "a tick moved the hero " + maxStep[0] + " blocks");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ effects (v1 13)

    /** {@code heroHealsHurtAlly}: the {@code ally} form heals the most hurt ally in range, and nobody else. */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "hero_ab_heal")
    public static void heroHealsHurtAlly(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 40);
        try {
            String id = "fx_ab_heal";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "support",
                    move("mend", "ally", WEIGHT0 + ", \"range\": 10, \"effects\": [{\"type\": \"heal\", \"amount\": 6}]"), ""), 8, 8);
            hero.setNoAi(true);
            FactionNpc low = noRegen(t.npc(10, 8, NpcRole.SWORDSMAN));
            FactionNpc high = noRegen(t.npc(6, 8, NpcRole.SWORDSMAN));
            low.setNoAi(true);
            high.setNoAi(true);
            low.setHealth(10);
            high.setHealth(16);
            int e0 = hero.energy();
            t.assertTrue(hero.combat().forceMove("mend"), "mend did not start");
            h.runAtTickTime(25, t.guard(() -> {
                near(t, 16, low.getHealth(), "the most hurt ally healed by 6");
                near(t, 16, high.getHealth(), "the less hurt ally was healed too");
                t.assertTrue(hero.energy() == e0 + 10 + 3, "energy " + e0 + " -> " + hero.energy() + " (10 for the use, 3 for 6 healed)");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroShieldExpires}, for an NPC and a player alike: an {@code allies} shield gives the hero, an allied
     * soldier and an allied player the same absorption, and it is gone from all three when its seconds run out.
     */
    @GameTest(template = "platform", timeoutTicks = 120, batch = "hero_ab_shield")
    public static void heroShieldExpiresOnNpcAndPlayerAlike(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            String id = "fx_ab_shield";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "support",
                    move("ward", "allies", WEIGHT0 + ", \"radius\": 8,"
                            + " \"effects\": [{\"type\": \"shield\", \"amount\": 6, \"seconds\": 2}]"), ""), 8, 8);
            hero.setNoAi(true);
            FactionNpc soldier = t.npc(10, 8, NpcRole.SWORDSMAN);
            soldier.setNoAi(true);
            ServerPlayer player = player(t, 6, 8, SIDE, 20);
            t.assertTrue(hero.combat().forceMove("ward"), "ward did not start");
            h.runAtTickTime(15, t.guard(() -> {
                for (LivingEntity e : List.<LivingEntity>of(hero, soldier, player)) {
                    near(t, 6, e.getAbsorptionAmount(), e.getName().getString() + "'s shield");
                }
            }));
            h.runAtTickTime(70, t.guard(() -> {
                for (LivingEntity e : List.<LivingEntity>of(hero, soldier, player)) {
                    near(t, 0, e.getAbsorptionAmount(), e.getName().getString() + "'s shield after 2 s");
                    t.assertTrue(e.getAttribute(Attributes.MAX_ABSORPTION).getModifiers().isEmpty(),
                            e.getName().getString() + " kept the shield's room");
                }
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroDotTicksThroughIframes}: a DoT ticks once a second for its seconds, and a soldier's sword blow right
     * after a tick still lands in full (the hero hit restored the target's hurt cooldown).
     */
    @GameTest(template = "platform", timeoutTicks = 140, batch = "hero_ab_dot")
    public static void heroDotTicksThroughIframes(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 140);
        try {
            String id = "fx_ab_dot";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker",
                    move("hex", "bolt", WEIGHT0 + ", \"range\": 10, \"effects\": [{\"type\": \"dot\", \"amount\": 2,"
                            + " \"seconds\": 4}]"), ""), 6, 6);
            hero.setNoAi(true);
            FactionNpc target = foe(t, 9, 6, 100);
            FactionNpc swordsman = t.npc(9, 8, NpcRole.SWORDSMAN);
            swordsman.setNoAi(true);
            force(t, hero, target, "hex");
            List<Float> drops = new ArrayList<>();
            float[] last = {100};
            boolean[] struck = new boolean[1];
            t.onEachTick(() -> {
                float hp = target.getHealth();
                if (hp < last[0] - 1e-4) {
                    drops.add(last[0] - hp);
                    last[0] = hp;
                    if (!struck[0]) {
                        struck[0] = true;
                        float before = target.getHealth();
                        target.hurt(h.getLevel().damageSources().mobAttack(swordsman), 5F);
                        near(t, 5, before - target.getHealth(), "the sword blow right after a DoT tick");
                        last[0] = target.getHealth();
                    }
                }
            });
            h.runAtTickTime(120, t.guard(() -> {
                t.assertTrue(drops.size() == 4, "DoT ticks: " + drops);
                for (float d : drops) {
                    near(t, 2, d, "a DoT tick");
                }
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroElementImmunity}: venom does nothing to a zombie (undead), frost nothing to a stray (freeze-immune), and
     * radiant hurts a husk 1.3 times as much as the same physical hit (v1 6.1).
     */
    @GameTest(template = "platform", timeoutTicks = 160, batch = "hero_ab_elements")
    public static void heroElementImmunity(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            String id = "fx_ab_elem";
            String bolts = String.join(", ", List.of("venom", "frost", "radiant", "physical").stream()
                    .map(e -> move(e + "_bolt", "bolt", WEIGHT0 + ", \"range\": 10, \"effects\": [{\"type\": \"damage\","
                            + " \"amount\": 5, \"element\": \"" + e + "\"}]")).toList());
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker", bolts, ""), 12, 12);
            hero.setNoAi(true);
            Zombie zombie = t.track(h.spawn(EntityType.ZOMBIE, new BlockPos(15, 2, 12)));
            Stray stray = t.track(h.spawn(EntityType.STRAY, new BlockPos(9, 2, 12)));
            Husk radiant = husk(t, 12, 15, 100);
            Husk plain = husk(t, 12, 9, 100);
            for (Mob m : List.<Mob>of(zombie, stray)) {
                m.setNoAi(true);
                m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in the sun
            }
            float zh = zombie.getHealth(), sh = stray.getHealth();
            h.runAtTickTime(2, t.guard(() -> force(t, hero, zombie, "venom_bolt")));
            h.runAtTickTime(32, t.guard(() -> force(t, hero, stray, "frost_bolt")));
            h.runAtTickTime(62, t.guard(() -> force(t, hero, radiant, "radiant_bolt")));
            h.runAtTickTime(92, t.guard(() -> force(t, hero, plain, "physical_bolt")));
            h.runAtTickTime(130, t.guard(() -> {
                near(t, zh, zombie.getHealth(), "venom hurt a zombie");
                near(t, sh, stray.getHealth(), "frost hurt a stray");
                float r = 100 - radiant.getHealth(), p = 100 - plain.getHealth();
                t.assertTrue(p > 0, "the physical bolt missed");
                t.assertTrue(Math.abs(r / p - 1.3) < 0.01, "radiant against the undead: " + r + " vs " + p);
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Marks stack and are spent (v1 13.5, 19): two brands put 4 stacks of {@code toll} on a soldier; the finisher deals
     * 1.5 a stack and clears them.
     */
    @GameTest(template = "platform", timeoutTicks = 120, batch = "hero_ab_marks")
    public static void heroMarksStackAndSpend(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            String id = "fx_ab_marks";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker",
                    move("brand", "bolt", WEIGHT0 + ", \"range\": 10, \"effects\": [{\"type\": \"mark\", \"mark\": \"toll\","
                            + " \"stacks\": 2, \"max_stacks\": 5, \"seconds\": 10}]") + ", "
                            + move("knell", "bolt", WEIGHT0 + ", \"range\": 10, \"effects\": [{\"type\": \"damage\", \"amount\":"
                            + " {\"mark\": \"toll\", \"per_mark_stack\": 1.5}}, {\"type\": \"clear_marks\", \"mark\": \"toll\"}]"),
                    ""), 6, 6);
            hero.setNoAi(true);
            FactionNpc target = foe(t, 9, 6, 100);
            h.runAtTickTime(2, t.guard(() -> force(t, hero, target, "brand")));
            h.runAtTickTime(30, t.guard(() -> force(t, hero, target, "brand")));
            h.runAtTickTime(58, t.guard(() -> {
                long now = h.getLevel().getGameTime();
                t.assertTrue(HeroStatus.peek(target).book().marks("toll", now) == 4, "two brands, 4 stacks");
                near(t, 100, target.getHealth(), "a brand hurt");
                force(t, hero, target, "knell");
            }));
            h.runAtTickTime(90, t.guard(() -> {
                near(t, 94, target.getHealth(), "1.5 a stack x 4 stacks");
                t.assertTrue(HeroStatus.peek(target).book().marks("toll", h.getLevel().getGameTime()) == 0, "the stacks were not spent");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code cleanse} (v1 13.2): an allied soldier loses the hero DoT, the slowness, the fire, the freezing and the mark
     * an enemy put on it; its speed effect and its own side's mark stay.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "hero_ab_cleanse")
    public static void cleanseRemovesTheHarmfulPart(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 60);
        try {
            String id = "fx_ab_cleanse";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "support",
                    move("purify", "allies", WEIGHT0 + ", \"radius\": 8, \"effects\": [{\"type\": \"cleanse\"}]"), ""), 8, 8);
            hero.setNoAi(true);
            FactionNpc ally = noRegen(t.npc(10, 8, NpcRole.SWORDSMAN));
            ally.setNoAi(true);
            ally.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            ally.setHealth(100);
            long now = h.getLevel().getGameTime();
            HeroStatus st = HeroStatus.of(ally);
            st.book().dot(UUID.randomUUID().toString(), "x", HeroDef.Element.FIRE, 1, 10, 1, now);
            st.book().mark("hex", 2, 5, 10, "an-enemy", false, true, now);
            st.book().mark("ward", 1, 5, 10, "a-friend", false, false, now);
            ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 1));
            ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 0));
            ally.igniteForSeconds(10F);
            ally.setTicksFrozen(100);
            t.assertTrue(hero.combat().forceMove("purify"), "purify did not start");
            h.runAtTickTime(15, t.guard(() -> {
                long n = h.getLevel().getGameTime();
                t.assertTrue(!st.book().hasDot(n), "the DoT stayed");
                t.assertTrue(st.book().marks("hex", n) == 0, "the enemy's mark stayed");
                t.assertTrue(st.book().marks("ward", n) == 1, "its own side's mark went");
                t.assertTrue(!ally.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the slowness stayed");
                t.assertTrue(ally.hasEffect(MobEffects.MOVEMENT_SPEED), "the speed went");
                t.assertTrue(!ally.isOnFire(), "still burning");
                t.assertTrue(ally.getTicksFrozen() == 0, "still frozen: " + ally.getTicksFrozen());
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ equal treatment (HEROES_V2 6.15)

    /**
     * Natsuho's {@code claw_string} (SLICE_PLAN 3.1: 45%, 45% and 70% of her attack power, the last with a bleed) hits an
     * enemy NPC soldier and an enemy player for the same damage, strike by strike, and bleeds both the same.
     */
    @GameTest(template = "platform", timeoutTicks = 260, batch = "hero_ab_natsuho")
    public static void natsuhoComboHitsSoldierAndPlayerEqually(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 260);
        try {
            HeroEntity hero = hero(t, "natsuho_komezaki", "fists", slice("natsuho_komezaki"), 8, 8);
            hero.setNoAi(true);
            FactionNpc soldier = foe(t, 10, 8, 100);
            ServerPlayer player = player(t, 6, 8, FOE, 100);
            List<Float> bySoldier = new ArrayList<>(), byPlayer = new ArrayList<>();
            float[] last = {100, 100};
            t.onEachTick(() -> {
                if (soldier.getHealth() < last[0] - 1e-4) {
                    bySoldier.add(last[0] - soldier.getHealth());
                    last[0] = soldier.getHealth();
                }
                if (player.getHealth() < last[1] - 1e-4) {
                    byPlayer.add(last[1] - player.getHealth());
                    last[1] = player.getHealth();
                }
            });
            h.runAtTickTime(2, t.guard(() -> force(t, hero, soldier, "claw_string")));
            h.runAtTickTime(120, t.guard(() -> force(t, hero, player, "claw_string")));
            h.runAtTickTime(240, t.guard(() -> {
                t.assertTrue(bySoldier.size() >= 4, "three strikes and a bleed on the soldier: " + bySoldier);
                t.assertTrue(bySoldier.equals(byPlayer), "the soldier took " + bySoldier + ", the player " + byPlayer);
                double attack = hero.getAttributeValue(Attributes.ATTACK_DAMAGE);
                near(t, attack * 0.45, bySoldier.get(0), "the first strike");
                near(t, attack * 0.70, bySoldier.get(2), "the third strike");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * A knock-back with a lift and a pull move an enemy soldier and an enemy player exactly the same. The move pushes
     * both away from the hero; then the same knock-back and pull, given to both in one tick, set the same velocity (a
     * mirror image: the soldier stands east of the hero, the player west). The server only sets a player's velocity
     * (its client moves it), so the velocity it is given is what is compared.
     */
    @GameTest(template = "platform", timeoutTicks = 80, batch = "hero_ab_push")
    public static void knockbackAndPullTheSameForNpcAndPlayer(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 80);
        try {
            String id = "fx_ab_push";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "controller",
                    move("shove", "bolt", WEIGHT0 + ", \"range\": 10, \"effects\": [{\"type\": \"knockback\", \"strength\": 2,"
                            + " \"up\": 0.5}]"), ""), 12, 12);
            hero.setNoAi(true);
            FactionNpc soldier = foe(t, 16, 12, 100);
            ServerPlayer player = player(t, 8, 12, FOE, 100);
            h.runAtTickTime(2, t.guard(() -> force(t, hero, soldier, "shove")));
            h.runAtTickTime(20, t.guard(() -> {
                t.assertTrue(soldier.getDeltaMovement().x > 0.3 && soldier.getDeltaMovement().y > 0.1,
                        "the move did not knock the soldier back and up: " + soldier.getDeltaMovement());
                force(t, hero, player, "shove");
            }));
            h.runAtTickTime(40, t.guard(() -> {
                t.assertTrue(player.getDeltaMovement().x < -0.3 && player.getDeltaMovement().y > 0.1,
                        "the move did not knock the player back and up: " + player.getDeltaMovement());
                Vec3[] got = new Vec3[4];
                for (LivingEntity e : List.<LivingEntity>of(soldier, player)) {
                    e.setDeltaMovement(Vec3.ZERO);
                    e.setOnGround(true);
                }
                com.factionclash.hero.HeroEffects.knockback(hero, soldier, 2, 0.5);
                com.factionclash.hero.HeroEffects.knockback(hero, player, 2, 0.5);
                got[0] = soldier.getDeltaMovement();
                got[1] = player.getDeltaMovement();
                for (LivingEntity e : List.<LivingEntity>of(soldier, player)) {
                    e.setDeltaMovement(Vec3.ZERO);
                }
                com.factionclash.hero.HeroEffects.pull(hero, soldier, 1);
                com.factionclash.hero.HeroEffects.pull(hero, player, 1);
                got[2] = soldier.getDeltaMovement();
                got[3] = player.getDeltaMovement();
                t.assertTrue(got[0].x > 0.3 && got[0].y > 0.1, "the soldier was not knocked back and up: " + got[0]);
                near(t, got[0].x, -got[1].x, "knock-back x");
                near(t, got[0].y, got[1].y, "knock-back lift");
                near(t, got[0].z, got[1].z, "knock-back z");
                t.assertTrue(soldier.hurtMarked && player.hurtMarked, "a receiver's velocity is not sent");
                t.assertTrue(got[2].x < -0.1, "the soldier was not pulled: " + got[2]);
                near(t, got[2].x, -got[3].x, "pull x");
                near(t, got[2].y, got[3].y, "pull lift");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** A {@code stat} and a potion {@code effect} land the same on an enemy soldier and an enemy player and end alike. */
    @GameTest(template = "platform", timeoutTicks = 140, batch = "hero_ab_stat")
    public static void statAndEffectTheSameForNpcAndPlayer(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 140);
        try {
            String id = "fx_ab_stat";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "controller",
                    move("hobble", "bolt", WEIGHT0 + ", \"range\": 10, \"effects\": [{\"type\": \"stat\", \"stat\": \"speed\","
                            + " \"percent\": -20, \"seconds\": 2}, {\"type\": \"effect\", \"id\": \"slowness\", \"level\": 1,"
                            + " \"seconds\": 2}]"), ""), 12, 12);
            hero.setNoAi(true);
            FactionNpc soldier = foe(t, 16, 12, 100);
            ServerPlayer player = player(t, 8, 12, FOE, 100);
            h.runAtTickTime(2, t.guard(() -> force(t, hero, soldier, "hobble")));
            h.runAtTickTime(30, t.guard(() -> force(t, hero, player, "hobble")));
            h.runAtTickTime(48, t.guard(() -> {
                for (LivingEntity e : List.<LivingEntity>of(soldier, player)) {
                    AttributeModifier m = e.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(HeroStatus.statId(Stat.SPEED));
                    t.assertTrue(m != null && Math.abs(m.amount() + 0.2) < EPS
                            && m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, e.getName().getString() + ": " + m);
                    MobEffectInstance s = e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    t.assertTrue(s != null && s.getAmplifier() == 0, e.getName().getString() + " slowness " + s);
                }
            }));
            h.runAtTickTime(120, t.guard(() -> {
                for (LivingEntity e : List.<LivingEntity>of(soldier, player)) {
                    t.assertTrue(e.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(HeroStatus.statId(Stat.SPEED)) == null,
                            e.getName().getString() + " kept the stat");
                    t.assertTrue(!e.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), e.getName().getString() + " kept the slowness");
                }
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * The slot cap per use and receiver at runtime (v1 16.1, HEROES_V2 6.14 rule 3), the same for any target: a skill
     * whose two hits each read 40% of the hero's boss health lands at most the skill cap (16 x the 1.5 gear room) on an
     * enemy soldier and on an enemy player alike; the second hit finds the cap spent.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "hero_ab_cap")
    public static void slotCapHoldsForAnyTarget(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 100);
        try {
            String id = "fx_ab_cap";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker",
                    move("crush", "bolt", WEIGHT0 + ", \"range\": 10, \"hits\": 2, \"hit_interval\": 0.3,"
                            + " \"effects\": [{\"type\": \"damage\", \"amount\": {\"max_health_percent\": 40}}]"), ""), 12, 12);
            hero.setNoAi(true);
            FactionNpc soldier = foe(t, 16, 12, 100);
            ServerPlayer player = player(t, 8, 12, FOE, 100);
            h.runAtTickTime(2, t.guard(() -> force(t, hero, soldier, "crush")));
            h.runAtTickTime(40, t.guard(() -> force(t, hero, player, "crush")));
            h.runAtTickTime(80, t.guard(() -> {
                double cap = 16 * 1.5;
                near(t, 100 - cap, soldier.getHealth(), "the soldier's two hits");
                near(t, 100 - cap, player.getHealth(), "the player's two hits");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the thrown basic attack (v1 9)

    /**
     * A {@code thrown} hero throws its projectile look at its target from range (it never walks into melee), the throws
     * hurt with its element, and each landed throw is a basic hit: energy and its {@code on_hit} mark.
     */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "hero_ab_thrown")
    public static void thrownHeroThrowsAndGainsEnergy(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            String id = "fx_ab_thrown";
            String basic = "{\"style\": \"thrown\", \"projectile\": \"snowball\", \"amount\": 4, \"interval\": 1.5,"
                    + " \"element\": \"arcane\", \"on_hit\": [{\"type\": \"mark\", \"mark\": \"singed\", \"stacks\": 1,"
                    + " \"max_stacks\": 3, \"seconds\": 10}]}";
            HeroEntity hero = hero(t, id, "focus", def(id, basic, "marksman", "", ""), 4, 12);
            Husk husk = husk(t, 13, 12, 200);
            hero.setTarget(husk);
            double[] closest = {99};
            t.onEachTick(() -> {
                closest[0] = Math.min(closest[0], hero.distanceTo(husk));
                long now = h.getLevel().getGameTime();
                HeroStatus st = HeroStatus.peek(husk);
                if (heroHit(husk, hero) && st != null && st.book().marks("singed", now) > 0 && hero.energy() >= 5) {
                    t.assertTrue(husk.getLastDamageSource().typeHolder().unwrapKey().orElseThrow().location().getPath()
                            .equals("hero_arcane"), "the throw's element: " + husk.getLastDamageSource());
                    t.assertTrue(closest[0] > 3, "the thrower walked into melee: " + closest[0]);
                    t.assertTrue(!hero.fightsInMelee(), "a thrown hero fights in melee");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * A bow hero's own arrows are its basic hits too (v1 9 and 14; A1b counted only melee blows): it shoots real
     * arrows as an archer does, and an arrow that lands gives it 5 energy and puts its {@code on_hit} mark on the target.
     */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "hero_ab_bow")
    public static void bowHeroArrowsAreBasicHits(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            String id = "fx_ab_bow";
            String basic = "{\"style\": \"bow\", \"on_hit\": [{\"type\": \"mark\", \"mark\": \"pinned\", \"stacks\": 1,"
                    + " \"max_stacks\": 3, \"seconds\": 10}]}";
            HeroEntity hero = hero(t, id, "katana", def(id, basic, "marksman", "", ""), 4, 12);
            Husk husk = husk(t, 13, 12, 200);
            hero.setTarget(husk);
            t.onEachTick(() -> {
                long now = h.getLevel().getGameTime();
                HeroStatus st = HeroStatus.peek(husk);
                DamageSource last = husk.getLastDamageSource();
                if (last != null && last.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow
                        && last.getEntity() == hero && st != null && st.book().marks("pinned", now) > 0) {
                    t.assertTrue(hero.energy() >= 5, "a landed arrow gave no energy: " + hero.energy());
                    t.assertTrue(hero.getMainHandItem().is(Items.BOW), "the bow hero holds " + hero.getMainHandItem());
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
