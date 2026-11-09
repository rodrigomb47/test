package com.factionclash.campaign;

import com.factionclash.FactionClash;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.structure.SiteFinder;
import com.factionclash.util.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Starts a campaign over several ticks so terrain evaluation never stalls the server. Each faction searches its
 * whole sector of the map on a coarse grid of real generated terrain (no water, gentle slopes, whole footprint
 * including the tree-plot pad), re-checks the best candidates at full resolution, then builds there. No mine is
 * built: the miners choose their own sites once the base stands.
 */
public final class CampaignStarter {
    private record Job(FactionType faction, boolean lair, double angle, double sectorHalfWidth) {
    }

    private record Candidate(BlockPos pos, SiteFinder.Site coarse) {
    }

    private final MinecraftServer server;
    private final List<Job> jobs = new ArrayList<>();
    private final List<BlockPos> chosen = new ArrayList<>();
    private List<BlockPos> grid;
    private final List<Candidate> scored = new ArrayList<>();
    private int jobIndex;
    private int gridIndex;
    /** 0 = own map sector, 1 = whole map, 2 = whole map at 1.5x radius (only if no dry land was found). */
    private int phase;
    private SiteFinder.Site bestOverall;
    private boolean done;
    private int radius;
    private final int requestedRadius;
    private final BlockPos mapCenter;

    public CampaignStarter(MinecraftServer server, int radius) {
        this.server = server;
        this.radius = radius;
        this.requestedRadius = radius;
        ServerLevel level = server.overworld();
        this.mapCenter = level.getSharedSpawnPos();
        int humans = FCConfig.HUMAN_FACTIONS.get();
        int lairs = FCConfig.MONSTER_LAIRS.get();
        FactionType[] hs = FactionType.humans();
        double humanHalf = Math.PI / humans;
        for (int i = 0; i < humans; i++) {
            jobs.add(new Job(hs[i], false, Math.toRadians(45) + 2 * Math.PI * i / humans, humanHalf));
        }
        for (int i = 0; i < lairs; i++) {
            jobs.add(new Job(FactionType.HOLLOW, true, 2 * Math.PI * i / Math.max(1, lairs), Math.PI / Math.max(2, lairs)));
        }
        CampaignData data = CampaignData.get(server);
        data.reset();
        data.center = mapCenter;
        data.radius = radius;
        Messages.toAll(server, Component.literal("Faction Clash: preparing a campaign (" + humans + " factions, " + lairs
                + " lairs, map radius " + radius + ")..."));
    }

    public boolean done() {
        return done;
    }

    private List<BlockPos> sectorGrid(Job job) {
        List<BlockPos> out = new ArrayList<>();
        int step = 32;
        int r0 = phase == 2 ? requestedRadius * 3 / 2 : requestedRadius;
        double rMin = phase > 0 ? 0 : (job.lair() ? r0 * 0.25 : r0 * 0.3);
        double rMax = r0 * 0.85;
        int radius = r0;
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r < rMin || r > rMax) {
                    continue;
                }
                double a = Math.atan2(dz, dx);
                double diff = Math.abs(Math.atan2(Math.sin(a - job.angle()), Math.cos(a - job.angle())));
                if (phase > 0 || diff <= job.sectorHalfWidth()) {
                    out.add(mapCenter.offset(dx, 0, dz));
                }
            }
        }
        // prefer positions near the sector's middle line and mid radius first
        out.sort(Comparator.comparingDouble(p -> Math.abs(Math.sqrt(p.distSqr(mapCenter)) - r0 * 0.55)));
        return out;
    }

    /** Does a bounded amount of work; returns true when finished. */
    public boolean step() {
        if (done) {
            return true;
        }
        if (jobIndex >= jobs.size()) {
            finish();
            return true;
        }
        Job job = jobs.get(jobIndex);
        ServerLevel level = server.overworld();
        SiteFinder.Footprint fp = job.lair() ? SiteFinder.lair() : SiteFinder.settlement();
        if (grid == null) {
            grid = sectorGrid(job);
            gridIndex = 0;
            scored.clear();
        }
        int budget = 3;
        while (budget-- > 0 && gridIndex < grid.size()) {
            BlockPos c = grid.get(gridIndex++);
            int searchRadius = phase == 2 ? requestedRadius * 3 / 2 : requestedRadius;
            if (!SiteFinder.insideMap(c, fp, mapCenter, searchRadius) || tooClose(c, job)) {
                continue;
            }
            SiteFinder.Site site = SiteFinder.evaluate(level, c.getX(), c.getZ(), fp, 8);
            if (site != null) {
                scored.add(new Candidate(c, site));
            }
        }
        if (gridIndex < grid.size()) {
            return false;
        }
        // refine the most promising candidates at full resolution: dry first, then flattest
        scored.sort(Comparator.comparingInt((Candidate k) -> k.coarse().waterSamples()).thenComparingDouble(k -> k.coarse().score()));
        SiteFinder.Site best = null;
        for (int i = 0; i < Math.min(4, scored.size()); i++) {
            Candidate k = scored.get(i);
            SiteFinder.Site fine = SiteFinder.evaluate(level, k.pos().getX(), k.pos().getZ(), fp, 4);
            if (fine != null && (best == null || fine.waterSamples() < best.waterSamples()
                    || (fine.waterSamples() == best.waterSamples() && fine.score() < best.score()))) {
                best = fine;
            }
        }
        if (best != null && (bestOverall == null || best.waterSamples() < bestOverall.waterSamples()
                || (best.waterSamples() == bestOverall.waterSamples() && best.score() < bestOverall.score()))) {
            bestOverall = best;
        }
        if ((bestOverall == null || bestOverall.waterSamples() > DRY_TOLERANCE) && phase < 2) {
            phase++;
            FactionClash.LOGGER.info("{}: no dry site in {}, widening the search", job.faction().displayName,
                    phase == 1 ? "its map sector" : "the map");
            grid = null;
            return false;
        }
        best = bestOverall;
        CampaignData data = CampaignData.get(server);
        if (best == null) {
            BlockPos nominal = mapCenter.offset((int) (Math.cos(job.angle()) * radius * 0.55), 0, (int) (Math.sin(job.angle()) * radius * 0.55));
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, nominal.getX(), nominal.getZ());
            FactionClash.LOGGER.warn("No evaluable site for {}; using nominal position", job.faction());
            best = new SiteFinder.Site(new BlockPos(nominal.getX(), y + 1, nominal.getZ()), 999, 99);
        }
        if (best.waterSamples() > 0) {
            FactionClash.LOGGER.warn("{} site at {} still has {} water samples (no dry land in its sector); terrain will be filled",
                    job.faction().displayName, best.center().toShortString(), best.waterSamples());
        } else {
            FactionClash.LOGGER.info("{} site at {} is dry (score {}, {} candidates checked)", job.faction().displayName,
                    best.center().toShortString(), String.format("%.1f", best.score()), scored.size());
        }
        if (job.lair()) {
            BaseBuilder.buildLair(level, data, best.center());
        } else {
            BaseBuilder.buildSettlement(level, data, job.faction(), best.center());
        }
        chosen.add(best.center());
        if (phase == 2 && !SiteFinder.insideMap(best.center(), fp, mapCenter, radius)) {
            radius = requestedRadius * 3 / 2;
            data.radius = radius;
            data.setDirty();
            FactionClash.LOGGER.info("Campaign map enlarged to radius {} to fit dry land", radius);
        }
        Messages.toAll(server, Component.literal("Faction Clash: " + job.faction().displayName + (job.lair() ? " lair" : " base")
                + " established (" + (jobIndex + 1) + "/" + jobs.size() + ")"));
        jobIndex++;
        grid = null;
        phase = 0;
        bestOverall = null;
        return false;
    }

    /** A couple of puddle samples are tolerated; the terrain pass fills them. */
    private static final int DRY_TOLERANCE = 2;

    private boolean tooClose(BlockPos c, Job job) {
        for (BlockPos other : chosen) {
            if (SiteFinder.dist(other, c) < (job.lair() ? 100 : 150)) {
                return true;
            }
        }
        return false;
    }

    private void finish() {
        CampaignData data = CampaignData.get(server);
        data.started = true;
        data.startedAt = server.overworld().getGameTime();
        for (FactionType t : FactionType.values()) {
            if (!data.basesOf(t).isEmpty()) {
                data.faction(t).alive = true;
            }
        }
        data.setDirty();
        done = true;
        Messages.toAll(server, Component.literal("Faction Clash campaign started! Use /fc join <faction> to join a faction."));
    }
}
