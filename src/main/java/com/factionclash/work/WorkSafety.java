package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.MapBounds;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.structure.MineLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where workers may dig and fell: the one set of rules miners, cave miners and woodcutters follow so that nobody
 * breaks into a base, another faction's land, another mine's stairs or a structure of the world (strongholds,
 * trial chambers, ancient cities, villages), and the registry of places reserved for one kind of work.
 */
public final class WorkSafety {
    private WorkSafety() {
    }

    /** PROPOSED: no digging this far (Chebyshev) beyond a settlement's or lair's walls, from this far below its floor up. */
    static final int UNDER_BASE_MARGIN = 2, UNDER_SETTLEMENT_DEPTH = 48, UNDER_LAIR_DEPTH = 16;
    /** PROPOSED: other factions' land (their MIN/MAX box) plus this margin is never dug or felled. */
    static final int FOREIGN_MARGIN = 8;
    /** PROPOSED: wood this close (Chebyshev) to a settlement's walls is never felled; a known village keeps 32. */
    static final int WALLS_WOOD_MARGIN = 2, VILLAGE_WOOD_RADIUS = 32;

    /**
     * True if a worker of {@code own} working {@code ownMine} (either may be null) may break the block at
     * {@code pos}. It may not when the block is unbreakable, holds a block entity (chests, spawners, vaults, beds,
     * shriekers), is a built block of the world ({@link #structural}), is part of any base's plan, lies under a
     * settlement or lair (just beyond its walls, from 48 or 16 blocks below its floor up), lies in another base's land,
     * is part of another mine's stairs, or lies in the deep dark, so ancient cities stay as they are.
     */
    public static boolean mayDig(ServerLevel level, BlockPos pos, @Nullable BaseState own, @Nullable WorkplaceState ownMine) {
        BlockState s = level.getBlockState(pos);
        if (s.getDestroySpeed(level, pos) < 0 || s.hasBlockEntity() || structural(s)) {
            return false;
        }
        CampaignData data = CampaignData.get(level.getServer());
        long key = pos.asLong();
        for (BaseState b : data.bases.values()) {
            if (b.destroyed) {
                continue;
            }
            if (b.planIndex().containsKey(key)) {
                return false;
            }
            if (!b.planIndex().isEmpty() && BaseZones.insideWalls(b, pos, UNDER_BASE_MARGIN)
                    && pos.getY() >= b.center.getY() - (b.isSettlement() ? UNDER_SETTLEMENT_DEPTH : UNDER_LAIR_DEPTH)) {
                return false;
            }
        }
        if (BaseZones.inForeignArea(data, own == null ? Integer.MIN_VALUE : own.id, pos, FOREIGN_MARGIN)) {
            return false;
        }
        for (WorkplaceState w : data.workplaces.values()) {
            if (w.type == WorkplaceState.Type.MINE && w != ownMine && w.origin != null
                    && Math.abs(pos.getX() - w.origin.getX()) <= 6 && Math.abs(pos.getZ() - w.origin.getZ()) <= 6
                    && MineLayout.nearStairs(w, pos)) {
                return false;
            }
        }
        return !level.getBiome(pos).is(Biomes.DEEP_DARK);
    }

    private static final Map<Block, Boolean> STRUCTURAL = new ConcurrentHashMap<>();

    /**
     * Built blocks of the world's structures that a miner leaves alone, as a careful player would: every kind of
     * bricks and tiles (stone, mossy, cracked and chiseled stone bricks, deepslate bricks and tiles, tuff, mud,
     * nether, end stone, prismarine, polished blackstone and quartz bricks, plain bricks, and their stairs, slabs and
     * walls), chiseled and polished deepslate, polished and chiseled tuff, quartz blocks, purpur, reinforced deepslate,
     * the sculk family, and copper grates, bulbs and chiseled copper. Mineshaft planks and rails and infested stone
     * stay diggable, as for a player.
     */
    public static boolean structural(BlockState s) {
        return STRUCTURAL.computeIfAbsent(s.getBlock(), WorkSafety::structuralBlock);
    }

    private static boolean structuralBlock(Block b) {
        if (b == Blocks.REINFORCED_DEEPSLATE || b == Blocks.CHISELED_DEEPSLATE || b == Blocks.CHISELED_TUFF
                || b.defaultBlockState().is(BlockTags.STONE_BRICKS)) {
            return true;
        }
        String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
        return path.contains("brick") || path.contains("deepslate_tile") || path.startsWith("polished_deepslate")
                || path.startsWith("polished_tuff") || (path.contains("quartz") && !path.contains("ore"))
                || path.contains("purpur") || path.contains("sculk") || path.contains("copper_grate")
                || path.contains("copper_bulb") || path.contains("chiseled_copper");
    }

    /**
     * Blocks that show a tree belongs to something built (a house corner, a fence, a lamp post): planks, wooden
     * slabs, stairs, fences, gates, doors and trapdoors, walls, glass and panes, wool and carpets, beds, cobblestone,
     * every structural block ({@link #structural}), bookshelves, hay bales, torches, lanterns, signs and rails.
     * Block entities count too, but the caller checks those.
     */
    public static boolean builtBlock(BlockState s) {
        return s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.FENCES)
                || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.WOODEN_DOORS) || s.is(BlockTags.WOODEN_TRAPDOORS)
                || s.is(BlockTags.WALLS) || s.is(Tags.Blocks.GLASS_BLOCKS) || s.is(Tags.Blocks.GLASS_PANES) || s.is(BlockTags.WOOL)
                || s.is(BlockTags.WOOL_CARPETS) || s.is(BlockTags.BEDS) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.COBBLESTONE_STAIRS)
                || s.is(Blocks.COBBLESTONE_SLAB) || structural(s) || s.is(Blocks.BOOKSHELF) || s.is(Blocks.HAY_BLOCK)
                || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH)
                || s.is(Blocks.LANTERN) || s.is(Blocks.SOUL_LANTERN) || s.is(BlockTags.ALL_SIGNS) || s.is(BlockTags.RAILS);
    }

    /** True if {@code pos} is part of the plan of any base still standing (its walls, houses, floors). */
    public static boolean planned(CampaignData data, BlockPos pos) {
        long key = pos.asLong();
        for (BaseState b : data.bases.values()) {
            if (!b.destroyed && b.planIndex().containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if wood at {@code pos} is not the woodcutter's to fell: part of any base's plan, within 2 blocks of a
     * settlement's walls (its own included), in another base's land (8 around its MIN/MAX box, 16 for a lair), off
     * the campaign map, or within 32 blocks of a village the faction knows of. The own base's work belt (its MIN/MAX
     * box) does not protect anything.
     */
    public static boolean protectedWood(ServerLevel level, CampaignData data, @Nullable BaseState own, BlockPos pos) {
        for (BaseState b : data.bases.values()) {
            if (b.destroyed) {
                continue;
            }
            if (b.planIndex().containsKey(pos.asLong()) || (b.isSettlement() && BaseZones.insideWalls(b, pos, WALLS_WOOD_MARGIN))) {
                return true;
            }
        }
        if (BaseZones.inForeignArea(data, own == null ? Integer.MIN_VALUE : own.id, pos, FOREIGN_MARGIN)) {
            return true;
        }
        if (!MapBounds.inside(data, pos, 0)) {
            return true;
        }
        return own != null && BaseZones.nearKnownVillage(data.factions.get(own.faction), pos, VILLAGE_WOOD_RADIUS);
    }

    /** The kinds of work that keep a place free of each other. */
    public enum Use { MINE_SITE, GROVE, FISH_STAND }

    /**
     * The one registry of reserved places: true if {@code p} is kept free of work {@code u}. A grove sapling keeps 6
     * away from any mine origin and off the base's road strip (|dz| at most 3 for |dx| up to the walls plus 30); a
     * fisherman's stand keeps 8 away from any mine origin (Chebyshev).
     */
    public static boolean reserved(CampaignData d, BaseState own, BlockPos p, Use u) {
        // step 4: MINE_SITE within 12 of a fishery origin or extent and within 6 of a woodland sapling or replant entry
        // step 5: GROVE within 6 of a VALID fishery origin or extent
        // step 6: FISH_STAND within 1 of a woodland sapling
        if (u == Use.GROVE && own != null) {
            int dx = p.getX() - own.center.getX(), dz = p.getZ() - own.center.getZ();
            if (Math.abs(dz) <= 3 && Math.abs(dx) <= BaseZones.half(own) + 30) {
                return true;
            }
        }
        int mineRadius = switch (u) {
            case GROVE -> 6;
            case FISH_STAND -> 8;
            case MINE_SITE -> -1;
        };
        if (mineRadius >= 0) {
            for (WorkplaceState w : d.workplaces.values()) {
                if (w.type == WorkplaceState.Type.MINE && w.origin != null
                        && Math.max(Math.abs(p.getX() - w.origin.getX()), Math.abs(p.getZ() - w.origin.getZ())) <= mineRadius) {
                    return true;
                }
            }
        }
        return false;
    }
}
