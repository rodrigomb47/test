package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.HeroState;
import com.factionclash.combat.TerrainEdges;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEffects;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroManager;
import com.factionclash.logic.combat.EdgeSafety;
import com.factionclash.logic.hero.EdgePlay;
import com.factionclash.logic.hero.FormGeometry;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroIdentity;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import com.factionclash.work.Inv;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static com.factionclash.gametest.HeroAbilityGameTests.MELEE;
import static com.factionclash.gametest.HeroAbilityGameTests.move;
import static com.factionclash.gametest.HeroBossGameTests.IDLE_MOVE;

/**
 * Battle without civility (HEROES_V2 6.17 and 14.5 "Battle without civility", {@code docs/design/breaching.md} 8 and
 * 11.2; tasks T1 and T3). Heroes' pushes land at full strength wherever they send an enemy and are aimed at edges: a
 * knockback beside lava carries an NPC soldier and a mock player as far as on open ground and into it; a hero walks
 * round to its target's far side before it pushes it off a 20-block drop (not with no edge near, nor with
 * {@code heroes.edgePlay} off); a phase shockwave pushes only enemies; a charge sends its target over an edge, or into
 * a lava pool, and stops short of it; a raised shield stops a push's damage, not the push; a hop of the hero's own stops short of lava; a {@code where_it_fell} revival rises out of the lava it
 * died in. And the defence, for ordinary NPCs and heroes alike: a soldier approaches a pusher from the side that keeps
 * the lava off its back (a hero or a player with a Knockback sword, the same rule), climbs out of lava drinking its
 * Fire Resistance, and breaks a 12-block fall with a water bucket. The mock player here moves by its velocity as a
 * client would ({@link #movingPlayer}); every push is checked on it and on an NPC soldier with the same numbers. Each
 * test has a batch of its own; every block placed is put back by the cleanup.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroTerrainGameTests {
    private HeroTerrainGameTests() {
    }

    private static final FactionType SIDE = FactionType.EMBER_LEGION, FOE = FactionType.GROVE_COVENANT;
    private static final double EPS = 1e-6;
    /**
     * The raised floors' row: 20 blocks over the field's floor (relative y 1). A test's area is boxed in barriers just over
     * its template, so tests that need height run in the 24-high {@code mobility_field}, not the 8-high platform.
     */
    private static final int HIGH = 21;

    // ------------------------------------------------------------------ fixtures

    /**
     * A mock player that moves by its velocity as a client moves it (vanilla's travel with no input: gravity, friction,
     * lava and water), takes fall and lava damage and dies as a player does (the death event, then vanilla's message).
     */
    static ServerPlayer movingPlayer(GameTestBases.Handle t, double x, double y, double z, FactionType faction, double health) {
        return movingPlayer(t, x, y, z, faction, health, false);
    }

    /**
     * {@link #movingPlayer(GameTestBases.Handle, double, double, double, FactionType, double)}, and with {@code shield} a
     * raised shield blocks as a player's does (its own tick does not count the use up, so it blocks from the raise).
     */
    static ServerPlayer movingPlayer(GameTestBases.Handle t, double x, double y, double z, FactionType faction, double health,
                                     boolean shield) {
        ServerLevel level = t.helper.getLevel();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "terrain-test-player")) {
            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return false;
            }

            @Override
            public boolean isBlocking() {
                return shield && com.factionclash.hero.HeroStatus.shieldUp(this);
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
                if (invulnerableTime > 0) {
                    invulnerableTime--;
                }
                if (isAlive()) {
                    travel(Vec3.ZERO);
                }
            }

            @Override
            public void die(DamageSource source) {
                net.neoforged.neoforge.common.CommonHooks.onLivingDeath(this, source);
            }
        };
        Vec3 at = t.helper.absoluteVec(new Vec3(x, y, z));
        p.moveTo(at.x, at.y, at.z, 0F, 0F);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        p.setHealth((float) health);
        p.setOnGround(true);
        try {
            java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(p, 0);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("cannot clear the spawn invulnerability: " + e);
        }
        CampaignData data = CampaignData.get(level.getServer());
        data.players.put(p.getUUID(), faction);
        level.addFreshEntity(p);
        t.onCleanup(() -> {
            data.players.remove(p.getUUID());
            p.discard();
        });
        return p;
    }

    /**
     * An NPC of {@code faction} at the middle of relative cell (x, y, z) that moves by its velocity but walks nowhere of
     * its own (its AI on, its speed 0): a still target for a push.
     */
    static FactionNpc still(GameTestBases.Handle t, FactionType faction, int x, int y, int z, double health) {
        FactionNpc n = t.track(t.helper.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(x, 2, z)));
        n.faction(faction);
        n.role(NpcRole.SWORDSMAN);
        n.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        n.setHealth((float) health);
        n.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        n.hunger().set(17, 0F, 0F);
        Vec3 at = t.helper.absoluteVec(new Vec3(x + 0.5, y, z + 0.5));
        n.moveTo(at.x, at.y, at.z, 0F, 0F);
        return n;
    }

    /** Sets one block (relative) and puts back what was there at the cleanup. */
    static void block(GameTestBases.Handle t, int x, int y, int z, Block b) {
        HeroMoveGameTests.block(t, x, y, z, b.defaultBlockState());
    }

    /** A rectangle of {@code b} at row {@code y}, put back at the cleanup. */
    static void rect(GameTestBases.Handle t, int x0, int x1, int y, int z0, int z1, Block b) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                block(t, x, y, z, b);
            }
        }
    }

    /** A lava pool in the platform's floor (relative row 1), put back at the cleanup. */
    static void lava(GameTestBases.Handle t, int x0, int x1, int z0, int z1) {
        rect(t, x0, x1, 1, z0, z1, Blocks.LAVA);
    }

    /** A hero's definition: a common {@code role} with a melee basic attack, these moves and behaviour fields. */
    static String heroDef(String id, String role, String behaviour, String moves, String more) {
        return "{\"format\": 2, \"id\": \"" + id + "\", \"name\": \"" + HeroIdentity.nameFromId(id) + "\", \"gender\": \"female\","
                + " \"archetype\": \"samurai\", \"rarity\": \"common\", \"behaviour\": {\"combat_role\": \"" + role + "\"" + behaviour
                + "}, \"basic_attack\": " + MELEE + ", \"moves\": [" + moves + "], \"boss\": {\"weak_points\": [\"back\"],"
                + " \"phases\": [{\"at\": 100}]}, \"personality\": {\"temperament\": \"stoic\"}, \"leader\": {\"eligible\": false}"
                + more + "}";
    }

    /** A hero of {@link #SIDE} at the middle of relative cell (x, y, z). */
    static HeroEntity heroAt(GameTestBases.Handle t, String id, String def, int x, int y, int z) {
        HeroEntity hero = HeroAbilityGameTests.hero(t, id, "katana", def, x, z);
        Vec3 at = t.helper.absoluteVec(new Vec3(x + 0.5, y, z + 0.5));
        hero.moveTo(at.x, at.y, at.z, hero.getYRot(), 0F);
        hero.setDeltaMovement(Vec3.ZERO);
        return hero;
    }

    static double flat(Vec3 v) {
        return Math.hypot(v.x, v.z);
    }

    static long now(GameTestHelper h) {
        return h.getLevel().getGameTime();
    }

    /** A melee push of knockback {@code strength} (a light move with reach 3.5). */
    static String shove(int strength) {
        return move("shove", "melee", "\"class\": \"light\", \"range\": 3.5, \"cooldown\": 30, \"effects\": [{\"type\": \"damage\","
                + " \"amount\": 1}, {\"type\": \"knockback\", \"strength\": " + strength + "}]");
    }

    // ------------------------------------------------------------------ T1: full strength (6.17)

    /**
     * {@code knockbackFullStrengthIntoLava}: a strength-2 knockback beside a lava pool sends an NPC soldier and a mock
     * player into it, with the same push as on open ground (the old cut to a third is gone): 0.8 of vanilla's knockback,
     * about 4 blocks, the same numbers for the soldier and the player.
     */
    @GameTest(template = "platform", timeoutTicks = 160, batch = "terrain_kb_lava")
    public static void knockbackFullStrengthIntoLava(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            HeroEntity hero = HeroAbilityGameTests.hero(t, "fx_terrain_kb", "katana",
                    HeroAbilityGameTests.def("fx_terrain_kb", MELEE, "striker", IDLE_MOVE, ""), 20, 22);
            hero.setNoAi(true);
            int[] lane = {7, 11, 15, 19};
            FactionNpc open = still(t, FOE, 9, 2, lane[0], 100), inLava = still(t, FOE, 9, 2, lane[1], 100);
            ServerPlayer pOpen = movingPlayer(t, 9.5, 2, lane[2] + 0.5, FOE, 100);
            ServerPlayer pLava = movingPlayer(t, 9.5, 2, lane[3] + 0.5, FOE, 100);
            lava(t, 12, 15, lane[1] - 1, lane[1] + 1);
            lava(t, 12, 15, lane[3] - 1, lane[3] + 1);
            LivingEntity[] who = {open, inLava, pOpen, pLava};
            Vec3[] v = new Vec3[4], start = new Vec3[4];
            boolean[] burnt = new boolean[4];
            long[] pushedAt = {-1};
            h.runAtTickTime(4, t.guard(() -> {
                for (int i = 0; i < 4; i++) {
                    Vec3 at = h.absoluteVec(new Vec3(7.5, 2, lane[i] + 0.5));
                    hero.moveTo(at.x, at.y, at.z, 0F, 0F);
                    start[i] = who[i].position();
                    HeroEffects.knockback(hero, who[i], 2, 0);
                    v[i] = who[i].getDeltaMovement();
                }
                Vec3 away = h.absoluteVec(new Vec3(20.5, 2, 22.5));
                hero.moveTo(away.x, away.y, away.z, 0F, 0F);
                double full = FormGeometry.knockbackStrength(2);
                t.assertTrue(Math.abs(flat(v[0]) - full) < 0.05, "open ground: " + v[0] + ", expected " + full);
                HeroAbilityGameTests.near(t, flat(v[0]), flat(v[1]), "the push beside lava is the push on open ground (soldier)");
                HeroAbilityGameTests.near(t, flat(v[0]), flat(v[2]), "a mock player is pushed as a soldier is (open)");
                HeroAbilityGameTests.near(t, flat(v[1]), flat(v[3]), "a mock player is pushed as a soldier is (lava)");
                pushedAt[0] = now(h);
            }));
            t.onEachTick(() -> {
                for (int i = 0; i < 4; i++) {
                    burnt[i] |= who[i].isInLava();
                }
                if (pushedAt[0] > 0 && now(h) == pushedAt[0] + 40) {
                    t.assertTrue(burnt[1] && burnt[3], "into the lava: soldier " + burnt[1] + ", player " + burnt[3]);
                    t.assertTrue(!burnt[0] && !burnt[2], "lava on open ground?");
                    double dn = flat(open.position().subtract(start[0])), dp = flat(pOpen.position().subtract(start[2]));
                    double expected = FormGeometry.knockbackDistance(2, 0);
                    t.assertTrue(Math.abs(dn - expected) < 0.25, "the soldier went " + dn + " blocks, expected about " + expected);
                    t.assertTrue(Math.abs(dn - dp) < 0.3, "not as far: soldier " + dn + ", player " + dp);
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroCirclesToPushTowardAnEdge}: a hero with a knockback 3 move walks round an NPC soldier, and another round a
     * mock player, each standing 2 blocks from the edge of a floor 20 blocks over the platform, and pushes it off from the
     * far side: when the push starts, the edge lies along the hero's line to its target (the pick's factor x2), and the
     * target goes over. A third hero facing a soldier on the plain platform (no edge near) does not walk round and picks
     * the push as usual.
     */
    @GameTest(template = HeroMobilityGameTests.FIELD, timeoutTicks = 400, batch = "terrain_circle")
    public static void heroCirclesToPushTowardAnEdge(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 400);
        try {
            // the floors: x 1..10 and 13..22, z 0..12; the targets 2 blocks from their east edges
            rect(t, 1, 10, HIGH, 0, 12, Blocks.STONE);
            rect(t, 13, 22, HIGH, 0, 12, Blocks.STONE);
            String keep = ", \"keep_distance\": 3";
            HeroEntity hn = heroAt(t, "fx_terrain_circle_npc", heroDef("fx_terrain_circle_npc", "striker", keep, shove(3), ""), 8, HIGH + 1, 1);
            HeroEntity hp = heroAt(t, "fx_terrain_circle_pl", heroDef("fx_terrain_circle_pl", "striker", keep, shove(3), ""), 20, HIGH + 1, 1);
            HeroEntity hx = heroAt(t, "fx_terrain_circle_flat", heroDef("fx_terrain_circle_flat", "striker", keep, shove(3), ""), 12, 2, 17);
            FactionNpc sn = still(t, FOE, 8, HIGH + 1, 4, 20);
            ServerPlayer sp = movingPlayer(t, 20.5, HIGH + 1, 4.5, FOE, 20);
            FactionNpc sx = still(t, FOE, 12, 2, 20, 200);
            HeroEntity[] heroes = {hn, hp, hx};
            LivingEntity[] targets = {sn, sp, sx};
            boolean[] aimed = new boolean[3], over = new boolean[3];
            long[] startedAt = {-1, -1, -1};
            h.runAtTickTime(3, t.guard(() -> {
                for (int i = 0; i < 3; i++) {
                    heroes[i].setTarget(targets[i]);
                }
            }));
            t.onEachTick(() -> {
                for (int i = 0; i < 3; i++) {
                    HeroEntity x = heroes[i];
                    LivingEntity g = targets[i];
                    if (startedAt[i] < 0 && "shove".equals(x.combat().lastMove()) && x.combat().running()) {
                        startedAt[i] = now(h);
                        // the pick's factor from where it stands now: the edge along its line to the target
                        double dx = g.getX() - x.getX(), dz = g.getZ() - x.getZ(), l = Math.hypot(dx, dz);
                        EdgePlay.Push p = x.combat().selector().push(x.combat().runningMove());
                        double f = EdgePlay.pickFactor(p, x.combat().edgePlay().ring(), dx / l, dz / l, l, 0);
                        aimed[i] = f >= 2 - EPS;
                        if (i < 2) {
                            t.assertTrue(aimed[i], x.heroId() + " pushed without aiming (factor " + f + ") from " + x.position()
                                    + " at " + g.position());
                        }
                    }
                    if (i < 2 && startedAt[i] > 0 && g.getY() < h.absolutePos(new BlockPos(0, HIGH - 4, 0)).getY()) {
                        over[i] = true;
                    }
                }
                if (startedAt[0] > 0 && startedAt[1] > 0 && startedAt[2] > 0 && over[0] && over[1]) {
                    t.assertTrue(hn.combat().edgePlay().walks() >= 1 && hp.combat().edgePlay().walks() >= 1,
                            "no walk round: " + hn.combat().edgePlay().walks() + " / " + hp.combat().edgePlay().walks());
                    t.assertTrue(hx.combat().edgePlay().walks() == 0, "the hero with no edge near walked round");
                    t.assertTrue(hn.getY() > h.absolutePos(new BlockPos(0, HIGH, 0)).getY()
                            && hp.getY() > h.absolutePos(new BlockPos(0, HIGH, 0)).getY(), "a hero went over its own edge");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code edgePlayOffStopsTheAimNotThePush}: with {@code heroes.edgePlay} false the same hero beside the same edge does
     * not walk round, and its push still lands at full strength (knockback 3: 1.2 of vanilla's), on an NPC soldier and a
     * mock player alike.
     */
    @GameTest(template = HeroMobilityGameTests.FIELD, timeoutTicks = 300, batch = "terrain_circle_off")
    public static void edgePlayOffStopsTheAimNotThePush(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        boolean was = FCConfig.HERO_EDGE_PLAY.get();
        t.onCleanup(() -> FCConfig.HERO_EDGE_PLAY.set(was));
        try {
            FCConfig.HERO_EDGE_PLAY.set(false);
            rect(t, 1, 10, HIGH, 0, 12, Blocks.STONE);
            rect(t, 13, 22, HIGH, 0, 12, Blocks.STONE);
            String keep = ", \"keep_distance\": 3";
            // the targets join the level first, so they tick before the heroes: the test reads the push itself
            FactionNpc sn = still(t, FOE, 8, HIGH + 1, 4, 200);
            ServerPlayer sp = movingPlayer(t, 20.5, HIGH + 1, 4.5, FOE, 200);
            HeroEntity hn = heroAt(t, "fx_terrain_off_npc", heroDef("fx_terrain_off_npc", "striker", keep, shove(3), ""), 8, HIGH + 1, 1);
            HeroEntity hp = heroAt(t, "fx_terrain_off_pl", heroDef("fx_terrain_off_pl", "striker", keep, shove(3), ""), 20, HIGH + 1, 1);
            HeroEntity[] heroes = {hn, hp};
            LivingEntity[] targets = {sn, sp};
            double[] pushed = {-1, -1};
            float[] health = {200, 200};
            h.runAtTickTime(3, t.guard(() -> {
                hn.setTarget(sn);
                hp.setTarget(sp);
            }));
            t.onEachTick(() -> {
                for (int i = 0; i < 2; i++) {
                    LivingEntity g = targets[i];
                    if (pushed[i] < 0 && g.getHealth() < health[i] - 0.5F && "shove".equals(heroes[i].combat().lastMove())) {
                        pushed[i] = flat(g.getDeltaMovement());
                    }
                    health[i] = g.getHealth();
                }
                if (pushed[0] >= 0 && pushed[1] >= 0) {
                    t.assertTrue(hn.combat().edgePlay().walks() == 0 && hp.combat().edgePlay().walks() == 0, "walked round with edgePlay off");
                    double full = FormGeometry.knockbackStrength(3);
                    t.assertTrue(pushed[0] > full * 0.9, "the push was cut: " + pushed[0] + " of " + full);
                    t.assertTrue(Math.abs(pushed[0] - pushed[1]) < 0.05, "soldier " + pushed[0] + ", player " + pushed[1]);
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code shockwavePushesOnlyEnemies}: a phase transition beside lava pushes an enemy NPC soldier and a mock player
     * into it at full strength, the same for both, and leaves the hero's own soldier where it stood.
     */
    @GameTest(template = "platform", timeoutTicks = 160, batch = "terrain_shockwave")
    public static void shockwavePushesOnlyEnemies(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            String id = "fx_terrain_shock";
            String boss = ", \"phases\": [{\"at\": 100}, {\"at\": 60, \"transition\": {\"seconds\": 1.0}}]";
            FactionNpc enemy = still(t, FOE, 12, 2, 8, 100);
            ServerPlayer player = movingPlayer(t, 8.5, 2, 8.5, FOE, 100);
            FactionNpc own = still(t, SIDE, 10, 2, 10, 100);
            FactionNpc hitter = HeroAbilityGameTests.foe(t, 20, 20, 100);
            HeroEntity hero = HeroBossGameTests.bossHero(t, id, HeroBossGameTests.bossDef(id, "striker", IDLE_MOVE, boss, ""), 10, 8);
            hero.setNoAi(true);
            lava(t, 14, 16, 7, 9);
            lava(t, 4, 6, 7, 9);
            Vec3 ownAt = own.position();
            boolean[] burnt = new boolean[2];
            long[] began = {-1};
            Vec3[] v = new Vec3[2];
            h.runAtTickTime(3, t.guard(() -> {
                hero.setHealth(hero.getMaxHealth() * 0.61F);
                HeroBossGameTests.blow(hero, hitter, (float) (hero.getMaxHealth() * 0.05));
            }));
            t.onEachTick(() -> {
                burnt[0] |= enemy.isInLava();
                burnt[1] |= player.isInLava();
                if (began[0] < 0 && hero.combat().boss().transitioning()) {
                    began[0] = now(h);
                    v[0] = enemy.getDeltaMovement();
                    v[1] = player.getDeltaMovement();
                    t.assertTrue(v[0].x > 0.5 && v[1].x < -0.5, "not pushed hard away from the hero: " + v[0] + " / " + v[1]);
                    t.assertTrue(Math.abs(flat(v[0]) - flat(v[1])) < 0.02, "soldier " + v[0] + ", player " + v[1]);
                }
                if (began[0] > 0 && now(h) == began[0] + 40) {
                    t.assertTrue(burnt[0] && burnt[1], "into the lava: soldier " + burnt[0] + ", player " + burnt[1]);
                    t.assertTrue(own.position().distanceTo(ownAt) < 0.3, "its own soldier was moved: " + ownAt + " -> " + own.position());
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code chargeStopsShortOfTheEdgeItsTargetGoesOver}: a charge through an NPC soldier, and another through a mock
     * player, each standing at the east edge of a floor 20 blocks up, sends the target over and stops the hero on the
     * floor (MOBILITY_AND_WORK 5.1.13: its own body never past its safe fall).
     */
    @GameTest(template = HeroMobilityGameTests.FIELD, timeoutTicks = 160, batch = "terrain_charge_edge")
    public static void chargeStopsShortOfTheEdgeItsTargetGoesOver(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            rect(t, 1, 11, HIGH, 2, 8, Blocks.STONE);
            rect(t, 1, 11, HIGH, 14, 20, Blocks.STONE);
            String charge = move("ram", "charge", "\"class\": \"heavy\", \"range\": 12, \"speed\": 160, \"cooldown\": 9,"
                    + " \"effects\": [{\"type\": \"damage\", \"amount\": 2}, {\"type\": \"knockback\", \"strength\": 2}]");
            HeroEntity a = heroAt(t, "fx_terrain_ram", HeroAbilityGameTests.def("fx_terrain_ram", MELEE, "vanguard", charge, ""), 3, HIGH + 1, 5);
            HeroEntity b = heroAt(t, "fx_terrain_ram2", HeroAbilityGameTests.def("fx_terrain_ram2", MELEE, "vanguard", charge, ""), 3, HIGH + 1, 17);
            FactionNpc soldier = still(t, FOE, 10, HIGH + 1, 5, 100);
            ServerPlayer player = movingPlayer(t, 10.5, HIGH + 1, 17.5, FOE, 100);
            double top = h.absolutePos(new BlockPos(0, HIGH + 1, 0)).getY();
            boolean[] over = new boolean[2];
            h.runAtTickTime(3, t.guard(() -> {
                HeroAbilityGameTests.force(t, a, soldier, "ram");
                HeroAbilityGameTests.force(t, b, player, "ram");
            }));
            t.onEachTick(() -> {
                over[0] |= soldier.getY() < top - 4;
                over[1] |= player.getY() < top - 4;
                t.assertTrue(a.getY() > top - 1 && b.getY() > top - 1, "a hero charged over the edge: " + a.position() + " / " + b.position());
                if (a.combat().lastRelease() > 0 && !a.combat().running() && b.combat().lastRelease() > 0 && !b.combat().running()
                        && over[0] && over[1]) {
                    double edge = h.absolutePos(new BlockPos(12, 0, 0)).getX();
                    t.assertTrue(a.getX() < edge && b.getX() < edge, "a hero stands past the edge: " + a.getX() + " / " + b.getX());
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code chargeStopsShortOfLavaItsTargetGoesInto}: the same beside a lava pool on flat ground (no drop to stop the
     * charge, only the lava): a charge through an NPC soldier, and another through a mock player, each standing 1.5
     * blocks from the pool, knocks the target in and stops the hero short of it, never in it or burning (6.17: its own
     * body never into lava or fire, a fire-proof hero excepted).
     */
    @GameTest(template = "platform", timeoutTicks = 160, batch = "terrain_charge_lava")
    public static void chargeStopsShortOfLavaItsTargetGoesInto(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            String charge = move("ram", "charge", "\"class\": \"heavy\", \"range\": 12, \"speed\": 160, \"cooldown\": 9,"
                    + " \"effects\": [{\"type\": \"damage\", \"amount\": 2}, {\"type\": \"knockback\", \"strength\": 2}]");
            // the receivers join the level first, so they tick before the heroes
            FactionNpc soldier = still(t, FOE, 9, 2, 5, 100);
            ServerPlayer player = movingPlayer(t, 9.5, 2, 17.5, FOE, 100);
            HeroEntity a = heroAt(t, "fx_terrain_ramlava", HeroAbilityGameTests.def("fx_terrain_ramlava", MELEE, "vanguard", charge, ""), 3, 2, 5);
            HeroEntity b = heroAt(t, "fx_terrain_ramlava2", HeroAbilityGameTests.def("fx_terrain_ramlava2", MELEE, "vanguard", charge, ""), 3, 2, 17);
            // pools on a stone bed (the platform's floor has air under it): no drop, so only the lava can stop the charge
            rect(t, 11, 14, 0, 4, 6, Blocks.STONE);
            rect(t, 11, 14, 0, 16, 18, Blocks.STONE);
            lava(t, 11, 14, 4, 6);
            lava(t, 11, 14, 16, 18);
            double edge = h.absolutePos(new BlockPos(11, 0, 0)).getX();
            boolean[] burnt = new boolean[2];
            h.runAtTickTime(3, t.guard(() -> {
                HeroAbilityGameTests.force(t, a, soldier, "ram");
                HeroAbilityGameTests.force(t, b, player, "ram");
            }));
            t.onEachTick(() -> {
                burnt[0] |= soldier.isInLava();
                burnt[1] |= player.isInLava();
                for (HeroEntity x : new HeroEntity[]{a, b}) {
                    t.assertTrue(!x.isInLava() && !x.isOnFire() && x.getX() < edge, x.heroId() + " charged into the lava: " + x.position());
                }
                if (a.combat().lastRelease() > 0 && !a.combat().running() && b.combat().lastRelease() > 0 && !b.combat().running()
                        && burnt[0] && burnt[1]) {
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code shieldStopsDamageNotPush} (HEROES_V2 6.17 and 6.19, the review's test for T1): a hero's knockback move on an
     * NPC soldier and a mock player with raised shields, facing it, deals no damage and its push lands whole (a blocked
     * blow still knocks the blocker back, as in vanilla; only vanilla's own knock-back of the blow is smaller on a
     * blocker, 0.15 for 0.4); the same numbers for the soldier and the player, shielded or not.
     */
    @GameTest(template = "platform", timeoutTicks = 120, batch = "terrain_shield_push")
    public static void shieldStopsDamageNotPush(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            int[] lane = {4, 9, 14, 19};
            // the receivers join the level first, so they tick before the heroes: the test reads the push itself
            FactionNpc guarded = still(t, FOE, 6, 2, lane[0], 100), bare = still(t, FOE, 6, 2, lane[2], 100);
            ServerPlayer guardedPl = movingPlayer(t, 6.5, 2, lane[1] + 0.5, FOE, 100, true);
            ServerPlayer barePl = movingPlayer(t, 6.5, 2, lane[3] + 0.5, FOE, 100, false);
            LivingEntity[] who = {guarded, guardedPl, bare, barePl};
            HeroEntity[] heroes = new HeroEntity[4];
            for (int i = 0; i < 4; i++) {
                String id = "fx_terrain_shield" + i;
                heroes[i] = heroAt(t, id, HeroAbilityGameTests.def(id, MELEE, "striker", shove(2), ""), 4, 2, lane[i]);
                heroes[i].setNoAi(true);
            }
            // the shielded soldier holds its shield up, as soldiers do, against an archer beyond the hero, facing west
            // (without AI, so no target goal turns it onto the hero and lowers it; a push sets its velocity all the same,
            // which is what the test reads); the player faces the hero too
            guarded.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            guarded.setNoAi(true);
            guarded.setTarget(HeroMoveGameTests.archer(t, 0, lane[0]));
            guarded.setYRot(90F);
            guarded.setYBodyRot(90F);
            guarded.setYHeadRot(90F);
            guarded.startUsingItem(InteractionHand.OFF_HAND);
            guardedPl.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            guardedPl.setYRot(90F);
            guardedPl.setYHeadRot(90F);
            guardedPl.startUsingItem(InteractionHand.OFF_HAND);
            double[] pushed = {-1, -1, -1, -1};
            boolean[] blocking = new boolean[2];
            h.runAtTickTime(10, t.guard(() -> {
                for (int i = 0; i < 4; i++) {
                    HeroAbilityGameTests.force(t, heroes[i], who[i], "shove");
                }
            }));
            t.onEachTick(() -> {
                for (int i = 0; i < 4; i++) {
                    if (pushed[i] < 0 && flat(who[i].getDeltaMovement()) > 0.3) {
                        pushed[i] = flat(who[i].getDeltaMovement());
                        if (i < 2) {
                            blocking[i] = who[i].isBlocking();
                        }
                    }
                }
                if (pushed[0] > 0 && pushed[1] > 0 && pushed[2] > 0 && pushed[3] > 0) {
                    t.assertTrue(blocking[0] && blocking[1], "a shield was not up at the hit: soldier " + blocking[0] + ", player " + blocking[1]);
                    t.assertTrue(guarded.getHealth() == 100 && guardedPl.getHealth() == 100,
                            "a raised shield let damage through: " + guarded.getHealth() + " / " + guardedPl.getHealth());
                    t.assertTrue(bare.getHealth() < 100 && barePl.getHealth() < 100, "the blow did no damage with no shield");
                    // the move's own push lands whole on the blockers: vanilla's own knock-back of the blow is all a shield
                    // changes (a blocked blow knocks its blocker back 0.15 instead of 0.4), the same for anyone
                    double move = FormGeometry.knockbackStrength(2);
                    t.assertTrue(pushed[0] > move - EPS && pushed[1] > move - EPS, "a raised shield stopped the push: soldier "
                            + pushed[0] + ", player " + pushed[1] + " (the move's push " + move + ")");
                    t.assertTrue(pushed[2] > move && pushed[2] - pushed[0] < 0.2, "shielded " + pushed[0] + ", bare " + pushed[2]);
                    HeroAbilityGameTests.near(t, pushed[0], pushed[1], "the shielded soldier and player are pushed alike");
                    HeroAbilityGameTests.near(t, pushed[2], pushed[3], "the bare soldier and player are pushed alike");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code hopStopsShortOfAHazard}: a {@code move_self} hop back of 6 blocks with a lava pool 3 blocks behind stops short
     * of it (6.17: its own body never into a hazard, as its dashes stop at an edge); with open ground behind, the same hop
     * goes its full way.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "terrain_hop")
    public static void hopStopsShortOfAHazard(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 100);
        try {
            String d = HeroAbilityGameTests.def("fx_terrain_hop", MELEE, "skirmisher", IDLE_MOVE, "");
            String d2 = HeroAbilityGameTests.def("fx_terrain_hop2", MELEE, "skirmisher", IDLE_MOVE, "");
            HeroEntity near = heroAt(t, "fx_terrain_hop", d, 12, 2, 7);
            HeroEntity open = heroAt(t, "fx_terrain_hop2", d2, 12, 2, 17);
            FactionNpc f1 = HeroAbilityGameTests.foe(t, 16, 7, 100), f2 = HeroAbilityGameTests.foe(t, 16, 17, 100);
            for (HeroEntity x : new HeroEntity[]{near, open}) {
                x.goalSelector.removeAllGoals(g -> true);
                x.targetSelector.removeAllGoals(g -> true);
            }
            lava(t, 7, 9, 6, 8);
            double[] start = new double[2];
            boolean[] burnt = {false};
            h.runAtTickTime(3, t.guard(() -> {
                near.setTarget(f1);
                open.setTarget(f2);
                start[0] = near.getX();
                start[1] = open.getX();
                near.combat().hop(HeroDef.Direction.BACK, 6);
                open.combat().hop(HeroDef.Direction.BACK, 6);
            }));
            t.onEachTick(() -> {
                burnt[0] |= near.isInLava() || near.isOnFire();
                if (now(h) > 0 && h.getTick() == 40) {
                    double lavaEdge = h.absolutePos(new BlockPos(10, 0, 0)).getX();
                    t.assertTrue(!burnt[0], "the hop landed it in the lava");
                    t.assertTrue(near.getX() >= lavaEdge, "it stands over the lava: " + near.getX() + " (the pool ends at " + lavaEdge + ")");
                    t.assertTrue(start[1] - open.getX() > 4.5, "the open hop went " + (start[1] - open.getX()));
                    t.assertTrue(start[0] - near.getX() < start[1] - open.getX() - 1, "the hop by the lava was not shortened");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroRisesSafeAfterALavaDeath}: a fixture with {@code revival.where: where_it_fell} killed in lava rises on the
     * nearest safe cell (out of the pool, nothing burning beside it, within 16 blocks); killed in the void, at home.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "terrain_rise")
    public static void heroRisesSafeAfterALavaDeath(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 60);
        MinecraftServer server = h.getLevel().getServer();
        try {
            String id = "fx_terrain_phoenix";
            HeroEntity hero = HeroAbilityGameTests.hero(t, id, "katana", HeroAbilityGameTests.def(id, MELEE, "striker", IDLE_MOVE,
                    ", \"revival\": {\"seconds\": 120, \"where\": \"where_it_fell\"}"), 18, 18);
            hero.setNoAi(true);
            lava(t, 17, 19, 17, 19);
            Vec3 in = h.absoluteVec(new Vec3(18.5, 1.1, 18.5));
            hero.moveTo(in.x, in.y, in.z, 0F, 0F);
            hero.setHealth(1F);
            hero.hurt(h.getLevel().damageSources().lava(), 10F);
            HeroState s = t.data.heroes.get(id);
            t.assertTrue(!s.alive, "it did not die in the lava");
            BlockPos fell = s.fellAt;
            t.assertTrue(h.getLevel().getFluidState(fell).is(net.minecraft.tags.FluidTags.LAVA), "it fell at " + fell + ", not in the lava");
            s.reviveAt = h.getLevel().getGameTime();
            HeroManager.tick(server);
            HeroEntity back = HeroManager.entity(server, id);
            t.assertTrue(back != null, "not back");
            back.setNoAi(true);
            BlockPos at = back.blockPosition();
            BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
            int[] drop = new int[1];
            t.assertTrue(TerrainEdges.safeStand(h.getLevel(), at.getX(), at.getY(), at.getZ(), c, drop), "it rose on no safe cell: " + at);
            for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                t.assertTrue(!h.getLevel().getFluidState(at.offset(o[0], -1, o[1])).is(net.minecraft.tags.FluidTags.LAVA),
                        "it rose with a foot in the pool: " + at);
            }
            t.assertTrue(at.distSqr(fell) <= 16 * 16 + 4, "it rose " + Math.sqrt(at.distSqr(fell)) + " blocks from where it fell");
            t.assertTrue(at.distSqr(fell) <= 4 * 4, "not the nearest safe cell: " + at + " for " + fell);
            t.assertTrue(!at.equals(t.base.point(BaseState.SPAWN)), "it rose at home");
            // killed in the void: no safe cell there, so at home
            BlockPos voidAt = new BlockPos(fell.getX(), h.getLevel().getMinBuildHeight() - 10, fell.getZ());
            t.assertTrue(HeroManager.safeCellNear(h.getLevel(), voidAt, HeroManager.SAFE_RISE_RADIUS) == null, "a safe cell in the void");
            back.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            s.fellAt = voidAt;
            s.reviveAt = h.getLevel().getGameTime();
            HeroManager.tick(server);
            HeroEntity home = HeroManager.entity(server, id);
            t.assertTrue(home != null && home.blockPosition().equals(t.base.point(BaseState.SPAWN)),
                    "after the void it should rise at home: " + (home == null ? "not back" : home.blockPosition()));
            home.setNoAi(true);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ T3: how ordinary NPCs defend (breaching.md 8)

    /** The cell at relative (cx, 2, cz) has lava within {@code reach} blocks behind it from (px, pz), whoever stands there. */
    private static boolean lavaBehind(GameTestHelper h, double cx, double cz, double px, double pz, double reach) {
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        int[] drop = new int[1];
        int y = h.absolutePos(new BlockPos(0, 2, 0)).getY();
        Vec3 cell = h.absoluteVec(new Vec3(cx, 2, cz)), p = h.absoluteVec(new Vec3(px, 2, pz));
        return EdgeSafety.edgeBehind(cell.x, y, cell.z, p.x, p.z, reach,
                (x, yy, z) -> EdgeSafety.column(TerrainEdges.ground(h.getLevel(), x, yy, z, c, drop), drop[0], false));
    }

    /**
     * One lane of the edge sense: the target at relative (tx + 0.5, 2, line + 0.5), a lava pool from x {@code lavaX} (4
     * long, {@code 2 half + 1} wide) beyond the soldier's start at ({@code startX}, 2, line), the soldier set on the target.
     * The short way runs straight along the line and ends on the target's lava side. Returns the soldier.
     */
    private static FactionNpc edgeLane(GameTestBases.Handle t, LivingEntity target, int line, int lavaX, int half, int startX) {
        lava(t, lavaX, lavaX + 3, line - half, line + half);
        FactionNpc s = t.track(t.helper.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(startX, 2, line)));
        s.faction(FOE);
        s.role(NpcRole.SWORDSMAN);
        s.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        s.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        s.setHealth(200);
        s.hunger().set(20, 5F, 0F);
        s.setTarget(target);
        return s;
    }

    /**
     * Watches two lanes: the soldier of {@code guarded} must reach its target on a cell with no lava within
     * {@code reach} at its back, the one of {@code control} (no pusher near) the short way, with the lava at its back.
     */
    private static void edgeRace(GameTestBases.Handle t, FactionNpc guarded, LivingEntity gTarget, FactionNpc control,
                                 LivingEntity cTarget, double reach) {
        GameTestHelper h = t.helper;
        boolean[] done = {false, false};
        boolean[] lavaBack = {false, false};
        Vec3[] at = new Vec3[2];
        t.onEachTick(() -> {
            FactionNpc[] s = {guarded, control};
            LivingEntity[] g = {gTarget, cTarget};
            for (int i = 0; i < 2; i++) {
                if (!done[i] && s[i].onGround() && s[i].distanceTo(g[i]) < 2.0) {
                    done[i] = true;
                    Vec3 origin = h.absoluteVec(Vec3.ZERO);
                    Vec3 rel = s[i].position().subtract(origin), p = g[i].position().subtract(origin);
                    at[i] = rel;
                    lavaBack[i] = lavaBehind(h, rel.x, rel.z, p.x, p.z, reach);
                }
            }
            if (done[0] && done[1]) {
                t.assertTrue(!lavaBack[0], "the soldier fought its pusher with the lava at its back, from " + at[0]);
                t.assertTrue(lavaBack[1], "with no pusher near it should walk the short way, the lava at its back: " + at[1]);
                t.succeed();
            }
        });
    }

    /**
     * {@code npcKeepsTheLavaEdgeAway}: a soldier between a lava pool and the enemy hero it fights (2 blocks from the lava,
     * 4 from the hero) goes round to fight it from a cell with no lava within 6 blocks at its back; one fighting a plain
     * soldier beside the same pool walks the short way and stands with the lava behind it.
     */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "terrain_sense_hero")
    public static void npcKeepsTheLavaEdgeAway(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            HeroEntity hero = heroAt(t, "fx_terrain_sense", HeroAbilityGameTests.def("fx_terrain_sense", MELEE, "striker", IDLE_MOVE, ""), 5, 2, 7);
            hero.setNoAi(true);
            FactionNpc plain = still(t, SIDE, 5, 2, 19, 200);
            plain.setNoAi(true);
            FactionNpc guarded = edgeLane(t, hero, 7, 11, 2, 9), control = edgeLane(t, plain, 19, 11, 2, 9);
            edgeRace(t, guarded, hero, control, plain, EdgeSafety.HERO_REACH);
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code npcKeepsTheLavaEdgeAwayFromAKnockbackPlayer}: the same with a mock player holding a Knockback II sword as the
     * pusher (equal treatment: a player's Knockback sword is a pusher exactly as an NPC's is; its reach 4 blocks, the lava
     * within it); the control fights a mock player with a plain sword, who is no pusher.
     */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "terrain_sense_player")
    public static void npcKeepsTheLavaEdgeAwayFromAKnockbackPlayer(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            ServerPlayer kb = movingPlayer(t, 4.5, 2, 7.5, SIDE, 200);
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            Holder<Enchantment> knock = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.KNOCKBACK);
            sword.enchant(knock, 2);
            kb.setItemInHand(InteractionHand.MAIN_HAND, sword);
            ServerPlayer plain = movingPlayer(t, 4.5, 2, 19.5, SIDE, 200);
            plain.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            // a Knockback sword's reach is 4: the lava 4.5 blocks from the player, the soldier 2.5 from it
            FactionNpc guarded = edgeLane(t, kb, 7, 9, 1, 7), control = edgeLane(t, plain, 19, 9, 1, 7);
            t.assertTrue(com.factionclash.combat.EdgeSense.pusher(kb) && !com.factionclash.combat.EdgeSense.pusher(plain),
                    "a Knockback sword makes a pusher, a plain one does not");
            edgeRace(t, guarded, kb, control, plain, EdgeSafety.PUSH_REACH);
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** A potion of Fire Resistance. */
    private static ItemStack fireResistance() {
        return PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE);
    }

    /**
     * {@code npcClimbsOutOfLava}: a soldier set in a lava pool (a block deep) is out within 3 s and has drunk its Fire
     * Resistance on the way (the potion gone, a glass bottle in its pack, the effect on it); a hero set in the same kind
     * of pool climbs out too (one rule set for NPCs and heroes). Both swim at a player's pace in lava and are out in
     * about 2 s; bobbing at the surface does not hand them back to their fight before they stand on the bank.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "terrain_lava_escape")
    public static void npcClimbsOutOfLava(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        try {
            // pools one block deep on a stone bed (the platform's floor has air under it, where lava would run on)
            rect(t, 7, 9, 0, 7, 9, Blocks.STONE);
            rect(t, 15, 17, 0, 15, 17, Blocks.STONE);
            lava(t, 7, 9, 7, 9);
            lava(t, 15, 17, 15, 17);
            FactionNpc soldier = t.track(h.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(8, 2, 8)));
            soldier.faction(FOE);
            soldier.role(NpcRole.SWORDSMAN);
            soldier.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60);
            soldier.setHealth(60);
            soldier.inventory().addItem(fireResistance());
            HeroEntity hero = heroAt(t, "fx_terrain_lava", HeroAbilityGameTests.def("fx_terrain_lava", MELEE, "striker", IDLE_MOVE, ""), 16, 2, 16);
            Vec3 a = h.absoluteVec(new Vec3(8.5, 1.05, 8.5)), b = h.absoluteVec(new Vec3(16.5, 1.05, 16.5));
            soldier.moveTo(a.x, a.y, a.z, 0F, 0F);
            hero.moveTo(b.x, b.y, b.z, 0F, 0F);
            long[] outAt = {-1, -1};
            boolean[] wasIn = {false, false};
            t.onEachTick(() -> {
                LivingEntity[] who = {soldier, hero};
                for (int i = 0; i < 2; i++) {
                    wasIn[i] |= who[i].isInLava();
                    if (wasIn[i] && outAt[i] < 0 && !who[i].isInLava() && who[i].onGround()) {
                        outAt[i] = h.getTick();
                    }
                }
                if (h.getTick() == 70) {
                    t.assertTrue(wasIn[0] && wasIn[1], "not set in the lava");
                    t.assertTrue(outAt[0] > 0 && outAt[0] <= 62, "the soldier is still in the lava (out at " + outAt[0] + ")");
                    t.assertTrue(outAt[1] > 0 && outAt[1] <= 62, "the hero is still in the lava (out at " + outAt[1] + ")");
                    t.assertTrue(soldier.isAlive(), "the soldier burnt to death");
                    t.assertTrue(soldier.terrainSurvival().drinks() == 1 && soldier.hasEffect(MobEffects.FIRE_RESISTANCE),
                            "it did not drink its Fire Resistance");
                    t.assertTrue(Inv.count(soldier.inventory(), s -> s.is(Items.GLASS_BOTTLE)) == 1
                            && Inv.count(soldier.inventory(), s -> s.is(Items.POTION)) == 0, "the bottle is not back in its pack");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code waterLandingBreaksAFall}: a soldier with a water bucket dropped from 12 blocks lands unhurt and has its
     * bucket back with water (the water picked up again); one without a bucket from the same height is hurt by the fall.
     */
    @GameTest(template = HeroMobilityGameTests.FIELD, timeoutTicks = 120, batch = "terrain_water_landing")
    public static void waterLandingBreaksAFall(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            FactionNpc wet = still(t, FOE, 8, 14, 16, 20), dry = still(t, FOE, 16, 14, 16, 20);
            for (FactionNpc n : new FactionNpc[]{wet, dry}) {
                n.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.25);
            }
            wet.inventory().addItem(new ItemStack(Items.WATER_BUCKET));
            t.onCleanup(() -> {
                for (int x = 6; x <= 10; x++) {
                    for (int z = 14; z <= 18; z++) {
                        BlockPos p = h.absolutePos(new BlockPos(x, 2, z));
                        if (!h.getLevel().getFluidState(p).isEmpty()) {
                            h.getLevel().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            });
            t.onEachTick(() -> {
                if (h.getTick() == 80) {
                    t.assertTrue(wet.onGround() && dry.onGround(), "not down yet");
                    t.assertTrue(wet.terrainSurvival().landings() == 1, "no water landing");
                    t.assertTrue(wet.getHealth() >= 20 - 1e-3, "the water landing hurt: " + wet.getHealth());
                    t.assertTrue(dry.getHealth() < 15, "a 12-block fall without water should hurt: " + dry.getHealth());
                    t.assertTrue(Inv.count(wet.inventory(), s -> s.is(Items.WATER_BUCKET)) == 1
                            && Inv.count(wet.inventory(), s -> s.is(Items.BUCKET)) == 0, "the water is not back in its bucket");
                    BlockPos under = h.absolutePos(new BlockPos(8, 2, 16));
                    t.assertTrue(!h.getLevel().getFluidState(under).isSource(), "the water was left on the ground");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
