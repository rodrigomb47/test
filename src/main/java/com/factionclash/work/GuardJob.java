package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignManager;
import com.factionclash.campaign.FactionState;
import com.factionclash.npc.FactionNpc;
import com.factionclash.storage.FactionStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Swordsmen and archers (spec 14): patrol the base, pick up loot from defeated enemies, collect equipment
 * upgrades, and take part in raids that physically travel to a known target, destroy its heart and carry loot home.
 */
public class GuardJob extends Job {
    enum State { PATROL, STORAGE, RAID_TRAVEL, RAID_ASSAULT, RAID_LOOT, RAID_RETURN }

    private State state = State.STORAGE;
    private BlockPos post;
    private int timer;
    private long lastStorage;
    private int assault;

    public GuardJob(FactionNpc npc) {
        super(npc);
    }

    @Override
    public String workplaceInfo() {
        FactionState f = data().faction(npc.getFaction());
        if (isRaider(f)) {
            BaseState t = data().base(f.raidTarget);
            return "Raid on " + (t == null ? "?" : t.faction.displayName + " at " + t.center.toShortString()) + " (" + state + ")";
        }
        return "Guard post " + (post == null ? "none" : post.toShortString());
    }

    private boolean isRaider(FactionState f) {
        return f.raidTarget >= 0 && f.raiders.contains(npc.getUUID());
    }

    @Override
    protected void run() {
        BaseState home = base();
        if (home == null) {
            status("No home base", "Idle");
            waitFor(400);
            return;
        }
        pickUpLoot();
        FactionState f = data().faction(npc.getFaction());
        if (isRaider(f) && state != State.RAID_TRAVEL && state != State.RAID_ASSAULT && state != State.RAID_LOOT
                && state != State.RAID_RETURN) {
            state = State.RAID_TRAVEL;
        }
        if (!isRaider(f) && (state == State.RAID_TRAVEL || state == State.RAID_ASSAULT || state == State.RAID_LOOT)) {
            state = State.RAID_RETURN;
        }
        switch (state) {
            case STORAGE -> {
                if (!atStorage()) {
                    return;
                }
                unloadNow();
                upgradeEquipment();
                if (Inv.count(npc.inventory(), x -> x.is(net.minecraft.world.item.Items.GOLDEN_APPLE)) == 0) {
                    takeFromStorage(x -> x.is(net.minecraft.world.item.Items.GOLDEN_APPLE), 1); // for a bad fight
                }
                stockClimbingBlocks();
                lastStorage = now();
                state = State.PATROL;
                post = null;
            }
            case PATROL -> patrol(home);
            case RAID_TRAVEL -> raidTravel(f);
            case RAID_ASSAULT -> raidAssault(f);
            case RAID_LOOT -> raidLoot(f);
            case RAID_RETURN -> {
                if (!atStorage()) {
                    status("Returning home from the raid", "Returning");
                    return;
                }
                unloadNow();
                f.raiders.remove(npc.getUUID());
                data().setDirty();
                state = State.PATROL;
            }
        }
    }

    @Override
    protected int keep(net.minecraft.world.item.ItemStack stack) {
        if (CLIMBING_BLOCKS.contains(stack.getItem())) {
            return CARRIED_BLOCKS;
        }
        return stack.is(net.minecraft.world.item.Items.GOLDEN_APPLE) ? 1 : 0;
    }

    /** PROPOSED: blocks a soldier carries to build itself up to an enemy standing out of reach (as a player carries a stack). */
    static final int CARRIED_BLOCKS = 8;
    /** PROPOSED: it only takes them while the storehouse keeps at least this many of that block for the builders. */
    static final int STOCK_FLOOR = 64;
    static final java.util.Set<net.minecraft.world.item.Item> CLIMBING_BLOCKS = java.util.Set.of(net.minecraft.world.item.Items.DIRT,
            net.minecraft.world.item.Items.COBBLESTONE, net.minecraft.world.item.Items.COBBLED_DEEPSLATE);

    /**
     * Like a player heading out with a stack of blocks: a few blocks of dirt or cobblestone to build itself up a block or
     * two to an enemy standing out of reach ({@link com.factionclash.combat.Pursuit}). Only from a storehouse with
     * plenty of that block, so builders and the blacksmith never go short.
     */
    private void stockClimbingBlocks() {
        int have = Inv.count(npc.inventory(), x -> CLIMBING_BLOCKS.contains(x.getItem()));
        for (net.minecraft.world.item.Item item : List.of(net.minecraft.world.item.Items.DIRT, net.minecraft.world.item.Items.COBBLESTONE,
                net.minecraft.world.item.Items.COBBLED_DEEPSLATE)) {
            if (have >= CARRIED_BLOCKS) {
                return;
            }
            int stock = FactionStorage.count(level(), base(), x -> x.is(item));
            if (stock - (CARRIED_BLOCKS - have) >= STOCK_FLOOR) {
                have += takeFromStorage(x -> x.is(item), CARRIED_BLOCKS - have);
            }
        }
    }

    private void patrol(BaseState home) {
        if (now() - lastStorage > 6000 || (cargoCount() > 0 && Inv.freeSlots(npc.inventory()) < 6)) {
            state = State.STORAGE;
            return;
        }
        if (post == null) {
            post = choosePost(home);
            timer = 0;
        }
        if (!goTo(post, 2.0, "guard post")) {
            return;
        }
        status("On guard at " + post.toShortString(), "Guarding");
        if (++timer > 160 + level().random.nextInt(200)) {
            post = null;
        }
    }

    private BlockPos choosePost(BaseState b) {
        List<BlockPos> posts = new ArrayList<>();
        for (String k : List.of(BaseState.GATE_WEST, BaseState.GATE_EAST, BaseState.SPAWN, BaseState.STATION_STAND, BaseState.STAGING)) {
            BlockPos p = b.point(k);
            if (p != null) {
                posts.add(k.startsWith("gate") ? p.offset(Integer.signum(b.center.getX() - p.getX()) * 2, 0, 0) : p);
            }
        }
        if (posts.isEmpty()) {
            return b.center;
        }
        return posts.get(level().random.nextInt(posts.size()));
    }

    private void pickUpLoot() {
        if (npc.tickCount % 10 != 0) {
            return;
        }
        for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, npc.getBoundingBox().inflate(2.0), ItemEntity::isAlive)) {
            ItemStack s = item.getItem();
            int room = Inv.room(npc.inventory(), s);
            if (room <= 0) {
                continue;
            }
            int take = Math.min(room, s.getCount());
            Crafting.addAll(npc.inventory(), s.split(take));
            if (s.isEmpty()) {
                item.discard();
            } else {
                item.setItem(s);
            }
            npc.take(item, take);
        }
    }

    private void raidTravel(FactionState f) {
        BaseState target = data().base(f.raidTarget);
        if (target == null) {
            state = State.RAID_RETURN;
            return;
        }
        if (target.destroyed) {
            state = State.RAID_LOOT;
            return;
        }
        if (npc.getHealth() < npc.getMaxHealth() * 0.35F) {
            f.raiders.remove(npc.getUUID());
            state = State.RAID_RETURN;
            return;
        }
        if (f.raidMusteredAt == 0 && f.raidRally != Long.MIN_VALUE) {
            // muster: gather at the rally point and go in together, not one by one
            BlockPos rally = BlockPos.of(f.raidRally);
            if (level().isLoaded(rally)) {
                rally = level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rally);
            }
            if (!goTo(rally, 6.0, "the rally point")) {
                status("Marching to the rally point before " + target.faction.displayName, "Raiding");
                return;
            }
            int here = 0;
            for (java.util.UUID u : f.raiders) {
                if (level().getEntity(u) instanceof FactionNpc r && r.blockPosition().closerThan(rally, 12)) {
                    here++;
                }
            }
            if (here * 10 >= f.raiders.size() * 7 || now() - f.raidStartedAt > 6000) {
                f.raidMusteredAt = now();
                data().setDirty();
                com.factionclash.util.Messages.toFaction(level().getServer(), npc.getFaction(), net.minecraft.network.chat.Component.literal(
                        "The raid party (" + here + " of " + f.raiders.size() + ") has gathered - assault on " + target.faction.displayName + "!"));
            } else {
                status("At the rally point, waiting for the raid party (" + here + "/" + f.raiders.size() + ")", "Raiding");
                return;
            }
        }
        if (goTo(target.point(BaseState.HEART), 3.5, "the raid target")) {
            state = State.RAID_ASSAULT;
            assault = 0;
        } else {
            status("Marching on " + target.faction.displayName + " at " + target.center.toShortString(), "Raiding");
        }
    }

    private void raidAssault(FactionState f) {
        BaseState target = data().base(f.raidTarget);
        if (target == null || target.destroyed) {
            state = State.RAID_LOOT;
            return;
        }
        BlockPos heart = target.point(BaseState.HEART);
        if (!com.factionclash.npc.NpcNavigator.within(npc, heart, 4.0)) {
            state = State.RAID_TRAVEL;
            return;
        }
        lookAt(heart);
        assault++;
        level().destroyBlockProgress(npc.getId(), heart, Math.min(9, assault / 40));
        status("Attacking the " + target.faction.displayName + " heart (" + Math.min(100, assault * 100 / 400) + "%)", "Assaulting");
        if (assault % 20 == 0) {
            npc.swing(InteractionHand.MAIN_HAND);
        }
        if (assault >= 400) {
            level().destroyBlockProgress(npc.getId(), heart, -1);
            CampaignManager.destroyBase(level(), target, npc.getFaction(), npc.npcName());
            state = State.RAID_LOOT;
        }
    }

    private void raidLoot(FactionState f) {
        BaseState target = data().base(f.raidTarget);
        if (target == null || !target.isSettlement() || target.allStoragePositions().isEmpty()) {
            state = State.RAID_RETURN;
            return;
        }
        if (!goTo(target.point(BaseState.STORAGE_STAND), 2.5, "the enemy storehouse")) {
            return;
        }
        int taken = 0;
        for (int i = 0; i < 6 && Inv.freeSlots(npc.inventory()) > 1; i++) {
            List<ItemStack> got = FactionStorage.extract(level(), target, s -> true, 64);
            for (ItemStack s : got) {
                ItemStack rest = Crafting.addAll(npc.inventory(), s);
                if (!rest.isEmpty()) {
                    FactionStorage.insert(level(), target, rest);
                }
                taken += s.getCount();
            }
            if (got.isEmpty()) {
                break;
            }
        }
        npc.swing(InteractionHand.MAIN_HAND);
        status("Looted " + taken + " items", "Looting");
        state = State.RAID_RETURN;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (state == State.PATROL) {
            post = null;
        }
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state.name());
        tag.putInt("assault", assault);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            state = State.STORAGE;
        }
        assault = tag.getInt("assault");
    }
}
