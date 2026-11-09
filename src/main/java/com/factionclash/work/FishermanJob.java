package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.MapBounds;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.FishingWater;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.ToolKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fisherman (spec 8): fishes real water outside the walls with a fishing rod, like a player. It looks for a pond,
 * lake, river or the sea at least 2 deep within reach of the base (never the farm's irrigation or any water near
 * farmland, see {@link FishingWater}), walks to a dry stand on the bank, casts, waits the vanilla bite time (Lure
 * shortens it, rain on the water speeds it up) and reels in a catch rolled from the vanilla fishing loot table (Luck
 * of the Sea applies; in open water the treasure catches can come up, as for a player). The catch flies to the
 * fisherman like a reeled-in item and is carried to storage, where the chef can cook it. NPCs cannot own a real
 * bobber entity (vanilla ties it to a player), so the cast is shown by the rod swing and the splash.
 *
 * <p>With no water near the base it walks out in eight directions in turn to look further away; when that finds
 * nothing it gives up for a while and has nothing to do, so the director retrains it. A spot that turns out unfit
 * (the water is gone, the stand cannot be reached) is remembered and another is looked for. Between dusk and dawn it
 * does not fish far outside the walls.
 */
public class FishermanJob extends Job {
    enum State { STORAGE, SEARCH, FORAGE, FISH }

    /** PROPOSED: a fisherman searches for water at most this often. */
    static final int SEARCH_TICKS = 600;
    /** PROPOSED: the walk to a spot is given up after this long or this many failed routes, and the spot marked unfit. */
    static final int TRAVEL_GIVE_UP_TICKS = 1200, TRAVEL_ROUTE_FAILURES = 3;
    /** PROPOSED: every this many casts the spot is checked by every rule again (each cast gets a cheap check). */
    static final int FULL_CHECK_CASTS = 20;
    /** PROPOSED: catches are picked up within this many blocks of the stand, on land only. */
    static final int PICKUP_RANGE = 3;
    /** PROPOSED: foraging walks this far out in one of eight directions, looks around this far, then tries the next one. */
    static final int FORAGE_DISTANCE = 72, FORAGE_SCAN = 40, FORAGE_DIRECTIONS = 8, FORAGE_LEG_TICKS = 2400;
    /** PROPOSED: after a full round of foraging finds nothing, the fisherman rests this long before looking again. */
    static final long FORAGE_REST_TICKS = 6000;
    /** PROPOSED: a catch that cannot be reached for this long is left where it lies. */
    static final int CHASE_TICKS = 200;

    private State state = State.STORAGE;
    /** Ticks until a fish bites; -1 when no line is cast. */
    private int bite = -1;
    private long nextSearch;
    private int forageDir, forageFails;
    private long restUntil;
    private int casts;
    private BlockPos forageTarget;
    private long forageStart;
    /**
     * Ticks spent walking to the stand so far (0: at the stand, or not walking there), and when that was last
     * counted. A gap while the job did not run (an errand home for food, a fight) counts as one retry wait at most.
     */
    private int travelTicks;
    private long lastTravelTick = -1;
    private UUID chasing;
    private long chaseStart;
    private final Set<UUID> leftLying = new HashSet<>();

    public FishermanJob(FactionNpc npc) {
        super(npc);
    }

    @Override
    public String workplaceInfo() {
        WorkplaceState w = Workplaces.held(npc, WorkplaceState.Type.FISHERY); // looking never claims
        if (w == null) {
            return "no fishing spot";
        }
        return switch (w.spot) {
            case FishingWater.VALID -> "Fishing spot at " + w.origin.toShortString() + (w.open ? " (open water)" : " (still water)") + ", "
                    + w.totalOutput + " caught";
            case FishingWater.NONE -> "No fishing water within reach";
            default -> "Looking for fishing water";
        };
    }

    /** How many casts this fisherman has made (for the GameTests). */
    public int casts() {
        return casts;
    }

    @Override
    protected void run() {
        BaseState b = base();
        WorkplaceState w = b == null ? null : Workplaces.claim(npc, WorkplaceState.Type.FISHERY);
        if (w == null || b == null) {
            status("No fishing spot", "Idle");
            waitFor(400);
            return;
        }
        if (nightAway(b, w)) {
            return;
        }
        switch (state) {
            case STORAGE -> storage(w);
            case SEARCH -> search(b, w);
            case FORAGE -> forage(b, w);
            case FISH -> fish(b, w);
        }
    }

    /**
     * Between dusk and dawn a fisherman does not work far outside the walls: it brings home what it carries and waits
     * at the storehouse. True while it does that.
     */
    private boolean nightAway(BaseState b, WorkplaceState w) {
        boolean fishingFar = w.spot == FishingWater.VALID && (state == State.FISH || state == State.STORAGE) && remoteAtNight(w.extent);
        boolean foragingFar = state == State.FORAGE && w.spot != FishingWater.VALID && restUntil == 0 && BaseZones.forageAllowed(b)
                && remoteAtNight(forageTargetFor(b));
        if (!fishingFar && !foragingFar) {
            return false;
        }
        bite = -1;
        stopTravel();
        forageTarget = null; // the leg starts again in the morning
        if (state == State.STORAGE || hasCargo()) {
            state = State.STORAGE; // unload first, then wait below on the next ticks
            return false;
        }
        if (!atStorage()) {
            return true;
        }
        status("Home for the night", "Resting");
        if (fishingFar) {
            markBusy(); // it has water to fish in the morning
        }
        waitFor(600);
        return true;
    }

    /** At most this much of a gap in the walk to the stand counts (a "cannot reach" retry waits 100 ticks). */
    private static final int MAX_TRAVEL_GAP = 101;

    private void stopTravel() {
        travelTicks = 0;
        lastTravelTick = -1;
    }

    private void storage(WorkplaceState w) {
        if (!atStorage()) {
            return;
        }
        unloadNow();
        upgradeEquipment();
        if (!ToolKind.ROD.matches(npc.tool())) {
            status("No fishing rod in storage", "Waiting");
            waitFor(400);
            return;
        }
        if (remoteAtNight(w.extent) && w.spot == FishingWater.VALID) {
            status("Home for the night", "Resting");
            markBusy(); // it has water to fish in the morning
            waitFor(600);
            return;
        }
        state = w.spot == FishingWater.VALID ? State.FISH : State.SEARCH;
        bite = -1;
        stopTravel();
    }

    private void search(BaseState b, WorkplaceState w) {
        if (w.spot == FishingWater.VALID) {
            state = State.FISH; // found meanwhile (the director looks too)
            return;
        }
        if (now() < nextSearch) {
            status("Looking for fishing water", "Searching");
            waitFor((int) Math.min(100, nextSearch - now()));
            return;
        }
        nextSearch = now() + SEARCH_TICKS;
        int r = FishingSpots.reach(b, false);
        FishingWater.Spot s = FishingSpots.search(level(), data(), b, b.center, r, r, w.badSpots);
        if (s != null) {
            found(b, w, s);
            return;
        }
        if (!BaseZones.forageAllowed(b)) {
            noWater(b, w); // a GameTest base: no walking off the platform
            return;
        }
        state = State.FORAGE;
        forageTarget = null;
    }

    /** Where the current forage leg goes: 72 blocks out from the centre in the current direction, kept on the map. */
    private BlockPos forageTargetFor(BaseState b) {
        double a = forageDir * Math.PI * 2 / FORAGE_DIRECTIONS;
        BlockPos p = b.center.offset((int) Math.round(Math.cos(a) * FORAGE_DISTANCE), 0, (int) Math.round(Math.sin(a) * FORAGE_DISTANCE));
        return MapBounds.clamp(data(), p, 16);
    }

    /**
     * No water near the base: like a player looking for a lake, walk out about 70 blocks in one direction (a different
     * one each time) and look around there. After eight directions with nothing found it rests, idle.
     */
    private void forage(BaseState b, WorkplaceState w) {
        if (w.spot == FishingWater.VALID) {
            state = State.FISH;
            restUntil = 0;
            return;
        }
        if (restUntil > 0) {
            if (now() < restUntil) {
                status("No fishing water within reach", "Idle");
                markIdle("no fishing water");
                waitFor(1200);
                return;
            }
            restUntil = 0; // rested: look near the base again, then forage another round
            forageFails = 0;
            state = State.SEARCH;
            return;
        }
        if (!BaseZones.forageAllowed(b)) {
            noWater(b, w);
            return;
        }
        if (forageTarget == null) {
            forageTarget = forageTargetFor(b);
            forageStart = now();
        }
        BlockPos t = level().isLoaded(forageTarget) ? level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, forageTarget) : forageTarget;
        status("Looking for fishing water further out (" + t.toShortString() + ")", "Travelling");
        if (goTo(t, 8.0, "water further out") || now() - forageStart > FORAGE_LEG_TICKS) {
            forageTarget = null;
            FishingWater.Spot s = FishingSpots.search(level(), data(), b, npc.blockPosition(), FORAGE_SCAN, FishingSpots.reach(b, true), w.badSpots);
            if (s != null) {
                forageFails = 0;
                found(b, w, s);
                return;
            }
            forageDir = (forageDir + 1) % FORAGE_DIRECTIONS;
            if (++forageFails >= FORAGE_DIRECTIONS) {
                noWater(b, w);
            }
        }
    }

    private void found(BaseState b, WorkplaceState w, FishingWater.Spot s) {
        FishingSpots.apply(data(), w, s);
        FishingSpots.announce(level(), b, w);
        markBusy();
        state = State.FISH;
        bite = -1;
        stopTravel();
        restUntil = 0;
    }

    /** Neither the search nor a round of foraging found water: the base has none within reach, for now. */
    private void noWater(BaseState b, WorkplaceState w) {
        w.spot = FishingWater.NONE;
        w.searched = now();
        w.open = false;
        w.origin = b.center;
        w.extent = b.center;
        data().setDirty();
        restUntil = now() + FORAGE_REST_TICKS;
        forageTarget = null;
        state = State.FORAGE;
        status("No fishing water within reach", "Idle");
        markIdle("no fishing water");
    }

    /** The spot is unfit after all: remember it, look for another. */
    private void badSpot(BaseState b, WorkplaceState w, String why) {
        w.addBadSpot(w.origin);
        w.spot = FishingWater.UNSEARCHED;
        w.open = false;
        w.origin = b.center;
        w.extent = b.center;
        data().setDirty();
        state = State.SEARCH;
        bite = -1;
        stopTravel();
        status(why + " - looking for other water", "Searching");
    }

    private void fish(BaseState b, WorkplaceState w) {
        if (w.spot != FishingWater.VALID) {
            state = State.SEARCH; // also a FISH state loaded from a save whose spot is not known any more
            bite = -1;
            stopTravel();
            return;
        }
        if (Inv.freeSlots(npc.inventory()) <= 1 || cargoCount() >= 32 || !ToolKind.ROD.matches(npc.tool())) {
            state = State.STORAGE;
            bite = -1;
            stopTravel();
            return;
        }
        // catches lie on the bank by the stand; never in the water, never the farm's harvest
        Vec3 stand = Vec3.atBottomCenterOf(w.extent);
        java.util.function.Predicate<ItemEntity> onBank = e -> !e.isInWater() && e.position().distanceToSqr(stand) <= PICKUP_RANGE * PICKUP_RANGE
                && !leftLying.contains(e.getUUID());
        BlockWork.pickUp(npc, s -> true, onBank);
        ItemEntity loose = BlockWork.nearestItem(npc, new AABB(w.extent).inflate(PICKUP_RANGE), s -> true, onBank);
        if (loose != null && npc.position().distanceToSqr(stand) <= (PICKUP_RANGE + 2) * (PICKUP_RANGE + 2)) {
            if (!loose.getUUID().equals(chasing)) {
                chasing = loose.getUUID();
                chaseStart = now();
            } else if (now() - chaseStart > CHASE_TICKS) {
                if (leftLying.size() > 64) {
                    leftLying.clear();
                }
                leftLying.add(chasing); // out of reach: leave it
                chasing = null;
                return;
            }
            goTo(loose.blockPosition(), 0.8, "the catch");
            return;
        }
        chasing = null;
        if (!goTo(w.extent, 1.0, "the fishing spot")) {
            travelTicks += lastTravelTick < 0 ? 0 : (int) Math.min(now() - lastTravelTick, MAX_TRAVEL_GAP);
            lastTravelTick = now();
            // a spot it cannot get to is given up, rather than digging toward a far lake for ever
            if (travelTicks > TRAVEL_GIVE_UP_TICKS || routeFailures() >= TRAVEL_ROUTE_FAILURES) {
                badSpot(b, w, "Could not reach the fishing spot");
            }
            return;
        }
        stopTravel();
        BlockPos water = w.origin;
        lookAt(water);
        if (bite < 0) {
            if (!FishingSpots.stillFishable(level(), data(), b, w)) {
                badSpot(b, w, "The water at the fishing spot changed");
                return;
            }
            casts++;
            if (casts % FULL_CHECK_CASTS == 0 && !FishingSpots.stillValid(level(), data(), b, w)) {
                badSpot(b, w, "The fishing spot is no longer fit");
                return;
            }
            // cast: vanilla waits 100-600 ticks for a fish, then 20-80 more for the bite; Lure shortens the first wait
            ItemStack rod = npc.getMainHandItem();
            float lure = EnchantmentHelper.getFishingTimeReduction(level(), rod, npc);
            bite = Math.max(20, Mth.nextInt(level().random, 100, 600) - (int) (lure * 20) + Mth.nextInt(level().random, 20, 80));
            npc.swing(InteractionHand.MAIN_HAND);
            level().playSound(null, npc.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F);
            status("Fishing - waiting for a bite", "Fishing");
            markBusy();
            return;
        }
        // vanilla counts down one a tick, and one more a quarter of the time while it rains on the water
        bite -= 1 + (level().random.nextFloat() < 0.25F && level().isRainingAt(water.above()) ? 1 : 0);
        if (bite > 0) {
            return;
        }
        bite = -1;
        if (!FishingSpots.waterAt(level(), data(), b, w)) {
            badSpot(b, w, "The water at the fishing spot changed");
            return;
        }
        reelIn(w, water, npc.getMainHandItem());
    }

    private static FishingHook lootHook;

    /**
     * The entity the fishing loot table is rolled for. In open water (with fishingTreasure on) it is a fishing hook,
     * built like a player's but never added to the world, so the treasure entry's open-water condition holds exactly
     * as for a player; elsewhere the fisherman itself, which gives fish and junk only.
     */
    public static Entity lootEntity(boolean open, FactionNpc npc) {
        if (!open || !FCConfig.FISHING_TREASURE.get()) {
            return npc;
        }
        if (lootHook == null || lootHook.level() != npc.level()) {
            lootHook = new FishingHook(EntityType.FISHING_BOBBER, npc.level()); // open water by default
        }
        return lootHook;
    }

    private void reelIn(WorkplaceState w, BlockPos water, ItemStack rod) {
        Vec3 at = Vec3.atCenterOf(water).add(0, 0.5, 0);
        level().sendParticles(ParticleTypes.FISHING, at.x, at.y, at.z, 12, 0.2, 0.05, 0.2, 0.02);
        level().playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.25F, 1.0F);
        Entity looter = lootEntity(w.open, npc);
        if (looter instanceof FishingHook hook) {
            hook.setPos(at); // where the bobber floats: the location conditions (bamboo in jungles) see the water
        }
        LootParams params = new LootParams.Builder(level())
                .withParameter(LootContextParams.ORIGIN, at)
                .withParameter(LootContextParams.TOOL, rod)
                .withParameter(LootContextParams.THIS_ENTITY, looter)
                .withParameter(LootContextParams.ATTACKING_ENTITY, npc)
                .withLuck(EnchantmentHelper.getFishingLuckBonus(level(), rod, npc))
                .create(LootContextParamSets.FISHING);
        LootTable table = level().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        List<ItemStack> catches = new ArrayList<>(table.getRandomItems(params));
        if (level().random.nextDouble() < npc.affix(com.factionclash.logic.Affix.ANGLER)) {
            catches.addAll(table.getRandomItems(params)); // Angler: the loot table is rolled once more
        }
        Vec3 toss = catchMotion(at, npc.position());
        for (ItemStack caught : catches) {
            // the catch is pulled out of the water toward the fisherman, like a reeled-in item, and lands at its feet
            ItemEntity e = new ItemEntity(level(), at.x, at.y, at.z, caught);
            e.setDeltaMovement(toss);
            level().addFreshEntity(e);
            w.totalOutput += caught.getCount();
            com.factionclash.achieve.FactionAchievements.criterion(level().getServer(), npc.getFaction(), "husbandry/fishy_business",
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(caught.getItem()).getPath(), npc.npcName());
        }
        // reeling in a catch costs the rod 1 durability (vanilla), and fishing pays experience (1-6) like a player's
        if (FCConfig.TOOL_WEAR.get() && !npc.sparesGear()) {
            rod.hurtAndBreak(1, npc, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        level().addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(level(), npc.getX(), npc.getY() + 0.5, npc.getZ(),
                npc.getRandom().nextInt(6) + 1));
        npc.swing(InteractionHand.MAIN_HAND);
        npc.awardXp(1);
        data().setDirty();
    }

    /**
     * The motion of a reeled-in catch: the vanilla arc (upward 0.08 times the fourth root of the distance, plus a
     * tenth of the height difference), lifted until it clears the fisherman's feet, with the sideways speed that
     * brings it down at the fisherman's feet under item gravity and drag. Vanilla's own sideways speed (a tenth of the
     * distance) falls short at the 2 to 5 blocks a fisherman stands from its bobber, and the catch would drop back
     * into the water.
     */
    static Vec3 catchMotion(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        double vy = dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08;
        for (int guard = 0; guard < 40 && peak(vy) < dy + 0.3; guard++) {
            vy += 0.04;
        }
        // fly until the arc comes back down to the fisherman's feet, adding up how far a unit sideways speed carries
        double y = 0, v = vy, carry = 0, drag = 1;
        for (int tick = 0; tick < 100; tick++) {
            v -= 0.04;
            y += v;
            v *= 0.98;
            carry += drag;
            drag *= 0.98;
            if (v < 0 && y <= dy) {
                break;
            }
        }
        double speed = carry > 0 ? flat / carry : 0;
        return flat < 1.0E-6 ? new Vec3(0, vy, 0) : new Vec3(dx / flat * speed, vy, dz / flat * speed);
    }

    /** How high an item thrown up at {@code vy} rises (item gravity 0.04 and drag 0.98 a tick). */
    private static double peak(double vy) {
        double y = 0, top = 0;
        for (int tick = 0; tick < 100 && vy > -0.04; tick++) {
            vy -= 0.04;
            y += vy;
            vy *= 0.98;
            top = Math.max(top, y);
        }
        return top;
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state.name());
        tag.putInt("forageDir", forageDir);
        tag.putInt("forageFails", forageFails);
        tag.putLong("restUntil", restUntil);
        tag.putInt("casts", casts);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            state = State.STORAGE;
        }
        // FISH resumes only while the spot is still known: fish() turns it into SEARCH otherwise
        forageDir = Math.floorMod(tag.getInt("forageDir"), FORAGE_DIRECTIONS);
        forageFails = Math.max(0, tag.getInt("forageFails"));
        restUntil = tag.getLong("restUntil");
        casts = Math.max(0, tag.getInt("casts"));
    }
}
