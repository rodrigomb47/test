package com.factionclash.work;

import com.factionclash.campaign.Report;
import com.factionclash.logic.StructureRules;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a scout can see of the world's structures from where it stands ({@link StructureRules}): villages (every kind
 * in the village tag, abandoned ones included), pillager outposts and woodland mansions whose bounds come within 48
 * blocks. Only chunks already in memory are read: the structure references of the fully loaded chunks within 3 chunks
 * of the scout, then the start of each referenced structure from its start chunk - fully loaded, or held in memory
 * past the structure stage of its generation. Looking never loads or generates a chunk; a structure whose start chunk
 * is not in memory is looked at again later.
 */
public final class StructureSightings {
    private StructureSightings() {
    }

    /**
     * A structure in sight: its kind, its start chunk (one structure, one start), its bounds, where it is reported
     * (a village's town centre, else the middle of its bounds, at surface height where that column is loaded) and what
     * it is called ("plains village", "Pillager outpost").
     */
    public record Sighting(Report.Kind kind, long start, BoundingBox box, BlockPos pos, String name) {
    }

    /**
     * The structures in sight of a scout at {@code eye}, leaving out those whose start chunk is in {@code skip}
     * (already seen on this trip). A village and an outpost or mansion never start in the same chunk (outposts keep
     * away from villages, mansions grow in dark forests where villages do not), so the start chunk names one.
     */
    public static List<Sighting> look(ServerLevel level, BlockPos eye, Set<Long> skip) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Map<Structure, Optional<Report.Kind>> kinds = new IdentityHashMap<>();
        Set<Long> looked = new HashSet<>();
        List<Sighting> out = new ArrayList<>();
        int ccx = eye.getX() >> 4, ccz = eye.getZ() >> 4, r = StructureRules.CHUNK_RADIUS;
        for (int cx = ccx - r; cx <= ccx + r; cx++) {
            for (int cz = ccz - r; cz <= ccz + r; cz++) {
                LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
                if (c == null) {
                    continue;
                }
                for (Map.Entry<Structure, LongSet> e : c.getAllReferences().entrySet()) {
                    if (e.getValue().isEmpty()) {
                        continue;
                    }
                    Report.Kind kind = kinds.computeIfAbsent(e.getKey(), s -> Optional.ofNullable(classify(registry, s))).orElse(null);
                    if (kind == null) {
                        continue;
                    }
                    for (LongIterator it = e.getValue().iterator(); it.hasNext(); ) {
                        long start = it.nextLong();
                        if (skip.contains(start) || !looked.add(start)) {
                            continue;
                        }
                        StructureStart st = startOf(level, start, e.getKey());
                        if (st == null || !st.isValid()) {
                            continue;
                        }
                        BoundingBox box = st.getBoundingBox();
                        if (!StructureRules.inSight(eye.getX() + 0.5, eye.getZ() + 0.5, box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
                            continue;
                        }
                        out.add(new Sighting(kind, start, box, reportPos(level, kind, st, eye.getY()), name(registry, e.getKey(), kind)));
                    }
                }
            }
        }
        return out;
    }

    /** The report kind of a structure (resolved through the structure registry and the village tag), or null. */
    public static Report.Kind classify(Registry<Structure> registry, Structure s) {
        ResourceLocation id = registry.getKey(s);
        StructureRules.Sight sight = StructureRules.classify(id == null ? null : id.toString(), registry.wrapAsHolder(s).is(StructureTags.VILLAGE));
        return sight == null ? null : Report.Kind.valueOf(sight.name());
    }

    private static String name(Registry<Structure> registry, Structure s, Report.Kind kind) {
        if (kind == Report.Kind.VILLAGE) {
            ResourceLocation id = registry.getKey(s);
            return StructureRules.villageName(id == null ? null : id.toString());
        }
        return kind.label;
    }

    /** The structure's start, read from its start chunk only if that chunk is already in memory (never loaded here). */
    private static StructureStart startOf(ServerLevel level, long chunk, Structure s) {
        ChunkPos cp = new ChunkPos(chunk);
        ChunkAccess c = level.getChunkSource().getChunkNow(cp.x, cp.z);
        if (c == null) {
            ChunkHolder holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(chunk);
            c = holder == null ? null : holder.getLatestChunk();
            // still being generated: its starts are settled once the structure stage is behind it
            if (c != null && !c.getPersistedStatus().isOrAfter(ChunkStatus.STRUCTURE_REFERENCES)) {
                c = null;
            }
        }
        return c == null ? null : c.getStartForStructure(s);
    }

    /**
     * Where a structure is reported: a village at its town centre (the first piece, around the bell), anything else at
     * the middle of its bounds; at the surface there when that column is loaded, else at the scout's own height.
     */
    static BlockPos reportPos(ServerLevel level, Report.Kind kind, StructureStart st, int fallbackY) {
        BoundingBox b = kind == Report.Kind.VILLAGE && !st.getPieces().isEmpty() ? st.getPieces().get(0).getBoundingBox() : st.getBoundingBox();
        int x = (b.minX() + b.maxX()) >> 1, z = (b.minZ() + b.maxZ()) >> 1;
        LevelChunk c = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        int y = c == null ? fallbackY : c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        return new BlockPos(x, y, z);
    }

    /**
     * The report a scout at {@code eye} makes of a structure in sight, with the people it can see there (within the
     * structure's bounds, 8 blocks around them, and 48 blocks of the scout): a village's villagers with trades
     * (counted; 0 for an abandoned village, whose zombie villagers are named), an outpost's pillagers or a mansion's
     * illagers.
     */
    public static Report report(ServerLevel level, BlockPos eye, Sighting s, long now) {
        AABB area = AABB.of(s.box()).inflate(8, 0, 8).intersect(new AABB(eye).inflate(StructureRules.SIGHT, 24, StructureRules.SIGHT));
        switch (s.kind()) {
            case VILLAGE -> {
                List<Villager> traders = level.getEntitiesOfClass(Villager.class, area, TraderJob::trades);
                String note;
                if (!traders.isEmpty()) {
                    note = s.name() + ", " + traders.size() + " villagers with trades (" + professions(traders) + ")";
                } else {
                    int zombies = level.getEntitiesOfClass(ZombieVillager.class, area, z -> z.isAlive()).size();
                    note = s.name() + (zombies > 0 ? ", abandoned (" + zombies + " zombie villager" + (zombies == 1 ? "" : "s")
                            + "), no villagers with trades" : ", no villagers with trades seen");
                }
                return new Report(Report.Kind.VILLAGE, s.pos(), now, null, traders.size(), note);
            }
            default -> {
                int illagers = level.getEntitiesOfClass(AbstractIllager.class, area, i -> i.isAlive()).size();
                String who = s.kind() == Report.Kind.PILLAGER_OUTPOST ? "pillager" : "illager";
                String note = illagers == 0 ? "no " + who + "s seen" : illagers + " " + who + (illagers == 1 ? "" : "s") + " seen";
                return new Report(s.kind(), s.pos(), now, null, illagers, note);
            }
        }
    }

    /** "armorer, farmer, librarian": the trades of these villagers. */
    public static String professions(List<Villager> villagers) {
        return villagers.stream().map(v -> BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).getPath())
                .distinct().sorted().collect(Collectors.joining(", "));
    }
}
