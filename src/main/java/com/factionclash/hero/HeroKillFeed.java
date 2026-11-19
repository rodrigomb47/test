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
import net.minecraft.server.level.ServerLevel;
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

/**
 * The hero kill feed (the owner's request of 2026-11-13, ROADMAP phase 4; HEROES_V2 6.17; its scope the owner's of
 * 2026-11-19: "only hero on hero and hero deaths by other sources should show, if a hero kills a npc or a monster it
 * shouldnt show"): every death of a hero, whatever killed it, is told in chat to all players, worded like vanilla's death
 * messages with each name's faction ("Natsuho Komezaki (Ember Legion) tried to swim in lava to escape Hiss (Grove
 * Covenant)"), and only while the game rule {@code showDeathMessages} is on. Nothing else is told: no line when a hero
 * kills an ordinary NPC, a monster, an animal or a player (a player's own death is vanilla's own message, to which the
 * feed adds nothing; the hero in it is named by its server-side display name). The killer is {@link KillCredits}'s: the
 * blow's attacker, or the enemy whose push, throw, pull or blow came within 15 s before a death by terrain; the words are
 * vanilla's keys as {@link KillCredit#words} chooses them. One rule for every killer: a hero, a player, an NPC, a monster
 * or terrain alone.
 *
 * <p>The feed is player-side presentation: it tells every player at once what no faction's people saw. No campaign code
 * reads it (the factions learn of deaths only through their own people, {@code docs/design/missing_npcs.md}).
 */
public final class HeroKillFeed {
    private HeroKillFeed() {
    }

    /** GameTests: the feed's lines while recording (null: not recording). */
    private static List<Line> recorder;

    /** A line of the feed: the hero that died, its killer (null: none) and what was said. */
    public record Line(LivingEntity victim, Entity killer, Component text) {
    }

    /** Starts recording the feed's lines (GameTests); {@link #stopRecording} ends it. */
    public static synchronized List<Line> record() {
        recorder = Collections.synchronizedList(new ArrayList<>());
        return recorder;
    }

    public static synchronized void stopRecording() {
        recorder = null;
    }

    /** Whether a death is the feed's: a campaign hero's (a preview or display copy has no campaign death), whoever killed it. */
    public static boolean told(LivingEntity victim) {
        return victim instanceof HeroEntity h && !h.isDisplayOnly();
    }

    /** At a hero's death (after its killer is credited): the feed's line, broadcast while {@code showDeathMessages} is on. */
    public static void onDeath(LivingEntity victim, DamageSource source) {
        if (!(victim.level() instanceof ServerLevel level) || !FCConfig.HERO_KILL_FEED.get() || !told(victim)
                || !level.getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)) {
            return;
        }
        Component line = line(victim, source);
        level.getServer().getPlayerList().broadcastSystemMessage(line, false);
        note(new Line(victim, KillCredits.killer(victim, source), line));
    }

    private static synchronized void note(Line l) {
        if (recorder != null) {
            recorder.add(l);
        }
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
}
