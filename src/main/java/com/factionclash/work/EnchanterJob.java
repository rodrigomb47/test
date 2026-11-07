package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.FactionState;
import com.factionclash.logic.XpLevels;
import com.factionclash.npc.FactionNpc;
import com.factionclash.storage.FactionStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;

import java.util.List;
import java.util.Optional;

/**
 * Enchanter: works the workshop's enchanting table the way a player does. It spends experience levels and lapis
 * lazuli on the faction's best unenchanted gear (soldiers' weapons and armour first, then pickaxes). The
 * experience is real: the orbs the faction's NPCs picked up from mined ores, emptied furnaces and bred animals.
 * Which enchantments come out is decided by the vanilla table (bookshelves around it, random costs).
 */
public class EnchanterJob extends Job {
    enum State { STORAGE, TABLE }

    private State state = State.STORAGE;
    /** The enchanter's own experience points (taken from the faction's) and the vanilla level they make. */
    private long xpPoints;
    private int timer;
    private String lastResult = "";

    public EnchanterJob(FactionNpc npc) {
        super(npc);
    }

    @Override
    public String workplaceInfo() {
        BaseState b = base();
        int shelves = b == null || !(level() instanceof ServerLevel sl) || b.point(BaseState.ENCHANTING) == null ? 0
                : bookshelves(sl, b.point(BaseState.ENCHANTING));
        return "Enchanting table in the workshop (level " + XpLevels.levelFor(xpPoints) + ", " + shelves + " bookshelves)"
                + (lastResult.isEmpty() ? "" : "; last: " + lastResult);
    }

    @Override
    protected int keep(ItemStack s) {
        return s.is(Items.LAPIS_LAZULI) ? 64 : 0;
    }

    @Override
    protected void run() {
        BaseState b = base();
        if (b == null) {
            status("No home base", "Idle");
            waitFor(400);
            return;
        }
        BlockPos table = b.point(BaseState.ENCHANTING);
        if (table == null || !level().getBlockState(table).is(Blocks.ENCHANTING_TABLE)) {
            status("The enchanting table is missing", "Blocked");
            waitFor(1200);
            return;
        }
        if (state == State.STORAGE) {
            storage(b);
        } else {
            atTable(table);
        }
    }

    private void storage(BaseState b) {
        if (!atStorage()) {
            return;
        }
        unloadNow(); // the gear enchanted last time goes back where the soldiers and miners take it from
        FactionState f = data().faction(npc.getFaction());
        if (f.xpBank > 0) {
            xpPoints += f.xpBank;
            f.xpBank = 0;
            data().setDirty();
        }
        int lapis = Inv.count(npc.inventory(), x -> x.is(Items.LAPIS_LAZULI));
        if (lapis < 3) {
            takeFromStorage(x -> x.is(Items.LAPIS_LAZULI), 16 - lapis);
            lapis = Inv.count(npc.inventory(), x -> x.is(Items.LAPIS_LAZULI));
        }
        int lvl = XpLevels.levelFor(xpPoints);
        if (lapis == 0) {
            status("No lapis lazuli to enchant with", "Waiting");
            waitFor(1200);
            return;
        }
        if (lvl < MIN_LEVEL) {
            status("Gathering experience: level " + lvl + " (" + xpPoints + " points) - miners, smelters and carers bring more", "Waiting");
            waitFor(1200);
            return;
        }
        ItemStack best = FactionStorage.findBest(level(), b, EnchanterJob::candidate, EnchanterJob::priority);
        ItemStack got = best.isEmpty() ? ItemStack.EMPTY : FactionStorage.extractExact(level(), b, best);
        if (got.isEmpty()) {
            status("Nothing worth enchanting in the storehouse (level " + lvl + ")", "Idle");
            waitFor(1200);
            return;
        }
        ItemStack rest = Crafting.addAll(npc.inventory(), got);
        if (!rest.isEmpty()) {
            FactionStorage.insert(level(), b, rest);
            return;
        }
        timer = 0;
        state = State.TABLE;
    }

    private void atTable(BlockPos table) {
        int slot = -1;
        for (int i = 0; i < npc.inventory().getContainerSize(); i++) {
            if (candidate(npc.inventory().getItem(i))) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            state = State.STORAGE;
            return;
        }
        if (!goTo(table, 2.2, "the enchanting table")) {
            return;
        }
        ItemStack item = npc.inventory().getItem(slot);
        lookAt(table);
        status("Enchanting " + item.getHoverName().getString() + " (level " + XpLevels.levelFor(xpPoints) + ")", "Enchanting");
        if (++timer < THINK_TICKS) {
            if (timer % 20 == 0) {
                npc.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }
        long[] xp = {xpPoints};
        int used = enchantOnce((ServerLevel) level(), table, item, npc.inventory(), xp, npc.getRandom());
        xpPoints = xp[0];
        if (used >= 0) {
            ItemStack done = npc.inventory().getItem(slot);
            lastResult = done.getHoverName().getString() + " " + describe(done);
            npc.awardXp(4 + 2 * used);
            npc.swing(InteractionHand.MAIN_HAND);
            com.factionclash.FactionClash.LOGGER.info("[{}] {} enchanted {} {} (slot {}, now level {})", npc.getFaction().displayName,
                    npc.npcName(), done.getHoverName().getString(), describe(done), used + 1, XpLevels.levelFor(xpPoints));
        } else {
            lastResult = "not enough levels for " + item.getHoverName().getString();
        }
        state = State.STORAGE; // back to the storehouse with it, enchanted or not
    }

    /** PROPOSED: the enchanter waits for at least this level before taking gear to the table. */
    static final int MIN_LEVEL = 5;
    /** PROPOSED: ticks at the table before the enchantment is chosen (a player reading the offers). */
    static final int THINK_TICKS = 60;

    /**
     * One enchantment at a real table, as the vanilla enchanting screen does it: the bookshelves around the table
     * set the power, three random offers are drawn, and the best one the enchanter can pay for (level and
     * 1-3 lapis) is taken. Spends the levels and lapis. Returns the offer used (0-2), or -1 if none was affordable.
     */
    public static int enchantOnce(ServerLevel level, BlockPos table, ItemStack stack, Container inv, long[] xp, RandomSource random) {
        if (stack.isEmpty() || !stack.isEnchantable()) {
            return -1;
        }
        int power = bookshelves(level, table);
        int lapis = Inv.count(inv, x -> x.is(Items.LAPIS_LAZULI));
        int lvl = XpLevels.levelFor(xp[0]);
        Optional<HolderSet.Named<Enchantment>> pool = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getTag(EnchantmentTags.IN_ENCHANTING_TABLE);
        if (pool.isEmpty()) {
            return -1;
        }
        int[] costs = new int[3];
        for (int i = 0; i < 3; i++) {
            costs[i] = EnchantmentHelper.getEnchantmentCost(random, i, power, stack);
            if (costs[i] < i + 1) {
                costs[i] = 0;
            }
        }
        for (int slot = 2; slot >= 0; slot--) {
            if (costs[slot] <= 0 || lvl < costs[slot] || lvl < slot + 1 || lapis < slot + 1) {
                continue;
            }
            List<EnchantmentInstance> list = EnchantmentHelper.selectEnchantment(random, stack, costs[slot], pool.get().stream());
            if (list.isEmpty()) {
                continue;
            }
            for (EnchantmentInstance e : list) {
                stack.enchant(e.enchantment, e.level);
            }
            Inv.consume(inv, x -> x.is(Items.LAPIS_LAZULI), slot + 1);
            xp[0] = XpLevels.spend(xp[0], slot + 1);
            level.playSound(null, table, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.1F + 0.9F);
            return slot;
        }
        return -1;
    }

    /** Enchanting power of the bookshelves around a table (each one with air between it and the table). */
    public static int bookshelves(ServerLevel level, BlockPos table) {
        float power = 0;
        for (BlockPos off : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (EnchantingTableBlock.isValidBookShelf(level, table, off)) {
                power += level.getBlockState(table.offset(off)).getEnchantPowerBonus(level, table.offset(off));
            }
        }
        return (int) power;
    }

    /** Gear worth the lapis: unenchanted iron, diamond or netherite weapons, armour and pickaxes, bows and crossbows. */
    public static boolean candidate(ItemStack s) {
        if (s.isEmpty() || !s.isEnchantable() || s.isEnchanted()) {
            return false;
        }
        if (s.getItem() instanceof TieredItem t) {
            return t.getTier() == Tiers.IRON || t.getTier() == Tiers.DIAMOND || t.getTier() == Tiers.NETHERITE;
        }
        if (s.getItem() instanceof ArmorItem a) {
            Holder<net.minecraft.world.item.ArmorMaterial> m = a.getMaterial();
            return m.is(ArmorMaterials.IRON) || m.is(ArmorMaterials.DIAMOND) || m.is(ArmorMaterials.NETHERITE) || m.is(ArmorMaterials.CHAIN);
        }
        return s.getItem() instanceof BowItem || s.getItem() instanceof CrossbowItem;
    }

    /** Soldiers' gear first (diamond before iron), then pickaxes. */
    public static int priority(ItemStack s) {
        int p = 0;
        if (s.getItem() instanceof TieredItem t) {
            p = t.getTier() == Tiers.NETHERITE ? 30 : t.getTier() == Tiers.DIAMOND ? 20 : 10;
            if (s.getItem() instanceof net.minecraft.world.item.PickaxeItem) {
                p -= 5;
            }
        } else if (s.getItem() instanceof ArmorItem a) {
            p = 8 + a.getDefense();
        } else {
            p = 12;
        }
        return p;
    }

    private static String describe(ItemStack s) {
        StringBuilder sb = new StringBuilder("(");
        var ench = s.getEnchantments();
        for (Holder<Enchantment> h : ench.keySet()) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(Enchantment.getFullname(h, ench.getLevel(h)).getString());
        }
        return sb.append(")").toString();
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state.name());
        tag.putLong("xpPoints", xpPoints);
        tag.putString("lastResult", lastResult);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            state = State.STORAGE;
        }
        xpPoints = tag.getLong("xpPoints");
        lastResult = tag.getString("lastResult");
    }
}
