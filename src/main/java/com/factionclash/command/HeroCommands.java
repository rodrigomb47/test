package com.factionclash.command;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.HeroState;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroEntry;
import com.factionclash.hero.HeroManager;
import com.factionclash.hero.HeroRegistry;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroAssembly;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroReader;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.logic.hero.Problems;
import com.factionclash.logic.hero.HeroClips;
import com.factionclash.logic.hero.HeroRevival;
import com.factionclash.logic.hero.HeroSize;
import com.factionclash.logic.hero.HeroTimings;
import com.factionclash.hero.HeroTimingFiles;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Hero commands (HEROES_V2 12.1; v1 27): {@code /fc hero list|info <id>} for everyone, {@code /fc hero check <id|all>}
 * (the definitions read and checked now, nothing applied) and {@code /fc hero reload} (read, checked and applied to the
 * living heroes) for operators, and {@code /fc admin hero give <id> <faction> [pos]|release <id>|revive <id>}. The
 * sheet screen comes later; {@code info} prints the sheet as text.
 *
 * <p>Screenshot tools (A2, operators): {@code preview <id> [faction] [pos] [yaw]} stands a display copy of a hero
 * ({@link HeroManager#preview}); {@code lineup [first] [count] [spacing] [faction]} stands a row of them in front of the
 * source, facing it (heroes in id order, preview fixtures included; from RCON use {@code execute as <player> at
 * <player> run ...}); {@code preview clear [id]} removes them; {@code pose <id|all> ...} forces what the model shows
 * on every loaded entity of that hero (the campaign's and its previews): a mood, an action at a speed, a base pose,
 * the weapon flag, talking, the wind-up flag (no blinking), the phase, blush, glowing eyes, or {@code clear}.
 */
public final class HeroCommands {
    private static final SuggestionProvider<CommandSourceStack> HEROES = (c, b) ->
            SharedSuggestionProvider.suggest(HeroRegistry.heroes().stream().map(HeroEntry::id), b);
    private static final SuggestionProvider<CommandSourceStack> FACTIONS = (c, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(FactionType.humans()).map(f -> f.id), b);

    private HeroCommands() {
    }

    /** {@code /fc hero ...} */
    public static LiteralArgumentBuilder<CommandSourceStack> player() {
        return Commands.literal("hero")
                .then(Commands.literal("list").executes(HeroCommands::list))
                .then(Commands.literal("info").then(Commands.argument("id", StringArgumentType.word()).suggests(HEROES).executes(HeroCommands::info)))
                .then(Commands.literal("check").requires(src -> src.hasPermission(2))
                        .executes(c -> check(c, "all"))
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(HEROES_OR_ALL).executes(c -> check(c, id(c)))))
                .then(Commands.literal("reload").requires(src -> src.hasPermission(2)).executes(HeroCommands::reload));
    }

    // ------------------------------------------------------------------ check and reload (A1b; v1 26.4, 27)

    /** At most this many problem lines in chat; the rest are in the log and {@code config/factionclash/heroes/_report.txt}. */
    private static final int CHAT_LINES = 30;

    /**
     * {@code /fc hero check <id|all>}: reads the definitions now (the server's packs and the config overrides) and checks
     * them without applying anything; prints the problems, then what the mod makes of the hero (v1 26.4).
     */
    private static int check(CommandContext<CommandSourceStack> c, String id) {
        MinecraftServer server = c.getSource().getServer();
        HeroAssembly.Result r = HeroRegistry.check(com.factionclash.hero.HeroDefinitions.read(server.getResourceManager()));
        HeroReader.Loaded l = r.loaded();
        boolean all = id.equals("all");
        List<Problems.Problem> problems = new java.util.ArrayList<>();
        for (Problems.Problem p : l.problems().all()) {
            if (all || id.equals(p.hero()) || p.file().endsWith("/" + id + ".json")) {
                problems.add(p);
            }
        }
        if (!all && l.hero(id) == null && problems.isEmpty()) {
            c.getSource().sendFailure(Component.literal("No hero definition \"" + id + "\" (data/" + com.factionclash.FactionClash.MODID
                    + "/heroes/" + id + ".json). /fc hero list shows the heroes."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal(l.problems().summary(all ? l.heroes().keySet()
                : l.hero(id) == null ? List.of() : List.of(id), all ? l.notLoaded() : l.notLoaded().contains(id) ? List.of(id) : List.of())
                + (problems.isEmpty() ? ", nothing to report" : ", " + problems.size() + " problem(s)")).withStyle(ChatFormatting.GOLD), false);
        problems.sort(java.util.Comparator.comparing((Problems.Problem p) -> p.severity().ordinal()).thenComparing(Problems.Problem::file)
                .thenComparingInt(Problems.Problem::line));
        int shown = 0;
        for (Problems.Problem p : problems) {
            if (shown++ >= CHAT_LINES) {
                int rest = problems.size() - CHAT_LINES;
                c.getSource().sendSuccess(() -> Component.literal(" ... and " + rest + " more (server log, config/"
                        + com.factionclash.FactionClash.MODID + "/heroes/_report.txt after a reload)").withStyle(ChatFormatting.GRAY), false);
                break;
            }
            ChatFormatting colour = switch (p.severity()) {
                case NOT_LOADED -> ChatFormatting.RED;
                case PART_LEFT_OUT -> ChatFormatting.GOLD;
                case ADJUSTED -> ChatFormatting.YELLOW;
                case NOTE -> ChatFormatting.GRAY;
            };
            String line = p.format();
            c.getSource().sendSuccess(() -> Component.literal(" " + line).withStyle(colour), false);
        }
        if (!all && l.hero(id) != null) {
            HeroEntry e = HeroRegistry.get(id);
            for (String line : sheet(l.hero(id), e == null ? null : e.style(), currentLevel(server, id))) {
                c.getSource().sendSuccess(() -> Component.literal(" " + line), false);
            }
        }
        return l.notLoaded().isEmpty() || !all && !l.notLoaded().contains(id) ? 1 : 0;
    }

    private static int currentLevel(MinecraftServer server, String id) {
        HeroState s = CampaignData.get(server).heroes.get(id);
        return s == null ? 1 : Math.max(1, s.level);
    }

    /**
     * What the mod makes of a hero (v1 26.4): role and style, its numbers with boss health at levels 1, its current one
     * and 30, its kit, moves and ultimates with their classes, the end of its fights, its revival, talents and lines.
     */
    public static List<String> sheet(HeroDef d, String style, int level) {
        List<String> out = new java.util.ArrayList<>();
        out.add(d.name() + (d.title() == null || d.title().isEmpty() ? "" : " \u00b7 " + d.title()) + " (" + d.id() + "): "
                + d.rarity().id() + " " + d.behaviour().role().id() + ", fights as " + com.factionclash.logic.hero.HeroLoadout.combatRoleId(d, style)
                + (com.factionclash.logic.hero.HeroLoadout.dutyRoleId(d) == null ? "" : ", works as " + com.factionclash.logic.hero.HeroLoadout.dutyRoleId(d))
                + (com.factionclash.logic.hero.HeroLoadout.bareHanded(d, style) ? " with its bare hands" : "")
                + ", " + d.basicAttack().style().id() + " basic attack, style " + (style == null ? "-" : style));
        int scale = FCConfig.BOSS_HEALTH_SCALE.get();
        for (int lv : new java.util.TreeSet<>(List.of(1, Math.max(1, level), com.factionclash.logic.hero.HeroCatalogue.MAX_LEVEL))) {
            var n = com.factionclash.logic.hero.HeroLoadout.numbers(d, lv, false, scale);
            out.add(String.format(Locale.ROOT, " level %d: %.0f health (%.1f x %.0f%% x %.2f), +%.2f attack, +%.2f armour, speed %.0f%%,"
                            + " knock-back resistance %.0f%%, war power %.1f", lv, n.maxHealth(), n.baseHealth(), n.healthScale(), n.roleFactor(),
                    n.attackBonus(), n.armorBonus(), (1 + n.speedModifier()) * 100, n.knockbackResistance() * 100,
                    HeroRules.heroPower(n.maxHealth(), lv, FCConfig.HERO_POWER_PERCENT.get())));
        }
        StringBuilder kit = new StringBuilder(" kit:");
        for (HeroDef.GearItem g : com.factionclash.logic.hero.HeroLoadout.kit(d, style)) {
            kit.append(' ').append(g.item()).append(g.name() == null ? "" : " \"" + g.name() + "\"").append(g.enchantments().isEmpty() ? "" : " " + g.enchantments());
        }
        out.add(kit.length() == 5 ? " kit: nothing (bare hands)" : kit.toString());
        StringBuilder moves = new StringBuilder(" moves:");
        for (HeroDef.Move m : d.v2().allMoves()) {
            moves.append(' ').append(m.id()).append(" (").append(m.cls().id()).append(' ').append(m.form().id()).append(')');
        }
        out.add(moves.length() == 7 ? " moves: none (basic attacks only)" : moves + "; run by the move engine from A3/A5");
        out.add(" end of a fight: " + d.v2().end().sheetLine() + "; phases " + d.v2().boss().phases().size() + ", poise "
                + d.v2().boss().poise() + ", per-hit cap " + d.v2().boss().maxHitShare() + "%");
        HeroDef.Revival rv = d.v2().revival();
        int own = rv == null ? 0 : rv.seconds();
        out.add(" revival: returns " + HeroRevival.countdown(HeroRules.reviveDelaySeconds(FCConfig.HERO_REVIVE_SECONDS.get(), own, 0,
                FCConfig.HERO_MIN_REVIVE_SECONDS.get()) * 20L) + " after a death" + (rv == null ? "" : " (its own revival: "
                + rv.where().id() + (rv.at() == HeroDef.RiseAt.NIGHT ? ", at nightfall" : "") + ")")
                + "; a faction's revival_rite shortens it, never under " + FCConfig.HERO_MIN_REVIVE_SECONDS.get() + " s");
        out.add(" talents: " + (d.v2().talents().isEmpty() ? "none" : String.join(", ", d.v2().talents().stream()
                .map(t -> t.id().id() + (t.realm() ? " (realm)" : "")).toList())));
        int lines = d.lines().values().stream().mapToInt(List::size).sum();
        out.add(" lines: " + lines + " in " + d.lines().size() + " event(s): " + String.join(", ", d.lines().keySet().stream().map(HeroDef.LineEvent::id).toList()));
        return out;
    }

    /**
     * {@code /fc hero reload}: the definitions read again from the server's packs, the overrides and the visual files
     * from disk, checked, applied to every loaded hero at once (a broken or removed file leaves its hero dormant), and
     * the summary with every NOT LOADED and PART LEFT OUT line in chat (v1 26.4).
     */
    private static int reload(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        HeroRegistry.reload(server.getResourceManager());
        int refreshed = HeroManager.refreshAll(server);
        HeroReader.Loaded l = HeroRegistry.report();
        c.getSource().sendSuccess(() -> Component.literal(l.problems().summary(l.heroes().keySet(), l.notLoaded()) + "; "
                + refreshed + " hero(es) in the world updated").withStyle(ChatFormatting.GOLD), true);
        int shown = 0;
        for (Problems.Problem p : l.problems().all()) {
            if (p.severity() != Problems.Severity.NOT_LOADED && p.severity() != Problems.Severity.PART_LEFT_OUT) {
                continue;
            }
            if (shown++ >= CHAT_LINES) {
                break;
            }
            String line = p.format();
            ChatFormatting colour = p.severity() == Problems.Severity.NOT_LOADED ? ChatFormatting.RED : ChatFormatting.GOLD;
            c.getSource().sendSuccess(() -> Component.literal(" " + line).withStyle(colour), false);
        }
        for (String p : HeroRegistry.problems()) {
            c.getSource().sendSuccess(() -> Component.literal(" " + p).withStyle(ChatFormatting.YELLOW), false);
        }
        return l.heroes().size();
    }

    /** {@code /fc admin hero ...} */
    public static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("hero")
                .then(Commands.literal("give").then(Commands.argument("id", StringArgumentType.word()).suggests(HEROES)
                        .then(Commands.argument("faction", StringArgumentType.word()).suggests(FACTIONS)
                                .executes(c -> give(c, null))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(c -> give(c, BlockPosArgument.getLoadedBlockPos(c, "pos")))))))
                .then(Commands.literal("release").then(Commands.argument("id", StringArgumentType.word()).suggests(HEROES)
                        .executes(c -> report(c, HeroManager.release(c.getSource().getServer(), id(c))))))
                .then(Commands.literal("revive").then(Commands.argument("id", StringArgumentType.word()).suggests(HEROES)
                        .executes(c -> report(c, HeroManager.revive(c.getSource().getServer(), id(c))))))
                // A6b's test hooks (SLICE_PLAN 6.5): health share, poise, an escape on demand, the target's read state
                .then(Commands.literal("health").then(Commands.argument("id", StringArgumentType.word()).suggests(HEROES)
                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.1, 100))
                                .executes(c -> health(c, DoubleArgumentType.getDouble(c, "percent"))))))
                .then(Commands.literal("poise").then(Commands.argument("id", StringArgumentType.word()).suggests(HEROES)
                        .then(Commands.literal("full").executes(c -> poise(c, -1)))
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0, 1000))
                                .executes(c -> poise(c, DoubleArgumentType.getDouble(c, "amount"))))))
                .then(Commands.literal("escape").then(Commands.argument("id", StringArgumentType.word()).suggests(HEROES)
                        .executes(HeroCommands::escape)))
                .then(Commands.literal("target-state").then(Commands.argument("id", StringArgumentType.word()).suggests(HEROES)
                        .then(Commands.argument("state", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("blocking", "bow", "eating", "clear"), b))
                                .executes(HeroCommands::targetState))))
                .then(Commands.literal("preview")
                        .then(Commands.literal("clear")
                                .executes(c -> clearPreviews(c, null))
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(HEROES).executes(c -> clearPreviews(c, id(c)))))
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(HEROES)
                                .executes(c -> preview(c, null, null, null))
                                .then(Commands.argument("faction", StringArgumentType.word()).suggests(FACTIONS)
                                        .executes(c -> preview(c, faction(c), null, null))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(c -> preview(c, faction(c), BlockPosArgument.getLoadedBlockPos(c, "pos"), null))
                                                .then(Commands.argument("yaw", FloatArgumentType.floatArg(-360, 360))
                                                        .executes(c -> preview(c, faction(c), BlockPosArgument.getLoadedBlockPos(c, "pos"),
                                                                FloatArgumentType.getFloat(c, "yaw"))))))))
                .then(Commands.literal("lineup")
                        .then(Commands.literal("clear").executes(c -> clearPreviews(c, null)))
                        .executes(c -> lineup(c, 0, 64, 2.0, null))
                        .then(Commands.argument("first", IntegerArgumentType.integer(0))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(c -> lineup(c, IntegerArgumentType.getInteger(c, "first"), IntegerArgumentType.getInteger(c, "count"), 2.0, null))
                                        .then(Commands.argument("spacing", DoubleArgumentType.doubleArg(0.75, 16))
                                                .executes(c -> lineup(c, IntegerArgumentType.getInteger(c, "first"), IntegerArgumentType.getInteger(c, "count"),
                                                        DoubleArgumentType.getDouble(c, "spacing"), null))
                                                .then(Commands.argument("faction", StringArgumentType.word()).suggests(FACTIONS)
                                                        .executes(c -> lineup(c, IntegerArgumentType.getInteger(c, "first"),
                                                                IntegerArgumentType.getInteger(c, "count"), DoubleArgumentType.getDouble(c, "spacing"),
                                                                faction(c))))))))
                .then(Commands.literal("pose").then(Commands.argument("id", StringArgumentType.word()).suggests(HEROES_OR_ALL)
                        .then(Commands.literal("clear").executes(c -> pose(c, "clear", null, 0)))
                        .then(Commands.literal("stop").executes(c -> pose(c, "stop", null, 0)))
                        .then(Commands.literal("mood").then(Commands.argument("value", StringArgumentType.word()).suggests(MOODS)
                                .executes(c -> pose(c, "mood", value(c), 0))))
                        .then(Commands.literal("action").then(Commands.argument("value", StringArgumentType.string()).suggests(ACTIONS)
                                .executes(c -> pose(c, "action", value(c), 1.0))
                                .then(Commands.argument("speed", DoubleArgumentType.doubleArg(HeroActions.MIN_SPEED, HeroActions.MAX_SPEED))
                                        .executes(c -> pose(c, "action", value(c), DoubleArgumentType.getDouble(c, "speed"))))))
                        .then(Commands.literal("base").then(Commands.argument("value", StringArgumentType.word()).suggests(BASES)
                                .executes(c -> pose(c, "base", value(c), 0))))
                        .then(Commands.literal("weapon").then(Commands.argument("value", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("drawn", "sheathed", "auto"), b))
                                .executes(c -> pose(c, "weapon", value(c), 0))))
                        .then(Commands.literal("phase").then(Commands.argument("n", IntegerArgumentType.integer(1, 4))
                                .executes(c -> pose(c, "phase", null, IntegerArgumentType.getInteger(c, "n")))))
                        .then(flag("talk"))
                        .then(flag("windup"))
                        .then(flag("blush"))
                        .then(flag("glow"))));
    }

    private static final SuggestionProvider<CommandSourceStack> HEROES_OR_ALL = (c, b) ->
            SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(java.util.stream.Stream.of("all"),
                    HeroRegistry.heroes().stream().map(HeroEntry::id)), b);
    private static final SuggestionProvider<CommandSourceStack> MOODS = (c, b) ->
            SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(HeroActions.MOODS.stream(), java.util.stream.Stream.of("auto")), b);
    private static final SuggestionProvider<CommandSourceStack> BASES = (c, b) -> SharedSuggestionProvider.suggest(HeroClips.FORCED, b);
    private static final SuggestionProvider<CommandSourceStack> ACTIONS = (c, b) -> {
        List<String> names = new java.util.ArrayList<>();
        for (int code = 1; code < 256; code++) {
            String n = HeroActions.actionName(code);
            if (n != null && !n.startsWith("move.") && !n.startsWith("state.")) {
                names.add(n);
            }
        }
        return SharedSuggestionProvider.suggest(names, b);
    };

    private static LiteralArgumentBuilder<CommandSourceStack> flag(String name) {
        return Commands.literal(name).then(Commands.argument("value", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("on", "off"), b))
                .executes(c -> pose(c, name, value(c), 0)));
    }

    private static String value(CommandContext<CommandSourceStack> c) {
        return StringArgumentType.getString(c, "value").toLowerCase(Locale.ROOT);
    }

    private static FactionType faction(CommandContext<CommandSourceStack> c) {
        return FactionType.byId(StringArgumentType.getString(c, "faction"));
    }

    // ------------------------------------------------------------------ screenshot tools (A2)

    /** A display copy where the operator stands (or at {@code pos}), facing the operator (or {@code yaw}). */
    private static int preview(CommandContext<CommandSourceStack> c, FactionType tint, BlockPos pos, Float yaw) {
        CommandSourceStack src = c.getSource();
        if (tint == null && hasArgument(c, "faction")) {
            src.sendFailure(Component.literal("Name a faction for the sash colour: " + String.join(", ",
                    Arrays.stream(FactionType.values()).map(x -> x.id).toList())));
            return 0;
        }
        Vec3 at = pos != null ? Vec3.atBottomCenterOf(pos) : src.getPosition();
        float facing = yaw != null ? yaw : src.getRotation().y + 180F;
        BlockPos p = pos != null ? pos : BlockPos.containing(at);
        HeroManager.Result r = HeroManager.preview(src.getLevel(), id(c), tint != null ? tint : FactionType.EMBER_LEGION, p, facing);
        return report(c, r);
    }

    private static boolean hasArgument(CommandContext<CommandSourceStack> c, String name) {
        try {
            c.getArgument(name, String.class);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * A row of display copies 4 blocks in front of the source, {@code spacing} apart along its right hand, all facing
     * it: heroes {@code first} to {@code first + count - 1} in id order (preview fixtures included), each on the ground.
     */
    private static int lineup(CommandContext<CommandSourceStack> c, int first, int count, double spacing, FactionType tint) {
        CommandSourceStack src = c.getSource();
        if (tint == null && hasArgument(c, "faction")) {
            src.sendFailure(Component.literal("Name a faction for the sash colour: " + String.join(", ",
                    Arrays.stream(FactionType.values()).map(x -> x.id).toList())));
            return 0;
        }
        List<HeroEntry> heroes = HeroRegistry.heroes();
        if (first >= heroes.size()) {
            src.sendFailure(Component.literal("Only " + heroes.size() + " hero(es): /fc hero list shows them."));
            return 0;
        }
        List<HeroEntry> row = heroes.subList(first, Math.min(heroes.size(), first + count));
        ServerLevel level = src.getLevel();
        Vec2 rot = src.getRotation();
        float yaw = rot.y;
        Vec3 forward = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 centre = src.getPosition().add(forward.scale(4));
        int placed = 0;
        for (int i = 0; i < row.size(); i++) {
            double offset = (i - (row.size() - 1) / 2.0) * spacing;
            Vec3 spot = centre.add(right.scale(offset));
            int x = Mth.floor(spot.x), z = Mth.floor(spot.z);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - src.getPosition().y) > 6) {
                y = Mth.floor(src.getPosition().y);
            }
            HeroManager.Result r = HeroManager.preview(level, row.get(i).id(), tint != null ? tint : FactionType.EMBER_LEGION,
                    new Vec3(spot.x, y, spot.z), yaw + 180F);
            if (r.ok()) {
                placed++;
            } else {
                src.sendFailure(Component.literal(r.message()));
            }
        }
        int n = placed;
        src.sendSuccess(() -> Component.literal("Lined up " + n + " hero(es): " + String.join(", ", row.stream().map(HeroEntry::id).toList())
                + " (/fc admin hero lineup clear removes them)"), true);
        return n;
    }

    private static int clearPreviews(CommandContext<CommandSourceStack> c, String id) {
        int n = 0;
        for (ServerLevel level : c.getSource().getServer().getAllLevels()) {
            n += HeroManager.clearPreviews(level, id);
        }
        int removed = n;
        c.getSource().sendSuccess(() -> Component.literal("Removed " + removed + " preview(s)."), true);
        return n;
    }

    /** Forces what the model shows on every loaded entity of a hero (or of every hero). */
    private static int pose(CommandContext<CommandSourceStack> c, String what, String value, double number) {
        CommandSourceStack src = c.getSource();
        String id = id(c);
        List<HeroEntity> targets = new java.util.ArrayList<>();
        for (ServerLevel level : src.getServer().getAllLevels()) {
            targets.addAll(HeroManager.loaded(level, id.equals("all") ? null : id));
        }
        if (targets.isEmpty()) {
            src.sendFailure(Component.literal("No loaded entity of " + (id.equals("all") ? "any hero" : "\"" + id + "\"")
                    + ". Give it to a faction or stand a preview first."));
            return 0;
        }
        String done = null;
        for (HeroEntity h : targets) {
            String r = applyPose(h, what, value, number);
            if (r.startsWith("!")) {
                src.sendFailure(Component.literal(r.substring(1)));
                return 0;
            }
            done = r;
        }
        String msg = done + " (" + targets.size() + " entit" + (targets.size() == 1 ? "y" : "ies") + ")";
        src.sendSuccess(() -> Component.literal(msg), true);
        return targets.size();
    }

    /** One entity's pose change; a message, or "!" + the reason it cannot be done. */
    private static String applyPose(HeroEntity h, String what, String value, double number) {
        switch (what) {
            case "clear" -> {
                h.clearPose();
                return "Pose cleared";
            }
            case "stop" -> {
                h.stopAction();
                return "Action stopped";
            }
            case "mood" -> {
                int m = value.equals("auto") ? -1 : HeroActions.moodCode(value);
                if (m < 0 && !value.equals("auto")) {
                    return "!Moods: " + String.join(", ", HeroActions.MOODS) + ", auto";
                }
                h.setForcedMood(m);
                return "Mood " + value;
            }
            case "action" -> {
                int code = actionCode(h, value);
                if (code < 0) {
                    return "!No action \"" + value + "\" (attack.1, guard, work.mine, emote.wave, move.<id>, ...)";
                }
                h.playAction(code, number, 0);
                String clip = HeroClips.actionClip(code, h.entry().visual().look().moves(), h.entry().visual().look().states());
                HeroTimings.Clip t = clip == null ? null : HeroTimingFiles.clip(h.entry(), clip);
                String timing = t == null ? "no timing file entry (the hero's chain may not have it)"
                        : t.totalTicks() + " ticks" + (t.firstHit() >= 0 ? ", hit at tick " + t.firstHit() : "")
                        + (number != 1.0 ? String.format(Locale.ROOT, " at 1x; at %.2fx %d ticks", number, HeroTimings.ticksAt(t.totalTicks(), number)) : "");
                return "Action " + clip + " (code " + code + "): " + timing;
            }
            case "base" -> {
                int b = HeroClips.FORCED.indexOf(value);
                if (b < 0) {
                    return "!Base poses: " + String.join(", ", HeroClips.FORCED);
                }
                h.setForcedPose(b);
                return "Base pose " + value;
            }
            case "weapon" -> {
                int w = switch (value) {
                    case "drawn" -> 1;
                    case "sheathed" -> 0;
                    case "auto" -> -1;
                    default -> -2;
                };
                if (w == -2) {
                    return "!weapon drawn, sheathed or auto";
                }
                h.setForcedWeapon(w);
                return "Weapon " + value;
            }
            case "phase" -> {
                h.setState(HeroActions.withPhase(h.state(), (int) number));
                return "Phase " + (int) number;
            }
            case "talk", "windup", "blush", "glow" -> {
                boolean on = value.equals("on");
                if (!on && !value.equals("off")) {
                    return "!" + what + " on or off";
                }
                switch (what) {
                    case "talk" -> h.setTalking(on);
                    case "windup" -> h.setStateFlag(HeroActions.STATE_WINDUP, on);
                    case "blush" -> h.setMood((byte) (on ? h.mood() | 1 << 6 : h.mood() & ~(1 << 6)));
                    default -> h.setMood((byte) (on ? h.mood() | 1 << 7 : h.mood() & ~(1 << 7)));
                }
                return what + " " + value;
            }
            default -> {
                return "!unknown pose " + what;
            }
        }
    }

    /** An action name's code: a fixed name (attack.1, work.mine, ...), {@code move.<id>} or {@code state.<name>} of this hero. */
    private static int actionCode(HeroEntity h, String name) {
        int code = HeroActions.actionCode(name);
        if (code >= 0 && !name.startsWith("move.") && !name.startsWith("state.")) {
            return code;
        }
        List<String> moves = h.entry().visual().look().moves(), states = h.entry().visual().look().states();
        for (int i = 0; i < Math.min(moves.size(), HeroActions.MOVE_SLOTS); i++) {
            String m = moves.get(i);
            if (m != null && (name.equals("move." + m) || name.equals(m))) {
                return HeroActions.FIRST_MOVE + i;
            }
        }
        for (int i = 0; i < Math.min(states.size(), 3); i++) {
            String st = states.get(i);
            if (st != null && (name.equals("state." + st) || name.equals(st))) {
                return HeroActions.FIRST_STATE + i;
            }
        }
        return -1;
    }

    private static String id(CommandContext<CommandSourceStack> c) {
        return StringArgumentType.getString(c, "id").toLowerCase(Locale.ROOT);
    }

    /**
     * A player giving the command gets the hero beside them; the server console or RCON at the base's spawn point.
     * Heroes live in the overworld (where the bases are): from another dimension the hero goes to the base's spawn point,
     * and a position given there is refused, since it would be read as overworld coordinates.
     */
    private static int give(CommandContext<CommandSourceStack> c, BlockPos pos) {
        FactionType f = FactionType.byId(StringArgumentType.getString(c, "faction"));
        if (f == null || !f.human) {
            c.getSource().sendFailure(Component.literal("Name a human faction: " + String.join(", ",
                    Arrays.stream(FactionType.humans()).map(x -> x.id).toList())));
            return 0;
        }
        boolean overworld = c.getSource().getLevel() == c.getSource().getServer().overworld();
        if (pos != null && !overworld) {
            c.getSource().sendFailure(Component.literal("Heroes live in the overworld: give a position from there, or leave it out "
                    + "for the base's spawn point."));
            return 0;
        }
        BlockPos at = pos != null ? pos : overworld && c.getSource().getEntity() instanceof Player p ? p.blockPosition() : null;
        return report(c, HeroManager.give(c.getSource().getServer(), id(c), f, null, at));
    }

    // ------------------------------------------------------------------ A6b's operator hooks

    /** The hero's living entity, or a failure message (null). */
    private static HeroEntity live(CommandContext<CommandSourceStack> c) {
        HeroEntity h = HeroManager.entity(c.getSource().getServer(), id(c));
        if (h == null || !h.isAlive()) {
            c.getSource().sendFailure(Component.literal("No loaded living entity of \"" + id(c) + "\"."));
            return null;
        }
        return h;
    }

    /** {@code health <id> <percent>}: its health jumps to that share of its maximum (phases follow on its next tick). */
    private static int health(CommandContext<CommandSourceStack> c, double percent) {
        HeroEntity h = live(c);
        if (h == null) {
            return 0;
        }
        h.setHealth((float) (h.getMaxHealth() * percent / 100.0));
        c.getSource().sendSuccess(() -> Component.literal(h.npcName() + " at " + Math.round(h.getHealth()) + " / "
                + Math.round(h.getMaxHealth()) + " health (" + percent + "%)."), true);
        return 1;
    }

    /** {@code poise <id> full|<amount>}: poise damage as from a hit; {@code full} fills the bar (a stagger, unless protected). */
    private static int poise(CommandContext<CommandSourceStack> c, double amount) {
        HeroEntity h = live(c);
        if (h == null) {
            return 0;
        }
        var p = h.combat().poise();
        if (p == null || p.none()) {
            c.getSource().sendFailure(Component.literal(h.npcName() + " has no poise bar."));
            return 0;
        }
        p.decay(h.level().getGameTime());
        double a = amount < 0 ? Math.max(1, p.max() - p.value()) : amount;
        boolean staggered = h.combat().poiseDamage(a);
        c.getSource().sendSuccess(() -> Component.literal(h.npcName() + ": poise " + Math.round(p.value()) + " / "
                + Math.round(p.max()) + (staggered ? ", staggered" : "")), true);
        return 1;
    }

    /** {@code escape <id>}: it escapes now, whatever its kind and health (the yield hook of the first draft is gone). */
    private static int escape(CommandContext<CommandSourceStack> c) {
        HeroEntity h = live(c);
        if (h == null) {
            return 0;
        }
        if (!h.combat().boss().escape().start(com.factionclash.logic.hero.EscapeRules.Trigger.LOW_HEALTH, true)) {
            c.getSource().sendFailure(Component.literal(h.npcName() + " is escaping already."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal(h.npcName() + " escapes."), true);
        return 1;
    }

    /** {@code target-state <id> blocking|bow|eating|clear}: its target reads so for its reactions until cleared. */
    private static int targetState(CommandContext<CommandSourceStack> c) {
        HeroEntity h = live(c);
        if (h == null) {
            return 0;
        }
        String st = StringArgumentType.getString(c, "state").toLowerCase(Locale.ROOT);
        int bits = switch (st) {
            case "blocking" -> com.factionclash.hero.HeroCombat.FORCE_BLOCKING;
            case "bow" -> com.factionclash.hero.HeroCombat.FORCE_BOW;
            case "eating" -> com.factionclash.hero.HeroCombat.FORCE_EATING;
            case "clear" -> 0;
            default -> -1;
        };
        if (bits < 0) {
            c.getSource().sendFailure(Component.literal("blocking, bow, eating or clear"));
            return 0;
        }
        int now = bits == 0 ? 0 : h.combat().forcedTargetState() | bits;
        h.combat().forceTargetState(now);
        c.getSource().sendSuccess(() -> Component.literal(h.npcName() + "'s target reads as " + (now == 0 ? "it is" : st)), true);
        return 1;
    }

    private static int report(CommandContext<CommandSourceStack> c, HeroManager.Result r) {
        if (!r.ok()) {
            c.getSource().sendFailure(Component.literal(r.message()));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal(r.message()), true);
        return 1;
    }

    /** Every hero: who it serves (or free), alive or "returns in m:ss", its level. */
    private static int list(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        CampaignData data = CampaignData.get(server);
        List<HeroEntry> heroes = HeroRegistry.heroes();
        c.getSource().sendSuccess(() -> Component.literal("Heroes (" + heroes.size() + ")").withStyle(ChatFormatting.GOLD), false);
        long now = server.overworld().getGameTime();
        for (HeroEntry e : heroes) {
            HeroState s = data.heroes.get(e.id());
            String line = " " + e.name() + (e.title().isEmpty() ? "" : " \u00b7 " + e.title()) + " (" + e.id() + "): " + status(s, now)
                    + (e.dormant() ? " (dormant: no valid definition)" : e.previewOnly() ? " (preview fixture)" : "");
            ChatFormatting colour = e.dormant() ? ChatFormatting.DARK_GRAY : s != null && s.owner != null ? s.owner.color : ChatFormatting.GRAY;
            c.getSource().sendSuccess(() -> Component.literal(line).withStyle(colour), false);
        }
        // heroes in the campaign whose files are gone: dormant, they fight as plain soldiers with the neutral look
        for (HeroState s : data.heroes.values()) {
            if (HeroRegistry.get(s.id) == null && s.owner != null) {
                c.getSource().sendSuccess(() -> Component.literal(" " + HeroManager.nameOf(s.id) + " (" + s.id + "): "
                        + status(s, now) + " (dormant: no hero files)").withStyle(ChatFormatting.DARK_GRAY), false);
            }
        }
        HeroReader.Loaded report = HeroRegistry.report();
        if (c.getSource().hasPermission(2) && (!HeroRegistry.problems().isEmpty() || !report.problems().all().isEmpty())) {
            c.getSource().sendSuccess(() -> Component.literal(" " + report.problems().summary(report.heroes().keySet(), report.notLoaded())
                    + "; " + (HeroRegistry.problems().size() + report.problems().all().size())
                    + " problem(s) in the hero files (/fc hero check)").withStyle(ChatFormatting.YELLOW), false);
        }
        return heroes.size();
    }

    private static String status(HeroState s, long now) {
        if (s == null || s.owner == null) {
            return "free" + (s != null && s.level > 0 ? ", level " + s.level : "");
        }
        String life = s.alive ? "alive" : s.reviveAt > 0 ? "returns in " + HeroRevival.countdown(s.reviveAt - now) : "fallen";
        return s.owner.displayName + ", " + life + ", level " + s.level;
    }

    /**
     * Whether this source may see where a hero is (its entity, health and home coordinates): operators (and the server
     * console or RCON) always, a player only for a hero serving the player's own faction.
     */
    static boolean mayLocate(CommandSourceStack source, HeroState s) {
        if (source.hasPermission(2)) {
            return true;
        }
        if (s == null || s.owner == null || !(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)) {
            return false;
        }
        return CampaignData.get(source.getServer()).players.get(p.getUUID()) == s.owner;
    }

    /** The hero sheet as text (the screen comes with A1b). */
    private static int info(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        String id = id(c);
        HeroEntry e = HeroRegistry.get(id);
        HeroState s = CampaignData.get(server).heroes.get(id);
        if (e == null && s == null) {
            c.getSource().sendFailure(Component.literal("No hero \"" + id + "\". /fc hero list shows them."));
            return 0;
        }
        long now = server.overworld().getGameTime();
        List<String> lines = new java.util.ArrayList<>();
        HeroEntry shown = e != null ? e : HeroRegistry.fallback();
        lines.add((e != null ? e.name() : HeroManager.nameOf(id)) + (e != null && !e.title().isEmpty() ? " · " + e.title() : "") + " (" + id + ")");
        lines.add(" Serves: " + status(s, now));
        // where the hero and its home are is intelligence (players learn other factions' bases from their scouts): only
        // operators and the hero's own faction see it
        boolean insider = mayLocate(c.getSource(), s);
        if (s != null && s.owner != null) {
            BaseState b = CampaignData.get(server).base(s.baseId);
            lines.add(" Home: " + (b == null ? "none" : insider ? b.name + " at " + b.center.toShortString() : b.name)
                    + "; fallen " + s.deaths + " time(s)");
        }
        HeroEntity h = HeroManager.entity(server, id);
        if (h != null && !insider) {
            lines.add(" In the world: only its own faction and operators see where");
        } else if (h != null) {
            lines.add(String.format(Locale.ROOT, " In the world: %s, %.0f/%.0f health, %s, holding %s (entity %s)", h.blockPosition().toShortString(),
                    h.getHealth(), h.getMaxHealth(), h.status(), h.getMainHandItem().isEmpty() ? "nothing" : h.getMainHandItem().getHoverName().getString(),
                    h.getUUID()));
        }
        var v = shown.visual();
        HeroSize.Size size = v.size(h != null ? h.getScale() : v.scale());
        lines.add(String.format(Locale.ROOT, " Body: %s %s (%s), %s style, fights as %s", v.form(), v.build(), v.base(), v.style(),
                shown.role().displayName));
        lines.add(String.format(Locale.ROOT, " Size: %.2f blocks tall (model %.2f), eyes at %.2f%s", size.height(),
                HeroSize.modelHeight(v.heightPx(), v.renderScale(), h != null ? h.getScale() : v.scale()), size.eyeHeight(),
                size.clamped() ? ", capped at 1.98" : size.floored() ? ", raised to the 1.2 minimum" : ""));
        lines.add(" Model: " + shown.model().getPath() + (e != null && e.ownModel() ? "" : " (neutral model)")
                + "; animations " + String.join(" > ", v.animationChain()));
        if (e != null && e.def() != null) {
            List<String> sheet = sheet(e.def(), e.style(), s == null ? 1 : Math.max(1, s.level));
            lines.addAll(sheet.subList(1, sheet.size()));
            if (!e.overrides().isEmpty()) {
                lines.add(" Config override: " + String.join(", ", e.overrides()));
            }
        } else {
            lines.add(" Dormant: no valid definition; it fights as a plain soldier with the neutral look (/fc hero check " + id + ")");
        }
        if (s != null) {
            lines.add(" Record: " + s.kills + " kill(s), " + s.deaths + " death(s), " + s.revivals + " revival(s), fights " + s.fights
                    + (h != null ? String.format(Locale.ROOT, ", energy %d%%%s", h.energy(), h.lastLine().isEmpty() ? "" : ", last said \"" + h.lastLine() + "\"") : ""));
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            ChatFormatting colour = i == 0 ? ChatFormatting.GOLD : ChatFormatting.WHITE;
            c.getSource().sendSuccess(() -> Component.literal(line).withStyle(colour), false);
        }
        return 1;
    }
}
