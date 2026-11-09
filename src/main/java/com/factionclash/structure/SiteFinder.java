package com.factionclash.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Chooses safe land for a settlement (spec 3): samples the real terrain of the whole footprint (the walled base and
 * the tree plot's pad west of it) and rejects water, steep ground and out-of-map positions. Mines are not part of
 * it: miners choose their own sites later.
 */
public final class SiteFinder {
    public record Site(BlockPos center, double score, int waterSamples) {
    }

    /** Footprint relative to the centre: x from minX to maxX, z from minZ to maxZ. */
    public record Footprint(int minX, int maxX, int minZ, int maxZ) {
    }

    private SiteFinder() {
    }

    public static Footprint settlement() {
        return new Footprint(BaseLayout.PLOT_ORIGIN_X - 26, BaseLayout.HALF + 2, -BaseLayout.HALF - 2, BaseLayout.HALF + 2);
    }

    public static Footprint lair() {
        return new Footprint(-LairLayout.HALF - 2, LairLayout.HALF + 2, -LairLayout.HALF - 2, LairLayout.HALF + 2);
    }

    public static List<int[]> candidateOffsets(int maxRadius, int step) {
        List<int[]> out = new ArrayList<>();
        out.add(new int[]{0, 0});
        for (int r = step; r <= maxRadius; r += step) {
            for (int dx = -r; dx <= r; dx += step) {
                for (int dz = -r; dz <= r; dz += step) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                        out.add(new int[]{dx, dz});
                    }
                }
            }
        }
        return out;
    }

    /** Evaluates one candidate centre. Uses generated-terrain heightmaps (no features), so it is cheap. */
    public static Site evaluate(ServerLevel level, int cx, int cz, Footprint fp, int sampleStep) {
        List<Integer> heights = new ArrayList<>();
        int water = 0;
        for (int x = cx + fp.minX(); x <= cx + fp.maxX(); x += sampleStep) {
            for (int z = cz + fp.minZ(); z <= cz + fp.maxZ(); z += sampleStep) {
                ChunkAccess chunk = level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.SURFACE, true);
                if (chunk == null) {
                    return null;
                }
                int floor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15);
                int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15);
                if (surface > floor) {
                    water++;
                }
                heights.add(floor);
            }
        }
        if (heights.isEmpty()) {
            return null;
        }
        int[] h = heights.stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(h);
        int median = h[h.length / 2];
        double mean = Arrays.stream(h).average().orElse(median);
        double var = 0;
        for (int v : h) {
            var += (v - mean) * (v - mean);
        }
        double std = Math.sqrt(var / h.length);
        int range = h[(int) (h.length * 0.95)] - h[(int) (h.length * 0.05)];
        double score = std * 2 + range + water * 25;
        if (median < level.getSeaLevel() - 1) {
            score += 40;
        }
        return new Site(new BlockPos(cx, median + 1, cz), score, water);
    }

    public static boolean insideMap(BlockPos center, Footprint fp, BlockPos mapCenter, int radius) {
        return center.getX() + fp.minX() >= mapCenter.getX() - radius && center.getX() + fp.maxX() <= mapCenter.getX() + radius
                && center.getZ() + fp.minZ() >= mapCenter.getZ() - radius && center.getZ() + fp.maxZ() <= mapCenter.getZ() + radius;
    }

    public static double dist(BlockPos a, BlockPos b) {
        return Math.sqrt(Mth.square(a.getX() - b.getX()) + Mth.square(a.getZ() - b.getZ()));
    }
}
