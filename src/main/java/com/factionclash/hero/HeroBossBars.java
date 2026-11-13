package com.factionclash.hero;

import com.factionclash.config.FCConfig;
import com.factionclash.logic.hero.BossRules;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.logic.leader.Palette;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The boss bar and the intro title of fighting heroes (HEROES_V2 6.13; task A6b). Presentation for players only (6.1):
 * nothing in a fight reads it. A hero that fights anyone (a player, NPC soldiers in a raid between two NPC factions,
 * another hero, monsters) shows players within 32 blocks a bar: {@code ★ Name · title  ◆◆◇}, {@code ⚡} when its
 * ultimate is ready, in the bar colour nearest its own (red while enraged), its health as the bar. A player sees at most
 * 3 bars, the nearest fighting heroes first. Each player near a fighting hero gets its title once per hero per in-game
 * day (its name in its colour, "title · faction"). A fight it does not win ends the bar in one of two states for 3 s:
 * {@code ★ Name · fled} (it escaped; the bar keeps its share) or {@code ★ Name · fallen} (it died; the bar is empty).
 * An ultimate darkens the screens of those who see its bar for 2 s. {@code boss.bossBars = false} shows none.
 */
public final class HeroBossBars {
    private HeroBossBars() {
    }

    /** Players are matched to bars this often (progress and the name follow every tick). */
    static final int ASSIGN_TICKS = 10;

    /** One hero's bar: while it fights, then its end state for 3 s. */
    static final class Bar {
        final ServerBossEvent event;
        final UUID hero;
        HeroEntity entity;
        String name = "";
        long endsAt = -1;
        long darkenUntil = -1;

        Bar(HeroEntity h) {
            this.hero = h.getUUID();
            this.entity = h;
            this.event = new ServerBossEvent(Component.literal(""), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        }

        boolean ending() {
            return endsAt >= 0;
        }
    }

    private static final Map<UUID, Bar> BARS = new HashMap<>();
    /** By hero: the in-game day each player last got its title (it outlives the bar of one fight). */
    private static final Map<UUID, Map<UUID, Long>> TITLED = new HashMap<>();
    /** Titles sent since the server started (tests). */
    private static int titlesSent;

    /** Whether the hero shows a bar now: alive, fighting, not escaping, not a display copy. */
    static boolean shows(HeroEntity h) {
        return h.isAlive() && !h.isRemoved() && !h.isPreview() && !h.dormant() && h.combat().boss().fighting()
                && !h.combat().boss().escape().running();
    }

    /** Every server tick (from {@code HeroEvents}): bars made, updated, matched to players, ended. */
    public static void tick(MinecraftServer server) {
        if (!FCConfig.BOSS_BARS.get()) {
            if (!BARS.isEmpty()) {
                clearAll();
            }
            return;
        }
        List<HeroEntity> live = HeroEntity.live();
        if (live.isEmpty() && BARS.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        for (int i = 0; i < live.size(); i++) {
            HeroEntity h = live.get(i);
            Bar b = BARS.get(h.getUUID());
            if (shows(h)) {
                if (b == null || b.ending()) {
                    if (b != null) {
                        b.event.removeAllPlayers();
                    }
                    b = new Bar(h);
                    BARS.put(h.getUUID(), b);
                }
                b.entity = h;
                update(b, h, now);
            } else if (b != null && !b.ending()) {
                // the fight is over (won, or reset 10 s after its last enemy): the bar goes
                b.event.removeAllPlayers();
                BARS.remove(h.getUUID());
            }
        }
        for (Iterator<Bar> it = BARS.values().iterator(); it.hasNext(); ) {
            Bar b = it.next();
            if (b.ending() && now >= b.endsAt || !b.ending() && (b.entity == null || b.entity.isRemoved())) {
                b.event.removeAllPlayers();
                it.remove();
            }
        }
        if (server.getTickCount() % ASSIGN_TICKS == 0 && !BARS.isEmpty()) {
            assign(server, now);
        }
    }

    /** The bar's text and colour are worked out this often (its fill every tick): no string built per tick per hero. */
    static final int NAME_TICKS = 5;

    private static void update(Bar b, HeroEntity h, long now) {
        HeroBoss boss = h.combat().boss();
        if (b.name.isEmpty() || h.tickCount % NAME_TICKS == 0) {
            HeroDef d = h.definition();
            boolean ultimate = d != null && d.v2() != null && d.v2().ultimate() != null && h.energy() >= HeroRules.ENERGY_MAX;
            BossRules.Phases ph = boss.phases();
            int total = BossRules.pips(ph == null ? 1 : ph.count(), d != null && d.v2() != null && d.v2().boss().rebirth());
            String text = BossRules.barName(h.npcName(), h.entry() == null ? null : h.entry().title(), boss.phase(), total, ultimate);
            if (!text.equals(b.name)) {
                b.name = text;
                b.event.setName(Component.literal(text).withStyle(style(h, d)));
            }
            b.event.setColor(boss.enraged() ? BossEvent.BossBarColor.RED : colour(h, d));
        }
        b.event.setProgress(Math.max(0F, Math.min(1F, h.getHealth() / Math.max(1F, h.getMaxHealth()))));
        if (b.darkenUntil >= 0 && now >= b.darkenUntil) {
            b.darkenUntil = -1;
            b.event.setDarkenScreen(false);
        }
    }

    private static int rgb(HeroEntity h, HeroDef d) {
        if (d != null && d.color() != HeroDef.NO_COLOR) {
            return d.color();
        }
        Integer c = h.getFaction().color.getColor();
        return c == null ? 0xFFFFFF : c;
    }

    private static Style style(HeroEntity h, HeroDef d) {
        return Style.EMPTY.withColor(TextColor.fromRgb(rgb(h, d)));
    }

    /** The bar colour nearest the hero's own (6.13). */
    static BossEvent.BossBarColor colour(HeroEntity h, HeroDef d) {
        return BossEvent.BossBarColor.valueOf(Palette.nearestBar(rgb(h, d)).name());
    }

    /**
     * Each player sees the bars of the fighting heroes within 32 blocks, at most 3, nearest first; a bar in its end
     * state keeps whom it had. A player near a fighting hero gets its title once that in-game day.
     */
    private static void assign(MinecraftServer server, long now) {
        List<Bar> active = new ArrayList<>();
        for (Bar b : BARS.values()) {
            if (!b.ending() && b.entity != null) {
                active.add(b);
            }
        }
        Map<Bar, List<ServerPlayer>> want = new HashMap<>();
        double[] dist = new double[active.size()];
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || !p.isAlive()) {
                    continue;
                }
                for (int i = 0; i < active.size(); i++) {
                    HeroEntity h = active.get(i).entity;
                    dist[i] = h.level() == level ? p.distanceTo(h) : -1;
                }
                for (int i : BossRules.nearestBars(dist)) {
                    want.computeIfAbsent(active.get(i), k -> new ArrayList<>()).add(p);
                }
                for (int i = 0; i < active.size(); i++) {
                    if (dist[i] >= 0 && dist[i] <= BossRules.BAR_RADIUS) {
                        title(active.get(i), p, level);
                    }
                }
            }
        }
        for (Bar b : active) {
            List<ServerPlayer> w = want.getOrDefault(b, List.of());
            for (ServerPlayer p : new ArrayList<>(b.event.getPlayers())) {
                if (!w.contains(p)) {
                    b.event.removePlayer(p);
                }
            }
            for (ServerPlayer p : w) {
                if (!b.event.getPlayers().contains(p)) {
                    b.event.addPlayer(p);
                }
            }
        }
    }

    /** The hero's title for one player, once per hero per in-game day: its name in its colour, "title · faction". */
    private static void title(Bar b, ServerPlayer p, ServerLevel level) {
        long day = BossRules.day(level.getDayTime());
        Map<UUID, Long> seen = TITLED.computeIfAbsent(b.hero, k -> new HashMap<>());
        Long last = seen.get(p.getUUID());
        if (last != null && last == day) {
            return;
        }
        seen.put(p.getUUID(), day);
        HeroEntity h = b.entity;
        HeroDef d = h.definition();
        String title = h.entry() == null ? null : h.entry().title();
        MutableComponent sub = Component.literal((title == null || title.isBlank() ? "" : title + " · ") + h.getFaction().displayName);
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 10));
        p.connection.send(new ClientboundSetSubtitleTextPacket(sub.withStyle(net.minecraft.ChatFormatting.GRAY)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(h.npcName()).withStyle(style(h, d))));
        titlesSent++;
    }

    // ------------------------------------------------------------------ the end of a fight, the ultimate

    /**
     * The hero's fight ended without a win: {@code fled} (it escaped: the bar keeps its share) or fallen (it died: the
     * bar is empty), shown to whom the bar had for 3 s. Nothing when no player saw its bar.
     */
    public static void end(HeroEntity h, boolean fled) {
        Bar b = BARS.get(h.getUUID());
        if (b == null || b.ending()) {
            return;
        }
        if (b.event.getPlayers().isEmpty()) {
            BARS.remove(h.getUUID());
            return;
        }
        b.name = BossRules.endName(h.npcName(), fled);
        b.event.setName(Component.literal(b.name).withStyle(style(h, h.definition())));
        b.event.setProgress(fled ? Math.max(0F, Math.min(1F, h.getHealth() / Math.max(1F, h.getMaxHealth()))) : 0F);
        b.event.setDarkenScreen(false);
        b.endsAt = h.level().getGameTime() + BossRules.END_SHOW_TICKS;
        b.entity = null;
    }

    /** An ultimate's wind-up: the screens of those who see the hero's bar darken for 2 s (6.4). */
    public static void darken(HeroEntity h) {
        Bar b = BARS.get(h.getUUID());
        if (b != null && !b.ending() && FCConfig.BOSS_BARS.get()) {
            b.event.setDarkenScreen(true);
            b.darkenUntil = h.level().getGameTime() + BossRules.DARKEN_TICKS;
        }
    }

    private static void clearAll() {
        for (Bar b : BARS.values()) {
            b.event.removeAllPlayers();
        }
        BARS.clear();
    }

    /** The server stops: no bar outlives it. */
    public static void serverStopped() {
        clearAll();
        TITLED.clear();
        titlesSent = 0;
    }

    // ------------------------------------------------------------------ what tests and operators read

    /** The players who see the hero's bar now (empty: none). */
    public static List<ServerPlayer> viewers(HeroEntity h) {
        Bar b = BARS.get(h.getUUID());
        return b == null ? List.of() : List.copyOf(b.event.getPlayers());
    }

    /** The bar's text now ("" with no bar). */
    public static String text(HeroEntity h) {
        Bar b = BARS.get(h.getUUID());
        return b == null ? "" : b.name;
    }

    /** The bar's colour and fill (null with no bar). */
    public static BossEvent.BossBarColor barColour(HeroEntity h) {
        Bar b = BARS.get(h.getUUID());
        return b == null ? null : b.event.getColor();
    }

    public static float progress(HeroEntity h) {
        Bar b = BARS.get(h.getUUID());
        return b == null ? -1 : b.event.getProgress();
    }

    /** Whether the hero's bar is darkening its viewers' screens now. */
    public static boolean darkening(HeroEntity h) {
        Bar b = BARS.get(h.getUUID());
        return b != null && b.event.shouldDarkenScreen();
    }

    /** Whether {@code p} got the hero's title in this in-game day. */
    public static boolean titled(HeroEntity h, ServerPlayer p) {
        Map<UUID, Long> seen = TITLED.get(h.getUUID());
        Long d = seen == null ? null : seen.get(p.getUUID());
        return d != null && d == BossRules.day(h.level().getDayTime());
    }

    public static int titlesSent() {
        return titlesSent;
    }
}
