package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.StructureRecord;
import com.factionclash.faction.FactionType;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hostile soldiers against an enemy player they cannot simply walk up to (the owner's report: a player in an animal
 * pen, or behind a broken door with a block placed in the doorway, killed soldiers that just stood within reach).
 * A mock survival player stands still where the test puts it; every test has its own batch, because a real player
 * in the level draws every hostile mob and soldier nearby.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class CombatReachGameTests {
    private CombatReachGameTests() {
    }

    /** The player's health: high, so a test never has to deal with a dead player. */
    private static final float PLAYER_HP = 1000F;

    /** A mock survival player of {@code faction} standing at relative {@code rel}; removed again by {@link #remove}. */
    static ServerPlayer enemyPlayer(GameTestHelper h, BlockPos rel, FactionType faction) {
        ServerLevel level = h.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "fc-raider"), false);
        ServerPlayer p = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(c);
        level.getServer().getPlayerList().placeNewPlayer(c, p, cookie);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos abs = h.absolutePos(rel);
        p.teleportTo(level, abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0F, 0F);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(PLAYER_HP);
        p.setHealth(PLAYER_HP);
        CampaignData.get(level.getServer()).players.put(p.getUUID(), faction);
        // a mock player's own living tick never runs (no client connection ticks it): count down its hurt cooldown
        h.onEachTick(() -> p.invulnerableTime = Math.max(0, p.invulnerableTime - 1));
        return p;
    }

    /**
     * The mock player has no physics of its own (a real client moves it): when the block under its feet is gone, it
     * drops a block, as it would fall.
     */
    static void gravity(GameTestHelper h, ServerPlayer p) {
        h.onEachTick(() -> {
            BlockPos below = p.blockPosition().below();
            if (h.getLevel().getBlockState(below).getCollisionShape(h.getLevel(), below).isEmpty()) {
                p.teleportTo(h.getLevel(), p.getX(), p.getY() - 1, p.getZ(), 0F, 0F);
            }
        });
    }

    static void remove(GameTestHelper h, ServerPlayer p) {
        CampaignData.get(h.getLevel().getServer()).players.remove(p.getUUID());
        if (h.getLevel().getServer().getPlayerList().getPlayer(p.getUUID()) != null) {
            h.getLevel().getServer().getPlayerList().remove(p);
        }
    }

    private static FactionNpc soldier(GameTestHelper h, int x, int z, FactionType f, NpcRole role) {
        FactionNpc n = h.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(x, 2, z));
        n.faction(f);
        n.role(role);
        if (role == NpcRole.ARCHER) {
            n.equip(new ItemStack(Items.BOW));
            n.setAmmo(new ItemStack(Items.ARROW));
        } else {
            n.equip(new ItemStack(Items.IRON_SWORD));
        }
        return n;
    }

    /** Whether the player could hit {@code n} from where it stands (3 blocks from the eye, the server allows 4). */
    static boolean inPlayersReach(ServerPlayer p, FactionNpc n) {
        return n.getBoundingBox().distanceToSqr(p.getEyePosition()) <= 4.0 * 4.0;
    }

    /** The player has been hurt by a faction NPC. */
    static boolean hurtByNpc(ServerPlayer p) {
        return p.getHealth() < PLAYER_HP && p.getLastHurtByMob() instanceof FactionNpc;
    }

    /** Sets fences (and gates) and lets them connect to each other, as placing them by hand does. */
    static void connect(GameTestHelper h, List<BlockPos> rel) {
        for (int pass = 0; pass < 2; pass++) {
            for (BlockPos r : rel) {
                BlockPos a = h.absolutePos(r);
                BlockState s = h.getLevel().getBlockState(a);
                h.getLevel().setBlock(a, Block.updateFromNeighbourShapes(s, h.getLevel(), a), Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * An animal pen of oak fences from (x0, z0) to (x1, z1) (relative, at y 2) with a closed oak gate in its north side
     * at (gx, z0) unless {@code gx} is out of range. Returns the fence and gate cells.
     */
    static List<BlockPos> pen(GameTestHelper h, int x0, int z0, int x1, int z1, int gx) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            cells.add(new BlockPos(x, 2, z0));
            cells.add(new BlockPos(x, 2, z1));
        }
        for (int z = z0 + 1; z < z1; z++) {
            cells.add(new BlockPos(x0, 2, z));
            cells.add(new BlockPos(x1, 2, z));
        }
        for (BlockPos c : cells) {
            if (c.getX() == gx && c.getZ() == z0) {
                h.setBlock(c, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING,
                        h.getTestRotation().rotate(Direction.NORTH)));
            } else {
                h.setBlock(c, Blocks.OAK_FENCE);
            }
        }
        connect(h, cells);
        return cells;
    }

    static String describe(GameTestHelper h, ServerPlayer p, List<FactionNpc> npcs) {
        StringBuilder sb = new StringBuilder();
        for (FactionNpc n : npcs) {
            BlockPos r = h.relativePos(n.blockPosition());
            sb.append(n.getRole().id).append(" at ").append(r.toShortString()).append(" d=")
                    .append(String.format("%.1f", Math.sqrt(n.getBoundingBox().distanceToSqr(p.getEyePosition()))))
                    .append(" target=").append(n.getTarget() == p ? "player" : String.valueOf(n.getTarget()))
                    .append(" status='").append(n.status()).append("' hp=").append(n.getHealth()).append("; ");
        }
        sb.append("player hp ").append(p.getHealth());
        return sb.toString();
    }

    /**
     * The owner's first report: an enemy player inside an animal pen, soldiers outside. They open the gate like a
     * player (and like the animal carer), go in, close it behind them so the cow stays in, and hit the player.
     */
    @GameTest(template = "platform", timeoutTicks = 400, batch = "reach_pen")
    public static void soldiersOpenThePenGateToReachAPlayer(GameTestHelper h) {
        List<BlockPos> fence = pen(h, 7, 9, 17, 19, 12);
        BlockPos gate = h.absolutePos(new BlockPos(12, 2, 9));
        Cow cow = h.spawn(EntityType.COW, new BlockPos(9, 2, 17));
        ServerPlayer p = enemyPlayer(h, new BlockPos(12, 2, 15), FactionType.GROVE_COVENANT);
        List<FactionNpc> npcs = List.of(soldier(h, 9, 3, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN),
                soldier(h, 15, 3, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN));
        npcs.forEach(n -> n.setTarget(p));
        AABB inside = new AABB(h.absolutePos(new BlockPos(8, 1, 10))).minmax(new AABB(h.absolutePos(new BlockPos(16, 4, 18))));
        int[] toggles = {0};
        boolean[] wasOpen = {false};
        h.onEachTick(() -> {
            boolean open = h.getLevel().getBlockState(gate).getValue(FenceGateBlock.OPEN);
            if (open != wasOpen[0]) {
                toggles[0]++;
                wasOpen[0] = open;
            }
        });
        h.runAtTickTime(398, () -> {
            remove(h, p);
            h.fail("the soldiers never reached the player in the pen: " + describe(h, p, npcs) + "; gate toggles " + toggles[0]);
        });
        h.succeedWhen(() -> {
            h.assertTrue(hurtByNpc(p), "the player was not hit yet");
            boolean in = npcs.stream().anyMatch(n -> inside.contains(n.position()));
            h.assertTrue(in, "no soldier inside the pen");
            boolean inGate = npcs.stream().anyMatch(n -> n.getBoundingBox().intersects(new AABB(gate)));
            h.assertTrue(!inGate && !h.getLevel().getBlockState(gate).getValue(FenceGateBlock.OPEN), "the gate is still open");
            if (!inside.contains(cow.position())) {
                remove(h, p);
                h.fail("the cow got out of the pen");
            }
            int cycles = npcs.stream().mapToInt(n -> n.gateGoal().cycles).sum();
            if (toggles[0] > 8 || cycles > 4) {
                remove(h, p);
                h.fail("the gate was opened and closed " + toggles[0] / 2 + " times (" + cycles + " by the soldiers)");
            }
            remove(h, p);
        });
    }

    /**
     * The owner's second report: the door of a base wall broken and a block (dirt) put into the doorway, the player
     * standing outside. The soldiers inside break the player's block at player speed (never the planned wall), walk
     * through and hit the player.
     */
    @GameTest(template = "platform", timeoutTicks = 500, batch = "reach_door")
    public static void soldiersBreakAPlayersBlockInABrokenDoorway(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 500);
        Map<BlockPos, BlockState> plan = new HashMap<>();
        Direction south = h.getTestRotation().rotate(Direction.SOUTH);
        for (int x = 0; x <= 23; x++) { // across the whole platform: the only way through is the doorway
            for (int y = 2; y <= 4; y++) {
                BlockPos r = new BlockPos(x, y, 9);
                BlockPos a = h.absolutePos(r);
                if (x == 12 && y <= 3) {
                    BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, south)
                            .setValue(DoorBlock.HALF, y == 2 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
                    plan.put(a, door);
                    continue; // the door is broken: nothing there now
                }
                h.setBlock(r, Blocks.STONE);
                plan.put(a, Blocks.STONE.defaultBlockState());
            }
        }
        t.base.structures.add(StructureRecord.of("Test wall", plan));
        t.base.invalidatePlanIndex();
        BlockPos dirt = new BlockPos(12, 2, 9);
        h.setBlock(dirt, Blocks.DIRT); // the player's block in the doorway, under the gap where the door's top half was
        ServerPlayer p = enemyPlayer(h, new BlockPos(12, 2, 11), FactionType.GROVE_COVENANT);
        List<FactionNpc> npcs = List.of(t.register(soldier(h, 8, 4, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN)),
                t.register(soldier(h, 16, 4, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN)));
        npcs.forEach(n -> n.setTarget(p));
        t.onCleanup(() -> remove(h, p));
        h.runAtTickTime(497, () -> t.fail("the soldiers never got through the doorway: " + describe(h, p, npcs)
                + "; dirt " + h.getBlockState(dirt).getBlock()));
        t.onEachTick(() -> {
            for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
                BlockState now = h.getLevel().getBlockState(e.getKey());
                if (e.getValue().is(Blocks.STONE) && !now.is(Blocks.STONE)) {
                    t.fail("a planned wall block was broken at " + h.relativePos(e.getKey()).toShortString());
                }
            }
        });
        t.onEachTick(() -> {
            if (hurtByNpc(p) && h.getBlockState(dirt).isAir()) {
                t.succeed();
            }
        });
    }

    /** A player on top of a 2-high dirt pillar: a soldier breaks the block under its feet (or climbs) and hits it. */
    @GameTest(template = "platform", timeoutTicks = 400, batch = "reach_perch")
    public static void soldierReachesAPlayerOnATwoHighPillar(GameTestHelper h) {
        h.setBlock(new BlockPos(12, 2, 14), Blocks.DIRT);
        h.setBlock(new BlockPos(12, 3, 14), Blocks.DIRT);
        ServerPlayer p = enemyPlayer(h, new BlockPos(12, 4, 14), FactionType.GROVE_COVENANT);
        gravity(h, p);
        FactionNpc n = soldier(h, 12, 6, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN);
        n.setTarget(p);
        h.runAtTickTime(398, () -> {
            remove(h, p);
            h.fail("the soldier never hit the player on its pillar: " + describe(h, p, List.of(n)));
        });
        h.succeedWhen(() -> {
            h.assertTrue(hurtByNpc(p), "the player was not hit yet");
            h.assertTrue(n.pursuit().breaks >= 1 && h.getBlockState(new BlockPos(12, 3, 14)).isAir(), "it did not break the block under the player");
            remove(h, p);
        });
    }

    /**
     * A player on top of a 2-high perch that is part of its base's plan (so it may not be broken): a soldier carrying
     * dirt builds itself up a block beside it, as a player would, and hits the player; the perch stays.
     */
    @GameTest(template = "platform", timeoutTicks = 400, batch = "reach_pillar")
    public static void soldierBuildsItselfUpToAPlayerOnAPlannedPerch(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.GROVE_COVENANT, 2, 11, 400);
        BlockPos low = new BlockPos(12, 2, 14), high = new BlockPos(12, 3, 14);
        h.setBlock(low, Blocks.STONE);
        h.setBlock(high, Blocks.STONE);
        t.base.structures.add(StructureRecord.of("Test perch", Map.of(h.absolutePos(low), Blocks.STONE.defaultBlockState(),
                h.absolutePos(high), Blocks.STONE.defaultBlockState())));
        t.base.invalidatePlanIndex();
        ServerPlayer p = enemyPlayer(h, new BlockPos(12, 4, 14), FactionType.GROVE_COVENANT);
        gravity(h, p);
        t.onCleanup(() -> remove(h, p));
        FactionNpc n = t.track(soldier(h, 12, 6, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN));
        n.inventory().addItem(new ItemStack(Items.DIRT, 4));
        n.setTarget(p);
        h.runAtTickTime(397, () -> t.fail("the soldier never got up to the player: " + describe(h, p, List.of(n))
                + "; pillars " + n.pursuit().pillars + ", plans " + n.pursuit().plans));
        t.onEachTick(() -> {
            t.assertTrue(h.getBlockState(low).is(Blocks.STONE) && h.getBlockState(high).is(Blocks.STONE), "the planned perch was broken");
            if (hurtByNpc(p)) {
                t.assertTrue(n.pursuit().pillars >= 1, "hit without building up?");
                t.succeed();
            }
        });
    }

    /** A player on a single block: struck from the ground beside it, nothing broken or built. */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "reach_step")
    public static void soldierHitsAPlayerOneBlockUpWithoutBreakingAnything(GameTestHelper h) {
        h.setBlock(new BlockPos(12, 2, 14), Blocks.DIRT);
        ServerPlayer p = enemyPlayer(h, new BlockPos(12, 3, 14), FactionType.GROVE_COVENANT);
        FactionNpc n = soldier(h, 12, 6, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN);
        n.inventory().addItem(new ItemStack(Items.DIRT, 4));
        n.setTarget(p);
        h.runAtTickTime(298, () -> {
            remove(h, p);
            h.fail("the soldier never hit the player one block up: " + describe(h, p, List.of(n)));
        });
        h.succeedWhen(() -> {
            h.assertTrue(hurtByNpc(p), "the player was not hit yet");
            if (n.pursuit().breaks > 0 || n.pursuit().pillars > 0 || !h.getBlockState(new BlockPos(12, 2, 14)).is(Blocks.DIRT)) {
                remove(h, p);
                h.fail("it broke or built something to reach a player one block up");
            }
            remove(h, p);
        });
    }

    /**
     * A player inside a pen with no gate (all of it planned fence): the soldiers cannot get in, so they keep out of
     * the player's reach, watching it, and call the archer, who shoots. Once the player opens a way out (a fence
     * removed), they go in and hit it. Waiting costs few path searches.
     */
    @GameTest(template = "platform", timeoutTicks = 700, batch = "reach_hold")
    public static void soldiersHoldOutOfReachOfAPlayerTheyCannotReach(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 700);
        List<BlockPos> fence = pen(h, 7, 9, 17, 19, -1);
        Map<BlockPos, BlockState> plan = new HashMap<>();
        for (BlockPos r : fence) {
            plan.put(h.absolutePos(r), Blocks.OAK_FENCE.defaultBlockState());
        }
        t.base.structures.add(StructureRecord.of("Test pen", plan));
        t.base.invalidatePlanIndex();
        ServerPlayer p = enemyPlayer(h, new BlockPos(12, 2, 11), FactionType.GROVE_COVENANT);
        t.onCleanup(() -> remove(h, p));
        // the soldiers start right at the fence, within the player's reach (where they stood before)
        List<FactionNpc> melee = List.of(t.register(soldier(h, 11, 8, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN)),
                t.register(soldier(h, 13, 8, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN)));
        FactionNpc archer = t.register(soldier(h, 21, 2, FactionType.EMBER_LEGION, NpcRole.ARCHER));
        archer.setNoAi(true); // until it has been called: an archer that saw the player itself would prove nothing
        melee.forEach(n -> n.setTarget(p));
        List<FactionNpc> all = List.of(melee.get(0), melee.get(1), archer);
        int[] inReach = {0};
        int[] searches = new int[2];
        boolean[] arrow = {false};
        float[] hp = {PLAYER_HP};
        AABB inside = new AABB(h.absolutePos(new BlockPos(8, 1, 10))).minmax(new AABB(h.absolutePos(new BlockPos(16, 4, 18))));
        t.onEachTick(() -> {
            long tick = h.getTick();
            if (p.getHealth() < hp[0]) {
                var src = p.getLastDamageSource();
                arrow[0] |= src != null && src.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow;
                hp[0] = p.getHealth();
            }
            if (tick == 150) {
                t.assertTrue(archer.getTarget() == p, "the archer was never called: " + describe(h, p, all));
                archer.setNoAi(false);
            }
            if (tick == 200) {
                for (int i = 0; i < 2; i++) {
                    searches[i] = ((com.factionclash.npc.NpcPathNavigation) melee.get(i).getNavigation()).searches;
                }
            }
            if (tick == 1) {
                t.assertTrue(melee.stream().allMatch(n -> inPlayersReach(p, n)), "the soldiers should start within the player's reach");
            }
            if (tick >= 100 && tick < 400) {
                for (FactionNpc n : melee) {
                    if (inPlayersReach(p, n)) {
                        inReach[0]++;
                    }
                }
            }
            if (tick == 399) {
                t.assertTrue(inReach[0] == 0, "soldiers stood within the player's reach for " + inReach[0] + " ticks: " + describe(h, p, all));
                t.assertTrue(melee.stream().allMatch(n -> n.getTarget() == p), "a soldier stopped watching the player: " + describe(h, p, all));
                t.assertTrue(melee.stream().allMatch(n -> n.distanceTo(p) < 10), "a soldier wandered off: " + describe(h, p, all));
                t.assertTrue(melee.get(0).distanceTo(melee.get(1)) > 1.5, "the soldiers wait on one spot instead of spreading out: " + describe(h, p, all));
                t.assertTrue(arrow[0], "the archer never hit the player: " + describe(h, p, all));
                for (int i = 0; i < 2; i++) {
                    int n = ((com.factionclash.npc.NpcPathNavigation) melee.get(i).getNavigation()).searches - searches[i];
                    // vanilla searched every 4 to 25 ticks (10 to 50 in 200 ticks); waiting checks every 40 ticks
                    t.assertTrue(n <= 15, "a waiting soldier searched " + n + " paths in 200 ticks");
                }
                for (BlockPos r : fence) {
                    t.assertTrue(h.getBlockState(r).is(Blocks.OAK_FENCE), "a planned fence was broken at " + r.toShortString());
                }
                // the player breaks out: a fence gone, a way in
                h.setBlock(new BlockPos(12, 2, 9), Blocks.AIR);
                p.setHealth(PLAYER_HP);
                p.setLastHurtByMob(null);
            }
            if (tick > 400 && p.getHealth() < PLAYER_HP && p.getLastHurtByMob() instanceof FactionNpc n && melee.contains(n)
                    && melee.stream().anyMatch(m -> inside.contains(m.position()))) {
                t.succeed();
            }
        });
        h.runAtTickTime(697, () -> t.fail("the soldiers did not go in once there was a way: " + describe(h, p, all)));
    }

    /**
     * What a fighter may break and build in a fight: never a planned block, an own player's edit only if it is not of
     * that faction, unplanned blocks in its own side's base only if a hostile player placed them, never a base's
     * ground, a container or a block beside water; in its own side's base it builds nothing.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void fightersBreakOnlyWhatIsNotTheirsToKeep(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 40);
        try {
            var level = h.getLevel();
            BlockPos a = new BlockPos(10, 2, 10), b = new BlockPos(11, 2, 10), c = new BlockPos(12, 2, 10), d = new BlockPos(13, 2, 10),
                    e = new BlockPos(14, 2, 10), f = new BlockPos(12, 1, 12), g = new BlockPos(10, 2, 14), w = new BlockPos(20, 2, 4),
                    i = new BlockPos(20, 2, 20), air = new BlockPos(12, 2, 13), out = new BlockPos(20, 2, 16), gap = new BlockPos(15, 2, 14);
            Map<BlockPos, BlockState> plan = new HashMap<>();
            plan.put(h.absolutePos(a), Blocks.STONE.defaultBlockState());
            plan.put(h.absolutePos(b), Blocks.OAK_DOOR.defaultBlockState());
            plan.put(h.absolutePos(c), Blocks.STONE.defaultBlockState());
            plan.put(h.absolutePos(gap), Blocks.STONE.defaultBlockState()); // broken: air now
            t.base.structures.add(StructureRecord.of("Test plan", plan));
            t.base.invalidatePlanIndex();
            h.setBlock(a, Blocks.STONE);
            h.setBlock(b, Blocks.DIRT); // a raider's block where the door was
            h.setBlock(c, Blocks.DIRT); // an Ember player's own change, kept by repairs
            t.base.playerEdits.add(h.absolutePos(c).asLong());
            h.setBlock(d, Blocks.DIRT); // unplanned, inside the walls box
            h.setBlock(e, Blocks.DIRT); // unplanned, put there by a hostile player
            t.base.intruderBlocks.add(h.absolutePos(e).asLong());
            h.setBlock(g, Blocks.CHEST);
            h.setBlock(w, Blocks.DIRT);
            h.setBlock(w.east(), Blocks.WATER);
            h.setBlock(i, Blocks.DIRT);
            FactionNpc own = t.register(soldier(h, 4, 20, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN));
            FactionNpc foe = t.track(soldier(h, 6, 20, FactionType.GROVE_COVENANT, NpcRole.SWORDSMAN));
            java.util.function.BiPredicate<FactionNpc, BlockPos> may = (n, r) -> com.factionclash.combat.Reach.mayBreak(level, h.absolutePos(r), n);
            h.assertFalse(may.test(own, a) || may.test(foe, a), "a planned block must never be broken");
            h.assertTrue(may.test(own, b) && may.test(foe, b), "a foreign block in a planned cell may be broken");
            h.assertFalse(may.test(own, c), "an own player's edit must be left to the own side");
            h.assertTrue(may.test(foe, c), "an enemy may break an edit of the base's players");
            h.assertFalse(may.test(own, d), "an unplanned block in its own base is not its to break");
            h.assertTrue(may.test(foe, d), "an enemy may break an unplanned block in the base");
            h.assertTrue(may.test(own, e) && may.test(foe, e), "a hostile player's block may be broken");
            h.assertFalse(may.test(own, f) || may.test(foe, f), "the base's ground must never be broken");
            h.assertFalse(may.test(own, g) || may.test(foe, g), "a container must never be broken");
            h.assertFalse(may.test(own, w) || may.test(foe, w), "a block holding back water must never be broken");
            h.assertTrue(may.test(own, i) && may.test(foe, i), "a plain block outside every base may be broken");
            java.util.function.BiPredicate<FactionNpc, BlockPos> place = (n, r) -> com.factionclash.combat.Reach.mayPlace(level, h.absolutePos(r), n);
            h.assertFalse(place.test(own, air), "no pillar in its own base");
            h.assertTrue(place.test(foe, air), "an enemy may build itself up in the base");
            h.assertTrue(place.test(own, out), "anyone may build up outside the bases");
            h.assertFalse(place.test(foe, gap), "never a block into a planned cell (a gap in a wall stays for the builders)");
        } finally {
            t.cleanup();
        }
        h.succeed();
    }
}
