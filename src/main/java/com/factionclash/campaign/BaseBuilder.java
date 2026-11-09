package com.factionclash.campaign;

import com.factionclash.FactionClash;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.FishingWater;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.storage.FactionStorage;
import com.factionclash.storage.StorageCategory;
import com.factionclash.structure.BaseLayout;
import com.factionclash.structure.Blueprint;
import com.factionclash.structure.LairLayout;
import com.factionclash.structure.Terrain;
import com.factionclash.structure.TreePlotLayout;
import com.factionclash.util.ChunkLoading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds starting settlements and lairs (spec 3). Starter supplies are granted exactly once per base. */
public final class BaseBuilder {
    private BaseBuilder() {
    }

    public static BaseState buildSettlement(ServerLevel level, CampaignData data, FactionType faction, BlockPos center) {
        int y0 = center.getY();
        int cx = center.getX(), cz = center.getZ();
        int h = BaseLayout.HALF;
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        Terrain.level(level, cx - h - 1, cz - h - 1, cx + h + 1, cz + h + 1, y0, 26, grass);
        int plotFar = BaseLayout.PLOT_ORIGIN_X - TreePlotLayout.depth(1) - 2;
        int plotHalf = TreePlotLayout.half(1) + 2;
        Terrain.level(level, cx + plotFar, cz - plotHalf, cx - h - 2, cz + plotHalf, y0, 24, grass);
        // slope the surroundings into the pads so nobody gets trapped in a cut or below a fill
        Terrain.skirt(level, java.util.List.of(
                new int[]{cx - h - 1, cz - h - 1, cx + h + 1, cz + h + 1},
                new int[]{cx + plotFar, cz - plotHalf, cx - h - 2, cz + plotHalf}), y0 - 1, 5, grass);

        BaseState base = new BaseState();
        base.faction = faction;
        base.kind = BaseState.Kind.SETTLEMENT;
        base.center = center.immutable();
        base.name = faction.displayName + " Keep";
        data.addBase(base);

        Blueprint bp = BaseLayout.generate(faction, center, level.random);
        // no mine is built with the base: each miner chooses its own place and digs its own shaft

        WorkplaceState plot = new WorkplaceState();
        plot.type = WorkplaceState.Type.TREE_PLOT;
        plot.origin = center.offset(BaseLayout.PLOT_ORIGIN_X, 0, 0);
        plot.facing = Direction.WEST;
        data.addWorkplace(base, plot);
        java.util.Set<BlockPos> workplaceManaged = new java.util.HashSet<>(TreePlotLayout.fence(plot, 1));
        for (BlockPos p : workplaceManaged) {
            bp.setAbs(p, Blocks.OAK_FENCE.defaultBlockState());
        }

        WorkplaceState farm = new WorkplaceState();
        farm.type = WorkplaceState.Type.FARM;
        farm.origin = bp.markers.get("farm_min");
        farm.extent = bp.markers.get("farm_max");
        data.addWorkplace(base, farm);

        bp.placeAll(level);
        for (BlockPos p : TreePlotLayout.spots(plot)) {
            level.setBlock(p.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(p, Blocks.OAK_SAPLING.defaultBlockState(), Block.UPDATE_ALL);
        }
        // two of the starting saplings are already grown so the woodcutter can start at once
        List<BlockPos> spots = TreePlotLayout.spots(plot);
        for (int i = 0; i < 2 && i < spots.size(); i++) {
            BlockPos p = spots.get(i);
            for (int k = 0; k < 6 && level.getBlockState(p).getBlock() instanceof SaplingBlock sb; k++) {
                sb.advanceTree(level, p, level.getBlockState(p), level.random);
            }
        }

        base.points.putAll(bp.markers);
        ensureWorkplaces(data, base);
        bp.chests.forEach((cat, list) -> base.storage(cat).addAll(list));
        base.beds.addAll(bp.beds);
        base.expansionLots.addAll(bp.lots);
        base.storageExpansionSlots.addAll(bp.storageSlots);
        // forced, randomly ticking work belt: groves and nearby mines keep ticking (the same box older bases have)
        BlockPos max = base.point(BaseState.MAX);
        base.points.put(BaseState.MAX, new BlockPos(cx + BaseLayout.WORK_AREA_EAST, max.getY(), max.getZ()));
        BlockPos min = base.point(BaseState.MIN);
        base.points.put(BaseState.MIN, new BlockPos(cx - BaseLayout.WORK_AREA_WEST,
                min.getY(), Math.min(min.getZ(), cz - TreePlotLayout.half(3) - 3)));

        Map<BlockPos, BlockState> plan = repairablePart(bp);
        plan.keySet().removeAll(workplaceManaged); // plot fences move when the woodcutter enlarges the plot
        base.structures.add(StructureRecord.of("Settlement", plan));

        spawnAnimals(level, bp.markers.get("pen"));
        grantStarterSupplies(level, data, base);
        spawnStartingRoster(level, data, base);
        ChunkLoading.forceBase(level, base, true);
        data.setDirty();
        FactionClash.LOGGER.info("Built {} at {} (y0={})", base.name, center.toShortString(), y0);
        return base;
    }

    /**
     * The settlement's standing workplaces, kept up every director period (it also brings bases from older saves up
     * to date): the pen (centre + gate) and one fishing spot. A missing fishing spot is created as not searched yet;
     * while a fishing spot has no known water its bobber and stand wait at the base centre, as a placeholder, until
     * the director or a fisherman finds real water outside the walls. It is never put on the farm's irrigation.
     */
    public static void ensureWorkplaces(CampaignData data, BaseState base) {
        boolean hasPen = false, hasFishery = false;
        for (int id : base.workplaces) {
            WorkplaceState w = data.workplace(id);
            if (w != null && w.type == WorkplaceState.Type.PEN) {
                hasPen = true;
            }
            if (w != null && w.type == WorkplaceState.Type.FISHERY) {
                hasFishery = true;
                if (w.spot != FishingWater.VALID && (!base.center.equals(w.origin) || !base.center.equals(w.extent))) {
                    w.origin = base.center; // an old save's farm channel, or a spot found unfit
                    w.extent = base.center;
                    data.setDirty();
                }
            }
        }
        BlockPos penCentre = base.point("pen");
        if (!hasPen && penCentre != null) {
            WorkplaceState pen = new WorkplaceState();
            pen.type = WorkplaceState.Type.PEN;
            pen.origin = penCentre;
            pen.extent = penCentre.offset(0, 0, -3); // the fence gate on the pen's north side
            data.addWorkplace(base, pen);
        }
        if (!hasFishery) {
            WorkplaceState fish = new WorkplaceState();
            fish.type = WorkplaceState.Type.FISHERY;
            fish.spot = FishingWater.UNSEARCHED;
            fish.origin = base.center;
            fish.extent = base.center;
            data.addWorkplace(base, fish);
        }
        // step 6: a missing TREE_PLOT is created here too (woodV 1, no fence)
    }

    /** Plan blocks that builders keep repaired: buildings, walls, paths. Crops, soil and water are excluded. */
    private static Map<BlockPos, BlockState> repairablePart(Blueprint bp) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        bp.blocks.forEach((p, s) -> {
            if (s.isAir() || s.is(Blocks.WATER) || s.is(Blocks.FARMLAND) || s.is(BlockTags.CROPS) || s.is(Blocks.GRASS_BLOCK)
                    || s.is(BlockTags.BEDS) || s.is(Blocks.OAK_WALL_SIGN)) {
                return;
            }
            out.put(p, s);
        });
        return out;
    }

    private static void spawnAnimals(ServerLevel level, BlockPos pen) {
        if (pen == null) {
            return;
        }
        for (EntityType<? extends Animal> type : List.of(EntityType.COW, EntityType.COW, EntityType.PIG, EntityType.PIG)) {
            Animal a = type.create(level);
            if (a != null) {
                a.moveTo(pen.getX() + 0.5 + level.random.nextInt(5) - 2, pen.getY(), pen.getZ() + 0.5 + level.random.nextInt(3) - 1,
                        level.random.nextFloat() * 360F, 0F);
                a.setPersistenceRequired();
                level.addFreshEntity(a);
            }
        }
    }

    /** Finite, one-time starter supplies (PROPOSED amounts). The flag is saved, so reloads never duplicate them. */
    public static void grantStarterSupplies(ServerLevel level, CampaignData data, BaseState base) {
        if (base.starterGranted) {
            return;
        }
        List<ItemStack> supplies = List.of(
                new ItemStack(Items.BREAD, 24), new ItemStack(Items.COOKED_BEEF, 8), new ItemStack(Items.BEEF, 6),
                new ItemStack(Items.PORKCHOP, 6), new ItemStack(Items.POTATO, 16), new ItemStack(Items.CARROT, 8),
                new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.COAL, 16), new ItemStack(Items.STICK, 16),
                new ItemStack(Items.WHEAT_SEEDS, 24), new ItemStack(Items.OAK_SAPLING, 8), new ItemStack(Items.STRING, 6),
                new ItemStack(Items.FLINT, 2), new ItemStack(Items.FEATHER, 2), new ItemStack(Items.RAW_IRON, 4),
                new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.OAK_PLANKS, 48),
                new ItemStack(Items.TORCH, 16), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_AXE),
                new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 4), new ItemStack(Items.LAPIS_LAZULI, 6));
        for (ItemStack s : supplies) {
            ItemStack rest = FactionStorage.insert(level, base, s);
            if (!rest.isEmpty()) {
                FactionClash.LOGGER.warn("Starter supply did not fit: {}", rest);
            }
        }
        base.starterGranted = true;
        data.setDirty();
    }

    private static void spawnStartingRoster(ServerLevel level, CampaignData data, BaseState base) {
        BlockPos spawn = base.point(BaseState.SPAWN);
        for (NpcRole role : NpcRole.startingRoster()) {
            FactionNpc npc = FactionNpc.create(level, base.faction, role, base, spawn.offset(level.random.nextInt(5) - 2, 0, level.random.nextInt(3) - 1));
            switch (role) {
                case BUILDER -> npc.equip(new ItemStack(Items.STONE_SHOVEL));
                case MINER -> npc.equip(new ItemStack(Items.STONE_PICKAXE));
                case FARMER -> npc.equip(new ItemStack(Items.STONE_HOE));
                case WOODCUTTER -> npc.equip(new ItemStack(Items.STONE_AXE));
                case SCOUT -> {
                    npc.equip(new ItemStack(Items.STONE_SWORD));
                    npc.equip(new ItemStack(Items.LEATHER_BOOTS));
                }
                case SWORDSMAN -> {
                    npc.equip(new ItemStack(Items.STONE_SWORD));
                    npc.equip(new ItemStack(Items.LEATHER_CHESTPLATE));
                    npc.equip(new ItemStack(Items.LEATHER_HELMET));
                }
                default -> {
                }
            }
            level.addFreshEntity(npc);
            Recruitment.register(data, npc, base);
        }
    }

    public static BaseState buildLair(ServerLevel level, CampaignData data, BlockPos center) {
        int h = LairLayout.HALF + 1;
        Terrain.level(level, center.getX() - h, center.getZ() - h, center.getX() + h, center.getZ() + h, center.getY(), 16,
                Blocks.COARSE_DIRT.defaultBlockState());
        Blueprint bp = LairLayout.generate(center, level.random);
        bp.placeAll(level);
        BaseState lair = new BaseState();
        lair.faction = FactionType.HOLLOW;
        lair.kind = BaseState.Kind.LAIR;
        lair.center = center.immutable();
        lair.name = "Hollow Lair";
        data.addBase(lair);
        lair.points.putAll(bp.markers);
        lair.lairCap = FCConfig.LAIR_BASE_CAP.get();
        lair.lastLairGrowth = level.getGameTime();
        lair.lastLairRaid = level.getGameTime();
        ChunkLoading.forceBase(level, lair, true);
        for (int i = 0; i < lair.lairCap; i++) {
            spawnLairMob(level, data, lair);
        }
        data.faction(FactionType.HOLLOW);
        data.setDirty();
        FactionClash.LOGGER.info("Built Hollow lair at {}", center.toShortString());
        return lair;
    }

    public static final String LAIR_TAG = "factionclash_lair";

    /** Hollow monsters are ordinary vanilla mobs with their usual behaviour, tied to a lair. */
    public static Mob spawnLairMob(ServerLevel level, CampaignData data, BaseState lair) {
        EntityType<? extends Mob> type = level.random.nextFloat() < 0.6F ? EntityType.ZOMBIE : EntityType.SKELETON;
        Mob mob = type.create(level);
        if (mob == null) {
            return null;
        }
        BlockPos at = lair.point(BaseState.SPAWN);
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
        DifficultyInstance diff = level.getCurrentDifficultyAt(at);
        mob.finalizeSpawn(level, diff, MobSpawnType.EVENT, null);
        if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
        }
        mob.setPersistenceRequired();
        CompoundTag tag = mob.getPersistentData();
        tag.putInt(LAIR_TAG, lair.id);
        if (mob instanceof net.minecraft.world.entity.PathfinderMob pm) {
            pm.restrictTo(lair.center, 10);
        }
        level.addFreshEntity(mob);
        lair.lairMobs.add(mob.getUUID());
        data.setDirty();
        return mob;
    }
}
