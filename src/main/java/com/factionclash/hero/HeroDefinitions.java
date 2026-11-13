package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.hero.HeroReader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The server definitions of the heroes (HEROES_V2 12.1): every {@code data/factionclash/heroes/<id>.json} of the
 * server's packs (the mod's own, or a datapack's file that replaces one), read whenever the server loads its data
 * ({@code /reload} too) and handed to {@link HeroRegistry}, which merges the operator's overrides and checks them.
 *
 * <p>The design names {@code SimpleJsonResourceReloadListener}; this is its sibling on the same {@code heroes} folder
 * (the same {@link FileToIdConverter}, so a datapack replaces a file the same way), reading the text rather than a
 * Gson tree: the hero reader forgives comments and trailing commas and names the line of every problem, which a tree
 * parsed by Gson has already lost (and Gson refuses a file with a comment before the reader could say why).
 * Registered in {@code AddReloadListenerEvent} ({@link HeroEvents}).
 */
public final class HeroDefinitions extends SimplePreparableReloadListener<List<HeroReader.Source>> {
    public static final FileToIdConverter FILES = FileToIdConverter.json("heroes");

    @Override
    protected List<HeroReader.Source> prepare(ResourceManager resources, ProfilerFiller profiler) {
        return read(resources);
    }

    @Override
    protected void apply(List<HeroReader.Source> sources, ResourceManager resources, ProfilerFiller profiler) {
        HeroRegistry.setDefinitionSources(sources);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread() && server.overworld() != null) {
            HeroManager.refreshAll(server); // /reload: living heroes take their new definitions at once (v1 2.3)
        }
    }

    /**
     * Every hero definition file of the packs, labelled {@code data/factionclash/heroes/<path>.json}. Files of another
     * namespace are not heroes of this mod and are skipped with a log line.
     */
    public static List<HeroReader.Source> read(ResourceManager resources) {
        List<HeroReader.Source> out = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> e : FILES.listMatchingResources(resources).entrySet()) {
            ResourceLocation id = FILES.fileToId(e.getKey());
            if (!id.getNamespace().equals(FactionClash.MODID)) {
                FactionClash.LOGGER.warn("[Heroes] {} ({}): heroes are read only from data/{}/heroes; skipped", e.getKey(),
                        e.getValue().sourcePackId(), FactionClash.MODID);
                continue;
            }
            String label = "data/" + FactionClash.MODID + "/heroes/" + id.getPath() + ".json";
            try (InputStream in = e.getValue().open()) {
                byte[] bytes = in.readNBytes(HeroCatalogue.MAX_FILE_BYTES * 2 + 1); // the reader refuses a file too big
                out.add(new HeroReader.Source(label, new String(bytes, StandardCharsets.UTF_8)));
            } catch (IOException | RuntimeException x) {
                FactionClash.LOGGER.error("[Heroes] {} ({}) could not be read: {}", label, e.getValue().sourcePackId(), x.toString());
                out.add(new HeroReader.Source(label, ""));
            }
        }
        return out;
    }
}
