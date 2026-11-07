package com.factionclash.work;

import com.factionclash.campaign.WorkplaceState;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Animal carer that works like a player: it goes into the pen through the gate and feeds two adults of a species
 * the food that species eats (vanilla breeding: wheat for cows and sheep, carrots/potatoes/beetroot for pigs, seeds
 * for chickens), so they breed and a real baby is born. It also shears sheep, milks cows with empty buckets and
 * picks up eggs and wool, and carries everything to storage. Pens are kept below a size cap.
 */
public class AnimalCarerJob extends PenJob {
    enum State { STORAGE, ENTER, WORK, EXIT, RETRIEVE }

    /** PROPOSED: at most this many animals (adults + babies) per species in one pen. */
    public static final int SPECIES_CAP = 8;

    private State state = State.STORAGE;
    private Animal target;
    private String action = "";
    private int timer;
    private final Map<UUID, Long> milkedAt = new HashMap<>();
    /** Escaped animal being lured back with its food, and the phase of that trip. */
    private Animal stray;
    private int strayPhase;
    private int strayTicks;
    private int gateOpenTicks;
    private final Map<UUID, Long> gaveUpOn = new HashMap<>();

    public AnimalCarerJob(FactionNpc npc) {
        super(npc);
    }

    @Override
    public String workplaceInfo() {
        WorkplaceState pen = pen();
        if (pen == null) {
            return "no pen";
        }
        StringBuilder sb = new StringBuilder("Pen at " + pen.origin.toShortString() + ":");
        for (Species sp : SPECIES) {
            List<? extends Animal> list = animals(pen, sp.type());
            if (!list.isEmpty()) {
                sb.append(" ").append(adults(list)).append("+").append(list.size() - adults(list)).append(" ").append(sp.name());
            }
        }
        return sb.toString();
    }

    @Override
    protected int keep(ItemStack s) {
        for (Species sp : SPECIES) {
            if (sp.food().contains(s.getItem())) {
                return 16;
            }
        }
        if (s.is(Items.BUCKET) || s.is(Items.SHEARS)) {
            return 4;
        }
        return 0;
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
            case STORAGE -> storage(pen);
            case ENTER -> {
                if (passGate(pen, true)) {
                    state = State.WORK;
                }
            }
            case WORK -> work(pen);
            case EXIT -> {
                if (passGate(pen, false)) {
                    state = State.STORAGE;
                }
            }
            case RETRIEVE -> retrieve(pen);
        }
    }

    /** A pen-species animal that wandered out (or a stray of one) near the pen that the carer has food for. */
    private Animal findStray(WorkplaceState pen) {
        AABB in = interior(pen).inflate(0.5);
        return level().getEntitiesOfClass(Animal.class, new AABB(pen.origin).inflate(32, 8, 32), a -> {
            Species sp = speciesOf(a);
            // only species the pen keeps - the starting cows and pigs, or whatever else lives in it - so the carer
            // does not chase every wild chicken around the base
            int kept = sp == null ? 0 : animals(pen, sp.type()).size();
            boolean penSpecies = kept > 0 || a.getType() == net.minecraft.world.entity.EntityType.COW
                    || a.getType() == net.minecraft.world.entity.EntityType.PIG;
            return sp != null && a.isAlive() && !a.isLeashed() && !in.contains(a.position()) && hasFoodFor(sp)
                    && penSpecies && kept < SPECIES_CAP
                    && now() - gaveUpOn.getOrDefault(a.getUUID(), -100000L) > 6000;
        }).stream().min(java.util.Comparator.comparingDouble(a -> a.distanceToSqr(Vec3.atCenterOf(pen.origin)))).orElse(null);
    }

    /**
     * Leads an escaped animal back like a player does: walks up to it holding its food so it follows (vanilla
     * tempting), walks to the gate, opens it, walks in with the animal behind, and closes the gate once it is in.
     */
    private void retrieve(WorkplaceState pen) {
        boolean giveUp = stray == null || !stray.isAlive() || ++strayTicks > 1200
                || stray.distanceToSqr(Vec3.atCenterOf(pen.origin)) > 40 * 40;
        if (!giveUp && interior(pen).inflate(0.5).contains(stray.position()) && strayPhase < 2) {
            stray = null; // it found its own way back
            state = insidePen(pen) ? State.WORK : State.STORAGE;
            return;
        }
        if (giveUp) {
            if (stray != null) {
                gaveUpOn.put(stray.getUUID(), now());
            }
            if (!npc.getBoundingBox().intersects(new AABB(pen.extent))) {
                gate(pen, false);
            }
            stray = null;
            strayPhase = 0;
            state = insidePen(pen) ? State.EXIT : State.STORAGE;
            return;
        }
        String name = stray.getType().getDescription().getString();
        tempt(stray);
        switch (strayPhase) {
            case 0 -> {
                status("Luring an escaped " + name + " with food", "Herding");
                if (goTo(stray.blockPosition(), 2.5, "the escaped " + name, 0.8)) {
                    strayPhase = 1;
                }
            }
            case 1 -> {
                status("Leading the " + name + " back to the pen", "Herding");
                // walk slowly enough for the animal to keep up; wait for it if it falls behind
                if (npc.distanceToSqr(stray) > 7 * 7) {
                    npc.getNavigation().stop();
                    if (npc.distanceToSqr(stray) > 10 * 10) {
                        strayPhase = 0;
                    }
                    return;
                }
                if (goTo(gateSide(pen, false), 1.0, "the pen gate", 0.6) && npc.distanceToSqr(stray) < 4 * 4) {
                    gate(pen, true);
                    strayPhase = 2;
                    gateOpenTicks = 0;
                }
            }
            case 2 -> {
                status("Leading the " + name + " into the pen", "Herding");
                boolean npcIn = insidePen(pen) && !npc.getBoundingBox().intersects(new AABB(pen.extent));
                boolean strayIn = interior(pen).contains(stray.position()) && !stray.getBoundingBox().intersects(new AABB(pen.extent));
                if (npcIn && strayIn) {
                    gate(pen, false);
                    npc.awardXp(1);
                    stray = null;
                    strayPhase = 0;
                    state = State.WORK;
                    return;
                }
                if (++gateOpenTicks > 300 && !npc.getBoundingBox().intersects(new AABB(pen.extent)) && !animalInGate(pen)) {
                    // it would not follow: shut the gate again rather than let the others out
                    gate(pen, false);
                    gaveUpOn.put(stray.getUUID(), now());
                    stray = null;
                    strayPhase = 0;
                    state = insidePen(pen) ? State.WORK : State.STORAGE;
                    return;
                }
                if (npc.distanceToSqr(stray) > 6 * 6) {
                    npc.getNavigation().stop();
                    return;
                }
                goTo(pen.origin.offset(0, 0, 1), 1.0, "the middle of the pen", 0.6);
            }
            default -> strayPhase = 0;
        }
    }

    private void storage(WorkplaceState pen) {
        if (insidePen(pen)) {
            state = State.EXIT;
            return;
        }
        if (!atStorage()) {
            return;
        }
        unloadNow();
        upgradeEquipment();
        for (Species sp : SPECIES) {
            for (net.minecraft.world.item.Item food : sp.food()) {
                int have = Inv.count(npc.inventory(), s -> s.is(food));
                if (have < 6) {
                    // leave seed stock for the farmer (seeds, and carrots/potatoes it replants) and a little wheat
                    int stored = com.factionclash.storage.FactionStorage.count(level(), base(), s -> s.is(food));
                    int spare = stored - feedReserve(food);
                    if (spare > 0) {
                        takeFromStorage(s -> s.is(food), Math.min(8 - have, spare));
                    }
                }
            }
        }
        if (Inv.count(npc.inventory(), s -> s.is(Items.BUCKET)) == 0) {
            takeFromStorage(s -> s.is(Items.BUCKET), 2);
        }
        if (Inv.count(npc.inventory(), s -> s.is(Items.SHEARS)) == 0) {
            takeFromStorage(s -> s.is(Items.SHEARS), 1);
        }
        Animal escaped = findStray(pen);
        if (escaped != null) {
            stray = escaped;
            strayPhase = 0;
            strayTicks = 0;
            state = State.RETRIEVE;
            return;
        }
        if (chooseTask(pen) == null) {
            status("Animals are fine for now", "Idle");
            waitFor(600);
            return;
        }
        state = State.ENTER;
    }

    /** Picks the next useful thing to do in the pen, or null if there is nothing (without moving). */
    private Animal chooseTask(WorkplaceState pen) {
        for (Species sp : SPECIES) {
            List<? extends Animal> list = animals(pen, sp.type());
            if (list.size() >= SPECIES_CAP) {
                continue;
            }
            List<? extends Animal> ready = list.stream().filter(a -> !a.isBaby() && a.getAge() == 0 && a.canFallInLove()).toList();
            boolean partnerInLove = list.stream().anyMatch(a -> !a.isBaby() && a.isInLove());
            if ((ready.size() >= 2 || (ready.size() >= 1 && partnerInLove)) && hasFoodFor(sp)) {
                action = "breed";
                return ready.get(0);
            }
        }
        if (Inv.count(npc.inventory(), s -> s.is(Items.SHEARS)) > 0) {
            for (Animal a : animals(pen, net.minecraft.world.entity.EntityType.SHEEP)) {
                if (a instanceof Sheep sheep && sheep.readyForShearing()) {
                    action = "shear";
                    return a;
                }
            }
        }
        if (Inv.count(npc.inventory(), s -> s.is(Items.BUCKET)) > 0) {
            for (Animal a : animals(pen, net.minecraft.world.entity.EntityType.COW)) {
                if (a instanceof Cow && !a.isBaby() && now() - milkedAt.getOrDefault(a.getUUID(), -100000L) > 6000) {
                    action = "milk";
                    return a;
                }
            }
        }
        return null;
    }

    /** PROPOSED: how much of a feed item stays in storage for the farmer (seed stock) and builders. */
    public static int feedReserve(net.minecraft.world.item.Item food) {
        if (food == Items.WHEAT_SEEDS) {
            return 24;
        }
        if (food == Items.CARROT || food == Items.POTATO || food == Items.BEETROOT) {
            return 4;
        }
        return 4; // wheat: the chef only bakes what is above its own larger reserve
    }

    private boolean hasFoodFor(Species sp) {
        return sp.food().stream().anyMatch(f -> Inv.count(npc.inventory(), s -> s.is(f)) > 0);
    }

    private void work(WorkplaceState pen) {
        if (target == null || !target.isAlive()) {
            target = chooseTask(pen);
            timer = 0;
        }
        if (target == null) {
            ItemEntity item = BlockWork.nearestItem(npc, interior(pen), s -> true);
            if (item != null && Inv.freeSlots(npc.inventory()) > 0) {
                status("Collecting eggs and wool", "Collecting");
                goTo(item.blockPosition(), 0.8, "items in the pen");
                return;
            }
            state = State.EXIT;
            return;
        }
        if (!goTo(target.blockPosition(), 1.8, "an animal")) {
            if (++timer > 200) {
                target = null; // animal keeps wandering off; choose again
            }
            return;
        }
        npc.getLookControl().setLookAt(target, 30F, 30F);
        switch (action) {
            case "breed" -> feed(target);
            case "shear" -> {
                if (target instanceof Sheep sheep && sheep.readyForShearing()) {
                    sheep.shear(SoundSource.PLAYERS);
                    npc.awardXp(1);
                }
            }
            case "milk" -> {
                if (Inv.consume(npc.inventory(), s -> s.is(Items.BUCKET), 1)) {
                    level().playSound(null, target.blockPosition(), SoundEvents.COW_MILK, SoundSource.PLAYERS, 1.0F, 1.0F);
                    Crafting.addAll(npc.inventory(), new ItemStack(Items.MILK_BUCKET));
                    milkedAt.put(target.getUUID(), now());
                }
            }
            default -> {
            }
        }
        npc.swing(InteractionHand.MAIN_HAND);
        target = null;
    }

    private void feed(Animal a) {
        if (feedLikePlayer(npc, a)) {
            status("Feeding " + a.getType().getDescription().getString() + " to breed them", "Breeding");
            npc.awardXp(1);
        }
    }

    /**
     * Feeds one adult exactly as a player would: one item of food that species accepts, only if it is an adult that
     * is ready to breed; the animal enters love mode and vanilla breeding does the rest. Returns true if fed.
     */
    public static boolean feedLikePlayer(FactionNpc npc, Animal a) {
        if (a.isBaby() || a.getAge() != 0 || !a.canFallInLove()) {
            return false;
        }
        for (ItemStack s : npc.inventory().getItems()) {
            if (!s.isEmpty() && a.isFood(s)) {
                s.shrink(1);
                npc.inventory().setChanged();
                a.setInLove(null);
                return true;
            }
        }
        return false;
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state.name());
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            state = State.STORAGE;
        }
        if (state == State.WORK || state == State.ENTER || state == State.RETRIEVE) {
            state = State.STORAGE;
        }
    }
}
