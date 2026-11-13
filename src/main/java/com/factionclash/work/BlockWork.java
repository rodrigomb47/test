package com.factionclash.work;

import com.factionclash.logic.Affix;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Player-like block work for NPCs. Break progress per tick uses the same formula as a player
 * (Player#getDigSpeed and BlockBehaviour#getDestroyProgress): the held tool's speed, Efficiency, Haste/Mining
 * Fatigue, the water and mid-air penalties, and the correct-tool rule (30 vs 100). Broken blocks drop their real
 * loot-table items into the world, which the NPC then picks up within the player pickup range.
 */
public final class BlockWork {
    private BlockWork() {
    }

    public static boolean canHarvest(BlockState state, ItemStack tool) {
        return !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
    }

    /** Fraction of the block broken per tick (1.0 = done), exactly as for a player holding {@code tool}. */
    public static float progressPerTick(FactionNpc npc, BlockState state, BlockPos pos, ItemStack tool) {
        float hardness = state.getDestroySpeed(npc.level(), pos);
        if (hardness < 0) {
            return 0F;
        }
        if (hardness == 0) {
            return 1F;
        }
        float speed = tool.getDestroySpeed(state);
        if (speed > 1.0F) {
            speed += (float) npc.getAttributeValue(Attributes.MINING_EFFICIENCY);
        }
        if (MobEffectUtil.hasDigSpeed(npc)) {
            speed *= 1.0F + (MobEffectUtil.getDigSpeedAmplification(npc) + 1) * 0.2F;
        }
        if (npc.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            speed *= switch (npc.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };
        }
        speed *= (float) npc.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (npc.isEyeInFluid(FluidTags.WATER)) {
            speed *= (float) npc.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
        }
        if (!npc.onGround()) {
            speed /= 5.0F;
        }
        // level-up perk: Diligence makes the NPC work a little faster
        speed *= (float) (1.0 + npc.affix(Affix.DILIGENCE));
        return speed / hardness / (canHarvest(state, tool) ? 30F : 100F);
    }

    /** Ticks a player-equivalent would need to break the block (for tests and status text). */
    public static int ticksToBreak(FactionNpc npc, BlockState state, BlockPos pos, ItemStack tool) {
        float p = progressPerTick(npc, state, pos, tool);
        return p <= 0 ? Integer.MAX_VALUE : (int) Math.ceil(1.0F / p);
    }

    /** Tracks breaking one block over several ticks, with the crack overlay and arm swings a player shows. */
    public static final class Breaker {
        private BlockPos pos;
        private float progress;
        private int ticks;

        public BlockPos target() {
            return pos;
        }

        public void reset(FactionNpc npc) {
            if (pos != null && npc.level() instanceof ServerLevel sl) {
                sl.destroyBlockProgress(npc.getId(), pos, -1);
            }
            pos = null;
            progress = 0;
            ticks = 0;
        }

        /**
         * Continues breaking {@code target} with the NPC's main-hand item. Returns true on the tick the block breaks
         * (drops spawned, block removed). Unbreakable blocks never finish.
         */
        private int gained;

        /** Items that went into the backpack from the last block this breaker broke. */
        public int lastGained() {
            return gained;
        }

        public boolean tick(FactionNpc npc, BlockPos target) {
            return tick(npc, target, s -> true);
        }

        /** As {@link #tick(FactionNpc, BlockPos)}; drops failing {@code keep} are tossed on the ground instead. */
        public boolean tick(FactionNpc npc, BlockPos target, Predicate<ItemStack> keep) {
            ServerLevel level = (ServerLevel) npc.level();
            gained = 0;
            boolean fresh = !target.equals(pos);
            if (fresh) {
                reset(npc);
                pos = target.immutable();
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                reset(npc);
                return true;
            }
            if (fresh && state.is(com.factionclash.registry.ModBlocks.FACTION_HEART.get())) {
                // a raider starts on a heart: its defending heroes' heart_attacked enrage (HEROES_V2 6.10), as for a player
                com.factionclash.hero.HeroBossEvents.heartAttacked(level, pos, npc);
            }
            npc.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            progress += progressPerTick(npc, state, pos, npc.getMainHandItem());
            if (ticks++ % 5 == 0) {
                npc.swing(InteractionHand.MAIN_HAND);
            }
            if (progress < 1.0F) {
                level.destroyBlockProgress(npc.getId(), pos, Math.min(9, (int) (progress * 10)));
                return false;
            }
            gained = breakNow(npc, pos, keep);
            reset(npc);
            return true;
        }
    }

    /**
     * A level affix (Prospector, Green Thumb) paying off: the block's own loot is rolled once more, with the same
     * tool, so the extra is exactly what that block can drop.
     */
    public static int dropExtra(FactionNpc npc, BlockState state, BlockPos pos) {
        ItemStack tool = npc.getMainHandItem();
        if (canHarvest(state, tool) && npc.level() instanceof ServerLevel level) {
            return give(npc, Block.getDrops(state, level, pos, null, npc, tool), s -> true);
        }
        return 0;
    }

    /**
     * Puts drops straight into the worker's backpack, as a player's mined blocks go straight into the inventory.
     * What does not fit, or what the worker does not want (junk it would throw away), lands at its feet.
     */
    static int give(FactionNpc npc, java.util.List<ItemStack> drops, Predicate<ItemStack> keep) {
        int n = 0;
        for (ItemStack d : drops) {
            if (d.isEmpty()) {
                continue;
            }
            if (!keep.test(d)) {
                npc.spawnAtLocation(d);
                continue;
            }
            int before = d.getCount();
            com.factionclash.achieve.FactionAchievements.obtained(npc.getServer(), npc.getFaction(), d, npc.npcName());
            ItemStack rest = Crafting.addAll(npc.inventory(), d);
            n += before - rest.getCount();
            if (!rest.isEmpty()) {
                npc.spawnAtLocation(rest);
            }
        }
        npc.inventory().setChanged();
        return n;
    }

    /**
     * Vanilla tool wear for a broken block (1 per block for a tool, 2 for a sword) - but only when the tool actually
     * helps with that block: a player clears leaves or digs dirt with an empty hand rather than wear out an axe or a
     * pickaxe on them, so the NPC spares its tool the same way.
     */
    public static void wear(FactionNpc npc, ItemStack tool, ServerLevel level, BlockState state, BlockPos pos) {
        if (com.factionclash.config.FCConfig.TOOL_WEAR.get() && !tool.isEmpty() && tool.getDestroySpeed(state) > 1.0F && !npc.sparesGear()) {
            tool.getItem().mineBlock(tool, level, state, pos, npc);
        }
    }

    /**
     * True if a living entity other than {@code self} stands where {@code state} would be placed — a player cannot
     * place a block inside a mob either, and doing so would suffocate it.
     */
    public static boolean obstructed(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.Entity self) {
        net.minecraft.world.phys.shapes.VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        AABB box = shape.bounds().move(pos);
        return !level.getEntities(self, box, e -> e.isAlive() && e.blocksBuilding).isEmpty();
    }

    public static int breakNow(FactionNpc npc, BlockPos pos) {
        return breakNow(npc, pos, s -> true);
    }

    /**
     * Breaks a block as a player would: the block's own loot only with a tool that can harvest it, straight into
     * the backpack (experience orbs and the like still appear in the world). Returns the items gained.
     */
    public static int breakNow(FactionNpc npc, BlockPos pos, Predicate<ItemStack> keep) {
        ServerLevel level = (ServerLevel) npc.level();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return 0;
        }
        ItemStack tool = npc.getMainHandItem();
        BlockEntity be = level.getBlockEntity(pos);
        java.util.List<ItemStack> drops = canHarvest(state, tool) ? Block.getDrops(state, level, pos, be, npc, tool) : java.util.List.of();
        // the experience a player would get (ores: coal, diamond, lapis, redstone...), worked out before the block goes
        int xp = canHarvest(state, tool) ? state.getExpDrop(level, pos, be, npc, tool) : 0;
        level.levelEvent(2001, pos, Block.getId(state));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        wear(npc, tool, level, state, pos);
        npc.exhaust(0.005F); // a player's block break
        if (canHarvest(state, tool)) {
            state.spawnAfterBreak(level, pos, tool, true);
            if (xp > 0) {
                state.getBlock().popExperience(level, pos, xp); // real orbs, picked up like a player would
            }
        }
        npc.swing(InteractionHand.MAIN_HAND);
        return give(npc, drops, keep);
    }

    /**
     * Picks up item entities touching the NPC's pickup box (same as a player: bounding box grown by 1, 0.5, 1).
     * Items that fail {@code keep} are left on the ground. Returns the number of items taken.
     */
    public static int pickUp(FactionNpc npc, Predicate<ItemStack> keep) {
        return pickUp(npc, keep, e -> true);
    }

    /** As {@link #pickUp(FactionNpc, Predicate)}, taking only the item entities that pass {@code where}. */
    public static int pickUp(FactionNpc npc, Predicate<ItemStack> keep, Predicate<ItemEntity> where) {
        int taken = 0;
        AABB box = npc.getBoundingBox().inflate(1.0, 0.5, 1.0);
        for (ItemEntity item : npc.level().getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.hasPickUpDelay() && where.test(e))) {
            ItemStack s = item.getItem();
            if (s.isEmpty() || !keep.test(s)) {
                continue;
            }
            int room = Inv.room(npc.inventory(), s);
            if (room <= 0) {
                continue;
            }
            int n = Math.min(room, s.getCount());
            Crafting.addAll(npc.inventory(), s.copyWithCount(n));
            npc.take(item, n);
            s.shrink(n);
            if (s.isEmpty()) {
                item.discard();
            } else {
                item.setItem(s);
            }
            taken += n;
        }
        return taken;
    }

    /** The nearest dropped item inside {@code area} that the NPC wants and has room for. */
    public static ItemEntity nearestItem(FactionNpc npc, AABB area, Predicate<ItemStack> keep) {
        return nearestItem(npc, area, keep, e -> true);
    }

    public static ItemEntity nearestItem(FactionNpc npc, AABB area, Predicate<ItemStack> keep, Predicate<ItemEntity> where) {
        List<ItemEntity> items = npc.level().getEntitiesOfClass(ItemEntity.class, area,
                e -> e.isAlive() && keep.test(e.getItem()) && Inv.room(npc.inventory(), e.getItem()) > 0 && where.test(e));
        return items.stream().min(Comparator.comparingDouble(npc::distanceToSqr)).orElse(null);
    }

    /** Within the reach a player has from where the NPC stands (eye to block centre). */
    public static boolean inReach(FactionNpc npc, BlockPos pos, double reach) {
        return npc.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) <= reach * reach;
    }
}
