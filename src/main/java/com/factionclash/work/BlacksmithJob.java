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
    enum State { PLAN, FORGE, SMELT, REPAIR }

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
            case REPAIR -> repair();
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
        if (startRepair()) {
            state = State.REPAIR;
            timer = 0;
            return;
        }
        if (state == State.FORGE) {
            return; // the anvil broke: forging a new one
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

        List<FactionNpc> crew = membersNearBase();
        // 0. Replacements for broken and nearly used-up gear (with tool wear on): one new tool of each kind a worker
        //    lacks a fresh one of, unless the storehouse already has one. Iron when there is iron, else stone (the
        //    miners always bring cobblestone), so work never waits on scarce iron.
        if (com.factionclash.config.FCConfig.TOOL_WEAR.get()) {
            java.util.Map<ToolKind, Integer> lacking = new java.util.EnumMap<>(ToolKind.class);
            for (FactionNpc n : crew) {
                ToolKind k = n.getRole().tool;
                if (k != ToolKind.NONE && (!k.matches(n.tool()) || worn(n.tool()))) {
                    lacking.merge(k, 1, Integer::sum);
                }
            }
            for (var e : lacking.entrySet()) {
                ToolKind k = e.getKey();
                if (e.getValue() <= FactionStorage.count(level, base, x -> k.matches(x) && !worn(x))) {
                    continue;
                }
                Crafting.Recipe r = replacement(k, iron, cobble, sticksAvail, FactionStorage.count(level, base, x -> x.is(Items.STRING)));
                if (r != null) {
                    out.add(new Order(r, 1, "replacing a worn-out " + k.name().toLowerCase()));
                    for (Crafting.Input in : r.inputs()) {
                        if (in.filter().test(new ItemStack(Items.IRON_INGOT))) {
                            iron -= in.count();
                        } else if (in.filter().test(new ItemStack(Items.COBBLESTONE))) {
                            cobble -= in.count();
                        } else if (in.filter().test(new ItemStack(Items.STICK))) {
                            sticksAvail -= in.count();
                        }
                    }
                }
            }
        }

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

        List<FactionNpc> members = crew;
        // 2. Worker tool upgrades (miners first: better pickaxes unlock better ore). A wooden pickaxe harvests no
        //    iron ore, so it is replaced with stone as soon as there is cobblestone.
        long woodenMiners = members.stream().filter(n -> n.getRole() == NpcRole.MINER && ToolKind.tierOf(n.tool()) < 1).count();
        if (woodenMiners > 0 && cobble >= 3 && sticksAvail >= 2
                && FactionStorage.count(level, base, s -> ToolKind.PICKAXE.matches(s) && ToolKind.tierOf(s) >= 1) == 0) {
            out.add(new Order(Crafting.tool("stone pickaxe", Items.COBBLESTONE, 3, 2, Items.STONE_PICKAXE), 1, "miner upgrade (iron ore needs stone)"));
            cobble -= 3;
            sticksAvail -= 2;
        }
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
        // 4b. Shields for swordsmen (6 planks and an iron ingot, as in vanilla).
        long shieldless = members.stream().filter(n -> n.getRole() == NpcRole.SWORDSMAN && !n.hasShield()).count()
                - FactionStorage.count(level, base, s -> s.getItem() instanceof net.minecraft.world.item.ShieldItem);
        if (shieldless > 0 && iron >= 1 && sticksAvail >= 12) {
            int n = (int) Math.min(shieldless, Math.min(iron, 2));
            out.add(new Order(new Crafting.Recipe("shield", List.of(Crafting.of(ItemTags.PLANKS, 6, "Planks"), Crafting.of(Items.IRON_INGOT, 1)),
                    Items.SHIELD, 1), n, "swordsmen's shields"));
            iron -= n;
            sticksAvail -= n * 12;
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

    // ------------------------------------------------------------------ anvil repairs (vanilla rules)

    /** PROPOSED: the anvil is a 31-iron forging (3 iron blocks and 4 ingots), made again when it breaks. */
    static final Crafting.Recipe ANVIL = new Crafting.Recipe("anvil", List.of(Crafting.of(Items.IRON_INGOT, 31)), Items.ANVIL, 1);

    /**
     * Worth a trip to the anvil: at least a quarter worn, still repairable (vanilla "Too Expensive" at 40 levels),
     * and either enchanted or iron/diamond/netherite gear whose repair takes less material than making a new one.
     */
    public static boolean worthRepairing(ItemStack s) {
        if (!s.isDamageableItem() || s.getDamageValue() < s.getMaxDamage() / 4 || s.getMaxDamage() < 4) {
            return false;
        }
        int units = unitsToRepair(s);
        if (s.getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST, 0) + units >= 40) {
            return false;
        }
        if (s.isEnchanted()) {
            return true;
        }
        int craftCost = craftCost(s);
        return craftCost > 0 && units < craftCost;
    }

    /** Material units vanilla needs to fully repair it (each one restores a quarter of the durability). */
    static int unitsToRepair(ItemStack s) {
        int damage = s.getDamageValue(), quarter = Math.max(1, s.getMaxDamage() / 4), units = 0;
        while (damage > 0) {
            damage -= Math.min(damage, quarter);
            units++;
        }
        return units;
    }

    /** Material units in the item's recipe (iron, diamond or netherite gear and chainmail); 0 for anything else. */
    static int craftCost(ItemStack s) {
        Item i = s.getItem();
        boolean good = i instanceof net.minecraft.world.item.TieredItem t
                && (t.getTier() == net.minecraft.world.item.Tiers.IRON || t.getTier() == net.minecraft.world.item.Tiers.DIAMOND
                || t.getTier() == net.minecraft.world.item.Tiers.NETHERITE)
                || i instanceof ArmorItem a && (a.getMaterial().is(net.minecraft.world.item.ArmorMaterials.IRON)
                || a.getMaterial().is(net.minecraft.world.item.ArmorMaterials.DIAMOND)
                || a.getMaterial().is(net.minecraft.world.item.ArmorMaterials.NETHERITE)
                || a.getMaterial().is(net.minecraft.world.item.ArmorMaterials.CHAIN));
        if (!good) {
            return 0;
        }
        if (i instanceof ArmorItem a) {
            return switch (a.getEquipmentSlot()) {
                case HEAD -> 5;
                case CHEST -> 8;
                case LEGS -> 7;
                default -> 4;
            };
        }
        if (s.is(ItemTags.PICKAXES) || s.is(ItemTags.AXES)) {
            return 3;
        }
        if (s.is(ItemTags.SWORDS) || s.is(ItemTags.HOES)) {
            return 2;
        }
        return 1;
    }

    /**
     * Takes a worn item and its repair material from the storehouse when the faction can pay the levels (from the
     * experience its NPCs gathered). With the anvil gone, it forges a new one instead (the builder puts it back).
     */
    private boolean startRepair() {
        if (!com.factionclash.config.FCConfig.TOOL_WEAR.get()) {
            return false;
        }
        BaseState b = base();
        BlockPos anvil = b.point(BaseState.ANVIL);
        if (anvil == null) {
            return false;
        }
        if (!(level().getBlockState(anvil).getBlock() instanceof net.minecraft.world.level.block.AnvilBlock)) {
            if (level().getBlockState(anvil).isAir() && FactionStorage.count(level(), b, x -> x.is(Items.ANVIL)) == 0
                    && FactionStorage.count(level(), b, x -> x.is(Items.IRON_INGOT)) >= 31) {
                orders.add(new Order(ANVIL, 1, "the anvil broke"));
                gather(orders.get(0));
                state = State.FORGE;
                timer = 0;
            }
            return false;
        }
        ItemStack worn = FactionStorage.findBest(level(), b, BlacksmithJob::worthRepairing, ItemStack::getDamageValue);
        if (worn.isEmpty()) {
            return false;
        }
        ItemStack material = FactionStorage.findBest(level(), b, m -> worn.getItem().isValidRepairItem(worn, m), m -> 0);
        if (material.isEmpty()) {
            return false;
        }
        int units = unitsToRepair(worn);
        int cost = worn.getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST, 0) + units;
        FactionState f = data().faction(npc.getFaction());
        if (com.factionclash.logic.XpLevels.levelFor(f.xpBank) < cost) {
            return false;
        }
        ItemStack got = FactionStorage.extractExact(level(), b, worn);
        if (got.isEmpty()) {
            return false;
        }
        Crafting.addAll(npc.inventory(), got);
        takeFromStorage(m -> ItemStack.isSameItemSameComponents(m, material), units);
        return true;
    }

    /** At the anvil: the vanilla material repair, paid in levels, with the vanilla 12% chance to damage the anvil. */
    private void repair() {
        BaseState b = base();
        BlockPos anvil = b.point(BaseState.ANVIL);
        if (!goTo(b.point(BaseState.WORKSHOP_STAND), 2.0, "the anvil")) {
            return;
        }
        lookAt(anvil);
        ItemStack item = Inv.first(npc.inventory(), BlacksmithJob::worthRepairing);
        if (item.isEmpty() || !(level().getBlockState(anvil).getBlock() instanceof net.minecraft.world.level.block.AnvilBlock)) {
            state = State.PLAN; // whatever it carries goes back at the storehouse
            return;
        }
        status("Repairing " + item.getHoverName().getString() + " at the anvil", "Smithing");
        if (++timer < 40) {
            return;
        }
        timer = 0;
        FactionState f = data().faction(npc.getFaction());
        int[] used = {0};
        repairWithMaterial(item, npc.inventory(), used);
        int prior = item.getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST, 0);
        int cost = prior + used[0];
        if (used[0] > 0 && com.factionclash.logic.XpLevels.levelFor(f.xpBank) >= cost) {
            f.xpBank = com.factionclash.logic.XpLevels.spend(f.xpBank, cost);
            item.set(net.minecraft.core.component.DataComponents.REPAIR_COST, net.minecraft.world.inventory.AnvilMenu.calculateIncreasedRepairCost(prior));
            data().setDirty();
            npc.swing(InteractionHand.MAIN_HAND);
            npc.awardXp(2);
            net.minecraft.world.level.block.state.BlockState st = level().getBlockState(anvil);
            if (npc.getRandom().nextFloat() < 0.12F) {
                net.minecraft.world.level.block.state.BlockState worse = net.minecraft.world.level.block.AnvilBlock.damage(st);
                if (worse == null) {
                    level().removeBlock(anvil, false);
                    level().levelEvent(1029, anvil, 0); // the anvil breaks
                } else {
                    level().setBlock(anvil, worse, 2);
                    level().levelEvent(1030, anvil, 0);
                }
            } else {
                level().levelEvent(1030, anvil, 0);
            }
        }
        state = State.PLAN;
    }

    /**
     * Vanilla anvil material repair: each unit of the repair material restores a quarter of the durability. Uses
     * the material from {@code inv}; returns the item (repaired in place), with the units used in {@code used[0]}.
     */
    public static ItemStack repairWithMaterial(ItemStack item, net.minecraft.world.Container inv, int[] used) {
        int quarter = Math.min(item.getDamageValue(), item.getMaxDamage() / 4);
        while (quarter > 0) {
            ItemStack mat = Inv.first(inv, m -> item.getItem().isValidRepairItem(item, m));
            if (mat.isEmpty()) {
                break;
            }
            mat.shrink(1);
            item.setDamageValue(item.getDamageValue() - quarter);
            used[0]++;
            quarter = Math.min(item.getDamageValue(), item.getMaxDamage() / 4);
        }
        inv.setChanged();
        return item;
    }

    /** Under a quarter of its durability left. */
    static boolean worn(ItemStack s) {
        return s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() < s.getMaxDamage() / 4;
    }

    /**
     * The recipe for a new tool of this kind: iron if affordable, else stone, else wood (planks: the way out when a
     * base has no cobblestone yet and its miner no pickaxe to get any); bows, crossbows and rods from string.
     */
    static Crafting.Recipe replacement(ToolKind k, int iron, int cobble, int sticks, int string) {
        int mat = switch (k) {
            case PICKAXE, AXE -> 3;
            case HOE, SWORD -> 2;
            case SHOVEL -> 1;
            default -> 0;
        };
        int stickCount = k == ToolKind.SWORD || k == ToolKind.SHOVEL ? (k == ToolKind.SWORD ? 1 : 2) : 2;
        if (mat > 0) {
            if (sticks < stickCount) {
                return null;
            }
            boolean useIron = iron >= mat;
            if (!useIron && cobble < mat) {
                // wooden: planks for the head (the gather step turns logs into planks), sticks for the handle
                if (sticks < stickCount + mat * 2) {
                    return null;
                }
                Item wood = switch (k) {
                    case PICKAXE -> Items.WOODEN_PICKAXE;
                    case AXE -> Items.WOODEN_AXE;
                    case HOE -> Items.WOODEN_HOE;
                    case SWORD -> Items.WOODEN_SWORD;
                    default -> Items.WOODEN_SHOVEL;
                };
                return new Crafting.Recipe("wooden " + k.name().toLowerCase(),
                        List.of(Crafting.of(ItemTags.PLANKS, mat, "Planks"), Crafting.of(Items.STICK, stickCount)), wood, 1);
            }
            Item out = switch (k) {
                case PICKAXE -> useIron ? Items.IRON_PICKAXE : Items.STONE_PICKAXE;
                case AXE -> useIron ? Items.IRON_AXE : Items.STONE_AXE;
                case HOE -> useIron ? Items.IRON_HOE : Items.STONE_HOE;
                case SWORD -> useIron ? Items.IRON_SWORD : Items.STONE_SWORD;
                default -> useIron ? Items.IRON_SHOVEL : Items.STONE_SHOVEL;
            };
            return Crafting.tool((useIron ? "iron " : "stone ") + k.name().toLowerCase(), useIron ? Items.IRON_INGOT : Items.COBBLESTONE,
                    mat, stickCount, out);
        }
        return switch (k) {
            case BOW -> string >= 3 && sticks >= 3 ? Crafting.BOW : null;
            case ROD -> string >= 2 && sticks >= 3 ? new Crafting.Recipe("fishing rod",
                    List.of(Crafting.of(Items.STICK, 3), Crafting.of(Items.STRING, 2)), Items.FISHING_ROD, 1) : null;
            case CROSSBOW -> string >= 2 && sticks >= 3 && iron >= 2 ? new Crafting.Recipe("crossbow",
                    List.of(Crafting.of(Items.STICK, 3), Crafting.of(Items.STRING, 2), Crafting.of(Items.IRON_INGOT, 2)), Items.CROSSBOW, 1) : null;
            default -> null;
        };
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
        tag.putString("state", state == State.FORGE || state == State.REPAIR ? State.PLAN.name() : state.name());
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
