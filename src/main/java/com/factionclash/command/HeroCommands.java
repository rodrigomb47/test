package com.factionclash.command;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.HeroState;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroEntry;
import com.factionclash.hero.HeroManager;
import com.factionclash.hero.HeroRegistry;
import com.factionclash.logic.hero.HeroRevival;
import com.factionclash.logic.hero.HeroSize;
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
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Hero commands (HEROES_V2 12.1; v1 27): {@code /fc hero list|info <id>} for everyone and
 * {@code /fc admin hero give <id> <faction> [pos]|release <id>|revive <id>} for operators. {@code check}, {@code reload}
 * and the sheet screen come with the kit format (A1b); {@code lineup}, {@code preview} and {@code pose} with the client
 * model (A2).
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
                        .executes(c -> report(c, HeroManager.revive(c.getSource().getServer(), id(c))))));
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
