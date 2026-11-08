package com.factionclash.work;

import com.factionclash.campaign.WorkplaceState;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.ToolKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Fisherman (spec 8): stands at the fishing spot with a fishing rod, casts, waits the vanilla bite time (Lure
 * shortens it) and reels in a catch rolled from the vanilla fishing loot table (Luck of the Sea applies). The catch
 * flies to the fisherman like a reeled-in item and is carried to storage, where the chef can cook it. NPCs cannot
 * own a real bobber entity (vanilla ties it to a player), so the cast is shown by the rod swing and the splash.
 */
public class FishermanJob extends Job {
    enum State { STORAGE, FISH }

    private State state = State.STORAGE;
    private int waitTicks = -1;

    public FishermanJob(FactionNpc npc) {
        super(npc);
    }

    private WorkplaceState spot() {
        return Workplaces.claim(npc, WorkplaceState.Type.FISHERY);
    }

    @Override
    public String workplaceInfo() {
        WorkplaceState w = spot();
        return w == null ? "no fishing spot" : "Fishing spot at " + w.origin.toShortString() + ", " + w.totalOutput + " caught";
    }

    @Override
    protected void run() {
        WorkplaceState w = spot();
        if (w == null || base() == null) {
            status("No fishing spot", "Idle");
            waitFor(400);
            return;
        }
        BlockWork.pickUp(npc, s -> true);
        if (state == State.STORAGE) {
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
            state = State.FISH;
            waitTicks = -1;
            return;
        }
        if (Inv.freeSlots(npc.inventory()) <= 1 || cargoCount() >= 32) {
            state = State.STORAGE;
            return;
        }
        BlockPos water = w.origin;
        if (!level().getFluidState(water).is(FluidTags.WATER)) {
            status("The fishing spot has no water", "Blocked");
            waitFor(400);
            return;
        }
        ItemEntity loose = BlockWork.nearestItem(npc, new net.minecraft.world.phys.AABB(w.extent).inflate(4), s -> true);
        if (loose != null) {
            goTo(loose.blockPosition(), 0.8, "the catch");
            return;
        }
        if (!goTo(w.extent, 1.0, "the fishing spot")) {
            return;
        }
        npc.getLookControl().setLookAt(water.getX() + 0.5, water.getY() + 0.5, water.getZ() + 0.5);
        ItemStack rod = npc.getMainHandItem();
        if (waitTicks < 0) {
            // cast: vanilla waits 100-600 ticks for a fish, then 20-80 more for the bite; Lure shortens the first wait
            float lure = EnchantmentHelper.getFishingTimeReduction(level(), rod, npc);
            waitTicks = Math.max(20, Mth.nextInt(level().random, 100, 600) - (int) (lure * 20) + Mth.nextInt(level().random, 20, 80));
            npc.swing(InteractionHand.MAIN_HAND);
            level().playSound(null, npc.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F);
            status("Fishing - waiting for a bite", "Fishing");
            return;
        }
        if (--waitTicks > 0) {
            return;
        }
        waitTicks = -1;
        reelIn(w, water, rod);
    }

    private void reelIn(WorkplaceState w, BlockPos water, ItemStack rod) {
        Vec3 at = Vec3.atCenterOf(water).add(0, 0.5, 0);
        level().sendParticles(ParticleTypes.FISHING, at.x, at.y, at.z, 12, 0.2, 0.05, 0.2, 0.02);
        level().playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.25F, 1.0F);
        LootParams params = new LootParams.Builder(level())
                .withParameter(LootContextParams.ORIGIN, at)
                .withParameter(LootContextParams.TOOL, rod)
                .withParameter(LootContextParams.THIS_ENTITY, npc)
                .withLuck(EnchantmentHelper.getFishingLuckBonus(level(), rod, npc))
                .create(LootContextParamSets.FISHING);
        LootTable table = level().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        for (ItemStack caught : table.getRandomItems(params)) {
            // the catch is pulled out of the water toward the fisherman, like a reeled-in item
            ItemEntity e = new ItemEntity(level(), at.x, at.y, at.z, caught);
            double dx = npc.getX() - at.x, dy = npc.getY() - at.y, dz = npc.getZ() - at.z;
            e.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
            level().addFreshEntity(e);
            w.totalOutput += caught.getCount();
            com.factionclash.achieve.FactionAchievements.criterion(level().getServer(), npc.getFaction(), "husbandry/fishy_business",
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(caught.getItem()).getPath(), npc.npcName());
        }
        // reeling in a catch costs the rod 1 durability (vanilla), and fishing pays experience (1-6) like a player's
        if (com.factionclash.config.FCConfig.TOOL_WEAR.get()) {
            rod.hurtAndBreak(1, npc, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        level().addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(level(), npc.getX(), npc.getY() + 0.5, npc.getZ(),
                npc.getRandom().nextInt(6) + 1));
        npc.swing(InteractionHand.MAIN_HAND);
        npc.awardXp(1);
        data().setDirty();
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state.name());
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        state = State.STORAGE;
    }
}
