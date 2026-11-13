package com.factionclash.campaign;

import com.factionclash.config.FCConfig;
import com.factionclash.logic.FoodPayment;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.npc.ToolKind;
import com.factionclash.storage.FactionStorage;
import com.factionclash.util.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Recruitment (spec 9): consumes food by its normal hunger value plus the role's equipment, charged through
 * exactly one route (the finished item if stored, otherwise its crafting materials). Everything is checked
 * before anything is taken, so a failed recruitment costs nothing.
 */
public final class Recruitment {
    public record Result(FactionNpc npc, String message) {
        public boolean ok() {
            return npc != null;
        }
    }

    private Recruitment() {
    }

    /** Food that counts toward recruitment: normal food, excluding harmful items and combat golden apples. */
    public static boolean isRecruitFood(ItemStack s) {
        FoodProperties f = s.get(DataComponents.FOOD);
        if (f == null || f.nutrition() <= 0) {
            return false;
        }
        return !(s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE) || s.is(Items.ROTTEN_FLESH)
                || s.is(Items.SPIDER_EYE) || s.is(Items.POISONOUS_POTATO) || s.is(Items.PUFFERFISH)
                || s.is(Items.SUSPICIOUS_STEW) || s.is(Items.CHORUS_FRUIT) || s.is(Items.CHICKEN));
    }

    public static int nutrition(ItemStack s) {
        FoodProperties f = s.get(DataComponents.FOOD);
        return f == null ? 0 : f.nutrition();
    }

    public static List<FoodPayment.Option> foodOptions(ServerLevel level, BaseState base) {
        Map<String, int[]> agg = new LinkedHashMap<>();
        for (Container c : FactionStorage.all(level, base)) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && isRecruitFood(s)) {
                    String key = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
                    int[] v = agg.computeIfAbsent(key, k -> new int[]{nutrition(s), 0});
                    v[1] += s.getCount();
                }
            }
        }
        List<FoodPayment.Option> out = new ArrayList<>();
        agg.forEach((k, v) -> out.add(new FoodPayment.Option(k, v[0], v[1])));
        return out;
    }

    public static int storedFoodValue(ServerLevel level, BaseState base) {
        return FoodPayment.totalValue(foodOptions(level, base));
    }

    /** Equipment payment plan for one role. */
    private record GearPlan(ItemStack finished, List<ToolKind.Cost> materials, ItemStack arrow, List<ToolKind.Cost> arrowMaterials) {
    }

    private static GearPlan planGear(ServerLevel level, BaseState base, NpcRole role, List<String> missing) {
        ItemStack finished = ItemStack.EMPTY;
        List<ToolKind.Cost> materials = List.of();
        if (role.tool != ToolKind.NONE) {
            // soldiers get the best stored weapon; civilians take the cheapest adequate tool and leave the rest
            finished = role.combatant ? FactionStorage.findBest(level, base, role.tool::matches, ToolKind::tierOf)
                    : FactionStorage.findBest(level, base, role.tool::matches, s -> -ToolKind.tierOf(s));
            if (finished.isEmpty()) {
                materials = role.tool.materialCost();
                for (ToolKind.Cost c : materials) {
                    if (c.item() == Items.STICK ? stickValue(level, base) < c.count()
                            : FactionStorage.count(level, base, s -> s.is(c.item())) < c.count()) {
                        missing.add(role.tool.basicItem().getHoverName().getString() + " (or " + c.count() + " "
                                + c.item().getDescription().getString() + ")");
                    }
                }
            }
        }
        ItemStack arrow = ItemStack.EMPTY;
        List<ToolKind.Cost> arrowMaterials = List.of();
        if (role.ranged) {
            arrow = FactionStorage.findBest(level, base, ToolKind::isArrow, s -> s.is(Items.ARROW) ? 0 : 1);
            if (arrow.isEmpty()) {
                arrowMaterials = List.of(new ToolKind.Cost(Items.FLINT, 1), new ToolKind.Cost(Items.STICK, 1), new ToolKind.Cost(Items.FEATHER, 1));
                for (ToolKind.Cost c : arrowMaterials) {
                    if (c.item() == Items.STICK ? stickValue(level, base) < c.count()
                            : FactionStorage.count(level, base, s -> s.is(c.item())) < c.count()) {
                        missing.add("an arrow (or " + c.item().getDescription().getString() + ")");
                    }
                }
            }
        }
        return new GearPlan(finished, materials, arrow, arrowMaterials);
    }

    /**
     * The food a recruit costs at this base: {@code recruitment.foodValue}, less a hero's {@code rally} at the base
     * (v1 21: {@code round(food x (1 - rally))}; task A4).
     */
    public static int foodCost(BaseState base) {
        int food = FCConfig.RECRUIT_FOOD_VALUE.get();
        double rally = com.factionclash.hero.HeroTalents.rallyPercent(base);
        return rally <= 0 ? food : (int) Math.round(food * (1 - rally / 100.0));
    }

    /** True if the base can pay for {@code role} right now; {@code why} receives what is missing. */
    public static boolean canAfford(ServerLevel level, BaseState base, NpcRole role, List<String> why) {
        int needFood = foodCost(base);
        FoodPayment.Plan plan = FoodPayment.plan(foodOptions(level, base), needFood);
        if (plan == null) {
            why.add("food (" + storedFoodValue(level, base) + "/" + needFood + " hunger points)");
        }
        planGear(level, base, role, why);
        return why.isEmpty();
    }

    public static Result recruit(ServerLevel level, CampaignData data, BaseState base, NpcRole role, String reason) {
        List<String> missing = new ArrayList<>();
        int needFood = foodCost(base);
        FoodPayment.Plan food = FoodPayment.plan(foodOptions(level, base), needFood);
        if (food == null) {
            missing.add("food (" + storedFoodValue(level, base) + "/" + needFood + " hunger points)");
        }
        int reserve = Logistics.foodReserve(data, base);
        if (food != null && reserve > 0 && storedFoodValue(level, base) - needFood < reserve) {
            // the food already promised: a day's meals for everyone living at the base
            missing.add("food beyond the reserve (" + storedFoodValue(level, base) + " stored, " + reserve + " kept for a day's meals)");
        }
        GearPlan gear = planGear(level, base, role, missing);
        if (!missing.isEmpty()) {
            return new Result(null, "Cannot recruit " + role.displayName + ": missing " + String.join(", ", missing));
        }
        // --- charge (all checks passed)
        List<String> paid = new ArrayList<>();
        for (FoodPayment.Use u : food.uses()) {
            Predicate<ItemStack> f = s -> isRecruitFood(s) && BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(u.key());
            FactionStorage.extract(level, base, f, u.count());
            paid.add(u.count() + "x " + u.key().replace("minecraft:", ""));
        }
        ItemStack tool = ItemStack.EMPTY;
        if (role.tool != ToolKind.NONE) {
            if (!gear.finished().isEmpty()) {
                tool = FactionStorage.extractExact(level, base, gear.finished());
                paid.add(tool.getHoverName().getString());
            } else {
                for (ToolKind.Cost c : gear.materials()) {
                    if (c.item() == Items.STICK) {
                        paid.add(chargeSticks(level, base, c.count()));
                    } else {
                        FactionStorage.extract(level, base, s -> s.is(c.item()), c.count());
                        paid.add(c.count() + "x " + c.item().getDescription().getString());
                    }
                }
                tool = role.tool.basicItem();
            }
        }
        ItemStack arrow = ItemStack.EMPTY;
        if (role.ranged) {
            if (!gear.arrow().isEmpty()) {
                arrow = FactionStorage.extractExact(level, base, gear.arrow());
                paid.add("1 arrow");
            } else {
                for (ToolKind.Cost c : gear.arrowMaterials()) {
                    if (c.item() == Items.STICK) {
                        chargeSticks(level, base, c.count());
                    } else {
                        FactionStorage.extract(level, base, s -> s.is(c.item()), c.count());
                    }
                }
                arrow = new ItemStack(Items.ARROW);
                // one crafting batch yields four arrows: the three spares go back into storage, not lost
                FactionStorage.insert(level, base, new ItemStack(Items.ARROW, 3));
                paid.add("arrow materials");
            }
        }
        BlockPos at = base.point(BaseState.SPAWN);
        FactionNpc npc = FactionNpc.create(level, base.faction, role, base, at == null ? base.center : at);
        if (!tool.isEmpty()) {
            npc.equip(tool);
        }
        if (!arrow.isEmpty()) {
            npc.setAmmo(arrow);
        }
        if (role.combatant) {
            // better gear supplied at recruitment when the warehouse has it (optional, not part of the bill)
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack best = FactionStorage.findBest(level, base, s -> s.getItem() instanceof ArmorItem a && a.getEquipmentSlot() == slot,
                        s -> ((ArmorItem) s.getItem()).getDefense());
                if (!best.isEmpty()) {
                    npc.equip(FactionStorage.extractExact(level, base, best));
                }
            }
        }
        level.addFreshEntity(npc);
        register(data, npc, base);
        base.lastRecruitAt = level.getGameTime();
        data.setDirty();
        String msg = "Recruited " + npc.npcName() + " (" + role.displayName + ", " + reason + ") paying " + String.join(", ", paid)
                + " [" + food.paidValue() + " hunger points]";
        Messages.toFaction(level.getServer(), base.faction, Component.literal(msg));
        return new Result(npc, msg);
    }

    /** Sticks obtainable from storage: sticks, plus 2 per plank and 8 per log (vanilla ratios). */
    public static int stickValue(ServerLevel level, BaseState base) {
        return FactionStorage.count(level, base, s -> s.is(Items.STICK))
                + FactionStorage.count(level, base, s -> s.is(net.minecraft.tags.ItemTags.PLANKS)) * 2
                + FactionStorage.count(level, base, s -> s.is(net.minecraft.tags.ItemTags.LOGS)) * 8;
    }

    /**
     * Charges sticks, converting planks or a log through the real recipes when needed. Spare sticks or planks
     * from a converted batch go back into storage, so nothing is lost or double-charged.
     */
    private static String chargeSticks(ServerLevel level, BaseState base, int need) {
        int have = FactionStorage.count(level, base, s -> s.is(Items.STICK));
        int fromSticks = Math.min(have, need);
        FactionStorage.extract(level, base, s -> s.is(Items.STICK), fromSticks);
        int rest = need - fromSticks;
        if (rest <= 0) {
            return need + "x Stick";
        }
        int batches = (rest + 3) / 4; // 2 planks -> 4 sticks
        int planksNeeded = batches * 2;
        int planksHave = FactionStorage.count(level, base, s -> s.is(net.minecraft.tags.ItemTags.PLANKS));
        int planksFromStore = Math.min(planksHave, planksNeeded);
        FactionStorage.extract(level, base, s -> s.is(net.minecraft.tags.ItemTags.PLANKS), planksFromStore);
        int planksShort = planksNeeded - planksFromStore;
        int logs = (planksShort + 3) / 4; // 1 log -> 4 planks
        if (logs > 0) {
            FactionStorage.extract(level, base, s -> s.is(net.minecraft.tags.ItemTags.LOGS), logs);
            int sparePlanks = logs * 4 - planksShort;
            if (sparePlanks > 0) {
                FactionStorage.insert(level, base, new ItemStack(Items.OAK_PLANKS, sparePlanks));
            }
        }
        int spareSticks = batches * 4 - rest;
        if (spareSticks > 0) {
            FactionStorage.insert(level, base, new ItemStack(Items.STICK, spareSticks));
        }
        return need + "x Stick (" + (logs > 0 ? logs + " log" : planksFromStore + " planks") + ")";
    }

    public static void register(CampaignData data, FactionNpc npc, BaseState base) {
        NpcRecord r = new NpcRecord();
        r.uuid = npc.getUUID();
        r.name = npc.npcName();
        r.role = npc.getRole();
        r.baseId = base.id;
        r.lastPos = npc.blockPosition();
        r.lastSeen = npc.level().getGameTime();
        data.faction(base.faction).roster.put(r.uuid, r);
        data.setDirty();
    }
}
