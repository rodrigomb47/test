package com.factionclash.structure;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.Project;
import com.factionclash.faction.FactionType;
import com.factionclash.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Block plans for builder projects. Plans are deterministic so progress indices stay valid after reloads. */
public final class ProjectLayouts {
    private ProjectLayouts() {
    }

    public static Blueprint plan(Project p, BaseState base) {
        return switch (p.type) {
            case HOUSE -> house(p.origin, base.faction, doorSouth(p.origin, base));
            case STORAGE_EXPANSION -> storage(p.origin, base);
            case WATCHTOWER -> house(p.origin, base.faction, true);
        };
    }

    /** Houses north of the main road face south toward it; houses south of it face north. */
    private static boolean doorSouth(BlockPos lot, BaseState base) {
        return lot.getZ() < base.center.getZ();
    }

    /**
     * 7x7 expansion house: levelled ground, stone walls, log corners, plank floor and roof, four bedrolls, a door
     * and a torch. Ground preparation (clearing and gap filling) comes first in the plan.
     */
    public static Blueprint house(BlockPos origin, FactionType faction, boolean doorSouth) {
        Blueprint bp = new Blueprint(origin);
        BlockState wall = Blocks.COBBLESTONE.defaultBlockState();
        BlockState frame = faction.style.frame().defaultBlockState();
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        // ground preparation: fill the foundation layer, clear the volume
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                bp.set(x, -2, z, Blocks.DIRT.defaultBlockState());
            }
        }
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                bp.set(x, -1, z, planks);
            }
        }
        for (int y = 0; y <= 3; y++) {
            for (int x = 0; x <= 6; x++) {
                for (int z = 0; z <= 6; z++) {
                    boolean edge = x == 0 || x == 6 || z == 0 || z == 6;
                    boolean corner = (x == 0 || x == 6) && (z == 0 || z == 6);
                    if (edge) {
                        bp.set(x, y, z, corner ? frame : wall);
                    }
                }
            }
        }
        int dz = doorSouth ? 6 : 0;
        Direction dir = doorSouth ? Direction.SOUTH : Direction.NORTH;
        // leave the doorway open until the end so the builder is never trapped
        bp.blocks.remove(bp.rel(3, 0, dz));
        bp.blocks.remove(bp.rel(3, 1, dz));
        int bz = doorSouth ? 1 : 5;
        for (int x : new int[]{1, 2, 4, 5}) {
            bp.set(x, 0, bz, ModBlocks.BEDROLL.get().defaultBlockState());
        }
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                bp.set(x, 4, z, planks);
            }
        }
        bp.wallTorch(3, 2, doorSouth ? 5 : 1, dir.getOpposite());
        bp.door(3, 0, dz, dir);
        return bp;
    }

    /** One double chest in a reserved storage slot pair. */
    public static Blueprint storage(BlockPos first, BaseState base) {
        Blueprint bp = new Blueprint(first);
        int idx = base.storageExpansionSlots.indexOf(first);
        BlockPos second = idx >= 0 && idx + 1 < base.storageExpansionSlots.size()
                ? base.storageExpansionSlots.get(idx + 1) : first.north();
        Direction toSecond = Direction.getNearest(second.getX() - first.getX(), 0, second.getZ() - first.getZ());
        Direction facing = toSecond.getCounterClockWise();
        BlockState s = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
        bp.setAbs(first, s.setValue(ChestBlock.TYPE, ChestType.LEFT));
        bp.setAbs(second, s.setValue(ChestBlock.TYPE, ChestType.RIGHT));
        return bp;
    }
}
