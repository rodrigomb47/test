package com.factionclash.work;

import com.factionclash.campaign.WorkplaceState;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.ToolKind;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Comparator;
import java.util.List;

/**
 * Butcher that works like a player (spec 8): only when a species in the pen has more adults than the breeding stock
 * it must keep, it goes in, kills one surplus adult with real melee hits from its weapon and picks up the drops.
 * Babies and animals in love are never touched, and the breeding stock is never reduced below the keep level.
 */
public class ButcherJob extends PenJob {
    enum State { STORAGE, ENTER, HUNT, COLLECT, EXIT }

    /** PROPOSED: adults of each species that are always kept for breeding. */
    public static final int BREEDING_STOCK = 4;

    private State state = State.STORAGE;
    private Animal target;
    private int attackCooldown;
    private int collectTicks;

    public ButcherJob(FactionNpc npc) {
        super(npc);
    }

    @Override
    public String workplaceInfo() {
        WorkplaceState pen = pen();
        return pen == null ? "no pen" : "Pen at " + pen.origin.toShortString() + ", keeps " + BREEDING_STOCK + " breeding adults per species";
    }

    /** A surplus adult to slaughter, or null. Never babies, never animals in love, never below the breeding stock. */
    private Animal surplus(WorkplaceState pen) {
        for (Species sp : SPECIES) {
            List<? extends Animal> list = animals(pen, sp.type());
            long adults = adults(list);
            Animal pick = list.stream().filter(a -> canCull(a, adults))
                    .min(Comparator.comparingDouble(npc::distanceToSqr)).orElse(null);
            if (pick != null) {
                return pick;
            }
        }
        return null;
    }

    /** The culling rule: an adult, not breeding, and only while its species has more adults than the breeding stock. */
    public static boolean canCull(Animal a, long adultsOfSpecies) {
        return a.isAlive() && !a.isBaby() && !a.isInLove() && adultsOfSpecies > BREEDING_STOCK;
    }

    @Override
    protected void run() {
        WorkplaceState pen = pen();
        if (pen == null || base() == null) {
            status("No animal pen", "Idle");
            waitFor(400);
            return;
        }
        BlockWork.pickUp(npc, s -> true);
        switch (state) {
            case STORAGE -> {
                if (insidePen(pen)) {
                    state = State.EXIT;
                    return;
                }
                if (!atStorage()) {
                    return;
                }
                unloadNow();
                upgradeEquipment();
                if (!ToolKind.SWORD.matches(npc.tool()) && !ToolKind.AXE.matches(npc.tool())) {
                    status("No weapon to butcher with", "Waiting");
                    waitFor(400);
                    return;
                }
                if (surplus(pen) == null) {
                    status("No surplus livestock - breeding stock is kept", "Idle");
                    waitFor(600);
                    return;
                }
                state = State.ENTER;
            }
            case ENTER -> {
                if (passGate(pen, true)) {
                    state = State.HUNT;
                    target = null;
                }
            }
            case HUNT -> hunt(pen);
            case COLLECT -> {
                ItemEntity item = BlockWork.nearestItem(npc, interior(pen), s -> true);
                if (item == null || ++collectTicks > 200 || Inv.freeSlots(npc.inventory()) == 0) {
                    state = surplus(pen) != null && Inv.freeSlots(npc.inventory()) > 2 ? State.HUNT : State.EXIT;
                    target = null;
                    return;
                }
                status("Collecting the meat and hides", "Collecting");
                goTo(item.blockPosition(), 0.8, "the drops");
            }
            case EXIT -> {
                if (passGate(pen, false)) {
                    state = State.STORAGE;
                }
            }
        }
    }

    private void hunt(WorkplaceState pen) {
        if (target == null || !target.isAlive()) {
            if (target != null) {
                // the last target died: collect its drops before anything else
                target = null;
                collectTicks = 0;
                state = State.COLLECT;
                return;
            }
            target = surplus(pen);
            if (target == null) {
                state = State.EXIT;
                return;
            }
        }
        if (target.isBaby() || target.isInLove()) {
            target = null; // rules changed while hunting (it grew up / is breeding): leave it alone
            return;
        }
        status("Butchering surplus " + target.getType().getDescription().getString(), "Butchering");
        goTo(target.blockPosition(), 1.5, "the surplus animal");
        npc.getLookControl().setLookAt(target, 30F, 30F);
        if (attackCooldown > 0) {
            attackCooldown--;
        } else if (npc.distanceToSqr(target) <= 6.25) {
            npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            npc.doHurtTarget(target);
            attackCooldown = 12;
            if (!target.isAlive()) {
                npc.awardXp(2);
            }
        }
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state.name());
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        state = State.STORAGE;
    }
}
