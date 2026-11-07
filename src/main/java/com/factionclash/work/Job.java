package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcNavigator;
import com.factionclash.npc.NpcRole;
import com.factionclash.npc.ToolKind;
import com.factionclash.storage.FactionStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Base class of every NPC job. A job is a saved state machine ticked by {@link WorkGoal}; combat pauses it
 * without losing progress. Helpers cover travel, unloading to faction storage and equipment upgrades.
 */
public abstract class Job {
    protected final FactionNpc npc;
    private int waitTicks;
    private long lastEquipCheck = -100000;

    protected Job(FactionNpc npc) {
        this.npc = npc;
    }

    public final void tick() {
        if (!(npc.level() instanceof ServerLevel)) {
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        run();
    }

    protected abstract void run();

    /** Called when combat or fleeing interrupts the job; progress fields are kept. */
    public void onPause() {
        npc.getNavigation().stop();
    }

    public void save(CompoundTag tag, HolderLookup.Provider provider) {
    }

    public void load(CompoundTag tag, HolderLookup.Provider provider) {
    }

    /** Readable workplace description for inspection. */
    public String workplaceInfo() {
        return "none";
    }

    public BlockPos destination() {
        return npc.navigator().target();
    }

    /** Scouts on retreat keep moving even when attacked. */
    public boolean ignoresCombat() {
        return false;
    }

    // ------------------------------------------------------------------ helpers

    protected void waitFor(int ticks) {
        waitTicks = Math.max(waitTicks, ticks);
    }

    protected ServerLevel level() {
        return (ServerLevel) npc.level();
    }

    protected CampaignData data() {
        return CampaignData.get(level().getServer());
    }

    protected BaseState base() {
        return npc.base();
    }

    protected long now() {
        return level().getGameTime();
    }

    protected void status(String text, String activity) {
        npc.setStatus(text, activity);
    }

    protected boolean goTo(BlockPos pos, double reach, String label) {
        return goTo(pos, reach, label, 1.0);
    }

    /** Walks toward {@code pos}; true once within reach. Repeated failure is reported and retried later. */
    protected boolean goTo(BlockPos pos, double reach, String label, double speed) {
        if (pos == null) {
            return false;
        }
        NpcNavigator.Result r = npc.navigator().travel(pos, reach, speed);
        switch (r) {
            case ARRIVED:
                return true;
            case MOVING:
                status("Walking to " + label, "Travelling");
                return false;
            default:
                status("Cannot reach " + label + " (" + npc.navigator().lastProblem() + "), will retry", "Blocked");
                npc.navigator().reset();
                waitFor(100);
                return false;
        }
    }

    protected boolean atStorage() {
        BaseState b = base();
        return b != null && goTo(b.point(BaseState.STORAGE_STAND), 2.0, "the storehouse");
    }

    /** How many of this item the NPC keeps when unloading (job consumables such as seeds or saplings). */
    protected int keep(ItemStack stack) {
        return 0;
    }

    protected boolean hasCargo() {
        Map<net.minecraft.world.item.Item, Integer> kept = new HashMap<>();
        for (int i = 0; i < npc.inventory().getContainerSize(); i++) {
            ItemStack s = npc.inventory().getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            int k = keep(s);
            int already = kept.getOrDefault(s.getItem(), 0);
            if (s.getCount() + already > k) {
                return true;
            }
            kept.put(s.getItem(), already + s.getCount());
        }
        return false;
    }

    public enum Unload { DONE, PARTIAL_FULL }

    /** Deposits cargo into faction storage. Must be at the storehouse. Never deletes items. */
    protected Unload unloadNow() {
        BaseState b = base();
        Map<net.minecraft.world.item.Item, Integer> kept = new HashMap<>();
        boolean full = false;
        for (int i = 0; i < npc.inventory().getContainerSize(); i++) {
            ItemStack s = npc.inventory().getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            int k = keep(s);
            int already = kept.getOrDefault(s.getItem(), 0);
            int keepHere = Math.max(0, Math.min(s.getCount(), k - already));
            kept.put(s.getItem(), already + keepHere);
            int deposit = s.getCount() - keepHere;
            if (deposit <= 0) {
                continue;
            }
            ItemStack out = s.split(deposit);
            ItemStack rest = FactionStorage.insert(level(), b, out);
            if (!rest.isEmpty()) {
                s.grow(rest.getCount());
                full = true;
            }
        }
        npc.inventory().setChanged();
        npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return full ? Unload.PARTIAL_FULL : Unload.DONE;
    }

    /** Takes items from storage into the backpack (at the storehouse). Returns the amount taken. */
    protected int takeFromStorage(Predicate<ItemStack> filter, int amount) {
        List<ItemStack> got = FactionStorage.extract(level(), base(), filter, amount);
        int n = 0;
        for (ItemStack s : got) {
            n += s.getCount();
            ItemStack rest = Crafting.addAll(npc.inventory(), s);
            if (!rest.isEmpty()) {
                n -= rest.getCount();
                FactionStorage.insert(level(), base(), rest);
            }
        }
        return n;
    }

    /** At the storehouse: swap in better tools/armour; replaced equipment disappears (spec 9). */
    protected void upgradeEquipment() {
        if (now() - lastEquipCheck < 1200) {
            return;
        }
        lastEquipCheck = now();
        NpcRole role = npc.getRole();
        BaseState b = base();
        if (role.tool != ToolKind.NONE) {
            int cur = role.tool.matches(npc.tool()) ? ToolKind.tierOf(npc.tool()) : -1;
            ItemStack best = FactionStorage.findBest(level(), b, role.tool::matches, ToolKind::tierOf);
            if (!best.isEmpty() && ToolKind.tierOf(best) > cur) {
                ItemStack got = FactionStorage.extractExact(level(), b, best);
                if (!got.isEmpty()) {
                    npc.equip(got);
                }
            }
        }
        if (role.ranged) {
            ItemStack bestArrow = FactionStorage.findBest(level(), b, ToolKind::isArrow, s -> s.is(Items.ARROW) ? 0 : 1);
            int cur = npc.ammo().isEmpty() ? -1 : (npc.ammo().is(Items.ARROW) ? 0 : 1);
            if (!bestArrow.isEmpty() && (bestArrow.is(Items.ARROW) ? 0 : 1) > cur) {
                ItemStack got = FactionStorage.extractExact(level(), b, bestArrow);
                if (!got.isEmpty()) {
                    npc.setAmmo(got);
                }
            }
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack cur = npc.getItemBySlot(slot);
            int curDef = cur.getItem() instanceof ArmorItem a ? a.getDefense() : -1;
            Predicate<ItemStack> fits = s -> s.getItem() instanceof ArmorItem a && a.getEquipmentSlot() == slot;
            ItemStack best = FactionStorage.findBest(level(), b, fits, s -> ((ArmorItem) s.getItem()).getDefense());
            if (best.isEmpty() || ((ArmorItem) best.getItem()).getDefense() <= curDef) {
                continue;
            }
            // Soldiers get scarce armour first: civilians only take a piece when a spare remains for soldiers.
            if (!role.combatant && FactionStorage.count(level(), b, fits) < 2) {
                continue;
            }
            ItemStack got = FactionStorage.extractExact(level(), b, best);
            if (!got.isEmpty()) {
                npc.equip(got);
            }
        }
    }

    protected void lookAt(BlockPos pos) {
        npc.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    protected static CompoundTag posTag(BlockPos p) {
        CompoundTag t = new CompoundTag();
        if (p != null) {
            t.putLong("p", p.asLong());
        }
        return t;
    }

    protected static BlockPos readPos(CompoundTag t, String key) {
        return t.contains(key) ? BlockPos.of(t.getLong(key)) : null;
    }
}
