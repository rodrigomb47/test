package com.factionclash.work;

import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * A woodcutter's scaffold pillar, as a player "nerd-poles" up beside a trunk too tall to fell from the ground: it
 * jumps and places a block under its feet, again and again, fells the tree from up there, and breaks the pillar
 * block under its feet on the way down, the blocks going back into its backpack. This class holds what the pillar is
 * made of, its saved state and the placing itself; {@link WoodcutterJob} drives it.
 *
 * <p>Only earth is used (dirt, coarse dirt): it comes back by hand at player speed. Stone, cobblestone and netherrack
 * need a pickaxe to drop anything, which a woodcutter does not carry, so they would be slow to break and lost.</p>
 */
public final class Scaffold {
    private Scaffold() {
    }

    /** PROPOSED: scaffold blocks fetched from the storehouse for a tall tree, and kept in the backpack when unloading. */
    public static final int CARRY = 32;

    /** True for an item the woodcutter builds its scaffold with: dirt or coarse dirt. */
    public static boolean isItem(ItemStack s) {
        return s.is(Items.DIRT) || s.is(Items.COARSE_DIRT);
    }

    /** True for a block a scaffold block can have become: dirt, coarse dirt, or dirt that grass spread onto. */
    public static boolean isBlock(BlockState s) {
        return s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.GRASS_BLOCK);
    }

    /** Scaffold blocks in the NPC's backpack. */
    public static int carried(FactionNpc npc) {
        return Inv.count(npc.inventory(), Scaffold::isItem);
    }

    /**
     * Places a scaffold block from the backpack at {@code at}, as a player's block placement: only into air or
     * something that gives way (grass, a snow layer), never into fluid or where a living entity other than the NPC
     * stands. Dirt first. True if placed.
     */
    public static boolean place(FactionNpc npc, BlockPos at) {
        ServerLevel level = (ServerLevel) npc.level();
        int slot = -1;
        for (int i = 0; i < npc.inventory().getContainerSize(); i++) {
            ItemStack s = npc.inventory().getItem(i);
            if (isItem(s) && (slot < 0 || s.is(Items.DIRT) && !npc.inventory().getItem(slot).is(Items.DIRT))) {
                slot = i;
            }
        }
        if (slot < 0) {
            return false;
        }
        BlockState here = level.getBlockState(at);
        ItemStack stack = npc.inventory().getItem(slot);
        BlockState block = ((BlockItem) stack.getItem()).getBlock().defaultBlockState();
        if (!(here.isAir() || here.canBeReplaced()) || !here.getFluidState().isEmpty() || BlockWork.obstructed(level, at, block, npc)) {
            return false;
        }
        npc.getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
        level.setBlock(at, block, Block.UPDATE_ALL);
        level.playSound(null, at, block.getSoundType(level, at, npc).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
        stack.shrink(1);
        npc.inventory().setChanged();
        npc.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /** True while the NPC stands within {@code slack} (sideways) of the middle of column (x, z). */
    public static boolean overColumn(FactionNpc npc, BlockPos column, double slack) {
        double dx = npc.getX() - (column.getX() + 0.5), dz = npc.getZ() - (column.getZ() + 0.5);
        return dx * dx + dz * dz <= slack * slack;
    }

    /**
     * Keeps the NPC still over the middle of its column, as a player standing on a pillar holds still: no sideways
     * motion, and back to the middle if it slid off it a little.
     */
    public static void holdStill(FactionNpc npc, BlockPos column) {
        npc.getNavigation().stop();
        // no step left over from a walk: the move control's last order is to stand at the middle, at no speed
        npc.getMoveControl().setWantedPosition(column.getX() + 0.5, npc.getY(), column.getZ() + 0.5, 0.0);
        Vec3 v = npc.getDeltaMovement();
        if (v.x != 0 || v.z != 0) {
            npc.setDeltaMovement(0, v.y, 0);
        }
        if (!overColumn(npc, column, 0.05)) {
            npc.setPos(column.getX() + 0.5, npc.getY(), column.getZ() + 0.5);
        }
    }

    /** What the woodcutter is doing with its pillar. */
    public enum Phase {
        /** Walking to the column's foot. */
        WALK,
        /** Jumping and placing a block underfoot, block by block, up to the planned height. */
        CLIMB,
        /** Cutting what is within reach (logs top first, then leaves), then stepping down one block. */
        WORK,
        /** Breaking the block underfoot (the step down). */
        DOWN
    }

    /**
     * A pillar: the cell its first block goes into ({@code base}, at the stump's level), its planned height, the
     * blocks standing under the woodcutter now ({@code height}), the phase, why it comes down early (null while it
     * works), and whether it was built to take down scaffold blocks left standing ({@code strays}) rather than to fell
     * a tree. Saved with the job, so a reload resumes it exactly.
     */
    public static final class Pillar {
        public final BlockPos base;
        public int goal;
        public int height;
        public Phase phase = Phase.WALK;
        public String down;
        public final boolean strays;
        /** Ticks in the air on the current jump, and jumps that landed without a block placed. */
        int airTicks, misses;
        /** A block went in under it on the current jump. */
        boolean placed;

        public Pillar(BlockPos base, int goal, boolean strays) {
            this.base = base.immutable();
            this.goal = goal;
            this.strays = strays;
        }

        /** The cell the woodcutter's feet are in while it stands on the pillar. */
        public BlockPos feet() {
            return base.above(height);
        }

        /** The block it stands on (null on the ground). */
        public BlockPos top() {
            return height > 0 ? base.above(height - 1) : null;
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("base", base.asLong());
            t.putInt("goal", goal);
            t.putInt("height", height);
            t.putString("phase", phase.name());
            if (down != null) {
                t.putString("down", down);
            }
            t.putBoolean("strays", strays);
            return t;
        }

        /** A saved pillar, or null for a tag without one. Unknown phases load as WORK (it looks around, then steps down). */
        public static Pillar load(CompoundTag t) {
            if (t == null || !t.contains("base")) {
                return null;
            }
            Pillar p = new Pillar(BlockPos.of(t.getLong("base")), Math.max(0, t.getInt("goal")), t.getBoolean("strays"));
            p.height = Math.max(0, t.getInt("height"));
            try {
                p.phase = Phase.valueOf(t.getString("phase").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                p.phase = p.height > 0 ? Phase.WORK : Phase.WALK;
            }
            p.down = t.contains("down") ? t.getString("down") : null;
            return p;
        }
    }
}
