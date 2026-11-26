package com.factionclash.command;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.CampaignManager;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.campaign.Recruitment;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.info.BaseInfo;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcInspector;
import com.factionclash.npc.NpcRole;
import com.factionclash.storage.FactionStorage;
import com.factionclash.util.ChunkLoading;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** /fc commands. Player commands are separate from admin diagnostics (spec 19). */
public final class FCCommands {
    private static final SuggestionProvider<CommandSourceStack> FACTIONS = (c, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(FactionType.humans()).map(f -> f.id), b);
    private static final SuggestionProvider<CommandSourceStack> ROLES = (c, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(NpcRole.values()).map(r -> r.id), b);

    private FCCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("fc")
                .then(Commands.literal("start").requires(s -> s.hasPermission(2))
                        .executes(c -> start(c, FCConfig.MAP_RADIUS.get()))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(160, 4096)).executes(c -> start(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("status").executes(FCCommands::status))
                .then(Commands.literal("join").then(Commands.argument("faction", StringArgumentType.word()).suggests(FACTIONS).executes(FCCommands::join)))
                .then(Commands.literal("leave").executes(FCCommands::leave))
                .then(Commands.literal("base").executes(FCCommands::teleportBase))
                .then(Commands.literal("info").executes(FCCommands::info))
                .then(Commands.literal("map").executes(FCCommands::map))
                .then(HeroCommands.player())
                .then(Commands.literal("achievements").executes(c -> achievements(c, null))
                        .then(Commands.argument("faction", StringArgumentType.word()).suggests(FACTIONS)
                                .executes(c -> achievements(c, StringArgumentType.getString(c, "faction")))))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("dump").executes(FCCommands::dump))
                        .then(Commands.literal("npcs").executes(FCCommands::npcs))
                        .then(Commands.literal("roll").executes(c -> roll(c, null))
                                .then(Commands.argument("faction", StringArgumentType.word()).suggests(FACTIONS)
                                        .executes(c -> roll(c, factionArg(c)))))
                        .then(Commands.literal("spawn").then(Commands.argument("faction", StringArgumentType.word()).suggests(FACTIONS)
                                .then(Commands.argument("role", StringArgumentType.word()).suggests(ROLES).executes(FCCommands::spawn))))
                        .then(Commands.literal("recruit").then(Commands.argument("faction", StringArgumentType.word()).suggests(FACTIONS)
                                .then(Commands.argument("role", StringArgumentType.word()).suggests(ROLES).executes(FCCommands::recruit))))
                        .then(Commands.literal("info").then(Commands.argument("faction", StringArgumentType.word()).suggests(FACTIONS).executes(FCCommands::adminInfo)))
                        .then(Commands.literal("probe").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(FCCommands::probe))))
                        .then(Commands.literal("mine").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(FCCommands::mineDump)))
                        .then(Commands.literal("blocks").then(Commands.argument("from", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .then(Commands.argument("to", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos()).executes(FCCommands::blockDump))))
                        .then(Commands.literal("destroy").then(Commands.argument("baseId", IntegerArgumentType.integer(1)).executes(FCCommands::destroy)))
                        .then(Commands.literal("raid").then(Commands.argument("faction", StringArgumentType.word()).suggests(FACTIONS)
                                .then(Commands.argument("baseId", IntegerArgumentType.integer(1)).executes(FCCommands::raid))))
                        .then(HeroCommands.admin())));
    }

    /** The advancements a faction has made, who made them and when, and its progress on the long ones. */
    private static int achievements(CommandContext<CommandSourceStack> c, String factionId) {
        net.minecraft.server.MinecraftServer server = c.getSource().getServer();
        CampaignData data = CampaignData.get(server);
        FactionType f = factionId != null ? FactionType.byId(factionId)
                : c.getSource().getEntity() instanceof ServerPlayer p ? data.players.get(p.getUUID()) : null;
        if (f == null || !f.human) {
            c.getSource().sendFailure(Component.literal("Name a faction: /fc achievements <faction>"));
            return 0;
        }
        FactionState fs = data.faction(f);
        c.getSource().sendSuccess(() -> Component.literal(f.displayName + ": " + fs.achievements.size() + " advancements").withStyle(f.color), false);
        fs.achievements.forEach((id, a) -> c.getSource().sendSuccess(() -> Component.literal(String.format(" - %s (day %.1f%s)",
                com.factionclash.achieve.FactionAchievements.title(server, id), a.time() / 24000.0, a.by().isEmpty() ? "" : ", " + a.by())), false));
        fs.advancementProgress.forEach((id, met) -> {
            int total = com.factionclash.achieve.FactionAchievements.criteriaCount(server, id);
            if (total > 1) {
                c.getSource().sendSuccess(() -> Component.literal(" ... " + com.factionclash.achieve.FactionAchievements.title(server, id) + ": " + met.size() + "/" + total)
                        .withStyle(net.minecraft.ChatFormatting.GRAY), false);
            }
        });
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> c, int radius) {
        if (CampaignManager.starting()) {
            c.getSource().sendFailure(Component.literal("A campaign is already being prepared."));
            return 0;
        }
        CampaignManager.start(c.getSource().getServer(), radius);
        c.getSource().sendSuccess(() -> Component.literal("Preparing campaign (radius " + radius + ")..."), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        CampaignData data = CampaignData.get(c.getSource().getServer());
        if (!data.started) {
            c.getSource().sendSuccess(() -> Component.literal(CampaignManager.starting() ? "Campaign is being prepared." : "No campaign running. An operator can use /fc start."), false);
            return 1;
        }
        c.getSource().sendSuccess(() -> Component.literal("Faction Clash campaign - map radius " + data.radius
                + (data.winner != null ? " - WINNER: " + data.winner.displayName : "")).withStyle(ChatFormatting.GOLD), false);
        for (FactionType t : FactionType.values()) {
            List<BaseState> bases = data.basesOf(t);
            if (bases.isEmpty()) {
                continue;
            }
            long alive = data.livingBasesOf(t).size();
            c.getSource().sendSuccess(() -> Component.literal(" " + t.displayName + ": " + (alive > 0 ? alive + " base(s) standing" : "eliminated"))
                    .withStyle(t.color), false);
        }
        return 1;
    }

    private static int join(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        FactionType f = FactionType.byId(StringArgumentType.getString(c, "faction"));
        CampaignData data = CampaignData.get(p.server);
        if (f == null || !f.human || data.livingBasesOf(f).isEmpty()) {
            c.getSource().sendFailure(Component.literal("No such surviving human faction."));
            return 0;
        }
        data.players.put(p.getUUID(), f);
        data.setDirty();
        c.getSource().sendSuccess(() -> Component.literal("You joined " + f.displayName + ". Use /fc base to travel home.").withStyle(f.color), false);
        com.factionclash.achieve.FactionAchievements.grantAll(p, f);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        CampaignData data = CampaignData.get(p.server);
        data.players.remove(p.getUUID());
        data.setDirty();
        c.getSource().sendSuccess(() -> Component.literal("You left your faction."), false);
        return 1;
    }

    /** The established teleport: a member travels to an allied base; ordinary NPCs never teleport like this. */
    private static int teleportBase(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        CampaignData data = CampaignData.get(p.server);
        FactionType f = data.players.get(p.getUUID());
        if (f == null) {
            c.getSource().sendFailure(Component.literal("Join a faction first (/fc join <faction>)."));
            return 0;
        }
        List<BaseState> bases = data.livingBasesOf(f);
        if (bases.isEmpty()) {
            c.getSource().sendFailure(Component.literal("Your faction has no surviving base."));
            return 0;
        }
        BlockPos s = bases.get(0).point(BaseState.SPAWN);
        p.teleportTo(p.server.overworld(), s.getX() + 0.5, s.getY(), s.getZ() + 0.5, p.getYRot(), p.getXRot());
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        CampaignData data = CampaignData.get(p.server);
        FactionType f = data.players.get(p.getUUID());
        if (f == null) {
            c.getSource().sendFailure(Component.literal("Join a faction first."));
            return 0;
        }
        for (BaseState b : data.livingBasesOf(f)) {
            BaseInfo.sendBaseInfo(p, b);
        }
        return 1;
    }

    private static int map(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        FactionType f = CampaignData.get(p.server).players.get(p.getUUID());
        if (f == null) {
            c.getSource().sendFailure(Component.literal("Join a faction first."));
            return 0;
        }
        BaseInfo.sendMap(p, f);
        return 1;
    }

    private static int adminInfo(CommandContext<CommandSourceStack> c) {
        FactionType f = FactionType.byId(StringArgumentType.getString(c, "faction"));
        if (f == null) {
            return 0;
        }
        CampaignData data = CampaignData.get(c.getSource().getServer());
        ServerLevel level = c.getSource().getServer().overworld();
        for (BaseState b : data.basesOf(f)) {
            for (String l : BaseInfo.baseLines(level, data, b)) {
                c.getSource().sendSuccess(() -> Component.literal(l), false);
            }
        }
        for (String l : BaseInfo.mapLines(data, f, level.getGameTime())) {
            c.getSource().sendSuccess(() -> Component.literal(l), false);
        }
        return 1;
    }

    private static int npcs(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getServer().overworld();
        int n = 0;
        for (Entity e : level.getAllEntities()) {
            if (e instanceof FactionNpc npc) {
                n++;
                for (String l : NpcInspector.lines(npc, true)) {
                    c.getSource().sendSuccess(() -> Component.literal(l), false);
                }
            }
        }
        int total = n;
        c.getSource().sendSuccess(() -> Component.literal(total + " NPCs loaded, " + ChunkLoading.travellerCount() + " travelling"), false);
        return n;
    }

    /**
     * The roll call ({@code docs/design/missing_npcs.md} 11): each faction's members not at home with what the faction
     * believes (state, since, expected back) beside the truth (dead, lost), and the news each carrier holds.
     */
    private static int roll(CommandContext<CommandSourceStack> c, FactionType only) {
        CampaignData data = CampaignData.get(c.getSource().getServer());
        long now = c.getSource().getServer().overworld().getGameTime();
        int n = 0;
        for (FactionState f : data.factions.values()) {
            if (only != null && f.type != only || f.roster.isEmpty()) {
                continue;
            }
            java.util.EnumMap<com.factionclash.logic.campaign.Whereabouts.State, Integer> counts =
                    new java.util.EnumMap<>(com.factionclash.logic.campaign.Whereabouts.State.class);
            int unknown = 0;
            for (NpcRecord r : f.roster.values()) {
                counts.merge(r.where, 1, Integer::sum);
                if (r.dead && r.where.counted()) {
                    unknown++;
                }
            }
            int deathsUnknown = unknown;
            c.getSource().sendSuccess(() -> Component.literal(f.type.displayName + ": " + counts + ", deaths not known yet " + deathsUnknown
                    + ", carriers " + f.news.size()).withStyle(f.type.color), false);
            for (NpcRecord r : f.roster.values()) {
                if (r.where == com.factionclash.logic.campaign.Whereabouts.State.HERE && !r.gone()) {
                    continue;
                }
                n++;
                String line = String.format(java.util.Locale.ROOT, " - %s: %s%s%s | truth: %s", com.factionclash.campaign.RollCall.who(r), r.where,
                        r.where == com.factionclash.logic.campaign.Whereabouts.State.AWAY
                                ? String.format(java.util.Locale.ROOT, " (left day %.2f, expected back by day %.2f)", r.leftAt / 24000.0, r.expectedBy / 24000.0) : "",
                        r.where == com.factionclash.logic.campaign.Whereabouts.State.MISSING || r.where == com.factionclash.logic.campaign.Whereabouts.State.PRESUMED_DEAD
                                || r.where == com.factionclash.logic.campaign.Whereabouts.State.KNOWN_DEAD
                                ? String.format(java.util.Locale.ROOT, " (since day %.2f)", r.since / 24000.0) : "",
                        r.dead ? String.format(java.util.Locale.ROOT, "dead day %.2f at %s%s", r.diedAt / 24000.0,
                                r.diedPos == null ? "?" : r.diedPos.toShortString(), r.witnessed ? ", seen" : ", unseen")
                                : r.vanished ? "lost" : "alive at " + r.lastPos.toShortString());
                c.getSource().sendSuccess(() -> Component.literal(line), false);
            }
            f.news.forEach((carrier, items) -> {
                NpcRecord cr = f.roster.get(carrier);
                String who = cr != null ? cr.name : carrier.toString();
                c.getSource().sendSuccess(() -> Component.literal(" * " + who + " carries " + items), false);
            });
        }
        int total = n;
        c.getSource().sendSuccess(() -> Component.literal(total + " members away, missing or dead; day " + String.format(java.util.Locale.ROOT, "%.2f", now / 24000.0)), false);
        return n;
    }

    private static FactionType factionArg(CommandContext<CommandSourceStack> c) {
        return FactionType.byId(StringArgumentType.getString(c, "faction"));
    }

    private static int spawn(CommandContext<CommandSourceStack> c) {
        FactionType f = factionArg(c);
        NpcRole r = NpcRole.byId(StringArgumentType.getString(c, "role"));
        CampaignData data = CampaignData.get(c.getSource().getServer());
        if (f == null || r == null || data.livingBasesOf(f).isEmpty()) {
            c.getSource().sendFailure(Component.literal("Unknown faction/role or faction has no base."));
            return 0;
        }
        BaseState b = data.livingBasesOf(f).get(0);
        ServerLevel level = c.getSource().getLevel();
        FactionNpc npc = FactionNpc.create(level, f, r, b, BlockPos.containing(c.getSource().getPosition()));
        npc.equip(r.tool.basicItem());
        if (r.ranged) {
            npc.setAmmo(new ItemStack(net.minecraft.world.item.Items.ARROW));
        }
        level.addFreshEntity(npc);
        Recruitment.register(data, npc, b);
        c.getSource().sendSuccess(() -> Component.literal("Spawned " + npc.npcName() + " (admin, free)"), true);
        return 1;
    }

    private static int recruit(CommandContext<CommandSourceStack> c) {
        FactionType f = factionArg(c);
        NpcRole r = NpcRole.byId(StringArgumentType.getString(c, "role"));
        CampaignData data = CampaignData.get(c.getSource().getServer());
        if (f == null || r == null || data.livingBasesOf(f).isEmpty()) {
            return 0;
        }
        Recruitment.Result res = Recruitment.recruit(c.getSource().getServer().overworld(), data, data.livingBasesOf(f).get(0), r, "admin request");
        c.getSource().sendSuccess(() -> Component.literal(res.message()), true);
        return res.ok() ? 1 : 0;
    }

    /** Diagnostics: compares generation heightmaps with real blocks and scores a candidate site. */
    private static int probe(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getServer().overworld();
        int x = IntegerArgumentType.getInteger(c, "x"), z = IntegerArgumentType.getInteger(c, "z");
        var chunk = level.getChunkSource().getChunk(x >> 4, z >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.SURFACE, true);
        int floor = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15);
        int surf = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15);
        int realTop = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        var top = level.getBlockState(new BlockPos(x, realTop - 1, z));
        var site = com.factionclash.structure.SiteFinder.evaluate(level, x, z, com.factionclash.structure.SiteFinder.settlement(), 4);
        String msg = "probe " + x + "," + z + " status=" + chunk.getPersistedStatus() + " floorWG=" + floor + " surfaceWG=" + surf
                + " realTop=" + realTop + " " + top + " sea=" + level.getSeaLevel() + " site=" + site;
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        FactionClash.LOGGER.info(msg);
        return 1;
    }

    /** Diagnostics: logs a box of blocks layer by layer (first letters of each block id), at most 32x8x32. */
    private static int blockDump(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = c.getSource().getServer().overworld();
        BlockPos a = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(c, "from");
        BlockPos b = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(c, "to");
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.min(Math.max(a.getX(), b.getX()), min.getX() + 31), Math.min(Math.max(a.getY(), b.getY()), min.getY() + 7),
                Math.min(Math.max(a.getZ(), b.getZ()), min.getZ() + 31));
        StringBuilder sb = new StringBuilder("blocks " + min.toShortString() + " .. " + max.toShortString() + "\n");
        for (int y = min.getY(); y <= max.getY(); y++) {
            sb.append("y=").append(y).append("\n");
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                sb.append(String.format("%5d ", z));
                for (int x = min.getX(); x <= max.getX(); x++) {
                    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, y, z)).getBlock()).getPath();
                    sb.append(id.equals("air") ? ".." : id.length() >= 2 ? id.substring(0, 2) : id).append(' ');
                }
                sb.append("\n");
            }
        }
        FactionClash.LOGGER.info(sb.toString());
        c.getSource().sendSuccess(() -> Component.literal("logged"), false);
        return 1;
    }

    /** Diagnostics: logs a mine's levels and the stretch of the current level's plan around its progress, with each cell's block. */
    private static int mineDump(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getServer().overworld();
        WorkplaceState w = CampaignData.get(c.getSource().getServer()).workplace(IntegerArgumentType.getInteger(c, "id"));
        if (w == null || w.type != WorkplaceState.Type.MINE) {
            return 0;
        }
        int y0 = w.origin.getY();
        StringBuilder sb = new StringBuilder("mine " + w.id + " origin " + w.origin.toShortString() + " facing " + w.facing
                + (w.hut ? " (hut)" : "") + " bottom " + w.bottom + " (y=" + (y0 - w.bottom) + ") shaftLimit " + w.shaftLimit
                + " maxRounds " + w.maxRounds + " cur " + w.cur + " shaftStep " + w.upgradeStep + " restTier " + w.restTier
                + (w.workedOut ? (w.givenUp ? " GIVEN UP (" : " WORKED OUT (") + w.note + ")" : "")
                + (w.helper == null ? "" : " helper " + w.helper + " on step " + w.helperStep
                + (com.factionclash.work.Workplaces.helperValid(CampaignData.get(c.getSource().getServer()), w) ? "" : " (stale)")) + "\n");
        for (int i = 0; i < w.levels.size(); i++) {
            WorkplaceState.MineLevel lv = w.levels.get(i);
            sb.append(i == w.cur ? " > " : w.helper != null && lv.step == w.helperStep ? " h " : "   ").append("level ").append(i).append(" step ").append(lv.step).append(" y=").append(y0 - lv.step)
                    .append(" ").append(com.factionclash.work.MineSites.bandOf(w, lv, level.getMinBuildHeight()).label)
                    .append(" tunnel ").append(lv.progress).append("/").append(com.factionclash.structure.MineLayout.tunnelPlan(lv).size())
                    .append(" rounds ").append(lv.rounds).append(lv.cut ? " cut" : "").append("\n");
        }
        WorkplaceState.MineLevel cur = com.factionclash.structure.MineLayout.current(w);
        if (cur != null) {
            var plan = com.factionclash.structure.MineLayout.tunnelPlan(cur);
            for (int i = Math.max(0, cur.progress - 6); i < Math.min(plan.size(), cur.progress + 6); i++) {
                var cell = plan.get(i);
                BlockPos p = com.factionclash.structure.MineLayout.tunnelPos(w, cur, cell);
                sb.append(i).append(i == cur.progress ? " >" : "  ").append(cell.torch() ? "TORCH " : "DIG ").append(p.toShortString())
                        .append(" now=").append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath()).append("\n");
            }
        }
        FactionClash.LOGGER.info(sb.toString());
        c.getSource().sendSuccess(() -> Component.literal("logged"), false);
        return 1;
    }

    private static int destroy(CommandContext<CommandSourceStack> c) {
        CampaignData data = CampaignData.get(c.getSource().getServer());
        BaseState b = data.base(IntegerArgumentType.getInteger(c, "baseId"));
        if (b == null) {
            return 0;
        }
        CampaignManager.destroyBase(c.getSource().getServer().overworld(), b, null, "admin");
        return 1;
    }

    /** {@code raid <faction> <baseId>} (live checks): that faction's raid on that base now, its party picked by the council. */
    private static int raid(CommandContext<CommandSourceStack> c) {
        CampaignData data = CampaignData.get(c.getSource().getServer());
        FactionType t = FactionType.byId(StringArgumentType.getString(c, "faction"));
        if (t == null || !data.factions.containsKey(t)) {
            c.getSource().sendFailure(Component.literal("Name a human faction."));
            return 0;
        }
        String r = com.factionclash.campaign.FactionDirector.forceRaid(c.getSource().getServer().overworld(), data, data.faction(t),
                data.base(IntegerArgumentType.getInteger(c, "baseId")));
        c.getSource().sendSuccess(() -> Component.literal(r), true);
        return 1;
    }

    /** Writes a machine-readable snapshot (used by the automated server tests) and logs it. */
    private static int dump(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getServer().overworld();
        JsonObject root = snapshot(level);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(root);
        try {
            Path out = c.getSource().getServer().getWorldPath(LevelResource.ROOT).resolve("factionclash_dump.json");
            Files.writeString(out, json);
            c.getSource().sendSuccess(() -> Component.literal("Wrote " + out), false);
        } catch (Exception e) {
            FactionClash.LOGGER.error("dump failed", e);
        }
        FactionClash.LOGGER.info("FC_DUMP {}", gson.toJson(root).replace('\n', ' '));
        return 1;
    }

    /**
     * The heroes for {@code sim.py}'s hero lines (SLICE_PLAN 6.4): who each serves, alive or when it returns, level, its
     * war power, fights by who the enemy was, kills, deaths, revivals, casts per move, phases reached, staggers,
     * escapes, last stands (the last four stay 0 until the boss systems count them), dormant, and the loaded entity's
     * health, energy and status.
     */
    static JsonArray heroSnapshot(ServerLevel level, CampaignData data) {
        JsonArray out = new JsonArray();
        long now = level.getGameTime();
        for (com.factionclash.campaign.HeroState s : data.heroes.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", s.id);
            com.factionclash.hero.HeroEntry e = com.factionclash.hero.HeroRegistry.get(s.id);
            o.addProperty("name", e != null ? e.name() : s.id);
            o.addProperty("owner", s.owner == null ? null : s.owner.id);
            o.addProperty("base", s.baseId);
            o.addProperty("alive", s.alive);
            o.addProperty("returnsIn", s.alive || s.reviveAt <= 0 ? 0 : Math.max(0, (s.reviveAt - now) / 20));
            o.addProperty("level", s.level);
            o.addProperty("dormant", e == null || e.dormant());
            o.addProperty("warPower", Math.round(com.factionclash.campaign.FactionDirector.heroWarPower(level, s, false) * 10) / 10.0);
            o.addProperty("kills", s.kills);
            o.addProperty("deaths", s.deaths);
            o.addProperty("revivals", s.revivals);
            o.addProperty("staggers", s.staggers);
            o.addProperty("escapes", s.escapes);
            o.addProperty("lastStands", s.lastStands);
            o.addProperty("phaseReached", s.phaseReached);
            JsonObject fights = new JsonObject();
            s.fights.forEach(fights::addProperty);
            o.add("fights", fights);
            JsonObject casts = new JsonObject();
            s.casts.forEach(casts::addProperty);
            o.add("casts", casts);
            if (s.alive && s.uuid != null && level.getEntity(s.uuid) instanceof com.factionclash.hero.HeroEntity h) {
                o.addProperty("health", Math.round(h.getHealth() * 10) / 10.0);
                o.addProperty("maxHealth", Math.round(h.getMaxHealth() * 10) / 10.0);
                o.addProperty("energy", h.energy());
                o.addProperty("role", h.getRole().id);
                o.addProperty("holding", h.getMainHandItem().isEmpty() ? "nothing" : net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(h.getMainHandItem().getItem()).getPath());
                o.addProperty("status", h.status());
                o.addProperty("pos", h.blockPosition().toShortString());
                // its navigation (A11): links taken by kind and failed, flights and how they ended, stuck recoveries
                if (h.getNavigation() instanceof com.factionclash.hero.HeroGroundNavigation nav) {
                    JsonObject links = new JsonObject();
                    nav.taken.forEach((k, v) -> links.addProperty(k.name().toLowerCase(java.util.Locale.ROOT), v));
                    o.add("links", links);
                    o.addProperty("linksFailed", nav.linksFailed);
                }
                com.factionclash.hero.HeroTravel tr = h.travel();
                o.addProperty("flights", tr.flights);
                o.addProperty("flightsLanded", tr.landed);
                o.addProperty("flightsStuck", tr.stuckEnds);
                o.addProperty("flightsAtEdge", tr.edgeEnds);
                o.addProperty("downs", h.movement().downs());
                o.addProperty("recoveries", h.navigator().recoveries);
            }
            out.add(o);
        }
        return out;
    }

    /**
     * A focused recon as the dump lists it (recon.md 4, task R6): the faces counted and their mean power, the heroes seen
     * at their war power, the yard's coverage, its points and middle, the watch's length, its last sample, the vantage,
     * the scout, the instrument ("spyglass", or "" for the eyes), how many faces it keeps, whether it is good and usable now.
     */
    static JsonObject reconJson(com.factionclash.logic.campaign.Intel.Recon r, long now) {
        JsonObject j = new JsonObject();
        j.addProperty("soldiers", r.soldiers());
        j.addProperty("power", Math.round(r.power() * 100) / 100.0);
        JsonObject heroes = new JsonObject();
        r.heroes().forEach((id, p) -> heroes.addProperty(id, Math.round(p * 100) / 100.0));
        j.add("heroes", heroes);
        j.addProperty("coverage", Math.round(r.coverage() * 100) / 100.0);
        j.addProperty("points", r.points());
        j.addProperty("middle", r.middle());
        j.addProperty("watchTicks", r.watchTicks());
        j.addProperty("at", r.at());
        j.addProperty("vantage", r.vantage());
        j.addProperty("by", r.by());
        j.addProperty("optic", r.optic());
        j.addProperty("faces", r.faces().size());
        j.addProperty("good", r.good());
        j.addProperty("usable", com.factionclash.logic.campaign.Intel.usable(r, now));
        return j;
    }

    /**
     * The last plan a council judged a target by (recon.md 5.5, task R6; {@code FactionDirector.LAST_PLAN}): when, the
     * base, whether it was a counter-attack's or an operator's, the verdict, the party, its power and the soldiers it
     * needed; the recon it rests on, the count, the share and its soldiers, the soldiers seen away (each sighting), the
     * reserve, whether the sightings made it, the heroes in the plan and those known only from sightings, the soldiers'
     * and the heroes' power, the plan's power, its unit and its heads; and the line's "against" text.
     */
    static JsonObject planJson(com.factionclash.campaign.FactionDirector.CouncilPlan cp, long now) {
        com.factionclash.logic.campaign.Reserve.Plan p = cp.plan();
        JsonObject j = new JsonObject();
        j.addProperty("at", cp.at());
        j.addProperty("base", cp.base());
        j.addProperty("subject", cp.subject() == null ? "" : cp.subject().id);
        j.addProperty("lair", cp.lair());
        j.addProperty("counter", cp.counter());
        j.addProperty("byOrder", cp.byOrder());
        j.addProperty("verdict", cp.verdict());
        j.addProperty("party", cp.party());
        j.addProperty("partyPower", Math.round(cp.partyPower() * 100) / 100.0);
        j.addProperty("needed", cp.needed());
        j.add("recon", reconJson(p.recon(), now));
        j.addProperty("counted", p.counted());
        j.addProperty("countedPower", Math.round(p.countedPower() * 100) / 100.0);
        j.addProperty("share", p.share());
        j.addProperty("shareSoldiers", p.shareSoldiers());
        JsonArray away = new JsonArray();
        for (com.factionclash.logic.campaign.Sightings.Sighting x : p.seenAway()) {
            away.add(sightingJson(x));
        }
        j.add("seenAway", away);
        j.addProperty("reserve", p.reserve());
        j.addProperty("reservePower", Math.round(p.reservePower() * 100) / 100.0);
        j.addProperty("fromSightings", p.fromSightings());
        JsonObject heroes = new JsonObject();
        p.heroes().forEach((id, hp) -> heroes.addProperty(id, Math.round(hp * 100) / 100.0));
        j.add("heroes", heroes);
        JsonArray known = new JsonArray();
        for (com.factionclash.logic.campaign.Sightings.Sighting x : p.knownHeroes()) {
            known.add(sightingJson(x));
        }
        j.add("knownHeroes", known);
        j.addProperty("soldierPower", Math.round(p.soldierPower() * 100) / 100.0);
        j.addProperty("heroPower", Math.round(p.heroPower() * 100) / 100.0);
        j.addProperty("power", Math.round(p.power() * 100) / 100.0);
        j.addProperty("unit", Math.round(p.unit() * 100) / 100.0);
        j.addProperty("heads", Math.round(p.heads() * 100) / 100.0);
        j.addProperty("against", cp.against() == null ? "" : cp.against());
        return j;
    }

    /** One sighting as the dump lists it (recon.md 5.5, task R11). */
    static JsonObject sightingJson(com.factionclash.logic.campaign.Sightings.Sighting x) {
        JsonObject si = new JsonObject();
        si.addProperty("id", x.id().toString());
        si.addProperty("faction", x.faction());
        if (x.isHero()) {
            si.addProperty("hero", x.hero());
        }
        si.addProperty("power", Math.round(x.power() * 100) / 100.0);
        si.addProperty("at", x.at());
        si.addProperty("pos", x.x() + ", " + x.z());
        si.addProperty("source", x.source().name().toLowerCase(java.util.Locale.ROOT));
        si.addProperty("by", x.by());
        si.addProperty("base", x.base());
        si.addProperty("fallen", x.fallen());
        return si;
    }

    public static JsonObject snapshot(ServerLevel level) {
        CampaignData data = CampaignData.get(level.getServer());
        JsonObject root = new JsonObject();
        root.addProperty("gameTime", level.getGameTime());
        root.addProperty("dayTime", level.getDayTime());
        root.addProperty("started", data.started);
        root.addProperty("winner", data.winner == null ? null : data.winner.id);
        root.addProperty("travellers", ChunkLoading.travellerCount());
        JsonArray bases = new JsonArray();
        for (BaseState b : data.bases.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", b.id);
            o.addProperty("faction", b.faction.id);
            o.addProperty("kind", b.kind.name());
            o.addProperty("center", b.center.toShortString());
            o.addProperty("destroyed", b.destroyed);
            o.addProperty("starterGranted", b.starterGranted);
            o.addProperty("beds", b.beds.size());
            o.addProperty("pendingRepairs", b.pendingRepairs.size());
            o.addProperty("playerEdits", b.playerEdits.size());
            o.addProperty("projects", b.projects.size());
            o.addProperty("lairMobs", b.lairMobs.size());
            o.addProperty("lairCap", b.lairCap);
            BlockPos station = b.point(BaseState.STATION);
            o.addProperty("stationPresent", station != null && level.getBlockState(station).is(com.factionclash.registry.ModBlocks.REPORTING_STATION.get()));
            if (b.isSettlement()) {
                Map<String, Integer> stock = new LinkedHashMap<>();
                for (Container ct : FactionStorage.all(level, b)) {
                    for (int i = 0; i < ct.getContainerSize(); i++) {
                        ItemStack s = ct.getItem(i);
                        if (!s.isEmpty()) {
                            stock.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(), s.getCount(), Integer::sum);
                        }
                    }
                }
                JsonObject st = new JsonObject();
                stock.forEach(st::addProperty);
                o.add("stock", st);
                o.addProperty("foodValue", Recruitment.storedFoodValue(level, b));
                o.addProperty("shortages", String.join(",", b.shortages));
                JsonObject lg = new JsonObject();
                for (var r : com.factionclash.campaign.Logistics.Resource.values()) {
                    var l = com.factionclash.campaign.Logistics.ledger(b, r);
                    if (l.size() > 0) {
                        lg.addProperty(r.name().toLowerCase(), l.latest() + " (" + com.factionclash.campaign.Logistics.forecast(l) + ")");
                    }
                }
                o.add("logistics", lg);
                JsonArray wps = new JsonArray();
                for (int id : b.workplaces) {
                    WorkplaceState w = data.workplace(id);
                    if (w == null) {
                        continue;
                    }
                    JsonObject wo = new JsonObject();
                    wo.addProperty("id", w.id);
                    wo.addProperty("type", w.type.name());
                    wo.addProperty("tier", w.tier);
                    wo.addProperty("upgradeStep", w.upgradeStep);
                    wo.addProperty("output", w.totalOutput);
                    if (w.type == WorkplaceState.Type.CAVE) {
                        wo.addProperty("marks", w.marks.size());
                        wo.addProperty("route", w.route.length);
                        wo.addProperty("progress", w.progress);
                        wo.addProperty("workedOut", w.workedOut);
                        wo.addProperty("note", w.note);
                        wo.addProperty("origin", w.origin.toShortString());
                    }
                    if (w.type == WorkplaceState.Type.MINE) {
                        wo.addProperty("tier", w.levels.size());
                        wo.addProperty("origin", w.origin.toShortString());
                        wo.addProperty("facing", w.facing.getSerializedName());
                        wo.addProperty("hut", w.hut);
                        wo.addProperty("bottom", w.bottom);
                        wo.addProperty("shaftLimit", w.shaftLimit);
                        wo.addProperty("maxRounds", w.maxRounds);
                        wo.addProperty("cur", w.cur);
                        wo.addProperty("restTier", w.restTier);
                        wo.addProperty("workedOut", w.workedOut);
                        wo.addProperty("givenUp", w.givenUp);
                        wo.addProperty("note", w.note);
                        if (w.helper != null) {
                            wo.addProperty("helper", w.helper.toString());
                            wo.addProperty("helperY", w.origin.getY() - w.helperStep);
                            wo.addProperty("helperValid", com.factionclash.work.Workplaces.helperValid(data, w));
                        }
                        JsonArray lvs = new JsonArray();
                        for (WorkplaceState.MineLevel lv : w.levels) {
                            JsonObject lo = new JsonObject();
                            lo.addProperty("y", w.origin.getY() - lv.step);
                            lo.addProperty("band", com.factionclash.work.MineSites.bandOf(w, lv, level.getMinBuildHeight()).name());
                            lo.addProperty("progress", lv.progress);
                            lo.addProperty("size", com.factionclash.structure.MineLayout.tunnelPlan(lv).size());
                            lo.addProperty("rounds", lv.rounds);
                            lo.addProperty("cut", lv.cut);
                            lvs.add(lo);
                        }
                        wo.add("levels", lvs);
                    }
                    if (w.type == WorkplaceState.Type.TREE_PLOT) {
                        wo.addProperty("origin", w.origin.toShortString());
                        wo.addProperty("felled", w.felled);
                        wo.addProperty("planted", w.planted.size());
                        wo.addProperty("replant", w.replant.size());
                        wo.addProperty("legacyFence", w.legacyFence);
                    }
                    if (w.type == WorkplaceState.Type.FISHERY) {
                        wo.addProperty("origin", w.origin.toShortString());
                        wo.addProperty("extent", w.extent.toShortString());
                        wo.addProperty("spot", w.spot == com.factionclash.logic.FishingWater.VALID ? "VALID"
                                : w.spot == com.factionclash.logic.FishingWater.NONE ? "NONE" : "UNSEARCHED");
                        wo.addProperty("open", w.open);
                        wo.addProperty("searched", w.searched);
                    }
                    wo.addProperty("assigned", w.assigned == null ? null : w.assigned.toString());
                    wps.add(wo);
                }
                o.add("workplaces", wps);
                o.addProperty("recruitNote", com.factionclash.campaign.FactionDirector.LAST_RECRUIT_NOTE.getOrDefault(b.id, ""));
                BlockPos penC = b.point("pen");
                if (penC != null) {
                    JsonObject pen = new JsonObject();
                    for (com.factionclash.work.PenJob.Species sp : com.factionclash.work.PenJob.SPECIES) {
                        var list = level.getEntities(sp.type(), new net.minecraft.world.phys.AABB(penC).inflate(5, 2, 3),
                                net.minecraft.world.entity.LivingEntity::isAlive);
                        if (!list.isEmpty()) {
                            pen.addProperty(sp.name(), list.stream().filter(x -> !x.isBaby()).count() + " adults + "
                                    + list.stream().filter(x -> x.isBaby()).count() + " young");
                        }
                    }
                    o.add("pen", pen);
                }
            }
            bases.add(o);
        }
        root.add("bases", bases);
        JsonArray factions = new JsonArray();
        for (FactionState f : data.factions.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("faction", f.type.id);
            o.addProperty("alive", f.alive);
            o.addProperty("knowledge", f.knowledge.size());
            o.addProperty("reportsDelivered", f.reportsDelivered);
            o.addProperty("xpBank", f.xpBank);
            o.addProperty("explored", f.explored.size());
            o.addProperty("raidsLaunched", f.raidsLaunched);
            o.addProperty("raidTarget", f.raidTarget);
            o.addProperty("raidPlanTicks", f.raidPlanTicks);
            o.addProperty("basesDestroyed", f.basesDestroyed);
            o.addProperty("lastRaidAt", f.lastRaidAt);
            // what the faction knows of enemy bases (docs/design/knowledge.md, recon.md): where each is, whether it is seen in
            // ruins, and its focused recons (recon.md 4, task R6): the kept good recon, the failed attempts since and the
            // sides tried; "base" is the base its report names (the operators' key, FactionDirector.reportedBase)
            long now = level.getGameTime();
            JsonArray seen = new JsonArray();
            for (com.factionclash.campaign.Report r : f.knowledge) {
                if (!r.base()) {
                    continue;
                }
                JsonObject so = new JsonObject();
                so.addProperty("subject", r.subject == null ? "?" : r.subject.id);
                so.addProperty("kind", r.kind.name());
                so.addProperty("pos", r.pos.toShortString());
                so.addProperty("fallen", r.fallen);
                so.addProperty("by", r.by);
                so.addProperty("observedAt", r.observedAt);
                so.addProperty("deliveredAt", r.deliveredAt);
                BaseState named = com.factionclash.campaign.FactionDirector.reportedBase(data, r);
                so.addProperty("base", named == null ? -1 : named.id);
                if (r.recon != null) {
                    so.add("recon", reconJson(r.recon, now));
                }
                so.addProperty("reconFailedAt", r.reconFailedAt);
                so.addProperty("reconFailures", r.reconFailures);
                so.addProperty("reconSidesTried", r.reconSidesTried);
                so.addProperty("backingOff", com.factionclash.logic.campaign.ReconRules.backingOff(r.reconFile(), now));
                seen.add(so);
            }
            o.add("enemyBasesKnown", seen);
            // the council's recon orders (recon.md 2.1), the strikes it saw (6), its last urgent orders, the heroes its
            // raid's recon saw (R9) and the last plan it judged a target by (5.5): the faction's own knowledge
            JsonArray orders = new JsonArray();
            for (com.factionclash.campaign.ReconOrder ro : f.reconOrders) {
                JsonObject oo = new JsonObject();
                oo.addProperty("target", ro.target == null ? "" : ro.target.toShortString());
                oo.addProperty("subject", ro.subject == null ? "" : ro.subject.id);
                oo.addProperty("kind", ro.kind == null ? "" : ro.kind.name());
                oo.addProperty("homeBase", ro.homeBase);
                oo.addProperty("orderedAt", ro.orderedAt);
                oo.addProperty("urgent", ro.urgent);
                oo.addProperty("scout", ro.scout == null ? "" : ro.scout.toString());
                NpcRecord sr = ro.scout == null ? null : f.roster.get(ro.scout);
                oo.addProperty("scoutName", sr == null ? "" : sr.name);
                oo.addProperty("takenAt", ro.takenAt);
                oo.addProperty("reported", ro.reported.name());
                oo.addProperty("reportedSides", ro.reportedSides);
                orders.add(oo);
            }
            o.add("reconOrders", orders);
            JsonObject struck = new JsonObject();
            f.struckBy.forEach((by, at) -> struck.addProperty(by.id, at));
            o.add("struckBy", struck);
            JsonObject urgentAt = new JsonObject();
            f.reconUrgentAt.forEach((by, at) -> urgentAt.addProperty(by.id, at));
            o.add("reconUrgentAt", urgentAt);
            JsonArray reconHeroes = new JsonArray();
            f.raidReconHeroes.forEach(reconHeroes::add);
            o.add("raidReconHeroes", reconHeroes);
            com.factionclash.campaign.FactionDirector.CouncilPlan cp = com.factionclash.campaign.FactionDirector.LAST_PLAN.get(f.type);
            if (cp != null) {
                o.add("plan", planJson(cp, now));
            }
            // what its people saw of enemy soldiers and heroes away from their bases (recon.md 5.5, task R11): the
            // council's reserve reads them on top of a fresh good recon
            JsonArray sightings = new JsonArray();
            for (com.factionclash.logic.campaign.Sightings.Sighting x : f.sightings.all()) {
                sightings.add(sightingJson(x));
            }
            o.add("sightings", sightings);
            JsonArray roster = new JsonArray();
            for (NpcRecord r : f.roster.values()) {
                JsonObject ro = new JsonObject();
                ro.addProperty("uuid", r.uuid.toString());
                ro.addProperty("name", r.name);
                ro.addProperty("role", r.role.id);
                ro.addProperty("alive", r.alive);
                ro.addProperty("level", r.level);
                ro.addProperty("pos", r.lastPos.toShortString());
                ro.addProperty("base", r.baseId);
                ro.addProperty("departedAt", r.departedAt);
                // the roll call (docs/design/missing_npcs.md): what the faction believes, and the truth beside it
                ro.addProperty("where", r.where.name());
                ro.addProperty("leftAt", r.leftAt);
                ro.addProperty("expectedBy", r.expectedBy);
                ro.addProperty("since", r.since);
                ro.addProperty("dead", r.dead);
                ro.addProperty("vanished", r.vanished);
                ro.addProperty("diedAt", r.diedAt);
                ro.addProperty("witnessed", r.witnessed);
                roster.add(ro);
            }
            o.add("roster", roster);
            JsonArray news = new JsonArray();
            f.news.forEach((carrier, items) -> {
                for (com.factionclash.campaign.News nw : items) {
                    JsonObject no = new JsonObject();
                    no.addProperty("carrier", carrier.toString());
                    no.addProperty("kind", nw.kind.name());
                    no.addProperty("text", nw.text);
                    no.addProperty("at", nw.at);
                    news.add(no);
                }
            });
            o.add("news", news);
            String council = com.factionclash.campaign.FactionDirector.LAST_COUNCIL.get(f.type);
            o.addProperty("council", council == null ? "" : council);
            factions.add(o);
        }
        root.add("factions", factions);
        root.add("heroes", heroSnapshot(level, data));
        JsonArray npcs = new JsonArray();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof FactionNpc n) {
                JsonObject o = new JsonObject();
                o.addProperty("uuid", n.getUUID().toString());
                o.addProperty("name", n.npcName());
                o.addProperty("faction", n.getFaction().id);
                o.addProperty("role", n.getRole().id);
                o.addProperty("pos", n.blockPosition().toShortString());
                o.addProperty("health", n.getHealth());
                o.addProperty("level", n.npcLevel());
                o.addProperty("xp", n.xp());
                o.addProperty("status", n.status());
                o.addProperty("tool", n.getMainHandItem().getHoverName().getString());
                o.addProperty("toolDamage", n.getMainHandItem().getDamageValue());
                o.addProperty("toolMax", n.getMainHandItem().getMaxDamage());
                o.addProperty("food", n.hunger().foodLevel());
                o.addProperty("saturation", n.hunger().saturation());
                o.addProperty("meals", com.factionclash.work.Inv.count(n.inventory(), com.factionclash.work.Job::isProvision));
                o.addProperty("toolEnch", n.getMainHandItem().getEnchantments().keySet().stream()
                        .map(h -> net.minecraft.world.item.enchantment.Enchantment.getFullname(h, n.getMainHandItem().getEnchantments().getLevel(h)).getString())
                        .collect(java.util.stream.Collectors.joining(", ")));
                o.addProperty("ammo", n.ammo().isEmpty() ? "" : n.ammo().getHoverName().getString());
                o.addProperty("items", com.factionclash.work.Inv.totalItems(n.inventory()));
                o.addProperty("recoveries", n.navigator().recoveries);
                o.addProperty("hero", n instanceof com.factionclash.hero.HeroEntity);
                BlockPos dest = n.navigator().target();
                o.addProperty("dest", dest == null ? "" : dest.toShortString());
                o.addProperty("surface", n.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        n.getBlockX(), n.getBlockZ()));
                o.addProperty("problem", n.navigator().lastProblem());
                o.addProperty("target", n.getTarget() == null ? "" : n.getTarget().getType().toShortString() + "@" + n.getTarget().blockPosition().toShortString());
                o.addProperty("xyz", String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", n.getX(), n.getY(), n.getZ()));
                com.factionclash.combat.Pursuit pu = n.pursuit();
                o.addProperty("pursuit", pu.mode() + (pu.holdSpot() == null ? "" : " hold@" + pu.holdSpot().toShortString())
                        + " plans=" + pu.plans + " holds=" + pu.holds + " breaks=" + pu.breaks + " pillars=" + pu.pillars
                        + " gates=" + n.gateGoal().cycles);
                net.minecraft.world.level.pathfinder.Path np = n.getNavigation().getPath();
                o.addProperty("path", np == null ? "none" : np.isDone() ? "done" : np.getNextNodeIndex() + "/" + np.getNodeCount()
                        + (np.canReach() ? " reach " : " partial ") + np.getEndNode().asBlockPos().toShortString());
                o.addProperty("goals", String.join(",", n.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning())
                        .map(g -> g.getGoal().getClass().getSimpleName()).toList()));
                JsonObject af = new JsonObject();
                n.affixes().forEach((k, v) -> af.addProperty(k.name(), v));
                o.add("affixes", af);
                o.addProperty("workplace", n.job() == null ? "" : n.job().workplaceInfo());
                o.addProperty("base", n.baseId());
                // a scout's recon trip (recon.md 3.8, task R6): the order, the side, the pillar, the watch, the holds
                if (n.job() instanceof com.factionclash.work.ScoutJob sj && sj.reconJson() != null) {
                    o.add("recon", sj.reconJson());
                }
                npcs.add(o);
            }
        }
        root.add("npcs", npcs);
        return root;
    }
}
