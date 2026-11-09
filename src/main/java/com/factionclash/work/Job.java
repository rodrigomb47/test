package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.CampaignData;
import com.factionclash.config.FCConfig;
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
    private int routeFailures;
    /** Steps left while digging out of a dead end (sealed cave, ravine, canopy) and ticks until the next one. */
    private int digSteps, digWait;
    private static final int MAX_DIG_STEPS = 64, DIG_STEP_TICKS = 20;

    protected Job(FactionNpc npc) {
        this.npc = npc;
    }

    public final void tick() {
        if (!(npc.level() instanceof ServerLevel)) {
            return;
        }
        if (survival()) {
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        run();
    }

    private long nextMealTrip, nextToolTrip, lastPack = -100000;

    /**
     * Errands a player would not put off: a spare tool from the backpack when the one in hand broke, a trip to the
     * storehouse for a new one when the work cannot be done by hand, and a trip home for food when hungry with an
     * empty backpack. Each gives up for a while when the storehouse has nothing, so work never stalls on it.
     */
    private boolean survival() {
        BaseState b = base();
        if (b == null) {
            return false;
        }
        NpcRole role = npc.getRole();
        if (role.tool != ToolKind.NONE && !role.tool.matches(npc.tool())) {
            for (int i = 0; i < npc.inventory().getContainerSize(); i++) {
                if (role.tool.matches(npc.inventory().getItem(i))) {
                    npc.equip(npc.inventory().removeItem(i, 1)); // the spare it carries
                    npc.inventory().setChanged();
                    return false;
                }
            }
            if (needsToolToWork(role) && now() >= nextToolTrip) {
                waitTicks = 0;
                status("Going to the storehouse for a new " + role.tool.name().toLowerCase(), "Travelling");
                if (!beforeErrand() || !atStorage()) {
                    return true;
                }
                lastEquipCheck = -100000;
                upgradeEquipment();
                if (!role.tool.matches(npc.tool())) {
                    status("No " + role.tool.name().toLowerCase() + " in the storehouse - waiting for the blacksmith", "Waiting");
                    nextToolTrip = now() + 1200;
                    if (toolMissingSince < 0) {
                        toolMissingSince = now();
                    }
                } else {
                    toolMissingSince = -1;
                }
                return true;
            }
        }
        // hungry, or badly hurt without the 18 food that lets it heal: fetch food like a player would
        boolean needsFood = npc.hunger().foodLevel() <= 6 || npc.getHealth() <= npc.getMaxHealth() * 0.3F && npc.hunger().foodLevel() < 18;
        if (FCConfig.HUNGER.get() && needsFood && now() >= nextMealTrip
                && Inv.count(npc.inventory(), Job::isProvision) == 0 && !npc.isEatingMeal()) {
            if (FactionStorage.count(level(), b, Job::isProvision) == 0) {
                nextMealTrip = now() + 2400; // nothing to eat at home either: work on, and look again later
                return false;
            }
            waitTicks = 0;
            status("Hungry - going to the storehouse for food", "Travelling");
            if (!beforeErrand() || !atStorage()) {
                return true;
            }
            nextMealTrip = now() + 600;
            return true;
        }
        return false;
    }

    /** Roles whose work cannot be done by hand (stone gives nothing without a pickaxe; no fishing without a rod;
     *  soldiers and scouts need their weapon). Woodcutters, farmers and builders can work bare-handed meanwhile. */
    private static boolean needsToolToWork(NpcRole role) {
        return role == NpcRole.MINER || role == NpcRole.FISHERMAN || role.combatant || role == NpcRole.SCOUT
                || role == NpcRole.CAVE_EXPLORER || role == NpcRole.BUTCHER;
    }

    /** Food worth packing: edible, harmless, and not raw meat, fish or potatoes the chef will cook. */
    public static boolean isProvision(ItemStack s) {
        if (s.isEmpty() || s.getFoodProperties(null) == null || s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            return false;
        }
        if (s.is(Items.BEEF) || s.is(Items.PORKCHOP) || s.is(Items.MUTTON) || s.is(Items.CHICKEN) || s.is(Items.RABBIT)
                || s.is(Items.COD) || s.is(Items.SALMON) || s.is(Items.POTATO)) {
            return false;
        }
        return !FactionNpc.isHarmfulFood(s, null);
    }

    /** PROPOSED: meals carried, enough for a day's work (soldiers, scouts and explorers who go far take one more). */
    private int provisionTarget() {
        NpcRole r = npc.getRole();
        if (com.factionclash.campaign.Logistics.rationing(base())) {
            return 1; // rationing: one meal each, the rest stays in the storehouse for everyone
        }
        // hurt: two more, eaten on the spot to get back over 18 food and heal
        int hurt = npc.getHealth() <= npc.getMaxHealth() * 0.5F ? 2 : 0;
        return (r.combatant || r == NpcRole.SCOUT || r == NpcRole.CAVE_EXPLORER ? 3 : 2) + hurt;
    }

    /** Since when the storehouse had no tool for this worker (-1: it has one, or has not looked yet). */
    private long toolMissingSince = -1;

    /** True when this worker has been unable to get its tool for at least {@code ticks}. */
    public boolean missingToolFor(long ticks) {
        return toolMissingSince >= 0 && now() - toolMissingSince >= ticks;
    }

    /** Since when this worker has had no work it can do (-1: busy), and why. Not saved: a reload starts busy. */
    private long idleSince = -1;
    private String idleReason = "";

    /**
     * The worker has nothing it can do: no place to work, nothing left to work on. The time it started is kept
     * across repeated calls, so {@link #idleFor} measures the whole idle stretch; the reason is the latest one.
     */
    protected void markIdle(String reason) {
        if (idleSince < 0) {
            idleSince = now();
        }
        idleReason = reason == null ? "" : reason;
    }

    /** The worker has work again: the idle stretch ends. */
    protected void markBusy() {
        idleSince = -1;
        idleReason = "";
    }

    /** True when the worker has had nothing to do for at least {@code ticks} (the director then retrains it). */
    public boolean idleFor(long ticks) {
        return idleSince >= 0 && now() - idleSince >= ticks;
    }

    /** Why the worker is idle, in words that follow "has had", for example "no place to dig a mine"; empty when busy. */
    public String idleReason() {
        return idleReason;
    }

    /**
     * Called right before a survival errand (a new tool, food) sends the worker to the storehouse. A job whose
     * worker has to leave its workplace a particular way first (a miner climbing out of its mine) does that here and
     * returns false until it is out; the errand then goes on as usual.
     */
    protected boolean beforeErrand() {
        return true;
    }

    /** PROPOSED: night for remote work, dusk to dawn (dayTime 13000-23000), roughly when monsters spawn in the open. */
    static final int NIGHT_START = 13000, NIGHT_END = 23000;
    /** PROPOSED: at night, work further than this many blocks outside the walls waits for daylight. */
    static final int NIGHT_SAFE_MARGIN = 16;

    protected boolean isNight() {
        long day = Math.floorMod(level().getDayTime(), 24000L);
        return day >= NIGHT_START && day < NIGHT_END;
    }

    /**
     * True when {@code p} is too far out to work at night: with remoteWorkInDaylightOnly, between dusk and dawn,
     * more than {@link #NIGHT_SAFE_MARGIN} blocks beyond the walls, where the guards do not reach.
     */
    protected boolean remoteAtNight(BlockPos p) {
        BaseState b = base();
        if (b == null || p == null || !FCConfig.REMOTE_WORK_IN_DAYLIGHT_ONLY.get() || !isNight()) {
            return false;
        }
        return BaseZones.chebyshev(b, p) > BaseZones.half(b) + NIGHT_SAFE_MARGIN;
    }

    /** How many times in a row the current destination could not be reached (0 after arriving). */
    protected int routeFailures() {
        return routeFailures;
    }

    /** At the storehouse: tops up the meals carried, the most filling food first. */
    private void packFood() {
        int need = provisionTarget() - Inv.count(npc.inventory(), Job::isProvision);
        for (int guard = 0; need > 0 && guard < 6; guard++) {
            ItemStack best = FactionStorage.findBest(level(), base(), Job::isProvision, s -> {
                var f = s.getFoodProperties(null);
                return f == null ? 0 : Math.round(f.saturation() * 10 + f.nutrition());
            });
            if (best.isEmpty()) {
                return;
            }
            int got = takeFromStorage(x -> ItemStack.isSameItemSameComponents(x, best), need);
            if (got <= 0) {
                return;
            }
            need -= got;
        }
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

    /**
     * True while the job itself decides when to step aside, fight back or head home (a scout away from home), so the
     * civilians' wounded flight to the station leaves it alone.
     */
    public boolean handlesOwnSafety() {
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
        if (digSteps > 0) {
            return digOut(pos, label);
        }
        NpcNavigator.Result r = npc.navigator().travel(pos, reach, speed);
        switch (r) {
            case ARRIVED:
                routeFailures = 0;
                return true;
            case MOVING:
                status("Walking to " + label, "Travelling");
                return false;
            default:
                // repeated total failure: dig toward the destination instead of waiting forever. Shut in (cave,
                // canopy, the floor of a pit or ravine) it keeps digging until it is out or a route exists, since
                // walking the partial path would only lead back to the same dead end.
                if (++routeFailures >= 2 && com.factionclash.npc.DigOut.shutIn(npc, npc.navigator().routeTarget(pos))) {
                    digSteps = MAX_DIG_STEPS;
                    digWait = 0;
                    npc.navigator().reset();
                    return digOut(pos, label);
                }
                if (routeFailures >= 2 && com.factionclash.npc.DigOut.step(npc, npc.navigator().routeTarget(pos))) {
                    status("Digging a way out toward " + label, "Digging");
                    npc.navigator().reset();
                    return false;
                }
                status("Cannot reach " + label + " (" + npc.navigator().lastProblem() + "), will retry", "Blocked");
                npc.navigator().reset();
                waitFor(100);
                return false;
        }
    }

    private boolean digOut(BlockPos pos, String label) {
        status("Digging a way out toward " + label, "Digging");
        if (--digWait > 0) {
            return false;
        }
        digWait = DIG_STEP_TICKS;
        BlockPos toward = npc.navigator().routeTarget(pos);
        boolean done = !com.factionclash.npc.DigOut.shutIn(npc, toward) || --digSteps <= 0;
        if (!done && digSteps % 4 == 0) {
            done = npc.navigator().canWalkToward(pos); // the staircase met a passage or slope that leads there
        }
        if (!done && !com.factionclash.npc.DigOut.step(npc, toward)) {
            digSteps = 0;
            status("Cannot dig a way out toward " + label + ", will retry", "Blocked");
            waitFor(100);
            return false;
        }
        if (done) {
            digSteps = 0;
            npc.navigator().reset();
        }
        return false;
    }

    protected boolean atStorage() {
        BaseState b = base();
        boolean at = b != null && goTo(b.point(BaseState.STORAGE_STAND), 2.0, "the storehouse");
        if (at && com.factionclash.config.FCConfig.HUNGER.get() && now() - lastPack > 100) {
            lastPack = now();
            packFood(); // like a player heading out: a few meals in the backpack
        }
        return at;
    }

    /** How many of this item the NPC keeps when unloading (job consumables such as seeds or saplings). */
    protected int keep(ItemStack stack) {
        return 0;
    }

    protected boolean hasCargo() {
        return cargoCount() > 0;
    }

    /** Items in the backpack that are cargo: everything except what stays with the NPC (see {@link #keptPerSlot}). */
    protected int cargoCount() {
        int[] kept = keptPerSlot();
        int n = 0;
        for (int i = 0; i < npc.inventory().getContainerSize(); i++) {
            n += npc.inventory().getItem(i).getCount() - kept[i];
        }
        return n;
    }

    /**
     * How many of each backpack slot stay with the NPC when it unloads: what the job keeps ({@link #keep}), the
     * packed meals (the most nourishing ones, up to the provision target) and a miner's spare pickaxe.
     */
    private int[] keptPerSlot() {
        int size = npc.inventory().getContainerSize();
        int[] out = new int[size];
        Map<Integer, Integer> mealKeep = new HashMap<>();
        if (com.factionclash.config.FCConfig.HUNGER.get()) {
            List<Integer> slots = new java.util.ArrayList<>();
            for (int i = 0; i < size; i++) {
                if (isProvision(npc.inventory().getItem(i))) {
                    slots.add(i);
                }
            }
            slots.sort(java.util.Comparator.comparingDouble(i -> -npc.inventory().getItem(i).getFoodProperties(null).saturation()));
            int left = provisionTarget();
            for (int i : slots) {
                int k = Math.min(left, npc.inventory().getItem(i).getCount());
                mealKeep.put(i, k);
                left -= k;
            }
        }
        Map<net.minecraft.world.item.Item, Integer> kept = new HashMap<>();
        boolean spareKept = false;
        for (int i = 0; i < size; i++) {
            ItemStack s = npc.inventory().getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            int already = kept.getOrDefault(s.getItem(), 0);
            int k = keep(s);
            if (npc.getRole() == NpcRole.MINER && npc.getRole().tool.matches(s) && !spareKept) {
                spareKept = true;
                k = Math.max(k, already + 1);
            }
            if (mealKeep.containsKey(i)) {
                k = Math.max(k, already + mealKeep.get(i));
            }
            int keepHere = Math.max(0, Math.min(s.getCount(), k - already));
            kept.put(s.getItem(), already + keepHere);
            out[i] = keepHere;
        }
        return out;
    }

    public enum Unload { DONE, PARTIAL_FULL }

    /** Deposits cargo into faction storage. Must be at the storehouse. Never deletes items. */
    protected Unload unloadNow() {
        BaseState b = base();
        boolean full = false;
        int[] kept = keptPerSlot();
        for (int i = 0; i < npc.inventory().getContainerSize(); i++) {
            ItemStack s = npc.inventory().getItem(i);
            int deposit = s.getCount() - kept[i];
            if (s.isEmpty() || deposit <= 0) {
                continue;
            }
            ItemStack out = s.split(deposit);
            ItemStack rest = FactionStorage.insert(level(), b, out, npc.npcName());
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
    /**
     * Material rank first, then the total of its enchantment levels. Silk Touch counts against a tool, as a player
     * keeps such a pickaxe aside: ore would come out as ore blocks with no experience, leaves without saplings.
     */
    public static int gearScore(ItemStack s, int rank) {
        var ench = s.getEnchantments();
        int levels = 0;
        for (var h : ench.keySet()) {
            if (h.is(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH)) {
                return rank * 100 - 150;
            }
            levels += ench.getLevel(h);
        }
        // nearly used up (under a quarter left): any fresh one of the same material is better
        boolean worn = s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() < s.getMaxDamage() / 4;
        return rank * 100 + levels - (worn ? 50 : 0);
    }

    /**
     * {@link #upgradeEquipment} at once, even if it ran less than a minute ago: for a decision that depends on the
     * best tool the storehouse has (a miner about to let its mine rest until a better pickaxe, say).
     */
    protected void upgradeEquipmentNow() {
        lastEquipCheck = -100000;
        upgradeEquipment();
    }

    protected void upgradeEquipment() {
        if (now() - lastEquipCheck < 1200) {
            return;
        }
        lastEquipCheck = now();
        NpcRole role = npc.getRole();
        BaseState b = base();
        if (role.tool != ToolKind.NONE) {
            // better material first; at the same material, the enchanted one
            int cur = role.tool.matches(npc.tool()) ? gearScore(npc.tool(), ToolKind.tierOf(npc.tool())) : -1;
            ItemStack best = FactionStorage.findBest(level(), b, role.tool::matches, s -> gearScore(s, ToolKind.tierOf(s)));
            if (!best.isEmpty() && gearScore(best, ToolKind.tierOf(best)) > cur) {
                ItemStack got = FactionStorage.extractExact(level(), b, best);
                if (!got.isEmpty()) {
                    npc.equip(got);
                }
            }
        }
        if (role == NpcRole.MINER && role.tool.matches(npc.tool())
                && Inv.count(npc.inventory(), role.tool::matches) == 0) {
            // a spare pickaxe in the backpack: a break deep in the mine does not mean a walk home
            ItemStack spare = FactionStorage.findBest(level(), b, role.tool::matches, s -> gearScore(s, ToolKind.tierOf(s)));
            if (!spare.isEmpty() && ToolKind.tierOf(spare) >= 1) {
                ItemStack got = FactionStorage.extractExact(level(), b, spare);
                ItemStack rest = Crafting.addAll(npc.inventory(), got);
                if (!rest.isEmpty()) {
                    FactionStorage.insert(level(), b, rest);
                }
            }
        }
        if (role == NpcRole.SWORDSMAN && (!npc.hasShield() || gearScore(npc.getOffhandItem(), 1) < 100)) {
            // a swordsman fights sword and board: a shield in the off hand (a fresh one when it is nearly broken)
            ItemStack shield = FactionStorage.findBest(level(), b, s -> s.getItem() instanceof net.minecraft.world.item.ShieldItem, s -> gearScore(s, 1));
            if (!shield.isEmpty() && gearScore(shield, 1) >= 100) {
                ItemStack got = FactionStorage.extractExact(level(), b, shield);
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
            int curDef = cur.getItem() instanceof ArmorItem a ? gearScore(cur, a.getDefense()) : -1;
            Predicate<ItemStack> fits = s -> s.getItem() instanceof ArmorItem a && a.getEquipmentSlot() == slot;
            ItemStack best = FactionStorage.findBest(level(), b, fits, s -> gearScore(s, ((ArmorItem) s.getItem()).getDefense()));
            if (best.isEmpty() || gearScore(best, ((ArmorItem) best.getItem()).getDefense()) <= curDef) {
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
