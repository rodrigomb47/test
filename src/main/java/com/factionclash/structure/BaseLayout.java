package com.factionclash.structure;

import com.factionclash.campaign.BaseState;
import com.factionclash.faction.FactionType;
import com.factionclash.registry.ModBlocks;
import com.factionclash.storage.StorageCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SmokerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.util.RandomSource;

/**
 * The starting settlement plan (all coordinates relative to the base centre; y=0 is the walking level).
 * 49x49 walled base with gates west and east; the levelled grove pad lies outside the west gate. Miners,
 * woodcutters and fishermen choose their own places outside the walls.
 */
public final class BaseLayout {
    public static final int HALF = 24;
    public static final int PLOT_ORIGIN_X = -32;
    /**
     * The base's work belt, west and east of the centre: its MIN/MAX box (z within the walls plus one, or the plot's
     * width) is kept loaded and randomly ticking, so saplings grow and nearby mines keep working without players.
     * The same numbers as older bases, whose hut mine reached that far east.
     */
    public static final int WORK_AREA_WEST = 58, WORK_AREA_EAST = 78;

    private BaseLayout() {
    }

    public static Blueprint generate(FactionType faction, BlockPos center, RandomSource random) {
        Blueprint bp = new Blueprint(center);
        FactionType.Style st = faction.style;
        BlockState wall = st.wall().defaultBlockState();
        BlockState frame = st.frame().defaultBlockState();
        BlockState floor = st.floor().defaultBlockState();
        BlockState roof = st.roof().defaultBlockState();
        BlockState perim = st.perimeter().defaultBlockState();
        BlockState path = Blocks.DIRT_PATH.defaultBlockState();

        bp.mark(BaseState.MIN, -HALF - 1, -1, -HALF - 1);
        bp.mark(BaseState.MAX, HALF + 1, 8, HALF + 1);

        // --- perimeter wall (5 high) with gates west and east
        for (int y = 0; y <= 4; y++) {
            for (int i = -HALF; i <= HALF; i++) {
                bp.set(i, y, -HALF, perim);
                bp.set(i, y, HALF, perim);
                bp.set(-HALF, y, i, perim);
                bp.set(HALF, y, i, perim);
            }
        }
        for (int i = -HALF; i <= HALF; i += 2) {
            bp.set(i, 5, -HALF, perim);
            bp.set(i, 5, HALF, perim);
            bp.set(-HALF, 5, i, perim);
            bp.set(HALF, 5, i, perim);
        }
        for (int z = -1; z <= 1; z++) {
            for (int y = 0; y <= 2; y++) {
                bp.set(-HALF, y, z, Blocks.AIR.defaultBlockState());
                bp.set(HALF, y, z, Blocks.AIR.defaultBlockState());
            }
        }
        // wooden double doors in both gates: players and NPCs open them to go in and out, monsters cannot
        for (int side : new int[]{-1, 1}) {
            int x = side * HALF;
            for (int y = 0; y <= 2; y++) {
                bp.set(x, y, 1, perim);
            }
            bp.set(x, 2, -1, perim);
            bp.set(x, 2, 0, perim);
            Direction facing = side < 0 ? Direction.EAST : Direction.WEST;
            gateDoor(bp, x, -1, facing, side < 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
            gateDoor(bp, x, 0, facing, side < 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
        }
        bp.mark(BaseState.GATE_WEST, -HALF, 0, 0);
        bp.mark(BaseState.GATE_EAST, HALF, 0, 0);
        for (int i = -HALF + 4; i <= HALF - 4; i += 7) {
            bp.wallTorch(i, 3, -HALF + 1, Direction.SOUTH);
            bp.wallTorch(i, 3, HALF - 1, Direction.NORTH);
            if (Math.abs(i) > 2) {
                bp.wallTorch(-HALF + 1, 3, i, Direction.EAST);
                bp.wallTorch(HALF - 1, 3, i, Direction.WEST);
            }
        }

        // --- main road and plaza
        for (int x = -HALF + 1; x <= HALF - 1; x++) {
            for (int z = -1; z <= 1; z++) {
                bp.set(x, -1, z, path);
            }
        }
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                bp.set(x, -1, z, perim);
            }
        }
        for (int z = -12; z <= -6; z++) {
            bp.set(0, -1, z, path);
        }
        for (int z = 6; z <= 11; z++) {
            bp.set(0, -1, z, path);
        }
        lanternPost(bp, -5, -5);
        lanternPost(bp, 5, -5);
        lanternPost(bp, -5, 5);
        lanternPost(bp, 5, 5);
        for (int x : new int[]{-16, -8, 8, 16}) {
            lanternPost(bp, x, -11);
            lanternPost(bp, x, 11);
        }
        lanternPost(bp, -20, 2);
        lanternPost(bp, 20, 2);
        lanternPost(bp, -12, -2);
        lanternPost(bp, 12, -2);
        bp.mark(BaseState.SPAWN, 0, 0, 2);

        // --- central reporting station (complete at start) with map board and faction heart
        building(bp, -6, -21, 6, -13, wall, frame, floor, roof);
        bp.door(0, 0, -13, Direction.SOUTH);
        bp.set(0, 0, -17, ModBlocks.REPORTING_STATION.get().defaultBlockState());
        bp.set(2, 0, -20, ModBlocks.WORLD_MAP_BOARD.get().defaultBlockState());
        bp.set(-2, 0, -20, ModBlocks.WORLD_MAP_BOARD.get().defaultBlockState());
        bp.set(0, 0, -20, ModBlocks.FACTION_HEART.get().defaultBlockState());
        for (int x = -3; x <= 3; x++) {
            bp.set(x, 0, -15, st.carpet().defaultBlockState());
        }
        bp.set(-5, 1, -20, st.banner().defaultBlockState());
        bp.set(5, 1, -20, st.banner().defaultBlockState());
        bp.wallTorch(-5, 2, -17, Direction.EAST);
        bp.wallTorch(5, 2, -17, Direction.WEST);
        bp.wallTorch(0, 2, -14, Direction.NORTH);
        bp.mark(BaseState.STATION, 0, 0, -17);
        bp.mark(BaseState.STATION_STAND, 0, 0, -16);
        bp.mark(BaseState.MAP_BOARD, 2, 0, -20);
        bp.mark(BaseState.HEART, 0, 0, -20);
        bp.wallSign(1, 1, -12, Direction.SOUTH, "Reporting", "Station", faction.displayName, "");

        // --- storage house with categorised double chests
        building(bp, -21, -21, -12, -13, wall, frame, floor, roof);
        bp.door(-16, 0, -13, Direction.SOUTH);
        bp.chestPair(-19, 0, -20, Direction.SOUTH, StorageCategory.FOOD);
        bp.chestPair(-17, 0, -20, Direction.SOUTH, StorageCategory.RAW);
        bp.chestPair(-15, 0, -20, Direction.SOUTH, StorageCategory.BUILDING);
        bp.chestPair(-13, 0, -20, Direction.SOUTH, StorageCategory.TOOLS);
        bp.chestPair(-20, 0, -18, Direction.EAST, StorageCategory.WEAPONS);
        bp.chestPair(-20, 0, -16, Direction.EAST, StorageCategory.ARMOR);
        bp.chestPair(-13, 0, -18, Direction.WEST, StorageCategory.SPECIAL);
        bp.chestPair(-13, 0, -16, Direction.WEST, StorageCategory.GENERAL);
        storageSign(bp, -19, -20, Direction.SOUTH, StorageCategory.FOOD);
        storageSign(bp, -17, -20, Direction.SOUTH, StorageCategory.RAW);
        storageSign(bp, -15, -20, Direction.SOUTH, StorageCategory.BUILDING);
        storageSign(bp, -13, -20, Direction.SOUTH, StorageCategory.TOOLS);
        storageSign(bp, -20, -18, Direction.EAST, StorageCategory.WEAPONS);
        storageSign(bp, -20, -16, Direction.EAST, StorageCategory.ARMOR);
        storageSign(bp, -13, -18, Direction.WEST, StorageCategory.SPECIAL);
        storageSign(bp, -13, -16, Direction.WEST, StorageCategory.GENERAL);
        bp.wallTorch(-16, 2, -20, Direction.SOUTH);
        bp.mark(BaseState.STORAGE_STAND, -16, 0, -17);
        // reserved spots for storage expansions; stored as chest pairs (first half, second half)
        bp.storageSlots.add(bp.rel(-13, 0, -14));
        bp.storageSlots.add(bp.rel(-13, 0, -15));
        bp.storageSlots.add(bp.rel(-19, 0, -14));
        bp.storageSlots.add(bp.rel(-18, 0, -14));
        bp.wallSign(-17, 1, -12, Direction.SOUTH, "Faction", "Storage", "", "");

        // --- workshop: smithy, kitchen and enchanting setup
        building(bp, 12, -21, 21, -13, wall, frame, floor, roof);
        bp.door(16, 0, -13, Direction.SOUTH);
        bp.set(13, 0, -20, Blocks.CRAFTING_TABLE);
        bp.set(14, 0, -20, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.SOUTH));
        bp.set(15, 0, -20, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.SOUTH));
        bp.set(18, 0, -20, Blocks.SMOKER.defaultBlockState().setValue(SmokerBlock.FACING, Direction.SOUTH));
        bp.set(20, 0, -20, Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.EAST));
        bp.mark(BaseState.CRAFTING, 13, 0, -20);
        bp.mark(BaseState.FURNACE, 14, 0, -20);
        bp.mark(BaseState.FURNACE_2, 15, 0, -20);
        bp.mark(BaseState.SMOKER, 18, 0, -20);
        bp.mark(BaseState.ANVIL, 20, 0, -20);
        bp.set(18, 0, -15, Blocks.ENCHANTING_TABLE);
        bp.mark(BaseState.ENCHANTING, 18, 0, -15);
        for (int x = 16; x <= 20; x++) {
            bp.set(x, 0, -17, Blocks.BOOKSHELF);
            bp.set(x, 1, -17, Blocks.BOOKSHELF);
        }
        bp.wallTorch(13, 2, -14, Direction.NORTH);
        bp.wallTorch(20, 2, -14, Direction.NORTH);
        bp.mark(BaseState.WORKSHOP_STAND, 15, 0, -19);
        bp.wallSign(15, 1, -12, Direction.SOUTH, "Workshop", "Smithy - Kitchen", "Enchanting", "");

        // --- builder staging chest with project sign
        bp.singleChest(7, 0, -5, Direction.WEST, BaseState.STAGING);
        bp.set(7, 0, -4, st.frame().defaultBlockState());
        bp.set(7, 1, -4, st.frame().defaultBlockState());
        bp.wallSign(6, 1, -4, Direction.WEST, "Construction", "No project", "", "");
        bp.mark(BaseState.STAGING_SIGN, 6, 1, -4);

        // --- animal pen (south-west)
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        for (int x = -20; x <= -10; x++) {
            bp.set(x, 0, 3, fence);
            bp.set(x, 0, 9, fence);
        }
        for (int z = 3; z <= 9; z++) {
            bp.set(-20, 0, z, fence);
            bp.set(-10, 0, z, fence);
        }
        bp.set(-15, 0, 3, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH));
        // no hay bale or other step next to the fence: animals would jump onto it and over the fence
        bp.mark("pen", -15, 0, 6);

        // --- farm (south-east): farmland rows around a water channel
        for (int x = 8; x <= 22; x++) {
            for (int z = 3; z <= 9; z++) {
                bp.set(x, -1, z, path);
            }
        }
        for (int z = 4; z <= 8; z++) {
            bp.set(15, -1, z, Blocks.WATER.defaultBlockState());
            for (int x = 9; x <= 21; x++) {
                if (x == 15) {
                    continue;
                }
                bp.set(x, -1, z, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
                Block crop = x <= 14 ? Blocks.WHEAT : (x <= 18 ? Blocks.CARROTS : Blocks.POTATOES);
                int age = random.nextInt(8);
                bp.set(x, 0, z, crop.defaultBlockState().setValue(CropBlock.AGE, age));
            }
        }
        bp.mark("farm_min", 9, -1, 4);
        bp.mark("farm_max", 21, -1, 8);

        // --- houses with ten beds (south row)
        house(bp, -21, 12, wall, frame, floor, roof, st.banner() == Blocks.RED_WOOL ? Blocks.RED_BED : Blocks.WHITE_BED);
        house(bp, 13, 12, wall, frame, floor, roof, Blocks.WHITE_BED);

        // --- expansion lots for future houses (7x7, origin = north-west corner)
        bp.lots.add(bp.rel(-20, 0, -9));
        bp.lots.add(bp.rel(14, 0, -9));
        bp.lots.add(bp.rel(-3, 0, 13));
        bp.lots.add(bp.rel(-11, 0, 13));
        bp.lots.add(bp.rel(5, 0, 13));

        // --- outside path to the grove pad
        for (int x = PLOT_ORIGIN_X + 1; x <= -HALF - 1; x++) {
            bp.set(x, -1, 0, path);
        }
        return bp;
    }

    private static void lanternPost(Blueprint bp, int x, int z) {
        bp.set(x, 0, z, Blocks.OAK_FENCE);
        bp.set(x, 1, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
    }

    private static void storageSign(Blueprint bp, int x, int z, Direction facing, StorageCategory cat) {
        bp.wallSign(x, 1, z, facing, "[Storage]", cat.label, "", "");
    }

    /** Rectangular building with log corners, wall infill 4 high, floor and flat roof. */
    static void building(Blueprint bp, int x1, int z1, int x2, int z2, BlockState wall, BlockState frame,
                         BlockState floor, BlockState roof) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                bp.set(x, -1, z, floor);
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                for (int y = 0; y <= 3; y++) {
                    bp.set(x, y, z, edge ? (corner ? frame : wall) : Blocks.AIR.defaultBlockState());
                }
                bp.set(x, 4, z, roof);
            }
        }
        // windows
        int mx = (x1 + x2) / 2;
        int mz = (z1 + z2) / 2;
        bp.set(x1, 1, mz, Blocks.GLASS_PANE);
        bp.set(x2, 1, mz, Blocks.GLASS_PANE);
        if (mx - 3 > x1) {
            bp.set(mx - 3, 1, z1, Blocks.GLASS_PANE);
            bp.set(mx + 3, 1, z1, Blocks.GLASS_PANE);
        }
    }

    /** Starting house: 9x9, five beds along the south wall, door north. (x1,z1) = north-west corner. */
    private static void house(Blueprint bp, int x1, int z1, BlockState wall, BlockState frame, BlockState floor,
                              BlockState roof, Block bed) {
        building(bp, x1, z1, x1 + 8, z1 + 8, wall, frame, floor, roof);
        bp.door(x1 + 4, 0, z1, Direction.NORTH);
        for (int dx : new int[]{1, 2, 3, 5, 6}) {
            bp.bed(bed, x1 + dx, 0, z1 + 7, Direction.SOUTH);
        }
        bp.wallTorch(x1 + 4, 2, z1 + 1, Direction.SOUTH);
        bp.set(x1 + 7, 0, z1 + 1, Blocks.CHEST.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.WEST));
    }

    private static void gateDoor(Blueprint bp, int x, int z, Direction facing, DoorHingeSide hinge) {
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        bp.set(x, 0, z, lower);
        bp.set(x, 1, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }
}
