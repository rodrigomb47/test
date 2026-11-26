package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.StructureRecord;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.campaign.ReconRules;
import com.factionclash.logic.campaign.ReconRules.Side;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import com.factionclash.work.BlockWork;
import com.factionclash.work.Inv;
import com.factionclash.work.PillarClimb;
import com.factionclash.work.ReconVantage;
import com.factionclash.work.ReconVantage.Vantage;
import com.factionclash.work.Scaffold;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The recon scout's vantage and its pillar ({@code docs/design/recon.md} 3.5, task R4a), in the 32 x 32
 * {@code mobility_field} (recon.md 10.2: the climb and the column search need open sky, so both tests have
 * {@code skyAccess = true}, and the real band of 61 to 72 blocks does not fit a test field: the target carries a scaled
 * one, 10 to 13 blocks, in its transient {@code testStandoff} and {@code testSight}). The column search: the side order,
 * the band, the axis first and along it the highest ground, ground it can walk to (not the top of an old pillar or a
 * cliff it cannot climb), a tree (no open sky, a trunk's top), a base's plan and a base's alarm box, a lair watched from
 * the ground, and a target whose band lies in chunks not loaded (nothing is loaded to look). The climb: the pillar driver
 * the woodcutter shares ({@link PillarClimb}) run by a test for an NPC with no job, as a recon scout will run it in R4b:
 * it looks once at each block, stops where its look says, is saved and loaded midway, and comes down with every block
 * back in its backpack.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class ReconVantageGameTests {
    private ReconVantageGameTests() {
    }

    private static final String FIELD = HeroMobilityGameTests.FIELD;
    private static final FactionType SIDE = FactionType.TIDE_ALLIANCE, ENEMY = FactionType.GROVE_COVENANT,
            NEIGHBOUR = FactionType.STONEGUARD;
    /** The target's centre (relative) and its scaled band. */
    private static final int TX = 14, TZ = 18, STANDOFF = 10, SIGHT = 13;

    /** Sets a block the test builds; the cleanup puts air back. */
    private static void put(GameTestBases.Handle t, List<BlockPos> built, int x, int y, int z, BlockState s) {
        BlockPos p = new BlockPos(x, y, z);
        t.helper.setBlock(p, s);
        built.add(p);
    }

    private static void column(GameTestBases.Handle t, List<BlockPos> built, int x, int z, int from, int to, BlockState s) {
        for (int y = from; y <= to; y++) {
            put(t, built, x, y, z, s);
        }
    }

    /** The search's best column's foot, relative to the field ({@code "none"} without one). */
    private static String best(GameTestHelper h, List<Vantage> v) {
        return v.isEmpty() ? "none" : rel(h, v.get(0).foot());
    }

    private static String rel(GameTestHelper h, BlockPos abs) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        return (abs.getX() - o.getX()) + "," + (abs.getY() - o.getY()) + "," + (abs.getZ() - o.getZ());
    }

    private static boolean has(GameTestHelper h, List<Vantage> v, int x, int z) {
        BlockPos o = h.absolutePos(new BlockPos(x, 0, z));
        return v.stream().anyMatch(c -> c.foot().getX() == o.getX() && c.foot().getZ() == o.getZ());
    }

    private static int indexOf(GameTestHelper h, List<Vantage> v, int x, int z) {
        BlockPos o = h.absolutePos(new BlockPos(x, 0, z));
        for (int i = 0; i < v.size(); i++) {
            if (v.get(i).foot().getX() == o.getX() && v.get(i).foot().getZ() == o.getZ()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * {@code aScoutsVantageIsGroundItCanWalkToUnderOpenSky}: our base at relative (12, 12), the enemy settlement at (14,
     * 18) with a scaled band of 10 to 13 blocks. The sides go west (the gate side facing home), east, north (facing
     * home), south, and a trip after a blind west starts east. On the flat field the east side's 42 columns are all
     * vantages, the axis at the standoff first. Then on the east side: an old dirt pillar on the axis (4 high: no walk steps
     * onto it, not even from the hill beside it), a hill on the axis beyond it (2 high, a 1-high skirt to walk up), a staircase 3 high five off
     * the axis (higher, and walkable) and a cliff 4 high three off it (higher, and no walk climbs it): the scout takes the
     * hill's near edge on the axis, never the pillar or the cliff, and the staircase only after every column nearer the
     * axis. A tree on the hill (leaves over it, a trunk on its far edge) sends it back to the axis at the standoff; a
     * neighbour's planned path over that column sends it one block off the axis. On the north side, the axis's two nearest
     * columns lie in our own base's alarm box: the third is taken. A lair at the same centre (a band of 7 to 9, on the
     * axis) is watched from the ground, under leaves too. A target 4000 blocks away has no vantage, and the search loads
     * no chunk to find that out.
     */
    @GameTest(template = FIELD, timeoutTicks = 60, batch = "workers_static", skyAccess = true)
    public static void aScoutsVantageIsGroundItCanWalkToUnderOpenSky(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 2, 60);
        List<BlockPos> built = new ArrayList<>();
        t.onCleanup(() -> built.forEach(p -> h.setBlock(p, Blocks.AIR)));
        t.guard(() -> {
            ServerLevel level = h.getLevel();
            BaseState target = KnowledgeGameTests.enemyBase(t, ENEMY, h.absolutePos(new BlockPos(TX, 2, TZ)));
            target.testHalf = 2;
            target.testStandoff = STANDOFF;
            target.testSight = SIGHT;
            BlockPos home = t.base.center;

            // the side order: the gate side facing home, the other gate side, the side facing home, the last
            t.assertTrue(ReconVantage.order(target, home).equals(List.of(Side.WEST, Side.EAST, Side.NORTH, Side.SOUTH)),
                    "side order: " + ReconVantage.order(target, home));
            t.assertTrue(ReconVantage.sides(target, home, 0).equals(List.of(Side.WEST, Side.EAST)), "a first trip: west, east");
            t.assertTrue(ReconVantage.sides(target, home, Side.WEST.bit()).equals(List.of(Side.EAST, Side.NORTH)),
                    "after a blind west: " + ReconVantage.sides(target, home, Side.WEST.bit()));

            // the flat field: every column of the band, the axis at the standoff first
            ReconRules.Band band = ReconVantage.band(target);
            t.assertTrue(band.equals(new ReconRules.Band(STANDOFF, SIGHT, ReconRules.LATERAL)), "the test band: " + band);
            List<ReconRules.Column> eastCols = ReconRules.columns(Side.EAST, band);
            List<Vantage> flat = ReconVantage.columns(level, t.data, target, Side.EAST);
            t.assertTrue(flat.size() == eastCols.size() && eastCols.size() == 42, "every column of the band on flat ground: "
                    + flat.size() + " of " + eastCols.size());
            t.assertTrue(best(h, flat).equals("24,2,18") && flat.get(0).rise() == 0, "the axis at the standoff: " + best(h, flat));
            for (int i = 0; i < flat.size(); i++) {
                Vantage v = flat.get(i);
                int dx = v.foot().getX() - target.center.getX(), dz = v.foot().getZ() - target.center.getZ();
                t.assertTrue(v.column().equals(eastCols.get(i)) && dx == v.column().dx() && dz == v.column().dz()
                        && Math.max(Math.abs(dx), Math.abs(dz)) >= STANDOFF && dx * dx + dz * dz <= SIGHT * SIGHT,
                        "on flat ground the band's own order, inside the band: " + v);
            }

            // the east side's land
            BlockState stone = Blocks.STONE.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
            column(t, built, 25, 18, 2, 5, dirt); // an old pillar on the axis, 4 high: two above the hill beside it
            for (int x = 26; x <= 27; x++) {
                for (int z = 17; z <= 19; z++) {
                    column(t, built, x, z, 2, 3, stone); // the hill on the axis, 2 high
                }
            }
            for (int z = 16; z <= 20; z++) {
                put(t, built, 28, 2, z, stone); // its skirt, 1 high, to walk up
            }
            for (int x = 26; x <= 27; x++) {
                put(t, built, x, 2, 16, stone);
                put(t, built, x, 2, 20, stone);
            }
            put(t, built, 23, 2, 13, stone); // a staircase five off the axis, 3 high
            column(t, built, 24, 13, 2, 3, stone);
            column(t, built, 25, 13, 2, 4, stone);
            column(t, built, 26, 13, 2, 4, stone);
            for (int x = 24; x <= 25; x++) {
                for (int z = 21; z <= 22; z++) {
                    column(t, built, x, z, 2, 5, stone); // a cliff three off the axis, 4 high, no way up
                }
            }
            List<Vantage> hill = ReconVantage.columns(level, t.data, target, Side.EAST);
            t.assertTrue(best(h, hill).equals("26,4,18") && hill.get(0).rise() == 2,
                    "the hill's near edge on the axis, the highest ground along it: " + best(h, hill));
            t.assertTrue(indexOf(h, hill, 27, 18) == 1 && indexOf(h, hill, 24, 18) == 2,
                    "then the hill's far edge, then the flat column at the standoff");
            t.assertTrue(!has(h, hill, 25, 18), "no walk steps onto the old pillar's top");
            for (int x = 24; x <= 25; x++) {
                for (int z = 21; z <= 22; z++) {
                    t.assertTrue(!has(h, hill, x, z), "no walk climbs the cliff: " + x + "," + z);
                }
            }
            int stair = indexOf(h, hill, 25, 13);
            t.assertTrue(stair > 0 && hill.get(stair).rise() == 3, "the staircase's top is ground: " + stair);
            for (int i = 0; i < stair; i++) {
                t.assertTrue(Math.abs(hill.get(i).column().lateral()) <= 5, "the higher staircase only after the lines nearer the axis: "
                        + hill.get(i));
            }

            // a tree on the hill: leaves over it, its trunk on the far edge
            BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            column(t, built, 27, 18, 4, 7, Blocks.OAK_LOG.defaultBlockState());
            for (int x = 26; x <= 28; x++) {
                for (int z = 17; z <= 19; z++) {
                    for (int y = 6; y <= 8; y++) {
                        if (!(x == 27 && z == 18 && y <= 7)) {
                            put(t, built, x, y, z, leaves);
                        }
                    }
                }
            }
            List<Vantage> tree = ReconVantage.columns(level, t.data, target, Side.EAST);
            t.assertTrue(best(h, tree).equals("24,2,18"), "under the tree no open sky, a trunk's top no ground: back to the "
                    + "standoff: " + best(h, tree));
            t.assertTrue(!has(h, tree, 26, 18) && !has(h, tree, 27, 18) && !has(h, tree, 26, 17) && !has(h, tree, 26, 19),
                    "nothing under the leaves or on the trunk");

            // a neighbour's planned path over the axis column at the standoff (its walls far off)
            BaseState neighbour = KnowledgeGameTests.enemyBase(t, NEIGHBOUR, h.absolutePos(new BlockPos(TX + 300, 2, TZ)));
            neighbour.structures.add(StructureRecord.of("Test path", Map.of(h.absolutePos(new BlockPos(24, 1, 18)),
                    Blocks.DIRT_PATH.defaultBlockState())));
            neighbour.invalidatePlanIndex();
            List<Vantage> plan = ReconVantage.columns(level, t.data, target, Side.EAST);
            t.assertTrue(!has(h, plan, 24, 18) && best(h, plan).equals("24,2,17"),
                    "a base's planned block is no vantage: one block off the axis: " + best(h, plan));
            t.data.bases.remove(neighbour.id);

            // the north side: the axis's two nearest columns lie in our own base's alarm box (its walls' half 1, plus 4)
            List<Vantage> north = ReconVantage.columns(level, t.data, target, Side.NORTH);
            t.assertTrue(best(h, north).equals("14,2,6"), "the axis outside the alarm box: " + best(h, north));
            for (Vantage v : north) {
                t.assertTrue(Math.max(Math.abs(v.foot().getX() - home.getX()), Math.abs(v.foot().getZ() - home.getZ()))
                        > 1 + ReconVantage.ALARM_MARGIN, "inside our alarm box: " + v);
            }

            // a lair at the same centre: watched from the ground on the axis, under leaves as well
            BaseState lair = new BaseState();
            lair.faction = ENEMY;
            lair.kind = BaseState.Kind.LAIR;
            lair.center = target.center;
            lair.name = "Recon test lair";
            lair.testHalf = 1;
            lair.testRadius = 2;
            lair.testStandoff = 7;
            lair.testSight = 9;
            t.data.addBase(lair);
            t.onCleanup(() -> t.data.bases.remove(lair.id));
            put(t, built, 6, 4, 18, leaves); // leaves two above the ground: no open sky, but room to stand
            List<Vantage> lairWest = ReconVantage.columns(level, t.data, lair, Side.WEST);
            t.assertTrue(lairWest.size() == 3 && lairWest.stream().allMatch(v -> v.column().lateral() == 0)
                            && best(h, lairWest).equals("7,2,18") && has(h, lairWest, 6, 18),
                    "a lair's vantages: on the axis, from the ground: " + lairWest.stream().map(v -> rel(h, v.foot())).toList());
            t.data.bases.remove(lair.id);

            // a target whose band lies in chunks not loaded: no vantage, and nothing loaded to look
            BaseState far = KnowledgeGameTests.enemyBase(t, ENEMY, h.absolutePos(new BlockPos(TX + 4000, 2, TZ)));
            far.testHalf = 2;
            far.testStandoff = STANDOFF;
            far.testSight = SIGHT;
            BlockPos farAxis = far.center.offset(STANDOFF, 0, 0);
            t.assertTrue(!level.hasChunk(farAxis.getX() >> 4, farAxis.getZ() >> 4), "the far band's chunk should not be loaded");
            List<Vantage> none = ReconVantage.columns(level, t.data, far, Side.EAST);
            t.assertTrue(none.isEmpty(), "no vantage in chunks not loaded: " + none.size());
            t.assertTrue(!level.hasChunk(farAxis.getX() >> 4, farAxis.getZ() >> 4), "the search loaded a chunk");
            t.succeed();
        }).run();
    }

    /**
     * {@code aPillarClimbLooksOnceAtEachBlock}: an NPC with no job and 6 dirt, at a column's foot, run by the test with the
     * pillar driver ({@link PillarClimb}) as a recon scout will be: it looks from the ground and once after each block (0,
     * 1, 2, 3 and 4), even while it stands 3 high for several ticks clearing a leaf over its head; its look at 2 being good
     * sets the stop two above it (4: {@link ReconRules#stopHeight}); the pillar is saved and loaded while it stands 3 high
     * (the climb goes on from the loaded pillar, and the look there is taken again: what is not saved starts again); it
     * holds still on top a moment, and comes down block by block: no dirt left in the column, all 6 back in its backpack,
     * on the ground unhurt, never off its column.
     */
    @GameTest(template = FIELD, timeoutTicks = 600, batch = "recon_pillar_climb", skyAccess = true)
    public static void aPillarClimbLooksOnceAtEachBlock(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 1, 2, 600);
        FactionNpc n;
        try {
            n = t.track(h.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(22, 2, 22)));
            n.faction(SIDE);
            n.role(NpcRole.SCOUT);
            n.goalSelector.removeAllGoals(g -> true); // only the test moves it: no job, no walk, no fight
            n.targetSelector.removeAllGoals(g -> true);
            n.inventory().setItem(0, new ItemStack(Items.DIRT, 6));
            // a leaf over its head once it stands 3 high, which it clears on its way up (Climber.clearOverhead)
            h.setBlock(new BlockPos(22, 7, 22), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
            t.onCleanup(() -> h.setBlock(new BlockPos(22, 7, 22), Blocks.AIR));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        BlockPos foot = h.absolutePos(new BlockPos(22, 2, 22));
        Scaffold.Pillar[] p = {new Scaffold.Pillar(foot, ReconRules.PILLAR_MAX, false)};
        List<Integer> looks = new ArrayList<>();
        BlockWork.Breaker breaker = new BlockWork.Breaker();
        int[] top = {-1}, held = {0}, reloaded = {0}, cleared = {0};
        PillarClimb.Climber scout = new PillarClimb.Climber() {
            @Override
            public void look(Scaffold.Pillar q) {
                looks.add(q.height);
                if (q.height == 2) {
                    q.goal = ReconRules.stopHeight(2); // a good look at 2: two blocks more
                }
            }

            @Override
            public boolean clearOverhead(Scaffold.Pillar q, BlockPos over) {
                cleared[0]++;
                breaker.tick(n, over); // by hand, at player speed: several ticks standing 3 high
                return true;
            }
        };
        t.onEachTick(() -> {
            Scaffold.Pillar q = p[0];
            t.assertTrue(q.phase == Scaffold.Phase.WALK || !PillarClimb.offColumn(n, q), "off its column at " + n.position());
            switch (q.phase) {
                case WALK -> {
                    if (PillarClimb.atFoot(n, q)) {
                        PillarClimb.begin(n, q);
                    }
                }
                case CLIMB -> {
                    if (reloaded[0] == 0 && looks.contains(3) && n.onGround()) {
                        Scaffold.Pillar loaded = Scaffold.Pillar.load(q.save()); // saved with the job, loaded: resumed where it was
                        t.assertTrue(loaded.save().equals(q.save()) && loaded.phase == Scaffold.Phase.CLIMB && loaded.goal == 4,
                                "the pillar should load as saved: " + loaded.save());
                        p[0] = loaded;
                        q = loaded;
                        reloaded[0] = 1;
                    }
                    PillarClimb.Climb c = PillarClimb.climb(n, q, breaker, scout);
                    t.assertTrue(c != PillarClimb.Climb.STOPPED, "stopped short at " + q.height);
                }
                case WORK -> {
                    if (!PillarClimb.settled(n, q)) {
                        return;
                    }
                    if (top[0] < 0) {
                        top[0] = q.height;
                        for (int y = 2; y < 2 + q.height; y++) {
                            t.assertTrue(h.getBlockState(new BlockPos(22, y, 22)).is(Blocks.DIRT), "the pillar's block at " + y);
                        }
                    }
                    if (held[0]++ < 20) {
                        return; // (a watch from the top)
                    }
                    if (!PillarClimb.down(q)) {
                        t.assertTrue(looks.equals(List.of(0, 1, 2, 3, 3, 4)) && reloaded[0] == 1,
                                "a look at each block, once, and again where it stood when loaded: " + looks);
                        t.assertTrue(cleared[0] > 1 && h.getBlockState(new BlockPos(22, 7, 22)).isAir(), "the leaf overhead cleared: " + cleared[0]);
                        t.assertTrue(top[0] == 4, "it should have stopped two above its good look: " + top[0]);
                        for (int y = 2; y <= 8; y++) {
                            t.assertTrue(!h.getBlockState(new BlockPos(22, y, 22)).is(Blocks.DIRT), "dirt left in the column at " + y);
                        }
                        t.assertTrue(Inv.count(n.inventory(), s -> s.is(Items.DIRT)) == 6, "all 6 dirt back: "
                                + Inv.count(n.inventory(), s -> s.is(Items.DIRT)));
                        t.assertTrue(n.onGround() && n.getBlockY() == foot.getY(), "on the ground: " + n.getY());
                        t.assertTrue(n.getHealth() >= n.getMaxHealth(), "hurt: " + n.getHealth());
                        t.succeed();
                    }
                }
                case DOWN -> {
                    PillarClimb.Down d = PillarClimb.stepDown(n, q, breaker, PillarClimb.PLAIN);
                    t.assertTrue(d != PillarClimb.Down.STUCK && d != PillarClimb.Down.GROUND, "the way down: " + d);
                }
            }
        });
    }
}
