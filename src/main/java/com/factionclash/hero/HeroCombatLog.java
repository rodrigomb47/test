package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.logic.hero.BossRules;
import com.factionclash.npc.FactionNpc;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/**
 * The live check's combat log (HEROES_V2 15.2 "Combat engine in game"): with {@code -Dfactionclash.heroCombatLog=true}
 * on a server (its {@code user_jvm_args.txt}) every hit a hero takes and every hit a hero deals goes to the log as one
 * line, with who it was (a player, a faction NPC, a hero or a mob), the amount before and after the boss rules and the
 * health left, so a fight against NPC soldiers can be set beside the same fight against a player (6.15's equal
 * treatment). Off by default: nothing is built or logged then.
 */
public final class HeroCombatLog {
    public static final boolean ON = Boolean.getBoolean("factionclash.heroCombatLog");

    private HeroCombatLog() {
    }

    /** What kind of person {@code e} is for the log. */
    static String kind(Entity e) {
        if (e == null) {
            return "none";
        }
        if (e instanceof Player) {
            return "player";
        }
        if (e instanceof HeroEntity) {
            return "hero";
        }
        if (e instanceof FactionNpc) {
            return "npc";
        }
        return "mob";
    }

    private static String who(Entity e) {
        return e == null ? "-" : HeroEntity.nameOf(e) + "/" + kind(e);
    }

    /** A hit the hero took: the amount sent, what the boss rules let land (and its poise), what it lost. */
    static void took(HeroEntity hero, DamageSource src, float sent, float lost, BossRules.Landed landed) {
        if (!ON) {
            return;
        }
        FactionClash.LOGGER.info(String.format(Locale.ROOT,
                "[Heroes] combat %s took %.2f from %s (%s): sent %.2f, landed %s, poise +%s, health %.1f/%.1f",
                hero.heroId(), lost, who(src.getEntity()), src.getMsgId(), sent,
                landed == null ? "-" : String.format(Locale.ROOT, "%.2f", landed.health()),
                landed == null ? "-" : String.format(Locale.ROOT, "%.1f", landed.poise()),
                hero.getHealth(), hero.getMaxHealth()));
    }

    /** A move it starts: its id, class, why it was picked, its plan (wind-up, hits, recovery) and its target. */
    static void move(HeroEntity hero, String id, String cls, String source, String plan, LivingEntity target) {
        FactionClash.LOGGER.info(String.format(Locale.ROOT, "[Heroes] combat %s starts %s (%s, %s) at %s: %s",
                hero.heroId(), id, cls, source, who(target), plan));
    }

    /** A basic attack that landed (vanilla's melee blow with the hero's attack damage): what the target lost. */
    static void basic(HeroEntity hero, LivingEntity target, float lost, boolean crit) {
        FactionClash.LOGGER.info(String.format(Locale.ROOT,
                "[Heroes] combat %s basic hit %s%s: lost %.2f, attack %.2f, armour %d, health %.1f/%.1f", hero.heroId(),
                who(target), crit ? " (crit)" : "", lost,
                hero.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE),
                target.getArmorValue(), target.getHealth(), target.getMaxHealth()));
    }

    /** A hit a hero dealt (through {@link HeroDamage#hit}): the amount sent and what the target really lost. */
    static void dealt(LivingEntity target, DamageSource src, float sent, float lost) {
        if (!ON) {
            return;
        }
        Entity by = src.getEntity();
        FactionClash.LOGGER.info(String.format(Locale.ROOT,
                "[Heroes] combat %s dealt %.2f to %s (%s): sent %.2f, armour %d, health %.1f/%.1f",
                by instanceof HeroEntity h ? h.heroId() : who(by), lost, who(target), src.getMsgId(), sent,
                target.getArmorValue(), target.getHealth(), target.getMaxHealth()));
    }
}
