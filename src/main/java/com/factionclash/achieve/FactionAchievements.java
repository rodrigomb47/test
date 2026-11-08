package com.factionclash.achieve;

import com.factionclash.FactionClash;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.FactionState;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The vanilla advancements, earned by a faction. Each faction collects the criteria of the real advancements
 * (the game's own names, descriptions and requirements) through what its NPCs do the way a player would - first
 * cobblestone, smelted iron, diamonds, a crop planted, animals bred, a fish caught, a monster slain, an item
 * enchanted, a new biome reached - and through its players' own advancements. When the requirements are met it is
 * announced in chat like a player's advancement.
 */
public final class FactionAchievements {
    private FactionAchievements() {
    }

    /** Items whose first arrival in a faction's hands meets a criterion: advancement -> criterion. */
    private static final Map<Item, String[]> ITEMS = Map.ofEntries(
            Map.entry(Items.CRAFTING_TABLE, new String[]{"story/root", "crafting_table"}),
            Map.entry(Items.STONE_PICKAXE, new String[]{"story/upgrade_tools", "stone_pickaxe"}),
            Map.entry(Items.IRON_INGOT, new String[]{"story/smelt_iron", "iron"}),
            Map.entry(Items.IRON_HELMET, new String[]{"story/obtain_armor", "iron_helmet"}),
            Map.entry(Items.IRON_CHESTPLATE, new String[]{"story/obtain_armor", "iron_chestplate"}),
            Map.entry(Items.IRON_LEGGINGS, new String[]{"story/obtain_armor", "iron_leggings"}),
            Map.entry(Items.IRON_BOOTS, new String[]{"story/obtain_armor", "iron_boots"}),
            Map.entry(Items.IRON_PICKAXE, new String[]{"story/iron_tools", "iron_pickaxe"}),
            Map.entry(Items.LAVA_BUCKET, new String[]{"story/lava_bucket", "lava_bucket"}),
            Map.entry(Items.OBSIDIAN, new String[]{"story/form_obsidian", "obsidian"}),
            Map.entry(Items.DIAMOND, new String[]{"story/mine_diamond", "diamond"}),
            Map.entry(Items.DIAMOND_HELMET, new String[]{"story/shiny_gear", "diamond_helmet"}),
            Map.entry(Items.DIAMOND_CHESTPLATE, new String[]{"story/shiny_gear", "diamond_chestplate"}),
            Map.entry(Items.DIAMOND_LEGGINGS, new String[]{"story/shiny_gear", "diamond_leggings"}),
            Map.entry(Items.DIAMOND_BOOTS, new String[]{"story/shiny_gear", "diamond_boots"}),
            Map.entry(Items.NETHERITE_HOE, new String[]{"husbandry/obtain_netherite_hoe", "netherite_hoe"}));

    /** An item reached the faction (into an NPC's backpack or the storehouse). */
    public static void obtained(MinecraftServer server, FactionType faction, ItemStack stack, String by) {
        if (stack.isEmpty() || server == null || faction == null) {
            return;
        }
        if (stack.is(ItemTags.STONE_TOOL_MATERIALS)) {
            criterion(server, faction, "story/mine_stone", "get_stone", by);
        }
        String[] c = ITEMS.get(stack.getItem());
        if (c != null) {
            criterion(server, faction, c[0], c[1], by);
        }
    }

    /** Something was eaten (Husbandry, A Balanced Diet). */
    public static void ate(MinecraftServer server, FactionType faction, ItemStack food, String by) {
        criterion(server, faction, "husbandry/root", "consumed_item", by);
        criterion(server, faction, "husbandry/balanced_diet", BuiltInRegistries.ITEM.getKey(food.getItem()).getPath(), by);
    }

    /**
     * Marks one criterion of a vanilla advancement (id without the "minecraft:" namespace) as met by the faction.
     * When the advancement's requirements are all met it is awarded and announced. Unknown advancements or criteria
     * are ignored, so the game's own data stays the authority.
     */
    public static void criterion(MinecraftServer server, FactionType faction, String advancement, String criterion, String by) {
        if (server == null || faction == null || !faction.human || !FCConfig.ACHIEVEMENTS.get()) {
            return;
        }
        CampaignData data = CampaignData.get(server);
        if (!data.started) {
            return;
        }
        FactionState f = data.faction(faction);
        String id = advancement.contains(":") ? advancement : "minecraft:" + advancement;
        if (!f.alive || f.achievements.containsKey(id)) {
            return;
        }
        AdvancementHolder h = server.getAdvancements().get(ResourceLocation.parse(id));
        if (h == null || h.value().display().isEmpty() || !h.value().criteria().containsKey(criterion)) {
            return;
        }
        Set<String> met = f.advancementProgress.computeIfAbsent(id, k -> new HashSet<>());
        if (!met.add(criterion)) {
            return;
        }
        data.setDirty();
        if (h.value().requirements().test(met::contains)) {
            award(server, faction, h, by);
        }
    }

    /** Meets every criterion at once: a faction player's own advancement counts for the whole faction. */
    public static void complete(MinecraftServer server, FactionType faction, AdvancementHolder h, String by) {
        if (server == null || faction == null || !FCConfig.ACHIEVEMENTS.get() || h.value().display().isEmpty()) {
            return;
        }
        CampaignData data = CampaignData.get(server);
        FactionState f = data.faction(faction);
        if (!data.started || !f.alive || f.achievements.containsKey(h.id().toString())) {
            return;
        }
        award(server, faction, h, by);
    }

    private static void award(MinecraftServer server, FactionType faction, AdvancementHolder h, String by) {
        CampaignData data = CampaignData.get(server);
        FactionState f = data.faction(faction);
        String id = h.id().toString();
        f.achievements.put(id, new FactionState.Achieved(server.overworld().getGameTime(), by == null ? "" : by));
        f.advancementProgress.remove(id);
        data.setDirty();
        DisplayInfo d = h.value().display().get();
        Component name = h.value().name().orElse(d.getTitle());
        MutableComponent msg = Component.translatable("chat.type.advancement." + d.getType().getSerializedName(),
                Component.literal(faction.displayName).withStyle(faction.color), name);
        if (by != null && !by.isEmpty()) {
            msg.append(Component.literal(" (" + by + ")").withStyle(ChatFormatting.GRAY));
        }
        if (d.shouldAnnounceChat() && FCConfig.ANNOUNCE_ACHIEVEMENTS.get()) {
            server.getPlayerList().broadcastSystemMessage(msg, false);
        }
        FactionClash.LOGGER.info("[Faction Clash] {}", msg.getString());
        if (FCConfig.GRANT_ACHIEVEMENTS_TO_PLAYERS.get()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (data.players.get(p.getUUID()) == faction) {
                    grant(p, h);
                }
            }
        }
    }

    /** Hands the faction's advancement to one of its players as a real advancement (config option). */
    public static void grant(ServerPlayer p, AdvancementHolder h) {
        AdvancementProgress pr = p.getAdvancements().getOrStartProgress(h);
        if (pr.isDone()) {
            return;
        }
        for (String c : pr.getRemainingCriteria()) {
            p.getAdvancements().award(h, c);
        }
    }

    /** All of a faction's advancements for a player who joins it or logs in (config option). */
    public static void grantAll(ServerPlayer p, FactionType faction) {
        if (!FCConfig.GRANT_ACHIEVEMENTS_TO_PLAYERS.get() || p.getServer() == null) {
            return;
        }
        FactionState f = CampaignData.get(p.getServer()).faction(faction);
        for (String id : f.achievements.keySet()) {
            AdvancementHolder h = p.getServer().getAdvancements().get(ResourceLocation.parse(id));
            if (h != null) {
                grant(p, h);
            }
        }
    }

    /** Readable title of an advancement id, for lists. */
    public static String title(MinecraftServer server, String id) {
        AdvancementHolder h = server.getAdvancements().get(ResourceLocation.parse(id));
        return h == null || h.value().display().isEmpty() ? id : h.value().display().get().getTitle().getString();
    }

    /** Number of criteria an advancement has (for progress on the long ones). */
    public static int criteriaCount(MinecraftServer server, String id) {
        AdvancementHolder h = server.getAdvancements().get(ResourceLocation.parse(id));
        return h == null ? 0 : h.value().criteria().size();
    }
}
