package com.factionclash.structure;

import com.factionclash.storage.StorageCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An ordered block plan in absolute coordinates with named functional markers. Used both for instant
 * placement of starting settlements and as the step list builders work through.
 */
public final class Blueprint {
    public final BlockPos origin;
    public final LinkedHashMap<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    public final Map<String, BlockPos> markers = new HashMap<>();
    public final EnumMap<StorageCategory, List<BlockPos>> chests = new EnumMap<>(StorageCategory.class);
    public final List<BlockPos> beds = new ArrayList<>();
    public final Map<BlockPos, String[]> signs = new HashMap<>();
    public final List<BlockPos> lots = new ArrayList<>();
    public final List<BlockPos> storageSlots = new ArrayList<>();

    public Blueprint(BlockPos origin) {
        this.origin = origin.immutable();
    }

    public BlockPos rel(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    public void set(int x, int y, int z, BlockState s) {
        BlockPos p = rel(x, y, z);
        blocks.remove(p);
        blocks.put(p, s);
    }

    public void set(int x, int y, int z, Block b) {
        set(x, y, z, b.defaultBlockState());
    }

    public void setAbs(BlockPos p, BlockState s) {
        blocks.remove(p);
        blocks.put(p.immutable(), s);
    }

    public void box(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, s);
                }
            }
        }
    }

    public void box(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
        box(x1, y1, z1, x2, y2, z2, b.defaultBlockState());
    }

    /** Perimeter walls of a rectangle from y1 to y2. */
    public void walls(int x1, int z1, int x2, int z2, int y1, int y2, BlockState s) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                set(x, y, z1, s);
                set(x, y, z2, s);
            }
            for (int z = z1 + 1; z < z2; z++) {
                set(x1, y, z, s);
                set(x2, y, z, s);
            }
        }
    }

    public void mark(String key, int x, int y, int z) {
        markers.put(key, rel(x, y, z));
    }

    public void door(int x, int y, int z, Direction facing) {
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        set(x, y, z, lower);
        set(x, y + 1, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** Places a bed whose head is at (x,y,z) and foot one block opposite {@code facing}. */
    public void bed(Block bed, int x, int y, int z, Direction facing) {
        BlockState head = bed.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.HEAD);
        BlockPos h = rel(x, y, z);
        BlockPos f = h.relative(facing.getOpposite());
        setAbs(f, head.setValue(BedBlock.PART, BedPart.FOOT));
        setAbs(h, head);
        beds.add(h);
    }

    /** A double chest: first half at (x,y,z), second toward {@code facing.getClockWise()}. */
    public void chestPair(int x, int y, int z, Direction facing, StorageCategory cat) {
        BlockPos a = rel(x, y, z);
        BlockPos b = a.relative(facing.getClockWise());
        BlockState s = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
        setAbs(a, s.setValue(ChestBlock.TYPE, ChestType.LEFT));
        setAbs(b, s.setValue(ChestBlock.TYPE, ChestType.RIGHT));
        if (cat != null) {
            chests.computeIfAbsent(cat, c -> new ArrayList<>()).add(a);
            chests.get(cat).add(b);
        }
    }

    public void singleChest(int x, int y, int z, Direction facing, String marker) {
        set(x, y, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
        mark(marker, x, y, z);
    }

    public void wallSign(int x, int y, int z, Direction facing, String... lines) {
        set(x, y, z, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing));
        signs.put(rel(x, y, z), lines);
    }

    public void wallTorch(int x, int y, int z, Direction facing) {
        set(x, y, z, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing));
    }

    public void stairs(Block stairs, int x, int y, int z, Direction facing) {
        set(x, y, z, stairs.defaultBlockState().setValue(StairBlock.FACING, facing));
    }

    /** Places every block, then fixes connection shapes (fences, panes, stairs), then writes sign text. */
    public void placeAll(ServerLevel level) {
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS);
        }
        fixShapes(level, blocks.keySet());
        applySigns(level);
    }

    public static void fixShapes(ServerLevel level, Iterable<BlockPos> positions) {
        for (BlockPos p : positions) {
            BlockState s = level.getBlockState(p);
            if (needsShapeFix(s)) {
                BlockState fixed = Block.updateFromNeighbourShapes(s, level, p);
                if (fixed != s && !fixed.isAir()) {
                    level.setBlock(p, fixed, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    public static boolean needsShapeFix(BlockState s) {
        return s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS) || s.is(BlockTags.STAIRS) || s.is(Blocks.GLASS_PANE)
                || s.is(Blocks.IRON_BARS) || s.is(BlockTags.FENCE_GATES);
    }

    public void applySigns(ServerLevel level) {
        signs.forEach((pos, lines) -> writeSign(level, pos, lines));
    }

    public static void writeSign(ServerLevel level, BlockPos pos, String... lines) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SignBlockEntity sign) {
            SignText text = new SignText();
            for (int i = 0; i < 4; i++) {
                text = text.setMessage(i, Component.literal(i < lines.length && lines[i] != null ? lines[i] : ""));
            }
            sign.setText(text, true);
            sign.setChanged();
            level.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
