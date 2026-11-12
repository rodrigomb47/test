package com.factionclash.command;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.HeroState;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroEntry;
import com.factionclash.hero.HeroManager;
import com.factionclash.hero.HeroRegistry;
import com.factionclash.logic.hero.HeroActions;
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
 * Hero commands (HEROES_V2 12.1; v1 27): {@code /fc hero list|info <id>} for everyone and
 * {@code /fc admin hero give <id> <faction> [pos]|release <id>|revive <id>} for operators. {@code check}, {@code reload}
 * and the sheet screen come with the kit format (A1b).
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
                .then(Commands.literal("info").then(Commands.argument("id", StringArgumentType.word()).suggests(HEROES).executes(HeroCommands::info)));
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

    /** A player giving the command gets the hero beside them; the server console or RCON at the base's spawn point. */
    private static int give(CommandContext<CommandSourceStack> c, BlockPos pos) {
        FactionType f = FactionType.byId(StringArgumentType.getString(c, "faction"));
        if (f == null || !f.human) {
            c.getSource().sendFailure(Component.literal("Name a human faction: " + String.join(", ",
                    Arrays.stream(FactionType.humans()).map(x -> x.id).toList())));
            return 0;
        }
        BlockPos at = pos != null ? pos : c.getSource().getEntity() instanceof Player p ? p.blockPosition() : null;
        return report(c, HeroManager.give(c.getSource().getServer(), id(c), f, null, at));
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
            String line = " " + e.name() + " (" + e.id() + "): " + status(s, now);
            ChatFormatting colour = s != null && s.owner != null ? s.owner.color : ChatFormatting.GRAY;
            c.getSource().sendSuccess(() -> Component.literal(line).withStyle(colour), false);
        }
        // heroes in the campaign whose files are gone: dormant, they fight as plain soldiers with the neutral look
        for (HeroState s : data.heroes.values()) {
            if (HeroRegistry.get(s.id) == null && s.owner != null) {
                c.getSource().sendSuccess(() -> Component.literal(" " + HeroManager.nameOf(s.id) + " (" + s.id + "): "
                        + status(s, now) + " (dormant: no hero files)").withStyle(ChatFormatting.DARK_GRAY), false);
            }
        }
        if (c.getSource().hasPermission(2) && !HeroRegistry.problems().isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal(" " + HeroRegistry.problems().size()
                    + " problem(s) in the hero files (see the server log)").withStyle(ChatFormatting.YELLOW), false);
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
        if (s != null && s.owner != null) {
            BaseState b = CampaignData.get(server).base(s.baseId);
            lines.add(" Home: " + (b == null ? "none" : b.name + " at " + b.center.toShortString()) + "; fallen " + s.deaths + " time(s)");
        }
        HeroEntity h = HeroManager.entity(server, id);
        if (h != null) {
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
        lines.add(" Kit, moves and lines: not in this build yet (plain soldier's attacks)"
                + (e != null && !e.overrides().isEmpty() ? "; config override: " + String.join(", ", e.overrides()) : ""));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            ChatFormatting colour = i == 0 ? ChatFormatting.GOLD : ChatFormatting.WHITE;
            c.getSource().sendSuccess(() -> Component.literal(line).withStyle(colour), false);
        }
        return 1;
    }
}
