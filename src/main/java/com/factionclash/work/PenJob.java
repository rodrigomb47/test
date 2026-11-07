package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Shared behaviour for workers who go into the animal pen: they open the fence gate, step through and close it
 * behind them, as a player does to keep the animals in.
 */
public abstract class PenJob extends Job {
    /** Species kept in pens and the vanilla food that breeds them. */
    public record Species(EntityType<? extends Animal> type, String name, List<Item> food) {
    }

    public static final List<Species> SPECIES = List.of(
            new Species(EntityType.COW, "cows", List.of(Items.WHEAT)),
            new Species(EntityType.SHEEP, "sheep", List.of(Items.WHEAT)),
            new Species(EntityType.PIG, "pigs", List.of(Items.CARROT, Items.POTATO, Items.BEETROOT)),
            new Species(EntityType.CHICKEN, "chickens", List.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS)));

    private int gatePhase;
    private int gateTicks;

    protected PenJob(FactionNpc npc) {
        super(npc);
    }

    /** The base's pen (not claimed: carers and butchers share it). */
    protected WorkplaceState pen() {
        BaseState b = base();
        if (b == null) {
            return null;
        }
        CampaignData data = data();
        for (int id : b.workplaces) {
            WorkplaceState w = data.workplace(id);
            if (w != null && w.type == WorkplaceState.Type.PEN) {
                return w;
            }
        }
        return null;
    }

    protected static AABB interior(WorkplaceState pen) {
        BlockPos c = pen.origin;
        return new AABB(c.getX() - 4, c.getY() - 1, c.getZ() - 2, c.getX() + 5, c.getY() + 3, c.getZ() + 3);
    }

    protected boolean insidePen(WorkplaceState pen) {
        return interior(pen).contains(npc.position());
    }

    protected List<? extends Animal> animals(WorkplaceState pen, EntityType<? extends Animal> type) {
        return level().getEntities(type, interior(pen).inflate(0.5), Animal::isAlive);
    }

    protected static long adults(List<? extends Animal> list) {
        return list.stream().filter(a -> !a.isBaby()).count();
    }

    private Direction inward(WorkplaceState pen) {
        BlockPos d = pen.origin.subtract(pen.extent);
        return Math.abs(d.getX()) > Math.abs(d.getZ()) ? (d.getX() > 0 ? Direction.EAST : Direction.WEST)
                : (d.getZ() > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    private void setGate(WorkplaceState pen, boolean open) {
        BlockState s = level().getBlockState(pen.extent);
        if (s.getBlock() instanceof FenceGateBlock && s.getValue(FenceGateBlock.OPEN) != open) {
            level().setBlock(pen.extent, s.setValue(FenceGateBlock.OPEN, open), Block.UPDATE_ALL);
            level().playSound(null, pen.extent, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    /**
     * Moves the NPC through the gate to the requested side. Returns true once there. Like a careful player it waits
     * for animals to move away from the gate before opening it, and closes it as soon as it has stepped through (it
     * stays open at most a few seconds if something goes wrong).
     */
    protected boolean passGate(WorkplaceState pen, boolean wantInside) {
        if (insidePen(pen) == wantInside && gatePhase == 0) {
            return true;
        }
        Direction in = inward(pen);
        BlockPos near = pen.extent.relative(wantInside ? in.getOpposite() : in);
        BlockPos far = pen.extent.relative(wantInside ? in : in.getOpposite());
        if (gatePhase == 0) {
            if (goTo(near, 1.0, "the pen gate")) {
                if (animalNearGate(pen) && ++gateTicks < 200) {
                    status("Waiting for the animals to move away from the gate", "Waiting");
                    return false;
                }
                setGate(pen, true);
                gatePhase = 1;
                gateTicks = 0;
            }
            return false;
        }
        boolean through = insidePen(pen) == wantInside && !npc.getBoundingBox().intersects(new AABB(pen.extent));
        boolean arrived = through || goTo(far, 0.7, "the pen gate");
        if (arrived || ++gateTicks > 100) {
            if (npc.getBoundingBox().intersects(new AABB(pen.extent)) || (animalInGate(pen) && gateTicks < 200)) {
                return false; // never shut the gate on itself or on an animal standing in it
            }
            setGate(pen, false);
            gatePhase = 0;
            gateTicks = 0;
            npc.navigator().reset();
            return insidePen(pen) == wantInside;
        }
        return false;
    }

    /** True if a penned animal stands right next to the gate (it would slip out when it opens). */
    protected boolean animalNearGate(WorkplaceState pen) {
        return !level().getEntitiesOfClass(Animal.class, new AABB(pen.extent).inflate(1.5, 1.0, 1.5),
                a -> a.isAlive() && !a.isLeashed()).isEmpty();
    }

    protected boolean animalInGate(WorkplaceState pen) {
        return !level().getEntitiesOfClass(Animal.class, new AABB(pen.extent), Animal::isAlive).isEmpty();
    }

    /** The cell just outside ({@code inside=false}) or just inside the gate. */
    protected BlockPos gateSide(WorkplaceState pen, boolean inside) {
        Direction in = inward(pen);
        return pen.extent.relative(inside ? in : in.getOpposite());
    }

    /** Species entry for an animal, or null if pens do not keep it. */
    protected static Species speciesOf(Animal a) {
        for (Species sp : SPECIES) {
            if (sp.type() == a.getType()) {
                return sp;
            }
        }
        return null;
    }

    /**
     * Vanilla tempting, as when a player holds an animal's food: the animal walks toward the holder until it is
     * within 2.5 blocks (TemptGoal behaviour), up to 10 blocks away.
     */
    protected void tempt(Animal a) {
        double d = a.distanceToSqr(npc);
        a.getLookControl().setLookAt(npc, a.getMaxHeadYRot() + 20, a.getMaxHeadXRot());
        if (d < 6.25) {
            a.getNavigation().stop();
        } else if (d < 100) {
            a.getNavigation().moveTo(npc, a instanceof net.minecraft.world.entity.animal.Pig ? 1.2 : 1.25);
        }
    }

    /** Opens or closes the pen gate directly (used when leading an animal in). */
    protected void gate(WorkplaceState pen, boolean open) {
        setGate(pen, open);
    }

    @Override
    public void onPause() {
        super.onPause();
        WorkplaceState pen = pen();
        if (pen != null && gatePhase != 0) {
            setGate(pen, false);
            gatePhase = 0;
        }
    }
}
