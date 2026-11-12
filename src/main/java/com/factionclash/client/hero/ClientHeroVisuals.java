package com.factionclash.client.hero;

import com.factionclash.FactionClash;
import com.factionclash.hero.HeroEntry;
import com.factionclash.hero.HeroRegistry;
import com.factionclash.logic.hero.HeroIdentity;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * After each client resource reload: the visual files a resource pack adds for heroes the mod does not ship (the art
 * pipeline's style fixtures, built into a temporary folder and packed for a screenshot session) become the client's
 * preview fixtures ({@link HeroRegistry#setClientEntries}), so this client draws them with their own model, texture,
 * face and animation chain when a server lines them up. Shipped heroes always use the mod's own files.
 */
public final class ClientHeroVisuals implements ResourceManagerReloadListener {
    private static final String DIR = "heroes";
    private static final String SUFFIX = ".visual.json";

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<String, HeroEntry> found = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        Map<ResourceLocation, Resource> files = manager.listResources(DIR, rl -> rl.getNamespace().equals(FactionClash.MODID)
                && rl.getPath().endsWith(SUFFIX));
        for (Map.Entry<ResourceLocation, Resource> f : files.entrySet()) {
            String path = f.getKey().getPath();
            String id = path.substring(DIR.length() + 1, path.length() - SUFFIX.length());
            if (!HeroIdentity.isHeroId(id) || HeroRegistry.get(id) != null && HeroRegistry.get(id).source() != HeroEntry.Source.FIXTURE) {
                continue; // the fallback, the libraries, a shipped hero or a test fixture
            }
            try (Reader r = f.getValue().openAsReader()) {
                JsonElement e = JsonParser.parseReader(r);
                if (e.isJsonObject()) {
                    found.put(id, HeroRegistry.fixtureEntry(id, e.getAsJsonObject(), problems, f.getKey().toString()));
                }
            } catch (Exception ex) {
                problems.add(f.getKey() + ": " + ex.getMessage());
            }
        }
        HeroRegistry.setClientEntries(found);
        if (!found.isEmpty()) {
            FactionClash.LOGGER.info("Heroes: {} preview fixture(s) from resource packs ({})", found.size(), String.join(", ", found.keySet()));
        }
        problems.forEach(p -> FactionClash.LOGGER.warn("Heroes: {}", p));
        HeroModel.forgetResolved();
        HeroRenderer.forgetResources();
    }
}
