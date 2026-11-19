package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroBlasts;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroTelegraphs;
import com.factionclash.logic.hero.Blasts;
import com.factionclash.logic.hero.Decals;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.factionclash.gametest.HeroAbilityGameTests.MELEE;
import static com.factionclash.gametest.HeroAbilityGameTests.force;
import static com.factionclash.gametest.HeroAbilityGameTests.foe;
import static com.factionclash.gametest.HeroAbilityGameTests.heroHit;
import static com.factionclash.gametest.HeroAbilityGameTests.move;
import static com.factionclash.gametest.HeroAbilityGameTests.player;

/**
 * Explosive moves (HEROES_V2 6.18 and 14.5 "Battle without civility"; task T2), with the test-only fixture
 * {@code fixture_bomber}: a marksman whose explosive {@code shot} (power 2) and explosive {@code rain} (power 1, 8
 * impacts) are explosions by nature. A blast breaks blocks exactly as vanilla's TNT of the same power does (the same rays
 * with the same random draws break the same blocks; obsidian and bedrock stand, TNT in reach is lit) and hurts and
 * pushes nobody, NPC soldier, mock player or dropped item; the move's own effects do the hurting, the same numbers for
 * a soldier and a player; with {@code mobGriefing} false or {@code heroes.explosiveBlocks} false no block breaks and
 * the damage is the same; the wind-up draws a disc of the blast's reach; a use blasts at most 6 times and a pulsing zone
 * only on its first pulse; and the pick never blasts near the hero's own base, against a soldier and a player alike.
 * Each test has a batch of its own; every block placed is put back, and the items and lit TNT a blast leaves are removed.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroExplosiveGameTests {
    private HeroExplosiveGameTests() {
    }

    private static final FactionType SIDE = FactionType.EMBER_LEGION;
    private static final double EPS = 1e-4;

    // ------------------------------------------------------------------ the fixture

    /** The fixture's explosive shot: power 2, a splash of 3 blocks, 4 damage (its only damage), a fire charge's look. */
    static String blastShot(int weight) {
        return move("blast_shot", "shot", "\"range\": 16, \"radius\": 3, \"projectile\": \"fire_charge\", \"cooldown\": 20,"
                + " \"weight\": " + weight + ", \"effects\": [{\"type\": \"damage\", \"amount\": 4}], \"explosive\": {\"power\": 2}");
    }

    /** The fixture's explosive rain: 8 impacts of power 1 (6.18: only its first 6 blast). */
    static String bombRain(int weight) {
        return move("bomb_rain", "rain", "\"radius\": 3, \"count\": 8, \"delay\": 0.9, \"cooldown\": 40, \"weight\": " + weight
                + ", \"effects\": [{\"type\": \"damage\", \"amount\": 1}], \"explosive\": 1");
    }

    /** {@code fixture_bomber} (14.5): a common marksman with the explosive shot and rain, and these other moves. */
    static String bomber(String id, String moves) {
        return HeroTerrainGameTests.heroDef(id, "marksman", "", moves, "");
    }

    static HeroEntity bomberAt(GameTestBases.Handle t, String id, String moves, int x, int z) {
        return HeroAbilityGameTests.hero(t, id, "katana", bomber(id, moves), x, z);
    }

    // ------------------------------------------------------------------ helpers

    static void block(GameTestBases.Handle t, int x, int y, int z, Block b) {
        HeroMoveGameTests.block(t, x, y, z, b.defaultBlockState());
    }

    /** A box of {@code b} (relative, inclusive), put back at the cleanup. */
    static void box(GameTestBases.Handle t, int x0, int x1, int y0, int y1, int z0, int z1, Block b) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    block(t, x, y, z, b);
                }
            }
        }
    }

    /** The cells of a relative box that are not air now, as absolute positions. */
    static Set<BlockPos> solid(GameTestHelper h, int x0, int x1, int y0, int y1, int z0, int z1) {
        Set<BlockPos> out = new HashSet<>();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos p = h.absolutePos(new BlockPos(x, y, z));
                    if (!h.getLevel().getBlockState(p).isAir()) {
                        out.add(p);
                    }
                }
            }
        }
        return out;
    }

    /** The blast resistance of every cell of a relative box now, as vanilla's explosion reads it (-1: air, no fluid). */
    static Map<BlockPos, Float> resistances(GameTestHelper h, int x0, int x1, int y0, int y1, int z0, int z1) {
        Map<BlockPos, Float> out = new HashMap<>();
        ServerLevel level = h.getLevel();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos p = h.absolutePos(new BlockPos(x, y, z));
                    BlockState s = level.getBlockState(p);
                    var f = level.getFluidState(p);
                    out.put(p, s.isAir() && f.isEmpty() ? -1F
                            : Math.max(s.getExplosionResistance(level, p, null), f.getExplosionResistance(level, p, null)));
                }
            }
        }
        return out;
    }

    /** What vanilla's ray rule breaks from {@code at} with every ray at {@code factor}, over the cells read before. */
    static Set<BlockPos> rays(Map<BlockPos, Float> world, Vec3 at, float power, float factor) {
        Set<BlockPos> out = new HashSet<>();
        for (Blasts.Cell c : Blasts.broken(at.x, at.y, at.z, power, factor, (x, y, z) -> world.getOrDefault(new BlockPos(x, y, z), -1F))) {
            out.add(new BlockPos(c.x(), c.y(), c.z()));
        }
        return out;
    }

    /** Lit TNT within the test's area: removed at once (before its fuse runs out); true when there was any. */
    static boolean snuffTnt(GameTestHelper h) {
        AABB area = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(32);
        List<PrimedTnt> lit = h.getLevel().getEntitiesOfClass(PrimedTnt.class, area);
        lit.forEach(PrimedTnt::discard);
        return !lit.isEmpty();
    }

    /** Removes the drops and lit TNT a blast left in the test's area. */
    static void sweep(GameTestBases.Handle t) {
        GameTestHelper h = t.helper;
        t.onCleanup(() -> {
            AABB area = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(32);
            h.getLevel().getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
            h.getLevel().getEntitiesOfClass(PrimedTnt.class, area).forEach(PrimedTnt::discard);
        });
    }

    /** A dropped item that never despawns nor is picked up, at the middle of relative cell (x, y, z). */
    static ItemEntity dropped(GameTestBases.Handle t, double x, double y, double z) {
        Vec3 at = t.helper.absoluteVec(new Vec3(x, y, z));
        ItemEntity item = new ItemEntity(t.helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.DIAMOND));
        item.setDeltaMovement(Vec3.ZERO);
        item.setNeverPickUp();
        item.setUnlimitedLifetime();
        t.helper.getLevel().addFreshEntity(item);
        return t.track(item);
    }

    static long now(GameTestHelper h) {
        return h.getLevel().getGameTime();
    }

    /** The newest blast of {@code hero} at or after {@code since}, or null. */
    static HeroBlasts.Blast blastOf(HeroEntity hero, long since) {
        HeroBlasts.Blast out = null;
        for (HeroBlasts.Blast b : HeroBlasts.recent()) {
            if (b.hero() == hero.getId() && b.at() >= since) {
                out = b;
            }
        }
        return out;
    }

    static int blastsOf(HeroEntity hero, String move, long since) {
        int n = 0;
        for (HeroBlasts.Blast b : HeroBlasts.recent()) {
            if (b.hero() == hero.getId() && b.move().equals(move) && b.at() >= since) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ the blast is vanilla's, and terrain only

    /**
     * {@code explosiveBlastIsVanillasTntAndHurtsNobody}: a hero's blast of power 2 against a stone-brick wall with
     * obsidian, bedrock and TNT in it breaks exactly the blocks vanilla's TNT explosion of power 2 breaks in an identical
     * wall with the same random draws (obsidian and bedrock stand, the TNT is lit), within vanilla's ray rule
     * ({@link Blasts#broken}: everything its weakest rays break, nothing its strongest do not); and an NPC soldier, a
     * mock player and a dropped item beside it are not hurt, not moved and not destroyed.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "explosive_rays")
    public static void explosiveBlastIsVanillasTntAndHurtsNobody(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 100);
        sweep(t);
        try {
            HeroEntity hero = bomberAt(t, "fixture_bomber_rays", blastShot(0), 20, 21);
            hero.setNoAi(true);
            int[] lanes = {6, 16};
            for (int zc : lanes) {
                box(t, 8, 15, 0, 6, zc - 4, zc + 4, Blocks.AIR);
                box(t, 8, 15, 1, 1, zc - 4, zc + 4, Blocks.STONE_BRICKS);
                box(t, 12, 12, 2, 4, zc - 2, zc + 2, Blocks.STONE_BRICKS);
                block(t, 12, 3, zc, Blocks.OBSIDIAN);
                block(t, 12, 2, zc + 1, Blocks.BEDROCK);
                block(t, 12, 4, zc + 1, Blocks.TNT);
            }
            FactionNpc soldier = foe(t, 10, lanes[0] - 2, 40);
            ServerPlayer player = player(t, 10, lanes[0] + 2, FactionType.GROVE_COVENANT, 40);
            ItemEntity item = dropped(t, 11.5, 2, lanes[0] + 0.5);
            h.runAtTickTime(10, t.guard(() -> {
                ServerLevel level = h.getLevel();
                Vec3 a = h.absoluteVec(new Vec3(11.6, 3.5, lanes[0] + 0.5));
                Vec3 b = h.absoluteVec(new Vec3(11.6, 3.5, lanes[1] + 0.5));
                Map<BlockPos, Float> worldA = resistances(h, 8, 15, 0, 6, lanes[0] - 4, lanes[0] + 4);
                Set<BlockPos> beforeA = solid(h, 8, 15, 0, 6, lanes[0] - 4, lanes[0] + 4);
                Set<BlockPos> beforeB = solid(h, 8, 15, 0, 6, lanes[1] - 4, lanes[1] + 4);
                Set<BlockPos> weakest = rays(worldA, a, 2F, Blasts.WEAKEST), strongest = rays(worldA, a, 2F, Blasts.STRONGEST);
                Vec3 sv = soldier.getDeltaMovement(), pv = player.getDeltaMovement();
                float sh = soldier.getHealth(), ph = player.getHealth();
                level.random.setSeed(20261119L);
                HeroBlasts.blastAt(hero, "blast_shot", 2, false, a);
                boolean litA = snuffTnt(h);
                level.random.setSeed(20261119L);
                level.explode(null, b.x, b.y, b.z, 2F, Level.ExplosionInteraction.TNT);
                boolean litB = snuffTnt(h);
                Set<BlockPos> brokenA = new HashSet<>(beforeA), brokenB = new HashSet<>(beforeB);
                brokenA.removeAll(solid(h, 8, 15, 0, 6, lanes[0] - 4, lanes[0] + 4));
                brokenB.removeAll(solid(h, 8, 15, 0, 6, lanes[1] - 4, lanes[1] + 4));
                Set<BlockPos> shifted = new HashSet<>();
                int dz = lanes[1] - lanes[0];
                for (BlockPos p : brokenA) {
                    shifted.add(p.offset(0, 0, dz));
                }
                t.assertTrue(!brokenA.isEmpty(), "the hero's blast broke nothing");
                t.assertTrue(shifted.equals(brokenB), "not vanilla's TNT: the hero's blast broke " + brokenA.size()
                        + " blocks, the same TNT " + brokenB.size());
                t.assertTrue(brokenA.containsAll(weakest), "it spared blocks even its weakest rays break: " + weakest.size()
                        + " of them, " + brokenA.size() + " broken");
                t.assertTrue(strongest.containsAll(brokenA), "it broke blocks no ray of power 2 reaches");
                t.assertTrue(h.getBlockState(new BlockPos(12, 3, lanes[0])).is(Blocks.OBSIDIAN), "the obsidian broke");
                t.assertTrue(h.getBlockState(new BlockPos(12, 2, lanes[0] + 1)).is(Blocks.BEDROCK), "the bedrock broke");
                t.assertTrue(litA && litB && h.getBlockState(new BlockPos(12, 4, lanes[0] + 1)).isAir(), "the TNT was not lit");
                // terrain only: nobody hurt, nobody pushed, nothing destroyed
                t.assertTrue(soldier.getHealth() == sh && player.getHealth() == ph, "the blast hurt someone: soldier "
                        + soldier.getHealth() + " of " + sh + ", player " + player.getHealth() + " of " + ph);
                t.assertTrue(soldier.getDeltaMovement().distanceTo(sv) < EPS && player.getDeltaMovement().distanceTo(pv) < EPS,
                        "the blast pushed someone: soldier " + soldier.getDeltaMovement() + ", player " + player.getDeltaMovement());
                t.assertTrue(item.isAlive(), "the blast destroyed a dropped item");
                HeroBlasts.Blast logged = blastOf(hero, now(h));
                t.assertTrue(logged != null && logged.power() == 2 && logged.blocks() && !logged.fire(), "logged: " + logged);
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ 14.5 explosiveMoveBreaksBlocksNotPeople

    /**
     * {@code explosiveMoveBreaksBlocksNotPeople}: the fixture's explosive shot at a stone-brick wall opens a hole in it
     * (within vanilla's ray rule from where it struck), leaves the obsidian in the wall and a dropped item before it
     * whole, lights the TNT in reach, and deals only its authored damage to an NPC soldier and a mock player behind it
     * (its splash: the same numbers for both, a hero's damage, not an explosion's); its wind-up draws a disc of the
     * blast's reach. With the game rule {@code mobGriefing} false, and then with {@code heroes.explosiveBlocks} false, the
     * same shot at the same wall breaks no block and lights no TNT, and the damage is the same.
     */
    @GameTest(template = "platform", timeoutTicks = 400, batch = "explosive_shot")
    public static void explosiveMoveBreaksBlocksNotPeople(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 400);
        sweep(t);
        var griefing = h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean griefingWas = griefing.get();
        boolean blocksWas = FCConfig.HERO_EXPLOSIVE_BLOCKS.get();
        t.onCleanup(() -> {
            griefing.set(griefingWas, h.getLevel().getServer());
            FCConfig.HERO_EXPLOSIVE_BLOCKS.set(blocksWas);
        });
        try {
            int[] lanes = {5, 12, 19};
            FactionNpc[] soldiers = new FactionNpc[3];
            ServerPlayer[] players = new ServerPlayer[3];
            ItemEntity[] items = new ItemEntity[3];
            for (int i = 0; i < 3; i++) {
                int zc = lanes[i];
                box(t, 9, 15, 0, 5, zc - 3, zc + 3, Blocks.AIR);
                box(t, 9, 15, 1, 1, zc - 3, zc + 3, Blocks.OBSIDIAN); // a floor no blast breaks: nobody falls
                box(t, 12, 12, 2, 4, zc - 2, zc + 2, Blocks.STONE_BRICKS);
                block(t, 12, 3, zc + 1, Blocks.OBSIDIAN);
                block(t, 12, 4, zc - 1, Blocks.TNT);
                soldiers[i] = foe(t, 13, zc, 100);
                players[i] = player(t, 13, zc + 2, FactionType.GROVE_COVENANT, 100);
                items[i] = dropped(t, 11.5, 2, zc + 0.5);
            }
            HeroEntity hero = bomberAt(t, "fixture_bomber", blastShot(0) + ", " + bombRain(0), 4, lanes[0]);
            hero.setNoAi(true);
            int[] stage = {0};
            long[] forcedAt = {-1};
            long[] doneAt = {-1};
            boolean[] lit = new boolean[3];
            float[] dealt = new float[3];
            List<Map<BlockPos, Float>> worlds = new ArrayList<>(List.of(Map.of(), Map.of(), Map.of()));
            List<Set<BlockPos>> walls = new ArrayList<>(List.of(Set.of(), Set.of(), Set.of()));
            t.onEachTick(() -> {
                int lane = stage[0] - 1;
                if (lane >= 0 && lane < 3) {
                    lit[lane] |= snuffTnt(h);
                } else {
                    snuffTnt(h);
                }
                long now = now(h);
                if (stage[0] == 0 && h.getTick() >= 3) {
                    stage[0] = 1;
                    fire(h, hero, soldiers[0], lanes[0], worlds, walls, 0, forcedAt);
                    return;
                }
                if (lane < 0 || lane > 2 || forcedAt[0] < 0) {
                    return;
                }
                if (doneAt[0] < 0 && blastOf(hero, forcedAt[0]) != null && !hero.combat().running()) {
                    doneAt[0] = now;
                }
                if (doneAt[0] < 0 || now < doneAt[0] + 3) {
                    return;
                }
                // the shot has struck, its run is over: what it did
                int zc = lanes[lane];
                HeroBlasts.Blast b = blastOf(hero, forcedAt[0]);
                FactionNpc s = soldiers[lane];
                ServerPlayer p = players[lane];
                float sl = 100 - s.getHealth(), pl = 100 - p.getHealth();
                t.assertTrue(sl > 0 && Math.abs(sl - pl) < EPS, "lane " + lane + ": soldier lost " + sl + ", player " + pl);
                t.assertTrue(heroHit(s, hero) && heroHit(p, hero), "lane " + lane + ": not the hero's damage last");
                t.assertTrue(items[lane].isAlive(), "lane " + lane + ": the blast destroyed a dropped item");
                t.assertTrue(h.getBlockState(new BlockPos(12, 3, zc + 1)).is(Blocks.OBSIDIAN), "lane " + lane + ": the obsidian broke");
                Set<BlockPos> broken = new HashSet<>(walls.get(lane));
                broken.removeAll(solid(h, 9, 15, 0, 5, zc - 3, zc + 3));
                dealt[lane] = sl;
                if (lane == 0) {
                    t.assertTrue(b.blocks() && b.power() == 2, "the blast: " + b);
                    t.assertTrue(!broken.isEmpty(), "the shot opened no hole");
                    Vec3 at = new Vec3(b.x(), b.y(), b.z());
                    t.assertTrue(rays(worlds.get(0), at, 2F, Blasts.STRONGEST).containsAll(broken),
                            "it broke blocks no ray of power 2 from " + at + " reaches: " + broken);
                    t.assertTrue(broken.containsAll(rays(worlds.get(0), at, 2F, Blasts.WEAKEST)), "it spared blocks every ray breaks");
                    t.assertTrue(lit[0], "the TNT in reach was not lit");
                    // the wind-up's decal: the shot's track and a disc of the blast's reach where it lands
                    double side = Blasts.reach(2) * Decals.DISC_SIDE;
                    boolean disc = false;
                    for (HeroTelegraphs.Sent x : HeroTelegraphs.recent()) {
                        if (x.hero() == hero.getId() && x.shown() && x.at() >= forcedAt[0]) {
                            for (int k = 0; k + Decals.PIECE_FLOATS <= x.packed().length; k += Decals.PIECE_FLOATS) {
                                disc |= Math.abs(x.packed()[k + 4] - side) < 0.01;
                            }
                        }
                    }
                    t.assertTrue(disc, "no disc of the blast's reach in the wind-up's decal");
                    // next: the same shot with mobGriefing false
                    griefing.set(false, h.getLevel().getServer());
                } else {
                    t.assertTrue(!b.blocks(), "lane " + lane + ": the blast could break blocks");
                    t.assertTrue(broken.isEmpty(), "lane " + lane + ": blocks broke: " + broken);
                    t.assertTrue(!lit[lane] && h.getBlockState(new BlockPos(12, 4, zc - 1)).is(Blocks.TNT), "lane " + lane
                            + ": the TNT was lit");
                    t.assertTrue(Math.abs(dealt[lane] - dealt[0]) < EPS, "lane " + lane + ": the damage changed: " + dealt[lane]
                            + " against " + dealt[0]);
                    if (lane == 1) {
                        griefing.set(griefingWas, h.getLevel().getServer());
                        FCConfig.HERO_EXPLOSIVE_BLOCKS.set(false);
                    }
                }
                if (lane == 2) {
                    t.succeed();
                    return;
                }
                stage[0]++;
                doneAt[0] = -1;
                fire(h, hero, soldiers[lane + 1], lanes[lane + 1], worlds, walls, lane + 1, forcedAt);
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** Moves the hero before lane {@code i}'s wall, reads the lane as it stands, and starts the shot at its soldier. */
    private static void fire(GameTestHelper h, HeroEntity hero, FactionNpc soldier, int zc, List<Map<BlockPos, Float>> worlds,
                             List<Set<BlockPos>> walls, int i, long[] forcedAt) {
        Vec3 at = h.absoluteVec(new Vec3(4.5, 2, zc + 0.5));
        hero.moveTo(at.x, at.y, at.z, -90F, 0F);
        hero.setDeltaMovement(Vec3.ZERO);
        worlds.set(i, resistances(h, 9, 15, 0, 5, zc - 3, zc + 3));
        walls.set(i, solid(h, 9, 15, 0, 5, zc - 3, zc + 3));
        forcedAt[0] = now(h);
        hero.setTarget(soldier);
        if (!hero.combat().forceMove("blast_shot")) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("the hero would not start its shot");
        }
    }

    // ------------------------------------------------------------------ at most 6 blasts a use; a zone's first pulse

    /**
     * {@code explosiveRainBlastsAtMostSixTimes}: the fixture's rain calls 8 impacts and only its first 6 blast (6.18:
     * at most 6 a use), each of power 1; a pulsing zone of 3 pulses blasts on its first pulse only. Blocks are kept
     * ({@code heroes.explosiveBlocks} false): only the blasts are counted.
     */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "explosive_rain")
    public static void explosiveRainBlastsAtMostSixTimes(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        sweep(t);
        boolean blocksWas = FCConfig.HERO_EXPLOSIVE_BLOCKS.get();
        t.onCleanup(() -> FCConfig.HERO_EXPLOSIVE_BLOCKS.set(blocksWas));
        try {
            FCConfig.HERO_EXPLOSIVE_BLOCKS.set(false);
            String zone = move("mine_field", "zone", "\"zone_at\": \"target\", \"range\": 12, \"radius\": 2, \"duration\": 2,"
                    + " \"interval\": 1, \"cooldown\": 40, \"weight\": 0, \"effects\": [{\"type\": \"damage\", \"amount\": 1}],"
                    + " \"explosive\": 1");
            HeroEntity rainer = bomberAt(t, "fixture_bomber_rain", blastShot(0) + ", " + bombRain(0), 4, 6);
            HeroEntity zoner = bomberAt(t, "fixture_bomber_zone", zone, 4, 18);
            rainer.setNoAi(true);
            zoner.setNoAi(true);
            FactionNpc a = foe(t, 12, 6, 200), b = foe(t, 12, 18, 200);
            long[] start = {-1};
            h.runAtTickTime(5, t.guard(() -> {
                start[0] = now(h);
                force(t, rainer, a, "bomb_rain");
                force(t, zoner, b, "mine_field");
                t.assertTrue(rainer.combat().runningMove().explosive().blasts() == 6, "the rain's blasts: "
                        + rainer.combat().runningMove().explosive());
            }));
            t.onEachTick(() -> {
                if (start[0] < 0 || now(h) < start[0] + 200) {
                    return;
                }
                int rain = blastsOf(rainer, "bomb_rain", start[0]), mines = blastsOf(zoner, "mine_field", start[0]);
                t.assertTrue(rain == 6, "the rain blasted " + rain + " times, not 6");
                t.assertTrue(mines == 1, "the zone blasted " + mines + " times, not once");
                for (HeroBlasts.Blast x : HeroBlasts.recent()) {
                    if (x.hero() == rainer.getId() && x.at() >= start[0]) {
                        t.assertTrue(x.power() == 1 && !x.blocks(), "a rain blast: " + x);
                    }
                }
                t.succeed();
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ 14.5 explosiveMoveSparesItsOwnBase

    /**
     * {@code explosiveMoveSparesItsOwnBase}: two fixture heroes in their own test base, one fighting an NPC soldier and
     * one a mock player, both targets inside the base's walls box: neither ever picks its explosive shot (it picks its
     * other moves, two light bolts, three times each), whatever its weight. Once the targets stand outside, beyond the blast's reach of the
     * walls box, both pick it. The same for the soldier and the player. Blocks are kept ({@code heroes.explosiveBlocks}
     * false), so the platform stays whole.
     */
    @GameTest(template = "platform", timeoutTicks = 900, batch = "explosive_own_base")
    public static void explosiveMoveSparesItsOwnBase(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 4, 11, 900);
        sweep(t);
        boolean blocksWas = FCConfig.HERO_EXPLOSIVE_BLOCKS.get();
        t.onCleanup(() -> FCConfig.HERO_EXPLOSIVE_BLOCKS.set(blocksWas));
        try {
            FCConfig.HERO_EXPLOSIVE_BLOCKS.set(false);
            // two light bolts, so the pick always has a fresh move (it never plays the same move twice in a row)
            String jab = move("jab", "bolt", "\"class\": \"light\", \"range\": 12, \"cooldown\": 3, \"weight\": 1,"
                    + " \"effects\": [{\"type\": \"damage\", \"amount\": 1}]") + ", " + move("poke", "bolt", "\"class\":"
                    + " \"light\", \"range\": 12, \"cooldown\": 3, \"weight\": 1, \"effects\": [{\"type\": \"damage\","
                    + " \"amount\": 1}]");
            HeroEntity hn = bomberAt(t, "fixture_bomber_home_npc", blastShot(10) + ", " + jab, 12, 10);
            HeroEntity hp = bomberAt(t, "fixture_bomber_home_pl", blastShot(10) + ", " + jab, 12, 14);
            FactionNpc soldier = foe(t, 13, 12, 1000);
            ServerPlayer player = player(t, 11, 12, FactionType.GROVE_COVENANT, 1000);
            HeroEntity[] heroes = {hn, hp};
            LivingEntity[] targets = {soldier, player};
            int[] jabs = new int[2], blasts = new int[2];
            String[] last = {null, null};
            boolean[] outside = {false};
            long[] movedAt = {-1};
            h.runAtTickTime(3, t.guard(() -> {
                hn.setTarget(soldier);
                hp.setTarget(player);
            }));
            t.onEachTick(() -> {
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = heroes[i];
                    if (x.getTarget() != targets[i]) {
                        x.setTarget(targets[i]);
                    }
                    String m = x.combat().running() ? x.combat().lastMove() : null;
                    if (m != null && !m.equals(last[i])) {
                        if (m.equals("jab") || m.equals("poke")) {
                            jabs[i]++;
                        } else if (m.equals("blast_shot")) {
                            t.assertTrue(outside[0], x.heroId() + " picked its blast at a target inside its own base");
                            blasts[i]++;
                        }
                    }
                    last[i] = m;
                }
                if (!outside[0] && jabs[0] >= 3 && jabs[1] >= 3) {
                    // the pick works (it jabs) and never blasts at home: now the targets stand outside the walls box
                    outside[0] = true;
                    movedAt[0] = now(h);
                    Vec3 s = h.absoluteVec(new Vec3(2.5, 2, 3.5)), p = h.absoluteVec(new Vec3(2.5, 2, 21.5));
                    soldier.moveTo(s.x, s.y, s.z, 0F, 0F);
                    player.moveTo(p.x, p.y, p.z, 0F, 0F);
                    double dS = HeroBlasts.ownSideDistance(hn, s.x, s.z), dP = HeroBlasts.ownSideDistance(hp, p.x, p.z);
                    t.assertTrue(dS > Blasts.reach(2) && dP > Blasts.reach(2), "outside but within the blast's reach: "
                            + dS + ", " + dP);
                    t.assertTrue(HeroBlasts.ownSideDistance(hn, soldier.getX(), soldier.getZ()) > 0, "still inside");
                }
                if (outside[0] && blasts[0] >= 1 && blasts[1] >= 1) {
                    t.succeed();
                }
            });
            h.runAtTickTime(880, t.guard(() -> t.fail("jabs " + jabs[0] + "/" + jabs[1] + ", blasts " + blasts[0] + "/" + blasts[1]
                    + ", outside " + outside[0] + " since " + movedAt[0] + "; heroes at " + h.relativeVec(hn.position()) + " and "
                    + h.relativeVec(hp.position()) + ", targets " + hn.getTarget() + " / " + hp.getTarget() + ", own side from targets "
                    + HeroBlasts.ownSideDistance(hn, soldier.getX(), soldier.getZ()) + " / "
                    + HeroBlasts.ownSideDistance(hp, player.getX(), player.getZ()) + ", last moves " + hn.combat().lastMove() + " / " + hp.combat().lastMove())));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
