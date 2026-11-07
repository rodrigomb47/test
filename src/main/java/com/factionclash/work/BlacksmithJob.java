package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.npc.ToolKind;
import com.factionclash.storage.FactionStorage;
import com.factionclash.structure.MineLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Blacksmith (spec 10): plans useful batches from real needs (mine-upgrade pickaxes, worker tools, soldier
 * swords and armour, bows, torches), gathers real inputs, crafts at the workshop and delivers to storage.
 * Without a smelter it also smelts raw ore.
 */
public class BlacksmithJob extends Job {
    enum State { PLAN, FORGE, SMELT }

    public record Order(Crafting.Recipe recipe, int times, String reason) {
    }

    private State state = State.PLAN;
    private final List<Order> orders = new ArrayList<>();
    private int timer;
    private final SmelterJob smelting;
    private boolean smeltVisitedStation;

    public BlacksmithJob(FactionNpc npc) {
        super(npc);
        this.smelting = new SmelterJob(npc);
    }

    @Override
    public String workplaceInfo() {
        return "Workshop anvil and crafting table" + (orders.isEmpty() ? "" : ", orders: " + orders.stream()
                .map(o -> o.times() + "x " + o.recipe().name()).toList());
    }

    @Override
    protected void run() {
        if (base() == null) {
            status("No home base", "Idle");
            waitFor(400);
            return;
        }
        switch (state) {
            case PLAN -> plan();
            case FORGE -> forge();
            case SMELT -> {
                smelting.run();
                if (smelting.state == CookingJob.State.STATION) {
                    smeltVisitedStation = true;
                } else if (smeltVisitedStation) {
                    smeltVisitedStation = false;
                    state = State.PLAN;
                }
            }
        }
    }

    private void plan() {
        if (!atStorage()) {
            return;
        }
        if (unloadNow() == Unload.PARTIAL_FULL && Inv.freeSlots(npc.inventory()) < 4) {
            status("Storehouse full", "Waiting");
            waitFor(200);
            return;
        }
        orders.clear();
        orders.addAll(planOrders());
        if (!orders.isEmpty()) {
            for (Order o : orders) {
                gather(o);
            }
            state = State.FORGE;
            timer = 0;
            return;
        }
        boolean smelterAlive = data().faction(npc.getFaction()).living(NpcRole.SMELTER) > 0;
        if (!smelterAlive && FactionStorage.count(level(), base(), SmelterJob::isOre) > 0) {
            smelting.state = CookingJob.State.STORAGE;
            smeltVisitedStation = false;
            state = State.SMELT;
            return;
        }
        status("No smithing orders - waiting for materials or requests", "Idle");
        waitFor(600);
    }

    private void gather(Order o) {
        for (Crafting.Input in : o.recipe().inputs()) {
            int need = in.count() * o.times() - Inv.count(npc.inventory(), in.filter());
            if (need > 0) {
                int got = takeFromStorage(in.filter(), need);
                if (got < need && in.label().equals(Items.STICK.getDescription().getString())) {
                    int planks = (need - got + 3) / 4 * 2;
                    int p = takeFromStorage(s -> s.is(ItemTags.PLANKS), planks);
                    if (p < planks) {
                        takeFromStorage(s -> s.is(ItemTags.LOGS), (planks - p + 3) / 4);
                    }
                } else if (got < need && in.label().equals("Planks")) {
                    takeFromStorage(s -> s.is(ItemTags.LOGS), (need - got + 3) / 4);
                }
            }
        }
    }

    private void forge() {
        BlockPos stand = base().point(BaseState.WORKSHOP_STAND);
        if (!goTo(stand, 2.0, "the anvil")) {
            return;
        }
        lookAt(base().point(BaseState.ANVIL));
        if (orders.isEmpty()) {
            state = State.PLAN;
            return;
        }
        Order o = orders.get(0);
        status("Forging " + o.recipe().name() + " (" + o.reason() + ")", "Smithing");
        if (++timer < 40) {
            return;
        }
        timer = 0;
        if (Crafting.craftWithIntermediates(npc.inventory(), o.recipe(), 1) > 0) {
            npc.swing(InteractionHand.MAIN_HAND);
            level().playSound(null, base().point(BaseState.ANVIL), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.4F, 1.0F);
            npc.awardXp(2);
            if (o.times() > 1) {
                orders.set(0, new Order(o.recipe(), o.times() - 1, o.reason()));
            } else {
                orders.remove(0);
            }
        } else {
            orders.remove(0);
        }
        if (orders.isEmpty()) {
            state = State.PLAN;
        }
    }

    /** Builds this trip's batch from actual faction needs and actual stored materials. */
    List<Order> planOrders() {
        List<Order> out = new ArrayList<>();
        ServerLevel level = level();
        BaseState base = base();
        CampaignData data = data();
        int iron = FactionStorage.count(level, base, s -> s.is(Items.IRON_INGOT));
        int diamonds = FactionStorage.count(level, base, s -> s.is(Items.DIAMOND));
        int cobble = FactionStorage.count(level, base, s -> s.is(Items.COBBLESTONE));
        int sticksAvail = FactionStorage.count(level, base, s -> s.is(Items.STICK))
                + FactionStorage.count(level, base, s -> s.is(ItemTags.PLANKS)) * 2
                + FactionStorage.count(level, base, s -> s.is(ItemTags.LOGS)) * 8;

        // 1. Pickaxe needed to pay for the next mine upgrade.
        for (int id : base.workplaces) {
            WorkplaceState wp = data.workplace(id);
            if (wp == null || wp.type != WorkplaceState.Type.MINE || wp.tier >= 4 || wp.upgrading()) {
                continue;
            }
            int need = MineLayout.upgradePickTier(wp.tier + 1);
            Predicate<ItemStack> ok = s -> ToolKind.PICKAXE.matches(s) && ToolKind.tierOf(s) >= need;
            if (FactionStorage.count(level, base, ok) == 0 && sticksAvail >= 2) {
                if (need <= 1 && cobble >= 3) {
                    out.add(new Order(Crafting.tool("stone pickaxe", Items.COBBLESTONE, 3, 2, Items.STONE_PICKAXE), 1, "mine upgrade payment"));
                    cobble -= 3;
                    sticksAvail -= 2;
                } else if (need == 2 && iron >= 3) {
                    out.add(new Order(Crafting.tool("iron pickaxe", Items.IRON_INGOT, 3, 2, Items.IRON_PICKAXE), 1, "mine upgrade payment"));
                    iron -= 3;
                    sticksAvail -= 2;
                } else if (need == 3 && diamonds >= 3) {
                    out.add(new Order(Crafting.tool("diamond pickaxe", Items.DIAMOND, 3, 2, Items.DIAMOND_PICKAXE), 1, "mine upgrade payment"));
                    diamonds -= 3;
                    sticksAvail -= 2;
                }
            }
        }

        List<FactionNpc> members = membersNearBase();
        // 2. Worker tool upgrades (miners first: better pickaxes unlock better ore).
        long weakMiners = members.stream().filter(n -> n.getRole() == NpcRole.MINER && ToolKind.tierOf(n.tool()) < 2).count();
        if (weakMiners > 0 && iron >= 3 && sticksAvail >= 2
                && FactionStorage.count(level, base, s -> s.is(Items.IRON_PICKAXE)) == 0) {
            out.add(new Order(Crafting.tool("iron pickaxe", Items.IRON_INGOT, 3, 2, Items.IRON_PICKAXE), 1, "miner upgrade"));
            iron -= 3;
            sticksAvail -= 2;
        }
        long weakMinersDiamond = members.stream().filter(n -> n.getRole() == NpcRole.MINER && ToolKind.tierOf(n.tool()) < 3).count();
        if (weakMinersDiamond > 0 && diamonds >= 6 && sticksAvail >= 2
                && FactionStorage.count(level, base, s -> s.is(Items.DIAMOND_PICKAXE)) == 0) {
            out.add(new Order(Crafting.tool("diamond pickaxe", Items.DIAMOND, 3, 2, Items.DIAMOND_PICKAXE), 1, "miner upgrade"));
            diamonds -= 3;
            sticksAvail -= 2;
        }
        // 3. Soldiers' swords.
        long weakSwords = members.stream().filter(n -> (n.getRole() == NpcRole.SWORDSMAN || n.getRole() == NpcRole.SCOUT)
                && ToolKind.tierOf(n.tool()) < 2).count()
                - FactionStorage.count(level, base, s -> s.is(Items.IRON_SWORD) || s.is(Items.DIAMOND_SWORD));
        if (weakSwords > 0 && iron >= 2) {
            int n = (int) Math.min(weakSwords, Math.min(iron / 2, sticksAvail));
            if (n > 0) {
                out.add(new Order(Crafting.tool("iron sword", Items.IRON_INGOT, 2, 1, Items.IRON_SWORD), n, "soldier swords"));
                iron -= n * 2;
                sticksAvail -= n;
            }
        }
        // 4. Soldiers' armour, most protective pieces first, never leaving fewer than 3 iron for tools.
        record Piece(EquipmentSlot slot, Item item, int cost, String name) {
        }
        for (Piece p : List.of(new Piece(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 8, "iron chestplate"),
                new Piece(EquipmentSlot.LEGS, Items.IRON_LEGGINGS, 7, "iron leggings"),
                new Piece(EquipmentSlot.HEAD, Items.IRON_HELMET, 5, "iron helmet"),
                new Piece(EquipmentSlot.FEET, Items.IRON_BOOTS, 4, "iron boots"))) {
            long lacking = members.stream().filter(n -> n.getRole().combatant && armorDefense(n, p.slot()) < defenseOf(p.item())).count()
                    - FactionStorage.count(level, base, s -> s.is(p.item()));
            int n = (int) Math.min(lacking, Math.max(0, (iron - 3) / p.cost()));
            if (n > 0) {
                out.add(new Order(Crafting.tool(p.name(), Items.IRON_INGOT, p.cost(), 0, p.item()), n, "soldier armour"));
                iron -= n * p.cost();
            }
        }
        // 5. Bows for archers that lack one.
        long bowless = members.stream().filter(n -> n.getRole() == NpcRole.ARCHER && !(n.tool().getItem() instanceof BowItem)).count()
                - FactionStorage.count(level, base, s -> s.getItem() instanceof BowItem);
        int string = FactionStorage.count(level, base, s -> s.is(Items.STRING));
        if (bowless > 0 && string >= 3 && sticksAvail >= 3) {
            out.add(new Order(Crafting.BOW, (int) Math.min(bowless, string / 3), "archer bows"));
        }
        // 6a. Keep a working stock of sticks for tools, torches and recruitment payments.
        if (FactionStorage.count(level, base, s -> s.is(Items.STICK)) < 16
                && FactionStorage.count(level, base, s -> s.is(ItemTags.LOGS)) >= 4) {
            out.add(new Order(Crafting.STICKS, 6, "stick stock"));
        }
        // 6b. Bone meal from bones (skeleton loot) for the woodcutter's saplings.
        int bones = FactionStorage.count(level, base, s -> s.is(Items.BONE));
        if (bones > 0 && FactionStorage.count(level, base, s -> s.is(Items.BONE_MEAL)) < 16) {
            out.add(new Order(new Crafting.Recipe("bone meal", List.of(Crafting.of(Items.BONE, 1)), Items.BONE_MEAL, 3),
                    Math.min(bones, 6), "bone meal for saplings"));
        }
        // 6. Torches for mines and lighting.
        int coal = FactionStorage.count(level, base, Crafting.FUEL_COAL);
        if (FactionStorage.count(level, base, s -> s.is(Items.TORCH)) < 16 && coal >= 2 && sticksAvail >= 2) {
            out.add(new Order(Crafting.TORCHES, Math.min(4, coal / 2), "torch stock"));
        }
        // 6c. Golden apples for soldiers' worst fights (8 gold ingots + 1 apple), one per two soldiers.
        long soldiers = members.stream().filter(n -> n.getRole().combatant).count();
        int gold = FactionStorage.count(level, base, s -> s.is(Items.GOLD_INGOT));
        if (gold >= 8 && FactionStorage.count(level, base, s -> s.is(Items.APPLE)) > 0
                && FactionStorage.count(level, base, s -> s.is(Items.GOLDEN_APPLE)) < soldiers / 2) {
            out.add(new Order(new Crafting.Recipe("golden apple", List.of(Crafting.of(Items.GOLD_INGOT, 8), Crafting.of(Items.APPLE, 1)),
                    Items.GOLDEN_APPLE, 1), 1, "golden apples for soldiers"));
        }
        // 7. Diamond swords once diamonds accumulate.
        long nonDiamond = members.stream().filter(n -> n.getRole() == NpcRole.SWORDSMAN && ToolKind.tierOf(n.tool()) < 3).count()
                - FactionStorage.count(level, base, s -> s.is(Items.DIAMOND_SWORD));
        if (nonDiamond > 0 && diamonds >= 5) {
            out.add(new Order(Crafting.tool("diamond sword", Items.DIAMOND, 2, 1, Items.DIAMOND_SWORD), 1, "elite soldier"));
        }
        return out;
    }

    private static int armorDefense(FactionNpc n, EquipmentSlot slot) {
        ItemStack s = n.getItemBySlot(slot);
        return s.getItem() instanceof ArmorItem a ? a.getDefense() : 0;
    }

    private static int defenseOf(Item item) {
        return item instanceof ArmorItem a ? a.getDefense() : 0;
    }

    private List<FactionNpc> membersNearBase() {
        BaseState b = base();
        BlockPos min = b.point(BaseState.MIN), max = b.point(BaseState.MAX);
        AABB box = min == null ? new AABB(b.center).inflate(48) : new AABB(min.getX(), min.getY() - 32, min.getZ(),
                max.getX() + 1, max.getY() + 32, max.getZ() + 1).inflate(16);
        FactionState f = data().faction(npc.getFaction());
        return level().getEntitiesOfClass(FactionNpc.class, box, n -> n.isAlive() && n.getFaction() == npc.getFaction()
                && f.roster.containsKey(n.getUUID()));
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state == State.FORGE ? State.PLAN.name() : state.name());
        CompoundTag sm = new CompoundTag();
        smelting.save(sm, provider);
        tag.put("smelting", sm);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            state = State.PLAN;
        }
        smelting.load(tag.getCompound("smelting"), provider);
    }
}
