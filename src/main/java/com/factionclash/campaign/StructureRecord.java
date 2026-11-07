package com.factionclash.campaign;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The saved plan of a finished faction structure, used to detect and repair damage. Plans are permanent, so a
 * destroyed wall section is never forgotten (spec 11).
 */
public final class StructureRecord {
    public String name;
    public long[] positions;
    public int[] states;
    public final List<BlockState> palette = new ArrayList<>();

    public static StructureRecord of(String name, Map<BlockPos, BlockState> blocks) {
        StructureRecord r = new StructureRecord();
        r.name = name;
        Map<BlockState, Integer> index = new HashMap<>();
        List<Long> pos = new ArrayList<>();
        List<Integer> st = new ArrayList<>();
        blocks.forEach((p, s) -> {
            if (s.isAir()) {
                return;
            }
            int i = index.computeIfAbsent(s, k -> {
                r.palette.add(k);
                return r.palette.size() - 1;
            });
            pos.add(p.asLong());
            st.add(i);
        });
        r.positions = pos.stream().mapToLong(Long::longValue).toArray();
        r.states = st.stream().mapToInt(Integer::intValue).toArray();
        return r;
    }

    public int size() {
        return positions.length;
    }

    public BlockPos pos(int i) {
        return BlockPos.of(positions[i]);
    }

    public BlockState state(int i) {
        return palette.get(states[i]);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putLongArray("pos", positions);
        t.putIntArray("st", states);
        ListTag pal = new ListTag();
        for (BlockState s : palette) {
            pal.add(NbtUtils.writeBlockState(s));
        }
        t.put("palette", pal);
        return t;
    }

    public static StructureRecord load(CompoundTag t, HolderLookup.Provider provider) {
        StructureRecord r = new StructureRecord();
        r.name = t.getString("name");
        r.positions = t.getLongArray("pos");
        r.states = t.getIntArray("st");
        ListTag pal = t.getList("palette", Tag.TAG_COMPOUND);
        var blocks = provider.lookupOrThrow(Registries.BLOCK);
        for (int i = 0; i < pal.size(); i++) {
            r.palette.add(NbtUtils.readBlockState(blocks, pal.getCompound(i)));
        }
        if (r.states.length != r.positions.length) {
            return null;
        }
        return r;
    }
}
