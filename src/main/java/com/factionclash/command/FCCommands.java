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
                .then(Commands.literal("achievements").executes(c -> achievements(c, null))
                        .then(Commands.argument("faction", StringArgumentType.word()).suggests(FACTIONS)
                                .executes(c -> achievements(c, StringArgumentType.getString(c, "faction")))))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("dump").executes(FCCommands::dump))
                        .then(Commands.literal("npcs").executes(FCCommands::npcs))
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
                        .then(Commands.literal("destroy").then(Commands.argument("baseId", IntegerArgumentType.integer(1)).executes(FCCommands::destroy)))));
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

    /** Diagnostics: logs every planned excavation cell of a mine's next tier and its current block. */
    private static int mineDump(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getServer().overworld();
        WorkplaceState w = CampaignData.get(c.getSource().getServer()).workplace(IntegerArgumentType.getInteger(c, "id"));
        if (w == null || w.type != WorkplaceState.Type.MINE) {
            return 0;
        }
        int lv = com.factionclash.structure.MineLayout.level(w);
        var plan = com.factionclash.structure.MineLayout.tunnelPlan(w);
        StringBuilder sb = new StringBuilder("mine " + w.id + " tier " + w.tier + " +" + w.subLevel + " level y="
                + com.factionclash.logic.SpiralMine.levelY(w.origin.getY(), lv) + " shaftStep " + w.upgradeStep
                + " tunnel " + w.progress + "/" + plan.size() + "\n");
        for (int i = Math.max(0, w.progress - 6); i < Math.min(plan.size(), w.progress + 6); i++) {
            var cell = plan.get(i);
            BlockPos p = com.factionclash.structure.MineLayout.tunnelPos(w, cell);
            sb.append(i).append(i == w.progress ? " >" : "  ").append(cell.torch() ? "TORCH " : "DIG ").append(p.toShortString())
                    .append(" now=").append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath()).append("\n");
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
            o.addProperty("basesDestroyed", f.basesDestroyed);
            JsonArray roster = new JsonArray();
            for (NpcRecord r : f.roster.values()) {
                JsonObject ro = new JsonObject();
                ro.addProperty("uuid", r.uuid.toString());
                ro.addProperty("name", r.name);
                ro.addProperty("role", r.role.id);
                ro.addProperty("alive", r.alive);
                ro.addProperty("level", r.level);
                ro.addProperty("pos", r.lastPos.toShortString());
                ro.addProperty("departedAt", r.departedAt);
                roster.add(ro);
            }
            o.add("roster", roster);
            factions.add(o);
        }
        root.add("factions", factions);
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
                BlockPos dest = n.navigator().target();
                o.addProperty("dest", dest == null ? "" : dest.toShortString());
                o.addProperty("surface", n.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        n.getBlockX(), n.getBlockZ()));
                o.addProperty("problem", n.navigator().lastProblem());
                o.addProperty("target", n.getTarget() == null ? "" : n.getTarget().getType().toShortString() + "@" + n.getTarget().blockPosition().toShortString());
                o.addProperty("goals", String.join(",", n.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning())
                        .map(g -> g.getGoal().getClass().getSimpleName()).toList()));
                JsonObject af = new JsonObject();
                n.affixes().forEach((k, v) -> af.addProperty(k.name(), v));
                o.add("affixes", af);
                o.addProperty("workplace", n.job() == null ? "" : n.job().workplaceInfo());
                npcs.add(o);
            }
        }
        root.add("npcs", npcs);
        return root;
    }
}
