package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.Report;
import com.factionclash.logic.Affix;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.ToolKind;
import com.factionclash.storage.FactionStorage;
import com.factionclash.util.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Trader: takes the base's surplus to a village the scouts reported and trades with its villagers the way a player
 * does - the villagers' own offers, prices (demand included) and stock, paid in real items. It sells surplus crops,
 * sticks, string, leather, rotten flesh, stone and the like for emeralds, and spends emeralds on armour, weapons and
 * tools better than what the storehouse holds (bread too when food is short), then brings everything home.
 * Villagers gain trading experience and unlock their next offers as with a player; the orbs they drop go to the
 * faction's experience. One trip at most every half day, by day, while the villagers are awake.
 */
public class TraderJob extends Job {
    enum State { PLAN, TRAVEL, TRADE, RETURN }

    /** Goods worth selling, and how many the base keeps for itself (PROPOSED). Others are never sold. */
    static final Map<Item, Integer> SELL = new LinkedHashMap<>();

    static {
        SELL.put(Items.WHEAT, 64);
        SELL.put(Items.POTATO, 64);
        SELL.put(Items.CARROT, 64);
        SELL.put(Items.BEETROOT, 32);
        SELL.put(Items.PUMPKIN, 0);
        SELL.put(Items.MELON, 0);
        SELL.put(Items.STICK, 64);
        SELL.put(Items.STRING, 16);
        SELL.put(Items.LEATHER, 16);
        SELL.put(Items.ROTTEN_FLESH, 0);
        SELL.put(Items.PAPER, 0);
        SELL.put(Items.FEATHER, 32);
        SELL.put(Items.FLINT, 32);
        SELL.put(Items.CLAY_BALL, 0);
        SELL.put(Items.STONE, 64);
        SELL.put(Items.GRANITE, 128);
        SELL.put(Items.ANDESITE, 128);
        SELL.put(Items.DIORITE, 128);
        SELL.put(Items.COAL, 96);
        SELL.put(Items.COD, 32);
        SELL.put(Items.SALMON, 32);
        SELL.put(Items.RABBIT_HIDE, 0);
        SELL.put(Items.WHITE_WOOL, 8);
    }

    /** At most this many of one good packed per trip, and this many kinds. */
    private static final int PACK_EACH = 64, PACK_KINDS = 8;
    /** PROPOSED: half a day between trips; villagers restock at their workstations meanwhile. */
    private static final long TRIP_INTERVAL = 12000;
    /** How far from home a village may be for a trip (blocks). */
    public static final int TRADE_RANGE = 320;
    private static final int TRADE_TICKS = 20;

    private State state = State.PLAN;
    private BlockPos village;
    private long lastTrip = -TRIP_INTERVAL;
    private final Set<UUID> visited = new HashSet<>();
    private UUID current;
    private int timer;
    private long tripStarted;
    /** What this village's villagers bought last time: only those are packed (everything sellable on a first trip). */
    private final Set<Item> villageBuys = new HashSet<>();
    /** This trip's totals by item name: goods sold and goods bought. */
    private final Map<String, Integer> sold = new LinkedHashMap<>(), bought = new LinkedHashMap<>();

    public TraderJob(FactionNpc npc) {
        super(npc);
    }

    /** A grown villager with a trade (not a nitwit or jobless one). */
    public static boolean trades(net.minecraft.world.entity.LivingEntity e) {
        return e instanceof Villager v && v.isAlive() && !v.isBaby() && v.getVillagerData().getProfession() != VillagerProfession.NONE
                && v.getVillagerData().getProfession() != VillagerProfession.NITWIT;
    }

    @Override
    public String workplaceInfo() {
        return village == null ? "No village to trade with" : "Trading with the village at " + village.toShortString()
                + (villageBuys.isEmpty() ? "" : " (it buys " + villageBuys.size() + " kinds of goods)");
    }

    @Override
    protected int keep(ItemStack s) {
        return 0;
    }

    @Override
    protected void run() {
        BaseState b = base();
        if (b == null) {
            status("No home base", "Idle");
            waitFor(400);
            return;
        }
        switch (state) {
            case PLAN -> plan(b);
            case TRAVEL -> travel();
            case TRADE -> trade();
            case RETURN -> home();
        }
    }

    private void plan(BaseState b) {
        Report r = nearestVillage(b);
        if (r == null) {
            status("No village reported within " + TRADE_RANGE + " blocks", "Idle");
            waitFor(1200);
            return;
        }
        if (!r.pos.equals(village)) {
            village = r.pos;
            villageBuys.clear();
        }
        if (now() - lastTrip < TRIP_INTERVAL) {
            status("Waiting for the villagers to restock", "Waiting");
            waitFor(600);
            return;
        }
        long day = level().getDayTime() % 24000;
        if (day > 9000 && day < 23500) {
            status("Waiting for daylight to set out for the village", "Waiting");
            waitFor(600);
            return;
        }
        if (!atStorage()) {
            return;
        }
        unloadNow();
        // pack the surplus: what this village bought last time, or every good worth selling on a first visit
        int kinds = 0;
        boolean rationing = com.factionclash.campaign.Logistics.rationing(b);
        for (Map.Entry<Item, Integer> e : SELL.entrySet()) {
            Item item = e.getKey();
            if (kinds >= PACK_KINDS || !villageBuys.isEmpty() && !villageBuys.contains(item)
                    || rationing && item.components().has(net.minecraft.core.component.DataComponents.FOOD)) {
                continue;
            }
            int spare = FactionStorage.count(level(), b, s -> s.is(item)) - e.getValue();
            if (spare >= 8 && takeFromStorage(s -> s.is(item), Math.min(PACK_EACH, spare)) > 0) {
                kinds++;
            }
        }
        takeFromStorage(s -> s.is(Items.EMERALD), 64 * 3);
        if (Inv.count(npc.inventory(), s -> !isProvision(s)) == 0) {
            status("Nothing to trade: no surplus goods or emeralds", "Idle");
            lastTrip = now() - TRIP_INTERVAL / 2;
            waitFor(1200);
            return;
        }
        visited.clear();
        sold.clear();
        bought.clear();
        current = null;
        tripStarted = now();
        state = State.TRAVEL;
    }

    private Report nearestVillage(BaseState b) {
        Report best = null;
        double bestD = (double) TRADE_RANGE * TRADE_RANGE;
        for (Report r : data().faction(npc.getFaction()).knowledge) {
            if (r.kind == Report.Kind.VILLAGE) {
                double d = r.pos.distSqr(b.center);
                if (d < bestD) {
                    bestD = d;
                    best = r;
                }
            }
        }
        return best;
    }

    private void travel() {
        if (village == null) {
            state = State.RETURN;
            return;
        }
        if (now() - tripStarted > 24000) {
            state = State.RETURN; // a day on the road: give up on this trip
            return;
        }
        if (goTo(village, 8.0, "the village")) {
            state = State.TRADE;
            timer = 0;
        }
    }

    private void trade() {
        List<Villager> around = level().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(village).inflate(48, 16, 48),
                v -> trades(v) && !visited.contains(v.getUUID()));
        Villager v = null;
        for (Villager c : around) {
            if (c.getUUID().equals(current)) {
                v = c;
            }
        }
        if (v == null) {
            v = around.stream().min(java.util.Comparator.comparingDouble(npc::distanceToSqr)).orElse(null);
            current = v == null ? null : v.getUUID();
            timer = 0;
        }
        if (v == null || now() - tripStarted > 24000) {
            state = State.RETURN;
            return;
        }
        if (v.isSleeping() || v.isTrading()) {
            visited.add(v.getUUID()); // asleep, or busy with a player: the next one
            return;
        }
        if (npc.distanceToSqr(v) > 2.5 * 2.5) {
            goTo(v.blockPosition(), 2.0, "a " + professionName(v));
            if (++timer > 400) {
                visited.add(v.getUUID()); // cannot get to it (fenced in, up a tower)
            }
            return;
        }
        npc.getNavigation().stop();
        npc.getLookControl().setLookAt(v, 30.0F, 30.0F);
        v.getLookControl().setLookAt(npc, 30.0F, 30.0F);
        status("Trading with a " + professionName(v), "Trading");
        if (++timer % TRADE_TICKS != 0) {
            return;
        }
        Trade done = tradeOnce(npc, v, TraderJob::forSale, s -> wanted(s));
        if (done == null) {
            visited.add(v.getUUID());
            return;
        }
        if (done.selling()) {
            sold.merge(done.paid().getHoverName().getString(), done.paid().getCount(), Integer::sum);
            villageBuys.add(done.paid().getItem());
        } else {
            bought.merge(done.got().getHoverName().getString(), done.got().getCount(), Integer::sum);
        }
        com.factionclash.achieve.FactionAchievements.criterion(level().getServer(), npc.getFaction(), "adventure/trade", "traded", npc.npcName());
        npc.awardXp(1);
    }

    private void home() {
        if (!atStorage()) {
            return;
        }
        unloadNow();
        lastTrip = now();
        state = State.PLAN;
        if (!sold.isEmpty() || !bought.isEmpty()) {
            Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Trader " + npc.npcName() + " is back from the village: "
                    + (sold.isEmpty() ? "sold nothing" : "sold " + summary(sold)) + "; " + (bought.isEmpty() ? "bought nothing" : "bought " + summary(bought))));
        }
        data().setDirty();
    }

    private static String summary(Map<String, Integer> totals) {
        List<String> out = new ArrayList<>();
        totals.forEach((k, n) -> out.add(n > 1 ? n + " " + k : k));
        return String.join(", ", out);
    }

    private static String professionName(Villager v) {
        return BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).getPath();
    }

    /** Goods the trader may hand over: the ones on the sell list (never its meals, emeralds or bought gear). */
    static boolean forSale(ItemStack s) {
        return SELL.containsKey(s.getItem());
    }

    /** Worth buying: armour, weapons and tools better than the storehouse's best of that kind; bread when food is short. */
    private boolean wanted(ItemStack s) {
        BaseState b = base();
        if (b == null) {
            return false;
        }
        if (s.is(Items.BREAD)) {
            return com.factionclash.campaign.Logistics.rationing(b);
        }
        return betterThanStock(s, pred -> FactionStorage.findBest(level(), b, pred, TraderJob::rank),
                pred -> Inv.count(npc.inventory(), pred));
    }

    /** Gear better (or one more of the best) than the base holds: fewer than 2 of that kind at that rank or better. */
    public static boolean betterThanStock(ItemStack s, java.util.function.Function<Predicate<ItemStack>, ItemStack> bestStored,
                                   java.util.function.Function<Predicate<ItemStack>, Integer> carried) {
        int rank = rank(s);
        if (rank < 0) {
            return false;
        }
        Predicate<ItemStack> sameKind = kind(s);
        Predicate<ItemStack> asGood = x -> sameKind.test(x) && rank(x) >= rank;
        if (carried.apply(asGood) >= 2) {
            return false; // bought enough of these on this trip
        }
        ItemStack best = bestStored.apply(sameKind);
        return best.isEmpty() || rank(best) <= rank;
    }

    private static Predicate<ItemStack> kind(ItemStack s) {
        if (s.getItem() instanceof ArmorItem a) {
            return x -> x.getItem() instanceof ArmorItem o && o.getEquipmentSlot() == a.getEquipmentSlot();
        }
        for (ToolKind k : ToolKind.values()) {
            if (k != ToolKind.NONE && k.matches(s)) {
                return k::matches;
            }
        }
        Item item = s.getItem();
        return x -> x.is(item);
    }

    /** Material rank of gear (-1: not gear the trader buys). Iron and better only, or a bow or crossbow. */
    static int rank(ItemStack s) {
        if (s.getItem() instanceof ArmorItem a) {
            var m = a.getMaterial();
            if (m.is(ArmorMaterials.NETHERITE)) {
                return 5;
            }
            if (m.is(ArmorMaterials.DIAMOND)) {
                return 4;
            }
            if (m.is(ArmorMaterials.IRON)) {
                return 3;
            }
            if (m.is(ArmorMaterials.CHAIN)) {
                return 2;
            }
            return -1;
        }
        int t = ToolKind.tierOf(s);
        if (s.getItem() instanceof net.minecraft.world.item.FishingRodItem) {
            return -1;
        }
        return t >= 2 || t == 1 && !(s.getItem() instanceof net.minecraft.world.item.TieredItem) ? t : -1;
    }

    /**
     * One trade with a villager, as a player makes it from the trading screen: an offer still in stock whose price the
     * NPC can pay from its backpack - selling first (goods for emeralds), then buying what is wanted. The villager
     * gains its trading experience (and drops the orb a player would get). Returns the trade (what was paid, what was
     * got, and a line such as "sold 20x Wheat" or "bought Iron Helmet"), or null when nothing more can be traded.
     */
    public record Trade(boolean selling, ItemStack paid, ItemStack got, String text) {
    }

    public static Trade tradeOnce(FactionNpc npc, AbstractVillager v, Predicate<ItemStack> forSale, Predicate<ItemStack> wanted) {
        MerchantOffer pick = null;
        boolean selling = false;
        for (MerchantOffer o : v.getOffers()) {
            if (o.isOutOfStock() || !o.getCostB().isEmpty()) {
                continue;
            }
            if (o.getResult().is(Items.EMERALD) && forSale.test(o.getCostA()) && canPay(npc, o)) {
                pick = o;
                selling = true;
                break;
            }
        }
        if (pick == null) {
            for (MerchantOffer o : v.getOffers()) {
                if (!o.isOutOfStock() && o.getCostB().isEmpty() && o.getCostA().is(Items.EMERALD) && !o.getResult().is(Items.EMERALD)
                        && wanted.test(o.getResult()) && canPay(npc, o)) {
                    pick = o;
                    break;
                }
            }
        }
        if (pick == null) {
            return null;
        }
        int price = price(npc, pick);
        Inv.remove(npc.inventory(), pick.getItemCostA()::test, price);
        ItemStack result = pick.assemble();
        ItemStack paid = pick.getCostA().copyWithCount(price);
        String what = (selling ? "sold " + price + "x " + paid.getHoverName().getString()
                : "bought " + (result.getCount() > 1 ? result.getCount() + "x " : "") + result.getHoverName().getString());
        Trade trade = new Trade(selling, paid, result.copy(), what);
        ItemStack rest = Crafting.addAll(npc.inventory(), result);
        if (!rest.isEmpty()) {
            npc.spawnAtLocation(rest);
        }
        npc.inventory().setChanged();
        v.notifyTrade(pick);
        v.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
        npc.swing(InteractionHand.MAIN_HAND);
        return trade;
    }

    /** The offer's price after demand and the Haggler affix (a good reputation lowers prices the same way). */
    static int price(FactionNpc npc, MerchantOffer o) {
        int base = o.getCostA().getCount();
        double off = npc.affix(Affix.HAGGLER);
        return off <= 0 || !o.getCostA().is(Items.EMERALD) ? base : Math.max(1, base - Mth.floor(base * off));
    }

    private static boolean canPay(FactionNpc npc, MerchantOffer o) {
        return Inv.count(npc.inventory(), o.getItemCostA()::test) >= price(npc, o) && Inv.room(npc.inventory(), o.getResult()) > 0;
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state.name());
        if (village != null) {
            tag.putLong("village", village.asLong());
        }
        tag.putLong("lastTrip", lastTrip);
        tag.putLong("tripStarted", tripStarted);
        ListTag buys = new ListTag();
        for (Item i : villageBuys) {
            buys.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(i).toString()));
        }
        tag.put("buys", buys);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            state = State.PLAN;
        }
        village = readPos(tag, "village");
        lastTrip = tag.contains("lastTrip") ? tag.getLong("lastTrip") : -TRIP_INTERVAL;
        tripStarted = tag.getLong("tripStarted");
        villageBuys.clear();
        ListTag buys = tag.getList("buys", Tag.TAG_STRING);
        for (int i = 0; i < buys.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(buys.getString(i));
            if (id != null) {
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(villageBuys::add);
            }
        }
        if (state == State.TRADE) {
            state = State.TRAVEL;
        }
    }
}
