package com.factionclash.campaign;

import com.factionclash.config.FCConfig;
import com.factionclash.logic.Ledger;
import com.factionclash.npc.NpcRole;
import com.factionclash.storage.FactionStorage;
import com.factionclash.util.Messages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The faction's quartermaster: samples what the storehouse holds of the resources that keep a base going, keeps the
 * trend per day and how long each lasts, warns the faction when one runs short, and decides on rationing (fewer
 * meals carried, only food producers recruited) and the food kept in reserve for the people already there.
 */
public final class Logistics {
    private Logistics() {
    }

    public enum Resource {
        FOOD("Food", "hunger points"),
        WOOD("Wood", "logs"),
        STONE("Stone", "cobblestone"),
        IRON("Iron", "ingots and raw iron"),
        FUEL("Fuel", "coal and charcoal"),
        DIAMOND("Diamonds", "diamonds");

        public final String label, unit;

        Resource(String label, String unit) {
            this.label = label;
            this.unit = unit;
        }
    }

    /** PROPOSED: sampling interval (one minute of game time). */
    public static final long SAMPLE_TICKS = 1200;
    /** PROPOSED: food a working NPC needs per day, in hunger points (about 1.5 bread, see survival.workExhaustionPerDay). */
    public static final int FOOD_PER_NPC_DAY = 8;

    public static int measure(ServerLevel level, BaseState b, Resource r) {
        return switch (r) {
            case FOOD -> Recruitment.storedFoodValue(level, b);
            case WOOD -> FactionStorage.count(level, b, s -> s.is(ItemTags.LOGS)) + FactionStorage.count(level, b, s -> s.is(ItemTags.PLANKS)) / 4;
            case STONE -> FactionStorage.count(level, b, s -> s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE));
            case IRON -> FactionStorage.count(level, b, s -> s.is(Items.IRON_INGOT) || s.is(Items.RAW_IRON));
            case FUEL -> FactionStorage.count(level, b, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL))
                    + FactionStorage.count(level, b, s -> s.is(Items.COAL_BLOCK)) * 9;
            case DIAMOND -> FactionStorage.count(level, b, s -> s.is(Items.DIAMOND));
        };
    }

    public static Ledger ledger(BaseState b, Resource r) {
        return b.ledgers.computeIfAbsent(r.name(), k -> new Ledger());
    }

    /** Called by the director: one sample a minute, then the shortage check with messages on every change. */
    public static void tick(ServerLevel level, CampaignData data, BaseState b, long t) {
        Ledger food = ledger(b, Resource.FOOD);
        if (food.size() > 0 && t - food.times()[food.size() - 1] < SAMPLE_TICKS) {
            return;
        }
        for (Resource r : Resource.values()) {
            ledger(b, r).record(t, measure(level, b, r));
        }
        data.setDirty();
        int pop = population(data, b);
        check(level, b, "FOOD", foodShort(b, pop),
                () -> String.format("food is running low (%d hunger points, %s) - rationing: one meal each, only food producers recruited",
                        ledger(b, Resource.FOOD).latest(), forecast(ledger(b, Resource.FOOD))),
                "food supply recovered - rationing ends");
        Ledger wood = ledger(b, Resource.WOOD);
        // hysteresis: short below 8 logs, recovered only from 16, so a stock hovering at the edge does not flap
        boolean woodShort = b.shortages.contains("WOOD") ? wood.latest() < 16 : wood.latest() < 8 && !(wood.perDay() > 0);
        check(level, b, "WOOD", woodShort,
                () -> "out of wood (" + wood.latest() + " logs) - no sticks for tools until the woodcutter brings more",
                "wood supply recovered");
        Ledger fuel = ledger(b, Resource.FUEL);
        check(level, b, "FUEL", fuel.latest() == 0 && wood.latest() < 24,
                () -> "no coal, charcoal or spare wood - furnaces and the smoker stand cold",
                "fuel available again");
    }

    /**
     * Short of food: less than a day's meals for everyone in store, or at this rate gone within a day and a half.
     * Rationing then lasts until there is a day and a half of meals in store and the stock is not falling fast, so
     * a stock hovering at the limit does not switch it on and off.
     */
    private static boolean foodShort(BaseState b, int pop) {
        Ledger food = ledger(b, Resource.FOOD);
        if (!FCConfig.HUNGER.get() || pop == 0) {
            return false;
        }
        if (b.shortages.contains("FOOD")) {
            return food.latest() < pop * FOOD_PER_NPC_DAY * 3 / 2 || food.daysLeft() < 1.5;
        }
        return food.latest() < pop * FOOD_PER_NPC_DAY || food.daysLeft() < 1.5;
    }

    private static void check(ServerLevel level, BaseState b, String key, boolean shortNow,
                              java.util.function.Supplier<String> warn, String recovered) {
        boolean was = b.shortages.contains(key);
        if (shortNow == was) {
            return;
        }
        if (shortNow) {
            b.shortages.add(key);
            Messages.toFaction(level.getServer(), b.faction, Component.literal("Logistics: " + warn.get()).withStyle(ChatFormatting.GOLD));
        } else {
            b.shortages.remove(key);
            Messages.toFaction(level.getServer(), b.faction, Component.literal("Logistics: " + recovered).withStyle(ChatFormatting.GREEN));
        }
    }

    public static String forecast(Ledger l) {
        double r = l.perDay();
        if (Double.isNaN(r)) {
            return "trend not known yet";
        }
        String trend = String.format("%+d/day", Math.round(r));
        double days = l.daysLeft();
        if (Double.isInfinite(days)) {
            return trend;
        }
        return trend + (days < 0.1 ? ", gone now" : String.format(", gone in about %.1f days", days));
    }

    /** Rationing is on while the base is short of food. */
    public static boolean rationing(BaseState b) {
        return b != null && b.shortages.contains("FOOD");
    }

    /** Food kept back from recruitment payments: a day's meals for everyone already living at the base. */
    public static int foodReserve(CampaignData data, BaseState b) {
        return FCConfig.HUNGER.get() ? population(data, b) * FOOD_PER_NPC_DAY : 0;
    }

    /** Roles that bring food in: still recruited while rationing. */
    public static boolean producesFood(NpcRole r) {
        return r == NpcRole.FARMER || r == NpcRole.FISHERMAN || r == NpcRole.BUTCHER || r == NpcRole.ANIMAL_CARER || r == NpcRole.CHEF;
    }

    public static int population(CampaignData data, BaseState b) {
        int n = 0;
        for (NpcRecord r : data.faction(b.faction).roster.values()) {
            if (r.alive && r.baseId == b.id) {
                n++;
            }
        }
        return n;
    }

    /** Lines for the reporting station's overview. */
    public static List<String> report(BaseState b) {
        List<String> out = new ArrayList<>();
        for (Resource r : Resource.values()) {
            Ledger l = ledger(b, r);
            if (l.size() == 0) {
                continue;
            }
            out.add((b.shortages.contains(r.name()) ? "! " : "  ") + r.label + ": " + l.latest() + " " + r.unit + " (" + forecast(l) + ")");
        }
        return out;
    }
}
