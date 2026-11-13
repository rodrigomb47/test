package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.logic.CaveSurvey;
import com.factionclash.logic.MinePlanner;
import com.factionclash.logic.MineSiteRules;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcNavigator;
import com.factionclash.structure.MineLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** World-facing helpers shared by cave explorers and the miners who work surveyed caves. */
public final class CaveWork {
    /** PROPOSED: how far an explorer can make out ore in a lit or dark cave (blocks from its eyes). */
    public static final int SIGHT = 10;
    /** PROPOSED: caves farther than this from the base centre are not surveyed (long trips with heavy cargo). */
    public static final int MAX_CAVE_DISTANCE = 128;

    private CaveWork() {
    }

    /**
     * True if {@code pos} lies in the base itself (its MIN/MAX box and 16 around it) or in one of the base's own mines
     * (worked out or not): the ground its tunnels may take up and a little around it, from the surface down to a few
     * blocks under its shaft. "Caves" there are the base's cellars and its own tunnels, not natural caves.
     */
    public static boolean ownWorkings(CampaignData data, BaseState b, BlockPos pos) {
        if (b.contains(pos, 16)) {
            return true;
        }
        for (int id : b.workplaces) {
            WorkplaceState w = data.workplace(id);
            if (w != null && w.type == WorkplaceState.Type.MINE && w.origin != null && inMineWorkings(w, pos)) {
                return true;
            }
        }
        return false;
    }

    /** PROPOSED: how far around a mine's corridor (and below its shaft) its workings count as the base's own. */
    static final int WORKINGS_MARGIN = 4, WORKINGS_DEPTH = 6;

    /** The mine's corridor widened by {@link #WORKINGS_MARGIN}, from 2 above the mouth to 6 under the shaft (at least 6 steps deep). */
    static boolean inMineWorkings(WorkplaceState w, BlockPos pos) {
        int y0 = w.origin.getY();
        if (pos.getY() > y0 + 2 || pos.getY() < y0 - Math.max(w.bottom, MinePlanner.MIN_DEPTH) - WORKINGS_DEPTH) {
            return false;
        }
        int f = MineLayout.forwardOf(w, pos), s = MineLayout.sideOf(w, pos);
        int end = MineSiteRules.corridorEnd(MineLayout.reachRounds(w));
        return f >= MineSiteRules.CORRIDOR_BACK - 2 && f <= end + WORKINGS_MARGIN
                && s >= MineSiteRules.CORRIDOR_SIDE_MIN - WORKINGS_MARGIN
                && s <= MineSiteRules.CORRIDOR_SIDE_MAX + WORKINGS_MARGIN;
    }

    /** Nothing to bump into and no fluid: air, torches, plants. */
    public static boolean open(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty() && !NpcNavigator.isDangerous(s);
    }

    /** An NPC can stand here: solid floor, room for feet and head, no fluid, nothing dangerous. */
    public static boolean standable(ServerLevel level, BlockPos p) {
        BlockState below = level.getBlockState(p.below());
        return open(level, p) && open(level, p.above()) && !below.getCollisionShape(level, p.below()).isEmpty()
                && below.getFluidState().isEmpty() && !NpcNavigator.isDangerous(below);
    }

    /**
     * The cave as the survey sees it: standable cells under cover within {@code range} of the entrance. Open-sky
     * cells only count right at the entrance or below it nearby (the mouth or pit leading in), so the survey does
     * not wander off over the surface.
     */
    public static CaveSurvey.Cells cells(ServerLevel level, BlockPos entrance, int range) {
        return new CaveSurvey.Cells() {
            @Override
            public boolean standable(int x, int y, int z) {
                BlockPos p = new BlockPos(x, y, z);
                if (!p.closerThan(entrance, range) || !level.isLoaded(p) || !CaveWork.standable(level, p)) {
                    return false;
                }
                return !level.canSeeSky(p) || p.closerThan(entrance, 3) || (y < entrance.getY() && p.closerThan(entrance, 12));
            }

            @Override
            public boolean open(int x, int y, int z) {
                BlockPos p = new BlockPos(x, y, z);
                return level.isLoaded(p) && CaveWork.open(level, p);
            }
        };
    }

    public static boolean dark(ServerLevel level, BlockPos p) {
        return level.getBrightness(LightLayer.BLOCK, p) < 8 && level.getBrightness(LightLayer.SKY, p) < 8;
    }

    /** Places one torch from the NPC's backpack on the floor at {@code p}, as a player lights a cave. */
    public static boolean placeTorch(FactionNpc npc, BlockPos p) {
        ServerLevel level = (ServerLevel) npc.level();
        BlockState torch = Blocks.TORCH.defaultBlockState();
        if (!level.getBlockState(p).isAir() || !torch.canSurvive(level, p)) {
            return false;
        }
        if (!Inv.consume(npc.inventory(), s -> s.is(Items.TORCH), 1)) {
            return false;
        }
        level.setBlock(p, torch, Block.UPDATE_ALL);
        com.factionclash.hero.HeroBossEvents.npcPlaced(npc, p, torch); // a light placed near a hero (6.9, as a player's)
        level.playSound(null, p, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
        npc.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /**
     * Ore blocks the NPC can actually see from where it stands: within {@link #SIGHT} blocks of its eyes, with at
     * least one face open to the cave, and a clear line of sight to that face (nothing solid in between).
     */
    public static List<BlockPos> visibleOres(ServerLevel level, FactionNpc npc) {
        List<BlockPos> out = new ArrayList<>();
        Vec3 eye = npc.getEyePosition();
        BlockPos c = BlockPos.containing(eye);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-SIGHT, -SIGHT, -SIGHT), c.offset(SIGHT, SIGHT, SIGHT))) {
            if (Vec3.atCenterOf(p).distanceToSqr(eye) > SIGHT * SIGHT || !MinerJob.isOre(level.getBlockState(p))) {
                continue;
            }
            for (Direction d : Direction.values()) {
                BlockPos face = p.relative(d);
                if (!open(level, face)) {
                    continue;
                }
                Vec3 aim = Vec3.atCenterOf(p).add(d.getStepX() * 0.49, d.getStepY() * 0.49, d.getStepZ() * 0.49);
                BlockHitResult hit = level.clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc));
                if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(p)) {
                    out.add(p.immutable());
                    break;
                }
            }
        }
        return out;
    }

    /** "5 iron, 3 coal, 1 diamond" for a set of ore positions (read while their chunks are loaded). */
    public static String oreSummary(ServerLevel level, Iterable<Long> ores) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (long l : ores) {
            BlockPos p = BlockPos.of(l);
            if (!level.isLoaded(p)) {
                counts.merge("ore", 1, Integer::sum);
                continue;
            }
            counts.merge(oreName(level.getBlockState(p)), 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).forEach(e -> {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(e.getValue()).append(' ').append(e.getKey());
        });
        return sb.isEmpty() ? "no ore seen" : sb.toString();
    }

    public static String oreName(BlockState s) {
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return id.replace("deepslate_", "").replace("_ore", "").replace("nether_", "").replace('_', ' ');
    }

    /**
     * Finds how to walk into the cave around {@code cavity}: flood-fills the cave air (not crossing into open sky)
     * and returns a standable spot open to the sky where the cave meets the surface, or null if this cave has no
     * natural opening nearby (closed caves are not entered; explorers do not dig).
     */
    public static BlockPos findEntrance(ServerLevel level, BlockPos cavity, BlockPos from) {
        return findEntrance(level, cavity, from, 40000);
    }

    public static BlockPos findEntrance(ServerLevel level, BlockPos cavity, BlockPos from, int maxCells) {
        if (level.isLoaded(cavity) && level.canSeeSky(cavity) && standable(level, cavity)) {
            return cavity; // the report already is the entrance a scout saw
        }
        BlockPos start = null;
        for (BlockPos p : BlockPos.betweenClosed(cavity.offset(-2, -2, -2), cavity.offset(2, 2, 2))) {
            if (level.isLoaded(p) && level.getBlockState(p).isAir() && !level.canSeeSky(p)) {
                start = p.immutable();
                break;
            }
        }
        if (start == null) {
            return null;
        }
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.asLong());
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        while (!queue.isEmpty() && seen.size() < maxCells) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.add(n.asLong()) || !n.closerThan(cavity, 48) || !level.isLoaded(n) || !level.getBlockState(n).isAir()) {
                    continue;
                }
                if (level.canSeeSky(n)) {
                    // the cave opens to the sky here (a mouth, a pit, a ravine): the way in is where someone can stand
                    // at the bottom of that opening, or at its rim when it is deep
                    BlockPos q = n;
                    int down = 0;
                    while (down < 24 && open(level, q.below()) && level.isLoaded(q.below())) {
                        q = q.below();
                        down++;
                    }
                    if (standable(level, q)) {
                        double dist = q.distSqr(from);
                        if (dist < bestD) {
                            bestD = dist;
                            best = q.immutable();
                        }
                    }
                    continue; // do not flood out into the open air above the ground
                }
                queue.add(n);
            }
        }
        return best;
    }

    /** Places one block from the NPC's backpack (cobblestone, dirt, ...) at {@code p}, e.g. as a step. */
    public static boolean placeBlock(FactionNpc npc, BlockPos p) {
        ServerLevel level = (ServerLevel) npc.level();
        ItemStack mat = Inv.first(npc.inventory(), MinerJob.SEAL);
        if (mat.isEmpty() || !(mat.getItem() instanceof net.minecraft.world.item.BlockItem bi) || !open(level, p)) {
            return false;
        }
        BlockState place = bi.getBlock().defaultBlockState();
        if (BlockWork.obstructed(level, p, place, npc)) {
            return false;
        }
        mat.shrink(1);
        npc.inventory().setChanged();
        level.setBlock(p, place, Block.UPDATE_ALL);
        level.playSound(null, p, place.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        npc.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /** A standable cell from which a player could reach {@code target} (eyes within {@code reach}). */
    public static BlockPos standNear(ServerLevel level, FactionNpc npc, BlockPos target, double reach) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(target.offset(-4, -5, -4), target.offset(4, 2, 4))) {
            if (Vec3.atBottomCenterOf(p).add(0, 1.62, 0).distanceToSqr(Vec3.atCenterOf(target)) > reach * reach || !standable(level, p)) {
                continue;
            }
            double d = npc.blockPosition().distSqr(p);
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }

    /** Takes up to {@code want} torches, crafting them from coal or charcoal and sticks in the backpack (2x2 recipe). */
    public static void craftTorchesInHand(FactionNpc npc, int want) {
        while (Inv.count(npc.inventory(), s -> s.is(Items.TORCH)) < want) {
            Crafting.ensureSticks(npc.inventory(), 1);
            if (Crafting.craft(npc.inventory(), Crafting.TORCHES, 1) <= 0) {
                return;
            }
        }
    }

    public static ItemStack stack(net.minecraft.world.item.Item item, int n) {
        return new ItemStack(item, n);
    }
}
