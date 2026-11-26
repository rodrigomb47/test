package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseWatch;
import com.factionclash.campaign.Report;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.campaign.Intel;
import com.factionclash.logic.campaign.ReconOptic;
import com.factionclash.logic.campaign.ReconRules;
import com.factionclash.logic.campaign.ReconRules.Side;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import com.factionclash.storage.FactionStorage;
import com.factionclash.work.BlacksmithJob;
import com.factionclash.work.Crafting;
import com.factionclash.work.Inv;
import com.factionclash.work.ReconOptics;
import com.factionclash.work.ReconVantage;
import com.factionclash.work.ReconVantage.Vantage;
import com.factionclash.work.ScoutJob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The recon scout's spyglass ({@code docs/design/recon.md} 5.6, task R12; the owner's answer of 2026-11-26): the vanilla
 * spyglass, never behind a tech level, lets a scout's focused look reach 96 blocks instead of 72, so its vantage band runs
 * 61 to 96. In a test field the real band does not fit: a GameTest base carries a scaled reach for the eyes
 * ({@code testSight}), and a spyglass scales it by the same 96 / 72. These tests check the column search and the samples'
 * reach with and without one, a real trip that watches through one from a hill only it reaches (held in hand while it
 * watches, the report tagged), the keep rule and the tag's save, and the blacksmith's vanilla recipe. A branch miner
 * taking the shards from an amethyst cluster is {@code FCGameTests.aMinerTakesTheAmethystClusterItsTunnelUncovers}.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class ReconOpticGameTests {
    private ReconOpticGameTests() {
    }

    private static final String FIELD = HeroMobilityGameTests.FIELD;
    private static final FactionType SIDE = FactionType.TIDE_ALLIANCE, ENEMY = FactionType.GROVE_COVENANT;

    private static void put(GameTestBases.Handle t, List<BlockPos> built, int x, int y, int z, BlockState s) {
        BlockPos p = new BlockPos(x, y, z);
        t.helper.setBlock(p, s);
        built.add(p);
    }

    private static boolean has(GameTestHelper h, List<Vantage> v, int x, int z) {
        BlockPos o = h.absolutePos(new BlockPos(x, 0, z));
        return v.stream().anyMatch(c -> c.foot().getX() == o.getX() && c.foot().getZ() == o.getZ());
    }

    private static String rel(GameTestHelper h, BlockPos abs) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        return (abs.getX() - o.getX()) + "," + (abs.getY() - o.getY()) + "," + (abs.getZ() - o.getZ());
    }

    private static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

    /** A hill 2 high (x0..x1, z0..z1) with a 1-high skirt round it to walk up. */
    private static void hill(GameTestBases.Handle t, List<BlockPos> built, int x0, int x1, int z0, int z1) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean top = x >= x0 && x <= x1 && z >= z0 && z <= z1;
                put(t, built, x, 2, z, stone);
                if (top) {
                    put(t, built, x, 3, z, stone);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the column search and the samples' reach

    /**
     * {@code aSpyglassFindsTheHillBeyondTheEyesReach} (recon.md 11, R12): the enemy settlement at relative (10, 18) with
     * the eyes' band scaled to 10 to 13 blocks; through a spyglass the reach is scaled by 96 / 72 to 17. On the flat
     * field the spyglass search keeps every column of the eyes' band first, in their own order (the axis at the
     * standoff), the deeper ones after. A hill 2 high on the east axis 15 to 16 blocks out, beyond the eyes' 13, is no
     * column to the eyes and one to the spyglass, after the eyes' columns; once a canopy of leaves covers the whole of
     * the eyes' band (no open sky anywhere in it), the eyes find no vantage on that side and the spyglass takes the
     * hill's near edge on the axis. The samples' reach is the same reach: 15 blocks from the centre a sample with the
     * eyes is none and one through a spyglass is taken; at 18 neither is.
     */
    @GameTest(template = FIELD, timeoutTicks = 60, batch = "workers_static", skyAccess = true)
    public static void aSpyglassFindsTheHillBeyondTheEyesReach(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 2, 60);
        List<BlockPos> built = new ArrayList<>();
        t.onCleanup(() -> built.forEach(p -> h.setBlock(p, Blocks.AIR)));
        t.guard(() -> {
            ServerLevel level = h.getLevel();
            int tx = 10, tz = 18;
            BaseState target = KnowledgeGameTests.enemyBase(t, ENEMY, h.absolutePos(new BlockPos(tx, 2, tz)));
            target.testHalf = 2;
            target.testStandoff = 10;
            target.testSight = 13;
            ReconRules.Band eyes = ReconVantage.band(target), glass = ReconVantage.band(target, ReconOptic.SPYGLASS);
            t.assertTrue(eyes.equals(new ReconRules.Band(10, 13, ReconRules.LATERAL)), "the eyes' scaled band: " + eyes);
            t.assertTrue(glass.equals(new ReconRules.Band(10, 17, ReconRules.LATERAL)), "the spyglass's: 13 x 96 / 72: " + glass);

            // the flat field: the eyes' columns first, in their own order, then the deeper ones
            List<Vantage> flatEyes = ReconVantage.columns(level, t.data, target, Side.EAST);
            List<Vantage> flatGlass = ReconVantage.columns(level, t.data, target, Side.EAST, ReconOptic.SPYGLASS);
            t.assertTrue(!flatEyes.isEmpty() && flatGlass.size() > flatEyes.size(), "the spyglass's band is deeper: "
                    + flatEyes.size() + " and " + flatGlass.size());
            for (int i = 0; i < flatEyes.size(); i++) {
                t.assertTrue(flatEyes.get(i).foot().equals(flatGlass.get(i).foot()), "the eyes' columns first, in their own order: "
                        + i + ": " + rel(h, flatEyes.get(i).foot()) + " and " + rel(h, flatGlass.get(i).foot()));
            }
            for (Vantage v : flatGlass) {
                int dx = v.column().dx(), dz = v.column().dz();
                t.assertTrue(dx * dx + dz * dz <= 17 * 17 && Math.max(Math.abs(dx), Math.abs(dz)) >= 10, "inside the spyglass's band: " + v);
            }
            t.assertTrue(flatEyes.stream().allMatch(v -> v.column().dx() <= 13), "the eyes reach no farther than 13");

            // a hill on the east axis 15 to 16 out, beyond the eyes' reach
            hill(t, built, tx + 15, tx + 16, tz - 1, tz + 1);
            List<Vantage> hillEyes = ReconVantage.columns(level, t.data, target, Side.EAST);
            List<Vantage> hillGlass = ReconVantage.columns(level, t.data, target, Side.EAST, ReconOptic.SPYGLASS);
            t.assertTrue(!has(h, hillEyes, tx + 15, tz) && !has(h, hillEyes, tx + 16, tz), "the hill is beyond the eyes' reach");
            t.assertTrue(rel(h, hillEyes.get(0).foot()).equals((tx + 10) + ",2," + tz), "the eyes: the axis at the standoff: "
                    + rel(h, hillEyes.get(0).foot()));
            int at = -1;
            for (int i = 0; i < hillGlass.size(); i++) {
                if (rel(h, hillGlass.get(i).foot()).equals((tx + 15) + ",4," + tz)) {
                    at = i;
                }
            }
            t.assertTrue(at >= hillEyes.size() && hillGlass.get(at).rise() == 2, "the spyglass finds the hill, after the eyes' "
                    + "columns: " + at + " of " + hillGlass.size());
            t.assertTrue(rel(h, hillGlass.get(0).foot()).equals((tx + 10) + ",2," + tz), "the eyes' best stays the spyglass's best: "
                    + rel(h, hillGlass.get(0).foot()));

            // a canopy over the whole of the eyes' band on that side (4 above the ground): no open sky in it
            for (int x = tx + 10; x <= tx + 13; x++) {
                for (int z = tz - 8; z <= tz + 8; z++) {
                    put(t, built, x, 6, z, LEAVES);
                }
            }
            List<Vantage> blindEyes = ReconVantage.columns(level, t.data, target, Side.EAST);
            List<Vantage> blindGlass = ReconVantage.columns(level, t.data, target, Side.EAST, ReconOptic.SPYGLASS);
            t.assertTrue(blindEyes.isEmpty(), "under the canopy the eyes have no vantage: " + blindEyes.size());
            t.assertTrue(!blindGlass.isEmpty() && rel(h, blindGlass.get(0).foot()).equals((tx + 15) + ",4," + tz),
                    "the spyglass takes the hill's near edge on the axis: " + (blindGlass.isEmpty() ? "none" : rel(h, blindGlass.get(0).foot())));

            // the samples' reach: the same scaled reach
            FactionNpc n = t.track(h.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(tx, 2, tz - 15)));
            n.faction(SIDE);
            n.role(NpcRole.SCOUT);
            n.setNoAi(true);
            Vec3 at15 = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(tx, 2, tz - 15)));
            n.moveTo(at15.x, at15.y, at15.z, 0F, 0F);
            long now = level.getGameTime();
            t.assertTrue(BaseWatch.reconSample(level, n, target, now) == null, "15 out the eyes' sample should be none");
            t.assertTrue(BaseWatch.reconSample(level, n, target, now, ReconOptic.SPYGLASS) != null, "15 out a spyglass's sample is taken");
            Vec3 at18 = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(tx, 2, tz - 18)));
            n.moveTo(at18.x, at18.y, at18.z, 0F, 0F);
            t.assertTrue(BaseWatch.reconSample(level, n, target, now, ReconOptic.SPYGLASS) == null, "18 out: beyond the spyglass's 17");
            t.assertTrue(BaseWatch.reconSight(target, ReconOptic.SPYGLASS) == 17 && BaseWatch.reconSight(target, ReconOptic.NONE) == 13,
                    "the scaled reach");
            // a real settlement: 72 and 96
            target.testSight = -1;
            t.assertTrue(BaseWatch.reconSight(target, ReconOptic.NONE) == 72 && BaseWatch.reconSight(target, ReconOptic.SPYGLASS) == 96,
                    "the real reach: 72 and 96");
            t.succeed();
        }).run();
    }

    // ------------------------------------------------------------------ the trip: a spyglass in hand

    /**
     * {@code aScoutWithASpyglassWatchesFromBeyondTheEyesReach} (recon.md 11, R12, the carry rule): the trip field of
     * {@code ReconTripGameTests} (the target's band scaled to 13 to 16, 21 through a spyglass), its west and east sides
     * already tried (so the trip goes north), a canopy of leaves over the whole of the eyes' north band and a hill on the
     * north axis 18 to 19 blocks out, beyond the eyes' 16. The eyes find no vantage there; a spyglass the blacksmith left
     * in the storehouse is taken with the dirt; the scout climbs on the hill, holds the spyglass in its hand all through
     * the watch (its sword in its backpack meanwhile), puts it away as it starts down, and the faction's report counts
     * the 3 soldiers, "pillar N", watched through a spyglass. Midway through the watch the scout is saved and loaded (a
     * chunk unload): it comes back watching with the spyglass still in hand, and the report keeps the tag; after the load
     * its sword is found in its backpack and comes back into its hand as it starts down.
     */
    @GameTest(template = FIELD, timeoutTicks = 6000, batch = "recon_spyglass_trip", skyAccess = true)
    public static void aScoutWithASpyglassWatchesFromBeyondTheEyesReach(GameTestHelper h) {
        ReconTripGameTests.Field fl = ReconTripGameTests.field(h, 6000, 4, true);
        GameTestBases.Handle t = fl.t();
        int tx = ReconTripGameTests.TX, tz = ReconTripGameTests.TZ;
        Set<UUID> faces = new HashSet<>();
        try {
            ServerLevel level = h.getLevel();
            fl.kept().reconSidesTried = Side.WEST.bit() | Side.EAST.bit(); // the gate sides were tried: north and south now
            for (int x = tx - 8; x <= tx + 8; x++) {
                for (int z = tz - ReconTripGameTests.SIGHT; z <= tz - ReconTripGameTests.STANDOFF; z++) {
                    put(t, fl.built(), x, 6, z, LEAVES);
                }
            }
            hill(t, fl.built(), tx - 1, tx + 1, tz - 19, tz - 18);
            FactionStorage.insert(level, t.base, new ItemStack(Items.SPYGLASS));
            faces.add(ReconTripGameTests.defender(t, tx, tz + 1).getUUID());
            faces.add(ReconTripGameTests.defender(t, tx + 2, tz + 2).getUUID());
            faces.add(ReconTripGameTests.defender(t, tx - 2, tz + 2).getUUID());
            t.assertTrue(ReconVantage.columns(level, t.data, fl.target(), Side.NORTH).isEmpty(), "the eyes should find no vantage north");
            List<Vantage> glass = ReconVantage.columns(level, t.data, fl.target(), Side.NORTH, ReconOptic.SPYGLASS);
            t.assertTrue(!glass.isEmpty() && rel(h, glass.get(0).foot()).equals(tx + ",4," + (tz - 18)), "the spyglass's vantage north: "
                    + (glass.isEmpty() ? "none" : rel(h, glass.get(0).foot())));
            t.assertTrue(fl.job().optic().none(), "the scout carries no spyglass yet");
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        ReconTripGameTests.Trip trip = new ReconTripGameTests.Trip();
        int[] watchTicks = {0};
        boolean[] down = {false}, reloaded = {false};
        FactionNpc[] scout = {fl.scout()};
        t.onEachTick(() -> {
            ReconTripGameTests.Field now = new ReconTripGameTests.Field(t, fl.f(), fl.target(), fl.order(), scout[0], fl.built());
            ReconTripGameTests.follow(now, trip);
            FactionNpc s = scout[0];
            String phase = now.job().reconPhase();
            var w = now.job().reconWatch();
            if (!reloaded[0] && phase.equals("PILLAR/WATCH") && w != null && w.watchTicks() >= 400) {
                // saved and loaded midway through the watch: the spyglass stays in its hand, the trip remembers it
                int height = now.job().reconPillar().height;
                s = (FactionNpc) ReconTripGameTests.reload(t, ReconTripGameTests.unload(s));
                scout[0] = s;
                ScoutJob job = (ScoutJob) s.job();
                t.assertTrue(job.reconPhase().equals("PILLAR/WATCH") && job.reconPillar() != null && job.reconPillar().height == height,
                        "the loaded scout should go on watching from its pillar: " + job.reconPhase());
                t.assertTrue(s.getMainHandItem().is(Items.SPYGLASS), "loaded with the spyglass in its hand: " + s.getMainHandItem());
                reloaded[0] = true;
            }
            if (phase.equals("PILLAR/WATCH")) {
                watchTicks[0]++;
                t.assertTrue(s.getMainHandItem().is(Items.SPYGLASS), "it watches with the spyglass in its hand: " + s.getMainHandItem());
                t.assertTrue(Inv.count(s.inventory(), x -> x.is(Items.IRON_SWORD)) == 1, "its sword waits in its backpack");
            } else if (phase.equals("PILLAR/DOWN") && watchTicks[0] > 0 && !down[0]) {
                down[0] = true;
                t.assertTrue(s.getMainHandItem().is(Items.IRON_SWORD), "the spyglass put away as it starts down: " + s.getMainHandItem());
            }
            if (trip.filed == null) {
                return;
            }
            Intel.Recon r = trip.filed.recon;
            t.assertTrue(trip.reported == ReconRules.ScoutReport.GOOD, "its order should learn of a good recon: " + trip.reported);
            t.assertTrue(r != null && r.good() && r.soldiers() == 3 && r.faces().equals(faces), "the recon should count the 3 soldiers: " + r);
            t.assertTrue("spyglass".equals(r.optic()), "the report's tag: " + r.optic());
            t.assertTrue(r.vantage().equals("pillar " + trip.top) && trip.top > 0, "the vantage: " + r.vantage());
            t.assertTrue(trip.column != null && trip.column.equals(new BlockPos(tx, 4, tz - 18)), "on the hill, beyond the eyes' reach: "
                    + trip.column);
            t.assertTrue(reloaded[0], "the scout was never saved and loaded midway through the watch");
            t.assertTrue(watchTicks[0] >= ReconRules.WATCH_TICKS - 40 && down[0], "watched with it in hand: " + watchTicks[0] + " ticks");
            t.assertTrue(s.getMainHandItem().is(Items.IRON_SWORD), "its sword back in its hand: " + s.getMainHandItem());
            t.assertTrue(Inv.count(s.inventory(), x -> x.is(Items.SPYGLASS)) == 1, "the spyglass in its backpack");
            t.assertTrue(FactionStorage.count(h.getLevel(), t.base, x -> x.is(Items.SPYGLASS)) == 0, "it took the spyglass from the storehouse");
            t.succeed();
        });
    }

    // ------------------------------------------------------------------ the keep rule and the tag

    /**
     * {@code aScoutKeepsItsSpyglassAndTheReportKeepsItsTag} (R12): a scout carrying nothing watches with the eyes; with a
     * spyglass in its backpack it carries one; it keeps one spyglass when it unloads (and its dirt as before), and nothing
     * of a stick. A recon tagged "spyglass" is saved and loaded with its tag; one saved without a tag (before R12) loads
     * as the eyes'.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void aScoutKeepsItsSpyglassAndTheReportKeepsItsTag(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 40);
        t.guard(() -> {
            FactionNpc scout = t.npc(6, 6, NpcRole.SCOUT);
            ScoutJob job = (ScoutJob) scout.job();
            t.assertTrue(job.optic() == ReconOptic.NONE, "no spyglass: the eyes");
            scout.inventory().setItem(4, new ItemStack(Items.SPYGLASS));
            t.assertTrue(job.optic() == ReconOptic.SPYGLASS, "a spyglass in its backpack: " + job.optic());
            t.assertTrue(ReconOptics.of(new ItemStack(Items.SPYGLASS)) == ReconOptic.SPYGLASS && ReconOptics.of(new ItemStack(Items.STICK)) == null,
                    "the item's row");
            int keepGlass, keepStick, keepDirt;
            try {
                java.lang.reflect.Method keep = ScoutJob.class.getDeclaredMethod("keep", ItemStack.class);
                keep.setAccessible(true);
                keepGlass = (int) keep.invoke(job, new ItemStack(Items.SPYGLASS));
                keepStick = (int) keep.invoke(job, new ItemStack(Items.STICK));
                keepDirt = (int) keep.invoke(job, new ItemStack(Items.DIRT));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            t.assertTrue(keepGlass == 1, "it keeps one spyglass when it unloads: " + keepGlass);
            t.assertTrue(keepStick == 0 && keepDirt == ReconRules.BLOCKS, "and nothing else new: " + keepStick + ", " + keepDirt);

            Intel.Recon r = new Intel.Recon(3, 1.5, Map.of(), 0.4, true, 1200, 500, "pillar 12", "Edrin Cole", Set.of(UUID.randomUUID()),
                    "spyglass");
            Intel.Recon back = Report.loadRecon(Report.saveRecon(r));
            t.assertTrue(back.equals(r) && "spyglass".equals(back.optic()), "the tag survives a save: " + back);
            CompoundTag old = Report.saveRecon(new Intel.Recon(3, 1.5, Map.of(), 0.4, true, 1200, 500, "pillar 12", "Edrin Cole", Set.of()));
            t.assertTrue(!old.contains("optic"), "the eyes write no tag");
            t.assertTrue(Report.loadRecon(old).optic().isEmpty() && ReconOptic.of(Report.loadRecon(old)) == ReconOptic.NONE,
                    "an old recon is the eyes'");
            t.succeed();
        }).run();
    }

    // ------------------------------------------------------------------ the blacksmith's spyglass

    /**
     * {@code theBlacksmithForgesTheVanillaSpyglassForAScoutWithNone} (R12): the blacksmith's recipe is vanilla's (read
     * from the server's recipe manager: an amethyst shard and two copper ingots make one spyglass); with a scout at home
     * carrying none, two copper ingots and a shard in the storehouse, it plans one; not when the scout carries one, not
     * when one waits in the storehouse, not without a shard; and the forging turns the inputs into a spyglass.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void theBlacksmithForgesTheVanillaSpyglassForAScoutWithNone(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 40);
        t.guard(() -> {
            ServerLevel level = h.getLevel();
            RecipeHolder<?> holder = level.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("spyglass")).orElse(null);
            t.assertTrue(holder != null, "vanilla's spyglass recipe");
            Recipe<?> vanilla = holder.value();
            Map<Item, Integer> want = new HashMap<>();
            for (Ingredient in : vanilla.getIngredients()) {
                if (in.isEmpty()) {
                    continue;
                }
                ItemStack[] options = in.getItems();
                t.assertTrue(options.length == 1, "one item per cell: " + options.length);
                want.merge(options[0].getItem(), 1, Integer::sum);
            }
            Map<Item, Integer> ours = new HashMap<>();
            for (Crafting.Input in : Crafting.SPYGLASS.inputs()) {
                for (Item item : want.keySet()) {
                    if (in.filter().test(new ItemStack(item))) {
                        ours.merge(item, in.count(), Integer::sum);
                    }
                }
            }
            t.assertTrue(want.equals(Map.of(Items.COPPER_INGOT, 2, Items.AMETHYST_SHARD, 1)), "vanilla's inputs: " + want);
            t.assertTrue(ours.equals(want) && Crafting.SPYGLASS.inputs().size() == want.size(), "ours: " + ours);
            ItemStack result = vanilla.getResultItem(level.registryAccess());
            t.assertTrue(result.is(Items.SPYGLASS) && result.getCount() == 1 && Crafting.SPYGLASS.output() == Items.SPYGLASS
                    && Crafting.SPYGLASS.outputCount() == 1, "one spyglass: " + result);

            FactionNpc smith = t.npc(6, 6, NpcRole.BLACKSMITH);
            FactionNpc scout = t.npc(8, 8, NpcRole.SCOUT);
            FactionStorage.insert(level, t.base, new ItemStack(Items.COPPER_INGOT, 2));
            FactionStorage.insert(level, t.base, new ItemStack(Items.AMETHYST_SHARD, 1));
            t.assertTrue(spyglassOrders(smith) == 1, "a spyglass for the scout with none: " + spyglassOrders(smith));
            scout.inventory().setItem(3, new ItemStack(Items.SPYGLASS));
            t.assertTrue(spyglassOrders(smith) == 0, "none for a scout that carries one");
            scout.inventory().setItem(3, ItemStack.EMPTY);
            FactionStorage.insert(level, t.base, new ItemStack(Items.SPYGLASS));
            t.assertTrue(spyglassOrders(smith) == 0, "none while one waits in the storehouse");
            FactionStorage.extract(level, t.base, x -> x.is(Items.SPYGLASS), 1);
            FactionStorage.extract(level, t.base, x -> x.is(Items.AMETHYST_SHARD), 1);
            t.assertTrue(spyglassOrders(smith) == 0, "none without a shard");

            SimpleContainer inv = new SimpleContainer(9);
            inv.setItem(0, new ItemStack(Items.COPPER_INGOT, 2));
            inv.setItem(1, new ItemStack(Items.AMETHYST_SHARD, 1));
            t.assertTrue(Crafting.craftWithIntermediates(inv, Crafting.SPYGLASS, 1) == 1, "forged");
            t.assertTrue(Inv.count(inv, x -> x.is(Items.SPYGLASS)) == 1 && Inv.count(inv, x -> x.is(Items.COPPER_INGOT)) == 0
                    && Inv.count(inv, x -> x.is(Items.AMETHYST_SHARD)) == 0, "the inputs used up for one spyglass");
            t.succeed();
        }).run();
    }

    /** How many spyglasses the blacksmith's plan forges now. */
    private static int spyglassOrders(FactionNpc smith) {
        try {
            java.lang.reflect.Method plan = BlacksmithJob.class.getDeclaredMethod("planOrders");
            plan.setAccessible(true);
            int n = 0;
            for (Object o : (List<?>) plan.invoke(smith.job())) {
                BlacksmithJob.Order order = (BlacksmithJob.Order) o;
                if (order.recipe() == Crafting.SPYGLASS) {
                    n += order.times();
                }
            }
            return n;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
