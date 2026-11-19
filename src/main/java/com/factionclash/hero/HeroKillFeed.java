package com.factionclash.hero;

import com.factionclash.campaign.BaseBuilder;
import com.factionclash.combat.CombatRules;
import com.factionclash.combat.KillCredits;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.hero.KillCredit;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatEntry;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DeathMessageType;
import net.minecraft.world.damagesource.FallLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The hero kill feed (the owner's request of 2026-11-13, ROADMAP phase 4; HEROES_V2 6.17): every death in which a hero
 * is the victim or the killer is told in chat to all players, worded like vanilla's death messages with each name's
 * faction ("Ash Rook (Grove Covenant) tried to swim in lava to escape Natsuho Komezaki (Ember Legion)"), and only while
 * the game rule {@code showDeathMessages} is on. The killer is {@link KillCredits}'s: the blow's attacker, or the enemy
 * whose push, throw, pull or blow came within 15 s before a death by terrain; the words are vanilla's keys as
 * {@link KillCredit#words} chooses them. One rule for every victim and killer: a player killed by a hero, a hero killed
 * by a player, by an NPC, by a monster or by terrain alone. Only presentation differs: a player's death is already told
 * by vanilla's own message, so for a player the feed is that message, with the player's faction beside its name
 * ({@link #naming}) and the hero's name and faction from the hero's server-side display name; any other victim's line
 * is the feed's own. Monsters and animals a hero kills are left out unless {@code heroes.killFeedMonsters} is on (a hero
 * on guard or butcher duty kills them all night); a monster that kills a hero is named.
 */
public final class HeroKillFeed {
    private HeroKillFeed() {
    }

    /** Players whose display name carries their faction during their own death message (server thread only). */
    private static final Set<Player> NAMING = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    /** GameTests: the feed's lines while recording (null: not recording). */
    private static List<Line> recorder;

    /** A line of the feed: who died, what was said, and whether it was the player's own vanilla message. */
    public record Line(LivingEntity victim, Entity killer, Component text, boolean vanilla) {
    }

    /** Starts recording the feed's lines (GameTests); {@link #stopRecording} ends it. */
    public static synchronized List<Line> record() {
        recorder = Collections.synchronizedList(new ArrayList<>());
        return recorder;
    }

    public static synchronized void stopRecording() {
        recorder = null;
    }

    /** Whether a hero is the victim or the killer, and the death is the feed's (persons, unless monsters are on). */
    public static boolean told(LivingEntity victim, Entity killer) {
        if (victim instanceof FactionNpc n && n.isDisplayOnly()) {
            return false; // a preview copy, a display NPC: no campaign death
        }
        boolean heroDied = victim instanceof HeroEntity;
        boolean heroKilled = killer instanceof HeroEntity k && !k.isDisplayOnly();
        if (!heroDied && !heroKilled) {
            return false;
        }
        return heroDied || person(victim) || FCConfig.HERO_KILL_FEED_MONSTERS.get();
    }

    /** A person: a player or a faction NPC (a hero is one). */
    static boolean person(Entity e) {
        return e instanceof Player || e instanceof FactionNpc;
    }

    /**
     * At a death (after its killer is credited, before vanilla tells a player's): the feed's line for a non-player
     * victim, broadcast while {@code showDeathMessages} is on; for a player, its name with its faction for vanilla's
     * own message, which follows.
     */
    public static void onDeath(LivingEntity victim, DamageSource source) {
        if (!(victim.level() instanceof ServerLevel level) || !FCConfig.HERO_KILL_FEED.get()) {
            return;
        }
        Entity killer = KillCredits.killer(victim, source);
        if (!told(victim, killer) || !level.getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (victim instanceof ServerPlayer p) {
            naming(p, true);
            // only for vanilla's message, which follows at once (a queued task: execute would run it now)
            server.tell(new net.minecraft.server.TickTask(server.getTickCount(), () -> naming(p, false)));
            note(new Line(victim, killer, p.getCombatTracker().getDeathMessage(), true));
            return;
        }
        Component line = line(victim, source);
        server.getPlayerList().broadcastSystemMessage(line, false);
        note(new Line(victim, killer, line, false));
    }

    private static synchronized void note(Line l) {
        if (recorder != null) {
            recorder.add(l);
        }
    }

    /** Puts a player's faction beside its name in its own display name (its death message), or takes it off again. */
    static void naming(Player p, boolean on) {
        if (on ? NAMING.add(p) : NAMING.remove(p)) {
            p.refreshDisplayName();
        }
    }

    /** {@code PlayerEvent.NameFormat}: the faction beside a dying player's name, for its death message only. */
    public static Component playerName(Player p, Component username) {
        return NAMING.contains(p) ? member(username.copy(), CombatRules.factionOf(p)) : username;
    }

    /**
     * The feed's line for a death by {@code source}: vanilla's key ({@link KillCredit#words}) with the feed's names
     * ({@link #name}), the named item, or the intentional game design's link.
     */
    public static Component line(LivingEntity victim, DamageSource source) {
        String msgId = source.getMsgId();
        Entity causing = source.getEntity();
        Entity attacker = KillCredits.attacker(source);
        LivingEntity credited = attacker == null ? KillCredits.credited(victim, msgId) : null;
        CombatTracker tracker = victim.getCombatTracker();
        CombatEntry sig = tracker.getMostSignificantFall();
        KillCredit.Fall fall = null;
        Entity starter = null;
        if (sig != null) {
            DamageSource fs = sig.source();
            if (fs.is(DamageTypeTags.IS_FALL) || fs.is(DamageTypeTags.ALWAYS_MOST_SIGNIFICANT_FALL)) {
                fall = KillCredit.Fall.accident(Objects.requireNonNullElse(sig.fallLocation(), FallLocation.GENERIC).languageKey());
            } else {
                starter = fs.getEntity();
                // vanilla compares the starter with the killing damage's causing entity by name: the same entity here
                fall = KillCredit.Fall.byHit(starter != null, starter != null && starter == causing, namedItem(starter) != null);
            }
        }
        KillCredit.Words w = KillCredit.words(msgId, type(source), attacker != null, namedItem(causing) != null, fall,
                credited != null, namedItem(credited) != null);
        Entity named = switch (w.killer()) {
            case ATTACKER -> attacker;
            case STARTER -> starter;
            case CREDITED -> credited;
            case NONE -> null;
        };
        List<Object> args = new ArrayList<>(3);
        args.add(name(victim));
        if (w.link()) {
            args.add(ComponentUtils.wrapInSquareBrackets(Component.translatable("death.attack." + msgId + ".link"))
                    .withStyle(CombatTracker.INTENTIONAL_GAME_DESIGN_STYLE));
        } else if (named != null) {
            args.add(name(named));
            if (w.item()) {
                ItemStack item = namedItem(named);
                args.add(item != null ? item.getDisplayName() : Component.empty());
            }
        }
        return Component.translatable(w.key(), args.toArray());
    }

    private static KillCredit.DeathType type(DamageSource s) {
        DeathMessageType t = s.type().deathMessageType();
        return t == DeathMessageType.FALL_VARIANTS ? KillCredit.DeathType.FALL_VARIANTS
                : t == DeathMessageType.INTENTIONAL_GAME_DESIGN ? KillCredit.DeathType.INTENTIONAL_GAME_DESIGN
                : KillCredit.DeathType.DEFAULT;
    }

    /** A living entity's main-hand item when it has a custom name (vanilla's {@code .item} messages), else null. */
    private static ItemStack namedItem(Entity e) {
        if (e instanceof LivingEntity l) {
            ItemStack s = l.getMainHandItem();
            return !s.isEmpty() && s.has(DataComponents.CUSTOM_NAME) ? s : null;
        }
        return null;
    }

    /**
     * A name in the feed: a hero's or an NPC's own name, a player's, a mob's (its custom name or its kind), followed by
     * its faction in brackets when it has one (a member player's faction, a hero's summon's, the Hollow's for a lair
     * monster): {@code Natsuho Komezaki (Ember Legion)}.
     */
    public static Component name(Entity e) {
        String own = e instanceof FactionNpc n && n.npcName() != null && !n.npcName().isEmpty() ? n.npcName() : null;
        MutableComponent base = own != null ? Component.literal(own) : e instanceof Player p ? p.getName().copy()
                : e.getName().copy();
        return member(base, faction(e));
    }

    private static FactionType faction(Entity e) {
        FactionType f = CombatRules.factionOf(e);
        if (f == null && e.getPersistentData().contains(BaseBuilder.LAIR_TAG)) {
            return FactionType.HOLLOW;
        }
        return f;
    }

    private static Component member(MutableComponent name, FactionType f) {
        if (f == null) {
            return name;
        }
        return Component.translatableWithFallback("factionclash.killfeed.member", "%1$s (%2$s)",
                name.withStyle(f.color), Component.literal(f.displayName).withStyle(f.color));
    }

    /** A new server: no player is left named. */
    public static void clearAll() {
        NAMING.clear();
    }
}
