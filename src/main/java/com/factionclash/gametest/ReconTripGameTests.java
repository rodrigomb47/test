package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.FactionDirector;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.campaign.ReconOrder;
import com.factionclash.campaign.ReconOrders;
import com.factionclash.campaign.Report;
import com.factionclash.campaign.RollCall;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.campaign.Intel;
import com.factionclash.logic.campaign.ReconRules;
import com.factionclash.logic.campaign.ReconRules.Side;
import com.factionclash.logic.campaign.Whereabouts;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import com.factionclash.storage.FactionStorage;
import com.factionclash.work.Inv;
import com.factionclash.work.Scaffold;
import com.factionclash.work.ScoutJob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The recon scout's trip, run for real ({@code docs/design/recon.md} 10.2, task R4b): a scout of our base takes the order
 * at home, fetches its dirt, walks round to the enemy base's vantage, climbs looking after each block, watches the yard
 * for a minute counting each soldier once by its face, takes the pillar down and carries the report home to the station.
 * Each test runs in the 32 x 32 {@code mobility_field} with {@code skyAccess = true} (the climb needs open sky): our base
 * in one corner (centre (6, 2, 6), walls half 1, a home area of 5 blocks, the storehouse stand at (4, 2, 4), a station
 * placed by the test) and the target at (16, 2, 21), a 4-high stone ring of half 4 with the real band scaled to fit the
 * field (13 to 16 blocks from its centre, in its transient {@code testStandoff} and {@code testSight}). The defenders are
 * soldiers with no AI, placed and moved by the test: nothing depends on whether a guard would pick the scout as a target
 * (recon.md 10.2). Every pillar, ring and entity a test made goes in its cleanup.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class ReconTripGameTests {
    private ReconTripGameTests() {
    }

    private static final String FIELD = HeroMobilityGameTests.FIELD;
    static final FactionType SIDE = FactionType.TIDE_ALLIANCE, ENEMY = FactionType.GROVE_COVENANT;
    /** Our base's centre, and the target's centre, its walls' half and its scaled band (relative). */
    static final int HX = 6, HZ = 6, TX = 16, TZ = 21, HALF = 4, STANDOFF = 13, SIGHT = 16;
    /** The station block and the stand the scout reports from (relative). */
    static final BlockPos STATION = new BlockPos(9, 2, 6), STATION_STAND = new BlockPos(8, 2, 6);

    /** A test's field: our base, our faction, the target, its order, our scout, and the blocks the test built. */
    record Field(GameTestBases.Handle t, FactionState f, BaseState target, ReconOrder order, FactionNpc scout, List<BlockPos> built) {
        ScoutJob job() {
            return (ScoutJob) scout.job();
        }

        /** Our faction's report of the target (null: none). */
        Report kept() {
            return ReconOrders.report(f, order);
        }
    }

    /**
     * Our base, its station, 30 dirt in its storehouse, the target with a stone ring {@code ring} blocks high (0: none),
     * our faction's report of it and an open order to watch it, and a scout at home with no dirt. The cleanup takes away
     * the ring, any dirt left in the field's bands, the dropped items and the entities.
     */
    static Field field(GameTestHelper h, int timeout, int ring, boolean scout) {
        return field(h, timeout, ring, scout, false);
    }

    /** As {@link #field(GameTestHelper, int, int, boolean)}; {@code carried}: the scout carries its 22 dirt and the storehouse has none. */
    static Field field(GameTestHelper h, int timeout, int ring, boolean scout, boolean carried) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 4, timeout, new BlockPos(4, 2, 4), new BlockPos(HX, 2, HZ));
        List<BlockPos> built = new ArrayList<>();
        try {
            ServerLevel level = h.getLevel();
            FactionState f = t.data.faction(SIDE);
            KnowledgeGameTests.keepRaidState(t, f);
            long now = level.getGameTime();
            t.data.startedAt = now; // the council's order window lies a raid interval away: it places no order of its own
            h.setBlock(STATION, com.factionclash.registry.ModBlocks.REPORTING_STATION.get().defaultBlockState());
            t.base.points.put(BaseState.STATION, h.absolutePos(STATION));
            t.base.points.put(BaseState.STATION_STAND, h.absolutePos(STATION_STAND));
            t.onCleanup(() -> h.setBlock(STATION, Blocks.AIR));
            t.onCleanup(() -> {
                for (int x = 0; x < 32; x++) {
                    for (int z = 0; z < 32; z++) {
                        for (int y = 2; y <= 25; y++) {
                            BlockPos p = new BlockPos(x, y, z);
                            if (Scaffold.isBlock(h.getBlockState(p))) {
                                h.setBlock(p, Blocks.AIR); // a pillar a failed test left standing
                            }
                        }
                    }
                }
                built.forEach(p -> h.setBlock(p, Blocks.AIR));
                AABB box = new AABB(h.absolutePos(BlockPos.ZERO)).expandTowards(32, 26, 32);
                level.getEntitiesOfClass(ItemEntity.class, box).forEach(Entity::discard);
            });
            BaseState target = KnowledgeGameTests.enemyBase(t, ENEMY, h.absolutePos(new BlockPos(TX, 2, TZ)));
            target.testHalf = HALF;
            target.testStandoff = STANDOFF;
            target.testSight = SIGHT;
            if (ring > 0) {
                ring(h, built, ring);
            }
            KnowledgeGameTests.deliver(t, f, KnowledgeGameTests.delivered(Report.Kind.ENEMY_BASE, target.center, ENEMY, now, null, 0), now);
            ReconOrder order = new ReconOrder(target.center, ENEMY, Report.Kind.ENEMY_BASE, t.base.id, now, false);
            f.reconOrders.add(order);
            if (!carried) {
                FactionStorage.insert(level, t.base, new ItemStack(Items.DIRT, 30));
            }
            FactionNpc n = scout ? t.npc(5, 7, NpcRole.SCOUT) : null;
            if (n != null) {
                n.equip(new ItemStack(Items.IRON_SWORD)); // a scout's tool: without one it would go home for one now and then
                if (carried) {
                    n.inventory().setItem(0, new ItemStack(Items.DIRT, ReconRules.BLOCKS));
                }
            }
            return new Field(t, f, target, order, n, built);
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** The target's walls: a ring of stone {@code height} blocks high, half {@link #HALF} from its centre. */
    static void ring(GameTestHelper h, List<BlockPos> built, int height) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int x = TX - HALF; x <= TX + HALF; x++) {
            for (int z = TZ - HALF; z <= TZ + HALF; z++) {
                if (x == TX - HALF || x == TX + HALF || z == TZ - HALF || z == TZ + HALF) {
                    for (int y = 2; y < 2 + height; y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        h.setBlock(p, stone);
                        built.add(p);
                    }
                }
            }
        }
    }

    /** An enemy soldier with no AI at relative (x, 2, z) (the cleanup discards it). */
    static FactionNpc defender(GameTestBases.Handle t, int x, int z) {
        FactionNpc n = t.helper.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(x, 2, z));
        n.faction(ENEMY);
        n.role(NpcRole.SWORDSMAN);
        n.setNoAi(true);
        return t.track(n);
    }

    static void put(GameTestBases.Handle t, LivingEntity e, double x, double z) {
        Vec3 at = Vec3.atBottomCenterOf(t.helper.absolutePos(new BlockPos((int) Math.floor(x), 2, (int) Math.floor(z))));
        e.moveTo(at.x, at.y, at.z, e.getYRot(), 0F);
        e.setDeltaMovement(Vec3.ZERO);
    }

    /** The entity as its chunk would save it, then gone from the world as at a chunk unload. */
    static CompoundTag unload(Entity e) {
        CompoundTag tag = new CompoundTag();
        e.save(tag);
        e.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        return tag;
    }

    /** Its chunk loads again: the saved entity joins the level as from disk, with a new runtime id and the same UUID. */
    static Entity reload(GameTestBases.Handle t, CompoundTag tag) {
        ServerLevel level = t.helper.getLevel();
        Entity e = EntityType.loadEntityRecursive(tag, level, x -> x);
        if (e == null || !level.addFreshEntity(e)) {
            throw new GameTestAssertException("the saved entity did not load");
        }
        return t.track(e);
    }

    /** Dirt blocks standing in column (x, z) of the field (relative). */
    static int dirtIn(GameTestHelper h, BlockPos column) {
        int n = 0;
        for (int y = 2; y <= 25; y++) {
            if (Scaffold.isBlock(h.getBlockState(new BlockPos(column.getX(), y, column.getZ())))) {
                n++;
            }
        }
        return n;
    }

    static BlockPos rel(GameTestHelper h, BlockPos abs) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        return abs.subtract(o);
    }

    static int dirtCarried(FactionNpc n) {
        return Inv.count(n.inventory(), s -> s.is(Items.DIRT));
    }

    /** What a trip saw, as the test follows it tick by tick. */
    static final class Trip {
        BlockPos column;
        int top = -1, firstGood = -1;
        long watchFrom = -1;
        boolean sawWatch;
        ReconRules.ScoutReport reported;
        Report filed;
    }

    /** Follows the scout's trip each tick: its pillar's column and top, its first good height, the report at the station. */
    static void follow(Field fl, Trip trip) {
        ScoutJob job = fl.job();
        Scaffold.Pillar p = job.reconPillar();
        if (p != null && p.height > 0) {
            if (trip.column == null) {
                trip.column = rel(fl.t().helper, p.base);
            }
            trip.top = Math.max(trip.top, p.height);
        }
        if (job.reconFirstGood() >= 0) {
            trip.firstGood = job.reconFirstGood();
        }
        if (job.reconWatch() != null && !trip.sawWatch) {
            trip.sawWatch = true;
            trip.watchFrom = fl.t().helper.getLevel().getGameTime();
        }
        Report kept = fl.kept();
        if (trip.filed == null && kept != null && (kept.recon != null || kept.reconFailures > 0)) {
            trip.filed = kept;
            trip.reported = fl.order().reported;
        }
    }

    // ------------------------------------------------------------------ 10.2 item 1: a recon of a walled base

    /**
     * {@code reconOfAWalledBase} (recon.md 10.2 item 1): three enemy soldiers in a 4-high ring; our scout takes the order
     * at the storehouse with its 22 dirt from there, walks round by the north-west corner to the west side (the gate
     * facing home), climbs on the axis at the standoff looking after each block, stops two blocks above its first good
     * look, watches a minute, takes the pillar down (no dirt left in the column, all 22 back in its backpack) and walks
     * home; at the station its order learns of a good recon and the faction's report holds it: 3 soldiers, their faces,
     * "pillar N", watched 1200 ticks.
     */
    @GameTest(template = FIELD, timeoutTicks = 4000, batch = "recon_trip_walled", skyAccess = true)
    public static void reconOfAWalledBase(GameTestHelper h) {
        Field fl = field(h, 4000, 4, true);
        GameTestBases.Handle t = fl.t();
        Set<UUID> faces = new HashSet<>();
        try {
            faces.add(defender(t, 17, 19).getUUID());
            faces.add(defender(t, 18, 21).getUUID());
            faces.add(defender(t, 17, 23).getUUID());
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        Trip trip = new Trip();
        t.onEachTick(() -> {
            follow(fl, trip);
            if (trip.filed == null) {
                return;
            }
            Intel.Recon r = trip.filed.recon;
            t.assertTrue(trip.reported == ReconRules.ScoutReport.GOOD, "its order should learn of a good recon: " + trip.reported);
            t.assertTrue(r != null && r.good() && r.soldiers() == 3 && r.faces().equals(faces), "the recon should count the 3 soldiers by "
                    + "their faces: " + r);
            t.assertTrue(r.watchTicks() >= ReconRules.WATCH_TICKS, "a whole minute's watch: " + r.watchTicks());
            t.assertTrue(trip.column != null && trip.column.equals(new BlockPos(TX - STANDOFF, 2, TZ)), "the west side's axis at the "
                    + "standoff: " + trip.column);
            t.assertTrue(trip.firstGood >= ReconRules.LOOK_FROM && trip.top == Math.min(ReconRules.PILLAR_MAX, trip.firstGood + ReconRules.CLIMB_PAST),
                    "two blocks above its first good look: first " + trip.firstGood + ", top " + trip.top);
            t.assertTrue(r.vantage().equals("pillar " + trip.top), "the vantage: " + r.vantage());
            t.assertTrue(r.optic().isEmpty(), "watched with the eyes (it carries no spyglass): no optic tag: " + r.optic());
            t.assertTrue(dirtIn(h, trip.column) == 0, "the pillar left standing: " + dirtIn(h, trip.column));
            t.assertTrue(dirtCarried(fl.scout()) == ReconRules.BLOCKS, "its 22 dirt back in its backpack: " + dirtCarried(fl.scout()));
            t.assertTrue(FactionStorage.count(h.getLevel(), t.base, s -> s.is(Items.DIRT)) == 30 - ReconRules.BLOCKS,
                    "the dirt it took from the storehouse");
            t.assertTrue(fl.scout().blockPosition().closerThan(h.absolutePos(STATION_STAND), 3), "filed away from the station: "
                    + rel(h, fl.scout().blockPosition()));
            t.succeed();
        });
    }

    // ------------------------------------------------------------------ 10.2 item 2: each face counted once

    /**
     * {@code distinctFacesCountedOnce} (recon.md 10.2 item 2): a soldier the test walks in and out of view every 60 ticks
     * across the watch (by the ring's west wall, out of sight from the pillar, then in the open yard) counts once, not
     * once a look; a soldier shut in a stone box the whole watch is not counted; one standing in view counts once. The
     * report counts 2.
     */
    @GameTest(template = FIELD, timeoutTicks = 4000, batch = "recon_trip_faces", skyAccess = true)
    public static void distinctFacesCountedOnce(GameTestHelper h) {
        Field fl = field(h, 4000, 4, true);
        GameTestBases.Handle t = fl.t();
        FactionNpc walker, standing, boxed;
        try {
            walker = defender(t, 17, 21);
            standing = defender(t, 18, 19);
            boxed = defender(t, 18, 23);
            BlockState stone = Blocks.STONE.defaultBlockState();
            for (int x = 17; x <= 19; x++) {
                for (int z = 22; z <= 24; z++) {
                    for (int y = 2; y <= 4; y++) {
                        if (!(x == 18 && z == 23 && y <= 3)) {
                            BlockPos p = new BlockPos(x, y, z);
                            h.setBlock(p, stone);
                            fl.built().add(p);
                        }
                    }
                }
            }
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        Trip trip = new Trip();
        int[] moves = {0};
        t.onEachTick(() -> {
            follow(fl, trip);
            if (trip.sawWatch && trip.filed == null && fl.job().reconWatch() != null && h.getTick() % 60 == 0) {
                boolean out = moves[0]++ % 2 == 0;
                put(t, walker, out ? 13.5 : 16.5, 21.5); // by the west wall (hidden from the pillar), or in the yard
            }
            if (trip.filed == null) {
                return;
            }
            Intel.Recon r = trip.filed.recon;
            t.assertTrue(moves[0] >= 10, "the walker should have gone in and out of view across the watch: " + moves[0]);
            t.assertTrue(r != null && r.good() && r.soldiers() == 2 && r.faces().contains(walker.getUUID())
                    && r.faces().contains(standing.getUUID()) && !r.faces().contains(boxed.getUUID()),
                    "each face once, the boxed one not at all: " + r);
            t.succeed();
        });
    }

    // ------------------------------------------------------------------ R4b: a watch saved and loaded midway

    /**
     * A trip saved and loaded on the way there and a watch saved and loaded midway (recon.md 15 item 4, 10.3: the
     * accumulator is kept by UUID, never by the runtime entity id). On its way to the target the scout's chunk is saved
     * and loaded (the scout comes back as a new entity from its saved form, its trip with it) and it goes on; midway
     * through the watch it is saved and loaded again, and so is a soldier it has already counted (a new runtime id, the
     * same UUID). The watch goes on from the same pillar and the report counts that soldier once: 2 in all.
     */
    @GameTest(template = FIELD, timeoutTicks = 4000, batch = "recon_trip_reload", skyAccess = true)
    public static void aWatchSavedAndLoadedMidwayCountsEachSoldierOnce(GameTestHelper h) {
        Field fl = field(h, 4000, 4, true);
        GameTestBases.Handle t = fl.t();
        FactionNpc[] seen = new FactionNpc[2];
        try {
            seen[0] = defender(t, 18, 19);
            seen[1] = defender(t, 17, 22);
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        UUID twice = seen[1].getUUID();
        FactionNpc[] scout = {fl.scout()};
        Trip trip = new Trip();
        int[] reloaded = {0, 0};
        t.onEachTick(() -> {
            Field now = new Field(t, fl.f(), fl.target(), fl.order(), scout[0], fl.built());
            follow(now, trip);
            BlockPos at = rel(h, scout[0].blockPosition());
            if (reloaded[1] == 0 && now.job().stateName().equals("OUTBOUND") && now.job().reconPhase().equals("APPROACH") && at.getZ() >= 12) {
                // saved and loaded on its way there (mid-trip): it goes on with the same trip
                FactionNpc s = (FactionNpc) reload(t, unload(scout[0]));
                scout[0] = s;
                ScoutJob job = (ScoutJob) s.job();
                t.assertTrue(job.stateName().equals("OUTBOUND") && job.reconPhase().equals("APPROACH") && dirtCarried(s) == ReconRules.BLOCKS,
                        "the loaded scout should go on to watch: " + job.stateName() + " " + job.reconPhase() + ", " + dirtCarried(s));
                reloaded[1] = 1;
                return;
            }
            var w = now.job().reconWatch();
            if (reloaded[0] == 0 && w != null && w.watchTicks() >= 400) {
                t.assertTrue(w.soldiers().size() == 2, "both soldiers should be counted before the load: " + w.soldiers().size());
                int oldId = seen[1].getId(), height = now.job().reconPillar().height;
                seen[1] = (FactionNpc) reload(t, unload(seen[1]));
                t.assertTrue(seen[1].getId() != oldId && seen[1].getUUID().equals(twice), "a new runtime id, the same face");
                FactionNpc s = (FactionNpc) reload(t, unload(scout[0]));
                scout[0] = s;
                ScoutJob job = (ScoutJob) s.job();
                t.assertTrue(job.reconPhase().equals("PILLAR/WATCH") && job.reconPillar() != null && job.reconPillar().height == height
                                && job.reconWatch() != null && job.reconWatch().soldiers().size() == 2,
                        "the loaded scout should go on watching from its pillar: " + job.reconPhase() + " " + job.reconWatch());
                reloaded[0] = 1;
            }
            if (trip.filed == null) {
                return;
            }
            Intel.Recon r = trip.filed.recon;
            t.assertTrue(reloaded[0] == 1 && reloaded[1] == 1, "the trip was never loaded on the way and midway through the watch");
            t.assertTrue(r != null && r.good() && r.soldiers() == 2 && r.faces().contains(twice),
                    "a soldier seen before and after the load counted once: " + r);
            t.assertTrue(dirtIn(h, trip.column) == 0 && dirtCarried(scout[0]) == ReconRules.BLOCKS, "the pillar taken down after the load");
            t.succeed();
        });
    }

    // ------------------------------------------------------------------ 10.2 item 3: a blind report, delivered

    /**
     * {@code aBlindReconIsNotUsed}, the scout's side (recon.md 10.2 item 3; the council's side is R5's): a ring 14 high,
     * too high for the pillar's cap of 20 from the band. The scout climbs to the cap on the west side, sees none of the
     * yard, takes the pillar down, walks round outside the standoff to the east side, climbs to the cap again, and goes
     * home with a blind report ("could not see inside"), round the base by the north-east corner to the side facing
     * home first, never inside the standoff's square. At the station its order learns of a blind recon; the faction's
     * report keeps no recon, counts one failure at the blind look's time and both sides tried, and the target backs off.
     */
    @GameTest(template = FIELD, timeoutTicks = 6000, batch = "recon_trip_blind", skyAccess = true)
    public static void aBlindReconIsDeliveredAndBacksTheTargetOff(GameTestHelper h) {
        Field fl = field(h, 6000, 14, true);
        GameTestBases.Handle t = fl.t();
        Trip trip = new Trip();
        Set<BlockPos> columns = new HashSet<>();
        int[] tops = {0, 0}, nearest = {Integer.MAX_VALUE};
        t.onEachTick(() -> {
            follow(fl, trip);
            nearest[0] = Math.min(nearest[0], wayOut(fl));
            Scaffold.Pillar p = fl.job().reconPillar();
            if (p != null && p.height > 0) {
                BlockPos c = rel(h, p.base);
                columns.add(c);
                int i = c.getX() < TX ? 0 : 1;
                tops[i] = Math.max(tops[i], p.height);
            }
            if (trip.filed == null) {
                return;
            }
            Report kept = trip.filed;
            long now = h.getLevel().getGameTime();
            t.assertTrue(trip.reported == ReconRules.ScoutReport.BLIND, "its order should learn of a blind recon: " + trip.reported);
            t.assertTrue(kept.recon == null && kept.reconFailures == 1 && kept.reconFailedAt > 0 && kept.reconFailedAt <= now
                            && kept.reconSidesTried == (Side.WEST.bit() | Side.EAST.bit()),
                    "no recon kept, one failure at the blind look's time, both sides tried: recon " + kept.recon + ", failures "
                            + kept.reconFailures + " at " + kept.reconFailedAt + ", sides " + kept.reconSidesTried);
            t.assertTrue(ReconRules.backingOff(kept.reconFile(), now), "the target should back off");
            t.assertTrue(tops[0] == ReconRules.PILLAR_MAX && tops[1] == ReconRules.PILLAR_MAX, "to the cap on both sides: " + tops[0]
                    + ", " + tops[1]);
            t.assertTrue(columns.size() == 2, "one column a side: " + columns);
            for (BlockPos c : columns) {
                t.assertTrue(dirtIn(h, c) == 0, "a pillar left standing at " + c);
            }
            t.assertTrue(dirtCarried(fl.scout()) == ReconRules.BLOCKS, "its dirt back: " + dirtCarried(fl.scout()));
            t.assertTrue(nearest[0] >= STANDOFF - 3, "home round the base by its corner, outside the standoff: nearest " + nearest[0]);
            t.succeed();
        });
    }

    /**
     * How near the target's centre (Chebyshev, relative) the scout stands while it is done at the target and on its way
     * home with what it saw, before it reaches the station; {@link Integer#MAX_VALUE} at any other time.
     */
    static int wayOut(Field fl) {
        if (!fl.job().reconPhase().startsWith("DONE")) {
            return Integer.MAX_VALUE;
        }
        BlockPos at = rel(fl.t().helper, fl.scout().blockPosition());
        return Math.max(Math.abs(at.getX() - TX), Math.abs(at.getZ() - TZ));
    }

    // ------------------------------------------------------------------ 10.2 item 8: a dead scout's report is lost

    /**
     * {@code aDeadScoutsReportIsLost} (recon.md 10.2 item 8): the recon scout, its good report in its pack, is killed on
     * the way home, out of its home area with nobody of ours in sight. Nothing is delivered: the faction's report has no
     * recon and the order is still its, untold. Its roll call record expected it back by its recon trip's own plan
     * ({@code Whereabouts.reconPlan}: the walk there, the vantage allowance, the watch and the walk home, from its
     * departure). Past that and the grace the roll call reports it missing; the base wants a follow-up scout for it; the
     * next council pass ends the order as lost and the target backs off.
     */
    @GameTest(template = FIELD, timeoutTicks = 4000, batch = "recon_trip_dead", skyAccess = true)
    public static void aDeadScoutsReportIsLost(GameTestHelper h) {
        Field fl = field(h, 4000, 4, true);
        GameTestBases.Handle t = fl.t();
        try {
            defender(t, 17, 21);
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        Trip trip = new Trip();
        t.onEachTick(() -> {
            follow(fl, trip);
            ScoutJob job = fl.job();
            FactionNpc n = fl.scout();
            if (!n.isAlive() || !job.reconPhase().startsWith("DONE")) {
                return; // (on its way home: round the base's corner first, then to the station)
            }
            BlockPos at = rel(h, n.blockPosition());
            if (Math.max(Math.abs(at.getX() - HX), Math.abs(at.getZ() - HZ)) < 9) {
                return; // (out of the home area and its edge band first)
            }
            Report carried = job.reconReport();
            t.assertTrue(carried != null && carried.recon != null && carried.recon.good(), "it should carry a good recon home: " + carried);
            ServerLevel level = h.getLevel();
            NpcRecord rec = t.data.record(n.getUUID());
            double dist = Math.sqrt(t.base.center.distSqr(fl.target().center));
            t.assertTrue(rec.where == Whereabouts.State.AWAY && rec.departedAt > 0
                    && rec.expectedBy == rec.departedAt + Whereabouts.reconPlanTicks(dist), "expected back by its recon trip's plan: "
                    + rec.where + ", left " + rec.departedAt + ", expected " + rec.expectedBy + " (plan " + Whereabouts.reconPlanTicks(dist) + ")");
            n.invulnerableTime = 0;
            n.hurt(n.damageSources().genericKill(), Float.MAX_VALUE);
            t.assertTrue(!n.isAlive(), "the scout should be dead");
            Report kept = fl.kept();
            t.assertTrue(kept != null && kept.recon == null, "nothing delivered: " + (kept == null ? null : kept.recon));
            t.assertTrue(fl.order().taken() && fl.order().reported == ReconRules.ScoutReport.NONE, "the order is still its, untold");
            t.assertTrue(rec.where == Whereabouts.State.AWAY, "nobody saw it die: " + rec.where);
            long late = rec.expectedBy + FCConfig.MISSING_GRACE_TICKS.get() + 1;
            t.assertTrue(FactionDirector.needs(level, t.data, t.base, late).stream()
                    .noneMatch(x -> x.reason().startsWith("follow-up")), "no follow-up while it is only away");
            RollCall.upkeep(level, t.data, t.base, late);
            t.assertTrue(rec.where == Whereabouts.State.MISSING, "missing past its plan and the grace: " + rec.where);
            t.assertTrue(FactionDirector.needs(level, t.data, t.base, late).stream()
                    .anyMatch(x -> x.role() == NpcRole.SCOUT && x.reason().equals("follow-up for missing scout " + rec.name)),
                    "a follow-up scout for the missing one: " + FactionDirector.needs(level, t.data, t.base, late));
            ReconOrders.settle(fl.f(), late);
            t.assertTrue(!fl.f().reconOrders.contains(fl.order()), "the order should lapse");
            t.assertTrue(kept.reconFailures == 1 && kept.reconFailedAt == late && ReconRules.backingOff(kept.reconFile(), late),
                    "a lost scout is a failed attempt: failures " + kept.reconFailures + " at " + kept.reconFailedAt);
            t.succeed();
        });
    }

    // ------------------------------------------------------------------ 10.2 item 9: the order is taken at the station

    /**
     * {@code theOrderIsTakenAtTheStation} (recon.md 10.2 item 9): an order placed while the only scout is out exploring
     * is not taken out there; the scout takes it only when it reports at the station, and then sets out to watch from
     * the storehouse with its dirt.
     */
    @GameTest(template = FIELD, timeoutTicks = 1200, batch = "recon_trip_station", skyAccess = true)
    public static void theOrderIsTakenAtTheStation(GameTestHelper h) {
        Field fl = field(h, 1200, 0, true);
        GameTestBases.Handle t = fl.t();
        ScoutJob job = fl.job();
        try {
            CompoundTag tag = new CompoundTag();
            tag.putString("state", "OUTBOUND");
            tag.putLong("target", h.absolutePos(new BlockPos(28, 2, 28)).asLong());
            tag.putLong("departed", h.getLevel().getGameTime());
            job.load(tag, h.getLevel().registryAccess());
            put(t, fl.scout(), 20, 10);
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        int[] step = {0};
        long[] takenAt = {0};
        t.onEachTick(() -> {
            ReconOrder o = fl.order();
            if (step[0] == 0) {
                t.assertTrue(!o.taken() && job.reconPhase().equals("none"), "taken away from home: " + o);
                if (h.getTick() >= 80) {
                    CompoundTag tag = new CompoundTag();
                    tag.putString("state", "RETURN");
                    job.load(tag, h.getLevel().registryAccess()); // its expedition over, it walks home to report
                    step[0] = 1;
                }
                return;
            }
            if (!o.taken()) {
                t.assertTrue(!job.stateName().equals("HOME"), "home without the order");
                return;
            }
            if (takenAt[0] == 0) {
                takenAt[0] = o.takenAt;
                t.assertTrue(o.scout.equals(fl.scout().getUUID()) && o.takenAt == h.getLevel().getGameTime(), "taken by its scout, now");
                t.assertTrue(fl.scout().blockPosition().closerThan(h.absolutePos(STATION_STAND), 3), "taken away from the station: "
                        + rel(h, fl.scout().blockPosition()));
                t.assertTrue(job.reconPhase().equals("PREPARE"), "about to set out: " + job.reconPhase());
                return;
            }
            if (job.stateName().equals("OUTBOUND")) {
                t.assertTrue(job.reconPhase().equals("APPROACH") && dirtCarried(fl.scout()) == ReconRules.BLOCKS,
                        "out to watch with its dirt: " + job.reconPhase() + ", " + dirtCarried(fl.scout()));
                t.succeed();
            }
        });
    }

    // ------------------------------------------------------------------ 10.2 item 11: a patrol, and a watch cut short

    /**
     * {@code aPatrolDoesNotBringItDown} (recon.md 10.2 item 11): an enemy soldier walks to and fro by the pillar's foot,
     * within 16 blocks and in sight, from the middle of the watch until 300 ticks after it. The scout stays on top and
     * keeps watching; after the watch it holds still while the soldier is near, and comes down only once it has gone;
     * its report is good. (This scout carries its 22 dirt from home: it keeps them when it unloads at the storehouse,
     * which has none to give.)
     */
    @GameTest(template = FIELD, timeoutTicks = 4500, batch = "recon_trip_patrol", skyAccess = true)
    public static void aPatrolDoesNotBringItDown(GameTestHelper h) {
        Field fl = field(h, 4500, 4, true, true);
        GameTestBases.Handle t = fl.t();
        FactionNpc patrol;
        try {
            defender(t, 17, 21);
            patrol = defender(t, 28, 2); // far off to begin with
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        Trip trip = new Trip();
        long[] gone = {-1}, filedAt = {-1};
        int[] held = {0};
        t.onEachTick(() -> {
            follow(fl, trip);
            long now = h.getLevel().getGameTime();
            ScoutJob job = fl.job();
            Scaffold.Pillar p = job.reconPillar();
            if (trip.sawWatch && gone[0] < 0) {
                long into = now - trip.watchFrom;
                if (into >= 600 && into < ReconRules.WATCH_TICKS + 300) {
                    put(t, patrol, 3.5, (into / 40) % 2 == 0 ? 15.5 : 27.5); // to and fro along the pillar's line, 6 out
                    t.assertTrue(p != null && p.height == trip.top && !job.reconPhase().endsWith("DOWN"),
                            "it came down for a passing soldier: " + job.reconPhase() + " at " + (p == null ? 0 : p.height));
                    if (into > ReconRules.WATCH_TICKS) {
                        held[0]++;
                    }
                } else if (into >= ReconRules.WATCH_TICKS + 300) {
                    put(t, patrol, 28.5, 2.5); // gone
                    gone[0] = now;
                }
            }
            if (trip.filed == null) {
                return;
            }
            if (filedAt[0] < 0) {
                filedAt[0] = now;
                t.assertTrue(gone[0] > 0 && held[0] >= 250, "it should have held still after the watch while the soldier was near: " + held[0]);
                t.assertTrue(trip.filed.recon != null && trip.filed.recon.good(), "the watch is good: " + trip.filed.recon);
                t.assertTrue(dirtIn(h, trip.column) == 0, "the pillar left standing");
            }
            if (job.unloadedAt() <= filedAt[0]) {
                return; // (home again: it unloads at the storehouse before it sets out exploring)
            }
            t.assertTrue(FactionStorage.count(h.getLevel(), t.base, s -> s.is(Items.DIRT)) == 0 && dirtCarried(fl.scout()) == ReconRules.BLOCKS,
                    "its dirt kept through the unloading: " + dirtCarried(fl.scout()) + " carried, "
                            + FactionStorage.count(h.getLevel(), t.base, s -> s.is(Items.DIRT)) + " stored");
            t.succeed();
        });
    }

    /**
     * {@code aWatchCutShort} (recon.md 10.2 item 11): the test hurts the scout {@code hitAt} ticks into its watch. It comes
     * down at once, block by block, and retreats home; its report is good only past 600 ticks of watching.
     */
    static void watchCutShort(GameTestHelper h, long hitAt, boolean good) {
        watchCutShort(h, hitAt, good, false);
    }

    /** As {@link #watchCutShort(GameTestHelper, long, boolean)}; {@code breakBlock}: a block of its pillar is broken instead of a hit. */
    static void watchCutShort(GameTestHelper h, long hitAt, boolean good, boolean breakBlock) {
        Field fl = field(h, 4000, 4, true);
        GameTestBases.Handle t = fl.t();
        try {
            defender(t, 17, 21);
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        Trip trip = new Trip();
        long[] hit = {-1};
        int[] heightAtHit = {0};
        boolean[] retreated = {false};
        t.onEachTick(() -> {
            follow(fl, trip);
            long now = h.getLevel().getGameTime();
            ScoutJob job = fl.job();
            if (trip.sawWatch && hit[0] < 0 && now - trip.watchFrom >= hitAt) {
                heightAtHit[0] = job.reconPillar().height;
                if (breakBlock) {
                    h.getLevel().destroyBlock(job.reconPillar().base.above(3), false); // someone breaks a block of its pillar
                } else {
                    fl.scout().invulnerableTime = 0;
                    fl.scout().hurt(fl.scout().damageSources().generic(), 1.0F);
                }
                hit[0] = now;
            }
            if (hit[0] > 0 && now == hit[0] + 2) {
                t.assertTrue(job.reconPhase().equals("PILLAR/DOWN"), "a hit should bring it down at once: " + job.reconPhase());
            }
            retreated[0] |= job.isRetreating();
            if (trip.filed == null) {
                return;
            }
            Intel.Recon r = trip.filed.recon;
            t.assertTrue(heightAtHit[0] == trip.top, "hit on top of its pillar");
            t.assertTrue(retreated[0], "it should have retreated home");
            if (good) {
                t.assertTrue(trip.reported == ReconRules.ScoutReport.GOOD && r != null && r.good() && r.watchTicks() >= ReconRules.MIN_WATCH_TICKS
                        && r.watchTicks() < ReconRules.WATCH_TICKS, "a watch cut short past 600 ticks is good: " + r);
            } else {
                t.assertTrue(trip.reported == ReconRules.ScoutReport.BLIND && r == null && trip.filed.reconFailures == 1,
                        "a watch cut short before 600 ticks is blind: " + trip.reported + ", " + r);
            }
            t.assertTrue(dirtIn(h, trip.column) == 0 && dirtCarried(fl.scout()) == ReconRules.BLOCKS - (breakBlock ? 1 : 0),
                    "the pillar taken down (all but the block broken under it back in the pack): " + dirtCarried(fl.scout()));
            t.succeed();
        });
    }

    /**
     * {@code aWatchCutShort} with a block of its pillar broken under it 700 ticks into the watch, as a passing soldier
     * that attacked the pillar would: it comes down at once (a block from its pack goes back into the gap on the way, so
     * it never drops more than one), retreats home, and the 700 ticks watched make a good report.
     */
    @GameTest(template = FIELD, timeoutTicks = 4000, batch = "recon_trip_cut_broken", skyAccess = true)
    public static void aBrokenPillarBlockBringsItDown(GameTestHelper h) {
        watchCutShort(h, 700, true, true);
    }

    @GameTest(template = FIELD, timeoutTicks = 4000, batch = "recon_trip_cut_early", skyAccess = true)
    public static void aWatchCutShortBeforeHalfAMinuteIsBlind(GameTestHelper h) {
        watchCutShort(h, 300, false);
    }

    @GameTest(template = FIELD, timeoutTicks = 4000, batch = "recon_trip_cut_late", skyAccess = true)
    public static void aWatchCutShortPastHalfAMinuteIsGood(GameTestHelper h) {
        watchCutShort(h, 700, true);
    }

    // ------------------------------------------------------------------ 10.2 item 12: a lair, watched from the ground

    /**
     * {@code lairRecon} (recon.md 10.2 item 12, 3.7): a lair test base at (16, 2, 18) with 4-high walls of half 3 and a
     * 3-wide gap in the middle of each, its band scaled to 15 to 17 blocks on the gap axes; three zombies inside. The
     * scout takes no dirt (a lair is watched from the ground), walks to the north gap's axis (the side facing home), builds
     * no pillar, watches through the gap and counts the 3 monsters. It carries a spyglass, which stays in its backpack (a
     * lair is watched with the eyes, its weapon in hand: task R12), so the report has no optic tag.
     */
    @GameTest(template = FIELD, timeoutTicks = 4000, batch = "recon_trip_lair", skyAccess = true)
    public static void lairRecon(GameTestHelper h) {
        Field fl = field(h, 4000, 0, false);
        GameTestBases.Handle t = fl.t();
        FactionNpc n;
        ReconOrder order;
        Set<UUID> monsters = new HashSet<>();
        try {
            fl.f().reconOrders.clear(); // the lair's order instead of the settlement's
            t.data.bases.remove(fl.target().id);
            BaseState lair = new BaseState();
            lair.faction = FactionType.HOLLOW;
            lair.kind = BaseState.Kind.LAIR;
            lair.center = h.absolutePos(new BlockPos(16, 2, 18));
            lair.name = "Recon test lair";
            lair.testHalf = 3;
            lair.testRadius = 2;
            lair.testStandoff = 15;
            lair.testSight = 17;
            t.data.addBase(lair);
            t.onCleanup(() -> t.data.bases.remove(lair.id));
            BlockState stone = Blocks.STONE.defaultBlockState();
            for (int x = 13; x <= 19; x++) {
                for (int z = 15; z <= 21; z++) {
                    boolean wall = x == 13 || x == 19 || z == 15 || z == 21;
                    boolean gap = (x == 13 || x == 19) && Math.abs(z - 18) <= 1 || (z == 15 || z == 21) && Math.abs(x - 16) <= 1;
                    if (wall && !gap) {
                        for (int y = 2; y <= 5; y++) {
                            BlockPos p = new BlockPos(x, y, z);
                            h.setBlock(p, stone);
                            fl.built().add(p);
                        }
                    }
                }
            }
            for (int[] at : new int[][]{{15, 18}, {16, 19}, {17, 17}}) {
                Zombie z = t.track(h.spawn(EntityType.ZOMBIE, new BlockPos(at[0], 2, at[1])));
                z.setNoAi(true);
                z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                monsters.add(z.getUUID());
            }
            long now = h.getLevel().getGameTime();
            KnowledgeGameTests.deliver(t, fl.f(), KnowledgeGameTests.delivered(Report.Kind.LAIR, lair.center, FactionType.HOLLOW, now, null, 0), now);
            order = new ReconOrder(lair.center, FactionType.HOLLOW, Report.Kind.LAIR, t.base.id, now, false);
            fl.f().reconOrders.add(order);
            n = t.npc(5, 7, NpcRole.SCOUT);
            n.equip(new ItemStack(Items.IRON_SWORD));
            n.inventory().addItem(new ItemStack(Items.SPYGLASS)); // carried, but never taken out at a lair (task R12)
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        boolean[] watched = {false};
        t.onEachTick(() -> {
            ScoutJob job = (ScoutJob) n.job();
            Scaffold.Pillar p = job.reconPillar();
            t.assertTrue(p == null || p.height == 0, "a pillar at a lair: " + (p == null ? 0 : p.height));
            t.assertTrue(!n.getMainHandItem().is(Items.SPYGLASS), "a spyglass in hand at a lair (watched from the ground, its weapon in hand)");
            if (job.reconWatch() != null && !watched[0]) {
                watched[0] = true;
                BlockPos at = rel(h, n.blockPosition());
                t.assertTrue(at.getX() == 16 && at.getZ() == 3, "watching from the north gap's axis at the standoff: " + at);
                t.assertTrue(dirtCarried(n) == 0, "no dirt taken for a lair: " + dirtCarried(n));
            }
            Report kept = ReconOrders.report(fl.f(), order);
            if (kept == null || kept.recon == null) {
                return;
            }
            Intel.Recon r = kept.recon;
            t.assertTrue(watched[0], "never watched");
            t.assertTrue(r.good() && r.soldiers() == 3 && r.faces().equals(monsters) && r.vantage().equals("ground"),
                    "the lair's 3 monsters counted from the ground: " + r);
            t.assertTrue(r.optic().isEmpty(), "a lair is watched with the eyes, spyglass or not: " + r.optic());
            t.succeed();
        });
    }

    // ------------------------------------------------------------------ recon.md 3.5 step 1: dirt dug at home

    /**
     * A scout short of dirt digs it (recon.md 3.5, step 1): the storehouse has none and it carries none, so before it sets
     * out it scrapes the grass off open ground in its own base's home area (here 10 blocks: a patch of grass laid into the
     * field's floor), under the woodcutter's rules ({@code DirtDig}: outside every base's walls box plus 4), by hand at
     * player speed; then it sets out with the dirt it dug, every block it took one cell of the patch.
     */
    @GameTest(template = FIELD, timeoutTicks = 3000, batch = "recon_trip_dig", skyAccess = true)
    public static void aScoutShortOfDirtDigsItAtHome(GameTestHelper h) {
        Field fl = field(h, 3000, 0, true, true);
        GameTestBases.Handle t = fl.t();
        List<BlockPos> patch = new ArrayList<>();
        try {
            fl.scout().inventory().setItem(0, ItemStack.EMPTY); // no dirt carried, none stored
            // the target's record out of the field: a GameTest base with no bounds holds 32 blocks round its centre as its
            // own land, where no other faction digs (WorkSafety.mayDig); the order still names it
            t.data.bases.remove(fl.target().id);
            t.base.testRadius = 10; // a home area of 10 blocks, room for open ground outside the walls box and its margin
            for (int x = 0; x <= 7; x++) {
                for (int z = 12; z <= 16; z++) {
                    BlockPos floor = new BlockPos(x, 1, z), under = floor.below();
                    BlockState was = h.getBlockState(under);
                    h.setBlock(under, Blocks.STONE); // a firm floor under the grass
                    h.setBlock(floor, Blocks.GRASS_BLOCK);
                    patch.add(floor);
                    t.onCleanup(() -> {
                        h.setBlock(floor, Blocks.SMOOTH_STONE);
                        h.setBlock(under, was);
                    });
                }
            }
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        boolean[] dug = {false};
        t.onEachTick(() -> {
            ScoutJob job = fl.job();
            dug[0] |= job.stateName().equals("DIG");
            if (!job.stateName().equals("OUTBOUND")) {
                return;
            }
            int taken = 0;
            for (BlockPos c : patch) {
                if (!h.getBlockState(c).is(Blocks.GRASS_BLOCK)) {
                    taken++;
                    double dx = c.getX() - HX, dz = c.getZ() - HZ;
                    t.assertTrue(dx * dx + dz * dz <= 10 * 10 && Math.max(Math.abs(dx), Math.abs(dz)) > 1 + 4,
                            "dug outside the home area or inside the walls' margin: " + c);
                }
            }
            t.assertTrue(dug[0], "it set out without digging");
            t.assertTrue(job.reconPhase().equals("APPROACH"), "out to watch: " + job.reconPhase());
            t.assertTrue(taken > 0 && dirtCarried(fl.scout()) == taken, "a block of dirt for each cell it dug: " + taken + " dug, "
                    + dirtCarried(fl.scout()) + " carried");
            t.succeed();
        });
    }

    // ------------------------------------------------------------------ recon.md 2.4: the second scout

    /**
     * A second scout for watching (recon.md 2.4; the owner agreed on 2026-11-25): an order of the base no scout took for
     * {@code RECON_WAIT} (6000 ticks) asks for "a scout to watch the Grove Covenant settlement" while the base has one
     * scout, not a tick before, not once a scout took it, and not with two scouts; with none, "no scout left" asks instead.
     * A follow-up scout is wanted only when the base's only scout is missing, not while it is away.
     */
    @GameTest(template = FIELD, timeoutTicks = 40, batch = "workers_static")
    public static void aSecondScoutIsWantedForAnOrderNoScoutTook(GameTestHelper h) {
        Field fl = field(h, 40, 0, false);
        GameTestBases.Handle t = fl.t();
        t.guard(() -> {
            ServerLevel level = h.getLevel();
            ReconOrder o = fl.order();
            String want = "a scout to watch the Grove Covenant settlement";
            long due = o.orderedAt + ReconRules.WAIT_TICKS;
            t.assertTrue(FactionDirector.needs(level, t.data, t.base, due).stream().noneMatch(x -> x.reason().equals(want))
                    && FactionDirector.needs(level, t.data, t.base, due).stream().anyMatch(x -> x.reason().equals("no scout left")),
                    "with no scout, the base wants one for the order as for any: " + FactionDirector.needs(level, t.data, t.base, due));
            UUID first = t.absentWorker(NpcRole.SCOUT);
            t.assertTrue(FactionDirector.needs(level, t.data, t.base, due - 1).stream().noneMatch(x -> x.reason().equals(want)),
                    "a tick before the wait is over");
            t.assertTrue(FactionDirector.needs(level, t.data, t.base, due).stream().anyMatch(x -> x.role() == NpcRole.SCOUT
                    && x.reason().equals(want)), "the order untaken for the wait: " + FactionDirector.needs(level, t.data, t.base, due));
            o.scout = first;
            o.takenAt = due;
            t.assertTrue(FactionDirector.needs(level, t.data, t.base, due + 1).stream().noneMatch(x -> x.reason().equals(want)),
                    "the order taken");
            o.scout = null;
            o.takenAt = 0;
            t.absentWorker(NpcRole.SCOUT);
            t.assertTrue(FactionDirector.needs(level, t.data, t.base, due).stream().noneMatch(x -> x.reason().equals(want)),
                    "two scouts already");
            // the follow-up: only for the base's only scout, and only once it is missing
            NpcRecord r1 = t.data.record(first);
            t.data.faction(SIDE).roster.values().removeIf(r -> r.role == NpcRole.SCOUT && r.baseId == t.base.id && !r.uuid.equals(first));
            r1.where(Whereabouts.State.AWAY, due);
            t.assertTrue(FactionDirector.needs(level, t.data, t.base, due).stream().noneMatch(x -> x.reason().startsWith("follow-up")),
                    "no follow-up for a scout only away");
            r1.where(Whereabouts.State.MISSING, due);
            t.assertTrue(FactionDirector.needs(level, t.data, t.base, due).stream()
                    .anyMatch(x -> x.reason().equals("follow-up for missing scout " + r1.name)), "a follow-up for the missing scout");
            t.succeed();
        }).run();
    }
}
